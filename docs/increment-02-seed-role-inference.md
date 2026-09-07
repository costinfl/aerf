# Increment 2 — Seed Role Inference

## Objective

Implement the first half of AERF v0.4's role inference model (§3): the
seed step, `R^(0) = R_seed` (§3.2). Role inference is called out as
foundational in v0.4 ("If role classification is unreliable, layer
metrics, invariants, governance mappings, and risk aggregation become
unreliable"), so it comes before any entropy metric.

## Scope

- New module `aerf-analysis`, depending on `aerf-model`.
- `RoleInferenceRule` — a pure function from one `Node` to at most one
  candidate `(Role, rationale)`, corresponding to one seed-evidence source
  (§3.2, §3.3).
- `RolePrecedence` — the §3.4 conflict-resolution order, reproduced
  exactly and documented as the spec's own stated hypothesis, not a law.
- `RoleSignal` / `RoleInferenceResult` — every firing rule's candidate is
  kept on the result, not just the winner, satisfying §3.1's
  "Explainable" requirement.
- `SeedRoleInferenceEngine` — evaluates all rules against a node, resolves
  conflicts via `RolePrecedence`, defaults to `Role.UNKNOWN` when nothing
  fires (§3.1 "Total"), and produces the same result regardless of the
  order rules were registered in (§3.1 "Deterministic"/"Stable").
- `DefaultSeedRules` — five small illustrative rules (Spring `@Controller`
  → Presentation, Spring `@Service` → Application, spring-data adapter
  evidence → Persistence, `NodeType.DATA` → Domain, `NodeType.CONFIG` →
  Infrastructure), explicitly marked as provisional/adapter-specific, not
  part of the frozen v0.4 model.
- 11 new tests: totality, determinism, independence from any
  pre-existing role on the node, conflict resolution via the §3.4 order,
  and a self-consistency check against the Increment 1 fixture graph.

## Deliberately not in scope

**The iterative half of §3.2, `R^(n+1) = F(R^(n), G)`, is not
implemented.** v0.4 states role inference proceeds by seeding roles from
a node's own evidence and then refining using graph relationships
(neighbor roles, centrality, dependency direction) until a fixed point.
`SeedRoleInferenceEngine` only does the first part — each node is
classified in complete isolation from the graph it lives in. The class is
named `Seed...` specifically so this is not mistaken for the complete
model. Iterative graph-relationship refinement is left for a later
increment, once there is a concrete case in real evidence that seed rules
alone get wrong (see Open Questions below).

Also not in scope: entropy metrics, the invariant DSL, calibration,
baseline/drift, confidence computation (§5.4 — analysis confidence is a
distinct, not-yet-implemented concept from role inference's own
"Unknown" outcome), and any OpenRewrite-backed extraction. `DefaultSeedRules`
reads only the same `Evidence` objects a real adapter would attach in
Increment 1's model — no new extraction logic was added.

## Implementation decisions not dictated by v0.4

1. **Seed rules are a `List<RoleInferenceRule>` passed into the engine at
   construction**, not hardcoded inside it. v0.4 doesn't say this
   explicitly for role rules, but it does say the analogous layer-entropy
   relation matrix "must remain governance-configurable rather than
   hard-coded as universal architecture" (§4.1); the same reasoning
   applies here, since a rule catalog is inherently
   adapter-and-organization-specific.
2. **`DefaultSeedRules`'s five rules are illustrative, not canonical.**
   They exist to make the engine exercisable against the Increment 1
   fixture and are expected to be replaced once a real adapter (e.g. a
   Spring/OpenRewrite bridge) exists.
3. **Fixture sharing resolved via a test-jar**, not a new module or
   duplicated code: `aerf-model` now publishes its `src/test/java` as a
   test-jar artifact (`maven-jar-plugin` `test-jar` goal), and
   `aerf-analysis` depends on it in test scope. This closes the open item
   flagged in `docs/increment-01-core-domain-model.md` ("fixture location").
4. **`inferAndApply` discards any role already on the input graph's
   nodes.** This treats "Role inference" as an authoritative pipeline
   stage (§7) that determines role from evidence, not one that merely
   patches gaps left by upstream stages. Tested explicitly
   (`inferenceIgnoresAnyPreExistingRoleAlreadyOnTheNode`).

## Evidence — what this increment proves or exposes about the AERF model

- **Self-consistency check passed.** Running seed inference over the
  Increment 1 canonical sample graph reproduces every manually-assigned
  role (Presentation/Application/Domain/Persistence) purely from the
  evidence already attached to each node — no role had to be hand-tuned
  to make this pass. This is a real, if small, falsification opportunity
  that AERF survived.
- **The §3.4 precedence order is exercisable and does something
  concrete.** A constructed node (`legacy.OrderEntity`: `NodeType.DATA`
  plus spring-data evidence) genuinely receives two conflicting seed
  signals — Domain (structural) and Persistence (semantic) — and the
  stated order resolves it to Persistence. Until this test existed, "the
  order is a hypothesis to be tested empirically" was just prose; now
  there is at least one concrete case it can be checked against as more
  evidence accumulates.
- **Totality and explainability compose cleanly.** A node with zero
  matching rules returns `UNKNOWN` with an empty signal list rather than
  an exception or a fabricated guess — confirming §3.5's "Unknown is a
  valid result" survives contact with a real (if minimal) rule engine.

## Open questions for the architecture (not resolved by this increment)

- **When does seed-only classification actually fail, in practice?**
  This increment doesn't yet have a case where `R^(0)` alone gives a
  wrong answer that graph-relationship refinement (`R^(n+1)`) would fix.
  Until one is found (ideally from real evidence, not a synthetic
  example), it's not clear how much complexity the iterative step is
  worth adding, or what its termination/stability behavior needs to
  guarantee.
- **Rule authorship boundary.** §3.3's evidence classes include "Graph"
  (inbound/outbound relationships, centrality, neighboring roles) and
  "Governance" (explicit AERF configuration). Neither is representable by
  the current `RoleInferenceRule` interface, which only sees one `Node`.
  Extending the interface to see the `Graph` (for the graph-evidence
  class) or an external rule configuration (for the governance-evidence
  class) is a design decision for whichever increment tackles §3.2's
  iterative step — flagging now so it isn't designed twice.
- **Confidence vs. Unknown.** §5.4 defines analysis confidence as
  "resolved relevant relations / total extracted relevant relations" — a
  graph-wide coverage measure, distinct from a single node being
  `Role.UNKNOWN`. This increment does not compute confidence; a future
  increment needs to decide how per-node `UNKNOWN` outcomes and the
  graph-wide confidence metric relate (e.g. does confidence count
  `UNKNOWN` nodes as "unresolved"?).

## Scope check

No graph-relationship iteration, no entropy metrics, no invariant DSL, no
calibration/drift, no confidence computation, no OpenRewrite integration,
and no new extraction logic were added. This increment only adds a seed
classification step on top of the Increment 1 model, per the MVP freeze
(§11: "Role inference" is listed as first-validation scope) and the
instruction to implement one foundational concept at a time.
