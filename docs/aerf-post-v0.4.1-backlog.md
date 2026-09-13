# AERF Implementation Phase --- Post-v0.4.1 Backlog

**Documentation baseline:** AERF v0.4 + v0.4.1 amendments + two v0.4
reconciliation rounds\
**Current implementation version:** `0.1.0-SNAPSHOT`\
**Next implementation development line:** `0.2.0-SNAPSHOT`\
**Current documentation version:** `v0.4.1`\
**v0.4.2:** intentionally not created

## 1. Purpose

This document starts the next implementation phase of AERF.

It does **not** introduce a new AERF conceptual/documentation version.

The conceptual documentation remains **AERF v0.4.1**:

-   v0.4 was the research/specification evolution that defined the
    framework and its MVP boundary.
-   v0.4 pre-implementation documentation was the
    implementation-oriented subset of that specification.
-   v0.4.1 recorded amendments produced by implementation experience.
-   Two subsequent reconciliation rounds verified the implementation
    against that contract and repaired the one genuine missing v0.4
    capability discovered during reconciliation: baseline-relative
    drift.
-   The reconciliation rounds are implementation/release evidence, not
    new AERF specification versions.

The implementation started independently from an empty repository at
`0.1.0-SNAPSHOT` and has deliberately remained there because no
implementation release was requested.

The next implementation phase may develop the inherited backlog as
`0.2.0-SNAPSHOT`. A final implementation release version is **not**
assigned by this document.

> **Continue the implementation from the reconciled v0.4.1 state. Do not
> invent a new conceptual AERF version merely because implementation
> work continues.**

## 2. Source of truth

Use the following evidence in this order:

1.  AERF v0.4 canonical pre-implementation documentation.
2.  AERF v0.4.1 patch.
3.  AERF v0.4.1 status report and open-question register.
4.  The two v0.4 reconciliation evidence reports.
5.  Existing implementation and tests.

The historical status report recorded 14 outstanding questions after
triage: five were proposed for v0.4.2 and nine for v0.5. That historical
split is **not** preserved as a version boundary here; the unresolved
items become one inherited backlog for the next implementation phase.
fileciteturn23file1

The reconciliation evidence confirms that governance configuration,
CSRF/mass-assignment modelling, non-monotonic role refinement, invariant
aggregation, the unified governance view, and the closed
security-concern vocabulary were not implemented during reconciliation.
fileciteturn23file8

## 3. Current implementation baseline

The reconciled implementation contains the v0.4 MVP, the v0.4.1
amendments, and the reconciliation remediation.

The historical implementation reached six Maven modules, the v0.4 MVP
increments 1--10, extraction increments 11--16,
hardening/real-repository increments 17--21, two real repository scans,
and 219 tests at the v0.4.1 status-report baseline.
fileciteturn23file16

The second reconciliation round established that a real `Pipeline.run`
output can be converted into a drift input and serialized, rather than
merely testing the drift formula in isolation. fileciteturn23file17

### Preserve

-   canonical AERF graph independence from OpenRewrite AST types;
-   extraction-adapter boundary;
-   deterministic, explainable role inference;
-   current monotonic role refinement;
-   explicit layer policy rather than ordering-derived policy;
-   bounded primary entropy values;
-   separate evidence-weighted persistence score;
-   separate entropy, drift, invariant and governance concepts;
-   visible uncertainty/undefined values;
-   exact-value regression tests where metric semantics can change.

The v0.4.1 report specifically warns that adding a relation, metric, or
policy input can silently alter confidence or metric denominators. Every
new relation/metric therefore requires regression checks against
`AnalysisConfidence` and each calculator's `relevantRelations`.
fileciteturn23file15

# 4. Inherited backlog

## OQ-01 --- When does seed-only role classification actually fail?

**Status:** ongoing empirical question.

A real Spring Data failure case was found and corrected at the adapter
level, but the broader question remains open. No evidence has yet
demonstrated a case where seed-plus-current-refinement itself produces
an incorrect role. fileciteturn23file18

