# Increment 25 — Governance Policy Boundary

Post-v0.4.1 implementation phase, resolving the **representation half of
OQ-02** and explicitly narrowing the other. Opens tier 2, which the
backlog commissions as a prerequisite: "**Before implementation,
establish the smallest configuration contract needed by OQ-04 and
OQ-06.**"

## Objective

"Define how governance is represented and where governance rules are
authored." Acceptance: governance input is represented explicitly; its
boundary from source-derived engineering evidence is clear; configuration
is deterministic and inspectable; no hidden default governance policy is
introduced.

## The state before this increment

`PipelineConfig` was a flat nine-component record and *was* the entire
governance surface. Organizational declarations sat interleaved with
engineering inputs and technology heuristics, with nothing naming which
was which. Worse, `LayerPolicy`'s declared matrix was **write-only** —
only `knowsRole` and `isAllowed` were exposed — so the policy a report
was produced under could not be read back at all, let alone serialized.
A report was not self-describing, and nothing downstream could tell
whether two scans had been governed by the same rules.

## Decision

Four authorship classes, not three. The fourth turns what looked like a
gap into a positive claim:

| Class | Authored by | Members |
|---|---|---|
| Engineering input | whoever runs the scan | `sourceRoots`, `classpath` |
| Detection catalog | adapter / framework maintainer | `seedRules`, `refinementRules`, `securityRules` |
| **Governance policy** | the organization | `layerPolicy`, `includeSelfCyclesInCycleEntropy`, `calibrationProfile`, `invariants` |
| **Measurement definition** | AERF v0.4 §4, fixed | per-dimension relation scope, the four dimension names, `MEMBER_OF`'s exclusion from confidence |

The governance grouping is not a judgement call — each member already
says it in its own documentation. `LayerPolicy`: "a governance-declared
layering matrix". `CalibrationProfile`: "a validated governance
configuration for aggregation". `Invariant` is §6's governance rule. And
the self-cycle flag is not a borderline case at all: `CycleEntropyCalculator`
quotes §4.2 verbatim — trivial single-node SCCs are "excluded unless
self-cycles are **explicitly governed**."

### What was built

- **`GovernancePolicy`** (`org.aerf.analysis.governance`) — the four
  governance inputs as one named value. **Nothing has a default.** Every
  component is required, so a policy cannot be built by omission and no
  governance choice can be made silently on an organization's behalf.
  That is OQ-02's "no hidden default governance policy", enforced by the
  compiler rather than by prose. An organization wanting no invariants
  says so with an empty list.
- **`DetectionCatalog`** (`org.aerf.analysis.detection`) — the three rule
  lists. The distinction from governance is **authorship**, not mechanism:
  a detection rule says a `@Controller` means presentation; a governance
  declaration says what the organization will tolerate.
- **`PipelineConfig`** — nine components become three, each naming one
  authorship class. `ExtractionRequest` is reused rather than reinvented:
  it already is `(sourceRoots, classpath)` with the same non-empty guard,
  so that check is no longer duplicated.

Both containers live in `aerf-analysis` out of necessity, not taste:
`aerf-report` depends on `aerf-analysis`, so placing them in
`aerf-pipeline` would have made governance unserializable by the report
module.

### Inspectability

`LayerPolicy` gained `knownRoles()` and `allowedTargets()`. This does not
weaken its no-derivation guarantee — that guarantee is about
*construction* (no factory may infer a matrix from an ordering), and
reading back what was declared runs the opposite direction.
`layerPolicyStillOffersNoFactoryThatDerivesAMatrixFromAnOrdering` makes
the guarantee itself executable.

`GovernanceJson` serializes the policy under one additive top-level
`"governance"` key, following increment 24's proven path. A role that is
known but was declared no targets is **absent** rather than emitted as an
empty array — materializing one would report a declaration nobody made.
A zero-weighted dimension *is* emitted: weighting security out is a
documented decision about a capability boundary, not the absence of one.

### A latent determinism bug, found and fixed

`LayerPolicy` stored `new LinkedHashMap<>(allowedTargets)`, preserving
the **caller's** iteration order — and both callers pass unordered maps
(`Map.of` in `PipelineTest`, `Collectors.toMap` in `Main`). Harmless
while the matrix was unreadable; a §14 violation the moment it reached
JSON.

This was measured, not assumed. `Collectors.toMap` over `Role` keys —
exactly what `Main` does — produced **two different orderings across five
JVM runs**:

