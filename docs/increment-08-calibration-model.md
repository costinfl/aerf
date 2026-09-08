# Increment 8 — Calibration Model

## Objective

Implement the calibration/aggregation core from AERF v0.4 §5: aggregated
entropy (§5.1), the three calibration function families (§5.2), analysis
confidence (§5.4), and maturity (§5.5). This is the "Calibration model"
item frozen into MVP scope by §11 (distinct from "ML calibration," which
§11 explicitly defers).

## Scope

New package `org.aerf.analysis.calibration` in `aerf-analysis`:
- `CalibrationFunction` + three implementations — `LinearCalibration`
  (`f(E)=E`), `SigmoidCalibration(k, t)` (`f(E)=1/(1+exp(-k(E-t)))`),
  `PowerCalibration(alpha)` (`f(E)=E^alpha`) — reproducing §5.2's three
  named families exactly, each documented as an unvalidated model family
  per the section's own caveat.
- `WeightedDimension` / `CalibrationProfile` — a governance configuration
  bundling each dimension's name, weight, and calibration function,
  validating `sum(w_d) = 1` at construction (§5.1's explicit constraint).
- `AggregatedEntropy` — computes `E_total = sum(w_d * f_d(E_d))`.
- `Maturity` / `MaturityLevel` — `M = 1 - E_total` and the L0–L4
  classification table (§5.5).
- `AnalysisConfidence` — `C = resolved relevant relations / total
  extracted relevant relations` (§5.4).
- 24 new tests, including one integration test that runs all four MVP
  entropy calculators (Increments 3, 5, 6, 7) against one graph and
  carries their real output through aggregation, maturity classification,
  and confidence — not synthetic dimension numbers.

## Deliberately deferred: §5.3, baseline-relative drift

`Delta_d = E_d^t - E_d^0` needs a *baseline*: a prior measurement to
compare the current one against. Representing, storing, and loading a
baseline is a different kind of problem than the pure computation this
increment covers — it drags in serialization/persistence questions that
belong with "JSON evidence/reporting," still further down the MVP list
and not yet built. Implementing `Delta_d` as a bare function over two
already-in-memory numbers would be trivial, but presenting that as
"baseline-relative drift" without anywhere to actually keep a baseline
would be hollow. Deferred to its own increment once reporting/storage
exists.

## The one real design decision this increment required

§5.1's formula, `E_total = sum(w_d * f_d(E_d))`, assumes every weighted
dimension has a value. But every calculator built in Increments 3, 5, 6,
and 7 can legitimately report **no value at all** — `OptionalDouble.empty()`
— when a graph has no relevant contexts for that dimension, by deliberate
design (undefined, not zero, established starting with layer entropy).
§5.1 doesn't say what aggregation should do when a weighted dimension is
undefined.

**Adopted rule:** if a dimension carries nonzero weight, it must have a
defined value, or the entire aggregate (`E_total`) is undefined too. A
dimension explicitly weighted `0` is exempt, since it contributes
nothing regardless. The alternative — silently treating an undefined
dimension as `0.0` — would mean "no measurable persistence contexts in
this graph" and "measured persistence access and found it perfectly
clean" produce the identical aggregate score, which is exactly the
undefined-vs-zero conflation every prior metric increment was built to
prevent. Making the *aggregate* undefined whenever a weighted input is
undefined is the only way to keep that guarantee at the aggregation
layer instead of losing it there.

## Other implementation decisions not dictated by v0.4

1. **`AnalysisConfidence` is graph-wide**, not scoped to any one metric's
   own relation filter. §5.4 is written at the level of the overall
   calibration/risk model, not tied to a specific dimension, so
   "relevant relations" is read as every edge extraction produced:
   `resolved / total = (edges with both endpoints NodeRef.Resolved) /
   graph.edges().size()`.
2. **Maturity boundary tie-breaks.** §5.5's table gives L0 (`M < 0.40`)
   and L4 (`M > 0.90`) with explicit, unambiguous strict inequalities —
   honored exactly, so `M == 0.90` falls to L3, not L4. The three
   interior boundaries (0.40, 0.60, 0.75) are each only the shared
   endpoint of two hyphenated ranges with no stated ownership; resolved
   as lower-inclusive, a specific tie-break the table itself doesn't
   make.
3. **`CalibrationProfile` validates the weight-sum constraint at
   construction**, not at aggregation time, so an invalid governance
   configuration fails fast rather than producing a silently-wrong
   `E_total`.

## Evidence — what this increment proves or exposes about the AERF model

- The integration test is the strongest evidence: real `LayerEntropyResult`,
  `CycleEntropyResult`, `PersistenceEntropyResult`, and
  `SecurityEntropyResult` values, computed by four independently-built
  calculators from four different increments, compose through
  `CalibrationProfile` and `AggregatedEntropy` without any adaptation
  code — confirming the `OptionalDouble value()` convention adopted
  independently in each of those increments was the right shared
  contract for this layer to consume.
- The undefined-weighted-dimension rule is a case where a *design
  principle* established early (§14's "measurement before aggregation") had
  a concrete, unstated consequence at the point of actually aggregating —
  it wasn't visible as a gap until aggregation was implemented and forced
  the question.

## Open questions for the architecture

Tracked in `docs/open-questions-register.md`; new entries from this
increment:

- **How should baseline-relative drift (§5.3) represent and load a
  baseline**, once reporting/serialization exists? Not decided here.
- **Is graph-wide confidence the right scope**, or should each entropy
  dimension eventually carry its own confidence (e.g. "persistence
  entropy is 0.5, computed with 90% confidence")? §5.4 doesn't
  distinguish; this increment implements only the graph-wide reading.
- **How should `R = sum(w_d f_d(E_d)) + beta * sum(gamma_d * max(0,
  Delta_d))`** (§5.3's full drift-aware risk model, combining calibrated
  entropy with drift) **eventually combine with governance invariant
  violations** (§6, not yet built)? The task instructions explicitly warn
  against collapsing these into one "architecture score" — this
  increment stops at `E_total`/maturity/confidence as three separately
  visible numbers and does not attempt the combined `R`.

## Scope check

No baseline/drift storage, no invariant DSL, no invariant-violation
contribution to risk, no JSON/reporting serialization, and no changes to
any of the four entropy calculators themselves. This increment adds the
aggregation/calibration/maturity/confidence layer on top of metrics
already built, consuming their existing `OptionalDouble` outputs
unchanged.
