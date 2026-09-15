# Increment 31 — Unified Governance-Facing View

Post-v0.4.1 implementation phase, resolving **OQ-16** — the last
commissioned item in the Tier-3 chain.

## Objective

The backlog commissions a view that can answer five questions:

1. what architectural condition was observed;
2. what changed relative to baseline;
3. what governance constraints were violated;
4. what risk interpretation follows;
5. what evidence supports each conclusion.

**Constraint:** "The underlying entropy dimensions, drift values and
invariant violations must remain traceable", and the historical report
"required entropy, drift and violations to remain separately visible
rather than collapsing them into a single architecture score."

Every answer already existed in code and none of them were composed.
Increment 30 kept violations out of `R` *because* this view is where they
were always going to meet without merging, which is why four recorded
findings named this increment as their owner.

## Decision

### There is no verdict, and that is the load-bearing decision

The view reports `R` with both terms, `E_inv` with every λ_k·I_k, each
constraint's own indicator, the unexcused-finding count, and §5.2's
maturity. It adds **no** pass/fail, no status, no ranking, no score of its
own.

Two reasons, and the second is the stronger:

- a verdict **is** the collapse the commission forbids — a reader who acts
  on one has stopped reading the parts;
- **a threshold is an invariant the organization already declares.**
  §6.3's own worked `entropy_budget` example is exactly that, and it
  appears in `violatedConstraints` like any other. A verdict computed here
  would be a second, hard-coded threshold competing with the declared one,
  and the declared one is the one governance actually agreed to.

Deriving a verdict from `severity` was refused for a third reason already
recorded in increment 29: `Invariant`'s javadoc keeps severity free text
so that no taxonomy v0.4 declines to state is asserted, and reading it as
"blocking" asserts one. `theUnifiedViewProducesNoOverallVerdict` makes all
of this executable — no boolean, no status-shaped component, and exactly
one graded value, the `MaturityLevel` §5.2 itself defines.

### Traceability is by identifier, stated as a contract

A finding row names its dimension and its subject. It carries **no
`Evidence`**.

That is not an omission. Every finding's provenance is already serialized
in full under the metric that produced it — `layerEntropy.violatingEdges`,
`persistenceEntropy.flaggedEdges`, `securityEntropy.flagged`, each edge
with its `provenance`. Copying it into a second section would roughly
double the document to restate what is already in it, and would create two
copies that can disagree — at which point only one of them is the
measurement's own. So question 5 is answered by naming the subject
precisely enough that a reader resolves it, pinned by
`theUnifiedViewTracesFindingsByIdentifierRatherThanCopyingEvidence`.

The subject type is the **existing `ExceptionTarget`**. That is deliberate
reuse: it is already this project's identifier-based addressing for "a
finding an approver could name", and it is exactly the key
`ExceptionLedger` joins on — so an excusal recorded in the view can never
be attached to a different finding than the one the ledger matched. The
excusal index is read *from the published ledger* rather than recomputed,
for the same reason: a second evaluation could differ from the one the
report already states.

It is `Optional` because an edge with an unresolved endpoint, and every
cycle finding, name nothing an approver could address. Such findings are
still listed; what they cannot offer is an address — and the view says so.

### It states what it cannot answer

`unanswered()` is **derived**, not supplied, so it cannot disagree with the
contents beside it. Without a baseline it names questions 2 and 4 and says
why; with one, it carries `RiskAssessment.undefinedBecause` through
**verbatim**, because a view that reworded a refusal could soften it into a
caveat.

The reason this exists at all: an empty drift section and a system that
genuinely did not move serialize identically, so silence would be a claim.
The JSON follows suit — `"changed": null` and `"risk": null`, never empty
objects. This is the idiom `skippedInvariants`,
`unevaluatedWeightedInvariants` and `undefinedBecause` already established.

### `R` and drift stay a caller's business

`toGovernanceView(subjectId)` answers three questions;
`toGovernanceView(subjectId, baseline)` answers five. The baseline is a
parameter for exactly the reason increment 30 made `R` a pure function:
this pipeline has no prior measurement and deliberately does not reach into
storage for one.

