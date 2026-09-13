# Increment 22 — The N+1 Heuristic's Source Side: Decision

First increment of the post-v0.4.1 implementation phase
(`docs/aerf-post-v0.4.1-backlog.md`), resolving **OQ-08**.

## Objective

Decide, from real evidence rather than intuition, whether
`PersistenceEntropyCalculator` should constrain the **source** side of a
flagged persistence context — today it constrains only the target's role
(`PERSISTENCE`) and the edge's own `ITERATED` provenance.

The backlog is explicit that this is an evidence question, not a licence
to change code: *"Do not change the heuristic merely because the question
exists."* It is also explicit about the order of work: question →
evidence → decision → decision record → tests → implementation.

## Decision

**Reject the source-side restriction. No production code changes.**

The deliverable is this decision record plus six regression tests that pin
behaviour which, until now, held only by omission.

## Evidence

### The stale report problem, found and fixed first

The committed `scripts/push-scan/sample-reports/spring-petclinic.json` was
produced **before** increment 20 taught the extractor to recognise Spring
Data repositories by marker-interface inheritance. It reports **zero**
`PERSISTENCE` nodes and therefore zero persistence contexts — useless as
evidence, and actively misleading if read as current.

Modern spring-petclinic was therefore re-scanned on current code
(`0.2.0-SNAPSHOT`, commit `b8ed3a2`) before deciding. Both files are kept:
the original is left untouched as the historical artefact the
reconciliation rounds cited, and the refresh is committed beside it as
`spring-petclinic-rescan-post-increment-20.json`. Nothing is rewritten to
look as though it always said this (backlog §10.5).

What the refresh changed, same source revision (`818c413`), same
governance config, different AERF version:

| | committed (pre-inc-20) | re-scan (current) |
|---|---|---|
| `PERSISTENCE` nodes | 0 | **8** |
| persistence relevant / flagged | 0 / 0 | **10 / 0** |
| persistence entropy | undefined | **0.0 (defined)** |
| layer entropy | 0.0 | **0.667** |
| total entropy | undefined | **0.222** |
| maturity | undefined | **0.778 (L3_CONTROLLED)** |
| confidence | 0.3846 | 0.3846 (unchanged) |

This is also direct OQ-01 evidence: increment 20's adapter fix did not
merely add nodes, it moved this codebase from *unmeasurable* to *measured*
on three of the four dimensions and on the aggregate.

### The two real source-side shapes

With the refresh in hand, AERF has now observed persistence contexts in
two real codebases, and they disagree about the source:

| Codebase | Relevant | Flagged | Source roles |
|---|---|---|---|
| spring-framework-petclinic (legacy, JDBC) | 8 | **1** | `PERSISTENCE`/`FUNCTION` × 8 |
| spring-petclinic (modern, Spring Data) | 10 | 0 | `PRESENTATION`/`FUNCTION` × 9, `UNKNOWN`/`FUNCTION` × 1 |

The single true positive AERF has ever produced on real code —
`JdbcOwnerRepositoryImpl#loadOwnersPetsAndVisits` calling
`#loadPetsAndVisits` inside a loop — has a **`PERSISTENCE` source**. It is
an intra-repository N+1, which is the textbook form of the defect.

### Why each candidate restriction fails

- **"Source must not be `PERSISTENCE`"** (exclude intra-repository calls):
  deletes the only true positive, taking the legacy scan from `0.125` to
  *undefined* — it removes numerator and denominator together.
- **"Source must be `PRESENTATION`/`APPLICATION`"** (only cross-layer calls
  count): same outcome, and it would additionally discard the
  `UNKNOWN`-sourced context in the modern scan — silently shrinking the
  denominator exactly when role inference is incomplete, which inverts the
  project's rule that incomplete evidence must stay visible.
- **"Source must plausibly iterate"**: the iteration signal already lives
  on the edge's own provenance (`ExecutionContext.ITERATED`), placed there
  by the extractor that actually saw the loop. No property of the calling
  *node* carries loop information at all. Any source-node proxy would be
  strictly less precise than the signal already in use.

### What §4.3 actually says