```
[PERSISTENCE, PRESENTATION, APPLICATION, DOMAIN, INFRASTRUCTURE]   x3
[PERSISTENCE, PRESENTATION, APPLICATION, INFRASTRUCTURE, DOMAIN]   x2
```

(`Role` overrides neither `hashCode` nor `equals`, so a `HashMap` keyed by
it orders on identity hash codes, which vary per JVM. `Map.of` randomizes
deliberately, and gave four distinct orderings in six runs.)

Storage is now canonicalized on `Role` declaration order — derived from
the model, never from caller input — using `EnumSet` throughout, with the
empty case built explicitly because `EnumSet.copyOf` throws
`IllegalArgumentException: Collection is empty`. After the fix, five CLI
runs produced a byte-identical `governance` block (`sha256` prefix
`f002db91b3a72658` every time).

### Why not a governance fingerprint

Rejected, and not merely as more expensive. A digest's soundness depends
on total coverage, which is impossible while predicates are
unserializable: two invariants sharing a name and severity but asserting
different conditions would hash identically, so the digest would claim
"same policy" when the policy differed. That is §5.4's prohibition on
converting incomplete evidence into certainty, applied to policy identity
instead of edge resolution. It is also not cheaper — both approaches need
the same canonical encoding, which is the actual cost. A serialized
declaration, by contrast, shows a reader exactly what it is *not* telling
them.

## What this deliberately does not do

**OQ-02's other half is still open.** The original question
(`docs/open-questions-register.md` §2) is narrower than the backlog's
restatement: it asks how an organization *declares or overrides a node's
role* — §3.3's missing fourth evidence class. Two structural obstacles,
both now pinned by tests:

1. **`RolePrecedence` has no notion of authorship.** It resolves
   competing signals purely by §3.4's role order, so a governance
   declaration added as just another `RoleInferenceRule` would **lose** to
   a naming heuristic. `roleConflictsAreStillResolvedByRolePrecedenceAloneWithNoNotionOfAuthorship`
   demonstrates it: governance declares PRESENTATION, a
   name-ends-with-Repository rule guesses PERSISTENCE, and PERSISTENCE
   wins.
2. **`Evidence` models only what was observed** — "always represents
   something that was actually observed" — which a declaration is not.

Implementing it is a measurement change (roles feed layer and persistence
entropy), is needed by neither OQ-04 nor OQ-06, and collides with gated
OQ-05. So OQ-02 is **PARTIALLY DECIDED**, the same pattern increment 23
used for OQ-11.

**No scope dimension was modelled.** A scope type with one inhabitant is
untestable — no assertion could distinguish a correct one-inhabitant model
from an incorrect one — and it would turn OQ-04's own acceptance
criterion ("existing single-policy behaviour remains reproducible") from
an identity into a claim needing proof, with a new failure mode: a wrong
default-scope lookup silently shrinking the layer denominator. OQ-04 and
OQ-06 extend `layerPolicy` and `includeSelfCyclesInCycleEntropy`
additively when they decide what a subsystem is.

## Findings recorded, not fixed

- **(A) Relation scope is measurement definition, not governance.**
  `Pipeline` composes each calculator's relation set from public,
  documented factories citing §6.3's worked example. It is not *hidden*,
  not a *default* (there is no declaration slot at all), and not
  *introduced* here — which is what the acceptance criterion prohibits.
  Deferring is also the safe call: `DimensionConfidence.forRelations`
  reads the same set, so one wrong plumb would move layer entropy and
  three confidences at once. Handed to OQ-06.
- **(B) `EntropySnapshot` carries no governance identity**, so
  baseline-vs-current drift is only sound if policy was identical and
  nothing says whether it was — a policy change could read as code drift.
  Belongs to OQ-14: fixing it forces "what does `Drift.compute` do when
  the policies differ — reject, warn, annotate, compare anyway?", which is
  drift semantics. This increment delivers the enabling half (the report
  now carries its policy) and pins the current blindness.
- **(C) Concern selection is fused into the detection catalog.** Which
  pattern signals a concern is engineering; which concerns an
  organization cares about is arguably governance. Separating them would
  filter the security opportunity denominator, making it a policy input.

## Measurement-change checklist (backlog §10.3)

This increment adds no relation, metric or policy input — it regroups
existing ones — but `LayerPolicy` sits on the measurement path, so the
checklist was run in full.

