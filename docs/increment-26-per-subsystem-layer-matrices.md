# Increment 26 — Per-Subsystem Layer Matrices

Post-v0.4.1 implementation phase, resolving **OQ-04**. The first item to
clear the subsystem blocker Increment 25 recorded.

## Objective

"Extend the governance configuration mechanism **only as far as needed**
to declare distinct layer policies." Acceptance: matrices remain
explicit; no ordering-derived fallback is silently introduced; existing
single-policy behaviour remains reproducible.

Original motivation (`open-questions-register` §4): "A legacy system might
reasonably apply different layering rules to different subsystems that
evolved in different eras."

## The blocker, and how it was cleared

Increment 25 recorded that **no subsystem concept exists anywhere in the
graph model**: `Node` has no module or package field, `NodeType.MODULE` is
declared but never emitted, `MEMBER_OF` is specifically
method→declaring-type rather than a general grouping relation, and node
`attributes` are empty on every real scan. Any of those would have meant
an extraction change.

None was needed, because of what Increment 25 itself established:

> **A subsystem is governance-declared, not source-derived.**

The extractor does not know what an organization's subsystems are; its
architects do. A subsystem boundary is an architectural assertion, which
puts it squarely on the governance side of the authorship boundary. So
OQ-04 required no model change, no new relation, and no extraction change
at all.

## Decision

### Membership is a declarative id prefix

`SubsystemLayerPolicy(name, idPrefix, layerPolicy)`, collected in a
validated `SubsystemLayerPolicies`.

This does **not** break `NodeId`'s opacity. Its contract is that *AERF*
does not define or interpret id structure — "an opaque, stable,
technology-derived string... AERF does not define how adapters derive
it". `matches` honours that literally: it calls `String.startsWith`, a
total operation on any string, and never parses, splits or interprets
anything. The prefix's *meaning* belongs entirely to the governance
author, who is entitled to know their own adapter's convention —
`JavaNodeIds` documents that a Java type's id is its qualified name and a
method's is `Owner#name(params)`, which is why declaring a package prefix
picks up that package's methods too without this code knowing any Java.

A `Predicate<Node>` would have been more flexible and was rejected: a
lambda cannot be serialized or read back, so it would put a black box
inside the `governance` report key and contradict the "deterministic and
inspectable" criterion Increment 25 had just established.

### The source's matrix governs an edge

Layering constrains what a component may *depend on*, so the source is
the party whose declared rules are being tested; a cross-subsystem call
is judged by the matrix its caller declared. Three properties follow:

- An edge is governed by **exactly one** matrix — never none, never two —
  so this can neither drop an edge from the denominator nor double-count
  one.
- It degenerates exactly to the old behaviour when one matrix covers
  everything.
- A subsystem's measurement never depends on its neighbours' declarations.

**Honest caveat:** no real repository in this project's evidence base
exercises this. Every one of petclinic's 21 relevant layer edges is
intra-package (16 `owner`, 5 `vet`). The rule was decided on principle and
is tested synthetically, and that is recorded rather than glossed.

### Overlapping selectors are rejected, not resolved

Declaring both `com.foo` and `com.foo.bar` throws at construction.
Most-specific-wins would be a *derivation* — the same mechanical
inference `LayerPolicy` refuses when it declines to build a matrix from an
ordering — and it would let a typo in one prefix silently reassign nodes
to a neighbouring subsystem instead of failing. This follows the project's
existing stance that an ambiguous declaration is rejected rather than
quietly normalized (`SecurityConcern.requireCanonical`).

**Recorded limitation:** "everything in `com.foo` except `com.foo.bar`"
therefore cannot be expressed.

### Existing single-policy behaviour

`GovernancePolicy.layerPolicy` becomes the **default** matrix — the one
governing any node no subsystem claims — and `subsystemLayerPolicies` sits
beside it. Declaring none leaves the default governing everything, **on
the same code path, not a parallel one**, so OQ-04's reproducibility
criterion is satisfied by construction rather than by proof.
`GovernancePolicy.withOneLayerMatrix(...)` names that case so it reads as
the decision it is, and every existing call site uses it.

## The guard test that fired

`GovernanceBoundaryTest.governanceDeclaresNoSubsystemScopeSoOq04AndOq06RemainUndecided`
failed the moment `subsystemLayerPolicies` was added — and that was the
mechanism Increment 25 built working exactly as intended, not a
regression. The full reactor produced **one** failure, that one.

It was split rather than deleted:

- `subsystemDeclarationReachesLayerPolicyOnly` — OQ-04's half is answered,
  so the pin narrows: subsystem declaration may reach the layer matrix and
  nothing else.
- `cycleEntropyScopeIsStillOneGlobalChoiceSoOq06RemainsUndecided` —
  OQ-06's half stays pinned. `includeSelfCyclesInCycleEntropy` is still a
  single global boolean, and cycle entropy must not acquire subsystem
  scope as a *side effect* of this increment.

## Measurement-change checklist (backlog §10.3)

This is a policy input, so the checklist applies in full.