So the view is **not** a `PipelineReport` component and has **no
`Main.toJson` key**. `GovernanceViewJson` is a standalone mapper following
`DriftJson` and `RiskJson`. A default scan is unchanged by this increment —
not "changed additively", unchanged.

`BaselineComparison` bundles drift and `R` because they share one
precondition, and the `Optional` around the pair distinguishes "no baseline
was supplied" from "a baseline was supplied and nothing was comparable",
which two empty collections could not.

**It names no baseline subject.** `Drift.compute` rejects two snapshots of
different subjects — §5.3's drift is one system over time — so the
baseline's subject is always the view's own, and a second copy of it would
only invite a reader to imagine it could differ. What genuinely can differ
between two measurements is the *governance* behind them, and that is
reported where it belongs: as `R`'s own stated refusal. (This was found by
a test failure, not by reading: the first draft carried a
`baselineSubjectId` and `Drift` rejected the differing value outright.)

### The view lives outside the `governance` package

New package `org.aerf.analysis.view`. Increment 25 drew an authorship
boundary in which everything under `org.aerf.analysis.governance` is
something the organization **declares**. This view is **derived** from a
measurement, so putting it there would blur the line that increment
established. Living in `aerf-analysis` also lets `aerf-report` serialize it,
since `aerf-report` sees `aerf-analysis` and never `aerf-pipeline`.

The composition itself is `GovernanceViewAssembler` in `aerf-pipeline`,
because only that module can see `PipelineReport`.

### Finding E, fixed: one rule, two callers

Each layer finding carries `governedBy` — the subsystem whose **own
matrix** judged it, empty meaning the default did.

The precision matters. Increment 27 allowed a subsystem to be declared
purely to scope cycle entropy, with no layering matrix. Such a node is
*claimed* by that subsystem and *judged* by the default, so answering
"which subsystem judged this?" with the claimant would credit a declaration
that had no part in the verdict.

To stop the report and the verdict drifting apart, the selection rule
became one method: `Subsystems.governingMatrix(NodeId)`.
`LayerEntropyCalculator.governingPolicy` now delegates to it and the view
calls the same method, so attribution and application cannot disagree.
Choosing the *source* as the deciding endpoint stays in the calculator —
that is increment 26's measurement decision, not something `Subsystems`
should know.

### Findings L and N answered; M re-recorded

**L:** every finding carries `excusedBy`, and the unmatched exceptions are
carried through. Increment 28's neutrality survives and becomes
structural — an excused finding is **still listed**, marked rather than
removed, no `DimensionObservation` count moves, and an invariant whose
every violation is excused **still does not hold**, because excusal accepts
a finding rather than denying it.

**N:** the `InvariantAggregate` is carried whole — the same instance, not a
copy and not a recomputation.

**M stands, deliberately.** §6.1 imposes no normalization on λ_k, so
`E_inv` is unbounded in the view too. Bounding it to make a composite
tidier would invent what the specification declines to state — "do not turn
an open question into code". `theUnifiedViewNeitherNormalizesEinvNorTurnsItIntoAnEntropyDimension`
pins it: exactly one `E_inv` is carried, so no "normalizedEinv" companion
can appear without that test failing.

### Two granularities, on purpose

A `DimensionObservation` reports the metric's own ratio; for cycle entropy
the denominator is every node and the numerator every node in a cycle. The
findings list reports addressable items, and there **one relevant SCC is
one finding**, because §4.2's unit is a set of nodes. They count different
things, and collapsing them would misreport one or the other.

## Measurement-change checklist (backlog §10.3)

The view is derived and is not a policy input, so the checklist applies to
the `Subsystems`/`LayerEntropyCalculator` extraction alone.