| Check | Result |
|---|---|
| `AnalysisConfidence` inspected | Untouched, and unreachable from here: no governance component is typed `RelationType` or `Graph`. Its 0.8 fixture passes unmodified. |
| Every calculator's `relevantRelations` inspected | All four re-read; **none changed**. Layer/cycle `{CALL, DEPENDS}`, persistence `{CALL}`, security none. |
| Any calculator changed | **No.** Deliberately — once `allowedTargets()` exists it is tempting to make `LayerEntropyCalculator` iterate the matrix instead of calling `isAllowed`, which would change iteration order and possibly the edge set. |
| Existing exact-value pipeline tests run unmodified | Yes — `PipelineTest`'s 5/7 confidence, 1.0 layer, 0.5 persistence and the three per-dimension fractions all pass verbatim. |
| Numerator/denominator regression tests added | `layerEntropyIsUnchangedWhenTheMatrixIsStoredInCanonicalOrder` — the same matrix declared in two orders gives identical `relevantEdges`, `violatingEdges` and `value()`. |
| Before/after comparison on a representative graph | Real spring-petclinic scan, byte-identical apart from the added key — see below. |

## Reachability

Not reported complete on unit tests alone. `GovernanceReportEndToEndTest`
runs a real `Pipeline.run` and asserts the governance arrives in the
serialized report — an improvement on increment 24, which verified its
equivalent by hand at the CLI, so this one keeps being checked. Confirmed
at the CLI too: `governance` is the first of fifteen top-level keys.

## Real-repo evidence

`scripts/push-scan/sample-reports/spring-petclinic-rescan-post-increment-25.json`,
stored alongside the increment-24 rescan rather than replacing it.

Diffed against increment 24 after normalizing the clone path:
**identical — same values, same order, no key added or removed except
`governance`.** `confidence` 0.385, `confidenceByDimension` unchanged on
all four, `layerEntropy` 0.667, `cycleEntropy` 0.0, `persistenceEntropy`
0.0, `securityEntropy` undefined, `totalEntropy` 0.222, `L3_CONTROLLED`,
118 nodes, 353 edges. A nine-to-three restructure of the config record and
a change to how `LayerPolicy` stores its matrix moved nothing at all on
real code.

## Increment report

```
Increment:      25 — governance policy boundary (OQ-02)
Question(s):    OQ-02. How is governance represented, and where are governance
                rules authored?
Decision:       Four authorship classes. GovernancePolicy gathers the four
                organizational declarations with no default for any of them;
                DetectionCatalog gathers the three technology catalogs;
                PipelineConfig becomes (ExtractionRequest, DetectionCatalog,
                GovernancePolicy). LayerPolicy becomes readable, with storage
                canonicalized on Role declaration order. The policy is
                serialized under one additive "governance" key. Relation scope
                is decided as measurement definition, not governance. OQ-02
                becomes PARTIALLY DECIDED.
Why:            Each governance member already describes itself as governance in
                its own javadoc, and §4.2 uses the word "governed" about the
                self-cycle flag. A declaration nobody can read back is not
                inspectable, so accessors and serialization are what the
                acceptance criterion actually asks for. Nothing smaller than one
                named governance value suffices for OQ-04/OQ-06; nothing larger
                is justified before they decide what a subsystem is.
Files changed:  GovernancePolicy.java, DetectionCatalog.java, GovernanceJson.java
                (new); LayerPolicy.java (accessors + canonical ordering);
                PipelineConfig.java (9 components -> 3); Pipeline.java (accessor
                paths); PipelineReport.java (carries its governance); Main.java
                (config factory split, additive JSON key, toJson package-private);
                aerf-pipeline/pom.xml (aerf-extraction declared explicitly);
                6 new test classes + 2 extended; docs/increment-25-*.md (new);
                backlog status tracker; post-increment-25 sample scan.
Tests added:    42 (266 -> 308)
Full test cmd:  mvn -B test
Test result:    308 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
Regression:     None. Every protected exact value passes unmodified. The real
                spring-petclinic scan is byte-identical to increment 24's apart
                from the added key. Five CLI runs emit an identical governance
                block, where the pre-fix code demonstrably varied.
Real-repo:      spring-petclinic — all metrics unmoved; governance now visible
                in the report for the first time.
Reachability:   Proven by GovernanceReportEndToEndTest through a real
                Pipeline.run, not only at the CLI.
Docs updated:   This file; backlog status tracker (OQ-02 -> PARTIALLY DECIDED).
Deferred kept:  OQ-02's role-assignment half, OQ-04/OQ-06 scope, relation-set
                configurability and concern selection each stay open and are
                guarded by GovernanceBoundaryTest.
Commit:         see git log for this increment
```
