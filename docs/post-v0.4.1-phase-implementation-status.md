# Post-v0.4.1 Implementation Phase — Final Status Report

Required by the backlog's Definition of Done §13.10: "a final
implementation status report identifies completed, deferred and changed
items."

**Phase scope:** the inherited backlog in `docs/aerf-post-v0.4.1-backlog.md`,
opened at commit `b8ed3a2` and comprising increments 22–31 plus two closing
assessments.

**Conceptual baseline: unchanged at v0.4.1.** There is no v0.4.2 and no v0.5.
Every decision below is an implementation decision recorded against the
existing specification, never an amendment to it. The implementation line is
**0.2.0-SNAPSHOT**; §13.11 says a release version is considered only after this
report, and that consideration is deliberately left to the reader.

**Test suite: 237 → 515**, 0 failures, 0 errors, 0 skipped (`mvn -B test`).

---

## 1. Completed items

Ten commissioned questions, each with a recorded decision, an increment
document, and a tracker row. Full reasoning lives in each increment's own
document; this is the index.

| OQ | Increment | Outcome in one line | Tests |
|---|---|---|---|
| OQ-08 | 22 | N+1 source-side restriction **rejected**; behaviour pinned rather than changed | 237→243 |
| OQ-11 | 23 | Canonical security-concern identifiers; enum and DSL halves **explicitly not** taken | 243→251 |
| OQ-13 | 24 | Per-dimension confidence; security's left undefined rather than a degenerate 1.0 | 251→266 |
| OQ-02 | 25 | `GovernancePolicy` boundary; four authorship classes; `LayerPolicy` made readable | 266→308 |
| OQ-04 | 26 | Per-subsystem layer matrices; the **source's** matrix governs; overlap rejected | 308→336 |
| OQ-06 | 27 | Cycle entropy scoped per subsystem, strictly after SCC detection | 337→362 |
| OQ-09 | 28 | Approved exceptions accept findings and **move no measured value** | 362→397 |
| OQ-15 | 29 | `E_inv` is an unbounded sum exactly as §6.1 states; not an entropy dimension | 397→431 |
| OQ-14 | 30 | `R` is §5.3's two terms; violations stay outside; refuses across a policy change | 431→474 |
| OQ-16 | 31 | Unified view composing all five answers, with **no verdict** | 474→515 |

### What "completed" means here

The backlog is explicit: "do not report an item as complete merely because its
unit tests pass. Completion means the behaviour is reachable at the intended
architectural boundary and its effect on existing measurements has been
checked." That standard was set by the V04-CAL-02 correction in the preceding
phase, where a formula passed its unit tests while being unreachable from any
real pipeline run.

Every item above therefore carries an end-to-end test driving a real
`Pipeline.run`, and every increment ran the full reactor plus a real-repository
check. Where an item deliberately reaches **no** boundary — `R` and the unified
view, both of which need a baseline the pipeline does not have — that absence is
itself asserted by test (`riskIsNotAPipelineReportComponent`,
`theViewIsNotAPipelineReportComponentAndReachesNoReportKey`) rather than left
implicit.

---

## 2. Assessed but deliberately not implemented

| OQ | Status | Why it stops here |
|---|---|---|
| **OQ-10** — CSRF / mass-assignment | **ASSESSED / OPEN.** Steps 1–3 of 4 complete | The commission orders "implement only after that contract is established", and step 1 blocks step 4: method annotations and parameter types **do not survive extraction at all**. Mass assignment needs an adapter evidence extension first — a real increment with §10.3 obligations, since it changes the security denominator. CSRF needs a calibration case that **no repository in the evidence base contains**. `docs/oq-10-evidence-availability-assessment.md` |
| **OQ-05** — revising role refinement | **GATED — checked, gate has not opened** | `roleRefinementPasses` = **0** in every committed sample from both repositories; all 137 assigned roles trace to one resolved declaration; **zero** precedence conflicts. Revision changes an already-assigned role, and no node has ever been assigned one twice. Per the backlog's own instruction the item is **left open, not closed**. `docs/oq-05-gate-check.md` |
| **OQ-01** — when seed-only classification fails | **OPEN (continuous)** | Served by each real scan. Increment 22 confirmed increment 20's adapter fix on real code; increment 24 re-scanned the same repository from a different directory and got a byte-identical graph. Still no case found where seed **plus** refinement produces a wrong role. |

These are §13.3's requirement working: gated and deferred questions remain
explicitly open rather than being approximated.

---

## 3. Partially decided — the named remainders

Three items are **not** fully closed, and in each case the unfinished half is
stated rather than implied, with a guard test that fails if someone closes it
silently.