**Action:** continue collecting evidence from real scans; record
concrete failure cases; do not invent a generalized solution without a
demonstrated failure mode.

------------------------------------------------------------------------

## OQ-08 --- Should N+1 constrain the source side?

Two real repository runs now exist, including a genuine persistence/N+1
finding in legacy PetClinic, so this question can be decided using
evidence rather than intuition. fileciteturn23file10

**Required work:**

1.  Inspect the current persistence heuristic and tests.
2.  Use the existing real-scan evidence.
3.  Decide whether source-side restriction improves precision/recall.
4.  Record the decision before changing code.
5.  Add regression tests for accepted and rejected source-side patterns.
6.  Re-run exact metric assertions.

Do not change the heuristic merely because the question exists.

------------------------------------------------------------------------

## OQ-11 --- Should security concern become a closed vocabulary?

The current implementation intentionally uses free text. The historical
proposal suggested stable documented identifiers, not necessarily an
enum, so invariants can reference concerns reliably.
fileciteturn23file17

**Required work:** decide the smallest representation that gives stable
machine-referenceable identifiers without prematurely designing the
complete security model.

**Acceptance:** existing XSS behaviour is unchanged; no
CSRF/mass-assignment implementation is smuggled into this item; tests
protect the compatibility decision.

------------------------------------------------------------------------

## OQ-13 --- Per-dimension confidence

Determine whether confidence should also be available per entropy
dimension, while preserving the already-resolved meaning of graph-wide
confidence. Existing calculators already have dimension-specific
relevant-relation scopes. fileciteturn23file10

**Acceptance:**

-   graph-wide confidence semantics remain unchanged;
-   per-dimension confidence, if adopted, uses that dimension's relevant
    evidence;
-   undefined dimensions remain undefined;
-   JSON/report output preserves uncertainty;
-   denominator/numerator selection is protected by exact tests.

# 5. Governance configuration foundation

The historical triage grouped OQ-02, OQ-04 and OQ-06 because they
require a governance configuration mechanism:

-   governance as an evidence/policy class;
-   per-subsystem layer matrices;
-   per-module/narrower cycle-entropy scope. fileciteturn23file18

Treat them as one coherent workstream.

## OQ-02 --- Governance evidence / rule authorship boundary

Define how governance is represented and where governance rules are
authored.

Before implementation, establish the smallest configuration contract
needed by OQ-04 and OQ-06.

**Acceptance:**

-   governance input is represented explicitly;
-   its boundary from source-derived engineering evidence is clear;
-   configuration is deterministic and inspectable;
-   no hidden default governance policy is introduced.

## OQ-04 --- Per-subsystem layer matrices

Extend the governance configuration mechanism only as far as needed to
declare distinct layer policies.

The v0.4.1 amendment established that the matrix must be explicitly
declared rather than derived from illustrative layer ordering.
fileciteturn23file4

**Acceptance:** matrices remain explicit; no ordering-derived fallback
is silently introduced; existing single-policy behaviour remains
reproducible.

## OQ-06 --- Cycle entropy scope

Define whether governance can scope cycle entropy below the whole graph.

Do not alter SCC detection merely to answer this question. The iterative
SCC implementation remains an existing implementation improvement.

**Acceptance:** global scope remains supported; narrower scope has
explicit semantics; tests cover scope boundaries and empty/undefined
cases.

# 6. Governance-facing risk model

This workstream depends on the governance configuration foundation.

Historical questions are:

-   OQ-09 --- approved exception and persistence finding;
-   OQ-14 --- drift-aware risk model `R` and violations;
-   OQ-15 --- invariant aggregation `E_inv`;
-   OQ-16 --- unified governance-facing view.

The historical report explicitly grouped these as one governance-facing
risk subsystem and required entropy, drift and violations to remain
separately visible rather than collapsing them into a single
architecture score. fileciteturn23file5

## OQ-09 --- Approved exceptions

Define:

-   what constitutes an approved exception;
-   how it is represented as governance input;
-   which finding it can suppress;
-   how original evidence remains traceable;
-   whether suppression affects entropy, findings, risk, or only
    governance presentation.

**Constraint:** never silently delete or mutate source evidence.

## OQ-15 --- Invariant aggregation `E_inv`

Define the aggregation semantics before implementation. The
reconciliation explicitly left this unimplemented.
fileciteturn23file6

**Acceptance:** deterministic aggregation; individual violations remain
visible; undefined/absent results have explicit semantics; no violation
disappears through aggregation.

## OQ-14 --- Risk model `R`

Define the relationship between entropy, baseline-relative drift,
invariant violations and governance policy.

Do not collapse them into an opaque single score. The historical v0.4.1
report states that §6 did not yet provide a complete combination
formula. fileciteturn23file5

## OQ-16 --- Unified governance-facing view

Compose the preceding semantics into a view that can answer:

-   what architectural condition was observed;
-   what changed relative to baseline;
-   what governance constraints were violated;
-   what risk interpretation follows;
-   what evidence supports each conclusion.

The underlying entropy dimensions, drift values and invariant violations
must remain traceable.

# 7. Extended security modelling

## OQ-10 --- CSRF and mass-assignment

The existing security implementation is intentionally limited to the
XSS-shaped rule. CSRF and mass-assignment are relationship/configuration
concerns and may require an edge-aware rule shape or a documented
node-evidence convention. fileciteturn23file5

**Required work:**

1.  Examine evidence available from the current extraction model.
2.  Decide whether the current rule contract can express the required
    evidence.
3.  If not, define the smallest evidence/rule extension.
4.  Implement only after that contract is established.

**Constraints:** do not assume a technology is insecure merely because
it is present; preserve the existing opportunity-vs-weakness
distinction; retain traceable evidence.

# 8. Non-monotonic role refinement

## OQ-05 --- Revising role refinement

The current contract is intentionally monotonic. Amendment 4 made
termination structural; the general revising form remains open because
it needs a convergence argument. fileciteturn23file4

**Gate:** do not implement revising inference merely because it is on
the backlog.

If a real scan produces a demonstrably wrong role that revision would
correct:

1.  capture the concrete graph/evidence case;
2.  define revision semantics;
3.  establish a termination/convergence bound;
4.  define rule conflict resolution;
5.  add adversarial oscillation tests;
6.  implement only then.

If no motivating real case appears, leave OQ-05 open.

# 9. Dependency order

``` text
Cheap/evidence-driven refinements
  OQ-08  N+1 source side
  OQ-11  security concern vocabulary
  OQ-13  per-dimension confidence

                    ↓

Governance configuration
  OQ-02  governance configuration/authorship
  OQ-04  per-subsystem layer policy
  OQ-06  cycle entropy scope

                    ↓

Governance-facing risk
  OQ-09  approved exceptions
  OQ-15  E_inv
  OQ-14  risk R
  OQ-16  unified view

Parallel where justified
  OQ-10  CSRF/mass-assignment
  OQ-05  non-monotonic roles, only if evidence justifies it

Continuous
  OQ-01  empirical role-classification evidence
  real repository scans
  regression validation
```

The historical report independently identified governance configuration
as the foundation, the governance-facing risk view as dependent on it,
and security/role refinement as parallel workstreams.
fileciteturn23file5

# 10. Implementation-agent rules

## 10.1 An open question is not a specification

Use:

``` text
historical question
    ↓
existing evidence
    ↓
decision
    ↓
decision record / contract clarification
    ↓
tests
    ↓
implementation
    ↓
evidence report
```

Do not turn an open question directly into code.

## 10.2 Preserve the existing architecture

Do not introduce:

-   technology-specific canonical graph structures;
-   OpenRewrite types outside the adapter boundary;
-   runtime instrumentation;
-   generic linting;
-   automatic refactoring;
-   ML merely for risk modelling;
-   storage/API/dashboard infrastructure unless explicitly required by a
    commissioned backlog item.

## 10.3 Every new relation or metric is a measurement change

