# AERF v0.4 Contract & Implementation Reconciliation

**Purpose:** executable-contract guidance for reconciling the AERF v0.4 pre-implementation specification with the v0.4.1 implementation/status record.

**Normative sources**

1. `AERF_v0.4_pre_implementation_documentation.pdf` — frozen conceptual specification immediately before implementation.
2. `aerf-v0.4.1-status-report.pdf` — implementation/status record at commit `cc602de`, including the seven amendments, resolved questions, and outstanding work.

## 1. How to use this document

This document is **not a redesign specification**.

The task is to determine whether the current implementation satisfies the v0.4 contract as amended by v0.4.1, and to add executable tests where the contract is currently unprotected.

Use this precedence:

1. v0.4 explicit contract.
2. v0.4.1 amendments that explicitly correct or clarify v0.4.
3. Existing implementation when it is demonstrably a better internal implementation of the same observable contract.
4. Historical documents only for context; do not turn historical future vision into v0.4 requirements.
5. If the sources do not settle a question, mark it **UNVERIFIED / OPEN** rather than inventing behaviour.

### Critical rule: do not revert a better implementation

A difference from the v0.4 prose is **not automatically a defect**.

If the implementation differs internally but preserves the contractual behaviour, keep it.

If it improves robustness while preserving the contract, classify it as:

`CONFORMING + IMPROVEMENT`

If it changes an externally observable contract, it requires an explicit disposition; do not silently reinterpret the specification.

### Critical rule: distinguish missing v0.4 behaviour from deferred work

The v0.4.1 outstanding-question register is a planning record, not a complete reconstruction of everything that was historically outside the MVP.

Do not promote v0.5 work into a v0.4 defect.

Conversely, if v0.4 explicitly required something and the implementation does not provide it, classify it as **REMEDIATION REQUIRED**, even if v0.4.1 did not list it as an outstanding question.

---

# 2. Disposition vocabulary

Use exactly one primary disposition for each reconciliation item:

- **CONFORMING** — implementation satisfies the v0.4 contract.
- **CONFORMING + IMPROVEMENT** — implementation satisfies the contract and has a defensible implementation improvement that should be retained.
- **CORRECTED BY v0.4.1** — v0.4 was amended and the implementation follows the amended contract.
- **REMEDIATION REQUIRED** — an explicit v0.4/v0.4.1 contractual requirement is missing, incorrect, or insufficiently protected by tests.
- **UNVERIFIED** — the supplied documents do not provide enough evidence to decide; inspect code/tests and report evidence.
- **DEFERRED BY CONTRACT** — explicitly outside the v0.4 MVP or explicitly deferred to a later version.
- **OPEN / UNDECIDED** — sources expose a genuine unresolved design question; do not invent a solution.

---

# 3. Contract reconciliation matrix

## V04-CORE-01 — Canonical graph

**Source:** v0.4 §§2–2.3.

The model is a typed directed multigraph:

`G=(V,E,τV,τE,ρ,μ)`

with node types, edge types, architectural roles, and derived metrics.

Required node types:

- MODULE
- COMPONENT
- FUNCTION
- DATA
- VIEW
- CONFIG
- SCRIPT

Required architectural roles:

- Presentation
- Application
- Domain
- Persistence
- Infrastructure
- External
- Unknown

Required v0.4 relation types:

- CALL
- DEPENDS
- READS
- WRITES
- EXTENDS
- IMPLEMENTS
- RENDERS
- CONFIGURES
- COMMUNICATES

**Disposition target:** CONFORMING.

**BDD intent:**

```gherkin
Feature: Canonical graph model

  Scenario: Represent architecture as a typed directed multigraph
    Given canonical graph facts
    When the graph is constructed
    Then nodes have canonical node types
    And relations are directed and labelled
    And nodes may carry architectural roles
    And graph metrics remain derived data rather than technology-specific AST objects

  Scenario: Unknown is a valid architectural role
    Given a node for which available evidence is insufficient
    When role inference completes
    Then the node has role Unknown
    And the engine does not invent a more specific role
```

---

## V04-GRAPH-02 — Structural membership relation

