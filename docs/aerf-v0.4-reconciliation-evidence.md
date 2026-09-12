# AERF v0.4 Contract Reconciliation — Evidence Report

Produced per `docs/aerf-v0.4-contract-reconciliation.md`'s section 13.
Normative sources: *AERF v0.4 — Canonical Pre-Implementation
Documentation* and `docs/reports/aerf-v0.4.1-status-report.pdf` (the
v0.4.1 status report at commit `cc602de`).

---

## Correction record (read this first)

The version of this report produced at commit `060e242` disposed
**V04-CAL-02 (baseline-relative drift, §5.3) as "REMEDIATION REQUIRED →
REMEDIATED"** on the strength of a new, tested `Drift.compute` formula
alone. That classification was **premature and is corrected here**: a
follow-up review asked this report to specifically trace the Increment
19 baseline store through the actual reporting pipeline and check
whether drift was *observable from a stored baseline and a current
scan* — not just computable from hand-built inputs in a unit test. It
was not. Tracing `PipelineReport` → `Main.toJson` → the JSON every
report writer in `aerf-report` produces turned up two real gaps the
first pass missed:

1. **No code anywhere in the reactor turned a `PipelineReport` (or its
   JSON) into the `EntropySnapshot` `Drift.compute` requires.** The
   formula existed in complete isolation from the pipeline that is
   supposed to produce its inputs.
2. **`PipelineReport` and its JSON carry no subject/project
   identifier at all** — `EntropySnapshot.subjectId` had nothing in
   the pipeline's own output to be populated from.

This means the original disposition was wrong at the time it was
written: the correct status then was closer to "REMEDIATION REQUIRED —
partially addressed (formula implemented and unit-tested; not reachable
from an actual scan)," not "REMEDIATED." This is now fixed for real
(see V04-CAL-02 below and commit `8740ea6`), and this report is
corrected to describe both what was wrong and what closes it — per this
process's own rule to never hide a deviation once found, extended here
to a mistake in the report itself, not just in the code it was
reviewing. **No ledger disposition *category* changes as a result**
(V04-CAL-02 is still, correctly, one "REMEDIATION REQUIRED → REMEDIATED"
item out of 21; the bucket counts in 13.2's summary were already
right) — what changes is that the remediation described is now the
real one, and the record says so plainly rather than presenting the
incomplete first pass as finished.

---

## 13.1 Execution summary

