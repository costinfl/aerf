# Increment 9 — Invariant DSL Core

## Objective

Implement AERF v0.4 §6, the invariant/governance DSL core: the model
(§6.1), its required semantics (§6.2), and both of the specification's
own worked examples (§6.3, §6.4) as actually-executable invariants —
including `no_presentation_to_persistence`, referenced in code comments
since Increment 1's fixture and finally made real.

## Scope

New package `org.aerf.analysis.invariant` in `aerf-analysis`:
- `Scope` — `NODE`/`EDGE`/`GRAPH`, §6.2's three required scopes.
- `PropertyKey` — the closed, bounded vocabulary of properties an
  invariant may reference (`NODE_ROLE`, `NODE_TYPE`, `SOURCE_ROLE`,
  `SOURCE_TYPE`, `TARGET_ROLE`, `TARGET_TYPE`, `EDGE_RELATION`, `METRIC`),
  each valid only in its own scope.
- `ValueExpression` — `Property` (a reference) or `Constant` (a literal).
- `Predicate` — `Equals`, `NotEquals`, `LessThan(OrEqual)`,
  `GreaterThan(OrEqual)`, `In` (membership), `And`, `Or`, `Not`, `Always`
  — exactly §6.2's required operator set, closed and non-recursive
  beyond composition (no variables, no function calls, no loops).
- `Invariant` — `(name, scope, when, assertion, severity)`, matching
  §6.3's `invariant { scope / when / assert / severity }` shape field
  for field.
- `InvariantEvaluator` — pure, deterministic evaluation against a
  `Graph` (+ a metrics map for `GRAPH` scope).
- `ViolationSubject` / `InvariantViolation` / `InvariantEvaluationResult`
  — every violating instance kept, not just a count; `indicatorValue()`
  collapses to §6.1's formal `I_k(S) ∈ {0, 1}` when that's what's wanted.
- `InvariantDsl` — static factory helpers so invariants can be
  constructed in Java reading close to §6.3's pseudocode.
- `examples.SpecWorkedExamples` — `noPresentationToPersistence()` and
  `entropyBudget(threshold)`, the specification's own two examples
  transcribed directly, not illustrative inventions.
- 21 new tests across pure predicate-logic correctness and all three
  scopes, including one run directly against the Increment 1 fixture.

## No parser, by design

§6.3 states: "YAML may be used as a transport format while the semantic
model remains independent of YAML." This licenses building the semantic
model — the AST of `Predicate`/`ValueExpression`/`Invariant` and its
evaluator — without any text or YAML parser at all. `InvariantDsl` gives
Java-level ergonomics for constructing invariants programmatically; a
parser translating a textual (YAML or otherwise) representation into
this same model is a distinct, later increment, and is exactly the kind
of decoupling §6.3 itself calls for.

## Implementation decisions not dictated by v0.4

1. **`severity` is a free-text `String`, not a closed enum.** v0.4 never
   enumerates a severity vocabulary; both worked examples use only
   `"critical"`. Same reasoning as `SecurityOpportunityRule.Finding`'s
   `concern` field (Increment 7) — inventing L0–L4-style named levels
   here would assert a taxonomy the specification doesn't.
2. **An `EDGE`-scope invariant excludes edges with an unresolved
   endpoint** from evaluation entirely — not a pass, not a violation.
   Direct continuation of the pattern established by every entropy
   calculator: a property that cannot be determined must not be silently
   judged either way.
3. **A `GRAPH`-scope invariant referencing an unsupplied metric throws**,
   rather than defaulting to pass or fail. A missing metric is a caller
   configuration error (the invariant asked for something nobody
   computed), categorically different from an edge whose evidence
   genuinely doesn't resolve — so it gets a different treatment
   (exception, not silent exclusion).
4. **`indicatorValue()` is a derived convenience, not the primary
   result.** §6.1 defines `I_k(S)` as a single 0/1 per invariant, but
   collapsing straight to that would discard exactly the per-instance
   evidence ("AERF should be able to explain why a finding exists, not
   merely output a number" — task instructions §11) needed to act on a
   violation. `InvariantEvaluationResult` keeps the full violation list;
   `indicatorValue()` is there for whenever the aggregate `E_inv = sum(lambda_k
   * I_k(S))` (§6.1) is eventually computed.
5. **Ordering comparisons (`<`, `<=`, `>`, `>=`) require both operands to
   be `Number`s**, throwing `IllegalStateException` otherwise (e.g.
   comparing two strings). v0.4 doesn't specify DSL type-checking; this
   increment does it at evaluation time rather than adding static
   type-checking machinery, which would be more DSL infrastructure than
   "core" calls for.

## Evidence — what this increment proves or exposes about the AERF model

- **The specification's own worked example now runs, against the
  specification-inspired fixture it was always meant to describe.**
  `no_presentation_to_persistence` evaluated against
  `CanonicalSampleGraphs.layeredOrderSlice()` finds exactly one
  violation — the `controller → repository` edge Increment 1 built and
  labeled "a deliberate layering violation" three increments before any
  invariant machinery existed to actually check it. This is the
  clearest falsification opportunity this project has produced so far:
  had the DSL model been unable to express this exact rule, that would
  have been a real problem with §6, not with the implementation.
- The `entropy_budget` example (§6.4) demonstrates `GRAPH`-scope
  invariants can reference computed metric values, not just raw
  graph structure — connecting this increment cleanly to Increment 8's
  `AggregatedEntropy.compute(...)` output as a natural future input.
- The property-reference vocabulary (`PropertyKey`, 8 closed values)
  turned out sufficient to express both of v0.4's own worked examples
  without needing anything beyond node/edge role, node/edge type, edge
  relation, and metric lookup — suggesting §6.2's requirement list
  ("References to canonical node, edge, role, and metric properties")
  was already a fairly complete enumeration in practice, not just in
  name.

## Open questions for the architecture

New entries added to `docs/open-questions-register.md`:

- **`E_inv = sum(lambda_k * I_k(S))` is not implemented.** Aggregating
  invariant indicator values by governance-selected importance
  (`lambda_k`) is the natural next step, structurally similar to
  Increment 8's `AggregatedEntropy` but with no stated normalization
  constraint on `lambda_k` (unlike `sum(w_d) = 1` for entropy weights).
  Deferred rather than guessed at.
- **How do invariant violations combine with `E_total`/maturity into an
  overall risk signal**, given the task instructions' explicit
  requirement not to collapse entropy, drift, and governance violations
  into one "architecture score"? Not attempted; likely the natural home
  for the `R` formula from §5.3 once drift also exists.

## Scope check

No YAML/text parser, no `E_inv` aggregation, no combination with
`AggregatedEntropy`/maturity, no changes to any entropy calculator or
role inference, and no new node/edge/relation concepts in `aerf-model`.
This increment implements exactly the invariant semantic model and its
evaluator, plus the two worked examples the specification itself
provides.