| Check | Result |
|---|---|
| Calculators changed | **One line.** `governingPolicy` now calls `Subsystems.governingMatrix` instead of inlining `governing(id).flatMap(Subsystem::layerPolicy)`. Same expression, one caller fewer. |
| Behaviour identity proven | `governingMatrixSelectsExactlyWhatTheCalculatorUsedToSelectInline` asserts the two agree for a subsystem with a matrix, one without, and an unclaimed node. The whole layer suite, `LayerEntropyCalculatorSubsystemTest` and `CycleEntropyCalculatorSubsystemTest` pass unmodified. |
| `AggregatedEntropy`, `CalibrationProfile`, `Drift`, `Risk`, `AggregatedInvariants` | All untouched. |
| `AnalysisConfidence` | Untouched and unreachable from anything added. |
| Every calculator's `relevantRelations` | All four re-read; none changed. |
| Does the view reach `totalEntropy`/`maturity`/`entropyByDimension()` | **No.** It reads them and writes nothing back, asserted by `composingAViewChangesNothingAboutTheReportItWasComposedFrom`. |
| Does the view reach any report | **No.** No `PipelineReport` component, no `Main.toJson` key, confirmed on a real scan. |
| Existing exact-value tests unmodified | Yes, and **no existing test file was edited at all** except `GovernanceBoundaryTest`, purely additively (65 insertions, 0 deletions). `PipelineTest`'s 5/7 confidence, 1.0 layer, 0.5 persistence and the three per-dimension fractions; `AnalysisConfidenceTest`'s 0.8; `PersistenceEntropyCalculatorTest`'s 0.125 and its `value()==1.0`/`weightedValue()==3.0` pair; `AggregatedEntropyTest`'s five; `DriftTest`'s eight; `CycleEntropyCalculatorTest`'s 2.0/3.0. |

## Reachability

`GovernanceViewEndToEndTest` composes the view from a genuine
`Pipeline.run`: both overloads, the subsystem attribution in both its
forms, an excused finding still counted, every invariant with its indicator
and λ_k, the full chain to JSON, and the assertion that `Main.toJson` emits
no view key.

## Real-repo evidence

**Demonstration** (`scripts/view-demo/`, reproducible), spring-petclinic
with subsystems, invariant weights, drift sensitivity and one approved
exception all declared:

```
1. layer 0.667 (14/21), cycle 0.0 (0/118), persistence 0.0 (0/10), security undefined
   totalEntropy=0.2222  maturity=0.7778  L3_CONTROLLED
2. layer 0.4000 -> 0.6667 (+0.2667); cycle +0.0000; persistence 0.1000 -> 0.0000 (-0.1000)
3. no_presentation_to_persistence  I_k=1 lambda=1.0  unexcused=13/14
   entropy_budget                  I_k=0 lambda=4.0  unexcused=0/0
   E_inv=1.0
4. R=0.7556  entropyTerm=0.2222  driftTerm=0.5333
      layer gamma=1.0 delta=+0.2667 penalty=0.2667
      cycle gamma=0.5 delta=+0.0000 penalty=0.0000
      persistence gamma=1.0 delta=-0.1000 penalty=0.0000
5. 14 finding(s), 13 unexcused; each judged-by=owner
```

The strongest evidence in that output is what did **not** move: `E_inv` =
1.0 is increment 29's demonstration exactly, `R` = 0.7556 with the same
three penalties is increment 30's exactly, and `totalEntropy` = 0.2222 has
been unchanged since increment 24. The view composes; it does not compute,
so none of these could have drifted.

Read alongside them: `judged-by=owner` resolves finding E on real code
(`vet`, declared with no matrix, correctly reports the default as judge);
the excused finding is still listed and the invariant still reads `I_k=1`;
and the same 14 edges appear as both layer findings and invariant
violations, which is two judgements about one edge rather than
double-counting.

The second run is the honest half — the same code with no baseline, naming
the two questions it cannot answer instead of showing two empty sections.

**Regression.** A fresh default-path scan is **identical to increment 30's**.
Verified programmatically: the top-level key set is unchanged, the document
is byte-identical to the increment-25 baseline after removing exactly the
seven previously-recorded additive keys, and none of `governanceView`,
`unanswered`, `observed`, `violated`, `changed` or `risk` appears anywhere
in it. This increment adds nothing to a scan.