| Field | Value |
|---|---|
| Repository | `costinfl/aerf` |
| Branch | `claude/aerf-core-domain-model-gwumun` |
| Commit before any reconciliation work | `89223f039a6b7d6bbd1a4ac73216a3696fdaff12` ("Add the v0.4.1 status report and its generator") |
| Commit after round 1 (initial reconciliation) | `060e24265782af58d4cff4cce27a8b2fcaa3b12a` ("v0.4 contract reconciliation: implement drift, protect entropy bounds and role totality") — **the round whose V04-CAL-02 disposition this report corrects** |
| Commit after round 2 (this correction) | `8740ea6d24039360fc51da6cfd2402c9bdb43cbc` ("Close the real V04-CAL-02 gap: wire Drift to an actual PipelineReport") |
| Intervening commits | `759c738` (adds the reconciliation instructions doc), `ff61fa4` (round 1's evidence report), `d74510c` (register housekeeping) — no code changes |
| Verification date/time (UTC), round 1 | 2026-09-11T21:14:45Z |
| Verification date/time (UTC), round 2 | 2026-09-12T21:23:47Z |
| Java | OpenJDK 21.0.10 (Temurin/Ubuntu build 21.0.10+7) |
| Maven | Apache Maven 3.9.11 |
| Test command (every run below) | `mvn -B test` from repository root |
| Baseline (`89223f0`): total / passed / failed / skipped | 219 / 219 / 0 / 0 |
| After round 1 (`060e242`): total / passed / failed / skipped | 231 / 231 / 0 / 0 |
| After round 2 (`8740ea6`): total / passed / failed / skipped | 237 / 237 / 0 / 0 |
| Build result (all three runs) | `BUILD SUCCESS` |
| Net new tests, round 1 | 12 (`DriftTest` ×8; one new test each in `LayerEntropyCalculatorTest`, `SecurityEntropyCalculatorTest`, `PersistenceEntropyCalculatorTest`, `IterativeRoleInferenceEngineTest`) |
| Net new tests, round 2 | 6 (`DriftJsonTest` ×3; `DriftEndToEndTest` ×1; two new tests in `PipelineTest`) |

All counts are taken directly from `mvn -B test` output, not estimated.
Each run was executed on a clean tree immediately after committing that
round's changes, confirmed with `git status --short` before running.

One additional command was run and its output is evidence for
V04-PIPE-03 specifically, not part of either full-suite run:
`mvn -pl aerf-model -B validate`, executed twice — once with a
temporary `org.openrewrite:rewrite-java:8.90.4` dependency added to
`aerf-model/pom.xml` (result: `BUILD FAILURE`, enforcer rule
`BannedDependencies` failed, see 13.2 V04-PIPE-03), once after
reverting that change with `git checkout -- aerf-model/pom.xml`
(result: `BUILD SUCCESS`). `git status --short aerf-model/pom.xml`
was empty both before the experiment and after the revert.

No test was skipped, weakened, or deleted to obtain a green build.
No test failed at any point in this session.

---

## 13.2 Reconciliation ledger

Dispositions use the exact vocabulary from
`docs/aerf-v0.4-contract-reconciliation.md` §2.

| ID | Status | Existing test(s) | New test(s) | Implementation change | Evidence |
|---|---|---|---|---|---|
| V04-CORE-01 | CONFORMING | `aerf-model`: `NodeTest.unknownRoleIsAFirstClassOutcomeNotAnError`, `EdgeTest.edgeWithResolvedEndpointsCarriesRelationAndProvenance`, `GraphTest.*`; `aerf-analysis`: `SeedRoleInferenceEngineTest.nodeWithNoMatchingEvidenceIsUnknownRatherThanGuessed` | none | none | `NodeType` (7 values), `Role` (7 values incl. `UNKNOWN`), `RelationType` match the v0.4 §2.2/§2.3 lists exactly (verified by direct read of `aerf-model/src/main/java/org/aerf/model/{NodeType,Role,RelationType}.java`). "Metrics remain derived data, not AST objects" is the §9 boundary, verified separately under V04-PIPE-03. |
| V04-GRAPH-02 | CORRECTED BY v0.4.1 | `aerf-openrewrite`: `JavaSourceExtractorTest.emitsAMemberOfEdgeFromEachMethodToItsDeclaringClass` | none | none (already implemented, Increment 21) | `RelationType.MEMBER_OF` exists; `JavaSourceExtractor.memberOfEdge(...)` emits one per declared method; documented as AERF v0.4.1 patch Amendment 6 in `docs/aerf-v0.4.1-patch.md`. Retained per the reconciliation instructions' explicit "do not remove `MEMBER_OF`" directive. |
| V04-ROLE-01 | CONFORMING | Determinism: `SeedRoleInferenceEngineTest.inferenceIsDeterministicAcrossRepeatedRuns`, `IterativeRoleInferenceEngineTest.inferenceIsDeterministicAcrossRepeatedRuns`. Explainability: `RoleInferenceResult.signals()` / `RoleSignal.rationale()` (structural — every result carries the firing rule names and rationale strings), demonstrated by `SeedRoleInferenceEngineTest.conflictingSignalsAreResolvedByTheSection34PrecedenceOrder` and `IterativeRoleInferenceEngineTest.conflictingSupertypeRolesAreResolvedByPrecedenceJustLikeSeedSignals` | `IterativeRoleInferenceEngineTest.everyNodeReceivesARoleIncludingNodesThatStayUnknown` | test only | Totality (§3: "every node has a role") was true by construction (`inferAll`/`infer` iterate every graph node and always put a result) but had no test asserting it directly before this session. The new test constructs a graph with one classifiable and one genuinely unclassifiable node and asserts both appear in the result map with a non-null role, the unclassifiable one landing on `UNKNOWN`. |
| V04-ROLE-02 | CORRECTED BY v0.4.1 | `IterativeRoleInferenceEngineTest.aTwoHopInheritanceChainNeedsTwoRefinementPassesToFullyResolve`, `.refinementNeverOverridesAnAlreadyKnownRoleEvenViaAnUnrelatedSupertype`, `.aLongerChainStillTerminatesWithinTheStructuralBound` | none | none | Graph-relationship refinement uses `InheritRoleFromSupertype` (EXTENDS/IMPLEMENTS). Monotonic-only scoping and the `\|V\|+1`-pass termination bound are documented as Amendment 4 in `docs/aerf-v0.4.1-patch.md` and enforced structurally by `IterativeRoleInferenceEngine`'s own contract (a rule is only ever invoked for an `UNKNOWN` node and may only return a concrete role — see the class's own javadoc and `GraphRoleRefinementRule`'s interface contract). |
| V04-ENT-01 | CONFORMING | `LayerEntropyResult.value()`, `CycleEntropyResult.value()`, `PersistenceEntropyResult.value()`, `SecurityEntropyResult.value()` are each `numerator/denominator` with `numerator` a subset of `denominator` by construction (verified by reading all four `*Result.value()` implementations) | `LayerEntropyCalculatorTest.allRelevantEdgesViolatingGivesTheUpperBoundOfOne`, `SecurityEntropyCalculatorTest.allOpportunitiesFlaggedGivesTheUpperBoundOfOne`, `PersistenceEntropyCalculatorTest.weightedValueIsNotBoundedToOneUnlikeThePlainRatio` (plus pre-existing `CycleEntropyCalculatorTest.selfCycleCountsWhenExplicitlyGoverned` and `PersistenceEntropyCalculatorTest.anIteratedCallToAPersistenceTargetIsFlagged`, both already exact-value 1.0 cases) | test only | The `[0,1]` bound held by construction for every dimension's primary `value()` but had no test pinning the upper boundary for Layer or Security before this session. Scope note (see 13.4): `PersistenceEntropyResult.weightedValue()` (Amendment 3's distinct "evidence-weighted" score) is explicitly **not** part of this bound — its own javadoc states it is "strictly greater [than `value()`] whenever a flagged edge is backed by more than one independently observed iterated call site." The new `weightedValueIsNotBoundedToOneUnlikeThePlainRatio` test proves this with an exact value (3.0) rather than leaving it asserted only in prose. |
| V04-ENT-02 | CORRECTED BY v0.4.1 | `LayerEntropyCalculatorTest.fixtureGraphHasExactlyOneViolationOutOfFourRelevantEdges`, `.graphWithNoRelevantEdgesYieldsAnUndefinedNotZeroValue`, `.anEdgeBetweenRolesUnknownToThePolicyIsExcluded`; `LayerPolicyTest.isAllowedReflectsTheDeclaredMatrixExactly` | none | none | `LayerPolicy` accepts only an explicit matrix (constructor takes `Map<Role, Set<Role>>`, no ordering-derivation code path exists anywhere in `LayerPolicy.java` or `LayerEntropyCalculator.java` — confirmed by reading both files in full). Amendment 2. |
| V04-ENT-03 | CONFORMING + IMPROVEMENT | `CycleEntropyCalculatorTest.twoNodeCyclePlusOneIsolatedNodeGivesTwoThirds`, `.aDagHasZeroCycleEntropyDefinedNotUndefined`, `.selfCycleIsExcludedByDefault`, `.selfCycleCountsWhenExplicitlyGoverned`; `StronglyConnectedComponentsTest.aVeryDeepLinearChainDoesNotOverflowTheStack`, `.aVeryDeepChainFeedingIntoACycleAtItsEndIsStillFoundCorrectly` | none | none | Formula and trivial-SCC exclusion conform to §4.2 exactly. The iterative (non-recursive) `StronglyConnectedComponents` rewrite (Increment 17) is retained as an internal implementation improvement: it changes no externally observable behavior (same inputs produce the same `CycleEntropyResult`) while eliminating a real `StackOverflowError` risk on deep graphs — `aVeryDeepLinearChainDoesNotOverflowTheStack` is the regression test guarding this improvement, per the "add a regression test only if the improvement could be accidentally lost" instruction. |
| V04-ENT-04 | CORRECTED BY v0.4.1 | `EvidenceTest.executionContextDefaultsToUnknownNotSingle`, `.executionContextCanBeSetExplicitly`; `PersistenceEntropyCalculatorTest.anIteratedCallToAPersistenceTargetIsFlagged`, `.aSingleExecutionCallToAPersistenceTargetIsNotFlagged`, `.evidenceWithNoExecutionContextClaimIsNotFlagged`, `.weightedValueCountsMultipleIteratedEvidenceItemsOnTheSameEdge`, `.weightedValueEqualsPlainValueWhenEveryFlaggedEdgeHasExactlyOneIteratedItem` | none | none | `ExecutionContext{SINGLE,ITERATED,UNKNOWN}` (Amendment 1) and evidence-weighted scoring (Amendment 3) both implemented and tested exactly as the amendments specify. |
| V04-ENT-05 | CONFORMING (implemented scope) / DEFERRED BY CONTRACT (CSRF, mass-assignment) | `DefaultSecurityRulesTest.unescapedOutputIsFlaggedAsAWeakness`, `.escapedOutputIsAnOpportunityButNotAWeakness`, `.aViewNodeWithNoRenderingEvidenceIsNotAnOpportunityAtAll`, `.aNonViewNodeIsNeverApplicable`; `SecurityEntropyCalculatorTest.oneFlaggedAmongThreeOpportunitiesGivesOneThird`, `.findingsRetainTraceableRationale` | none | none | "Technology presence alone is not a violation" is proven directly: `escapedOutputIsAnOpportunityButNotAWeakness` shows a rendering technology present with a control in place produces an opportunity but no finding; `aViewNodeWithNoRenderingEvidenceIsNotAnOpportunityAtAll` shows no evidence at all produces no opportunity, not a false negative "weak" result. CSRF/mass-assignment: confirmed absent by reading `aerf-analysis/src/main/java/org/aerf/analysis/metrics/security/` in full — only `DefaultSecurityRulesTest`'s XSS-shaped rule exists. This is open-questions-register #10, unchanged by this session per the "preserve unresolved questions" instruction. |
| V04-ENT-06 | DEFERRED BY CONTRACT | n/a | none | none | `grep -rli "modal" --include=*.java .` (excluding `target/`) returns zero matches anywhere in the reactor. No `NodeType`/evidence concept for modal implementations exists. Confirmed as intentionally out of the MVP freeze (v0.4 §11), not an oversight. |
| V04-ENT-07 | DEFERRED BY CONTRACT | n/a | none | none | `grep -rli "jstl\|webflow\|tiles" --include=*.java .` returns one hit: a comment in `aerf-pipeline/src/main/java/org/aerf/pipeline/Main.java` line 76 ("section 11 defers JSP/WebFlow...") acknowledging the deferral — no extraction or metric code exists. Matches the reconciliation instructions' explicit direction not to invent this. |
| V04-CAL-01 | CONFORMING | `CalibrationProfileTest.weightsSummingToOneAreAccepted`, `.weightsNotSummingToOneAreRejected`, `.aZeroWeightedDimensionIsAllowedAsLongAsTheTotalIsOne`, `.aNegativeWeightIsRejectedAtTheDimensionItself`; `AggregatedEntropyTest.computesTheWeightedSumWithLinearCalibration`, `.appliesEachDimensionsOwnCalibrationFunction`, `.anUndefinedWeightedDimensionMakesTheAggregateUndefined`, `.aZeroWeightedDimensionBeingUndefinedDoesNotBlockAggregation`, `.aMissingKeyInTheValueMapIsTreatedTheSameAsUndefined`; `CalibrationFunctionsTest.*`; `CalibrationIntegrationTest.aggregatesAllFourMvpDimensionsAndClassifiesMaturity` | none | none | `CalibrationProfile`'s constructor enforces `sum(w_d) == 1.0` (tolerance `1e-9`) or throws — an invalid configuration cannot be used at all, stronger than the bare formula in §5.1. No universal weights or thresholds are asserted anywhere in the codebase (`CalibrationFunction`'s own javadoc: "No implementation of this interface should be selected or parameterized as a default"). |
| V04-CAL-02 | **REMEDIATION REQUIRED → REMEDIATED** (corrected; see the Correction record above — round 1 alone was insufficient) | none existed | Round 1: `DriftTest` (8 tests). Round 2: `PipelineTest.entropyByDimensionMatchesEachResultsOwnValueUnderPipelinesOwnDimensionNames`, `.toEntropySnapshotCarriesTheGivenSubjectIdAndTheSameValuesAsEntropyByDimension`; `DriftJsonTest` (3 tests: `oneDimensionSerializesItsBaselineCurrentAndDelta`, `anEmptyDriftMapSerializesAsAnEmptyObject`, `multipleDimensionsPreserveInsertionOrder`); `DriftEndToEndTest.aRealPipelineReportProducesNonZeroDriftAgainstAPriorBaselineAndSerializesToJson` — the actual end-to-end proof | Round 1 added `aerf-analysis/.../{EntropySnapshot,DimensionDrift,Drift}.java` (the formula, in isolation). Round 2 added: `PipelineReport.entropyByDimension()` (the four-dimension map `Pipeline.run` already builds internally for `AggregatedEntropy`, reconstructed from the report's own stored results); `PipelineReport.toEntropySnapshot(subjectId)` (packages a real report as `Drift`'s input); `aerf-report`'s `DriftJson` (serializes a computed drift map, matching every other metric's writer). Also fixed a real latent bug round 1 introduced: `EntropySnapshot`'s constructor used `Map.copyOf`, which — per this project's own documented Increment 1 regression, cited in `aerf-v0.4.1-patch.md`'s non-normative note — does not guarantee preserving a source map's iteration order; left as-is, a `PipelineReport`'s drift could have serialized its dimensions in a different order on different JVM invocations of the identical input, violating §14. Now wrapped in an explicit `LinkedHashMap`. | **Traced the actual path, not just the formula.** `Main.toJson(PipelineReport)` (`aerf-pipeline/src/main/java/org/aerf/pipeline/Main.java`) was read in full: it serializes `layerEntropy.value`, `cycleEntropy.value`, `persistenceEntropy.value`, `securityEntropy.value` under each metric's own nested key — the raw numbers `Drift` needs are present in every report — but nothing in `aerf-report` reads JSON back (confirmed: `aerf-report/src/main/java/org/aerf/report/` contains eight files, every one a writer — `JsonWriter`, `MetricsJson`, `CalibrationJson`, `InvariantJson`, `GraphJson`, `JsonObjectBuilder`, `JsonValue`, `JsonSupport` — no reader/parser anywhere), and `PipelineReport`'s record components (`graph`, `roleRefinementPasses`, four entropy results, calibration values, invariants, diagnostics) include no project/subject identifier at all. Round 1's `Drift.compute` was therefore correct in isolation but unreachable from a real scan — there was no code path from "a `PipelineReport` just came out of `Pipeline.run`" to "here are two `EntropySnapshot`s to diff." Round 2 closes exactly that gap without adding any storage/API layer (the boundary `Drift`'s own javadoc already drew and this round did not cross): `entropyByDimension()`/`toEntropySnapshot()` work only on a `PipelineReport` already in hand, `subjectId` is caller-supplied rather than invented, and `DriftEndToEndTest` proves the whole chain with a genuine `Pipeline.run` output on one side (asserting its real, live-computed values — layer `1.0`, persistence `0.5` — match `PipelineTest`'s own known fixture numbers) rather than two hand-built snapshots. |
| V04-CAL-03 | CORRECTED BY v0.4.1 | `AnalysisConfidenceTest.anEmptyGraphIsUndefinedNotZeroOrOne`, `.allResolvedEdgesGiveFullConfidence`, `.oneUnresolvedEdgeOutOfTwoGivesOneHalf`, `.memberOfEdgesAreExcludedEntirelyEvenWhenUnresolved`, `.theFixtureGraphHasFourOfFiveEdgesResolved` | none | none | `AnalysisConfidence.compute` excludes `RelationType.MEMBER_OF` from both numerator and denominator (Amendment 6) and never inspects a node's `Role` at all (Amendment 7) — confirmed by reading the full method body. `memberOfEdgesAreExcludedEntirelyEvenWhenUnresolved` specifically proves the exclusion is unconditional (an artificially *unresolved* `MEMBER_OF` edge still contributes nothing), not merely moot because real `MEMBER_OF` edges happen to always resolve. |
| V04-CAL-04 | CONFORMING | `MaturityTest.isOneMinusTotalEntropy`, `.isUndefinedWhenTotalEntropyIsUndefined`, `.classifiesBelowFortyAsChaotic`, `.classifiesTheInteriorBoundariesAsLowerInclusive`, `.classifiesExactlyNinetyAsControlledNotOptimized`, `.classifiesAboveNinetyAsOptimized` | none | none | `Maturity.compute` is exactly `1 - E_total`, undefined when `E_total` is undefined. `MaturityLevel.classify` implements all five provisional levels with the two explicit strict boundaries (`< 0.40`, `> 0.90`) honored exactly. The interior-boundary tie-break is a documented deviation — see 13.4. |
| V04-GOV-01 | CONFORMING | `InvariantEvaluatorNodeScopeTest.aDataNodeWithAKnownRolePasses`, `.aDataNodeWithUnknownRoleViolates`, `.eachViolatingNodeIsReportedSeparately`; `InvariantEvaluatorEdgeScopeTest.theFixtureGraphHasExactlyOneViolationTheDeliberateOne`; `InvariantEvaluatorGraphScopeTest.underBudgetHolds`, `.overBudgetViolatesAsASingleGraphLevelFinding` | none | none | `InvariantViolation` carries evidence (the violating node/edge/graph-level subject); a satisfied invariant reports no violations (`aHoldingInvariantSerializesAnEmptyViolationsArray` in `InvariantJsonTest`, cross-referenced under V04-REPORT-01). §6.1's weighted `E_inv` aggregation is explicitly not implemented — that is open-questions-register #15, listed as DEFERRED in 13.5, not silently added here. |
| V04-GOV-02 | CONFORMING | `InvariantEvaluatorNodeScopeTest.*` (NODE), `InvariantEvaluatorEdgeScopeTest.*` (EDGE, references source/target), `InvariantEvaluatorGraphScopeTest.*` (GRAPH, references graph-level metrics); `PredicateEvaluationTest.andRequiresAllOperands`, `.orRequiresAnyOperand`, `.notInvertsItsOperand`, `.equalsComparesResolvedValues`, `.orderingComparisonsWorkOnNumbers`, `.orderingComparisonOnNonNumericValuesThrows`, `.membershipMatchesAnyCandidate`, `.combinedExpressionEvaluatesCorrectly`, `.indicatorValueMatchesSection61Definition` | none | none | `Scope` is exactly `{NODE, EDGE, GRAPH}`. `Predicate` is a `sealed interface` with exactly the operator set §6.2 asks for (`Equals`, `NotEquals`, `LessThan`, `LessThanOrEqual`, `GreaterThan`, `GreaterThanOrEqual`, `In`, `And`, `Or`, `Not`, `Always`) — no variable binding, function call, or loop construct exists anywhere in the type, which is what makes "non-Turing-complete and bounded" true by construction rather than by a runtime check. This absence-of-a-feature property cannot itself be unit-tested (there is nothing to invoke); the closed, exhaustive operator set is what `PredicateEvaluationTest` verifies instead. Determinism follows from every `Predicate`/`ValueExpression` evaluation being a pure function of its inputs with no shared mutable state (confirmed by reading `InvariantEvaluator.java` in full) — not separately re-tested here since nothing in the evaluation path could introduce non-determinism. |
| V04-PIPE-01 | CONFORMING | `PipelineTest.findsTheDirectPresentationToPersistenceLayeringViolation`, `.findsTheNPlusOneLoop`, `.resolvesTheThreeLevelInheritanceChainOnlyAfterTwoRefinementPasses`, `.producesADefinedTotalEntropyAndMaturityWithConfidenceReflectingTwoJdkBoundaryReferences`, `.aGraphScopeInvariantIsSkippedRatherThanCrashingWhenItsMetricIsUndefined`, `.extractionProducesNoDiagnostics` | none | none | `Pipeline.run` (`aerf-pipeline/src/main/java/org/aerf/pipeline/Pipeline.java`) composes extraction → `GraphAssembler` → `IterativeRoleInferenceEngine` → four entropy calculators → `AggregatedEntropy`/`Maturity`/`AnalysisConfidence` → `InvariantEvaluator`, in that literal method-call order — confirmed by reading the full method body. `PipelineTest` exercises this against a real, purpose-built defect sample and asserts exact, analytically-derived findings at every stage, not just that the pipeline runs. |
| V04-PIPE-02 | CONFORMING (implemented Java/Spring scope); DEFERRED BY CONTRACT (JSP/WebFlow/Tiles/JavaScript) | `JavaSourceExtractorTest.recordsTheUndeclaredSupertypeAsUnresolvedL1Syntax`, `.recordsACallToAnUnresolvableMethodAsL1SyntaxUnresolved`, `.recordsJdkImplementsTargetsAsL2ResolvedFidelityButGraphUnresolved`; `GraphAssemblerTest.*` (provenance retained through fact→graph resolution) | none | none | "Adapter does not invent unsupported relations": both cited tests show an unresolvable reference becomes `NodeRef.unresolved(...)` carrying its as-written description, never a fabricated node — the same conservatism principle verified for `MEMBER_OF` under V04-GRAPH-02. Full JSP/WebFlow/Tiles/JS extraction: confirmed absent (same grep as V04-ENT-07); deferred per v0.4 §11, unchanged here. |
| V04-PIPE-03 | CONFORMING | POM inspection: `aerf-model/pom.xml`, `aerf-analysis/pom.xml`, `aerf-report/pom.xml`, `aerf-extraction/pom.xml` each carry a `maven-enforcer-plugin` `ban-openrewrite` execution excluding `org.openrewrite:*`, with `<fail>true</fail>` | **New evidence this session**: a real negative-control experiment (not merely POM inspection) — see 13.1 | none (POMs already correct; experiment reverted, no change retained) | `mvn -pl aerf-model -B validate` with a temporary `org.openrewrite:rewrite-java:8.90.4` dependency added produced `BUILD FAILURE` with `Rule 0: org.apache.maven.enforcer.rules.dependency.BannedDependencies failed`, naming the exact banned artifact and its transitive closure (`rewrite-core`, `rewrite-yaml`, `rewrite-properties`, `rewrite-xml`). After `git checkout -- aerf-model/pom.xml`, the same command produced `BUILD SUCCESS` with the rule explicitly reported as `passed`. **Precision note** (see 13.4): `aerf-pipeline` and `aerf-openrewrite` are *not* banned — necessarily, since `aerf-pipeline` depends on the adapter and `aerf-openrewrite` *is* the adapter — confirmed by reading both POMs, which document this exemption in their own comments. The v0.4.1 status report's summary phrase "banned from every module except the adapter" is imprecise (it is every module except the adapter *and* the pipeline that composes it); the boundary itself — the canonical graph never exposes an OpenRewrite type — is intact regardless. |
| V04-REPORT-01 | CONFORMING | `InvariantJsonTest.theFixtureGraphsViolationSerializesWithFullEdgeEvidence`, `.aHoldingInvariantSerializesAnEmptyViolationsArray`; `CalibrationJsonTest.aDefinedTotalEntropySerializesAsANumber`, `.anUndefinedTotalEntropySerializesAsNullNotZero`, `.maturityLevelSerializesAsItsName`; `GraphJsonTest.*`; `MetricsJsonTest.*`; `JsonWriterTest.*` | none | none | Findings retain evidence: `theFixtureGraphsViolationSerializesWithFullEdgeEvidence` confirms a violation's full edge (and, through it, evidence) round-trips into JSON, not just a bare pass/fail. Uncertainty stays visible: `anUndefinedTotalEntropySerializesAsNullNotZero` confirms an undefined metric serializes as JSON `null`, never a silently-substituted `0`. |

