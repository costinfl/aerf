# AERF v0.4.1 — Patch Notes

Status: living amendment to *AERF v0.4 — Canonical Pre-Implementation
Documentation*, adopted from evidence gathered while building the
reference implementation (Increments 1–6). This document does not
replace v0.4; it amends specific sections, each entry citing the
original text, the problem the implementation ran into, the amendment
adopted, and the increment that produced the evidence. Future amendments
should append a new numbered entry rather than editing an existing one,
so the record of *why* the model changed stays intact.

Authority: these amendments were adopted with explicit sign-off from the
project owner (2026-09-08), under the standing instruction that
implementation-discovered inconsistencies may be resolved directly, with
either a documentation note or a spec patch, rather than necessarily
blocking on a fresh question each time — see Agent Behavior §12 in the
implementation-agent instructions, which this document satisfies for the
items below.

---

## Amendment 1 — Evidence gains an execution-context field

**Affected sections:** §2.2 (canonical node model), §2.4 (canonical edge
model), Appendix A (canonical conceptual model).

**Original text:** `v = (id, type, role, attributes)`; `e = (v_i, v_j,
label)`; Appendix A lists a node's fields as "type / role / attributes,
evidence" and an edge's fields as "label / source / target / provenance."
No field anywhere in §2 or Appendix A represents iteration, loops, or
repeated execution.

**Problem found:** §4.3's N+1 heuristic is defined as "repeated
persistence operations within iteration or repeated execution contexts."
Implementing it requires knowing, for a given observed persistence
access, whether that access occurs inside a loop — a fact the canonical
model has no field for. This is not an implementation-detail gap; it is
a concept §4.3 depends on that §2's frozen model does not represent at
all.

