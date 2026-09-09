# Increment 19 — Scan Dashboard

## Objective

Give AERF's scan results somewhere to live and be looked at. Every prior
increment produced a `PipelineReport` for one run and discarded it once
the process exited; this increment adds persistence (a Supabase Postgres
project, `aerf-db`) and a small public dashboard — a project dropdown, a
table of that project's past scans, and a side drawer with the full
metrics for whichever scan is selected — published as a GitHub Pages
project site. Unlike Increments 1–18, this is infrastructure/tooling
around the AERF pipeline, not new AERF v0.4 model or extraction
behavior; the "Objective/Scope/Evidence" format is kept anyway, since
this project's discipline of writing every decision down doesn't stop
being useful just because the increment isn't about the specification.

## Scope

Four new top-level things, none of them Java/Maven:

- `supabase/schema.sql` — two tables (`projects`, `scans`), RLS enabled
  with public (anon/publishable-key) **read-only** access and no write
  policy at all — only a service-role key, which bypasses RLS entirely,
  can insert. This matters concretely, not just as a best practice:
  the publishable key is embedded in the public dashboard's JS, so if
  anon could write, anyone who opened the page could write bogus scan
  rows.
- `scripts/push-scan/push-scan.mjs` — a small Node script that reads one
  `PipelineReport` JSON file (`aerf-pipeline`'s `Main` output, unchanged)
  and upserts it into Supabase: one `projects` row, one `scans` row with
  flat summary columns derived from the report's own top-level fields
  plus the full report kept verbatim in a `jsonb` column. Run manually,
  per the user's own answer when asked how results should get from a
  scan into the database — not a new step inside `aerf-pipeline`, not
  CI-automated yet.
- `dashboard/` — the published static site (`index.html`, `style.css`,
  `app.js`), reading Supabase directly from the browser with the
  publishable key. No build step.
- `.github/workflows/pages.yml` — deploys `dashboard/` to GitHub Pages
  on push, separate from `ci.yml` (a different artifact, different
  trigger path-filter). The account's existing `costinfl.dev` custom
  domain redirect already extends to a new repo's project page with no
  further DNS/CNAME work, per the user's own confirmation.

**Explicitly out of scope:** CI-automated scanning (the user chose
manual for now); a real service-role key ever touching this session (by
design — see "Why I can't run the write path myself" below); tracking
any project beyond `spring-petclinic` for now (the user's own choice);
any change to `aerf-pipeline`'s own report shape — the dashboard and the
seed script both consume `PipelineReport`'s existing JSON as-is.

## Why I can't run the write path myself

Two independent reasons converged on the same design, not one driving
the other:

1. **Security shape.** The publishable/anon key is meant to be public
   (it ends up in the dashboard's JS, readable by anyone). A key that
   can write must never be the one embedded in public-facing code, so
   the write path needs a *different*, private credential regardless of
   who holds it.
2. **This session's own network policy blocks it anyway** — confirmed,
   not assumed: both `https://sxfgopmtedjutkxuixas.supabase.co` and the
   CDN hosts originally considered for loading `supabase-js`
   (`esm.sh`, `cdn.jsdelivr.net`) returned `403` from this session's
   egress proxy (`/root/.ccr/README.md`: *"the destination host is not
   allowed by your organization's egress policy for this session... do
   not retry or route around it"*). Even with a service-role key in
   hand, this session could not have reached Supabase to seed data
   itself.

Both the schema and the seed script are therefore handed to the user to
run themselves (see "What the user needs to do" below) — not a
workaround for a missing key alone, but the correct shape independent
of it.

## The CDN block turned into a design improvement, not just a blocker

The same proxy block that ruled out self-seeding also broke the
dashboard's first draft, which loaded `@supabase/supabase-js` from
`esm.sh` at runtime. Rather than accept an untestable dependency, the
library is vendored instead: `dashboard/vendor/supabase.js` is the
package's own official UMD build, copied in from the same npm install
the seed script already needs (`scripts/push-scan/node_modules`), with
`dashboard/vendor/README.md` recording exactly where it came from and
how to update it. This is a strictly better shape for a static site
regardless of the sandbox's restrictions — no external script host to
go down, change its response, or need a Subresource Integrity hash
against — and it is what made local, full end-to-end verification of
the dashboard possible at all in this session (see "Evidence" below).

## Implementation decisions not otherwise obvious

1. **The `scans` table's flat columns are derived, not independently
   computed.** `push-scan.mjs`'s `summaryRow(report)` reads
   `report.layerEntropy.value`, `report.totalEntropy`, etc. directly by
   the same field names `Pipeline`/`Main` already produce, rather than
   recomputing anything from the raw graph. The script can never
   disagree with what the pipeline itself reported.
2. **`report` is stored as one `jsonb` column, not decomposed into more
   tables.** The drawer needs the full graph, every entropy dimension's
   relevant/violating (or flagged) edge lists, every invariant's
   violations, and extraction diagnostics — exactly what
   `PipelineReport`'s JSON already contains. Decomposing it into a
   normalized schema would mean writing (and keeping in sync) a second
   serialization of the same data `aerf-report`'s JSON writers already
   produce, for no reader this increment has.
3. **"Undefined" is rendered as its own visibly distinct state, not
   blank or zero**, in the stat tiles, the summary table, and the
   drawer's entropy rows (`formatRatio` returns an explicit,
   italicized "undefined" span rather than `null` or `0.000`) — the
   same section 3.5/5.1 principle every entropy calculator's own code
   already follows, carried through to the last layer that renders it
   to a person.
4. **Skipped invariants get their own labeled section in the drawer**,
   not silently absent from the invariants list — mirroring
   `PipelineReport.skippedInvariants()`'s own Increment 18 rationale
   (section 5.4: incomplete evidence must stay visible).

## Evidence — what building and testing this proved

- **A real CSS bug, found by testing the actual rendered page rather
  than trusting the markup.** `.drawer { ...; display: flex; }` and the
  browser's default `[hidden] { display: none }` have equal selector
  specificity, and my rule came later in the stylesheet — so setting
  the `hidden` attribute from JS did nothing, and the drawer rendered
  open by default. Found via a real headless-Chromium screenshot (not
  by reading the CSS and reasoning it should work), fixed with
  `.drawer[hidden], .drawer-backdrop[hidden] { display: none; }` (a
  higher-specificity attribute selector that wins regardless of source
  order).
- **A CDN dependency that would have silently broken the page for real
  visitors**, caught the same way: the first draft's `import ... from
  'https://esm.sh/@supabase/supabase-js@2'` failed in this sandbox
  (blocked host), which is not by itself evidence of a real-world
  problem — but it forced writing the vendored version, which is
  strictly more robust for every visitor regardless of network policy,
  not just this session's.
- **End-to-end rendering verified against the real Increment 18
  spring-petclinic report**, not a hand-built fixture: a Playwright test
  intercepted the Supabase REST calls and returned rows built from
  `scripts/push-scan/sample-reports/spring-petclinic.json` (the exact
  file the user will seed with) using the same `summaryRow` derivation
  `push-scan.mjs` uses. The rendered dashboard reproduced Increment
  18's own real findings exactly: total entropy and maturity both
  "undefined" (persistence entropy has zero relevant edges — no
  repository in petclinic carries an explicit `@Repository`
  annotation), confidence `0.385`, zero role-refinement passes, 43
  `PRESENTATION` / 75 `UNKNOWN` nodes, `entropy_budget` listed under
  "Skipped," and `no_presentation_to_persistence` holding. Confirmed in
  both light and dark mode (a fresh page load per mode — mid-session
  `prefers-color-scheme` emulation in headless Chromium turned out to
  be an unreliable way to test a theme switch, a testing-methodology
  note worth recording since it cost real time chasing a phantom "dark
  mode is broken" lead before a fresh-load test showed the CSS was
  correct all along).
- **The seed script's validation paths were exercised directly**
  (`--help`, missing required arguments, missing environment
  variables) — all three produce the intended usage message and exit
  code before ever attempting a Supabase call.

## What the user needs to do

1. Run `supabase/schema.sql` in the Supabase SQL editor for `aerf-db`
   (no schema-execution tool is available to this session — checked;
   only a read-only `mcp__Supabase__query_logs` tool is present).
2. From `scripts/push-scan/`, `npm install`, then run `push-scan.mjs`
   against the committed sample report with their own
   `SUPABASE_SERVICE_ROLE_KEY` (never given to this session) — the
   exact command is in `push-scan.mjs`'s own `--help` output and in
   this repo's root `README`-equivalent instructions from the assistant
   turn that shipped this increment.
3. Enable GitHub Pages once: **Settings → Pages → Source: GitHub
   Actions**.

## Open questions for the architecture

None new for AERF v0.4 itself — this increment is tooling around the
pipeline, not a change to the model, extraction, or metrics. Two
tooling-scoped follow-ups worth naming for whoever picks this up next,
not filed in `open-questions-register.md` since that register is
specifically for AERF v0.4 architecture, not dashboard/ops decisions:
automating scans (currently manual only, per the user's own choice) and
whether `scans` should ever be pruned/aggregated once real scan history
accumulates (no retention policy exists yet, because there is no
history yet).

## Scope check

One dashboard, one seed script, one schema, one deploy workflow. No
change to `aerf-pipeline`, `aerf-report`, or any other Java module — the
dashboard and the seed script both consume `PipelineReport`'s existing
JSON output unmodified. No automation added beyond what the user asked
for (manual scans, one tracked project). The Java reactor's own test
suite is unaffected: `mvn -B test` still passes with the same 216 tests
Increment 18 left it at.
