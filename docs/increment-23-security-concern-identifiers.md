# Increment 23 — Security Concern Identifiers

Post-v0.4.1 implementation phase, resolving the **first half of OQ-11**
and explicitly narrowing the second.

## Objective

Decide "the smallest representation that gives stable
machine-referenceable identifiers without prematurely designing the
complete security model" for a security rule's `concern`, which is free
text today.

## Decision

**Canonical string constants, not an enum. No DSL plumbing.**

- `SecurityConcern.XSS = "xss"` — a named constant, so consumers reference
  a symbol rather than retyping a literal.
- `SecurityConcern.requireCanonical(String)` — enforces lowercase
  alphanumeric words joined by single hyphens (`[a-z0-9]+(-[a-z0-9]+)*`),
  applied at both construction points.

### Why not an enum

§4.4 names XSS, CSRF and mass-assignment as *examples* of
governance-relevant weaknesses — "covers governance-relevant weaknesses
such as…" — not a closed set. Contrast `Role` and `RelationType`, which
v0.4 does define exhaustively and which *are* enums here. An enum would
assert a closure the specification declines to make, and would stop an
organization adding a concern of its own without forking this project.
A named constant delivers the stability the question asked for while
leaving the set open.

### Why rejection rather than normalization

`requireCanonical` throws on `"Mass Assignment"` instead of quietly
returning `"mass-assignment"`. Normalizing would invent an identifier the
rule author never chose, and two rules could then silently converge on one
concern without either having agreed to it.

## What this deliberately does not do

OQ-11's stated motivation was "so invariants can reference concerns
reliably". **That is still impossible, and this increment does not
change it.** Verified by reading the DSL: `PropertyKey` has exactly eight
constants — `NODE_ROLE`, `NODE_TYPE`, `SOURCE_ROLE`, `SOURCE_TYPE`,
`TARGET_ROLE`, `TARGET_TYPE`, `EDGE_RELATION`, `METRIC` — none
security-related, and `InvariantEvaluator` never receives a
`SecurityEntropyResult` at all. Only the aggregate security *ratio* is
reachable, as a GRAPH-scope metric.

Making a finding's concern addressable from the DSL means deciding how a
finding is scoped and evaluated — a security-model design question, which
this item's own acceptance criteria forbid answering here. So OQ-11 is
recorded as **PARTIALLY DECIDED**: the identifier half is delivered, the
DSL half stays open and named.

`theInvariantDslStillCannotReferenceAConcern` asserts that no
security-related `PropertyKey` exists. It fails the moment someone adds
one — which is the intent: the remaining half of OQ-11 should be reopened
deliberately, not discovered by accident.

## A real gap found while wiring this

`SecurityOpportunityRule.Finding` has always rejected a blank concern.
`SecurityFinding` — the outer record that actually reaches a report —
validated only non-null. A blank or mixed-case concern could therefore
enter a report through the second path even though the first refused it.
Both now enforce the same canonical form.

## Compatibility

This tightens validation, so a third-party rule emitting
`"Cross Site Scripting"` would now throw where it previously succeeded.
Recorded here rather than glossed: only `"xss"` exists anywhere in this
project, so nothing in-tree is affected, but the behaviour change is real
for any out-of-tree rule catalog.

## Scope check

- **Not a measurement change** (backlog §10.3). A concern appears in
  neither `SecurityEntropyResult`'s numerator nor its denominator —
  `value()` counts findings, not concern values. No relation, metric or
  policy input was added; `AnalysisConfidence` and every calculator's
  `relevantRelations` are untouched.
- The JSON wire value is byte-identical: `MetricsJsonTest`'s
  `"concern":"xss"` assertion passes unmodified, as do
  `DefaultSecurityRulesTest`'s and `SecurityEntropyCalculatorTest`'s
  literal `"xss"` assertions. Those literals were deliberately *not*
  changed to reference the new constant, so the constant and the wire
  value pin each other independently.
- No CSRF or mass-assignment detection was added (OQ-10 untouched).

## Increment report

```
Increment:      23 — security concern identifiers (OQ-11)
Question(s):    OQ-11. Should the security concern become a closed vocabulary?
Decision:       Canonical string constants with enforced lexical form; no enum;
                no DSL plumbing. OQ-11 becomes PARTIALLY DECIDED.
Why:            §4.4 leaves the concern set open, so an enum would assert a
                closure the spec declines to make. The DSL half is unreachable
                without designing finding-scope evaluation, which this item's
                acceptance criteria forbid.
Files changed:  SecurityConcern.java (new); SecurityOpportunityRule.java
                (Finding validates canonical form); SecurityFinding.java
                (same, closing a validation gap); DefaultSecurityRules.java
                (literals -> constant); SecurityConcernTest.java (new);
                docs/increment-23-*.md (new); backlog status tracker.
Tests added:    8 (243 -> 251 in the reactor)
Full test cmd:  mvn -B test
Test result:    251 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None. Not a measurement change. Existing literal "xss"
                assertions in MetricsJsonTest, DefaultSecurityRulesTest and
                SecurityEntropyCalculatorTest pass unmodified, proving the
                serialized value did not move.
Real-repo:      Not applicable — neither real scan produces a VIEW node, so no
                security finding exists in either to re-check.
Docs updated:   This file; backlog status tracker (OQ-11 -> PARTIALLY DECIDED).
Deferred kept:  OQ-11's DSL half explicitly still open, guarded by a test.
                OQ-10 (CSRF/mass-assignment) untouched.
Commit:         see git log for this increment
```