**Amendment:** A piece of evidence (attached to a node's attributes or
an edge's provenance) gains one additional field, `execution_context`,
with three possible values:

- `SINGLE` — the adapter positively observed a single, non-repeated
  execution.
- `ITERATED` — the adapter positively observed the access occurring
  inside a loop, a stream's per-element callback, or an equivalent
  repeated-execution construct.
- `UNKNOWN` — the adapter made no claim about execution context either
  way. **This is the default** for evidence produced before this
  amendment and for any evidence a future adapter chooses not to
  populate this field for.

This is the only control-flow-adjacent concept added to the canonical
model in v0.4.1. AERF still has no general representation of loops,
branches, or control flow as first-class graph nodes/edges — an
`execution_context` tag on a piece of evidence is a narrower, evidence-level
fact ("this specific observation happened inside a loop"), not a claim
about program structure in general.

**Rationale / evidence:** Implemented in Increment 6
(`aerf-model`, `Evidence` class + new `ExecutionContext` enum). The
`UNKNOWN` default was chosen specifically so this amendment is
backward-compatible with every piece of evidence recorded in Increments
1–5: none of it makes any claim about iteration, so re-interpreting it as
`UNKNOWN` (not `SINGLE`) preserves its original meaning exactly. A
general loop/control-flow graph concept was considered and rejected as
disproportionate to what a "basic" heuristic (§11's own framing) needs.

---

## Amendment 2 — The layer-entropy relation matrix must be declared explicitly, not derived from an ordering

**Affected section:** §4.1 (layer entropy).

**Original text:** "A typical allowed ordering is: Presentation ->
Application -> Domain -> Persistence -> Infrastructure. The relation
matrix must remain governance-configurable rather than hard-coded as
universal architecture."

**Problem found:** Read together, this suggests the allowed-transitions
matrix could be mechanically derived from the stated ordering. Two
natural derivations were tried and **both contradict a case v0.4 treats
as settled elsewhere**:

- *Adjacent-layers-only* (a role may call itself or the next role in the
  chain) flags `Application → Persistence` (skipping `Domain`) as a
  violation — but that is an unremarkable, common pattern (a service
  calling a repository directly).
- *Any-forward-call* (a role may call any later role in the chain) does
  **not** flag `Presentation → Persistence` as a violation — but §6.3's
  own worked invariant example, `no_presentation_to_persistence`, treats
  exactly that transition as a critical violation.

Since no single mechanical rule matches the framework's own worked
example, the ordering cannot be treated as a derivation recipe.

**Amendment:** The "typical allowed ordering" in §4.1 is illustrative
context only, not a derivation rule. The layer-entropy relation matrix
(which role may call which role) **must always be declared explicitly**
by governance configuration. AERF v0.4.1 defines no default derivation
from an ordered list of roles, and no future implementation should
introduce one silently — if a derivation is ever wanted, it must be
proposed and validated against cases like the one above, not assumed.

**Rationale / evidence:** Implemented in Increment 3 (`aerf-analysis`,
`LayerPolicy`, which accepts only an explicit matrix). No code changes
accompany this amendment; it formalizes the interpretation already
adopted in that increment.

---

## Amendment 3 — "Evidence-weighted" N+1 score is defined concretely

**Affected sections:** §4.3 (persistence/N+1 entropy), Appendix B
(initial entropy inventory).

**Original text:** §4.3's body describes the heuristic but gives no
formula. Appendix B's summary table gives the formula as "Evidence-weighted
N+1 patterns / relevant persistence contexts" — but "evidence-weighted"
is not defined anywhere in the document.

**Problem found:** Every other MVP metric (§4.1, §4.2) is a plain count
ratio (`violating / total`, `participating / total`). Appendix B implies
this one metric is different — weighted — without saying by what. Left
undefined, an implementation is forced to either invent a weighting
scheme silently or ignore Appendix B's own wording.

**Amendment:** Define the weight of one relevant persistence context
(an edge) as the count of its evidence items marked
`execution_context = ITERATED` (see Amendment 1) — i.e. the number of
independently observed repeated-execution call sites backing that one
access. The evidence-weighted N+1 score is then:

```
sum(weight(edge) for edge in relevant persistence contexts) / count(relevant persistence contexts)
```

The denominator is an unweighted count, exactly as Appendix B states
("relevant persistence contexts," not "weighted persistence contexts").
This evidence-weighted score is reported **alongside**, not instead of,
the plain `flagged / relevant` ratio: both are legitimate, distinctly
meaningful views of the same underlying findings, consistent with the
"measurement before aggregation" principle (§14) that individual
dimensions — and, here, alternate framings of one dimension — remain
separately visible rather than being collapsed into a single number.

**Rationale / evidence:** Implemented as part of this patch
(`aerf-analysis`, `PersistenceEntropyResult.weightedValue()`), building
on Increment 6. See that increment's document for the plain-ratio
`value()` this supplements.

---

## Amendment 4 — Iterative role refinement is scoped to monotonic transitions

**Affected section:** §3.2 (role inference — iterative refinement).

**Original text:** `R^(0) = R_seed`; `R^(n+1) = F(R^(n), G)`. "Inference
terminates when a fixed point is reached."

**Problem found:** No termination guarantee is established for an
arbitrary `F`. An unconstrained refinement function — one that may
revise an *already-assigned* role based on its neighborhood, which
§3.3's "Graph" evidence class (centrality, neighboring roles) seems to
invite — can trivially fail to converge: two rules that each flip a
node's role in response to the other's current role would oscillate
forever. "Terminates when a fixed point is reached" describes a
property of the intended output, not a property `F` is shown to have.

**Amendment:** For v0.4.1, `F` is scoped to rules with the following
contract: a rule may only be invoked for a node whose current role is
`Unknown`, and may only return a concrete (non-`Unknown`) role — it may
never revise an already-resolved node's role, and never reassign a role
back to `Unknown`. Under this restriction, the number of `Unknown` nodes
strictly decreases on every pass that changes anything, over a finite
node set, so termination within at most `|V| + 1` passes is
**structurally guaranteed**, not merely asserted.

A more general form of `F` — one capable of *revising* an
already-assigned role from its neighborhood (e.g. reclassifying a
weakly-seeded node surrounded by strongly-classified neighbors of a
different role) — remains a legitimate reading of §3.2/§3.3 and is
explicitly **not ruled out**, only deferred: it requires its own
convergence argument (e.g. a bound on how many times any single node may
be revised) before being adopted, and is not part of v0.4.1.

**Rationale / evidence:** Implemented in Increment 4 (`aerf-analysis`,
`IterativeRoleInferenceEngine`, `GraphRoleRefinementRule`). This
amendment documents a real, deliberate narrowing of §3.2's scope for the
current implementation, not an incidental implementation detail — a
reader of v0.4 alone would not know this restriction exists.

---

## Non-normative implementation note — determinism and `Map.copyOf`

Not a specification amendment (no conceptual change), but worth
recording so it isn't rediscovered: `java.util.Map.copyOf(...)` does
**not** guarantee that a returned map's iteration order matches its
source, even when the source is a `LinkedHashMap`. An early version of
the canonical graph implementation used it for the node map and for a
node's attribute map, which silently violated §14's determinism
principle. Fixed in Increment 1 by wrapping an explicit `LinkedHashMap`
in `Collections.unmodifiableMap` instead, with a regression test
(`GraphTest.nodeIterationOrderIsInsertionOrderNotHashOrder`) using node
ids chosen so natural hash order would not coincide with insertion
order. Any future implementation on a different platform/language should
verify the equivalent guarantee explicitly rather than assume a
"copy-to-immutable" utility preserves order.
