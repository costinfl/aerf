# Increment 21 — A Structural `MEMBER_OF` Relation

## Objective

Close open question #17: the canonical model has no structural relation
connecting a `FUNCTION` node to the `COMPONENT` node that declares it, so
Increment 16 worked around the gap by copying a class's stereotype
evidence onto its own declared methods at extraction time — a real fix
for role propagation, but adapter-level evidence duplication, not a
graph fact any future rule or metric could use.

## Scope

- `RelationType` gains `MEMBER_OF` (`aerf-model`) — recorded as **AERF
  v0.4.1 patch Amendment 6** in `docs/aerf-v0.4.1-patch.md`, since this
  changes §2.4's canonical edge model, unlike Increment 20's purely
  adapter-level work.
- `JavaSourceExtractor` emits one `MEMBER_OF` edge per declared method,
  from the method's `FUNCTION` node to its declaring class's `COMPONENT`
  node. Always resolvable by construction — a method's declaring class is
  never external to the parse batch it's declared in, unlike every other
  edge this extractor emits.
- **Deliberately not consumed by role inference.** Open question #17
  named three options — add the relation, add a dedicated
  graph-refinement rule consuming it, or keep evidence-copying as the
  accepted mechanism. This increment adopts the first and third, not the
  second: `GraphRoleRefinementRule`'s contract (Amendment 4) only lets a
  rule see roles as of the *start* of the current pass, so a
  `MEMBER_OF`-consuming rule could only resolve a method one full pass
  *after* its class resolves — changing the exact pass counts several
  already-verified worked examples depend on (Increment 16's defect
  sample, Increment 18/20's real-repository runs), for no gain over what
  evidence-copying already gives role inference today. The relation is
  added for its own sake, not as a replacement.

## A real side effect this increment found and fixed

Adding `MEMBER_OF` edges to the graph moved `aerf-pipeline`'s own
defect-sample confidence from 0.714 to 0.857 — caught immediately by
`PipelineTest`'s existing, exact-value assertion. Root cause:
`AnalysisConfidence` (§5.4) is deliberately graph-wide, not scoped to any
metric's own relation filter — it originally read "relevant relations"
as *every* edge in the graph. A `MEMBER_OF` edge is always resolved by
construction, so counting it inflates confidence by an amount
proportional to how many methods a codebase happens to have, with zero
relationship to how well extraction resolved anything genuinely
uncertain — exactly backwards from what confidence exists to measure.
Fixed by excluding `MEMBER_OF` from both `AnalysisConfidence`'s numerator
and denominator, documented in that class's own javadoc and in Amendment
6 itself. No entropy metric needed a similar fix — `LayerEntropyCalculator`,
`CycleEntropyCalculator`, and `PersistenceEntropyCalculator` all already
scope to an explicit relation allow-list that never included `MEMBER_OF`.

## Evidence — what building and testing this proved

- `emitsAMemberOfEdgeFromEachMethodToItsDeclaringClass`
  (`JavaSourceExtractorTest`): the edge exists and resolves correctly.
- `memberOfEdgesAreExcludedEntirelyEvenWhenUnresolved`
  (`AnalysisConfidenceTest`): proves the exclusion is unconditional, not
  merely "happens not to matter because they're always resolved" — an
  artificially unresolved `MEMBER_OF` edge is paired with a real
  unresolved `CALL` edge and asserted to have zero effect either way.
- `PipelineTest`'s pre-existing confidence assertion passes unchanged
  once the exclusion is in place — direct proof the fix actually
  restores the intended behavior, not just that some number moved.
- 219 tests pass across the reactor.

## Open questions for the architecture

None newly raised. Closes open question #17 — see the Resolved section
of `docs/open-questions-register.md`.

## Scope check

A real, narrow canonical-model change (`aerf-model` gains one enum
constant; `aerf-analysis`'s calibration javadoc gains a documented
exclusion), plus the adapter-level edge emission that makes it real
data. No role-inference behavior changes — confirmed by every existing
role-related test passing with unchanged assertions.
