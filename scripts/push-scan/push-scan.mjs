#!/usr/bin/env node
// Upserts one aerf-pipeline PipelineReport (JSON, as produced by
// `aerf-pipeline`'s Main) into the aerf-db Supabase project's
// `projects`/`scans` tables (see ../../supabase/schema.sql), for the
// scan dashboard at ../../dashboard.
//
// Requires a Supabase *service role* key, never the publishable/anon
// key the dashboard itself uses - the schema's RLS policies grant anon
// read-only access, so a write needs the key that bypasses RLS. Keep
// that key in your own shell environment; this script only ever reads
// it from an env var, never accepts it as a CLI argument (which would
// end up in shell history).
//
// Usage:
//   SUPABASE_URL=https://sxfgopmtedjutkxuixas.supabase.co \
//   SUPABASE_SERVICE_ROLE_KEY=... \
//   node push-scan.mjs --project-id spring-petclinic \
//     --project-name "Spring PetClinic" \
//     --repo-url https://github.com/spring-projects/spring-petclinic \
//     --report sample-reports/spring-petclinic.json

import { createClient } from '@supabase/supabase-js';
import { readFileSync } from 'node:fs';

function printUsageAndExit(code) {
  console.error(`Usage: node push-scan.mjs --project-id <id> --project-name <name> --report <path> [--repo-url <url>]

Required environment variables:
  SUPABASE_URL               e.g. https://sxfgopmtedjutkxuixas.supabase.co
  SUPABASE_SERVICE_ROLE_KEY  a Supabase service role key (never the publishable/anon key)

Arguments:
  --project-id     Stable slug used as the primary key, e.g. spring-petclinic
  --project-name   Human-readable name shown in the dashboard's dropdown
  --report         Path to a PipelineReport JSON file (aerf-pipeline's Main output)
  --repo-url       Optional source repository URL, shown in the dashboard
  --help           Show this message
`);
  process.exit(code);
}

function parseArgs(argv) {
  const args = { projectId: null, projectName: null, repoUrl: null, report: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '--help':
        printUsageAndExit(0);
        break;
      case '--project-id':
        args.projectId = argv[++i];
        break;
      case '--project-name':
        args.projectName = argv[++i];
        break;
      case '--repo-url':
        args.repoUrl = argv[++i];
        break;
      case '--report':
        args.report = argv[++i];
        break;
      default:
        console.error(`Unrecognized argument: ${arg}`);
        printUsageAndExit(2);
    }
  }
  return args;
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`Missing required environment variable: ${name}`);
    printUsageAndExit(2);
  }
  return value;
}

/**
 * Derives the dashboard's flat summary columns from a PipelineReport's
 * own top-level fields (aerf-pipeline's PipelineReport.java /
 * Main.java's toJson) - deliberately matched field-for-field rather
 * than recomputed, so this script never disagrees with what the
 * pipeline itself reported.
 */
function summaryRow(report) {
  const invariantResults = Object.values(report.invariants ?? {});
  return {
    node_count: report.graph.nodes.length,
    edge_count: report.graph.edges.length,
    role_refinement_passes: report.roleRefinementPasses,
    layer_entropy: report.layerEntropy?.value ?? null,
    cycle_entropy: report.cycleEntropy?.value ?? null,
    persistence_entropy: report.persistenceEntropy?.value ?? null,
    security_entropy: report.securityEntropy?.value ?? null,
    total_entropy: report.totalEntropy ?? null,
    maturity: report.maturity ?? null,
    maturity_level: report.maturityLevel ?? null,
    confidence: report.confidence ?? null,
    invariant_violation_count: invariantResults.filter((r) => r.holds === false).length,
    skipped_invariants: report.skippedInvariants ?? [],
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.projectId || !args.projectName || !args.report) {
    console.error('--project-id, --project-name, and --report are all required.\n');
    printUsageAndExit(2);
  }

  const supabaseUrl = requireEnv('SUPABASE_URL');
  const serviceRoleKey = requireEnv('SUPABASE_SERVICE_ROLE_KEY');

  let report;
  try {
    report = JSON.parse(readFileSync(args.report, 'utf-8'));
  } catch (error) {
    console.error(`Failed to read/parse report file ${args.report}: ${error.message}`);
    process.exit(1);
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false },
  });

  const { error: projectError } = await supabase
    .from('projects')
    .upsert({ id: args.projectId, name: args.projectName, repo_url: args.repoUrl }, { onConflict: 'id' });
  if (projectError) {
    console.error(`Failed to upsert project: ${projectError.message}`);
    process.exit(1);
  }

  const row = { project_id: args.projectId, report, ...summaryRow(report) };
  const { data, error: scanError } = await supabase.from('scans').insert(row).select('id, scanned_at');
  if (scanError) {
    console.error(`Failed to insert scan: ${scanError.message}`);
    process.exit(1);
  }

  console.log(`Inserted scan ${data[0].id} for project '${args.projectId}' (scanned_at ${data[0].scanned_at}).`);
}

main();
