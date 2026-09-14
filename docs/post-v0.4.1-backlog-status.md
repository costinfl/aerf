# Post-v0.4.1 Backlog — Status Tracker

A living index of the inherited backlog defined in
`docs/aerf-post-v0.4.1-backlog.md`. One row per open question: what was
decided, why, where it was implemented, and what evidence supports it.

This file exists to satisfy that backlog's Definition of Done §13.1 —
"every commissioned backlog item has a recorded decision" — in one place,
the same way `docs/open-questions-register.md` does for the v0.4 era. The
full reasoning for each decision lives in its own increment document; this
is the index, not the record.

**A decision is not a specification change.** Nothing here advances the
AERF documentation version. The conceptual baseline stays **v0.4.1**;
there is deliberately no v0.4.2 and no v0.5. The implementation line for
this phase is **0.2.0-SNAPSHOT**.

## Status vocabulary

- **OPEN** — not yet started.
- **DECIDED / NO CODE CHANGE** — question answered from evidence; the
  existing behaviour was deliberately kept, and is now pinned by tests
  that did not exist before.
- **DECIDED / IMPLEMENTED** — question answered and the decision built.
- **PARTIALLY DECIDED** — one half settled, a named remainder explicitly
  still open. The remainder must be stated, not implied.
- **GATED** — deliberately not started; the gate condition is named. A
  gated item is not "pending work", it is work that must not begin until
  its evidence appears.
- **BLOCKED** — cannot proceed until a named earlier decision lands.

## Tier 1 — evidence-driven

