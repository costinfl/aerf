# Increment 16 — Pipeline Runner and CI

## Objective

Sixth and final step of the ExtractionAdapter Plan: compose every stage
built across Increments 1-15 — extraction, role inference, the four MVP
entropy metrics, invariant evaluation, calibration — into one runnable
pipeline over real Java source, and give the repository its first CI. This
is the plan's own "does the model survive contact with reality" test run
end to end for the first time, against three deliberately planted defects.

## Scope

- New module `aerf-pipeline` (`Pipeline`, `PipelineConfig`, `PipelineReport`,
  `Main`), depending on `aerf-openrewrite`, `aerf-analysis`, `aerf-report`.
  Like `aerf-openrewrite`, it carries no `maven-enforcer` ban (it depends
  on OpenRewrite transitively, by design).
- `Pipeline.run(PipelineConfig)` — extraction → `GraphAssembler` →
  `IterativeRoleInferenceEngine` (seed + graph-relationship refinement to
  a fixed point) → all four entropy calculators → `AggregatedEntropy` /
  `Maturity` / `MaturityLevel` / `AnalysisConfidence` → every configured
  `Invariant`. No new analysis logic — every stage is a call to an
  existing, independently-tested class; this class only decides order and
  carries data between stages.
- `Main` — a runnable CLI (`java ... Main <source-root> [classpath...]`)
  printing one JSON report via `aerf-report`'s existing writers, using an
  explicit illustrative governance config (`LayerPolicy`,
  `CalibrationProfile`, both `SpecWorkedExamples` invariants) built the
  same way every prior "illustrative, not part of v0.4" catalog in this
  codebase is.
- A **new, purpose-built "defect sample"** under
  `aerf-pipeline/src/test/resources/defect-sample` — kept separate from
  `aerf-openrewrite`'s own sample (see "Why a separate sample" below) —
  planting exactly the three defects the plan's Verification section asks
  for: a direct Presentation→Persistence layering violation
  (`OrderController` depending on and calling `OrderRepository` directly,
  skipping an Application layer entirely), an N+1 loop (the same call
  repeated inside a for-each loop), and a three-level inheritance chain
  (`OrderRepository` → `JpaOrderRepository` → `OrderRepositoryImpl`)
  needing two role-refinement passes, not one.
- **A real capability found and closed while wiring this up:**
  `JavaSourceExtractor` (from `aerf-openrewrite`) now propagates a class's
  own Spring stereotype evidence onto each of its declared methods'
  `FUNCTION` `NodeFact`s too — see "Why FUNCTION-level evidence
  propagation was necessary" below. This is a small, targeted change to
  an earlier increment's module, made because the pipeline could not
  otherwise produce a single flagged layering or N+1 finding at all.
- First GitHub Actions workflow (`.github/workflows/ci.yml`): build and
  test on push/PR (`mvn -B test`), JDK 21.
- 5 new tests in `aerf-pipeline`; propagation covered by 1 new test in
  `aerf-openwrite`'s existing suite.