**Ledger summary:** 21 items. 12 CONFORMING (some with a DEFERRED-BY-CONTRACT sub-scope noted inline: V04-ENT-05, V04-PIPE-02), 6 CORRECTED BY v0.4.1, 2 DEFERRED BY CONTRACT outright (V04-ENT-06, V04-ENT-07), 1 CONFORMING + IMPROVEMENT (V04-ENT-03), 1 REMEDIATION REQUIRED → remediated (V04-CAL-02). These bucket counts are unchanged from round 1 — the correction recorded above changed *what actually satisfies* V04-CAL-02's REMEDIATED status, not which bucket it sits in. Zero items are UNVERIFIED and zero are OPEN/UNDECIDED.

---

## 13.3 BDD / contract coverage

Every scenario from `docs/aerf-v0.4-contract-reconciliation.md`, mapped to the real test that covers it (this project does not use a Gherkin/Cucumber runner — every mapping below is to a JUnit 5 `@Test` method, matching the codebase's actual, existing testing convention rather than inventing a BDD framework it does not have).

| Feature / Scenario | Mapped test |
|---|---|
| Canonical graph model / Represent architecture as a typed directed multigraph | `EdgeTest.edgeWithResolvedEndpointsCarriesRelationAndProvenance`, `GraphTest.nodeIterationOrderIsInsertionOrderNotHashOrder` (structural, no AST leakage: enforced separately, see OpenRewrite separation row below) |
| Canonical graph model / Unknown is a valid architectural role | `NodeTest.unknownRoleIsAFirstClassOutcomeNotAnError`, `SeedRoleInferenceEngineTest.nodeWithNoMatchingEvidenceIsUnknownRatherThanGuessed` |
| Structural membership / Record function membership | `JavaSourceExtractorTest.emitsAMemberOfEdgeFromEachMethodToItsDeclaringClass` |
| Role inference contract / Identical evidence produces identical roles | `SeedRoleInferenceEngineTest.inferenceIsDeterministicAcrossRepeatedRuns`, `IterativeRoleInferenceEngineTest.inferenceIsDeterministicAcrossRepeatedRuns` |
| Role inference contract / Role inference is total | `IterativeRoleInferenceEngineTest.everyNodeReceivesARoleIncludingNodesThatStayUnknown` **(new this session)** |
| Role inference contract / Role classification is explainable | `IterativeRoleInferenceEngineTest.conflictingSupertypeRolesAreResolvedByPrecedenceJustLikeSeedSignals` (structural: `RoleInferenceResult.signals()`) |
| Iterative role refinement / Refinement may use graph relationships | `IterativeRoleInferenceEngineTest.aTwoHopInheritanceChainNeedsTwoRefinementPassesToFullyResolve` |
| Iterative role refinement / Refinement terminates | `IterativeRoleInferenceEngineTest.aLongerChainStillTerminatesWithinTheStructuralBound` |
| Iterative role refinement / Role transitions are monotonic in v0.4.1 | `IterativeRoleInferenceEngineTest.refinementNeverOverridesAnAlreadyKnownRoleEvenViaAnUnrelatedSupertype` |
| Entropy bounds / Entropy is normalized | `LayerEntropyCalculatorTest.allRelevantEdgesViolatingGivesTheUpperBoundOfOne` **(new)**, `SecurityEntropyCalculatorTest.allOpportunitiesFlaggedGivesTheUpperBoundOfOne` **(new)**, `CycleEntropyCalculatorTest.selfCycleCountsWhenExplicitlyGoverned`, `PersistenceEntropyCalculatorTest.anIteratedCallToAPersistenceTargetIsFlagged` |
| Layer entropy / Calculate layer entropy from the configured matrix | `LayerEntropyCalculatorTest.fixtureGraphHasExactlyOneViolationOutOfFourRelevantEdges` |
| Layer entropy / Ordering does not implicitly define policy | `LayerPolicyTest.isAllowedReflectsTheDeclaredMatrixExactly` (no ordering-derivation code path exists to test the absence of) |
| Cycle entropy / Count nodes participating in relevant cycles | `CycleEntropyCalculatorTest.twoNodeCyclePlusOneIsolatedNodeGivesTwoThirds` |
| Cycle entropy / Ignore trivial SCCs by default | `CycleEntropyCalculatorTest.selfCycleIsExcludedByDefault` |
| Cycle entropy / Handle deep graphs without recursive SCC failure | `StronglyConnectedComponentsTest.aVeryDeepLinearChainDoesNotOverflowTheStack` |
| Persistence entropy / Unknown execution context is preserved | `EvidenceTest.executionContextDefaultsToUnknownNotSingle` |
| Persistence entropy / Iterated persistence evidence is distinguishable | `EvidenceTest.executionContextCanBeSetExplicitly`, `PersistenceEntropyCalculatorTest.anIteratedCallToAPersistenceTargetIsFlagged` |
| Persistence entropy / N+1 findings remain heuristic | `PersistenceEntropyResult`'s own javadoc quotes §4.3's heuristic warning verbatim; behaviorally demonstrated by `weightedValue()` and `value()` staying separately visible (`PersistenceEntropyCalculatorTest.weightedValueEqualsPlainValueWhenEveryFlaggedEdgeHasExactlyOneIteratedItem` shows they are related but distinct measures, never collapsed into one asserted "defect") |
| Security entropy / Technology presence alone is not a security violation | `DefaultSecurityRulesTest.escapedOutputIsAnOpportunityButNotAWeakness`, `.aViewNodeWithNoRenderingEvidenceIsNotAnOpportunityAtAll` |
| Security entropy / XSS evidence can produce a security finding | `DefaultSecurityRulesTest.unescapedOutputIsFlaggedAsAWeakness`, `SecurityEntropyCalculatorTest.findingsRetainTraceableRationale` |
| Entropy aggregation / Aggregate dimensions using configured weights | `AggregatedEntropyTest.computesTheWeightedSumWithLinearCalibration`, `.appliesEachDimensionsOwnCalibrationFunction` |
| Baseline-relative drift / Calculate drift against a recorded baseline | `DriftTest.deltaIsCurrentMinusBaselinePerSection53` (formula, round 1); `DriftEndToEndTest.aRealPipelineReportProducesNonZeroDriftAgainstAPriorBaselineAndSerializesToJson` (round 2 — the same formula computed from a genuine `Pipeline.run` output, not hand-built inputs, and carried through to serialized JSON) |
| Baseline-relative drift / Preserve baseline identity | `DriftTest.refusesToCompareMeasurementsOfDifferentSubjects` |
| Analysis confidence / Confidence measures evidence resolution | `AnalysisConfidenceTest.oneUnresolvedEdgeOutOfTwoGivesOneHalf` |
| Analysis confidence / Confidence is independent of role assignment | Structural: `AnalysisConfidence.compute` never reads `Node.role()` (confirmed by reading the method); no role-outcome input exists to vary in a test, which is the point — see V04-CAL-03 in 13.2 |
| Analysis confidence / MEMBER_OF does not inflate confidence | `AnalysisConfidenceTest.memberOfEdgesAreExcludedEntirelyEvenWhenUnresolved` |
| AERF maturity / Maturity is derived from total entropy | `MaturityTest.isOneMinusTotalEntropy` |
| AERF maturity / Provisional boundaries are applied consistently | `MaturityTest.classifiesBelowFortyAsChaotic`, `.classifiesTheInteriorBoundariesAsLowerInclusive`, `.classifiesExactlyNinetyAsControlledNotOptimized`, `.classifiesAboveNinetyAsOptimized` |
| Governance invariants / A satisfied invariant evaluates successfully | `InvariantEvaluatorNodeScopeTest.aDataNodeWithAKnownRolePasses` |
| Governance invariants / A violated invariant produces evidence | `InvariantEvaluatorNodeScopeTest.aDataNodeWithUnknownRoleViolates` |
| Bounded governance DSL / Evaluate a node-scoped rule | `InvariantEvaluatorNodeScopeTest.*` |
| Bounded governance DSL / Evaluate an edge-scoped rule | `InvariantEvaluatorEdgeScopeTest.theFixtureGraphHasExactlyOneViolationTheDeliberateOne` |
| Bounded governance DSL / Evaluate a graph-scoped rule | `InvariantEvaluatorGraphScopeTest.underBudgetHolds` |
| Bounded governance DSL / Logical expressions are composable | `PredicateEvaluationTest.combinedExpressionEvaluatesCorrectly`, `.andRequiresAllOperands`, `.orRequiresAnyOperand`, `.notInvertsItsOperand` |
| AERF analysis pipeline / Evidence flows through the canonical stages | `PipelineTest.findsTheDirectPresentationToPersistenceLayeringViolation` (exercises the full extraction→...→reporting chain against a real defect sample) |
| Technology adapter boundary / Adapter evidence becomes canonical evidence | `GraphAssemblerTest.*` |
| Technology adapter boundary / Adapter does not invent unsupported relations | `JavaSourceExtractorTest.recordsTheUndeclaredSupertypeAsUnresolvedL1Syntax` |
| OpenRewrite separation / Canonical model is technology-independent | Structural: no `org.openrewrite` import exists anywhere under `aerf-model/src/main/java` (confirmed by directory read); no test can "prove a negative import" more directly than the enforcer rule itself does |
| OpenRewrite separation / OpenRewrite dependency remains isolated | **New evidence this session**: the negative-control experiment described in 13.1/13.2 (V04-PIPE-03) — not a JUnit test (Maven enforcer rules are not JUnit-testable in this reactor), but a real, reproduced build-failure/build-success pair |
| Evidence reporting / Findings retain supporting evidence | `InvariantJsonTest.theFixtureGraphsViolationSerializesWithFullEdgeEvidence` |
| Evidence reporting / Uncertainty remains visible | `CalibrationJsonTest.anUndefinedTotalEntropySerializesAsNullNotZero` |

No scenario in the source document was left `NOT IMPLEMENTED / UNVERIFIED`.

---

## 13.4 Deviations and compensating changes

Every place this session found the implementation differing from v0.4's literal prose, whether or not v0.4.1 already corrected it:

1. **`MEMBER_OF` is not in v0.4 §2.4's original relation list.** Corrected by Amendment 6 (Increment 21). Retained per the reconciliation instructions' own explicit direction. No compensating change needed — already contract-compatible by the amendment.

2. **Iterative role refinement is monotonic-only, not the fully general "revising" reading §3.2 could support.** Corrected by Amendment 4 (Increment 4). Retained: the general form remains a legitimate reading but was deliberately deferred pending its own convergence argument (open-questions-register #5), not implemented here per the "deferred work stays deferred" instruction.

3. **The layer-entropy relation matrix is never derived from the "typical allowed ordering" §4.1 mentions.** Corrected by Amendment 2. Retained; no code path for a derivation exists to remove.

4. **`AnalysisConfidence` reads "extracted relevant relations" as every graph edge except `MEMBER_OF`, a scoping §5.4's text does not itself state.** Corrected by Amendments 6 and 7. Retained and actively protected by `memberOfEdgesAreExcludedEntirelyEvenWhenUnresolved`.

5. **`PersistenceEntropyResult.weightedValue()` is not bounded to `[0,1]`, unlike every other entropy dimension's primary value.** This is a genuine deviation from §4's general "[0,1]" statement, resolved by Amendment 3's own text: `weightedValue()` is Appendix B's distinct "evidence-weighted" score, explicitly reported *alongside* (not instead of) the bounded `value()`. Not a defect — but this session found it had never been made *test-visible* that the two have different boundedness contracts, which could mislead a future contributor into "fixing" `weightedValue()` to be bounded. Compensating change: added `weightedValueIsNotBoundedToOneUnlikeThePlainRatio`, which asserts the exact value `3.0` rather than merely `> 1.0`, so the distinction is pinned precisely.

6. **`MaturityLevel`'s interior boundaries (0.40, 0.60, 0.75) are resolved lower-inclusive, a specific tie-break v0.4 §5.5's own hyphenated-range notation does not itself make.** This is documented directly in `MaturityLevel`'s own javadoc as "adopted here as an implementation decision," but — unlike the seven items above — it was never elevated to a numbered `aerf-v0.4.1-patch.md` amendment. This session did not add one (amending the patch document is outside the reconciliation instructions' scope, which asks for test protection and remediation, not new patch authorship), but flags it here explicitly per the "never hide a deviation simply because tests pass" instruction. Existing tests (`classifiesTheInteriorBoundariesAsLowerInclusive`, `classifiesExactlyNinetyAsControlledNotOptimized`) already protect the chosen behavior; no further action taken.

7. **§5.3's drift formula had no implementation at all anywhere in the reactor.** This is the session's one REMEDIATION REQUIRED item — see V04-CAL-02 in 13.2. Not previously corrected by any v0.4.1 amendment; the v0.4.1 status report's own outstanding-question #12 described the *storage* gap (now closed by Increment 19) without noting the *calculation* itself was also entirely absent from the Java codebase. Compensating change: implemented in two rounds (see 13.2 and item 9 below).

8. **The v0.4.1 status report's phrase "OpenRewrite dependencies are banned from every module except the adapter" is imprecise**: `aerf-pipeline` is also exempt, necessarily. This is a documentation-precision finding about the *status report*, not about the implementation (the implementation's own POM comments already state this correctly) or about v0.4 itself. No compensating change to code; flagged here so it is not silently carried forward as fact into a future v0.4.2 planning document. See V04-PIPE-03 in 13.2 for the full evidence.

