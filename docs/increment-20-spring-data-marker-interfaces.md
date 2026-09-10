# Increment 20 — Spring Data Marker-Interface Persistence Evidence

## Objective

Close open question #18: `DefaultSeedRules`' only persistence rule
(`PersistenceBySpringDataAdapter`) fires on `sourceAdapter ==
"spring-data"` evidence, but `JavaSourceExtractor` only ever produced
that evidence for an explicit `@Repository` annotation. Increment 18's
real `spring-petclinic` run found this makes every idiomatic Spring Data
repository (`extends JpaRepository<...>`, no annotation at all)
invisible to role inference — a large, common real-world category, not
an edge case. This increment makes `JavaSourceExtractor` recognize that
shape too.

## Scope

- `JavaSourceExtractor` gains `SPRING_DATA_MARKER_INTERFACES`, a
  hard-coded set of Spring Data's own marker-interface family
  (`org.springframework.data.repository.Repository` and its common
  subinterfaces — `CrudRepository`, `PagingAndSortingRepository`,
  `ListCrudRepository`, `ListPagingAndSortingRepository`,
  `ReactiveCrudRepository`, `ReactiveSortingRepository` — plus
  `org.springframework.data.jpa.repository.JpaRepository`, the one real
  `spring-framework-petclinic`/`spring-petclinic` repositories actually
  extend).
- When a class's **directly declared** `extends`/`implements` target
  resolves (by FQN, never by simple name — same conservatism as every
  annotation match in this adapter) to one of those interfaces,
  `visitClassDeclaration` now emits a `spring-data`-sourced `Evidence`
  item carrying a new `springDataMarkerInterface` attribute (the
  matched FQN) — structurally parallel to `springStereotypeEvidence`'s
  handling of `@Repository`/`@Service`/`@Controller`, and read by the
  exact same, unmodified `PersistenceBySpringDataAdapter` seed rule (it
  already matches on `sourceAdapter == "spring-data"` alone, not on any
  attribute) — **no seed-rule-side change was needed at all.**
- This evidence is folded into the same per-class evidence list that
  already gets copied onto a class's declared methods at extraction time
  (the Increment 16 substitute for open question #17's missing
  declares/member-of relation), so a repository interface's own methods
  become classifiable too, not just the interface itself. The field that
  holds it was renamed `currentOwnerStereotypeEvidence` →
  `currentOwnerRoleEvidence` since it is no longer only annotation-driven.
- New fixtures: a local stub
  `org/springframework/data/repository/Repository.java` (same reasoning
  as the existing `org/springframework/stereotype/*` stubs — same FQN as
  the real Spring Data type, zero network access) and
  `com/example/OrderQueryRepository.java`, an interface extending it
  directly with **no** `@Repository` annotation — the real-world shape
  this increment exists for. `OrderRepositoryImpl` (annotation-based,
  already covered) stays as the negative control for the marker-interface
  path and vice versa.

## Why direct-declaration only, not transitive

Open question #18 explicitly named this as undecided: should
`interface MyRepo extends JpaRepository<...>` be recognized only when
`JpaRepository` (or one of its siblings) is the class's own immediate
supertype, or also through an intermediate custom interface one layer
removed? This increment matches **only the directly declared**
`extends`/`implements` target — the same one-hop scope
`InheritRoleFromSupertype` already uses for graph-relationship
refinement, and the simpler of the two readings. No real repository
examined so far (`spring-petclinic`, `spring-framework-petclinic`)
needed the transitive case; widening this is left for a future increment
if one is actually found, rather than guessed at now.

## Evidence — what building and testing this proved

- `recognizesASpringDataRepositoryByMarkerInterfaceWithNoAnnotationAtAll`:
  `OrderQueryRepository` (no annotation) gets `spring-data` evidence with
  `springDataMarkerInterface = org.springframework.data.repository.Repository`,
  propagated onto its declared method too.
- `realExtractedEvidenceDrivesSeedRoleInferenceEndToEnd` extended:
  `OrderQueryRepository` now classifies as `Role.PERSISTENCE` end-to-end
  through unmodified `DefaultSeedRules` — the exact failure case open
  question #1/#18 named, now fixed for this shape.
- Negative control unchanged: `BaseEntity` (no annotation, no marker
  interface) still gets no spring/spring-data evidence at all.
- 217 tests pass across the reactor. `JavaSourceExtractorTest` itself is
  now 16 tests, up from 15 — 1 new test plus extending one existing
  test's assertions.

## Open questions for the architecture

None newly raised. This closes open question #18 and, for the marker-
interface shape specifically, strengthens open question #1's real
evidence (the annotation-only gap it found is no longer a gap for this
shape). The register is updated accordingly — see
`docs/open-questions-register.md`.

## Scope check

Extractor-level rule catalog work only (`aerf-openrewrite`, illustrative
per its own javadoc — "concrete rules are explicitly extraction-adapter
work," not part of the frozen v0.4 spec itself). No `aerf-model`/
`aerf-analysis` model change, so no `aerf-v0.4.1-patch.md` amendment is
needed — this is the same kind of entry the patch document's own
Amendment 5 rationale describes as adapter-catalog work, not a spec
amendment.
