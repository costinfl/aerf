# Increment 27 — Cycle Entropy Scope

Post-v0.4.1 implementation phase, resolving the **measurement half of
OQ-06** and explicitly narrowing the other. Closes tier 2.

## Objective

"Define whether governance can scope cycle entropy below the whole
graph." Acceptance: global scope remains supported; narrower scope has
explicit semantics; tests cover scope boundaries and empty/undefined
cases. Hard constraint: **do not alter SCC detection merely to answer
this question.**

## Two readings, and only one is answered

The backlog asks about *scoping the measurement*. The original register
(§6) motivates it differently: "a governance policy might reasonably
tolerate cycles **within** one module while forbidding them **across**
module boundaries." That second one is a *relevance* policy — it would
change which SCCs count, and so the graph-wide `E_C` numerator.

**Only the measurement half is implemented.** The tolerance half would
change §4.2's own definition of relevance rather than extend governance
configuration, and there is nothing to calibrate it against: see the
evidence position below. It is recorded as still open and guarded by
`governanceCannotDeclareACycleIrrelevantSoOq06sToleranceHalfRemainsOpen`.

## Three facts that decided the design

1. **Cycle entropy's denominator is unfiltered.** `E_C =
   participatingNodes / graph.nodes().size()`; relation scope enters only
   the numerator. This is the opposite of layer entropy, where increment
   26 could prove the denominator structurally immovable. Scoping only
   the numerator against a graph-wide denominator would silently change
   what the number means — so a scoped reading scopes **both** sides.
2. **An SCC is a set of nodes and can straddle a boundary.** Increment
   26's "the source governs" has no analogue here: a cross-subsystem
   cycle has no single owning subsystem, and any design needing to pick
   one would be wrong.
3. **There was not one cycle anywhere in the evidence base.** All eight
   committed sample reports show `cycleEntropy: 0.0` with zero SCCs —
   modern petclinic (118 nodes) and legacy spring-framework-petclinic
   (216 nodes) are both fully acyclic over `{CALL, DEPENDS}`. The
   pipeline's `defect-sample` is a strict DAG.

## Decision

**Yes — governance can scope cycle entropy, as an additional
per-subsystem measurement reported beside an unchanged global one.** The
increment-24 additive pattern, not a replacement.

All scoping happens strictly *after* `StronglyConnectedComponents.find`,
which stays exactly the pure function of `(node order, edge order,
relevantRelations)` its javadoc promises. The hard constraint is made
executable by `sccDetectionSeesTheSameGraphWhateverIsDeclared`.

### Semantics, every case explicit

| Case | Result |
|---|---|
| Node claimed by no subsystem | Counts graph-wide, in no scoped reading |
| Subsystem claiming no nodes in this graph | **undefined** — not measurable here, which is a different statement from having no cycles |
| Subsystem with nodes but no cycles | `0.0` — a real measurement |
| SCC spanning two subsystems | Each counts its own participating nodes; the SCC gets no owner |

A consequence worth pinning, and pinned: the subsystem numerators sum to
**at most** the graph-wide numerator, with equality exactly when every
participating node is claimed.

### One subsystem declaration, two scoped dimensions

Increment 26 fused subsystem identity with a layering matrix —
correctly, with one consumer. Cycle entropy is the second, and needs the
identity without a matrix. A second consumer is precisely the evidence
that justifies extracting what was rightly fused before:

- `Subsystem(name, idPrefix, Optional<LayerPolicy>)` — factories
  `of(name, idPrefix)` and `withLayerPolicy(...)`. `matches` is unchanged
  (still `startsWith`, still never parsing an id).
- `SubsystemLayerPolicies` → `Subsystems`; `SubsystemLayerPolicy` is
  absorbed and deleted.

One list serves both dimensions, so a node's subsystem can never depend
on which metric is asking — and an organization scoping cycles is not
forced to invent a layering matrix it does not want. A subsystem with no
matrix is judged by the default one, exactly as an unclaimed node is.

**Wire-format change:** the report's `governance.subsystemLayerPolicies`
key becomes `governance.subsystems`, and a subsystem that declared no
matrix serializes `"layerPolicy": null` — distinct from an empty matrix.
That key shipped only in increment 26, inside this unreleased
`0.2.0-SNAPSHOT` line, and the only consumer is this repository; recorded
here rather than passed over silently.

## Guard tests amended, not deleted

Both prior pins fired or would have lied, and each was narrowed
deliberately:

- `subsystemDeclarationReachesLayerPolicyOnly` →
  `thereIsExactlyOneSubsystemDeclarationServingBothScopedDimensions`. The
  new pin is that there is one `subsystems` component and not one per
  dimension.
- `cycleEntropyScopeIsStillOneGlobalChoiceSoOq06RemainsUndecided` →
  `governanceCannotDeclareACycleIrrelevantSoOq06sToleranceHalfRemainsOpen`.
  This one still *passed* after the change — but its name would have been
  a lie, so it was rewritten to guard what is genuinely still open. §4.2's
  one named lever on cycle relevance is the self-cycle choice, and it
  stays a single global boolean.
- New: `scopingCycleEntropyDidNotMakeTheGraphWideMeasurementOptional`.

## Measurement-change checklist (backlog §10.3)

| Check | Result |
|---|---|
| SCC detection | **Untouched.** Pinned by `sccDetectionSeesTheSameGraphWhateverIsDeclared` and again end-to-end. |
| Graph-wide `E_C` | Unchanged, denominator still `graph.nodes().size()`. Pinned by `theGlobalValueIsUnchangedByAnyDeclaration` and `theGraphWideMeasurementIsIdenticalWhetherOrNotSubsystemsAreDeclared`. |
| `AnalysisConfidence` | Untouched and unreachable from governance. |
| Every calculator's `relevantRelations` | All four re-read; **none changed**. Scoping does not touch relation scope. |
| Existing exact-value tests unmodified | Yes — 5/7 confidence, 1.0 layer, 0.5 persistence, the three per-dimension fractions, and `CycleEntropyCalculatorTest`'s `2.0/3.0`, the one non-trivial cycle value in the suite and the one most exposed to a denominator change. |
| Before/after on real code | Byte-identical, below. |

## Reachability

`CycleScopeEndToEndTest` runs a real `Pipeline.run` over parsed source
and asserts the graph-wide value, the per-subsystem split, the
cross-boundary rule, the undefined case, and that both measurements reach
the serialized report — the V04-CAL-02 standard, in the suite rather than
by hand.

## Evidence: a new fixture, because there was nothing to use

`aerf-pipeline/src/test/resources/cyclic-sample/` is new;
`defect-sample` is untouched. It carries both shapes the semantics turn
on, and through the real extractor it produces:

```
12 nodes (6 types, 6 methods), 2 SCCs, 4 participating -> E_C = 4/12 = 0.333
  SCC  [billing.Invoice, billing.LineItem]        intra-subsystem
  SCC  [billing.Ledger, shipping.Crate]           crossing the boundary