| OQ | Question | Status | Decision | Increment |
|---|---|---|---|---|
| OQ-08 | Should the N+1 heuristic constrain the source side? | **DECIDED / NO CODE CHANGE** | **No.** The only true positive on real code (legacy petclinic's intra-repository N+1) has a `PERSISTENCE` source, so every candidate restriction discards it; an allowlist would also drop `UNKNOWN`-sourced contexts, shrinking the denominator precisely when role inference is incomplete. The iteration signal already sits on edge provenance, which is more precise than any source-node proxy. Behaviour pinned by 6 new tests. | 22 |
| OQ-11 | Should the security concern become a closed vocabulary? | **PARTIALLY DECIDED** | **Identifiers: yes; enum: no; DSL: not yet.** `SecurityConcern.XSS` plus `requireCanonical` (lowercase hyphenated form, enforced at both construction points) gives stable machine-referenceable identifiers while leaving the set open, since §4.4 names concerns as examples rather than a closed set. **Still open:** the DSL half. `PropertyKey` has no security-related constant and `InvariantEvaluator` never sees a `SecurityEntropyResult`, so an invariant still cannot reference a concern — which was OQ-11's original motivation. Reaching it means designing finding-scope evaluation, a security-model question this item's acceptance criteria forbid answering. Guarded by `theInvariantDslStillCannotReferenceAConcern`, which fails if such a key is ever added. | 23 |
| OQ-13 | Should confidence also exist per entropy dimension? | **DECIDED / IMPLEMENTED** | **Yes for the three relation-based dimensions; security explicitly undefined.** New `DimensionConfidence.forRelations(Graph, Set<RelationType>)` beside an untouched `AnalysisConfidence`; each calculator exposes `confidence(Graph)` supplying its own relation scope. Role and policy filters are excluded from **both** numerator and denominator — Amendment 7 forbids a role outcome affecting confidence, and including persistence's target-role filter would make that dimension trivially 1.0. Security measures over nodes, which are present by construction, so it returns `OptionalDouble.empty()` rather than a degenerate 1.0. Empty denominator is undefined, never 1.0. Reaches the JSON report as an additive `confidenceByDimension` key beside the unchanged flat `confidence`; **stops there** — no storage, API or dashboard surface. Verified reachable via the CLI, and a real spring-petclinic scan is byte-identical to the increment-20 rescan apart from the new key. | 24 |

## Tier 2 — governance configuration foundation

| OQ | Question | Status | Decision | Increment |
|---|---|---|---|---|
| OQ-02 | Governance evidence / rule authorship boundary | **PARTIALLY DECIDED** | **Representation and boundary: decided and implemented.** Four authorship classes — engineering input, detection catalog, governance policy, and measurement definition (fixed by §4, declared by nobody). `GovernancePolicy` gathers the organization's four declarations with **no default for any of them**, so no governance choice can be made silently; `DetectionCatalog` gathers the three technology catalogs; `PipelineConfig` becomes `(ExtractionRequest, DetectionCatalog, GovernancePolicy)`. The grouping is provable by quotation — each member's own javadoc already calls itself governance, and §4.2 says self-cycles are "explicitly governed". `LayerPolicy` is now readable (its matrix was write-only), with storage canonicalized on `Role` declaration order after measurement showed `Collectors.toMap` over `Role` keys yields different orders across JVM runs. Serialized under one additive `governance` key. **Still open:** §3.3's Governance *evidence* class — governance still cannot assign or override a role, which was register entry §2's original motivation. `RolePrecedence` has no notion of authorship (a governance declaration would lose to a naming heuristic) and `Evidence` models only what was observed, so this needs its own decision, is a measurement change, and collides with gated OQ-05. Guarded by `GovernanceBoundaryTest`. | 25 |
| OQ-04 | Per-subsystem layer matrices | **DECIDED / IMPLEMENTED** | **Yes, and the blocker dissolved rather than being solved.** A subsystem is *governance-declared*, never source-derived — an architectural assertion, so it sits on Increment 25's governance side — which meant no model change, no new relation and no extraction change. `SubsystemLayerPolicy(name, idPrefix, layerPolicy)` selects members by a prefix over the node id; this keeps `NodeId` opaque to AERF, which only calls `startsWith` and never parses or interprets, while the prefix's meaning belongs to the governance author who knows their own adapter's convention. An opaque `Predicate` was rejected as unserializable and thus incompatible with Increment 25's inspectability criterion. **The source's matrix governs an edge** — layering constrains what a component may depend on — which guarantees exactly one matrix per edge, so the denominator cannot move. Overlapping selectors are **rejected**, not resolved by specificity, since most-specific-wins is a derivation and would let a typo silently reassign nodes. `layerPolicy` becomes the default matrix, so declaring no subsystems is the prior behaviour on the same code path. Demonstrated on real code: petclinic with `owner` strict and `vet` legacy moves layer entropy 14/21 → 11/21 with the denominator unchanged at 21. | 26 |
| OQ-06 | Cycle entropy scope below the whole graph | **PARTIALLY DECIDED** | **Scoped measurement: yes, implemented.** Cycle entropy is now reported per declared subsystem beside an unchanged graph-wide value, computed strictly *after* `StronglyConnectedComponents.find` so the hard constraint ("do not alter SCC detection") holds, pinned by `sccDetectionSeesTheSameGraphWhateverIsDeclared`. **Both** numerator and denominator scope to the subsystem's own nodes: cycle entropy's denominator is the node population itself, so scoping only the numerator would change the metric's meaning rather than narrow it. A cross-subsystem SCC is owned by neither side — each counts the nodes it claims — because an SCC is a set and can straddle a boundary, unlike increment 26's edges. A subsystem claiming no node reads **undefined**, not 0.0, distinct from a measured 0.0 meaning it has nodes and none are in a cycle. Increment 26's fused `SubsystemLayerPolicy` was split into `Subsystem` + optional matrix so one declaration serves both scoped dimensions. **Still open:** the *tolerance* half — the register §6 motivation, "tolerate cycles within one module while forbidding them across module boundaries". That is a relevance policy: it would change §4.2's own definition of which SCCs count, and **no repository in this project's evidence base contains a cycle at all** to calibrate it against (§10.4). Guarded by `governanceCannotDeclareACycleIrrelevantSoOq06sToleranceHalfRemainsOpen`. | 27 |

## Tier 3 — governance-facing risk

| OQ | Question | Status | Decision | Increment |
|---|---|---|---|---|
| OQ-09 | Approved exceptions and persistence findings | OPEN — unblocked by increment 25; `GovernancePolicy` is the surface an exception would be declared on | — | 28 |
| OQ-15 | Invariant aggregation `E_inv` | OPEN — unblocked by increment 25; still blocked on invariants carrying no weight (blocker 2 below), so λ_k has nowhere to live | — | 29 |
| OQ-14 | Drift-aware risk model `R` | BLOCKED on OQ-15 | — | 30 |
| OQ-16 | Unified governance-facing view | BLOCKED on OQ-14 | — | 31 |

## Parallel and continuous

| OQ | Question | Status | Decision | Increment |
|---|---|---|---|---|
| OQ-10 | CSRF and mass-assignment modelling | OPEN — first step is an evidence-availability assessment, not implementation | — | — |
| OQ-05 | Non-monotonic (revising) role refinement | GATED — begins only if a real scan produces a demonstrably wrong role that revision would correct | — | — |
| OQ-01 | When does seed-only role classification actually fail? | OPEN (continuous) — served by each real-repository scan | Evidence added in increment 22: re-scanning modern spring-petclinic on current code moved it from 0 to 8 `PERSISTENCE` nodes, and from *unmeasurable* to measured on persistence, layer, total entropy and maturity. Increment 20's adapter fix is confirmed on real code. Still no case found where seed **plus current refinement** produces a wrong role. Increment 24 re-scanned the same repository from a different directory four increments later: the graph, every role assignment and every metric came back byte-identical in the same order, which is direct real-code evidence for determinism (V04-ROLE-01) and again surfaced no misclassification for OQ-05's gate. | 22, 24 |

## Known blockers found before work started

Recorded here because they were established by reading the code, not by
speculation, and they change what the affected items can even mean:

1. **No subsystem/module concept exists in the canonical graph.**
   `Node` is `(id, type, role, attributes, evidence)` with no package or
   module field, and while `NodeType.MODULE` is declared, the extractor
   never emits one. OQ-04 and OQ-06 both presuppose subsystem membership,
   so identifying a subsystem is itself a decision that must precede them.
2. **No invariant carries a weight.** `Invariant` has a free-text
   `severity` and nothing numeric, so `E_inv`'s `lambda_k` (OQ-15) has
   nowhere to live yet.
3. **A security concern is unreachable from the invariant DSL.**
   `PropertyKey` has exactly eight constants and `InvariantEvaluator`
   never receives a `SecurityEntropyResult`, so OQ-11's stated motivation
   ("so invariants can reference concerns reliably") cannot be satisfied
   by a vocabulary choice alone.
4. **Governance configuration is exclusively programmatic.** No
   YAML/JSON/properties parsing dependency or resource loading exists in
   any module; `PipelineConfig` is the entire governance surface. A
   declarative format would be new infrastructure, not a refactor.
   *Still true after increment 25* — that increment named and grouped the
   governance surface, it did not make it declarative.

## Findings recorded by increments 25-27, deferred to a named owner

Each is guarded by a test in `GovernanceBoundaryTest` or
`GovernanceReportEndToEndTest`, so it is reopened deliberately rather
than discovered by accident.

| # | Finding | Owner |
|---|---|---|
| A | **Relation scope is not governable.** Which relations each dimension measures over is composed by `Pipeline` from public, documented calculator factories citing §6.3's worked example. Decided as *measurement definition* fixed by §4 — not hidden, not a default, and not introduced by increment 25. Making it configurable is a policy input, and `DimensionConfidence` reads the same set, so one wrong plumb would move layer entropy and three confidences at once. | **Unowned.** OQ-06 closed without taking it: increment 27 scoped the measurement without touching relation scope, and nothing since has claimed it. Needs an owner before it can be decided. |
| B | **`EntropySnapshot` carries no governance identity**, so comparing a stored baseline against a current scan is only sound if policy was identical, and nothing in the data says whether it was — a policy change can read as code drift. Increment 25 delivered the enabling half: a `PipelineReport` now carries the policy it was measured under, so a caller storing a baseline has something to store. Fixing the rest forces "what does `Drift.compute` do when the two policies differ?", which is drift semantics. | OQ-14 (increment 30) |
| C | **Concern selection is fused into the detection catalog.** Which code pattern signals a concern is engineering; which concerns an organization cares about is arguably governance. One interface does both. Separating them would filter the security opportunity denominator, making it a policy input. | OQ-10 / a future security-model decision |
| D | **An invariant's predicates are not serialized.** `GovernanceJson` emits name, scope, severity and referenced metric names only; there is no `Predicate` serializer anywhere, and writing one means designing the invariant DSL's textual form, which v0.4 leaves unimplemented. The `governance` key is therefore explicitly partial, and a governance hash was rejected for exactly this reason. | OQ-11's DSL half / GOV-02 |
| E | **Which subsystem judged an edge is not reported.** `LayerEntropyResult` carries relevant and violating edges but not the governing matrix, so a reader must infer from the declared prefixes which declaration produced a violation. Per-subsystem *reporting* is a distinct concern from per-subsystem *measurement*, which increment 26 delivered. | OQ-16 (increment 31) |
| F | **Nested subsystems are unrepresentable.** `Subsystems` rejects a prefix that would also claim another's nodes, so "everything in `com.foo` except `com.foo.bar`" cannot be declared. Resolving by specificity was rejected as a derivation. Since increment 27 this limits cycle scope as well as layer matrices. | a future decision, if evidence appears |
| G | **Cross-subsystem layer semantics are untested by real code.** Every relevant layer edge in the petclinic evidence base is intra-package, so "the source's matrix governs" is decided on principle and tested synthetically only. | revisit when a multi-module repository enters the evidence base |
| H | **A cycle-tolerance policy is not implemented.** OQ-06's other half — letting governance declare a cycle confined to one subsystem irrelevant. It would change §4.2's own definition of which SCCs count rather than extend governance configuration, and there is **no cycle anywhere in the evidence base** to calibrate it against: all eight committed sample reports show `cycleEntropy: 0.0` with zero SCCs, across both petclinics. Guarded by test. | revisit when a real repository with a cycle enters the evidence base |
| I | **Cycle entropy has no real-repository demonstration.** Increment 27's semantics are exercised end to end only against the new `cyclic-sample` fixture, because no scanned repository contains a cycle. Unlike OQ-04, which moved a real measurement 14/21 → 11/21 on petclinic. | same as H |