Whenever a new relation, metric or policy input is introduced:

1.  inspect `AnalysisConfidence`;
2.  inspect every calculator's `relevantRelations`;
3.  run existing exact-value pipeline tests;
4.  add denominator/numerator regression tests;
5.  compare before/after measurements on representative graphs.

This is mandatory because `MEMBER_OF` previously changed confidence
unexpectedly even though it looked structurally harmless.
fileciteturn23file9

## 10.4 Evidence before abstraction

Prefer real repository evidence over speculative generality.

## 10.5 Do not rewrite history silently

New implementation decisions may be recorded as new phase/decision
documents. Do not rewrite v0.4 or v0.4.1 history to make later decisions
appear originally specified.

# 11. Testing and evidence

Every completed backlog item must provide evidence appropriate to its
scope:

### Unit

Direct semantic behaviour.

### Integration

Proof that behaviour is reachable through the intended module/pipeline
boundary.

### Regression

Proof that existing v0.4.1 measurements remain stable unless
deliberately changed.

### Negative

Proof that prohibited or deferred behaviour remains absent where that
boundary matters.

### Real repository

Use real repositories for empirical questions where representative
evidence exists.

### Documentation

Record original question, evidence, decision, reason, implementation
location, tests and limitations.

# 12. Versioning policy

Documentation and implementation versions are intentionally independent.

  --------------------------------------------------------------------------
  Artifact                State                   Meaning
  ----------------------- ----------------------- --------------------------
  AERF documentation      `v0.4.1`                current
                                                  conceptual/specification
                                                  state

  AERF implementation     `0.1.0-SNAPSHOT`        unreleased implementation
                                                  lineage

  Next implementation     `0.2.0-SNAPSHOT`        next working
  development                                     implementation line

  AERF documentation      **does not exist**      intentionally skipped
  `v0.4.2`                                        

  Next documentation      **not assigned**        requires genuine
  version                                         conceptual/specification
                                                  evolution
  --------------------------------------------------------------------------

Completing this inherited implementation backlog does **not
automatically create AERF documentation `v0.5`**.

A documentation version advances only when the framework's
conceptual/specification content changes enough to warrant a new
documentation release.

An implementation version advances according to implementation/release
semantics independently.

If implementation exposes a genuinely new architectural concept, changes
an existing contract, or creates a new framework-level decision, capture
that decision explicitly and then determine whether the documentation
should advance.

# 13. Definition of done

This implementation phase is complete when:

1.  Every commissioned backlog item has a recorded decision.
2.  Implemented decisions have appropriate unit, integration and
    regression evidence.
3.  Gated/deferred questions remain explicitly open rather than being
    approximated.
4.  Existing v0.4.1 semantics are preserved unless deliberately
    superseded.
5.  No new relation or metric was introduced without
    confidence/denominator regression checks.
6.  Governance input and engineering evidence remain distinguishable.
7.  Entropy, drift, invariant violations and governance/risk
    interpretation remain separately traceable.
8.  Real-repository evidence exists where empirical questions require
    it.
9.  Technology independence outside adapters is preserved.
10. A final implementation status report identifies completed, deferred
    and changed items.
11. Only then is an implementation release version considered.

# 14. Required increment report

For every increment, return:

``` text
Increment:
Question(s):
Decision:
Why:
Files changed:
Tests added/changed:
Full test command:
Test result:
Regression impact:
Real-repository evidence:
Documentation updated:
Deferred items protected:
Commit:
```

Do not report an item as complete merely because its unit tests pass.
Completion means the behaviour is reachable at the intended
architectural boundary and its effect on existing measurements has been
checked.

# 15. Starting instruction

Begin with the first implementation increment from the inherited
backlog.

Do not create AERF `v0.5` documentation as part of this work.

Do not create AERF `v0.4.2` documentation.

The objective is:

> **Continue implementation from the reconciled AERF v0.4.1 contract,
> resolving the inherited backlog deliberately and producing
> implementation evidence without inventing a new conceptual version.**
