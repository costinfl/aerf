# Increment 29 — Invariant Aggregation `E_inv`

Post-v0.4.1 implementation phase, resolving **OQ-15**.

## Objective

AERF v0.4 §6.1: **`E_inv = Σ(λ_k · I_k(S))`**, where `I_k(S)` is 0 when
an invariant holds and 1 when violated. The backlog commissions this as
"define the aggregation semantics before implementation"; the
reconciliation explicitly left it unimplemented.

Acceptance: deterministic aggregation; individual violations remain
visible; undefined/absent results have explicit semantics; **no violation
disappears through aggregation**.

## The blocker, and how it was cleared

Increment 25 recorded it: `Invariant` carries a free-text `severity` and
nothing numeric, so λ_k had nowhere to live. Verified exhaustively —
across the whole repository `severity` is never compared, ordered,
thresholded, or mapped to a number. Every use is construction,
validation, pass-through, or display.

It was cleared by asking where a weight belongs rather than what to do
with severity. **The entropy side had already answered:** a dimension's
weight does not live on the dimension, it lives in `WeightedDimension`
inside `CalibrationProfile`. λ_k is described in the sources as
"governance-selected importance" — a governance choice *about* a rule,
not a property *of* one. So `Invariant`, which is §6.3's rule, stays
completely unchanged.

**Deriving λ_k from severity was rejected outright.** `Invariant`'s own
javadoc says severity is deliberately open because "inventing a closed
set here would assert a taxonomy the specification does not"; a
severity-to-weight table asserts precisely that taxonomy through the back
door. The two stay independent, and an organization may weight a "minor"
invariant heavily.

## Decision

### `E_inv` is an unbounded sum, exactly as §6.1 states

§5.1 constrains entropy weights with `Σw_d = 1`. **§6.1 states no
normalization constraint on λ_k at all.** So this is a plain weighted
sum: no denominator, no clamp. Inventing a normalization would assert
something the specification declines to, and
`PersistenceEntropyResult.weightedValue()` is the standing precedent for
a deliberately unbounded companion score.

That carries a consequence which is recorded *and pinned* rather than
left implicit: **`E_inv` is not an entropy dimension and must never be
fed into `CalibrationProfile`.** §5.1's aggregation assumes every `E_d`
is in [0,1] and the profile enforces `Σw = 1`; `E_inv` satisfies
neither. It would also collapse governance violations into the single
architecture score this project has consistently refused to produce.
`einvIsNotAnEntropyDimension` and
`einvDoesNotEnterTotalEntropyOrMaturity` make that executable.

### Undefined semantics mirror `AggregatedEntropy` word for word

`Pipeline` already skips a GRAPH-scope invariant whose referenced metric
the run left undefined, recording it in `skippedInvariants()`. Such an
invariant produced **no `I_k(S)` at all** — the exact analogue of an
undefined entropy dimension. So:

- a skipped invariant carrying **nonzero** λ_k makes `E_inv` undefined;
- weighted **0** it is exempt, "since it does not actually contribute to
  the sum" — `AggregatedEntropy`'s own wording, transplanted;
- coercing a missing indicator to 0 would claim the invariant *holds*
  where it was never checked.

Declaring **no** weights leaves `E_inv` undefined rather than zero:
declaring no importances is not a statement that an organization's
invariants are unimportant.

### Weights must cover every configured invariant

Construction throws otherwise. Anything else is a hidden default — an
unweighted invariant would silently contribute nothing, so a violation
could vanish from the aggregate merely because nobody assigned it an
importance. That is precisely what the acceptance criterion forbids.
Weighting an invariant that was never configured is rejected too: the
weight would name nothing.

### Every term stays visible beside the total

`InvariantAggregate` carries `contributions` — each invariant's own λ_k,
`I_k` and product — alongside the value, and
`unevaluatedWeightedInvariants` names why an undefined total is
undefined. This is what makes "no violation disappears through
aggregation" structural rather than argued: the sum can always be read
back to the invariants that produced it, and an undefined aggregate
still reports the terms that *were* evaluated.

## Measurement-change checklist (backlog §10.3)

| Check | Result |
|---|---|
| Calculators changed | **None.** |
| `AggregatedEntropy`, `CalibrationProfile`, `EntropySnapshot`, `Drift` | All untouched. `AggregatedEntropyTest`'s five tests pass unmodified. |
| `AnalysisConfidence` | Untouched and unreachable. |
| Every calculator's `relevantRelations` | All four re-read; none changed. |
| Does `E_inv` reach `totalEntropy`/`maturity`/drift | **No**, asserted end to end: declaring weights leaves `totalEntropy`, `maturity`, `maturityLevel` and `entropyByDimension()` identical, and the latter still holds exactly the four §4 dimensions. |
| Existing exact-value tests unmodified | Yes — 5/7 confidence, 1.0 layer, 0.5 persistence, the three per-dimension fractions, `AnalysisConfidenceTest`'s 0.8, `PersistenceEntropyCalculatorTest`'s 0.125 and its `value()==1.0` / `weightedValue()==3.0` pair. |

