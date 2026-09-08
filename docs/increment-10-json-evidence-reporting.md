# Increment 10 — JSON Evidence/Reporting

## Objective

Implement AERF v0.4 §11's final MVP item: "JSON evidence/reporting" —
the last stage of the canonical pipeline (§7: "...Invariant evaluation →
Calibration/risk aggregation → Governance + engineering reports"). This
closes out every item on §11's "First validation" table.

## Scope

New module `aerf-report`, depending on `aerf-model` and `aerf-analysis`
— justified the same way `aerf-analysis` was in Increment 2: there is
now real, varied content across three modules worth serializing, not
speculative scaffolding.

- `org.aerf.report.json`: a small, closed, dependency-free JSON value
  algebra (`JsonValue`: object/array/string/number/boolean/null),
  `JsonObjectBuilder` for ergonomic construction, and `JsonWriter` for
  text serialization.
- `org.aerf.report`: mappers from AERF's already-typed domain objects to
  `JsonValue` —
  - `GraphJson`: the canonical graph itself (nodes, edges, evidence,
    provenance) — "JSON evidence" in the most literal sense.
  - `MetricsJson`: all four entropy calculators' results (Increments 3,
    5, 6, 7), including their full evidence lists, not just ratios.
  - `InvariantJson`: invariant evaluation results (Increment 9),
    including every violation's subject and rationale.
  - `CalibrationJson`: individual calibration outputs (Increment 8) —
    total entropy, maturity, maturity level, confidence — as
    independent values, not a combined report object (see below).
- 24 new tests across all four modules' worth of mapped output.

## Why no JSON library dependency

The project has been dependency-free (beyond JUnit) through nine
increments. Adding one now for serialization was considered and
rejected: §14's determinism principle — "identical source/configuration
produces reproducible results" — is easiest to guarantee when this
project controls key ordering and number formatting itself, rather than
depending on a third-party library's version-specific behavior (which
could reorder object keys via reflection, or change its own formatting
conventions across releases without this project noticing). `JsonValue`
is a closed, six-variant algebra sized exactly to what needs
serializing — not a general-purpose JSON binding layer — so hand-writing
it is proportionate, not premature.

`JsonValue.JsonObject` reapplies Increment 1's own lesson directly:
`Map.copyOf` does not preserve iteration order, so it wraps an explicit
`LinkedHashMap` instead — the same fix, the same reasoning, now needed a
second time in a second module, which is itself a small confirmation
that determinism requires active guarding, not a one-time fix.

## Write-only, by design

This increment implements serialization only — there is no JSON
*reader*. A parser is required by two already-deferred items: loading a
stored baseline for §5.3's drift computation, and a textual/YAML
frontend for the invariant DSL (§6.3 explicitly separates "transport
format" from the semantic model, per Increment 9). Both are still open;
building a reader now, before either consumer exists, would be
speculative. "Reporting" is naturally an output concern first.

## Implementation decisions not dictated by v0.4

1. **Undefined values serialize to JSON `null`, never `0` and never an
   omitted field.** This is the same undefined-vs-zero discipline every
   entropy calculator and the calibration layer maintain in code,
   carried through to the serialized form — a report reader must be able
   to see "not measurable" as distinct from "measured and clean," the
   same way an in-process caller of `OptionalDouble` can.
2. **No combined "governance report" JSON schema.** `CalibrationJson`
   serializes `AggregatedEntropy`, `Maturity`, `MaturityLevel`, and
   `AnalysisConfidence` as independent values because no composite type
   bundling them exists yet — inventing one now would silently answer
   open question #16 (how entropy, drift, and invariant violations
   should combine into one governance view) rather than deciding it
   deliberately.
3. **Numbers serialize via `Double.toString(value)`** (Java's shortest
   round-tripping decimal representation), not rounded to a fixed
   decimal count. Rounding for readability would lose precision an exact
   reproducibility check might need — the same tension "measurement
   before aggregation" resolves elsewhere in favor of keeping raw
   values, applied here to display formatting.
4. **`CycleEntropyResult`'s SCC node-id sets are sorted before
   serialization**, even though `Set<NodeId>` itself has no guaranteed
   order. Without this, two structurally identical graphs could produce
   byte-different JSON depending on incidental `HashSet` iteration
   order — an easy, otherwise-invisible determinism leak at exactly the
   serialization boundary this increment exists to make trustworthy.

## Evidence — what this increment proves or exposes about the AERF model

- Every domain type built across nine prior increments — `Node`, `Edge`,
  `Evidence`, `NodeRef`, four `*EntropyResult`s, `InvariantEvaluationResult`,
  `MaturityLevel` — mapped to JSON with no design surprises and no
  changes needed to any of those types. That's a real (if quiet)
  confirmation that the "keep raw evidence alongside every ratio"
  pattern, followed independently in Increments 3 through 9, produced
  types that were already report-shaped without having been designed
  for reporting.
- Writing `JsonValue.JsonObject`'s order-preservation fix reproduced
  Increment 1's `Map.copyOf` finding almost exactly. That repetition is
  itself evidence worth recording: a "watch for `Map.copyOf`" note in
  one increment's documentation didn't prevent the same mistake from
  being freshly written a second time in a different module — worth
  remembering as a general lesson about how easy this specific pitfall
  is to reintroduce, not just a one-off bug.

## Open questions for the architecture

No new entries — this increment's two open decisions (combined report
schema, and how reporting relates to the still-deferred baseline/drift
and invariant-parser work) are the same ones already tracked as
register items #12, #14, and #16. Completing JSON reporting is what
finally unblocks meaningful progress on those, rather than raising new
ones.

## Scope check

No JSON parser/reader, no combined governance-report document format,
no baseline storage, no textual invariant syntax, and no changes to any
domain type in `aerf-model` or `aerf-analysis`. This increment adds pure
serialization on top of types that already existed, completing every
item on §11's MVP freeze table.