**Source:** v0.4.1 Amendment 6 / Increment 21.

`MEMBER_OF` is a structural relation from a FUNCTION to its declaring COMPONENT.

The status report explicitly records this as a v0.4.1 amendment.

**Disposition:** CORRECTED BY v0.4.1.

**BDD intent:**

```gherkin
Feature: Structural membership

  Scenario: Record function membership
    Given a function declared by a component
    When extraction produces canonical graph facts
    Then a MEMBER_OF relation connects the function to its declaring component
```

Do not remove MEMBER_OF merely because it was not in the original v0.4 edge list.

---

# 4. Role inference

## V04-ROLE-01 — Deterministic, total, explainable inference

**Source:** v0.4 §3.

Role inference must be:

- deterministic;
- total;
- explainable;
- stable;
- technology-independent.

Unknown is valid and uncertainty must be preserved.

Evidence classes:

- Structural
- Semantic
- Graph
- Governance

**BDD intent:**

```gherkin
Feature: Role inference contract

  Scenario: Identical evidence produces identical roles
    Given identical graph evidence and configuration
    When role inference is run twice
    Then the resulting role assignments are identical

  Scenario: Role inference is total
    Given a graph containing nodes with insufficient evidence
    When role inference completes
    Then every node has a role
    And insufficiently supported nodes may be Unknown

  Scenario: Role classification is explainable
    Given a node assigned a role
    When the result is inspected
    Then the supporting inference evidence is available
```

---

## V04-ROLE-02 — Iterative refinement

**Source:** v0.4 §3.2; v0.4.1 Amendment 4.

The conceptual model uses iterative refinement toward a fixed point.

v0.4.1 changed the implementation semantics to **monotonic role transitions**, guaranteeing termination. Non-monotonic/revising refinement was explicitly deferred.

**Disposition:** CORRECTED BY v0.4.1.

**BDD intent:**

```gherkin
Feature: Iterative role refinement

  Scenario: Refinement may use graph relationships
    Given seed role evidence
    And graph relationships between nodes
    When role inference refines the seed assignments
    Then graph evidence may contribute to the resulting roles

  Scenario: Refinement terminates
    Given a graph requiring multiple inference passes
    When role inference runs
    Then it terminates without cyclic role oscillation

  Scenario: Role transitions are monotonic in v0.4.1
    Given an inference run
    When a node changes role during refinement
    Then the transition follows the implemented monotonic transition rules
    And the process terminates
```

Do **not** add non-monotonic role revision as a v0.4 test requirement. It is an outstanding v0.5 question.

---

# 5. Entropy metrics

## V04-ENT-01 — Bounded entropy

**Source:** v0.4 §4.

Each entropy dimension is normalized to `[0,1]`.

- `0` means no detected deviation in the defined measurement universe.
- `1` means the defined maximum within that measurement universe.

Entropy is structural deviation, not a synonym for generic complexity.

**BDD intent:**

```gherkin
Feature: Entropy bounds

  Scenario: Entropy is normalized
    Given any valid metric measurement
    When entropy is calculated
    Then the result is between 0 and 1 inclusive
```

---

## V04-ENT-02 — Layer entropy

**Source:** v0.4 §4.1; v0.4.1 Amendment 2.

Original v0.4 definition:

`E_L = violating relevant edges / total relevant edges`

The architectural ordering is illustrative. The relation matrix must be governance-configurable.

v0.4.1 explicitly establishes that the matrix is **declared explicitly** and is not derived from the ordering.

**Disposition:** CORRECTED BY v0.4.1.

**BDD intent:**

```gherkin
Feature: Layer entropy

  Scenario: Calculate layer entropy from the configured matrix
    Given a canonical graph with relevant architectural relations
    And an explicitly configured layer policy matrix
    When layer entropy is calculated
    Then only relations forbidden by that matrix are violations
    And layer entropy equals violating relevant edges divided by total relevant edges

  Scenario: Ordering does not implicitly define policy
    Given an illustrative architectural ordering
    And an explicitly configured layer policy matrix
    When layer entropy is calculated
    Then the explicit matrix determines violations
    And the ordering is not used to derive an implicit matrix
```

