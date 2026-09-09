# Increment 18 — A Real-Repository Pipeline Run

## Objective

Point `aerf-pipeline` at a real, cloned, non-synthetic codebase for the
first time — the ExtractionAdapter Plan's own stated purpose ("use real
code and evidence to determine whether the model survives contact with
reality") and the piece Increment 16 deliberately deferred pending
Increment 17's Tarjan rewrite. Not a permanent CI job (see "Scope" below)
— an exploratory run, treated exactly as the plan's Risks section asked:
"observation, not assertion." It found one real bug (fixed here) and one
real, concrete counter-example for open question #1 (recorded, not
"fixed" — see below).

## Scope

- Cloned `spring-projects/spring-petclinic` (a small, real, idiomatic
  Spring Boot application: 30 source files, real Spring MVC controllers,
  real Spring Data JPA repositories, real domain entities) into a
  scratch directory — not committed to this repository; a real external
  codebase is data for an exploratory run, not a fixture this repository
  owns or maintains.
- Resolved its real dependency classpath (171 jars) via its own
  `./mvnw dependency:build-classpath`, and ran `aerf-pipeline`'s `Main`
  against its `src/main/java` with that real classpath supplied.
- **Found and fixed a real crash**: `Pipeline.run` threw
  `IllegalStateException` from `InvariantEvaluator` when a GRAPH-scope
  invariant (`entropy_budget`) referenced `total_entropy` and
  `total_entropy` was undefined — which happens legitimately whenever
  any nonzero-weighted entropy dimension has no relevant edges/nodes at
  all (section 3.5/5.1), exactly what happened here because
  `persistenceEntropy` had zero relevant edges (see "Evidence" below).
  Fixed by adding `Invariant.referencedMetricNames()` and having
  `Pipeline` skip (not evaluate) a GRAPH-scope invariant whose
  referenced metrics aren't all available, reporting the skip in a new
  `PipelineReport.skippedInvariants()` field rather than letting it
  silently disappear or crash the run.