9. **This report itself, in its round-1 form, overstated item 7's completion** — the deviation most worth flagging, since it is a mistake in the reconciliation process rather than in the code being reconciled. Round 1 implemented `Drift.compute` as a correct, well-tested pure formula and classified V04-CAL-02 as fully "REMEDIATED" without checking whether anything could actually call it with real data. It could not: no code turned a `PipelineReport` into the `EntropySnapshot` shape `Drift` requires, and `PipelineReport`/its JSON carried no subject identifier at all. A follow-up review caught this by asking the report to specifically trace the storage-to-drift path rather than accept the ledger's own claim. See the Correction record at the top of this document and V04-CAL-02 in 13.2 for the actual fix (round 2, commit `8740ea6`). Per the evidence-quality rule "never hide a deviation simply because tests pass": round 1's tests all passed; the gap was in what they proved, not in whether they ran.

No deviation found in this session was hidden, and none required reverting a better implementation. Items 1-6 and 8 were already correctly handled (a v0.4.1 amendment, a deliberate and now more-precisely-tested scope boundary, or a documentation-precision note); item 7 is the one genuine code remediation, and item 9 is this report's own mistake in first describing it, corrected here rather than left standing.

---

## 13.5 Deferred work protection

Confirmed: no v0.5 or explicitly-deferred capability was implemented in this session because it appeared in the historical AERF documents or the outstanding-question register.

