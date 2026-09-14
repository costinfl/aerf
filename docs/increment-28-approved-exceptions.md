# Increment 28 — Approved Exceptions

Post-v0.4.1 implementation phase, resolving **OQ-09**. Opens tier 3, the
governance-facing risk workstream.

## Objective

The commission asks for five definitions, under one constraint — *never
silently delete or mutate source evidence.* Each is answered below.

The original register question (§9) is sharper than the backlog's
restatement and poses the binary directly: does an approved exception
remove the flag from `PersistenceEntropyResult` entirely, "or does the
metric stay a raw, unfiltered signal while exceptions are applied only at
the governance layer downstream (meaning `PersistenceEntropyResult` might
eventually need an 'excused' bucket alongside 'flagged')?"

## The five answers

**What constitutes an approved exception.** A finding an organization has
looked at and decided to live with, carrying a target, a reason, and an
approver — **all three required, none defaultable**. An exception with no
stated justification or no named owner is not an approved exception, it
is an unexplained hole, and the compiler says so. Critically: an
exception is an **acceptance of a finding, never a denial of it.**
Governance may say "this is real and we accept it"; it may not say "your
measurement is wrong."

**How it is represented as governance input.** `ApprovedException(target,
reason, approvedBy)` in `ApprovedExceptions`, a sixth component of
`GovernancePolicy`. `ExceptionTarget` is a sealed `OfNode` / `OfEdge`,
mirroring the node/edge vocabulary `ViolationSubject` already
establishes, but by **id** — an exception must be declarable before a
scan runs, when no `Node` or `Edge` exists yet.

**Exact ids, never prefixes** — deliberately unlike `Subsystem`. A
subsystem is a broad architectural region; an exception is a narrow
admission about one specific finding somebody reviewed. A prefix would
waive findings nobody has seen, including ones that do not exist yet.

**Which finding it can suppress.** Any finding identifiable as a single
node or edge: layer violations, flagged persistence contexts, flagged
security findings, and NODE- or EDGE-scope invariant violations. Two are
deferred with reasons, both guarded by test — **cycle findings**, because
a relevant SCC is a *set* of nodes and excusing one would have to decide
whether naming a member excuses the whole cycle, and **GRAPH-scope
invariant violations**, because `ViolationSubject.OfGraph` names nothing
addressable.

**How original evidence stays traceable.** By never touching it. The
finding stays in its result list with its provenance; excusal is recorded
in a separate `ExceptionLedger` as a *relation between* a declaration and
an observation. This was forced by a real constraint: `Evidence`'s
contract is that it "always represents something that was actually
observed", it is immutable with no copy-with method, and
`GovernanceBoundaryTest.evidenceStillModelsOnlyWhatWasObservedSoADeclarationHasNoNaturalPlaceInIt`
fails if governance ever reaches it. The tempting design — mark the
excused edge's provenance — was blocked by a live tripwire, and that was
the right answer anyway.

**Whether suppression affects entropy, findings, risk, or only governance
presentation.** **Governance presentation, alone.** Not entropy, not the
finding lists, and not risk — risk is OQ-14's to define, and the ledger
is what that model is expected to consume.

## Why entropy stays raw

Decisive and concrete: **drift is already implemented.** If approving an
exception lowered `E_P`, then approving one would register as *code
improvement* against a stored baseline — the architecture would look
better because somebody signed a form. Beyond that, a metric that moves
with governance opinion is no longer a measurement of the code.

**The §4.3 tension, recorded rather than resolved by fiat.** §4.3 defines
the metric over contexts "where access is **not** batched or otherwise
justified". Grammatically that is a *relevance* restriction, not an
acceptance clause — on the text alone, a batched access is simply not an
instance of the pattern. But this project's own commentary has
consistently read it the other way: the calculator's javadoc calls it "an
explicit governance exception", increment 06 says it "carves out an
exception", and the register entry asks how an exception "suppresses" a
finding. **That divergence between the spec's text and this project's
reading is itself a finding**, and it is stated here rather than quietly
settled.

Either way, the two halves are not symmetric. *Batched* is a property of
the code an extractor could in principle observe; *otherwise justified*
is irreducibly a human judgement. AERF detects neither, so the only route
is a human assertion — and letting a governance declaration overrule an
extractor's observation is exactly what increment 27 refused for cycle
relevance. A "this is actually batched" claim is better evidence for
improving the heuristic, with a test, than for editing a number.

## Where excusal lives — not in the calculators

Computed strictly downstream of every calculator, by
`ApprovedExceptionEvaluator`, which is handed finished results and
returns a new value. No calculator is touched; no metric result gains an
"excused" bucket. Three reasons:

- Increment 06 warned that "building a one-off exception mechanism just
  for this metric would be exactly the kind of premature,
  metric-specific abstraction the project's philosophy warns against",
  and `PersistenceEntropyCalculator`'s javadoc says the representation
  "belongs naturally to the invariant/exception model (section 6), **not
  to this metric**." Both honoured.
- One mechanism serves every finding type instead of five.
- **It makes measurement-neutrality structural rather than argued:** no
  calculator changes, so no measured value *can* move.

The existing precedent is `AggregatedEntropy`'s note that "a dimension
explicitly weighted `0` is exempt" — governance's established way to say
*don't count this*, applied downstream with the measurement untouched.
And `unmatched` follows `skippedInvariants`: reported explicitly rather
than silently absent.

## One thing found while implementing

