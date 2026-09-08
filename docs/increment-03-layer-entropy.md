# Increment 3 — Layer Entropy

## Objective

Implement the first entropy metric, layer entropy (AERF v0.4 §4.1),
against roles already present on a graph (whether hand-assigned, as in
the Increment 1 fixture, or produced by Increment 2's seed role
inference — this metric doesn't care which).

## Scope

New package `org.aerf.analysis.metrics.layer` in the existing
`aerf-analysis` module:
- `LayerPolicy` — an explicit, governance-declared allowed-transitions
  matrix between roles.
- `LayerEntropyCalculator` — computes `E_L = violating relevant edges /
  total relevant edges` (§4.1) against a graph and a policy.
- `LayerEntropyResult` — keeps the actual relevant/violating `Edge`
  instances, not just counts, and exposes `value()` as an `OptionalDouble`
  that is empty (not `0.0`) when there are no relevant edges.
- 8 new tests (4 for `LayerPolicy`, 4 for `LayerEntropyCalculator`),
  including one run directly against the Increment 1 fixture graph.

## A real ambiguity found while designing this, and how it was resolved

§4.1 gives a "typical allowed ordering" — `Presentation → Application →
Domain → Persistence → Infrastructure` — and separately requires the
relation matrix to "remain governance-configurable rather than
hard-coded as universal architecture." The natural implementation move is
to mechanically derive an allowed-pairs matrix from that ordering. Two
derivations were tried and **both contradict a case v0.4 itself treats as
settled**:

- **Adjacent-only** (a role may call itself or the next role in the
  chain) would flag `Application → Persistence` — skipping `Domain` — as
  a violation. That is an ordinary, unremarkable pattern (a service
  calling a repository directly) and Increment 1's fixture graph treats
  it as legitimate.
- **Forward-only** (a role may call any later role in the chain) would
  **not** flag `Presentation → Persistence` as a violation. But §6.3's
  own worked invariant example, `no_presentation_to_persistence`, treats
  exactly that transition as a critical violation, and Increment 1's
  fixture graph calls it out by name as "a deliberate layering
  violation."

Since no single mechanical rule matches the framework's own examples,
`LayerPolicy` does not derive a matrix from an ordering at all — it only
accepts an explicit, hand-declared matrix. This is not a workaround; it
is what "governance-configurable... rather than hard-coded" means taken
literally. Per the agent instructions ("stop at the relevant boundary;
explain the issue... propose the smallest possible resolution"), the
smallest resolution was to remove the derivation entirely rather than
pick one of the two contradicting defaults and hide the disagreement.

## Other implementation decisions not dictated by v0.4

1. **"Relevant" edge relations default to `{CALL, DEPENDS}`**, taken
   directly from §6.3's own worked invariant example
   (`edge.label in [CALL, DEPENDS]`) rather than invented, but left
   configurable on `LayerEntropyCalculator` rather than hardcoded.
2. **An edge is excluded from both numerator and denominator** (not
   counted as compliant, not counted as violating) when either endpoint
   is `NodeRef.Unresolved`, or when either endpoint's role is not part of
   the policy's declared role vocabulary (`LayerPolicy.knowsRole`). This
   applies to `Role.UNKNOWN` nodes and to any role a governance policy
   simply chose not to take a position on (e.g. `External`, in the test
   fixture policy). This is an application of §3.5's uncertainty
   principle to a metric: what cannot be judged must not be silently
   folded into either side of the ratio.
3. **`value()` returns an empty `OptionalDouble`, not `0.0`, when there
   are no relevant edges.** A bare `0.0` would misreport "no deviation
   detected" when actually nothing was measurable at all — a materially
   different claim that downstream governance consumers need to be able
   to tell apart.
4. **The test fixture's `LayerPolicy` is declared by hand in the test**,
   not offered as a reusable AERF default, since (per the ambiguity
   above) no default currently exists that the framework's own examples
   would endorse.

## Evidence — what this increment proves or exposes about the AERF model

- Running the calculator against the Increment 1 fixture with a matrix
  matching the fixture's own stated intent gives `E_L = 1/4 = 0.25`,
  correctly isolating the one deliberate `Presentation → Persistence`
  violation while correctly not flagging the ordinary
  `Application → Persistence` call — confirming the metric's edge
  classification logic is sound once given an unambiguous matrix.
- The ordering-vs-matrix ambiguity above is a genuine gap between how
  §4.1 is worded (an ordering) and how it must actually be operated (an
  explicit matrix) — worth carrying into whatever later increment
  designs the governance DSL (§6), since the DSL will need a concrete
  syntax for declaring this matrix, not just an ordering.

## Open questions for the architecture

- Should AERF eventually ship a suggested *default* matrix derivation
  after all (e.g. requiring governance to explicitly opt in to
  layer-skipping per role-pair, rather than declaring the whole matrix
  from scratch every time)? This increment takes no position beyond
  "not automatically, and not silently."
- `LayerPolicy` currently only supports a single flat matrix. If
  different subsystems of a legacy application should be judged by
  different layering rules (plausible in an enterprise system that
  evolved through several architectural eras), a future increment will
  need a way to scope a policy to part of the graph — not attempted here.

## Scope check

No changes to the domain model, no role-inference iteration, no other
entropy dimension (cycle, persistence, security, modal, frontend), no
invariant DSL, no calibration/aggregation, and no OpenRewrite
integration. This increment adds exactly one metric, consuming — not
producing — roles.