## Findings recorded, not fixed

- **Finding M stands.** `E_inv` remains unbounded and therefore not
  comparable across configurations. The view reports it unchanged; deciding
  a normalization §6.1 declines to state needs its own decision record.
- **The view has no storage, API or dashboard surface**, deliberately — no
  report key, no Supabase column, no `push-scan` field. Two of its five
  answers need a baseline, and choosing where baselines live is storage
  infrastructure the backlog forbids introducing uncommissioned.
- **A cycle finding still names nothing addressable.** The view lists one
  row per relevant SCC with an empty subject and says so in `unanswered`,
  which surfaces finding J rather than resolving it.
- **`governedBy` answers only for layer findings.** No other dimension is
  judged by a matrix, so the field is empty elsewhere — an absence that
  means "not applicable" rather than "the default judged it", and the two
  are not distinguished.

## Increment report

```
Increment:      31 — unified governance-facing view (OQ-16)
Question(s):    OQ-16. Compose the preceding semantics into a view answering
                what was observed, what changed, what was violated, what risk
                follows, and what evidence supports each - with entropy, drift
                and invariant violations remaining traceable and not collapsed
                into a single architecture score.
Decision:       GovernanceView composes all five answers and produces NO
                verdict: no pass/fail, no status, no score beyond section 5.2's
                maturity. Traceability is by identifier - each finding names its
                subject via the existing ExceptionTarget and carries no copy of
                evidence already serialized under its own metric. Questions 2
                and 4 need a caller-supplied baseline, so the view is not a
                PipelineReport component and gets no Main.toJson key; what it
                cannot answer it names, derived, rather than showing empty
                sections. Layer findings are attributed to the subsystem whose
                own matrix judged them via one shared rule,
                Subsystems.governingMatrix, which the calculator now calls too.
                Excusal marks findings without removing them or moving a count,
                and E_inv is carried whole and unnormalized.
Why:            A verdict is the collapse the commission forbids, and is also
                redundant: a threshold is an invariant the organization already
                declares, so computing one here would compete with the declared
                one. Evidence is referenced rather than copied because two
                copies can disagree and only one is the measurement's own. The
                view is caller-composed because the pipeline has no baseline and
                must not acquire one. Attribution moved into one method so the
                matrix reported and the matrix applied cannot drift apart.
Files changed:  DimensionObservation, TraceableFinding, ViolatedConstraint,
                BaselineComparison, GovernanceView (new package
                org.aerf.analysis.view); GovernanceViewAssembler,
                GovernanceViewJson (new); Subsystems (governingMatrix),
                LayerEntropyCalculator (delegates), PipelineReport (two
                toGovernanceView overloads), ExceptionJson (target widened to
                package-private); 4 new test classes, 3 guards added;
                docs/increment-31-*.md (new); backlog status tracker;
                scripts/view-demo/.
Tests added:    41 (474 -> 515)
Full test cmd:  mvn -B test
Test result:    515 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None. No existing test file was modified except
                GovernanceBoundaryTest, purely additively. Every protected exact
                value passes unmodified. A fresh petclinic scan is IDENTICAL to
                increment 30's - this increment adds nothing to a scan at all.
Real-repo:      spring-petclinic, all five questions answered at once, with
                E_inv = 1.0, R = 0.7556 and totalEntropy = 0.2222 each exactly
                reproducing the increment that first produced them; layer
                violations attributed to the 'owner' subsystem; one finding
                excused and still counted.
Reachability:   GovernanceViewEndToEndTest, through a real Pipeline.run, both
                overloads, and the assertion that no report key is emitted.
Docs updated:   This file; backlog status tracker (OQ-16 -> DECIDED/IMPLEMENTED,
                findings E, L and N struck as resolved, M re-recorded).
Deferred kept:  E_inv's incomparability, the view's absent storage surface, the
                unaddressable cycle finding, and governedBy's not-applicable
                ambiguity each recorded with an owner.
Commit:         see git log for this increment
```
