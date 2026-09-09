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

// Plain-English explanations for the metric labels below - AERF's own
// vocabulary (entropy, maturity, confidence, invariants) reads as
// jargon without them. Shown via a shared tooltip (see wireInfoIcons)
// activated by an "i" button next to the label it explains.
const METRIC_INFO = {
  roleRefinement:
    'How many extra passes were needed to work out architectural roles from graph relationships (e.g. inheriting a role from a supertype), beyond what annotations alone could seed directly.',
  layerEntropy:
    "Share of layer-relevant calls/dependencies that skip past an architectural layer (e.g. a UI class calling a data-access class directly). 0 = fully layered. Undefined when there's nothing relevant to measure.",
  cycleEntropy: 'Share of nodes caught in a circular dependency. 0 = no cycles found.',
  persistenceEntropy:
    'Share of calls into persistence code that happen inside a loop - a common "N+1" performance mistake, one query per item instead of one query total. Undefined when no persistence calls were found at all.',
  securityEntropy:
    'Share of applicable security-relevant code (like view rendering) where a real weakness was detected. Undefined when nothing applicable was found.',
  totalEntropy:
    'All the entropy numbers above, combined by weight into one score. 0 = no measured drift. Undefined if any weighted dimension is itself undefined.',
  maturity: '1 minus total entropy - a simple, inverted view of the same score. Higher is better.',
  confidence:
    "Share of extracted relationships where both ends were identified. Lower usually means more calls into third-party/JDK code that was never itself scanned - not a defect, just a reason to read the other numbers with that in mind.",
  violations:
    'A governance rule (e.g. "the UI layer must never call persistence directly") that was checked against this scan and found broken.',
  skippedInvariants:
    "A rule that couldn't be checked this run because a number it depends on was undefined (see Total entropy) - not a violation, just not evaluable this time.",
};

function infoIcon(key) {
  return `<button class="info-icon" type="button" data-info="${escapeHtml(key)}" aria-label="What is this?">i</button>`;
}

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
const metricTooltip = document.getElementById('metric-tooltip');

let currentScans = [];
let currentProjects = [];

init();

async function init() {
  drawerClose.addEventListener('click', closeDrawer);
  drawerBackdrop.addEventListener('click', closeDrawer);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closeDrawer();
      hideTooltip();
    }
  });
  projectSelect.addEventListener('change', () => loadScans(projectSelect.value));
  wireInfoIcons();

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
    statTile('Total entropy', 'totalEntropy', formatRatio(latest.total_entropy)),
    statTile('Maturity', 'maturity', formatRatio(latest.maturity), latest.maturity_level ?? ''),
    statTile('Confidence', 'confidence', formatRatio(latest.confidence)),
    statTile(
      'Invariant violations',
      'violations',
      String(latest.invariant_violation_count),
      violationBadge(latest.invariant_violation_count).outerHTML,
    ),
  ].join('');
}

