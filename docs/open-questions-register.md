# Open Questions Register

A living, consolidated list of unresolved conceptual/architectural
questions surfaced across increments. Each increment's own document
records questions at the time they were found; this register exists so
they stay visible in one place instead of being buried in six separate
files once several increments have passed. Entries are removed (moved to
`aerf-v0.4.1-patch.md` or a future patch) once actually resolved, not
just discussed — an entry here is not yet settled.

Update this file whenever an increment closes with an open question, and
prune it whenever one is resolved by a later increment or patch.

---

### 1. When does seed-only role classification actually fail?

*From: Increment 2.* No case has yet been found where `R^(0)` (seed
inference alone) gives a wrong answer that graph-relationship refinement
(`R^(n+1)`) corrects, beyond the synthetic inheritance-chain example used
to test iteration mechanically. Needs real evidence from running against
an actual codebase — not resolvable from first principles.

### 2. Rule authorship boundary for the "Governance" evidence class

*From: Increment 2, still open after Increment 4.* §3.3 names four
evidence classes: Structural, Semantic, Graph, Governance. Seed rules
(Increment 2) cover Structural/Semantic; graph refinement rules
(Increment 4) cover Graph. Nothing yet represents "Governance" evidence
— an organization's explicit declaration overriding or seeding a role
directly (distinct from the invariant DSL in §6, which *constrains*
roles/edges rather than *assigning* them). Needs a decision once
governance configuration has a concrete shape.

### 3. How should per-node `Unknown` and graph-wide analysis confidence (§5.4) interact?

*From: Increments 2 and 4.* §5.4 defines confidence as "resolved
relevant relations / total extracted relevant relations" — a
graph-wide, relation-centric measure. Role inference's `Unknown` is a
per-node outcome. Does a node stuck at `Unknown` count as an
"unresolved relation" for every edge touching it? Best answered when
§5.4 is actually implemented, not guessed at now.

### 4. Should `LayerPolicy` support per-subsystem matrices, not just one global one?

*From: Increment 3.* A legacy system might reasonably apply different
layering rules to different subsystems that evolved in different eras.
v0.4 doesn't address this; not attempted, no immediate need identified.

### 5. Non-monotonic (revising) graph-relationship role refinement

*From: Increment 4, formalized in `aerf-v0.4.1-patch.md` Amendment 4.*
Deliberately deferred, not ruled out. Needs its own convergence argument
(e.g. a bound on how many times a single node may be revised) before
attempting — this is real design work, not a quick fill-in.

### 6. Should cycle entropy be scoped below the whole graph (e.g. per module)?

*From: Increment 5.* §4.2 as written is global. A governance policy
might reasonably tolerate cycles within one module while forbidding them
across module boundaries. Not attempted; would be a
governance-configuration extension analogous to `LayerPolicy`.

### 7. `StronglyConnectedComponents`'s recursive implementation and stack depth

*From: Increment 5.* Pure implementation debt, not a spec question: the
recursive Tarjan's implementation will stack-overflow on a sufficiently
deep (but perfectly ordinary, non-cyclic) dependency chain. Needs an
iterative rewrite before running against a real system of nontrivial
size.

### 8. Should the N+1 heuristic also constrain the *source* side, not just the target's role?

*From: Increment 6.* `PersistenceEntropyCalculator` only requires the
*target* of a flagged edge to have `Role.PERSISTENCE`. A more precise
heuristic might also require the *source* to be something that
plausibly iterates. Not attempted, to keep the heuristic "basic" as
§11 frames it — worth revisiting once real evidence shows whether the
simpler version over- or under-flags in practice.

### 9. How should an approved "batched or otherwise justified" exception (§4.3) eventually suppress a persistence-entropy finding?

*From: Increment 6.* Once the invariant/exception model (§6) exists:
does an approved exception remove the flag from
`PersistenceEntropyResult` entirely, or does the metric stay a raw,
unfiltered signal while exceptions are applied only at the governance
layer downstream (meaning `PersistenceEntropyResult` might eventually
need an "excused" bucket alongside "flagged")? Depends on decisions not
yet made about the invariant DSL's shape.

### 10. How should CSRF and mass-assignment detection be modeled?

*From: Increment 7.* Unlike the XSS rule implemented (a single-node
check on `VIEW` evidence), CSRF and mass-assignment are naturally
relationship concerns — about a form submission or endpoint binding
configuration, not one artifact in isolation. Needs either an edge-aware
`SecurityOpportunityRule` variant (parallel to
`GraphRoleRefinementRule`) or a documented node-evidence convention for
representing binding/form configuration. Not decided.

### 11. Should security "concern" become a closed, governance-configured vocabulary?

*From: Increment 7.* Currently a free-text `String` (deliberately, since
§4.4 gives XSS/CSRF/mass-assignment only as examples, not a closed set).
Once the invariant DSL exists, invariants may want to reference a
specific concern reliably (e.g. `finding.concern == "xss"`), which would
push toward some agreed vocabulary — even if not a fixed enum, at least
documented string constants. Left open pending the DSL's actual design.

### 12. How should baseline-relative drift (§5.3) represent and load a baseline?

*From: Increment 8.* `Delta_d = E_d^t - E_d^0` is a trivial function
given two numbers, but AERF has no representation yet of *storing* a
prior measurement to diff against. Deliberately deferred until
JSON/reporting (still later on the MVP list) gives it somewhere to live,
rather than bolting on an ad hoc storage mechanism now.

### 13. Should confidence (§5.4) be graph-wide only, or per-dimension too?

*From: Increment 8.* `AnalysisConfidence` currently computes one
graph-wide ratio (resolved/total edges), independent of any specific
entropy dimension. §5.4 doesn't say whether a governance consumer should
instead see "persistence entropy computed at 90% confidence" alongside
the persistence score itself. Not decided; the graph-wide reading was
implemented as the more literal one.

### 14. How does the full drift-aware risk model `R` (§5.3) eventually combine with invariant violations (§6)?

*From: Increment 8.* §5.3's `R = sum(w_d f_d(E_d)) + beta *
sum(gamma_d * max(0, Delta_d))` combines calibrated entropy with drift,
but says nothing about governance invariant violations, which the task
instructions explicitly require to stay a separately visible signal, not
collapsed into one "architecture score." This increment stops at
`E_total`/maturity/confidence; how those combine with invariant results
once the DSL (§6) exists is undecided.

---

## Resolved (moved to the v0.4.1 patch, kept here for traceability)

- ~~What does "evidence-weighted" mean for the N+1 score?~~ → Amendment
  3, `aerf-v0.4.1-patch.md`.
- ~~Does the layer-entropy matrix derive from an ordering?~~ → Amendment
  2, `aerf-v0.4.1-patch.md`.
- ~~How is "repeated execution context" represented at all?~~ →
  Amendment 1, `aerf-v0.4.1-patch.md`.
- ~~Is iterative role refinement's `F` guaranteed to terminate?~~ →
  Amendment 4, `aerf-v0.4.1-patch.md`.
