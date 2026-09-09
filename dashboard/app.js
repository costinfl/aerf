// `supabase` (the createClient factory) comes from vendor/supabase.js,
// loaded as a plain <script> before this module - see vendor/README.md
// for why it's vendored rather than CDN-loaded.

// Safe to embed: this is the publishable/anon key, and the schema's RLS
// policies (supabase/schema.sql) grant it read-only access - it can
// never write, no matter who has it. See docs/increment-19-*.md.
const SUPABASE_URL = 'https://sxfgopmtedjutkxuixas.supabase.co';
const SUPABASE_ANON_KEY = 'sb_publishable_EeB9qA3R_GpAG_LhGZi4IQ_nGuPpmru';

const supabaseClient = supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: { persistSession: false },
});

const ROLE_COLORS = {
  PRESENTATION: 'var(--cat-1)',
  APPLICATION: 'var(--cat-2)',
  DOMAIN: 'var(--cat-3)',
  PERSISTENCE: 'var(--cat-4)',
  INFRASTRUCTURE: 'var(--cat-5)',
  EXTERNAL: 'var(--cat-6)',
  UNKNOWN: 'var(--cat-7)',
};

const projectSelect = document.getElementById('project-select');
const projectMeta = document.getElementById('project-meta');
const statTiles = document.getElementById('stat-tiles');
const tableBody = document.getElementById('scan-table-body');
const drawer = document.getElementById('drawer');
const drawerBackdrop = document.getElementById('drawer-backdrop');
const drawerBody = document.getElementById('drawer-body');
const drawerTitle = document.getElementById('drawer-title');
const drawerSubtitle = document.getElementById('drawer-subtitle');
const drawerClose = document.getElementById('drawer-close');

let currentScans = [];
let currentProjects = [];

init();

async function init() {
  drawerClose.addEventListener('click', closeDrawer);
  drawerBackdrop.addEventListener('click', closeDrawer);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeDrawer();
  });
  projectSelect.addEventListener('change', () => loadScans(projectSelect.value));

  try {
    const { data, error } = await supabaseClient.from('projects').select('id, name, repo_url').order('name');
    if (error) throw error;
    currentProjects = data ?? [];
    renderProjectOptions(currentProjects);
    if (currentProjects.length > 0) {
      projectSelect.value = currentProjects[0].id;
      loadScans(currentProjects[0].id);
    } else {
      projectSelect.innerHTML = '<option value="" disabled selected>No projects yet</option>';
      tableBody.innerHTML = emptyRow('No projects have been scanned yet.');
    }
  } catch (error) {
    projectSelect.innerHTML = '<option value="" disabled selected>Failed to load</option>';
    tableBody.innerHTML = emptyRow(`Could not load projects: ${escapeHtml(error.message)}`);
  }
}

function renderProjectOptions(projects) {
  projectSelect.innerHTML = projects
    .map((p) => `<option value="${escapeHtml(p.id)}">${escapeHtml(p.name)}</option>`)
    .join('');
}

async function loadScans(projectId) {
  const project = currentProjects.find((p) => p.id === projectId);
  projectMeta.textContent = project?.repo_url ?? '';
  tableBody.innerHTML = emptyRow('Loading scans…');
  statTiles.hidden = true;

  const { data, error } = await supabaseClient
    .from('scans')
    .select('*')
    .eq('project_id', projectId)
    .order('scanned_at', { ascending: false });

  if (error) {
    tableBody.innerHTML = emptyRow(`Could not load scans: ${escapeHtml(error.message)}`);
    return;
  }

  currentScans = data ?? [];
  if (currentScans.length === 0) {
    tableBody.innerHTML = emptyRow('No scans recorded for this project yet.');
    return;
  }

  renderStatTiles(currentScans[0]);
  renderTable(currentScans);
}

function renderStatTiles(latest) {
  statTiles.hidden = false;
  statTiles.innerHTML = [
    statTile('Total entropy', formatRatio(latest.total_entropy)),
    statTile('Maturity', formatRatio(latest.maturity), latest.maturity_level ?? ''),
    statTile('Confidence', formatRatio(latest.confidence)),
    statTile(
      'Invariant violations',
      String(latest.invariant_violation_count),
      violationBadge(latest.invariant_violation_count).outerHTML,
    ),
  ].join('');
}