"Repeated persistence operations within iteration or repeated execution
contexts" constrains *what* is repeated and *in what context*. It does not
constrain *who* repeats it. The current implementation matches the
specification as written; the proposed restriction would narrow it.

## The test gap this exposed

The 13 pre-existing tests all used an `APPLICATION` or `PRESENTATION`
source. **Not one exercised a `PERSISTENCE` source** — the exact shape
that produces AERF's only real finding. The behaviour was correct but
accidental: nothing would have failed if a future change had quietly
started filtering on source role.

Six tests added to
`aerf-analysis/src/test/java/org/aerf/analysis/metrics/persistence/PersistenceEntropyCalculatorTest.java`:

1. `persistenceToPersistenceIteratedCallIsFlagged` — the legacy petclinic shape.
2. `persistenceToPersistenceNonIteratedCallStillCountsInTheDenominator`.
3. `sourceRoleDoesNotAffectRelevanceOrValue` — the anti-regression pin: the same call measured across **every** `Role` constant, asserting identical relevance and value each time.
4. `sourceNodeTypeDoesNotAffectRelevance` — the "plausibly iterates" half, across `FUNCTION`/`COMPONENT`/`SCRIPT`.
5. `presentationSourcedRepositoryCallsAreRelevantButUnflaggedWithoutIteration` — the modern petclinic shape, giving a *defined* 0.0.
6. `legacyPetclinicShapedFixtureYieldsOneOverEight` — an exact-value mirror of the committed legacy scan: 8 relevant, 1 flagged, `0.125` plain and weighted.

### One planned test was dropped as structurally impossible

The plan called for a test pinning an apparent asymmetry: the calculator
checks only that the source `instanceof NodeRef.Resolved`, while the
target is additionally looked up via `graph.node(...)`. Reading
`aerf-model`'s `Graph` showed such a graph cannot be built: `Graph` has a
**private** constructor, and `Graph.Builder.addEdge` calls
`requireKnownIfResolved` on *both* endpoints. A resolved reference to an
absent node is rejected at construction. The asymmetry is therefore
defensive only, unreachable through the public API, and untestable without
reflection. Recorded here rather than faked.

## Scope check

- No production file changed. `PersistenceEntropyCalculator` is byte-for-byte
  as it was.
- **Not a measurement change** (backlog §10.3): no relation, metric or
  policy input was added, so `AnalysisConfidence` and every calculator's
  `relevantRelations` are untouched. The existing exact-value assertions
  (`PipelineTest` 5/7, 1.0, 0.5; `AnalysisConfidenceTest` 0.8) pass
  unmodified.
- OQ-09's "batched or otherwise justified" exception clause remains
  unimplemented and out of scope, as the calculator's own javadoc already
  records.

## Increment report

```
Increment:      22 — persistence source-scope decision (OQ-08)
Question(s):    OQ-08. Should the N+1 heuristic constrain the source side?
Decision:       No. Reject the restriction; pin current behaviour with tests.
Why:            The only true positive on real code has a PERSISTENCE source;
                every candidate restriction discards it or shrinks the
                denominator when role inference is incomplete. The iteration
                signal already sits on edge provenance, which is more precise
                than any source-node proxy.
Files changed:  PersistenceEntropyCalculatorTest.java (+6 tests);
                scripts/push-scan/sample-reports/spring-petclinic-rescan-post-increment-20.json (new);
                docs/increment-22-persistence-source-scope-decision.md (new);
                docs/post-v0.4.1-backlog-status.md (OQ-08 row).
                No production source changed.
Tests added:    6 (13 -> 19 in that class; 237 -> 243 in the reactor)
Full test cmd:  mvn -B test
Test result:    243 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None. Not a measurement change; no relation/metric/policy
                input added; existing exact-value assertions unmodified.
Real-repo:      spring-petclinic re-scanned on current code (818c413, 170-jar
                classpath); spring-framework-petclinic report re-read.
Docs updated:   This file; backlog status tracker.
Deferred kept:  OQ-09 (justified-access exceptions) untouched; no source-side
                filtering introduced "for later".
Commit:         see git log for this increment
```