## Reachability

`InvariantAggregateEndToEndTest` runs a real `Pipeline.run`. Its second
test is the one that matters: reusing `PipelineTest`'s own
skipped-invariant setup, a weighted-but-skipped invariant leaves `E_inv`
undefined **while still being named in two places** — `skippedInvariants`
and `unevaluatedWeightedInvariants` — so nothing disappears.

## Real-repo evidence

**Demonstration** (`scripts/einv-demo/`, reproducible), spring-petclinic
with weights declared over its own two illustrative invariants:

```
DECLARED [no_presentation_to_persistence λ=1.0, entropy_budget λ=4.0]
totalEntropy=0.2222222222222222  (unchanged by weighting)
E_inv=1.0
  term  no_presentation_to_persistence  lambda=1.0  I_k=1  contribution=1.0
  term  entropy_budget                  lambda=4.0  I_k=0  contribution=0.0
```

petclinic violates the layering invariant and satisfies the entropy
budget (0.222 under the declared 0.35). Note the **heavily**-weighted
invariant contributes nothing, because λ_k multiplies an indicator that
is zero — weight is importance, not a score. And `totalEntropy` is
unchanged, as it must be.

**Regression.** A fresh default-path scan is **identical to the
increment-25 baseline** once the six keys added since are removed, all of
them empty or undefined: `governance.subsystems`,
`governance.invariantWeights`, `governance.approvedExceptions`,
`cycleEntropy.bySubsystem`, `exceptionLedger`, and an `invariantAggregate`
whose `value` is JSON `null`.

## Findings recorded, not fixed

- **`E_inv` is unbounded and therefore not comparable across
  configurations**, nor a drift-friendly quantity. If OQ-14 wants a
  bounded companion it must decide the normalization §6.1 declines to
  state — and decide it as a recorded decision, not by quietly dividing.
- **Nothing consumes `E_inv` yet.** OQ-14's risk model is the intended
  consumer, joining the exception ledger (finding L).
- **`severity` remains free text and unused in any computation.** λ_k does
  not replace it and the two are independent.
- **Weights join to invariants by name**, the same join key
  `WeightedDimension` uses against the dimension-value map — and, like it,
  an unknown name is an error here rather than being silently ignored.

## Increment report

```
Increment:      29 — invariant aggregation E_inv (OQ-15)
Question(s):    OQ-15. How should E_inv = sum(lambda_k * I_k(S)) aggregate
                invariant indicators, given section 6.1 states no
                normalization constraint on lambda_k?
Decision:       lambda_k lives in a separate governance declaration
                (InvariantWeights), never on Invariant, mirroring how an entropy
                dimension's weight lives in CalibrationProfile. E_inv is a plain
                unbounded sum exactly as section 6.1 states, is NOT an entropy
                dimension, and never enters totalEntropy. A skipped invariant
                with nonzero weight makes E_inv undefined; weighted 0 it is
                exempt. Declared weights must cover every configured invariant.
                Every term is reported beside the total.
Why:            Section 6.1 imposes no normalization, so inventing one would
                assert what v0.4 declines to; the unbounded consequence is
                pinned rather than hidden. The undefined policy is
                AggregatedEntropy's own, transplanted: a skipped invariant has
                no I_k at all, and coercing it to 0 would claim it holds where
                it was never checked. Full coverage is required because an
                unweighted invariant is a hidden default through which a
                violation could vanish.
Files changed:  WeightedInvariant, InvariantWeights, InvariantContribution,
                InvariantAggregate, AggregatedInvariants, InvariantAggregateJson
                (all new); GovernancePolicy (seventh component), Pipeline,
                PipelineReport, GovernanceJson, Main; 3 new test classes,
                several extended, 1 guard amended and 1 added;
                docs/increment-29-*.md (new); backlog status tracker;
                scripts/einv-demo/.
Tests added:    34 (397 -> 431)
Full test cmd:  mvn -B test
Test result:    431 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None. No calculator and no section 5 type was modified. Every
                protected exact value passes unmodified, and a fresh petclinic
                scan is identical to the increment-25 baseline apart from
                additive empty keys.
Real-repo:      spring-petclinic, E_inv = 1.0 from a violated layering invariant
                at lambda 1 and a satisfied budget invariant at lambda 4, with
                totalEntropy unchanged.
Reachability:   InvariantAggregateEndToEndTest, through a real Pipeline.run,
                including the weighted-but-skipped undefined case.
Docs updated:   This file; backlog status tracker (OQ-15 -> DECIDED/IMPLEMENTED,
                blocker 2 struck as resolved).
Deferred kept:  E_inv's incomparability, its lack of a consumer, and severity's
                continued independence from lambda_k each recorded with an owner.
Commit:         see git log for this increment
```
