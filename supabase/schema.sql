-- AERF scan dashboard schema, for the aerf-db Supabase project.
--
-- Run once in the Supabase SQL editor. Two tables, kept deliberately
-- close to PipelineReport's own shape
-- (aerf-pipeline/src/main/java/org/aerf/pipeline/PipelineReport.java):
-- flat columns drive the dashboard's summary table without parsing JSON
-- client-side, and the full report is kept verbatim in `scans.report`
-- so the side drawer needs no second query and no new serialization
-- code on either side.
--
-- Row Level Security: public (anon/publishable key) can only ever
-- SELECT. There is no insert/update/delete policy for anon at all -
-- only a service-role key, which bypasses RLS entirely, can write.
-- This matters because the publishable key ends up embedded in the
-- public dashboard's JS; if it could write, anyone visiting the page
-- could write bogus rows.

create table if not exists projects (
  id text primary key,               -- slug, e.g. 'spring-petclinic'
  name text not null,
  repo_url text,
  created_at timestamptz not null default now()
);

create table if not exists scans (
  id uuid primary key default gen_random_uuid(),
  project_id text not null references projects(id) on delete cascade,
  scanned_at timestamptz not null default now(),
  node_count int not null,
  edge_count int not null,
  role_refinement_passes int not null,
  layer_entropy double precision,
  cycle_entropy double precision,
  persistence_entropy double precision,
  security_entropy double precision,
  total_entropy double precision,
  maturity double precision,
  maturity_level text,
  confidence double precision,
  invariant_violation_count int not null,
  skipped_invariants text[] not null default '{}',
  report jsonb not null               -- the full PipelineReport, verbatim
);

create index if not exists scans_project_id_scanned_at_idx
  on scans (project_id, scanned_at desc);

alter table projects enable row level security;
alter table scans enable row level security;

drop policy if exists "public read projects" on projects;
create policy "public read projects" on projects for select using (true);

drop policy if exists "public read scans" on scans;
create policy "public read scans" on scans for select using (true);