function statTile(label, value, sub = '') {
  return `<div class="stat-tile">
    <p class="stat-label">${escapeHtml(label)}</p>
    <p class="stat-value">${value}</p>
    ${sub ? `<p class="stat-sub">${sub}</p>` : ''}
  </div>`;
}

function renderTable(scans) {
  tableBody.innerHTML = scans
    .map(
      (scan, index) => `<tr data-clickable tabindex="0" data-index="${index}">
        <td>${formatDate(scan.scanned_at)}</td>
        <td>${scan.node_count}</td>
        <td>${scan.edge_count}</td>
        <td>${formatRatio(scan.total_entropy)}</td>
        <td>${formatRatio(scan.maturity)}${scan.maturity_level ? ` <span class="value-undefined">(${escapeHtml(scan.maturity_level)})</span>` : ''}</td>
        <td>${formatRatio(scan.confidence)}</td>
        <td>${violationBadge(scan.invariant_violation_count).outerHTML}</td>
        <td>${scan.skipped_invariants?.length ? scan.skipped_invariants.length : '&mdash;'}</td>
      </tr>`,
    )
    .join('');

  tableBody.querySelectorAll('tr[data-clickable]').forEach((row) => {
    const open = () => openDrawer(scans[Number(row.dataset.index)]);
    row.addEventListener('click', open);
    row.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        open();
      }
    });
  });
}

function violationBadge(count) {
  const span = document.createElement('span');
  if (count > 0) {
    span.className = 'badge badge-critical';
    span.textContent = `${count} violation${count === 1 ? '' : 's'}`;
  } else {
    span.className = 'badge badge-good';
    span.textContent = 'none';
  }
  return span;
}

function openDrawer(scan) {
  const project = currentProjects.find((p) => p.id === scan.project_id);
  drawerTitle.textContent = project?.name ?? scan.project_id;
  drawerSubtitle.textContent = `Scanned ${formatDate(scan.scanned_at)}`;
  drawerBody.innerHTML = renderDrawerBody(scan);
  drawer.hidden = false;
  drawerBackdrop.hidden = false;
  drawer.setAttribute('aria-hidden', 'false');
  drawerClose.focus();
}

function closeDrawer() {
  drawer.hidden = true;
  drawerBackdrop.hidden = true;
  drawer.setAttribute('aria-hidden', 'true');
}

function renderDrawerBody(scan) {
  const report = scan.report;
  return [
    renderGraphSection(scan, report),
    renderEntropySection(report),
    renderAggregateSection(scan),
    renderInvariantsSection(report),
    renderDiagnosticsSection(report),
    renderRawJsonSection(report),
  ].join('');
}

function renderGraphSection(scan, report) {
  const roleCounts = {};
  for (const node of report.graph.nodes) {
    roleCounts[node.role] = (roleCounts[node.role] ?? 0) + 1;
  }
  const total = report.graph.nodes.length || 1;
  const roles = Object.entries(roleCounts).sort((a, b) => b[1] - a[1]);

  const bar = roles
    .map(([role, count]) => `<span style="flex:${count};background:${ROLE_COLORS[role] ?? 'var(--text-muted)'}"></span>`)
    .join('');
  const legend = roles
    .map(
      ([role, count]) =>
        `<span class="role-legend-item"><span class="role-swatch" style="background:${ROLE_COLORS[role] ?? 'var(--text-muted)'}"></span>${escapeHtml(role)} (${count})</span>`,
    )
    .join('');

  return `<div class="drawer-section">
    <h3>Graph</h3>
    <div class="metric-row"><span class="metric-name">Nodes</span><span></span><span class="metric-value">${scan.node_count}</span></div>
    <div class="metric-row"><span class="metric-name">Edges</span><span></span><span class="metric-value">${scan.edge_count}</span></div>
    <div class="metric-row"><span class="metric-name">Role refinement</span><span></span><span class="metric-value">${scan.role_refinement_passes} pass${scan.role_refinement_passes === 1 ? '' : 'es'}</span></div>
    <p class="stat-label" style="margin-top:14px">Role distribution (${total} nodes)</p>
    <div class="role-bar">${bar}</div>
    <div class="role-legend">${legend}</div>
  </div>`;
}