Specifically checked against §10 and §11 of the reconciliation instructions:

- **Modal entropy (V04-ENT-06), JSP/frontend entropy (V04-ENT-07), full WebFlow/Tiles/JavaScript extraction (part of V04-PIPE-02)**: confirmed absent before this session (grep evidence in 13.2) and left absent after it. Nothing was added toward any of these.
- **CSRF/mass-assignment modelling (open-questions-register #10)**: not attempted. `SecurityOpportunityRule`'s catalog is unchanged from before this session.
- **Governance evidence class / configuration format (#2, #4, #6)**: not attempted. No `Governance` evidence-class rule, no per-subsystem `LayerPolicy` matrix, no per-module cycle-entropy scope was added.
- **Non-monotonic/revising role refinement (#5)**: not attempted. `GraphRoleRefinementRule`'s contract is unchanged.
- **Approved-exception suppression model, invariant aggregation `E_inv`, unified governance risk view (#9, #14, #15, #16)**: not attempted. `InvariantEvaluationResult` and `AggregatedEntropy` are unchanged apart from the new `Drift` class, which is additive and does not touch either.
- **Security concern closed vocabulary (#11)**: not attempted. `SecurityOpportunityRule.Finding.concern()` remains free-text.

The one exception, and why it was unavoidable per the reconciliation instructions themselves: **V04-CAL-02 (drift)**. The instructions state explicitly, twice, that this item is different in kind from the rest of the outstanding-question list: "This is an important reconciliation item: it is not a new v0.4.2 invention" (§5) and "#12 is special... the reconciliation should test and, if necessary, remediate the missing wiring rather than treat drift as a new v0.4.2 invention" (§11). Section 5.3's formula is an explicit v0.4 MVP-scope requirement, not a deferred capability, so implementing it here is compliance with an existing contract item, not an unauthorized pull-forward of future work — the same distinction the instructions draw in their "Critical rule: distinguish missing v0.4 behaviour from deferred work."

The implementation was also kept deliberately minimal per that same instruction, across both rounds: round 1 added a pure calculation class; round 2 (this correction) added exactly enough to make that calculation reachable from a real `PipelineReport` — a derived map, a snapshot factory, and a JSON writer, all built only from data the pipeline already computes — with no storage/API surface of its own. Loading a *stored* baseline (e.g. from the dashboard's Supabase `scans` table) remains outside this pipeline's reach, deliberately: that boundary was correct in round 1 and round 2 did not need to cross it to close the actual gap, which turned out to be entirely on the Java side (see V04-CAL-02 in 13.2). Neither round built any dashboard/CLI/storage feature — only what §5.3 itself requires.

---

## 13.6 Test failures

None, across both rounds. Every test run (individually while writing each of `DriftTest`, `LayerEntropyCalculatorTest`, `SecurityEntropyCalculatorTest`, `PersistenceEntropyCalculatorTest`, `IterativeRoleInferenceEngineTest`, `DriftJsonTest`, `PipelineTest`'s two new tests, `DriftEndToEndTest`; the full `mvn -B test` reactor run at the end of each round) passed on its first execution. No test was retried after a failure, weakened, or deleted. The correction this document records was a **classification error in the evidence report**, not a test failure — every round-1 test passed then and still passes now; they were simply insufficient in scope to support the disposition round 1 gave them credit for.

---

## 13.7 Final assessment

**`RECONCILIATION PASS`**

Every one of the 21 V04-* contract items in `docs/aerf-v0.4-contract-reconciliation.md` now reaches a definite, *verified* disposition — 12 CONFORMING, 6 CORRECTED BY v0.4.1, 2 DEFERRED BY CONTRACT, 1 CONFORMING + IMPROVEMENT, and the 1 REMEDIATION REQUIRED item (V04-CAL-02, baseline-relative drift) actually remediated end-to-end: reachable from a real `Pipeline.run` output, not just a formula proven correct in isolation. That last point took two rounds to get right, and this report says so rather than presenting the corrected result as though round 1 had already been sufficient — see the Correction record at the top and deviation item 9 in 13.4. Every BDD scenario in the source document maps to a real, passing test or to structural evidence precisely explained where a literal test is not the right tool (an absent code path, a non-invocable "feature" whose absence is the point). Three genuinely new, previously-unprotected contract requirements were found and given tests during the survey itself, none specifically named by the source reconciliation document — entropy's `[0,1]` upper bound for Layer and Security, role inference's totality, and (found only by the follow-up review that prompted this correction) drift's actual reachability from a real report. The OpenRewrite module boundary (V04-PIPE-03) was verified by an actual, reproduced build failure and recovery, not by configuration inspection alone. Nine deviations from literal v0.4 prose — including this report's own round-1 overstatement — are recorded in 13.4, none hidden; no deferred/v0.5 capability was implemented in either round. The full reactor test suite (237 tests across 6 modules) passes cleanly at the final commit `8740ea6`, up from 219 before this reconciliation began and 231 after the incomplete first round.
