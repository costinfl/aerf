# Increment 1 — AERF Core Domain Model

## Objective

Establish the AERF v0.4 canonical graph representation — the structure the
rest of the pipeline (extraction, role inference, metrics, invariants,
calibration, reporting) will be built on. No behavior beyond construction
and inspection is implemented in this increment.

## Scope

- Maven reactor (`pom.xml`) with a single module, `aerf-model`.
- Canonical model types: `NodeType`, `Role`, `RelationType`, `NodeId`,
  `Node`, `NodeRef`, `Edge`, `Evidence`, `ExtractionFidelity`, `Graph`
  (with a validating `Graph.Builder`).
- Unit tests for identity, immutability, unresolved-endpoint
  representation, and deterministic iteration order.
- A reusable fixture, `CanonicalSampleGraphs.layeredOrderSlice()`, in
  `aerf-model`'s test sources, for reuse by later metric/invariant tests.

## What AERF v0.4 explicitly specifies (implemented as specified)

- The graph as `G = (V, E, tau_V, tau_E, rho, mu)` (§2.1) — `V`/`E` and the
  `tau_V`/`tau_E`/`rho` assignments are implemented; `mu` (derived
  metrics) is intentionally not yet represented.
- `NodeType` enum values: `MODULE, COMPONENT, FUNCTION, DATA, VIEW,
  CONFIG, SCRIPT` (§2.2) — reproduced verbatim, no additions.
- `Role` enum values: `Presentation, Application, Domain, Persistence,
  Infrastructure, External, Unknown` (§2.3) — reproduced verbatim.
- `RelationType` enum values: `CALL, DEPENDS, READS, WRITES, EXTENDS,
  IMPLEMENTS, RENDERS, CONFIGURES, COMMUNICATES` (§2.4) — reproduced
  verbatim.
- `ExtractionFidelity` levels `L1`/`L2`/`L3` (§8.1).
- `Role.UNKNOWN` as a first-class, valid outcome, never fabricated away
  (§3.5).
- Edges carry provenance sufficient to explain their source (§8), and
  adapters must not invent relations merely to complete the graph (§8) —
  enforced structurally by `Graph.Builder` rejecting edges whose
  *resolved* endpoint was never added, and by `NodeRef.Unresolved`
  letting a relation be recorded without a fabricated node.
- Determinism (design principle, §14): node/edge iteration order is
  insertion order, not hash order (see below — this required a
  correction to `java.util.Map.copyOf`, which does not preserve order).

## Implementation decisions not dictated by v0.4 (flagged, not silent)

1. **Node attribute schema.** `Node.attributes()` is an open
   `Map<String,String>`. v0.4 does not define a canonical attribute
   vocabulary per node type, so none is invented here. Concrete adapters
   will decide what keys they populate.
2. **Node identity/equality is by `id` alone.** Two `Node` instances with
   the same id are the same graph entity even with different roles or
   attributes. This is required for role inference's iterative
   refinement (`R^(n+1) = F(R^(n), G)`, §3.2) to update a node's role
   across iterations without that being a different node. `Node.withRole`
   and `Node.withAttribute` return updated copies for that purpose.
3. **`Edge` has no identity field.** v0.4's edge model (§2.4, Appendix A)
   lists `label, source, target, provenance` — no id. Since the graph is
   explicitly a multigraph, two edges with identical endpoints/relation
   but different evidence are both legitimate and are stored as distinct
   entries in a `List<Edge>` (not a `Set`), preserving multiplicity and
   insertion order.
4. **`NodeRef` (resolved/unresolved edge endpoints).** Not explicitly
   named in v0.4, but required to satisfy two explicit v0.4 requirements
   simultaneously: adapters must be conservative and not invent nodes
   (§8), while unresolved information must remain representable rather
   than dropped or fabricated (§3.5). `NodeRef.Unresolved` carries a
   human-readable reason instead of a `NodeId`.
5. **Evidence is always "known."** `Evidence` represents something
   actually observed; it has no "unresolved" variant. Absence of
   knowledge is represented at the point it occurs — `Role.UNKNOWN` for
   roles, `NodeRef.Unresolved` for edge endpoints — rather than inventing
   a parallel "unresolved evidence" concept alongside them.
6. **Reactor shape.** Only `aerf-model` exists as a module. `aerf-analysis`,
   `aerf-extraction`, `aerf-oprewrite`, `aerf-report` are deliberately not
   created yet — they would be empty scaffolding with no content to
   justify their existence at this increment.
7. **Fixture location.** `CanonicalSampleGraphs` currently lives in
   `aerf-model`'s test sources (`src/test/java`), which limits reuse to
   `aerf-model` itself. When `aerf-analysis` is introduced and needs the
   same fixture, it should move to a shared test-fixtures artifact rather
   than being duplicated.

## A correctness issue found and fixed during this increment

`java.util.Map.copyOf(...)` does **not** guarantee that iteration order
matches the source map, even when the source is a `LinkedHashMap`. An
early version of `Graph` and `Node` used `Map.copyOf` for the immutable
node map and attribute map; this silently violated the determinism
principle (§14: "identical source/configuration produces reproducible
results") the moment a graph had more than a handful of nodes. Fixed by
wrapping an explicit `LinkedHashMap` in `Collections.unmodifiableMap`
instead. `GraphTest` now asserts insertion-order iteration directly with
ids chosen so that natural `String` hash order would not coincide with
insertion order, so this regression cannot silently return.

This is exactly the kind of implementation difficulty the process is
meant to surface: v0.4 states determinism as a principle but (correctly,
per its own scope boundary in the Document Status section) leaves *how*
to guarantee it at the API level as an implementation concern.

## Open questions for the architecture (not resolved by this increment)

- **Role conflict precedence (§3.4)** — `External > Persistence >
  Infrastructure > Application > Domain > Presentation` — is stated as an
  unvalidated hypothesis. Increment 1 does not implement role inference
  at all, so this is not yet exercised; flagging it here so it isn't lost
  before the role-inference increment.
- **Attribute vocabulary.** Whether AERF v0.5 should standardize a
  minimal attribute schema per `NodeType` (e.g. a canonical "qualified
  name" attribute) is left open; v0.4 does not specify one.
- **Edge multiplicity semantics for metrics.** Layer entropy (§4.1) is
  defined as "violating relevant edges / total relevant edges." Because
  the model is a multigraph, two `CALL` edges between the same two nodes
  backed by two different call sites count as two edges. Whether that is
  the intended unit of measurement (call-site level) versus a
  node-pair/relation level count is not stated in v0.4 and will need a
  decision before layer entropy is implemented.

## Evidence — what this increment proves or exposes about the AERF model

- The v0.4 canonical model (§2.1–§2.4, Appendix A) is expressible as a
  small, dependency-free Java API (9 classes, ~450 lines including
  javadoc) without needing to compromise any stated concept.
- The tension between "conservative adapters" (§8) and "preserve
  unresolved information" (§3.5) is real and needed an explicit
  mechanism (`NodeRef`) to satisfy both — v0.4's prose states both
  requirements but does not name the mechanism connecting them.
- Determinism (§14) is not free — a natural, idiomatic Java choice
  (`Map.copyOf`) silently breaks it. Anyone implementing this model needs
  to know that upfront rather than discovering it once metrics disagree
  between two runs of the same input.

## Scope check

No entropy metrics, role inference logic, invariant DSL, calibration,
baseline/drift, confidence computation, reporting/serialization,
OpenRewrite integration, or any technology adapter were implemented.
This matches the MVP freeze (§11) and the task's "First Implementation
Phase" instruction to start with only the canonical representation.