---

## V04-ENT-03 — Cycle entropy

**Source:** v0.4 §4.2; v0.4.1 Increment 17.

`E_C = nodes participating in relevant SCCs / total nodes`

Trivial one-node SCCs are excluded unless self-cycles are explicitly governed.

The implementation uses iterative SCC processing to avoid the recursion-stack risk discovered during hardening.

**Disposition:** CONFORMING + IMPROVEMENT.

**BDD intent:**

```gherkin
Feature: Cycle entropy

  Scenario: Count nodes participating in relevant cycles
    Given a graph containing cyclic and acyclic components
    When cycle entropy is calculated
    Then nodes participating in relevant SCCs contribute to the numerator
    And non-cyclic nodes do not

  Scenario: Ignore trivial SCCs by default
    Given a graph containing a single-node SCC without a governed self-cycle
    When cycle entropy is calculated
    Then that SCC does not contribute to cycle entropy

  Scenario: Handle deep graphs without recursive SCC failure
    Given a graph deep enough to exceed practical recursive traversal limits
    When cycle entropy is calculated
    Then analysis completes without stack-overflow caused by recursive SCC traversal
```

Keep the iterative implementation.

---

## V04-ENT-04 — Persistence / N+1 heuristic

**Source:** v0.4 §4.3; v0.4.1 Amendments 1 and 3.

The first implementation targets repeated persistence operations within iteration/repeated execution contexts where access is not batched or justified.

This is explicitly a **heuristic**, not a claim that every repeated query is an N+1 defect.

v0.4.1 introduced:

- execution context: `SINGLE`, `ITERATED`, `UNKNOWN`;
- default `UNKNOWN`;
- evidence-weighted N+1 measurement based on evidence items marked `ITERATED`.

**Disposition:** CORRECTED BY v0.4.1.

**BDD intent:**

```gherkin
Feature: Persistence entropy

  Scenario: Unknown execution context is preserved
    Given persistence evidence without sufficient execution-context evidence
    When the evidence is recorded
    Then its execution context is UNKNOWN

  Scenario: Iterated persistence evidence is distinguishable
    Given persistence evidence occurring in an iteration context
    When the evidence is recorded
    Then its execution context is ITERATED

  Scenario: N+1 findings remain heuristic
    Given repeated persistence operations
    When persistence entropy is calculated
    Then the result is based on the defined evidence
    And the finding does not claim that every repeated operation is an N+1 defect
```

The v0.4.1 outstanding source-side constraint question remains separate and is not to be invented here.

---

## V04-ENT-05 — Security entropy

**Source:** v0.4 §4.4; v0.4.1 status.

Security entropy covers control weaknesses such as:

- XSS exposure;
- missing/ineffective CSRF protection;
- mass-assignment/data-binding exposure.

The metric concerns detected control weaknesses, not mere technology presence.

The implemented XSS rule is node-level. CSRF and mass-assignment modelling remains an explicitly outstanding v0.5 question because they are relationship concerns.

**Disposition:** CONFORMING for the implemented/basic security scope; CSRF/mass-assignment are DEFERRED BY CONTRACT / OPEN according to the v0.4.1 register.

**BDD intent:**

```gherkin
Feature: Security entropy

  Scenario: Technology presence alone is not a security violation
    Given a technology associated with a security concern
    And no evidence of the corresponding control weakness
    When security entropy is calculated
    Then technology presence alone does not create the finding

  Scenario: XSS evidence can produce a security finding
    Given source evidence matching the implemented XSS control weakness
    When security analysis runs
    Then the corresponding security evidence is reported
```

Do not invent a CSRF or mass-assignment algorithm.

---

## V04-ENT-06 — Modal entropy

**Source:** v0.4 §4.5.

Modal entropy is a first-class frontend dimension:

`E_M = non-canonical modal implementations / total detected modal implementations`

A canonical modal pattern is governed; deviations are violations unless explicitly permitted.

**Disposition:** DEFERRED BY CONTRACT for the first implementation where not implemented, because the v0.4 MVP freeze includes only the basic entropy dimensions and the status record does not establish implementation of modal extraction.