**A single exception can excuse findings in more than one dimension.** An
exception names a *location*, and one edge can genuinely be two findings
at once — a presentation node calling a persistence node is a layer
violation, and if that call sits in a loop it is also a flagged N+1.
Rather than let one excusal quietly stand for both, the ledger records
each separately, so a reader can see that an approver reasoning about one
dimension also signed off the other. The exception still counts as
matched exactly once. Pinned by
`oneExceptionExcusesEveryFindingAtItsTargetAndEachIsListedSeparately`.

## Measurement-change checklist (backlog §10.3)

| Check | Result |
|---|---|
| Calculators changed | **None.** That is the whole proof; every other row follows from it. |
| `AnalysisConfidence` | Untouched and unreachable. |
| Every calculator's `relevantRelations` | All four re-read; none changed. |
| Existing exact-value tests unmodified | Yes — 5/7 confidence, 1.0 layer, 0.5 persistence, the three per-dimension fractions, `AnalysisConfidenceTest`'s 0.8, and `PersistenceEntropyCalculatorTest`'s 0.125 mirror plus its `value()==1.0` / `weightedValue()==3.0` pair. The weighted pair is the tightest constraint, since any excusal applied to that numerator would have to be expressed in *evidence items* rather than edges — another reason not to reach into it. |
| New guard | `anApprovedExceptionNeverBecomesEvidenceOrReachesAFinding` asserts no exception type appears in any metric result and none carries `Evidence`. |

## Reachability

`ApprovedExceptionEndToEndTest` runs a real `Pipeline.run` and asserts
every measured value is identical with and without an exception, the
excused finding's edge list is *equal* to the unexcused run's, and both
the ledger and the declaration reach the serialized report.

## Real-repo evidence

**The demonstration.** Legacy spring-framework-petclinic contains the only
genuine finding AERF has ever made on real code:
`JdbcOwnerRepositoryImpl#loadOwnersPetsAndVisits → loadPetsAndVisits`, an
iterated persistence call, `E_P = 1/8`. Excusing it
(`scripts/exception-demo/`, reproducible):

```
BEFORE  E_P=0.125 weighted=0.125 relevant=8 flagged=1 total=0.041666666666666664
AFTER   E_P=0.125 weighted=0.125 relevant=8 flagged=1 total=0.041666666666666664
FINDING STILL LISTED: true
LEDGER  persistence excused by alice - batched at the JDBC layer; reviewed 2026-09
UNMATCHED 0
```

Every measured value identical, the flagged edge list equal to the
unexcused run's, and the only change is the governance verdict naming who
accepted it and why. No sample report is committed for this run: the two
scans would differ solely in the `exceptionLedger` key, so the numbers
above are the evidence.

**Regression.** A fresh modern-petclinic scan is **identical to the
increment-25 default-path baseline** once the four keys added since are
removed (`governance.subsystems`, `governance.approvedExceptions`,
`cycleEntropy.bySubsystem`, `exceptionLedger` — all empty) — layer 0.667,
cycle 0.0, persistence 0.0, total 0.222, confidence 0.385, 118 nodes, 353
edges.

## Findings recorded, not fixed

- **Cycle findings and graph-scope violations cannot be excused**, each
  for the reason given above, guarded by test.
- **Exceptions do not expire.** Nothing in AERF reads a clock, and
  "expired" needs semantics the risk model has not defined. A stale
  exception is visible today only when it stops matching, via
  `unmatched` — a matched exception may still be years old and
  unreviewed, and the report cannot say so.
- **Nothing consumes the ledger yet.** Risk is OQ-14's; this increment
  stops at making excusal observable.
- **§4.3 reads as relevance in the spec and as acceptance in this
  project's commentary** — recorded so the divergence is deliberate.

## Increment report

```
Increment:      28 — approved exceptions (OQ-09)
Question(s):    OQ-09. What is an approved exception, what can it suppress, and
                does suppression reach entropy, findings, risk or presentation?
Decision:       An exception is an acceptance of a finding, never a denial. It
                changes no measured value and removes no finding: it produces an
                ExceptionLedger beside the measurements saying which findings are
                accepted and by whom. Target, reason and approver all required.
                Exact ids, not prefixes. Scope is node- and edge-shaped findings;
                cycles and graph scope deferred. Suppression affects governance
                presentation alone.
Why:            Drift is already implemented, so an exception that lowered
                entropy would register as code improvement against a baseline.
                Evidence models only what was observed and has a live tripwire,
                so excusal must live outside the evidence chain. Computing it
                downstream of every calculator makes measurement-neutrality
                structural rather than argued.
Files changed:  ExceptionTarget, ApprovedException, ApprovedExceptions,
                ExcusedFinding, ExceptionLedger, ApprovedExceptionEvaluator,
                ExceptionJson (all new); GovernancePolicy (sixth component),
                Pipeline, PipelineReport, GovernanceJson, Main; 4 new test
                classes, 2 extended, 1 guard amended and 1 added;
                docs/increment-28-*.md (new); backlog status tracker;
                scripts/exception-demo/.
Tests added:    35 (362 -> 397)
Full test cmd:  mvn -B test
Test result:    397 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None, and structurally so — no calculator was modified. Every
                protected exact value passes unmodified.
Real-repo:      Legacy spring-framework-petclinic's genuine N+1 excused with
                E_P unmoved at 0.125 and the finding still listed in full.
Reachability:   ApprovedExceptionEndToEndTest, through a real Pipeline.run.
Docs updated:   This file; backlog status tracker (OQ-09 -> DECIDED/IMPLEMENTED).
Deferred kept:  Cycle and graph-scope excusal, expiry, and ledger consumption
                (OQ-14) each recorded with a reason; the §4.3 reading divergence
                recorded rather than settled.
Commit:         see git log for this increment
```
