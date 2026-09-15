# Increment 30 — Drift-Aware Risk Model `R`

Post-v0.4.1 implementation phase, resolving **OQ-14**.

## Objective

AERF v0.4 §5.3:
**`R = Σ(w_d · f_d(E_d)) + β · Σ(γ_d · max(0, Δ_d))`** — calibrated entropy
plus a penalty for baseline-relative worsening.

The commission: "Define the relationship between entropy, baseline-relative
drift, invariant violations and governance policy. **Do not collapse them into
an opaque single score.**"

Four findings recorded by earlier increments named this one as their owner —
**B, K, L, M, N** — and each gets an explicit disposition below rather than a
third silent deferral.

## Decision

### `R` is §5.3's two terms. Violations stay outside it.

§5.3 states exactly two terms and says nothing about invariant violations.
Adding a third would do two things at once: invent a formula the specification
does not state, and perform precisely the collapse the commission forbids.

The argument is not new here — it is `einvIsNotAnEntropyDimension`'s, extended.
§6.1 imposes no normalization on λ_k, so `E_inv` is unbounded; §5.3 imposes no
sum constraint on γ_d, so the drift penalty is unbounded too, and `R` with it.
Three unbounded quantities on different scales do not add up to a meaningful
number, and a reader handed one could not say which of them moved.

So `E_inv` and the exception ledger are reported **beside** `R`, never inside
it. `violationsAreNotATermInTheRiskModel` makes that executable: no `Risk`
parameter and no `RiskAssessment` component is invariant- or ledger-typed.
**Composing entropy, drift and violations into one view without merging them is
OQ-16 — increment 31, the next one.** That is where findings L, M and N go.

### `R` is a pure function, and reaches no report

`Pipeline` has no `subjectId` and no prior measurement, and it deliberately does
not acquire one — `Drift`'s javadoc: "Loading two historical snapshots… is left
to whichever caller already has that access." `R` is drift-aware, so it needs a
baseline the pipeline does not have.

So `Risk.compute(...)` is a sibling of `Drift.compute` and
`AggregatedInvariants.compute`: a pure static function over values the caller
already holds. **`R` is not a `PipelineReport` component and gets no
`Main.toJson` key.** Shipping a field that can never be populated on the path
that owns it would be worse than its absence; `riskIsNotAPipelineReportComponent`
pins it, and the real-repo check below confirms no risk key appears in a scan.

`E_total` is **passed in** rather than recomputed from the snapshot, so the
report's own `totalEntropy` and `R`'s first term cannot disagree.

### Finding B, fixed: `R` refuses across a policy change

`EntropySnapshot` gains `Optional<String> governanceFingerprint`.
`GovernanceFingerprint.of(GovernancePolicy)` derives a hex SHA-256 over
everything readable: the layer matrix, declared subsystems and their matrices,
the self-cycle choice, calibration weights and function names, each invariant's
name/scope/severity/referenced metrics, λ_k, the drift sensitivity, and every
approved exception.

**Its guarantee is one-directional, and that is stated in its own javadoc rather
than glossed:**

- **different fingerprints prove the policies differ** — sound, and the only
  direction anything relies on;
- **identical fingerprints do *not* prove sameness**, because invariant
  predicates remain unserializable (finding D). Pinned by
  `twoPoliciesDifferingOnlyInAnInvariantsPredicateFingerprintIdentically`.

This does not overturn increment 25's rejection of a governance hash. That
rejected a digest as a **substitute** for serializing governance, on the grounds
that "a digest over incomplete input would claim two policies identical when
only their names matched" — an objection about the *sameness* direction, which
still stands. Here the full policy is serialized as it has been since increment
25, and the digest sits beside it as a change *detector*, used only where it is
sound.

`Optional` is what makes the absent case honest. A baseline stored before this
increment genuinely has no recorded policy, which is **not** the same as having
a matching one, so `Risk` refuses there too, and
`EntropySnapshot.withoutGovernanceIdentity(...)` names that case explicitly
instead of defaulting it to "assume equal".

### `Drift` is unchanged, deliberately

Its javadoc says it "stops at the pure calculation §5.3 defines", and a numeric
difference between two numbers is arithmetically sound whatever policy produced
them. What is unsound is *interpreting* that difference as code drift. So the
check lives in `Risk`, which interprets, not in `Drift`, which subtracts.

That split is what answers the question the old guard comment said fixing
finding B would force — "what does `Drift.compute` do when the two policies
differ?" — and it carries a consequence recorded below: `Drift` used alone still
carries the old blindness.

### Only worsening is penalized

`Δ_d = current − baseline` and higher entropy is worse, so §5.3's
`max(0, Δ_d)` means an improved dimension contributes **zero**, not a negative.
Risk does not go down because something unrelated got better —
`anImprovementInOneDimensionDoesNotOffsetARegressionInAnother`. The delta is
nonetheless carried **unclamped** on `DriftPenalty`, so a reader can see that
the dimension improved and that the improvement bought nothing.