function entropyRow(label, value, relevantCount, flaggedCount, flaggedLabel) {
  const pct = value == null ? 0 : Math.round(value * 100);
  const valueText = formatRatio(value);
  const countText =
    relevantCount === 0
      ? 'no relevant context'
      : `${flaggedCount}/${relevantCount} ${flaggedLabel}`;
  return `<div class="metric-row">
      <span class="metric-name">${escapeHtml(label)}</span>
      <span class="meter-track"><span class="meter-fill" style="width:${pct}%"></span></span>
      <span class="metric-value">${valueText}</span>
    </div>
    <div class="metric-row"><span></span><span class="value-undefined">${escapeHtml(countText)}</span><span></span></div>`;
}

function renderEntropySection(report) {
  return `<div class="drawer-section">
    <h3>Entropy dimensions</h3>
    ${entropyRow('Layer', report.layerEntropy.value, report.layerEntropy.relevantEdgeCount, report.layerEntropy.violatingEdgeCount, 'violating')}
    ${entropyRow('Cycle', report.cycleEntropy.value, report.cycleEntropy.totalNodeCount, report.cycleEntropy.participatingNodeCount, 'in a cycle')}
    ${entropyRow('Persistence (N+1)', report.persistenceEntropy.value, report.persistenceEntropy.relevantEdgeCount, report.persistenceEntropy.flaggedEdgeCount, 'flagged')}
    ${entropyRow('Security', report.securityEntropy.value, report.securityEntropy.opportunityCount, report.securityEntropy.flaggedCount, 'flagged')}
  </div>`;
}

function renderAggregateSection(scan) {
  return `<div class="drawer-section">
    <h3>Aggregate (section 5)</h3>
    <div class="metric-row"><span class="metric-name">Total entropy</span><span></span><span class="metric-value">${formatRatio(scan.total_entropy)}</span></div>
    <div class="metric-row"><span class="metric-name">Maturity</span><span></span><span class="metric-value">${formatRatio(scan.maturity)}</span></div>
    <div class="metric-row"><span class="metric-name">Maturity level</span><span></span><span class="metric-value">${scan.maturity_level ? escapeHtml(scan.maturity_level) : '<span class="value-undefined">undefined</span>'}</span></div>
    <div class="metric-row"><span class="metric-name">Confidence</span><span></span><span class="metric-value">${formatRatio(scan.confidence)}</span></div>
  </div>`;
}

function renderInvariantsSection(report) {
  const invariants = Object.values(report.invariants ?? {});
  const items = invariants
    .map(
      (inv) => `<div class="invariant-item">
        <div>
          <div class="invariant-name">${escapeHtml(inv.invariantName)}</div>
          <div class="invariant-severity">${escapeHtml(inv.severity)}${inv.violations?.length ? ` &middot; ${inv.violations.length} violation${inv.violations.length === 1 ? '' : 's'}` : ''}</div>
        </div>
        ${inv.holds ? badgeHtml('good', 'holds') : badgeHtml('critical', 'violated')}
      </div>`,
    )
    .join('');

  const skipped = report.skippedInvariants ?? [];
  const skippedHtml = skipped.length
    ? `<p class="stat-label" style="margin-top:14px">Skipped (referenced an undefined metric)</p>
       <ul class="skipped-list">${skipped.map((name) => `<li>${escapeHtml(name)}</li>`).join('')}</ul>`
    : '';

  return `<div class="drawer-section">
    <h3>Invariants (section 6)</h3>
    ${items || '<p class="value-undefined">No invariants configured for this run.</p>'}
    ${skippedHtml}
  </div>`;
}

function renderDiagnosticsSection(report) {
  const diagnostics = report.extractionDiagnostics ?? [];
  if (diagnostics.length === 0) return '';
  return `<div class="drawer-section">
    <h3>Extraction diagnostics</h3>
    <ul class="diagnostics-list">${diagnostics.map((d) => `<li>${escapeHtml(d)}</li>`).join('')}</ul>
  </div>`;
}

function renderRawJsonSection(report) {
  return `<div class="drawer-section">
    <details class="raw-json">
      <summary>View raw report JSON</summary>
      <pre>${escapeHtml(JSON.stringify(report, null, 2))}</pre>
    </details>
  </div>`;
}

function badgeHtml(kind, label) {
  return `<span class="badge badge-${kind}">${escapeHtml(label)}</span>`;
}

function formatRatio(value) {
  return value == null ? '<span class="value-undefined">undefined</span>' : value.toFixed(3);
}

function formatDate(iso) {
  return new Date(iso).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

function emptyRow(message) {
  return `<tr><td colspan="8" class="empty-state">${escapeHtml(message)}</td></tr>`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[c]);
}
