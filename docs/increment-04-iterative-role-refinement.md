# Increment 4 — Iterative Graph-Relationship Role Refinement

## Objective

Complete AERF v0.4 §3.2's role inference model by implementing the part
Increment 2 deliberately deferred: the iterative refinement step,
`R^(n+1) = F(R^(n), G)`, which uses graph relationships rather than a
node's own evidence in isolation.

## Scope

In the existing `aerf-analysis` module, package `org.aerf.analysis.role`:
- `GraphRoleRefinementRule` — like `RoleInferenceRule`, but sees the whole
  `Graph` and a snapshot of every node's current role (`R^(n)`), not just
  one node's evidence. This is the "Graph" evidence class from §3.3
  (inbound/outbound relationships, dependency direction, centrality,
  neighboring roles).
- `IterativeRoleInferenceEngine` — runs `SeedRoleInferenceEngine` for
  `R^(0)`, then repeatedly applies refinement rules to still-`UNKNOWN`
  nodes until a pass changes nothing (a fixed point).
- `IterativeRoleInferenceResult` — final roles plus the accumulated
  signal trail (seed signals and any refinement signals that fired) and
  the number of refinement passes actually needed.
- `DefaultGraphRefinementRules` (package `org.aerf.analysis.role.graph`)
  — one illustrative rule: an `UNKNOWN` node that `EXTENDS`/`IMPLEMENTS` a
  node with a known role inherits that role.
- A small refactor: `RolePrecedence.winner(List<RoleSignal>)`, extracted
  from `SeedRoleInferenceEngine`'s inline conflict-resolution loop so
  both engines resolve conflicts identically instead of duplicating the
  logic.
- 9 new tests (6 for the engine, 3 for the rule), including one that
  requires exactly two refinement passes to fully resolve.

## The termination gap in v0.4, and how this increment avoids it

§3.2 states inference "terminates when a fixed point is reached," but
does not establish that a fixed point is guaranteed to exist for an
arbitrary refinement function `F` over an arbitrary graph. An
unconstrained `F` (one that can change any node's role based on any
other node's current role) can trivially fail to converge — two rules
that each flip a node's role in response to the other's current role
would oscillate forever.

Rather than resolve this in the abstract, this increment constrains `F`
to a shape where termination is structurally guaranteed instead of
argued for after the fact:

- `IterativeRoleInferenceEngine` only ever calls a refinement rule about
  a node whose current role is `Role.UNKNOWN`.
- A rule may only contribute a concrete (non-`UNKNOWN`) candidate role.
  It cannot be asked about, or change, an already-resolved node.

Under this contract, every pass that changes anything strictly reduces
the number of `UNKNOWN` nodes by at least one, and the graph has
finitely many nodes — so the loop is guaranteed to reach a fixed point in
at most `|V| + 1` passes. A defensive `IllegalStateException` beyond that
bound exists only as a guard against a future rule implementation that
somehow violates the contract; it is not expected to ever fire given the
typed interface as written.

This is a real, if narrow, choice about how much of §3.2's model to
implement: the full generality of "role inference may use graph
relationships including neighboring roles" (which could imply a role
changing based on what its neighbors currently are, not just adopting a
neighbor's settled role) is **not** implemented — only the monotonic,
provably-terminating subset of it is. See Open Questions below.

## Evidence — what this increment proves or exposes about the AERF model

- **Iteration is not cosmetic.** The two-hop chain test
  (`A extends B extends C`, only `C` seeded) genuinely needs two passes:
  a single "graph-aware seed" step (looking at neighbors once, without
  repeating) would resolve `B` but leave `A` at `UNKNOWN`, since `B`'s
  role isn't known yet during the same pass that inspects it. Running
  the engine confirms `passes() == 2` and both `A` and `B` end at
  `Role.PERSISTENCE`. This is a genuine test of §3.2's iterative
  structure, not just its seed half.
- **Monotonicity holds under an adversarial-ish case.** A node seeded
  `Presentation` that also extends a node seeded `Persistence` keeps its
  seeded role — refinement never touches an already-resolved node, even
  though a naive "always recompute from neighbors" design might have
  overridden it.
- **The precedence order composes across both phases.** A node with two
  supertypes of different known roles (`Domain` and `Persistence`)
  resolves to `Persistence` via the same `RolePrecedence` used by seed
  inference — confirming conflict resolution is one shared mechanism,
  not two separate ones that could disagree.

## Implementation decisions not dictated by v0.4

1. **Refinement is restricted to monotonic, UNKNOWN-only transitions.**
   Not stated in v0.4; adopted specifically to make termination provable
   rather than assumed (see above). This is the main way this increment
   narrows the model rather than fully implementing it.
2. **`InheritRoleFromSupertype` is illustrative, not canonical**, same
   caveat as Increment 2's seed rules — v0.4 names the evidence class,
   not a concrete rule.
3. **Conflicting refinement candidates resolve via the same
   `RolePrecedence` as seed signals**, rather than a separate mechanism —
   an implementation choice for consistency, since v0.4 doesn't say
   whether seed-conflict and graph-conflict resolution should be the same
   procedure.
4. **`passes()` counts only passes that changed something** (0 if seed
   roles were already a fixed point), not total loop iterations, so it's
   a meaningful measure of how much the graph-relationship step
   contributed beyond seeding.

## Open questions for the architecture

- **Non-monotonic refinement is out of scope here.** §3.3's "Graph"
  evidence class also mentions centrality and general neighboring roles,
  which could plausibly justify a rule that revises an *already-assigned*
  role (not just fills in `UNKNOWN`) based on its neighborhood — e.g. "a
  node classified Domain by a weak seed signal, but surrounded entirely
  by Persistence neighbors, might actually be Persistence." Implementing
  that would reintroduce the general termination problem this increment
  sidesteps, and needs its own convergence argument (e.g. a bound on how
  many times any single node may be revised) before it should be
  attempted.
- **Confidence interaction, still open from Increment 2.** §5.4's
  analysis confidence (resolved relevant relations / total extracted
  relevant relations) is still not implemented. Now that role resolution
  can depend on a multi-pass process, it's an open question whether
  confidence should reflect *how many passes* a node needed (a node
  resolved only in pass 4 of 4 is arguably less certain than one seeded
  directly), or purely the evidence-coverage definition given in §5.4.

## Scope check

No entropy metric changes, no invariant DSL, no calibration/drift, no
confidence computation, and no OpenRewrite integration. This increment
completes §3.2's role inference model within the constrained (monotonic)
scope described above; it does not implement the more general
neighbor-revision behavior §3.3 gestures at, and says so rather than
quietly deciding the narrower version is "the" model.