### Undefined semantics resolve a real conflict

Two existing policies meet here and disagree: `AggregatedEntropy` poisons its
whole aggregate when a weighted dimension is undefined; `Drift` silently
*omits* such a dimension. `R` follows the first, as `AggregatedInvariants` did
before it:

- `E_total` undefined → `R` undefined; it is a term.
- A dimension with **nonzero** γ_d absent from the drift map — because it was
  undefined at either endpoint — makes `R` undefined **and is named**. Treating
  it as a zero penalty would claim "no worsening observed" where nothing was
  comparable.
- γ_d = 0 is exempt, "since it does not actually contribute to the sum" —
  `AggregatedEntropy`'s own wording, transplanted.
- No declared sensitivity at all → `R` undefined, rather than silently returning
  `E_total` under a different name.

An undefined `R` still reports every term it *could* compute, including the
entropy term and the penalties: hiding them would lose the evidence a reader
needs to see why.

### Every term stays visible

`RiskAssessment` carries the entropy term, the drift term, each per-dimension
`DriftPenalty` (dimension, γ_d, Δ_d, penalty) and `undefinedBecause`, alongside
the value. That is what makes "not an opaque single score" **structural** rather
than argued — the same shape `InvariantAggregate.contributions` and
`DimensionDrift`'s retained baseline/current already use. A defined `R` carrying
reasons it is undefined is rejected at construction.

### β and γ_d are one declaration

`DriftSensitivity` holds β and the γ_d collection together, because β with no γ
and γ with no β are the same statement — "no penalty" — said two different ways,
and declaring them apart would let a caller state one and forget the other.
`WeightedDriftDimension` mirrors `WeightedInvariant`: non-blank name,
non-negative weight, **no upper bound and no sum constraint**, because §5.3
states none. `GovernancePolicy` gains it as an eighth component.

## Measurement-change checklist (backlog §10.3)

β and γ_d are policy inputs, so the checklist applies in full.

| Check | Result |
|---|---|
| Calculators changed | **None.** |
| `AggregatedEntropy`, `CalibrationProfile`, `Drift`, `DimensionDrift` | All untouched. `DimensionDrift`'s four components are asserted unchanged by the amended guard. |
| `AnalysisConfidence` | Untouched and unreachable from anything added. |
| Every calculator's `relevantRelations` | All four re-read; none changed. |
| Does `R` reach `totalEntropy`/`maturity`/`entropyByDimension()` | **No.** `riskIsNotAnEntropyDimensionEither` asserts `AggregatedEntropy` takes no drift or risk input and that `WeightedDimension` carries no drift type — w_d and γ_d are different judgements. |
| Does `R` reach any report | **No**, by design. No `PipelineReport` component, no `Main.toJson` key, confirmed on a real scan. |
| Existing exact-value tests unmodified | Yes — `PipelineTest`'s 5/7 confidence, 1.0 layer, 0.5 persistence and the three per-dimension fractions; `AnalysisConfidenceTest`'s 0.8; `PersistenceEntropyCalculatorTest`'s 0.125 and its `value()==1.0` / `weightedValue()==3.0` pair; `AggregatedEntropyTest`'s five. `DriftTest`'s eight values are unchanged; its seventeen `new EntropySnapshot(` sites moved mechanically to `EntropySnapshot.withoutGovernanceIdentity(`, which is the same two arguments under a name that states what it means. |

## Reachability

`RiskEndToEndTest` runs a real `Pipeline.run` for the current side and supplies a
caller-held baseline, following `DriftEndToEndTest`'s established pattern, then
walks baseline → current → drift → `R` → JSON. Two of its five tests are the
refusals: one where the baseline was measured under a genuinely different
`GovernancePolicy`, one where the baseline has no recorded policy at all.

## Real-repo evidence

**Demonstration** (`scripts/risk-demo/`, reproducible), spring-petclinic with
β = 2.0 and γ declared over layer, cycle and persistence:

```
=== same policy ===
E_total (entropy term) = OptionalDouble[0.2222222222222222]
drift term (beta=2.0)  = OptionalDouble[0.5333333333333332]
   layer        gamma=1.0 delta=+0.2667 penalty=0.2667
   cycle        gamma=0.5 delta=+0.0000 penalty=0.0000
   persistence  gamma=1.0 delta=-0.1000 penalty=0.0000
R                      = OptionalDouble[0.7555555555555554]

=== policy changed between the two measurements ===
layer entropy OptionalDouble[0.0] -> OptionalDouble[0.6666666666666666]  (same code, different matrix)
R = OptionalDouble.empty
   because: the two measurements were governed by different policies, so their difference is not necessarily code drift
```

The first run shows both terms and all three penalties separately, and shows
persistence **improving** by 0.10 while contributing penalty `0.0` — an
improvement that does not buy down the layer regression. The second run is the
same source code: only the layering matrix differs, layer entropy moves
0.0 → 0.667 as a pure consequence of governance, and `R` refuses to call that
drift. That is finding B, fixed, on real code.

