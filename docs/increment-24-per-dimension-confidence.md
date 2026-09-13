# Increment 24 — Per-Dimension Confidence

Post-v0.4.1 implementation phase, resolving **OQ-13**. This is the only
measurement change in tier 1, so backlog §10.3's checklist applies in full.

## Objective

Decide whether confidence should also exist per entropy dimension, rather
than only as the single graph-wide figure `AnalysisConfidence` produces —
"so a reader can tell which dimension a low confidence actually came from"
— and implement it as far as the JSON report, no further.

## Decision

**Adopt, for the three relation-based dimensions. Security is explicitly
undefined.**

`AnalysisConfidence` stays exactly as it is. A new sibling,
`DimensionConfidence.forRelations(Graph, Set<RelationType>)`, answers the
narrower question, and each calculator exposes one instance method
`OptionalDouble confidence(Graph)` that supplies its own relation scope.

### Numerator and denominator

- **Denominator** — edges whose relation is in that dimension's set.
- **Numerator** — those edges with both endpoints resolved, using the
  *same* resolution test `AnalysisConfidence` applies. There is one
  definition of "resolved" in this codebase, not two.
- **Empty denominator → `OptionalDouble.empty()`**, never `1.0`. A
  dimension that measured nothing has no confidence reading; it does not
  have perfect confidence.

Both bounds hold by construction: the numerator is a filtered subset of
the denominator, so the value is in [0,1] without clamping — the same
property every primary `value()` in §4 has.

### Why role and policy filters are excluded from both sides

Each dimension narrows further than its relation set before computing
entropy: layer drops edges whose roles the policy does not know,
persistence keeps only calls whose *target* is PERSISTENCE. Neither filter
enters confidence, on either side.

- **Amendment 7 already settled the layer case**: a node's role outcome
  must never affect confidence. `layerConfidenceIgnoresTheRolePolicyFilter`
  pins it.
- **Persistence would otherwise be trivially 1.0.** An edge cannot be
  selected by its target's role unless that target resolved — so folding
  that filter into the denominator would make the numerator equal the
  denominator by construction, reporting perfect confidence precisely
  where unresolved calls are the thing a reader needs to see.
  `persistenceConfidenceIgnoresTheTargetRoleFilterSoItIsNotTriviallyOne`
  pins it: one resolved persistence call and one unresolved call give a
  relevant-edge count of 1 but a confidence of 0.5.

### Why security is undefined rather than 1.0

`SecurityEntropyCalculator` measures over `graph.nodes()`, and a node is
present by construction — there is no resolved/unresolved population to
measure at all. A ratio over that set would be a degenerate `1.0`
asserting certainty that was never measured, which is exactly the
coercion §5.4 forbids. `confidence(Graph)` returns
`OptionalDouble.empty()` and the JSON carries `null`.

That asymmetry is honest rather than a hole: undefined is first-class
throughout this codebase, and `perDimensionConfidenceKeepsUndefinedDistinctFromZero`
pins that a real `0.0` (every reference this dimension measured failed to
resolve) still serializes distinctly from it.

### Why an instance method rather than a public `relevantRelations()`

Exposing each calculator's relation set would widen the public API and
invite callers to re-derive confidence themselves — a derivation that can
drift from `compute()` the moment a scope changes. Asking the calculator
keeps one owner for the question "what evidence is mine?".

## Measurement-change checklist (backlog §10.3)

| Check | Result |
|---|---|
| `AnalysisConfidence` inspected | Untouched. `graphWideConfidenceIsUnchangedByAnyOfThis` pins its semantics. |
| Every calculator's `relevantRelations` inspected | All four read; none changed. Layer/cycle = {CALL, DEPENDS}, persistence = {CALL}, security = none. |
| Existing exact-value pipeline tests run unmodified | Yes — `PipelineTest`'s 5/7 confidence, 1.0 layer and 0.5 persistence assertions pass verbatim; `AnalysisConfidenceTest`'s 0.8 fixture passes verbatim. |
| Numerator/denominator regression tests added | `outOfScopeRelationsMoveNeitherNumeratorNorDenominator`, plus the two filter-exclusion pins above. |
| Before/after comparison on a representative graph | Two real scans of spring-petclinic, byte-identical apart from the added key — see Real-repo evidence. |
| No relation, metric or policy input added | Correct. This increment reads existing edges; it adds no `RelationType`, no calculator input, no policy surface. |

### The `MEMBER_OF` pin

Increment 21 added `MEMBER_OF` and it silently moved graph-wide confidence
0.714 → 0.857, because such edges are always resolved by construction.
That is why this checklist exists. Per-dimension confidence excludes
`MEMBER_OF` *structurally* — no dimension's relation set contains it — so
no special case was needed, but
`memberOfEdgesEnterNoDimensionsDenominator` asserts the exclusion is real
rather than incidental, and fails if any dimension ever takes it in.

On real code this is not a marginal effect: **93 of spring-petclinic's
353 edges are `MEMBER_OF`**. Had they entered the denominators, the
reported figure would have been 0.547 instead of 0.385.

