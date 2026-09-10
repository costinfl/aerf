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

## Amendment 5 — Evidence gains structured attributes

**Affected sections:** §2.2/§2.4/Appendix A (same fields Amendment 1
touched), plus the illustrative rule catalogs it enabled
(`DefaultSeedRules`, `DefaultSecurityRules` in `aerf-analysis`).

**Original text:** As in Amendment 1 — Evidence's fields are
`sourceAdapter`, `description`, `location`, `fidelity`, plus Amendment
1's `execution_context`. `description` is free-text prose.

**Problem found:** Every illustrative rule built so far (Increments 2
and 7) matches evidence by parsing `description` prose for a fixed
substring — `.contains("@Controller")`, `.contains("unescaped
output")`. These were always documented as illustrative stand-ins for
what a real adapter would produce (`DefaultSeedRules`'s own javadoc:
"concrete rules are explicitly extraction-adapter work"), but a real
adapter parsing real annotations and expressions has a *structured* fact
to report — an annotation's fully-qualified type name, a resolved
encoding function — not a sentence to compose and then have a rule
re-parse. Forcing every adapter to fabricate matching prose to be
legible to existing rules would be exactly backwards: it would make the
rule catalog the source of truth for what evidence looks like, instead
of the adapter's actual observation.

**Amendment:** Evidence gains an `attributes` field: an open,
adapter-defined `Map<String, String>` of structured facts, alongside
(not instead of) `description`. Empty by default for evidence built
before this amendment. No key vocabulary is prescribed by v0.4 itself —
same reasoning as `SecurityOpportunityRule.Finding`'s `concern` field
(§4.4 gives examples, not a closed set) — but this patch records the two
conventions the illustrative rule catalogs were updated to prefer, so
they are documented rather than left to be reverse-engineered from code:
an `annotation` attribute valued with the annotation's fully-qualified
type name (e.g. `org.springframework.stereotype.Controller`), and an
`outputEncoding` attribute valued `"escaped"` or `"unescaped"`. Every
updated rule checks the structured attribute first and falls back to
the original prose substring match when the attribute is absent, so
evidence built before this amendment — including every existing test
fixture — continues to match unchanged.

**Rationale / evidence:** Implemented in Increment 11 (`aerf-model`,
`Evidence` gains `attributes()` plus a new `Evidence.Builder`, since the
four existing `of(...)` factories already cover every combination of
`location` and `execution_context` — a sixth field would otherwise
double that to eight overloads). All 65 pre-existing `Evidence.of(...)`
call sites, all in test sources, needed no changes: no production code
constructed `Evidence` before this increment, so the change is purely
additive. `DefaultSeedRules` and `DefaultSecurityRules` updated to
prefer the structured attributes with prose as fallback; new tests
confirm each rule now fires from the attribute alone, with no matching
substring anywhere in the description.

---

## Amendment 6 — Edge relation set gains a structural `MEMBER_OF` relation

**Affected sections:** §2.4 (canonical edge model, `tau_E`).

**Original text:** §2.4's relation type set (`tau_E`) has no relation
connecting a `FUNCTION` node to the `COMPONENT` node that declares it -
only inheritance-style relations (`EXTENDS`/`IMPLEMENTS`) connect two
`COMPONENT` nodes to each other.

**Problem found:** Recorded as open question #17 during Increment 16:
building the end-to-end pipeline runner found that graph-relationship
role refinement (`R^(n+1)`, `InheritRoleFromSupertype`) has no way to
propagate a class's role down to its own methods, because no structural
relation connects a method to its declaring class at all. Increment 16
worked around this by having `JavaSourceExtractor` copy a class's own
stereotype evidence onto each of its declared methods' `NodeFact`s at
*extraction* time - a real fix for role propagation, but, as that
increment's own record says, "adapter-level evidence duplication, not a
graph-relationship fact - it only works for the one adapter and evidence
shape that was updated to do it," with no general answer for e.g. a
future `CALL`-based centrality signal wanting to reason about a method's
declaring class.

**Amendment:** `tau_E` gains `MEMBER_OF`: a purely structural relation
from a `FUNCTION` node to the `COMPONENT` node that declares it. Unlike
every other relation this project's adapters emit, its target is, by
construction, always in the same parse batch as its source (a method's
declaring class is never external), so a real adapter can always resolve
it - there is no "unresolved `MEMBER_OF` target" case analogous to an
unresolved `EXTENDS` target.

**Deliberately not wired into role inference.** Open question #17 named
three options: add the structural relation, add a dedicated
graph-refinement rule consuming it, or keep the Increment 16
evidence-copy workaround as the accepted mechanism. This amendment
adopts the first and third together, **not** the second, for a concrete
reason found while evaluating it: `GraphRoleRefinementRule`'s contract
(Amendment 4) only lets a rule see roles as of the *start* of the current
pass, so a method could only inherit its declaring class's role one full
pass *after* the class itself resolved - not the same pass, as
evidence-copying achieves today by making the fact directly available at
seed time (`R^(0)`). Wiring a `MEMBER_OF`-consuming refinement rule in
addition to (or instead of) evidence-copying would therefore change the
number of refinement passes several already-verified worked examples
depend on (Increment 16's "exactly two role-refinement passes" defect,
Increment 18/20's real-repository runs) for no behavioral gain over what
evidence-copying already provides for role inference specifically. The
relation is added for its own sake - a real structural fact §2's model
was missing, useful to any future rule or metric that wants it - without
disturbing role inference's current, tested behavior.

**A second, unplanned finding while implementing this.** No entropy
metric's `relevantRelations` allow-list includes `MEMBER_OF` (all three
default to explicit `CALL`/`DEPENDS` subsets), so layer/cycle/persistence
entropy are genuinely unaffected. `AnalysisConfidence` (§5.4) is not
relation-scoped at all, though - by design, it reads "relevant relations"
as *every* edge the graph contains - and `MEMBER_OF` edges are, by
construction, always resolved (a method's declaring class is always in
the same parse batch). Adding them without exclusion measurably inflated
confidence on `aerf-pipeline`'s own defect-sample fixture (0.714 →
0.857) for a reason with nothing to do with extraction quality: it just
means the sample has methods. `AnalysisConfidence.compute` was updated
in the same increment to exclude `MEMBER_OF` from both its numerator and
denominator, with the reasoning recorded in that class's own javadoc.
This is the same shape of amendment as Amendment 3's "relevant
persistence contexts" definition — a metric's own relevance filter, not
a change to what `MEMBER_OF` means structurally.

**Rationale / evidence:** Implemented in Increment 21
(`aerf-model`, `RelationType.MEMBER_OF`; `aerf-openrewrite`,
`JavaSourceExtractor` emits one `MEMBER_OF` edge per declared method;
`aerf-analysis`, `AnalysisConfidence` excludes it). See
`docs/increment-21-*.md`.

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