Do not create new modal extraction requirements unless separately authorized.

---

## V04-ENT-07 — JSP/frontend entropy

**Source:** v0.4 §4.6.

The v0.4 conceptual model identifies evidence such as:

- conditional rendering;
- tenant/role fragmentation;
- excessive view responsibility;
- inline business logic;
- JavaScript rendering complexity;
- interaction fragmentation.

The document explicitly says JSP/JSTL/JavaScript/jQuery are not inherently bad.

The MVP freeze explicitly defers full JSP/frontend extraction.

**Disposition:** DEFERRED BY CONTRACT for full implementation.

Do not turn individual historical examples into mandatory v0.4 tests.

---

# 6. Calibration, confidence and maturity

## V04-CAL-01 — Aggregated entropy

**Source:** v0.4 §5.1.

`E_total = Σ(w_d * f_d(E_d))`

with:

`Σ w_d = 1`

Weights are governance-selected and calibration functions are model families requiring empirical validation.

**BDD intent:**

```gherkin
Feature: Entropy aggregation

  Scenario: Aggregate dimensions using configured weights
    Given dimension entropy values
    And configured weights whose sum is 1
    And configured calibration functions
    When total entropy is calculated
    Then the configured weighted calibrated dimensions determine the result
```

Do not assert universal weights or thresholds.

---

## V04-CAL-02 — Baseline-relative drift

**Source:** v0.4 §5.3; v0.4.1 Increment 19 / outstanding #12.

`Δ_d = E_d^t - E_d^0`

The v0.4 conceptual model explicitly includes baseline-relative drift.

v0.4.1 records that storage was built in Increment 19, but the report was not yet wired to load a historical measurement and calculate drift.

**Disposition:** **REMEDIATION REQUIRED**.

This is an important reconciliation item: it is not a new v0.4.2 invention.

**BDD intent:**

```gherkin
Feature: Baseline-relative drift

  Scenario: Calculate drift against a recorded baseline
    Given a recorded baseline measurement for a project
    And a current measurement for the same project
    When drift is calculated
    Then drift for each dimension equals current entropy minus baseline entropy
    And baseline and current entropy remain separately available

  Scenario: Preserve baseline identity
    Given measurements belonging to different projects or baselines
    When drift is calculated
    Then the calculation does not silently compare unrelated measurements
```

The actual persistence/API mechanism must follow the existing implementation; do not invent names or formats in the test specification.

---

## V04-CAL-03 — Confidence

**Source:** v0.4 §5.4; v0.4.1 Amendment 7.

v0.4 defines confidence as resolved relevant relations divided by total extracted relevant relations.

v0.4.1 clarifies that confidence is not affected by a node's role outcome.

`MEMBER_OF` must be excluded from confidence because its target is structurally guaranteed to resolve and otherwise artificially inflates confidence.

**Disposition:** CORRECTED BY v0.4.1.

**BDD intent:**

```gherkin
Feature: Analysis confidence

  Scenario: Confidence measures evidence resolution
    Given extracted relevant relations
    And their resolution status
    When confidence is calculated
    Then confidence reflects resolved relevant relations over total relevant relations

  Scenario: Confidence is independent of role assignment
    Given identical resolved graph evidence
    And a change in inferred role outcome
    When confidence is calculated
    Then confidence does not change solely because the role changed

  Scenario: MEMBER_OF does not inflate confidence
    Given a graph containing MEMBER_OF relations
    When confidence is calculated
    Then MEMBER_OF relations are excluded from the confidence numerator and denominator
```

---

## V04-CAL-04 — Maturity levels

**Source:** v0.4 §5.5.

`M = 1 - E_total`

Provisional conceptual levels:

- L0: `M < 0.40`
- L1: `0.40–0.60`
- L2: `0.60–0.75`
- L3: `0.75–0.90`
- L4: `> 0.90`

The thresholds are provisional and must not be presented as universal benchmarks before empirical validation.

**BDD intent:**