| Check | Result |
|---|---|
| `AnalysisConfidence` inspected | Untouched and unreachable: no governance component is typed `RelationType` or `Graph`. Its 0.8 fixture passes unmodified. |
| Every calculator's `relevantRelations` inspected | All four re-read; **none changed**. Subsystem declaration changes which matrix judges an edge, never which edges are in relation scope — pinned by `subsystemDeclarationDoesNotChangeWhichEdgesAreInRelationScope`. |
| Existing exact-value pipeline tests run unmodified | Yes — 5/7 confidence, 1.0 layer, 0.5 persistence and the three per-dimension fractions all pass verbatim. |
| Denominator regression test added | `aCrossSubsystemEdgeIsNeverDroppedFromTheDenominator`, and confirmed on real code: the demonstration below keeps the denominator at 21 while the numerator falls. |
| Ordering-derived fallback | None. `noOrderingDerivedFallbackIsIntroduced` asserts a subsystem without a declared matrix is unrepresentable and that no `List` parameter could carry a role ordering into the calculator. |
| Before/after on a representative graph | Default path byte-identical on real code; subsystem path demonstrably different. Both below. |

## Reachability

`GovernanceReportEndToEndTest` gained
`aDeclaredSubsystemReachesTheSerializedReportAndChangesWhichEdgesViolate`
— a real `Pipeline.run` where a permissive subsystem matrix clears
violations while the relevant-edge count and graph-wide confidence stay
put, and the declaration appears in the serialized report. Not unit tests
alone, per the V04-CAL-02 lesson.

## Real-repo evidence

**Regression, default path.** A fresh spring-petclinic scan diffed against
`spring-petclinic-rescan-post-increment-25.json`, clone path normalized:
**identical apart from one added key**, `"subsystemLayerPolicies":[]`.
Layer 0.667, total 0.222, confidence 0.385, 118 nodes, 353 edges — all
unmoved.

**Demonstration, subsystem path.**
`scripts/push-scan/sample-reports/spring-petclinic-subsystems-post-increment-26.json`,
reproducible via `scripts/subsystem-demo/`. Petclinic's own feature
packages are declared as subsystems, `owner` strict and `vet` a legacy era
tolerating a controller calling a repository directly:

| | layer | relevant | violating | by subsystem |
|---|---|---|---|---|
| default (one matrix) | 0.6667 | 21 | 14 | owner 11, vet 3 |
| owner strict, vet legacy | **0.5238** | **21** | **11** | owner 11 |

The three `vet` violations clear, all eleven `owner` violations remain,
and **the denominator does not move** — the anti-regression property
holding on real code, not only in a fixture. Total entropy 0.222 → 0.175,
maturity 0.778 → 0.825, still `L3_CONTROLLED`. Graph-wide confidence
0.385 both times: Amendment 7 respected, a policy outcome never moving
confidence. Cycle and persistence entropy unchanged.

`Main`'s illustrative config deliberately declares **no** subsystems, so
the committed default-path scan stays comparable with every earlier
increment. That is why the demonstration needs its own small driver rather
than a CLI flag.

## Findings recorded, not fixed

- **Which subsystem judged an edge is not reported.**
  `LayerEntropyResult` carries relevant and violating edges but not the
  governing matrix, so a reader cannot tell from the report alone which
  declaration produced a violation — it has to be inferred from the
  prefixes. That is per-subsystem *reporting*, which belongs to OQ-16's
  unified governance view.
- **Nested subsystems are unrepresentable**, by the overlap rejection
  above.
- **Cross-subsystem semantics are untested by real code.** Synthetic tests
  only, until a multi-module repository enters the evidence base.

## Increment report

```
Increment:      26 — per-subsystem layer matrices (OQ-04)
Question(s):    OQ-04. Should LayerPolicy support per-subsystem matrices?
Decision:       Yes. A subsystem is governance-declared as a name plus an id
                prefix, never source-derived, so no model/relation/extraction
                change was needed. The source's matrix governs an edge.
                Overlapping selectors are rejected rather than resolved by
                specificity. layerPolicy becomes the default matrix and
                declaring no subsystems is the old behaviour on the same path.
Why:            A subsystem boundary is an architectural assertion, which puts
                it on the governance side of Increment 25's authorship
                boundary. A prefix keeps NodeId opaque to AERF (startsWith
                interprets nothing) while staying serializable, which an
                opaque Predicate would not. Source-governs is what layering
                actually constrains, and it guarantees exactly one matrix per
                edge so the denominator cannot move.
Files changed:  SubsystemLayerPolicy.java, SubsystemLayerPolicies.java (new);
                GovernancePolicy.java (one component + withOneLayerMatrix);
                LayerEntropyCalculator.java (per-edge policy selection);
                Pipeline.java, GovernanceJson.java (wiring + serialization);
                3 new test classes, 3 extended, 1 guard test split;
                docs/increment-26-*.md (new); backlog status tracker;
                scripts/subsystem-demo/ and the subsystem sample scan.
Tests added:    28 (308 -> 336)
Full test cmd:  mvn -B test
Test result:    336 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None on the default path. Every protected exact value passes
                unmodified; the real petclinic scan is byte-identical to
                increment 25's apart from an empty subsystemLayerPolicies key.
Real-repo:      spring-petclinic with owner strict and vet legacy moves layer
                entropy 14/21 -> 11/21 with the denominator unchanged at 21
                and confidence unchanged at 0.385.
Reachability:   Proven through a real Pipeline.run in
                GovernanceReportEndToEndTest, not only at the CLI.
Docs updated:   This file; backlog status tracker (OQ-04 -> DECIDED/IMPLEMENTED).
Deferred kept:  OQ-06's cycle scope explicitly still open and guarded by test.
                Nested subsystems, per-subsystem reporting, and the untested
                cross-subsystem case each recorded with a named owner.
Commit:         see git log for this increment
```