function statTile(label, infoKey, value, sub = '') {
  return `<div class="stat-tile">
    <p class="stat-label">${escapeHtml(label)} ${infoIcon(infoKey)}</p>
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
  hideTooltip();
}

/**
 * One shared tooltip, positioned near whichever info icon was
 * activated, rather than a separate tooltip element per icon - the
 * same metric label (and so the same icon) can appear in the stat
 * tiles, the table header, and the drawer, and both the table and the
 * drawer re-render their whole innerHTML on every scan/row change, so
 * delegating from `document` (rather than binding a listener per icon)
 * is what keeps this working after a re-render without re-wiring.
 */
function wireInfoIcons() {
  document.addEventListener('click', (event) => {
    const icon = event.target.closest('.info-icon');
    if (icon) {
      event.stopPropagation();
      const key = icon.dataset.info;
      if (metricTooltip.dataset.for === key && !metricTooltip.hidden) {
        hideTooltip();
      } else {
        showTooltip(icon, METRIC_INFO[key] ?? 'No description available.');
      }
      return;
    }
    if (!event.target.closest('.metric-tooltip')) {
      hideTooltip();
    }
  });
  // A tooltip is `position: fixed`, anchored to the icon's viewport
  // position at the moment it opened - if the drawer's own scrollable
  // body (or the page) scrolls afterward, that position goes stale, so
  // any scroll just closes it rather than tracking the icon around.
  document.addEventListener('scroll', hideTooltip, { capture: true, passive: true });
  window.addEventListener('resize', hideTooltip);
}

function showTooltip(anchor, text) {
  metricTooltip.textContent = text;
  metricTooltip.dataset.for = anchor.dataset.info;
  metricTooltip.style.left = '-9999px';
  metricTooltip.style.top = '-9999px';
  metricTooltip.hidden = false;

  document.querySelectorAll('.info-icon[aria-expanded="true"]').forEach((el) => el.setAttribute('aria-expanded', 'false'));
  anchor.setAttribute('aria-expanded', 'true');

  const anchorRect = anchor.getBoundingClientRect();
  const tooltipRect = metricTooltip.getBoundingClientRect();
  const left = Math.max(8, Math.min(anchorRect.left, window.innerWidth - tooltipRect.width - 8));
  let top = anchorRect.bottom + 6;
  if (top + tooltipRect.height > window.innerHeight - 8) {
    top = anchorRect.top - tooltipRect.height - 6;
  }
  metricTooltip.style.left = `${left}px`;
  metricTooltip.style.top = `${top}px`;
}

function hideTooltip() {
  if (metricTooltip.hidden) return;
  metricTooltip.hidden = true;
  document.querySelectorAll('.info-icon[aria-expanded="true"]').forEach((el) => el.setAttribute('aria-expanded', 'false'));
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
    <div class="metric-row"><span class="metric-name">Role refinement ${infoIcon('roleRefinement')}</span><span></span><span class="metric-value">${scan.role_refinement_passes} pass${scan.role_refinement_passes === 1 ? '' : 'es'}</span></div>
    <p class="stat-label" style="margin-top:14px">Role distribution (${total} nodes)</p>
    <div class="role-bar">${bar}</div>
    <div class="role-legend">${legend}</div>
  </div>`;
}

function entropyRow(label, infoKey, value, relevantCount, flaggedCount, flaggedLabel) {
  const pct = value == null ? 0 : Math.round(value * 100);
  const valueText = formatRatio(value);
  const countText =
    relevantCount === 0
      ? 'no relevant context'
      : `${flaggedCount}/${relevantCount} ${flaggedLabel}`;
  return `<div class="metric-row">
      <span class="metric-name">${escapeHtml(label)} ${infoIcon(infoKey)}</span>
      <span class="meter-track"><span class="meter-fill" style="width:${pct}%"></span></span>
      <span class="metric-value">${valueText}</span>
    </div>
    <div class="metric-row"><span></span><span class="value-undefined">${escapeHtml(countText)}</span><span></span></div>`;
}

function renderEntropySection(report) {
  return `<div class="drawer-section">
    <h3>Entropy dimensions</h3>
    ${entropyRow('Layer', 'layerEntropy', report.layerEntropy.value, report.layerEntropy.relevantEdgeCount, report.layerEntropy.violatingEdgeCount, 'violating')}
    ${entropyRow('Cycle', 'cycleEntropy', report.cycleEntropy.value, report.cycleEntropy.totalNodeCount, report.cycleEntropy.participatingNodeCount, 'in a cycle')}
    ${entropyRow('Persistence (N+1)', 'persistenceEntropy', report.persistenceEntropy.value, report.persistenceEntropy.relevantEdgeCount, report.persistenceEntropy.flaggedEdgeCount, 'flagged')}
    ${entropyRow('Security', 'securityEntropy', report.securityEntropy.value, report.securityEntropy.opportunityCount, report.securityEntropy.flaggedCount, 'flagged')}
  </div>`;
}

function renderAggregateSection(scan) {
  return `<div class="drawer-section">
    <h3>Aggregate (section 5)</h3>
    <div class="metric-row"><span class="metric-name">Total entropy ${infoIcon('totalEntropy')}</span><span></span><span class="metric-value">${formatRatio(scan.total_entropy)}</span></div>
    <div class="metric-row"><span class="metric-name">Maturity ${infoIcon('maturity')}</span><span></span><span class="metric-value">${formatRatio(scan.maturity)}</span></div>
    <div class="metric-row"><span class="metric-name">Maturity level</span><span></span><span class="metric-value">${scan.maturity_level ? escapeHtml(scan.maturity_level) : '<span class="value-undefined">undefined</span>'}</span></div>
    <div class="metric-row"><span class="metric-name">Confidence ${infoIcon('confidence')}</span><span></span><span class="metric-value">${formatRatio(scan.confidence)}</span></div>
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
    ? `<p class="stat-label" style="margin-top:14px">Skipped (referenced an undefined metric) ${infoIcon('skippedInvariants')}</p>
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
