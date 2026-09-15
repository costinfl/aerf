# Finding R — Evidence attributes reach the report

A maintenance fix, not a commissioned increment. The BA agent classified
finding R as "an evidence-traceability maintenance defect", separate from the
H/I/U/V evidence-acquisition work and from the S/T evidence-model boundary.

## The defect

`GraphJson.evidence(...)` serialized an evidence item's `sourceAdapter`,
`description`, `location`, `fidelity` and `executionContext` — and silently
dropped `Evidence.attributes()`.

That map is not decoration. `DefaultSeedRules` assigns roles by matching
`evidence.attributes().get("annotation")` against a resolved fully-qualified
name, and `DefaultSeedRules`' Spring Data rule matches
`springDataMarkerInterface` the same way.

On a real spring-petclinic scan those two attributes decide **every role the
run assigns**:

| Attribute | Occurrences | What it decides |
|---|---|---|
| `annotation=org.springframework.stereotype.Controller` | 43 | every `PRESENTATION` node |
| `springDataMarkerInterface=org.springframework.data.jpa.repository.JpaRepository` | 5 | `PERSISTENCE` |
| `springDataMarkerInterface=org.springframework.data.repository.Repository` | 3 | `PERSISTENCE` |
| *(no attributes)* | 530 | plain declaration evidence |

**51 of 118 nodes** — 43 + 8 — carried a role whose deciding evidence was
invisible in the report that stated the role. The original finding named only
the 43; measuring it while fixing it showed the Spring Data half was equally
affected.

### Why the description was not a substitute

A reader saw `"description": "@Controller annotation observed"`, which is built
from the annotation's **simple name**. The extractor deliberately records
evidence only for an annotation whose type resolved to one of three exact FQNs —
its own javadoc says matching "a real FQN, not a name, is what makes this
conservative rather than a heuristic that could misattribute an unrelated
framework's same-named annotation."

Dropping the attribute discarded precisely that distinction. The report showed
the conservative rule's *conclusion* while hiding the evidence that made it
conservative. §3.5 requires evidence to stay traceable, and it was not.

## The fix

One production file: `aerf-report/src/main/java/org/aerf/report/GraphJson.java`.

`evidence(...)` gains an `attributes` object. Node and evidence attribute maps
now render through **one** private helper, so the two cannot diverge again.
`Evidence` already stores them in a `LinkedHashMap` for determinism, and the
renderer preserves that order rather than sorting — §14's "identical sources
produce identical output" holds across runs, not just within one.

An evidence item with no attributes serializes as `{}` rather than omitting the
key, matching how node attributes have always behaved: absent and empty are
different claims, and a consumer should never have to distinguish "this adapter
recorded nothing" from "this serializer forgot".

## Sample reports: existing ones untouched

The five committed samples are **unchanged**. They are dated evidence of what
increments 19/20/24/25/26 produced, and §10.5 forbids rewriting history
silently — increments 24, 25 and 26 each *added* a dated sample rather than
editing an earlier one.

`spring-petclinic-rescan-post-finding-r.json` is added alongside them.

## Verification

- `mvn -B test`: **523 tests, 0 failures, 0 errors, 0 skipped** (515 → 523).
  No existing test file was modified except `GraphJsonTest`, purely additively.
- **Protected exact values pass verbatim** — `PipelineTest`'s 5.0/7.0
  confidence, 1.0 layer, 0.5 persistence and the three per-dimension fractions;
  `AnalysisConfidenceTest`'s 0.8; `PersistenceEntropyCalculatorTest`'s 0.125 and
  its `value()==1.0`/`weightedValue()==3.0` pair; `AggregatedEntropyTest`'s five;
  `DriftTest`'s eight; `CycleEntropyCalculatorTest`'s 2.0/3.0.
- **§10.3 does not apply** — this is serialization only. Confirmed rather than
  assumed: no calculator was touched, all four `relevantRelations` re-read and
  unchanged, and `AnalysisConfidence` is unreachable from the change.
- **Real-repo:** a fresh spring-petclinic scan is **identical to increment 30's
  once the new key is removed from every evidence object** — verified
  programmatically by stripping `attributes` from each object carrying
  `sourceAdapter` + `executionContext` and comparing the whole document. The
  change is purely additive; no value moved.

`EvidenceTraceabilityEndToEndTest` is the reachability proof, and it is the test
that could not be written before: it takes a `PRESENTATION` node from a real
`Pipeline.run`, asserts in memory that its role came from the annotation
attribute, then asserts the same attribute appears in `Main.toJson`'s output.

## Scope

This is the first report change since increment 30, and the first this phase
that is **not** confined to a new top-level key — it adds one key to every
evidence object, 522 of them in a petclinic scan. Any consumer diffing reports
across this commit will see that, which is why it is recorded here rather than
folded into other work.

Nothing else changed. No calculator, no metric, no governance type, no measured
value.

## Findings recorded while fixing

- **Y — `DEPENDS` drops generic type arguments.** `Owner.pets : List<Pet>`
  produces an edge to `List`, never to `Pet`. A bidirectional JPA association is
  the most common source of a class-level cycle in Java and is therefore
  invisible to cycle entropy. Fixing it would add `DEPENDS` edges, moving the
  layer denominator, cycle entropy and three confidences at once — a §10.3
  measurement change needing its own commissioned item. **This finding
  retargeted the cycle-rich acquisition away from JPA codebases.**
- **Z — library and JDK types appear unresolved.** On the live scan `String`,
  `List`, `Integer` and `LocalDate` all produce *unresolved* `DEPENDS` targets
  while project-local types resolve, which suggests dependency jars are not
  reaching OpenRewrite's type table. If so it depresses every confidence
  denominator — petclinic's layer confidence is 0.377. Recorded for
  investigation, not fixed here.