```gherkin
Feature: AERF maturity

  Scenario: Maturity is derived from total entropy
    Given a calculated total entropy
    When maturity is calculated
    Then maturity equals 1 minus total entropy

  Scenario: Provisional boundaries are applied consistently
    Given total entropy and the configured provisional maturity boundaries
    When maturity is classified
    Then the corresponding AERF maturity level is returned
```

Do not confuse these with extraction-fidelity levels L1–L3.

---

# 7. Governance invariant DSL

## V04-GOV-01 — Invariant model

**Source:** v0.4 §6.

Invariants are explicit organizational constraints, distinct from metrics.

`I_k(S)=0` when the invariant holds and `1` when violated.

The conceptual model supports weighted invariant entropy:

`E_inv = Σ(λ_k * I_k(S))`

**BDD intent:**

```gherkin
Feature: Governance invariants

  Scenario: A satisfied invariant evaluates successfully
    Given a system satisfying an invariant
    When the invariant is evaluated
    Then it is reported as satisfied

  Scenario: A violated invariant produces evidence
    Given a system violating an invariant
    When the invariant is evaluated
    Then the violation is reported
    And its evidence is available
```

---

## V04-GOV-02 — DSL semantic boundaries

**Source:** v0.4 §6.2.

The DSL is:

- deterministic;
- pure;
- non-Turing-complete;
- bounded;
- scoped to NODE, EDGE, GRAPH;
- supports AND, OR, NOT;
- supports comparison and membership;
- references canonical node, edge, role and metric properties;
- carries severity and evidence reporting.

**BDD intent:**

```gherkin
Feature: Bounded governance DSL

  Scenario: Evaluate a node-scoped rule
    Given a valid NODE-scoped invariant
    When it is evaluated
    Then the result is deterministic

  Scenario: Evaluate an edge-scoped rule
    Given a valid EDGE-scoped invariant
    When it is evaluated
    Then the rule can reference source and target properties

  Scenario: Evaluate a graph-scoped rule
    Given a valid GRAPH-scoped invariant
    When it is evaluated
    Then graph-level metric properties can be referenced

  Scenario: Logical expressions are composable
    Given valid AND, OR and NOT expressions
    When they are evaluated
    Then their Boolean semantics are deterministic
```

Do not introduce a more expressive rule language.

---

# 8. Pipeline and architectural boundaries

## V04-PIPE-01 — Canonical execution pipeline

**Source:** v0.4 §7.

Required conceptual sequence:

`Source → technology-specific extraction → canonical graph → graph normalization → role inference → metric computation → invariant evaluation → calibration/risk aggregation → governance + engineering reports`

**BDD intent:**

```gherkin
Feature: AERF analysis pipeline

  Scenario: Evidence flows through the canonical stages
    Given an analyzable source system
    When the AERF pipeline executes
    Then extraction produces evidence
    And normalization produces the canonical graph
    And role inference supplies architectural semantics
    And metrics quantify structure
    And invariants evaluate policy
    And reporting exposes the resulting evidence
```

Do not prescribe internal class names or APIs unless they already exist.

---

## V04-PIPE-02 — Technology adapters remain separated

**Source:** v0.4 §§8–9.

Technology-specific extraction contributes evidence to the technology-agnostic core.

The adapter architecture explicitly covers Java, Spring, Hibernate/JPA, JSP, WebFlow, Tiles and JavaScript conceptually.

Adapters must be conservative and relations require provenance.

**BDD intent:**

```gherkin
Feature: Technology adapter boundary

  Scenario: Adapter evidence becomes canonical evidence
    Given technology-specific source evidence
    When the adapter processes it
    Then it emits canonical graph facts
    And those facts retain sufficient provenance

  Scenario: Adapter does not invent unsupported relations
    Given source material without evidence for a relation
    When extraction runs
    Then the relation is not fabricated merely to complete the graph
```

Full JSP/WebFlow/Tiles/JavaScript extraction is outside the first implementation freeze unless separately demonstrated as implemented.

---

## V04-PIPE-03 — OpenRewrite boundary

**Source:** v0.4 §9 and v0.4 Definition of Done; v0.4.1 status.