- **Found a real, concrete counter-example for open question #1**
  ("when does seed-only role classification actually fail?") — recorded
  in `open-questions-register.md`, not resolved here (a fix is a
  separate, deliberate design decision — new open question #18).
- 4 new tests: `Invariant.referencedMetricNames()` (`InvariantTest`,
  `aerf-analysis`) and the skip-not-crash behavior
  (`PipelineTest`, `aerf-pipeline`).

**Explicitly out of scope:** fixing open question #1's finding (adding a
Spring Data marker-interface seed rule) — a real design decision (which
marker interfaces, how deep to match) that deserves its own increment,
not a side effect of this one (see new open question #18); adding a
permanent CI job for real-repository runs — this was one manual,
observational run, not automation; committing the cloned repository or
its classpath into this repository.

## Why `spring-petclinic`

Small enough to run in seconds and read every line of by hand (necessary
for confirming *why* a finding occurred, not just *that* it occurred);
real enough to carry genuine Spring idioms this project's own sample
fixtures, written by the same person who wrote the extractor, would
never accidentally omit or accidentally match. That gap — between what a
fixture author expects and what real code actually does — is exactly
what an author's own synthetic samples structurally cannot test, and
exactly what this increment exists to check.

## Evidence — what this run found

- **The crash was real and reproducible, not a fluke.** `total_entropy`
  came back undefined because `persistenceEntropy` had zero relevant
  edges: `PersistenceEntropyCalculator` only counts a `CALL` edge whose
  *target* node has `Role.PERSISTENCE`, and — see the next finding — no
  node in this codebase ever got that role. Confirmed the fix by
  re-running the exact same command after it: exit 0, no exception,
  `entropy_budget` correctly listed in `skippedInvariants` while
  `no_presentation_to_persistence` (which references no metric) still
  evaluated normally.
- **Open question #1's real failure case, confirmed by reading the
  source directly, not inferred from the tool's output alone**: `git
  grep`-level inspection of `VetRepository.java` and `OwnerRepository.java`
  shows `public interface VetRepository extends Repository<Vet, Integer>`
  and `public interface OwnerRepository extends JpaRepository<Owner, Integer>`
  — no `@Repository` anywhere. Every one of petclinic's three repository
  interfaces, and every one of its domain entities (`Owner`, `Pet`,
  `Vet`, `Visit`, all extending a shared `BaseEntity`/`NamedEntity`/
  `Person` hierarchy with no stereotype evidence anywhere in the chain),
  stayed `Role.UNKNOWN`. Of 118 extracted nodes, exactly 43 (every
  `@Controller` class and its own methods, via Increment 16's evidence
  propagation) got `PRESENTATION`; zero got `PERSISTENCE`, `APPLICATION`,
  or `DOMAIN`. `DefaultSeedRules`' `PersistenceBySpringDataAdapter` rule
  requires `sourceAdapter == "spring-data"` evidence, which
  `JavaSourceExtractor` only emits for an explicit `@Repository`
  annotation (Increment 15); `DefaultGraphRefinementRules`'
  `InheritRoleFromSupertype` requires the supertype to itself be a graph
  node with a known role, which `Repository`/`JpaRepository` never are
  (external, unextracted marker interfaces). Neither mechanism this
  project has built can see this pattern — and it is not a rare or
  contrived pattern: it is *the* standard way to write a Spring Data
  repository. This is exactly the shape open question #1 asked for
  ("a node... no seed rule fires for at all but its neighbors imply a
  role") and exactly why Increment 15's own three-annotated-classes
  sample (each unambiguous under seed rules alone) could never have
  found it — the gap is precisely in code that *doesn't* carry the
  annotation this project's own extractor knows how to read.
- **An apparent second anomaly, chased down and found to be no anomaly
  at all — worth recording as a methodology note, not a finding about
  the pipeline.** Comparing a raw `JavaSourceExtractor.extract()` call
  against the same source/classpath (which showed every `EXTENDS`/
  `IMPLEMENTS` target, including third-party ones like
  `org.springframework.data.jpa.repository.JpaRepository`, resolving to
  a clean fully-qualified name) against the final `PipelineReport`'s
  graph (which showed those same edges' targets as `NodeRef.Unresolved`)
  looked like a real discrepancy at first — until checking each
  unresolved edge's own evidence `fidelity` field, which reads
  `L2_SYMBOL_RESOLVED` in every case. There was no discrepancy: this is
  exactly Increment 13's already-established extractor-vs-graph-resolution
  distinction (the `java.io.Serializable` finding), recurring here at
  real-code scale for `Serializable`, `JpaRepository`, `Repository`,
  `RuntimeHintsRegistrar`, `Formatter`, `Validator`, and
  `WebMvcConfigurer` — the extractor confidently resolved every one of
  their types via the real classpath; `GraphAssembler` correctly reports
  them `Unresolved` because none of them was itself part of the
  extracted source set. Recorded here because chasing it down (a
  targeted spike comparing `ExtractionResult` facts directly against the
  final `Graph`) is itself a small, concrete demonstration of this
  project's own "verify, don't assume" discipline catching a
  self-inflicted misreading before it became a false claim in this
  document.
- **`AnalysisConfidence` came back `100/260 ≈ 0.385`** on real code —
  markedly lower than any synthetic sample this project has run, and
  exactly what the previous finding predicts: a real Spring application
  makes far more calls into unextracted library code (Spring MVC,
  Spring Data, the JDK) than any hand-built sample does. Not a defect;
  the metric doing exactly what section 5.4 asks of it.
- **`layerEntropy` came back `0.0`** with only 7 relevant edges, all
  `PRESENTATION`-to-`PRESENTATION` calls between a controller's own
  methods (allowed under any reasonable layering policy) — an
  unsurprising, correct result given that no node besides controllers
  ever received a role at all, not evidence the codebase is well-layered
  so much as evidence there was almost nothing left for this metric to
  measure once #1's gap is accounted for.

## Open questions for the architecture

New: **#18** (Spring Data marker-interface seed rule — the concrete,
scoped follow-up to #1's finding). #1 itself updated with the real
counter-example, not closed — a fix is a separate, deliberate design
decision this increment does not make.

## Scope check

One real-repository run, one real crash found and fixed (with tests),
one real open-question-1 counter-example recorded. No change to
`DefaultSeedRules`, `DefaultGraphRefinementRules`, or any extraction
logic — the temptation to "just add the Spring Data rule while I'm here"
was deliberately not taken, per open question #18's own reasoning: which
marker interfaces to recognize and how deep to match them are real
design decisions, not a two-line fix to slip in as a side effect. `mvn
-B test` at the repo root passes with 216 tests total (up from 211 after
Increment 17), across all seven modules.