billing   3/6  = 0.5    (3 types in cycles, 3 methods not)
shipping  1/4  = 0.25   (Crate in a cycle; Label acyclic)
com.example.shared.Clock and its method: claimed by neither, counted graph-wide
```

Scoped numerators 3 + 1 = 4 = the graph-wide numerator, since here every
participating node is claimed.

**This is the first cycle this project has ever measured end to end.**

**Real-repo position, stated plainly rather than dressed up.**
spring-petclinic *cannot* demonstrate this feature: it contains no cycle,
so every scoped reading would be `0.0` or undefined. Unlike increment 26,
which moved a real measurement 14/21 → 11/21 on real code, OQ-06 has no
real-repository demonstration available, and the fixture is the
demonstration. A fresh petclinic scan was still run as a regression
check: **identical to the increment-25 default-path baseline** once the
two additive keys (`governance.subsystems: []` and
`cycleEntropy.bySubsystem: []`) are removed — layer 0.667, cycle 0.0,
total 0.222, confidence 0.385, 118 nodes, 353 edges. The `Subsystem`
extraction moved nothing.

## Findings recorded, not fixed

- **A cycle-tolerance policy is not implemented.** No cycle exists in the
  evidence base to calibrate one against (§10.4, evidence before
  abstraction), and it would change §4.2's relevance definition rather
  than extend governance configuration.
- **Finding A (relation scope governability)** was OQ-06's to own.
  Nothing here makes it governable. It needs a new owner.
- **Nested subsystems remain unrepresentable** (finding F), now affecting
  cycle scope as well as layer.

## Increment report

```
Increment:      27 — cycle entropy scope (OQ-06)
Question(s):    OQ-06. Can governance scope cycle entropy below the whole graph?
Decision:       Yes, as a per-subsystem measurement reported beside an unchanged
                global one, scoping both numerator and denominator, computed
                strictly after SCC detection. A cross-subsystem SCC is owned by
                neither side. Subsystem identity is extracted from increment 26's
                fused type so one declaration serves both scoped dimensions.
                The tolerance half is NOT implemented; OQ-06 becomes
                PARTIALLY DECIDED.
Why:            Cycle entropy's denominator is the node population itself, so a
                scoped numerator over a graph-wide denominator would change the
                metric's meaning rather than narrow it. An SCC is a set and can
                straddle a boundary, so no owner may be assigned. The tolerance
                half has zero cycles in the evidence base to calibrate against.
Files changed:  Subsystem.java, Subsystems.java (extracted from increment 26's
                SubsystemLayerPolicy/SubsystemLayerPolicies, which are deleted);
                SubsystemCycleEntropy.java (new); CycleEntropyResult.java,
                CycleEntropyCalculator.java, LayerEntropyCalculator.java,
                GovernancePolicy.java, Pipeline.java, MetricsJson.java,
                GovernanceJson.java; cyclic-sample fixture (new);
                CycleEntropyCalculatorSubsystemTest, CycleScopeEndToEndTest
                (new), 5 test classes extended, 2 guard tests amended;
                docs/increment-27-*.md (new); backlog status tracker.
Tests added:    25 (337 -> 362; 336 before the guard-test split)
Full test cmd:  mvn -B test
Test result:    362 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None. Every protected exact value passes unmodified, including
                CycleEntropyCalculatorTest's 2.0/3.0. A fresh petclinic scan is
                identical to the increment-25 baseline apart from two additive
                empty keys.
Real-repo:      No demonstration possible — no real repository scanned by this
                project contains a cycle. Recorded as a limitation; the
                cyclic-sample fixture is the demonstration instead.
Reachability:   CycleScopeEndToEndTest, through a real Pipeline.run over parsed
                source.
Docs updated:   This file; backlog status tracker (OQ-06 -> PARTIALLY DECIDED).
Deferred kept:  Cycle-tolerance policy, relation-scope governability (finding A,
                now needing a new owner), nested subsystems.
Commit:         see git log for this increment
```