**Regression.** A fresh default-path scan is **identical to the increment-25
baseline** once the seven keys added since are removed, all empty or undefined:
`governance.subsystems`, `governance.invariantWeights`,
`governance.driftSensitivity` (`{"beta": null, "dimensionWeights": {}}`),
`governance.approvedExceptions`, `cycleEntropy.bySubsystem`, `exceptionLedger`,
and `invariantAggregate`. **No risk key is emitted at all** — verified
programmatically against the scan output, which is the strongest available form
of "R reaches no report".

## Findings recorded, not fixed

- **`Drift` used alone still carries the old blindness.** The fingerprint check
  lives in `Risk`; a caller diffing two snapshots directly gets raw deltas with
  no policy comparison, and that is deliberate — `Drift` subtracts, it does not
  interpret.
- **A matching fingerprint does not prove matching governance**, because
  invariant predicates remain unserializable. The same gap as finding D, now
  with a second dependent.
- **`R` still has no storage, API or dashboard surface**, deliberately: no
  Supabase column, no `push-scan` field, no report key.
- **Finding K stands, re-recorded.** Approved exceptions still do not expire.
  `R` defines no expiry semantics either — §5.3 has no time term beyond the
  baseline comparison — and nothing in AERF reads a clock, so this increment
  could not discharge it. It needs an owner that has a notion of time.

## Increment report

```
Increment:      30 — drift-aware risk model R (OQ-14)
Question(s):    OQ-14. How should R = sum(w_d*f_d(E_d)) + beta*sum(gamma_d*
                max(0,Delta_d)) relate entropy, baseline-relative drift,
                invariant violations and governance policy, without collapsing
                them into an opaque single score?
Decision:       R is exactly section 5.3's two terms; invariant violations and
                the exception ledger are reported BESIDE it, never inside it,
                because section 5.3 states no third term and three unbounded
                quantities on different scales do not add up. R is a pure
                function (Risk.compute), not a PipelineReport component and not
                a JSON key, because the pipeline has no baseline and must not
                acquire one. EntropySnapshot now records the governance
                fingerprint it was measured under, and R is undefined - with a
                stated reason - when the two fingerprints differ OR when either
                is absent. Drift itself is unchanged. Undefined policy mirrors
                AggregatedEntropy: undefined E_total poisons R; a nonzero-gamma
                dimension that was not comparable poisons R and is named;
                gamma=0 is exempt; no declared sensitivity leaves R undefined.
                Every term, penalty and reason stays separately visible.
Why:            The commission forbids an opaque score, so the structure has to
                make that impossible rather than merely avoid it. Violations are
                excluded because section 5.3 names two terms and inventing a
                third asserts what v0.4 declines to - the same argument
                einvIsNotAnEntropyDimension already makes. R is a pure function
                because three javadocs forbid the pipeline acquiring a baseline.
                The fingerprint is used only in the direction where it is sound:
                different means different; identical proves nothing, since
                invariant predicates are unserializable. The check belongs in
                Risk, which interprets a delta, not in Drift, which subtracts.
Files changed:  WeightedDriftDimension, DriftSensitivity, GovernanceFingerprint,
                DriftPenalty, RiskAssessment, Risk, RiskJson (all new);
                GovernancePolicy (eighth component), EntropySnapshot (third
                component + withoutGovernanceIdentity), PipelineReport,
                GovernanceJson; 5 new test classes, several extended, 1 guard
                rewritten to pin the answer and 2 guards added;
                docs/increment-30-*.md (new); backlog status tracker;
                scripts/risk-demo/.
Tests added:    43 (431 -> 474)
Full test cmd:  mvn -B test
Test result:    474 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None. No calculator, no section 5 aggregate and no drift type
                was modified. Every protected exact value passes unmodified;
                DriftTest's eight are unchanged apart from a mechanical rename
                of the snapshot factory. A fresh petclinic scan is identical to
                the increment-25 baseline apart from additive empty keys, and
                emits no risk key at all.
Real-repo:      spring-petclinic, R = 0.7556 from E_total 0.2222 plus a drift
                term of 0.5333 at beta=2.0, with an improved persistence
                dimension contributing zero penalty; and the same source code
                under a different layering matrix refusing to yield an R.
Reachability:   RiskEndToEndTest, through a real Pipeline.run with a
                caller-supplied baseline, including both refusal cases and JSON.
Docs updated:   This file; backlog status tracker (OQ-14 -> DECIDED/IMPLEMENTED,
                finding B struck as resolved, K re-recorded, L/M/N reassigned to
                OQ-16).
Deferred kept:  Drift's standalone blindness, the fingerprint's one-directional
                guarantee, R's absent storage surface, and finding K each
                recorded with an owner.
Commit:         see git log for this increment
```
