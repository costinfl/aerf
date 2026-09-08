# Increment 7 — Basic Security Entropy

## Objective

Implement the fourth and final MVP entropy metric: basic security
entropy from AERF v0.4 §4.4 — "governance-relevant weaknesses such as
XSS exposure, missing or ineffective CSRF protection, and
mass-assignment/data-binding exposure... The metric represents detected
control weaknesses, not mere technology presence."

## Scope

New package `org.aerf.analysis.metrics.security` in `aerf-analysis`:
- `SecurityOpportunityRule` — evaluates one `Node` in isolation (same
  starting scope as role inference's seed rules), returning an optional
  `Finding(concern, weaknessDetected, rationale)`.
- `SecurityFinding` / `SecurityEntropyResult` — every opportunity found
  is kept, not just counts, mirroring every other metric implemented so
  far; `value()` is an empty `OptionalDouble` when there are no
  applicable opportunities.
- `SecurityEntropyCalculator` — runs a rule set against every node,
  aggregating into one ratio per Appendix B's formula shape.
- `DefaultSecurityRules` (package `.rules`) — **one** illustrative rule,
  `UnencodedViewOutputRule`: a `NodeType.VIEW` node whose evidence
  mentions `"unescaped output"` is a detected XSS weakness; one
  mentioning `"escaped output"` is an opportunity handled correctly; a
  VIEW node with no rendering evidence at all is not an opportunity.
- 9 new tests, including one against the Increment 1 fixture graph
  (which predates any `VIEW` node and correctly yields zero
  opportunities — not a false "clean" score of `0.0`, but `undefined`).

## Why concern type is a free-text label, not a closed enum

`Role` and `RelationType` are closed sets in v0.4 — `rho(v) in
{Presentation, Application, ...}` is set-membership notation. §4.4 is
different: "covers governance-relevant weaknesses **such as** XSS...
CSRF... mass-assignment" is explicitly exemplary, not exhaustive.
Modeling concern type as a fixed Java enum would silently assert a
closed taxonomy v0.4 never claims. `SecurityOpportunityRule.Finding`
therefore carries `concern` as a `String`.

## Why only one concern (XSS) has a rule

CSRF and mass-assignment, as v0.4 describes them, are naturally
relationship concerns — "missing CSRF protection" is a property of a
form submission or endpoint configuration (plausibly an edge, or a
`CONFIG` node plus a `CONFIGURES` edge), not a property of one node in
isolation the way "this template renders unescaped output" is.
`SecurityOpportunityRule` in this increment only sees one `Node`,
matching the pattern role inference's seed rules started with (§3.2's
`R^(0)`, node-only, before graph-relationship rules were introduced in
Increment 4). Extending to CSRF/mass-assignment either needs an
edge-aware rule interface (parallel to `GraphRoleRefinementRule`) or a
node-level proxy for "this endpoint's binding configuration," and either
is a genuine design decision deserving its own increment rather than
being folded in here — consistent with the instruction not to implement
every dimension/sub-case at once "if doing so would make the first
increment too large."

## Implementation decisions not dictated by v0.4

1. **Rules operate on `Node`, not `Edge` or the whole `Graph`.** A
   deliberate scope limit for this increment (see above), not a claim
   that node-level evidence is sufficient for security entropy in
   general.
2. **The `"unescaped output"` / `"escaped output"` text convention is
   illustrative**, exactly like `DefaultSeedRules`' `"@Controller"`
   convention — a stand-in for what a real JSP/JSTL adapter would
   eventually produce as structured evidence, not a claim about how a
   real adapter's evidence will actually read.
3. **A `VIEW` node with no rendering evidence contributes no
   opportunity**, rather than being treated as either compliant or
   weak. This directly implements §4.4's "not mere technology
   presence": merely being a `VIEW` node says nothing about its
   security posture without positive evidence either way.

## Evidence — what this increment proves or exposes about the AERF model

- The `SecurityOpportunityRule` shape (`Node -> Optional<Finding>`) is
  structurally identical to `RoleInferenceRule` (`Node ->
  Optional<Candidate>`) from Increment 2. Two unrelated MVP concerns —
  "what role does this play" and "is this a security weakness" — both
  reduce to the same shape: a rule inspecting one node's evidence and
  optionally producing a labeled, explainable finding. That repetition
  is a real signal that AERF's evidence-and-rule pattern generalizes,
  not a coincidence specific to roles.
- Running the calculator against the Increment 1 fixture graph (which
  has no `VIEW` nodes) correctly yields zero opportunities rather than a
  spuriously clean `0.0` score — confirming the "undefined vs. zero"
  discipline established for every other metric extends cleanly to this
  one, including the case where an entire node type the metric cares
  about is simply absent from the graph.

## Open questions for the architecture

Tracked centrally in `docs/open-questions-register.md`; the two specific
to this increment:

- **How should CSRF and mass-assignment detection be modeled once
  attempted?** Likely needs either an edge-aware
  `SecurityOpportunityRule` variant or a documented convention for
  representing "this endpoint's binding/form configuration" as node
  evidence. Not decided here.
- **Should `concern` eventually become a closed, governance-configured
  vocabulary** (so invariants in the future §6 DSL can reference
  `finding.concern == "xss"` reliably) **or stay fully open-ended?**
  Left open pending the invariant DSL's actual design.

## Scope check

No changes to role inference, layer entropy, cycle entropy, or
persistence entropy. No CSRF or mass-assignment detection. No invariant
DSL, no calibration/aggregation, no OpenRewrite integration, and no real
JSP/JSTL adapter — this increment completes the MVP's fourth and final
frozen entropy dimension (§11) using exactly one illustrative rule, the
same "basic, not comprehensive" posture used for cycle and persistence
entropy.