## Reachability at the architectural boundary

Not reported complete on unit tests alone — the V04-CAL-02 lesson, where
a tested-but-unreachable formula was wrongly called done. Verified by
running the CLI end to end:

```
$ java -cp ... org.aerf.pipeline.Main aerf-pipeline/src/test/resources/defect-sample
"confidence": 0.7142857142857143
"confidenceByDimension": {"layer":0.6,"cycle":0.6,"persistence":0.6666666666666666,"security":null}
```

5/7, 3/5, 3/5, 2/3, undefined — matching the unit-test fractions exactly,
reached through `Pipeline` → `PipelineReport` → `CalibrationJson` →
`Main.toJson` with no test-only path involved.

## Real-repo evidence

`scripts/push-scan/sample-reports/spring-petclinic-rescan-post-increment-24.json`,
a fresh scan of spring-petclinic, stored alongside the increment-20 rescan
rather than replacing it.

```
confidence            0.38461538461538464   (100/260)
confidenceByDimension layer       0.3770491803278688  (92/244  = 23/61)
                      cycle       0.3770491803278688  (92/244  = 23/61)
                      persistence 0.38636363636363635 (85/220  = 17/44)
                      security    null
```

Each fraction was recomputed independently from the emitted graph and ties
out. The two relation-based scopes differ in denominator (244 vs 220) and
still land close together here, which is itself the useful reading: on this
repository the low confidence is *uniform*, not one dimension dragging the
graph-wide figure down — a distinction the single 0.385 could not express.

**Regression evidence.** Diffed against the increment-20 rescan after
normalizing the clone path: `layerEntropy` 0.667, `cycleEntropy` 0.0,
`persistenceEntropy` 0.0, `securityEntropy` undefined, `totalEntropy`
0.222, `maturity` 0.778, `L3_CONTROLLED`, `confidence` 0.385, 118 nodes,
353 edges — **byte-identical, in the same order, with `confidenceByDimension`
the only added content.** Two independent runs from different directories
four increments apart, so this incidentally re-confirms extraction and
edge-ordering determinism as well.

## Scope

Java plus the JSON report, and it stops there. No Supabase column, no
dashboard change, no scan-row schema change (backlog §10.2 and the
scoping decision taken for this phase). `confidenceByDimension` is an
*additive* top-level key placed beside the existing flat `"confidence"`,
which keeps its meaning and position; no existing consumer has to change.

`PipelineReport`'s new component wraps its map in
`Collections.unmodifiableMap(new LinkedHashMap<>(...))`, not `Map.copyOf`,
which does not preserve iteration order — the Increment 1 gotcha.
`perDimensionConfidencePreservesTheOrderItWasGiven` pins the writer's half
of that.

## Increment report

```
Increment:      24 — per-dimension confidence (OQ-13)
Question(s):    OQ-13. Should confidence also exist per entropy dimension?
Decision:       Adopt for the three relation-based dimensions, scoped to each
                calculator's own relation set, with role/policy filters excluded
                from both numerator and denominator. Security is explicitly
                undefined, not 1.0. Graph-wide AnalysisConfidence unchanged.
Why:            The three relation-based dimensions measure over edges, which
                carry a real resolved/unresolved axis; security measures over
                nodes, which are present by construction, so a ratio there
                would assert unmeasured certainty. Excluding the role filters
                is required by Amendment 7 and keeps persistence confidence
                from being trivially 1.0.
Files changed:  DimensionConfidence.java (new); LayerEntropyCalculator.java,
                CycleEntropyCalculator.java, PersistenceEntropyCalculator.java,
                SecurityEntropyCalculator.java (one confidence(Graph) method
                each); PipelineReport.java (one component);  Pipeline.java
                (calculators hoisted to locals, map built in dimension order);
                CalibrationJson.java (writer); Main.java (one additive key);
                DimensionConfidenceTest.java (new); PipelineTest.java,
                CalibrationJsonTest.java; docs/increment-24-*.md (new);
                backlog status tracker; post-increment-24 sample scan.
Tests added:    15 (251 -> 266 in the reactor): 11 DimensionConfidenceTest,
                1 PipelineTest, 3 CalibrationJsonTest.
Full test cmd:  mvn -B test
Test result:    266 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None observed. The four protected exact-value assertions pass
                unmodified. A real spring-petclinic scan is byte-identical to
                the increment-20 rescan apart from the added key. MEMBER_OF
                enters no dimension's denominator, pinned by test.
Real-repo:      spring-petclinic — layer/cycle 23/61, persistence 17/44,
                security undefined; graph-wide 0.385 unchanged. 93 of 353
                edges are MEMBER_OF and are correctly excluded.
Reachability:   Verified via the CLI, not unit tests alone (V04-CAL-02 lesson).
Docs updated:   This file; backlog status tracker (OQ-13 -> DECIDED-IMPLEMENTED).
Deferred kept:  No storage, API or dashboard surface touched. No new relation,
                metric or policy input. OQ-04/OQ-06 subsystem scoping untouched.
Commit:         see git log for this increment
```