| OQ | Decided | Still open | Guard |
|---|---|---|---|
| OQ-11 | Canonical concern identifiers | The **DSL half** — an invariant still cannot reference a concern, which was OQ-11's original motivation. `PropertyKey` has no security constant and `InvariantEvaluator` never sees a `SecurityEntropyResult`. | `theInvariantDslStillCannotReferenceAConcern` |
| OQ-02 | Representation and authorship boundary | §3.3's **Governance evidence class** — governance still cannot assign or override a role. `RolePrecedence` has no notion of authorship (a declaration would lose to a naming heuristic) and `Evidence` models only what was observed. | `governanceDeclaresNoRoleAssignmentSoSection33sGovernanceEvidenceClassRemainsUnimplemented` |
| OQ-06 | Scoped measurement | The **tolerance half** — governance cannot declare a cycle irrelevant. It would change §4.2's own definition of which SCCs count, and **no repository in the evidence base contains a cycle** to calibrate against. | `governanceCannotDeclareACycleIrrelevantSoOq06sToleranceHalfRemainsOpen` |

---

## 4. Changed items — what this phase altered in existing behaviour

This is the section §13.4 and §13.5 exist for. The honest summary is that
**almost nothing measured changed**, and every exception is named.

### Measured values: one deliberate change, one measurement-neutral extension

- **Increment 24 (OQ-13)** added per-dimension confidence. It is a *new*
  measurement beside the existing flat `confidence`, which was left untouched.
  `AnalysisConfidenceTest`'s 0.8 and `PipelineTest`'s 5.0/7.0 pass verbatim.
- **Increment 26 (OQ-04)** is the one place a real measurement can move, and
  only when an organization declares a subsystem matrix: petclinic goes
  14/21 → 11/21 with the **denominator unchanged at 21**. Declaring no
  subsystem leaves the prior behaviour on the same code path, not a parallel
  one.

Everything else is additive by construction. Increments 28, 29, 30 and 31 each
modified **no calculator at all**; 27 computes its scoping strictly after
`StronglyConnectedComponents.find`, pinned by
`sccDetectionSeesTheSameGraphWhateverIsDeclared`.

### Structural changes to existing types

| Change | Increment | Nature |
|---|---|---|
| `PipelineConfig` 9 components → 3 | 25 | Grouping; every input preserved |
| `GovernancePolicy` 4 → 8 components | 25–30 | Additive; `withOneLayerMatrix` absorbs callers that do not care |
| `LayerPolicy` gained `knownRoles()`/`allowedTargets()` | 25 | Read-only; the matrix was previously write-only |
| `LayerPolicy` storage canonicalized on `Role` order | 25 | **Bug fix.** `Map.of` and `Collectors.toMap` gave 2–4 different orderings across JVM runs, which would have violated §14's determinism in serialized output |
| `EntropySnapshot` gained `governanceFingerprint` | 30 | Additive; `withoutGovernanceIdentity(...)` names the legacy case explicitly |
| `LayerEntropyCalculator.governingPolicy` delegates to `Subsystems.governingMatrix` | 31 | Same expression, extracted so the view and the calculator read one rule |

### Protected exact values — all pass verbatim, every increment

`PipelineTest`'s 5.0/7.0 confidence, 1.0 layer, 0.5 persistence and three
per-dimension fractions; `AnalysisConfidenceTest`'s 0.8;
`PersistenceEntropyCalculatorTest`'s 0.125 and its `value()==1.0` /
`weightedValue()==3.0` pair; `AggregatedEntropyTest`'s five; `DriftTest`'s
eight; `CycleEntropyCalculatorTest`'s 2.0/3.0.

The single exception is cosmetic and recorded: `DriftTest`'s seventeen
positional `new EntropySnapshot(...)` sites moved to
`EntropySnapshot.withoutGovernanceIdentity(...)` in increment 30 — the same two
arguments under a name that states what they mean. **No assertion changed.**

### The report JSON grew by seven keys, then stopped

Across increments 24–30 a default scan gained exactly seven additive keys:
`confidenceByDimension`, `governance.subsystems`,
`governance.invariantWeights`, `governance.driftSensitivity`,
`governance.approvedExceptions`, `cycleEntropy.bySubsystem`, `exceptionLedger`,
`invariantAggregate`. Increment 31 added **none** — a fresh petclinic scan is
identical to increment 30's, byte for byte.

---

## 5. Definition of Done — item by item