OpenRewrite supplies extraction evidence but AERF is not an OpenRewrite wrapper.

The canonical graph must not expose OpenRewrite AST types.

The v0.4.1 report states that this boundary is mechanically enforced: OpenRewrite dependencies are banned from every module except the adapter.

**Disposition:** CONFORMING if current enforcement remains green.

**BDD / verification intent:**

```gherkin
Feature: OpenRewrite separation

  Scenario: Canonical model is technology-independent
    Given OpenRewrite-derived extraction evidence
    When it enters AERF
    Then the canonical graph contains AERF model objects
    And it does not expose OpenRewrite AST types

  Scenario: OpenRewrite dependency remains isolated
    Given the Maven multi-module project
    When dependency boundary checks run
    Then OpenRewrite dependencies are permitted only in the designated adapter module
```

---

# 9. JSON evidence and reporting

## V04-REPORT-01 — Evidence-first reporting

**Source:** v0.4 §3.5, §7, Definition of Done; v0.4.1 status.

Reports must preserve traceability from findings to evidence.

The implementation status reports JSON evidence/reporting as completed in MVP Increment 10 and later reports published from real repository scans.

**BDD intent:**

```gherkin
Feature: Evidence reporting

  Scenario: Findings retain supporting evidence
    Given an analysis finding
    When the report is generated
    Then the finding is represented in the evidence output
    And sufficient provenance/evidence is retained to explain the finding

  Scenario: Uncertainty remains visible
    Given unresolved extraction evidence
    When the report is generated
    Then unresolved evidence is not silently converted into certainty
```

---

# 10. Explicitly deferred v0.4 work

The following must **not** be converted into remediation tickets merely because they are absent from the MVP:

- Full JSP/frontend extraction.
- Full WebFlow semantics.
- Runtime monitoring.
- Incident correlation.
- Machine-learning calibration.
- Enterprise dashboard aggregation.
- Automatic refactoring recipes.
- Broad multi-language support.
- Advanced statistical models.

These are explicitly listed as deferred in the v0.4 MVP freeze.

Historical AERF documents also describe broader capabilities such as governance maturity, richer frontend dimensions, compliance evidence, heatmaps, and portfolio-level evolution. Those documents are useful for the long-term vision, but they must not silently become v0.4 acceptance criteria.

---

# 11. v0.4.1 outstanding questions: preserve their status

The following remain questions recorded by v0.4.1 and should not be invented into tests unless the current implementation already makes a definite choice that needs verification:

- #1 — when seed-only role classification fails;
- #2 — governance evidence class rule authorship boundary;
- #4 — per-subsystem layer-policy matrices;
- #5 — non-monotonic/revising role refinement;
- #6 — cycle entropy below whole-graph scope;
- #8 — N+1 source-side constraint;
- #9 — approved exception suppressing a persistence finding;
- #10 — CSRF/mass-assignment modelling;
- #11 — security concern closed vocabulary;
- #12 — baseline-relative drift wiring;
- #13 — per-dimension confidence;
- #14 — drift-aware risk aggregation;
- #15 — invariant aggregation;
- #16 — unified governance view.

v0.4.1 proposes #12, #13, #8 and #11 as v0.4.2 refinements, with #1 continuing as empirical/background work. It proposes #2, #4, #6, #9, #10, #14, #15, #16 and #5 for v0.5/new subsystem work.

**Important:** #12 is special. Drift itself is already part of the v0.4 contract; the v0.4.1 report says storage exists but report wiring remains incomplete. Therefore the reconciliation should test and, if necessary, remediate the missing wiring rather than treat drift as a new v0.4.2 invention.

---

# 12. Test strategy

The goal is **contract protection**, not test volume.

For every reconciliation item:

1. Find existing tests first.
2. If an existing test already proves the contract, do not duplicate it.
3. If implementation behaviour is better but contract-compatible, preserve it and add a regression test only if the improvement could be accidentally lost.
4. If an explicit contract requirement is untested, add a focused test.
5. If the requirement is not implemented, make the smallest compensating implementation change necessary and add the test.
6. Do not redesign unrelated code.
7. Do not implement deferred v0.5 features merely to make this document "complete".
8. Do not invent API names, file formats, rule syntax, thresholds, or algorithms absent from the sources/current implementation.
9. Prefer deterministic exact-value tests for formal formulas and boundary conditions.
10. Use integration tests where the contract concerns module boundaries or end-to-end evidence flow.

