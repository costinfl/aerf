# Increment 5 — Cycle Entropy

## Objective

Implement the second entropy metric, cycle entropy (AERF v0.4 §4.2):
`E_C = nodes participating in relevant SCCs / total nodes`, where
"Trivial single-node SCCs are excluded unless self-cycles are explicitly
governed."

## Scope

New package `org.aerf.analysis.metrics.cycle` in `aerf-analysis`:
- `StronglyConnectedComponents` (package-private) — Tarjan's algorithm
  over a subset of the graph's edges (configurable relation set,
  resolved endpoints only), deterministic by construction (traversal
  order follows the graph's own insertion-ordered node/edge iteration).
- `CycleEntropyCalculator` — filters Tarjan's output down to "relevant"
  SCCs per §4.2's exclusion rule, with an `includeSelfCycles` flag for
  the "unless explicitly governed" exception.
- `CycleEntropyResult` — keeps the actual participating SCCs (as
  `List<Set<NodeId>>`), not just a count, and a `value()` that is an
  empty `OptionalDouble` when the graph has zero nodes (mirroring
  `LayerEntropyResult`'s undefined-vs-zero distinction from Increment 3).
- 12 new tests: 5 direct algorithm tests (isolating SCC correctness from
  entropy-specific filtering) and 7 calculator tests, including one that
  reuses the Increment 1 fixture graph directly and confirms it is
  acyclic (`E_C = 0`).

## Implementation decisions not dictated by v0.4

1. **Cycle-relevant relations default to `{CALL, DEPENDS}`**, for the
   same reason as `LayerEntropyCalculator` (Increment 3): §4.2 doesn't
   name a default set for this metric either, so the set from §6.3's
   worked invariant example is reused rather than invented fresh, and
   left configurable.
2. **Self-loop detection is scoped to the same relevant-relation set** as
   cycle detection itself — a self-loop via an irrelevant relation (e.g.
   a `CONFIGURES` edge from a node to itself) does not count, for
   consistency with "only relevant edges may form or be part of a
   cycle."
3. **SCC algorithm implemented recursively**, favoring clarity and direct
   testability over handling arbitrarily deep graphs. Flagged as a known
   limitation rather than solved now, per the instruction not to
   prematurely optimize for enterprise scale — see Open Questions.
4. **`StronglyConnectedComponents` is package-private**, not part of the
   public API. AERF v0.4 doesn't ask for SCC computation as a
   general-purpose reusable capability; only the entropy metric built on
   top of it is a public concept from the specification.

## Evidence — what this increment proves or exposes about the AERF model

- Cycle entropy composes cleanly with the rest of the pipeline without
  any changes to the domain model: it consumes the same `Graph` and
  `Edge`/`NodeRef` shapes layer entropy and role inference already use,
  confirming the canonical model's edge/relation representation is
  general enough for a structurally different kind of metric (global
  graph topology, not per-edge classification).
- Running it against the Increment 1 fixture confirms that graph is
  acyclic (`E_C = 0`), which was assumed but never previously verified —
  a small, free cross-check that the fixture's deliberate layering
  violation is a legitimate architectural bypass, not an accidental
  dependency cycle.
- The "trivial single-node SCC" exclusion and its self-cycle exception
  are directly testable and behave exactly as worded in §4.2 once
  self-loop detection is scoped to the same relation set as everything
  else — no ambiguity comparable to Increment 3's layer-ordering problem
  turned up here. §4.2 is more precisely specified than §4.1 was.

## Open questions for the architecture

- **Stack depth on deep, non-cyclic dependency chains.** The recursive
  Tarjan implementation will overflow the JVM call stack on a
  sufficiently long chain (this is plausible in a real legacy
  application with deep call hierarchies, even without any actual
  cycles). An iterative rewrite is straightforward but was not done here
  to keep this increment small; it should happen before this is run
  against a real system of nontrivial size, not held as a permanent
  limitation.
- **Should cycle entropy be scoped by role or module, not just globally?**
  §4.2 as written measures cycles across the whole graph. A legacy
  system might reasonably tolerate cycles within a single module while
  forbidding them across module boundaries (a common governance
  distinction in practice). v0.4 doesn't distinguish these, and this
  increment implements only the global form; a scoped variant, if
  needed, is a governance-configuration extension for later, analogous
  to how `LayerPolicy` externalized layering rules.

## Scope check

No domain model changes, no other entropy dimension (persistence,
security, modal, frontend), no invariant DSL, no calibration/aggregation,
and no OpenRewrite integration. This increment adds exactly one metric,
independent of role inference or layer entropy — it only needs the
canonical graph and edge relations, not roles at all.