| § | Requirement | Status |
|---|---|---|
| 13.1 | Every commissioned item has a recorded decision | **Met.** Ten decided and implemented; OQ-10 assessed with steps 1–3 recorded; OQ-05 gate-checked. All indexed in `docs/post-v0.4.1-backlog-status.md`. |
| 13.2 | Implemented decisions have unit, integration and regression evidence | **Met.** 515 tests; every item has an end-to-end test on a real `Pipeline.run`; every increment ran the full reactor and a real-repository check. |
| 13.3 | Gated/deferred questions stay explicitly open | **Met.** OQ-05 gated with a quantified check; OQ-10 open with its blockers named; three partial decisions each carry a guard test that fails if the remainder is closed silently. |
| 13.4 | v0.4.1 semantics preserved unless deliberately superseded | **Met.** Nothing was superseded. All protected exact values pass verbatim; the one measured change (OQ-04) requires an explicit declaration to occur. |
| 13.5 | No new relation or metric without confidence/denominator regression checks | **Met.** Every increment ran the §10.3 checklist, including the two that are *policy inputs* rather than metrics (29's λ_k, 30's β/γ). `AnalysisConfidence` was never modified; all four `relevantRelations` re-read and unchanged each time. |
| 13.6 | Governance input and engineering evidence remain distinguishable | **Met, and made structural.** Increment 25 separated them into `GovernancePolicy`, `DetectionCatalog` and measurement definition. `GovernanceBoundaryTest` fails if governance ever manufactures `Evidence` or an exception reaches a finding. |
| 13.7 | Entropy, drift, violations and risk stay separately traceable | **Met, and it is the phase's through-line.** `E_inv` is reported beside `totalEntropy`, never inside; `R` is §5.3's two terms with violations outside; the unified view composes all of them with **no verdict**. Three guard tests pin it. |
| 13.8 | Real-repository evidence where empirical questions require it | **Met.** OQ-08 decided *because of* the one true positive on legacy petclinic; OQ-04 demonstrated 14/21 → 11/21; OQ-09, OQ-15, OQ-14 and OQ-16 each have a reproducible `scripts/*-demo/`. Where real evidence does **not** exist it is recorded as a finding rather than substituted — findings H, I, U, V. |
| 13.9 | Technology independence outside adapters preserved | **Met.** `maven-enforcer-plugin` still bans `org.openrewrite:*` from aerf-model/analysis/report/extraction. No technology-specific canonical graph structure was introduced. Increment 26's subsystem selector deliberately keeps `NodeId` opaque — `startsWith` only, never parsed — and increment 31 re-affirmed that by refusing to recover parameter types from ids. |
| 13.10 | This report | **This document.** |
| 13.11 | Only then is a release version considered | **Not taken.** The line stays `0.2.0-SNAPSHOT`. |

---

## 6. Open findings carried forward

Twenty-six findings (A–X, plus B1 and B2) are recorded in the tracker, four of
them struck as resolved. Each carries a named owner or an explicit "unowned". They
are not a defect list — most are decisions whose
evidence has not yet appeared. The ones that would most change what AERF can do:

- **R** — `Evidence.attributes()` never reaches the report, yet it decides 43
  of 118 role assignments on a real scan. The smallest real gap found in this
  phase, and it blocks OQ-10's own traceability constraint.
- **S, T** — no method-level annotation and no method parameter type survives
  extraction. Together these bound what any future concern can even ask about.
- **A** — relation scope is not governable, and is **unowned**. `DimensionConfidence`
  reads the same set, so one wrong plumb would move layer entropy and three
  confidences at once.
- **D, B2** — invariant predicates are unserializable, which is why the
  governance fingerprint's guarantee is deliberately one-directional.
- **H, I, U, V** — four separate cases where the evidence base contains no
  instance to calibrate against: no cycle, no security configuration, no
  repository that exercises graph refinement. Adding one repository with any of
  these would unblock more than any code change.
- **W** — a *monotonic* refinement improvement the evidence suggests
  (implementation → interface), recorded as an observation and explicitly not
  proposed, since it would change role assignment and therefore every
  downstream metric.

---

## 7. How the phase was worked

Recorded because the method is reusable, and because two of the backlog's rules
did real work rather than sitting in a preamble.

**"An open question is not a specification" (§10.1)** held throughout. Each
increment ran question → evidence → decision → decision record → tests →
implementation → evidence report. Twice the evidence answered *against* the
obvious implementation: OQ-08 was decided by **rejecting** the restriction it
proposed, and OQ-05's gate check is a decision not to build.

**Guard tests became the phase's main mechanism.** Written to fail the day
someone widens a surface, so widening happens through a decision record rather
than by accident. In increments 26, 27, 29, 30 and 31 exactly one fired each
time — every one amended deliberately, never deleted. Increment 27's case is
the most instructive: the guard still *passed*, but its name would have become
a lie, so it was rewritten anyway. Increment 30's was the payoff — finding B's
own tripwire, rewritten from pinning an absence to pinning the answer.

**Being wrong was routine and is recorded.** Four times a test failed because
*my expectation* was wrong and the code was right — increment 24's persistence
fraction, increment 28's assumption that one exception excuses one finding,
increment 29's assumption that petclinic satisfies its entropy budget, and
increment 31's assumption that a baseline may name a different subject. Each
was verified arithmetically before the expectation was changed, and each left
behind an assertion or a javadoc paragraph making the real reason visible.

**The specification's silences were honoured rather than filled.** §6.1 states
no normalization on λ_k and §5.3 none on γ_d, so `E_inv` and `R` are both
unbounded and neither is an entropy dimension. The recurring temptation was to
bound them for tidiness; it was refused three times, and finding M records that
the question remains genuinely open rather than settled by omission.