---

# 13. Required evidence report from the implementation assistant

After implementing/running the reconciliation tests, return a **machine-readable and human-readable evidence report**.

Create:

`docs/aerf-v0.4-reconciliation-evidence.md`

The report must contain the following sections.

## 13.1 Execution summary

Include:

- repository commit before changes;
- repository commit after changes;
- date/time of verification;
- Java/Maven version if available from the build;
- exact test command(s) executed;
- total tests executed;
- passed;
- failed;
- skipped;
- build result.

Do not claim tests were run if they were not.

## 13.2 Reconciliation ledger

For **every V04-* item in this document**, report:

| ID | Status | Existing test(s) | New test(s) | Implementation change | Evidence |
|---|---|---|---|---|---|

Allowed statuses are the disposition vocabulary from §2.

For each item marked `REMEDIATION REQUIRED`, state exactly what was changed and why.

For each item marked `CONFORMING + IMPROVEMENT`, explain why retaining the implementation is preferable to reverting it.

For each `UNVERIFIED` item, state precisely what could not be established.

For each `DEFERRED BY CONTRACT` item, cite the corresponding v0.4/v0.4.1 scope evidence.

## 13.3 BDD / contract coverage

List each BDD scenario from this document and map it to:

- implemented test class/method;
- integration test;
- existing test;
- or `NOT IMPLEMENTED / UNVERIFIED`.

Do not fabricate scenario names if the actual test structure uses another naming scheme; provide the real mapping.

## 13.4 Deviations and compensating changes

Explicitly list:

- every place where implementation differs from literal v0.4 wording;
- whether v0.4.1 corrected it;
- whether the implementation is retained because it is contract-compatible/better;
- any compensating change made.

This section is essential. **Never hide a deviation simply because the tests pass.**

## 13.5 Deferred work protection

Confirm that no v0.5/deferred capability was implemented solely because it appeared in the historical documents or outstanding-question register.

If any such work was unavoidable, explain it explicitly.

## 13.6 Test failures

For every failure:

- test;
- failure;
- root cause;
- whether it is a contract defect, implementation defect, test defect, or environment problem;
- action taken.

Do not silently weaken or delete a failing test to obtain a green build.

## 13.7 Final assessment

Give one overall result:

- `RECONCILIATION PASS`
- `RECONCILIATION PASS WITH UNVERIFIED ITEMS`
- `RECONCILIATION REQUIRES FURTHER REMEDIATION`

Then give a short explanation.

---

# 14. Evidence quality rules

The evidence report is itself part of the engineering record.

The assistant must:

- report actual commands and actual outcomes;
- distinguish tests from manual inspection;
- distinguish implementation evidence from specification evidence;
- link every remediation decision to a reconciliation ID;
- preserve unresolved questions rather than inventing answers;
- avoid claiming empirical validation where only unit/integration tests exist;
- avoid claiming regulatory compliance from passing AERF tests;
- keep the report reproducible from the repository state.

The v0.4 research discipline explicitly requires preservation of raw evidence and separation of formal definitions from empirical validation. This reconciliation follows the same principle.

---

# 15. Completion rule

The reconciliation is complete when:

1. every v0.4 contract item in this document has a disposition;
2. explicit v0.4 requirements that are not protected by tests have tests;
3. explicit implementation defects have compensating fixes and regression tests;
4. better contract-compatible implementations are retained rather than reverted;
5. deferred work remains deferred;
6. unresolved questions remain explicitly unresolved;
7. the full relevant Maven test suite is run;
8. `docs/aerf-v0.4-reconciliation-evidence.md` is produced with the evidence described above.

**Do not declare completion solely because the test suite is green.**

The objective is a traceable reconciliation between the frozen v0.4 contract, v0.4.1 corrections, and actual implementation behaviour.