**Explicitly out of scope, deferred:** the plan's own second CI job
("running the analysis against a real cloned repository") is **not**
added in this increment — see "The real-repository CI job was deliberately
not added" below. `Pipeline` also does not attempt drift (§5.3) or
`E_inv` aggregation (§6.1) — both remain open questions (#12, #14, #15)
this increment does not resolve, since `InvariantEvaluationResult` and
each entropy dimension's own result already carry everything a caller
needs to combine them however governance chooses, and inventing a
combination formula here would be answering an open question by
implementation rather than by decision.

## Why a separate sample, not an extension of `aerf-openrewrite`'s

`aerf-openrewrite`'s existing sample (Increments 13-15) is tuned as a
precise **extraction**-correctness oracle — every fact it produces is
individually asserted against in `JavaSourceExtractorTest`. Adding a
deliberate layering violation or a three-level inheritance chain to it
would perturb edge/node counts several existing tests pin exactly,
entangling two increments' worth of fixtures for no benefit. A dedicated
`defect-sample`, scoped to what Increment 16 needs to demonstrate, keeps
each increment's fixtures — and the tests that pin them — independently
stable, matching this project's own "small deterministic increments"
discipline.

## Why FUNCTION-level evidence propagation was necessary

Every metric this increment needed to demonstrate — `LayerEntropyCalculator`,
`PersistenceEntropyCalculator`, `SpecWorkedExamples.noPresentationToPersistence`
— reads a **`CALL`/`DEPENDS` edge's own endpoint nodes'** roles directly
(`graph.node(ref.id()).role()`). But a `CALL` edge's endpoints are always
`FUNCTION` nodes (Increment 14's design), and nothing prior to this
increment ever gave a `FUNCTION` node a role: `DefaultSeedRules`' Spring
rules only ever matched evidence Increment 15 attached to a class's own
`COMPONENT` node, and `DefaultGraphRefinementRules`' only rule
(`InheritRoleFromSupertype`) only follows `EXTENDS`/`IMPLEMENTS` edges,
which never touch a `FUNCTION` node either. Concretely: without this
change, `OrderController#getOrder(...)`'s `FUNCTION` node stayed
`Role.UNKNOWN` forever, `LayerEntropyCalculator` excluded every `CALL`
edge from its measurement universe entirely (`sourceRole.isEmpty()`), and
the pipeline could report *zero* layering violations and *zero* N+1
findings — not because none existed, but because the model as it stood
could never see one on a `CALL` edge at all. Copying a class's stereotype
evidence onto each of its own declared methods closes this for the one
evidence shape this project currently produces; the underlying structural
gap (no relation connects a `FUNCTION` node to its declaring `COMPONENT`
node) is recorded as new open question #17, not silently resolved by this
workaround.

## Implementation decisions not dictated by v0.4

1. **`PipelineConfig` requires every governance-sensitive input
   explicitly, with no built-in default anywhere in `Pipeline` itself.**
   `LayerPolicy` and `CalibrationFunction` are both already documented
   elsewhere in this codebase as "must be an explicit governance choice";
   `Pipeline` does not quietly pick one. `Main` has to make some concrete
   choice to be runnable at all, so it does — via a clearly-named
   `illustrativeGovernanceConfig`, exactly the same status as
   `DefaultSeedRules`/`DefaultSecurityRules`/`DefaultGraphRefinementRules`.
2. **Security is weighted `0.0`, not `0.25`, in every calibration profile
   this increment constructs (`Main` and `PipelineTest` alike).**
   `DefaultSecurityRules`' one rule only applies to `NodeType.VIEW` nodes,
   and no adapter in this project — Java-only, per §11 — has ever emitted
   one. `SecurityEntropyResult.value()` is therefore always undefined for
   any analysis this pipeline can currently run, and `AggregatedEntropy`
   requires every *nonzero-weight* dimension to be defined or the whole
   aggregate becomes undefined. Weighting it `0.0` uses `AggregatedEntropy`'s
   own documented escape hatch ("a dimension explicitly weighted 0 is
   exempt") rather than fabricating a view-layer adapter this increment
   has no business building, or silently reporting an always-undefined
   total entropy from a runnable CLI.
3. **`IterativeRoleInferenceEngine` returns only a role map, not a new
   `Graph`** (unlike `SeedRoleInferenceEngine.inferAndApply`) —
   `Pipeline.applyRoles` reconstructs one, mirroring `inferAndApply`'s own
   logic exactly, so both engines end up producing the same shape of
   result for any caller.
4. **`Main`'s JSON output is one hand-assembled object combining every
   `aerf-report` writer's output**, not a new bundled type in
   `aerf-report` itself — `CalibrationJson`'s own documentation already
   states there is deliberately no single combined "calibration report"
   object, since bundling calibration, metrics, invariants, and the graph
   together is a schema decision `aerf-report` has not made (open
   question #16). `Main` makes that call for its own CLI output only,
   without asserting it as `aerf-report`'s canonical shape.

## Evidence — what this increment proves or exposes about the AERF model

- **The pipeline runner actually finds all three planted defects on
  real, parsed code**, confirmed against real numbers rather than
  approximate assertions: `layerEntropy.value() == 1.0` (all three
  layer-relevant edges in the sample violate), `persistenceEntropy.value()
  == 0.5` (one of two direct-to-repository calls is looped),
  `roleRefinementPasses == 2` (the exact pass count the three-level chain
  requires, derived analytically from `IterativeRoleInferenceEngine`'s
  per-pass snapshot semantics before ever running the code, then
  confirmed by it). This is the first time in the project any of these
  numbers came from parsing text rather than being hand-asserted into a
  test fixture.
- **Interface-extends-interface produces `IMPLEMENTS`, not `EXTENDS`** —
  `public interface JpaOrderRepository extends OrderRepository {}`
  produces an `IMPLEMENTS` edge in `JavaSourceExtractor`'s output, not
  `EXTENDS`, discovered by inspecting real extracted output rather than
  assumed from the keyword in the source. `J.ClassDeclaration.getExtends()`
  is `null` for an interface's own `extends` clause; OpenRewrite's parser
  represents it through `getImplements()` instead, matching a real
  quirk of the Java grammar (an interface's `extends` clause syntactically
  permits a comma-separated list, exactly like `implements`, unlike a
  class's single-supertype `extends`). Harmless here — both
  `InheritRoleFromSupertype` and this increment's own layering checks
  already treat `EXTENDS`/`IMPLEMENTS` identically — but a caller that
  cared to distinguish "this type extends that interface" from "this type
  implements that interface" at the interface-inheritance level could not,
  from this extractor's output alone. Not filed as a new open question:
  no current metric or rule in this codebase needs that distinction, and
  the plan's own Increment 13 entry already anticipated relation-mapping
  surprises as exactly the kind of thing "verify, don't assume" exists to
  catch.
- **`AnalysisConfidence` (§5.4) correctly surfaces real, uneventful
  incompleteness at the JDK boundary**, not a defect: two of the defect
  sample's seven edges — `Order`'s own `private final Long id` field
  (resolves to `java.lang.Long`, L2 fidelity) and `getManyOrders`' loop
  body calling `orders.add(...)` (resolves to `java.util.List#add`, L2
  fidelity) — are legitimately `NodeRef.Unresolved` at the graph level for
  exactly Increment 13's already-established reason: OpenRewrite resolved
  both types confidently, but neither `java.lang.Long` nor `java.util.List`
  was itself part of the extracted source set, so no `NodeFact` exists for
  either. `AnalysisConfidence` reports `5/7 ≈ 0.714`, not `1.0` — a small,
  concrete demonstration that §5.4's "must be reported rather than
  silently converting incomplete evidence into certainty" holds even on a
  sample with no unresolved *types* at all, only unresolved *extraction
  scope*.
- **A constructor's `FUNCTION` id uses OpenRewrite's own internal name,
  confirmed rather than assumed:** `JavaType.Method.getName()` for a
  constructor is literally the string `"<constructor>"`
  (`com.example.Order#<constructor>(java.lang.Long)` in `Main`'s real
  output), not the class's simple name. This was flagged as unverified in
  Increment 14's own notes and never separately checked; running the real
  pipeline against a real constructor finally confirmed it. Harmless for
  this project (no code reads or parses a `FUNCTION` id's internal
  structure), but exactly the kind of assumption this project's discipline
  says must be checked, not carried forward untested.
- **The FUNCTION-role-propagation gap (see above) is this increment's
  most significant architectural finding** — filed as open question #17
  rather than silently patched over, since the fix applied here (copy
  evidence at extraction time) is adapter-specific and does not generalize
  to every future evidence source the way a structural graph relation
  would.

## The real-repository CI job was deliberately not added

The plan's own Risks section names this exact gate: *"Recursive Tarjan
(open question #7) becomes real ... treat an iterative rewrite as a gate
before Increment 16's real-repository run."* `StronglyConnectedComponents`
is still implemented recursively (confirmed by reading its source in this
increment, not assumed) and will stack-overflow on a sufficiently deep,
perfectly ordinary dependency chain — a risk a small synthetic sample
never exercises but a real, nontrivial-sized repository plausibly would.
Adding a CI job that runs `Main` against a real cloned repository before
that gate closes would risk a flaky, hard-to-diagnose CI failure
unrelated to whatever change triggered it, rather than a meaningful
signal. `.github/workflows/ci.yml` records this reasoning inline as a
comment; open question #7 remains the tracking entry for the gate itself.

## Open questions for the architecture

New: **#17**, "should the canonical model have a structural relation from
a FUNCTION node to its declaring COMPONENT node?" (see above). Existing
**#7** (recursive Tarjan) is now directly load-bearing for CI, not just a
future concern — recorded inline in the new workflow file as well as in
the register.

## Scope check

One pipeline runner, one CLI entry point, one new purpose-built sample,
one CI workflow (build+test only), one small, well-motivated extension to
an earlier increment's extractor (evidence propagation) with its own gap
filed as an open question rather than hidden. No drift/§5.3, no `E_inv`
aggregation/§6.1, no real-repository CI job, no iterative Tarjan rewrite —
all four remain open, tracked, and explicitly out of this increment's
scope rather than silently skipped. `mvn -B test` at the repo root passes
with 209 tests total (up from 203 after Increment 15), across all seven
modules including this one's 5. This closes the ExtractionAdapter Plan:
every increment from 11 through 16 is complete, and AERF v0.4's pipeline
has now run — and found real, verifiable findings — on real Java source
for the first time.
