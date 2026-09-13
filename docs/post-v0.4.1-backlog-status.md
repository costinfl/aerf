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
| OQ-08 | Should the N+1 heuristic constrain the source side? | OPEN | — | 22 |
| OQ-11 | Should the security concern become a closed vocabulary? | OPEN | — | 23 |
| OQ-13 | Should confidence also exist per entropy dimension? | OPEN | — | 24 |

## Tier 2 — governance configuration foundation

| OQ | Question | Status | Decision | Increment |
|---|---|---|---|---|
| OQ-02 | Governance evidence / rule authorship boundary | OPEN | — | 25 |
| OQ-04 | Per-subsystem layer matrices | BLOCKED on OQ-02, and on deciding how a subsystem is identified at all | — | 26 |
| OQ-06 | Cycle entropy scope below the whole graph | BLOCKED on OQ-04's subsystem concept | — | 27 |

## Tier 3 — governance-facing risk

| OQ | Question | Status | Decision | Increment |
|---|---|---|---|---|
| OQ-09 | Approved exceptions and persistence findings | BLOCKED on OQ-02 | — | 28 |
| OQ-15 | Invariant aggregation `E_inv` | BLOCKED on OQ-02 | — | 29 |
| OQ-14 | Drift-aware risk model `R` | BLOCKED on OQ-15 | — | 30 |
| OQ-16 | Unified governance-facing view | BLOCKED on OQ-14 | — | 31 |

## Parallel and continuous

| OQ | Question | Status | Decision | Increment |
|---|---|---|---|---|
| OQ-10 | CSRF and mass-assignment modelling | OPEN — first step is an evidence-availability assessment, not implementation | — | — |
| OQ-05 | Non-monotonic (revising) role refinement | GATED — begins only if a real scan produces a demonstrably wrong role that revision would correct | — | — |
| OQ-01 | When does seed-only role classification actually fail? | OPEN (continuous) — served by each real-repository scan | — | — |

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
