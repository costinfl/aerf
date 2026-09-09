# Increment 15 — Spring/Annotation Semantic Evidence

## Objective

Fifth step of the ExtractionAdapter Plan: emit structured annotation
evidence so `DefaultSeedRules` (Increment 2) can classify real,
parsed source for the first time — replacing the invented test strings
role inference has run against since Increment 2 with evidence a real
adapter actually produced. This is where open question #1 ("when does
seed-only role classification actually fail?") finally gets real data
to run against, per the plan's own framing, even though (see "Evidence"
below) the answer this sample gives is not yet a failure case.

## Scope

`JavaSourceExtractor`'s `visitClassDeclaration` gains one addition:
`springStereotypeEvidence(...)`, called for every class's leading
annotations, which emits `Evidence` for exactly the three Spring
stereotype annotations `DefaultSeedRules` already knows how to read
(`@Controller`, `@Service`, `@Repository`) — using the literal
`sourceAdapter` strings (`"spring"`, `"spring-data"`) and `annotation`
attribute key those rules were already written against (Increment 2,
Amendment 5). Only an annotation whose type *resolved* to exactly one of
these three fully-qualified names produces evidence; anything else,
resolved or not, is silently skipped.

Three local stub annotation source files
(`org/springframework/stereotype/{Controller,Service,Repository}.java`)
are added to the sample project — real `@interface` declarations in the
real `org.springframework.stereotype` package, so their resolved
fully-qualified names are byte-identical to what a genuine Spring
dependency would produce, without adding one (per the plan's owner
decision: "no network at analysis time," and the plan's own anticipation
that "local stub Spring annotation source files" might be needed for
fully offline testing).

Three sample classes now carry a stereotype: `@Service` on
`OrderService`, `@Repository` on `OrderRepositoryImpl`, and a new class,
`OrderController.java` (`@Controller`, with a field `DEPENDS` on
`OrderService` and a resolved `CALL` to `OrderService#placeOrder`) —
giving the sample project one representative of each of the three roles
`DefaultSeedRules` already has a rule for.

5 new tests in `JavaSourceExtractorTest`, including one that runs the
full extraction → assembly → `SeedRoleInferenceEngine` pipeline and
asserts real roles come out the other end. `aerf-openrewrite`'s `pom.xml`
gains a test-scoped dependency on `aerf-analysis` to make this possible.

**Explicitly out of scope, deferred to a later increment:** meta-annotation
resolution (real Spring's `@Service` is itself meta-annotated
`@Component`; a custom annotation meta-annotated with `@Service` would
not be detected here — only an exact match on one of the three literal
FQNs is checked, one level deep); any annotation beyond the three named
stereotypes (no generic "any annotation becomes evidence" mechanism was
built — see "Why exactly three, not general annotation evidence" below);
annotation *arguments* (e.g. `@RequestMapping("/orders")`'s path string)
— `J.Annotation.getArguments()` is never read.

## Implementation decisions not dictated by v0.4

1. **Why exactly three, not general annotation evidence.** A generic
   "emit evidence for every annotation observed" mechanism was
   considered and rejected for this increment: it would produce evidence
   no seed rule reads yet (dead weight), and — more importantly — general
   annotation-to-attribute mapping raises design questions (how to
   represent annotation arguments, repeated annotations, annotations on
   members vs. types) this increment doesn't need answered to satisfy its
   actual goal, which is specifically "make the existing three seed rules
   fire on real code." Scoping to exactly what `DefaultSeedRules`
   consumes keeps the increment's diff matched to its stated purpose,
   per this project's stated discipline against speculative generality.
2. **A resolved match on the exact FQN, never the simple name.** An
   annotation named `@Controller` whose type did not resolve (no stub
   file, wrong package, a same-named annotation from an unrelated
   library) produces no evidence at all, rather than a best-effort guess
   from the simple name — the same conservatism Increment 13 established
   for `EXTENDS`/`IMPLEMENTS` targets, applied here to annotations. A
   simple-name match would risk classifying a node by a name collision
   rather than a verified fact, which §8 rules out.
3. **Local stub annotation files, not a real `spring-context` dependency,
   and not a fabricated non-Spring package.** Adding the real dependency
   would violate the plan's "no network at analysis time" decision and
   couple this test-only fixture to an actual Spring version; inventing
   a fake package (e.g. `com.example.stereotype.Controller`) would mean
   the FQN constants `DefaultSeedRules` and this extractor both hard-code
   (`org.springframework.stereotype.Controller`) could never actually be
   exercised by a test. A minimal, real-package stub reconciles both
   constraints: the resolved FQN is exactly what production code would
   produce, entirely offline.
4. **Stereotype evidence is *added to* the existing declaration evidence
   on the same `NodeFact`, not a separate node or a replacement.** A
   class's `COMPONENT` `NodeFact` already carries one evidence item (the
   declaration itself, from Increment 13); this increment's evidence is
   appended to that same list before the fact is built once, so
   `GraphAssembler`'s existing node-merge logic needs no changes — the
   annotation evidence is simply more evidence about a node this
   extractor was already going to report.

## Evidence — what this increment proves or exposes about the AERF model

- **The core payoff: unmodified, pre-existing role-inference code
  correctly classifies real extracted evidence.**
  `realExtractedEvidenceDrivesSeedRoleInferenceEndToEnd` runs
  `JavaSourceExtractor` → `GraphAssembler` → `SeedRoleInferenceEngine`
  with `DefaultSeedRules.illustrativeRules()` — rules written in
  Increment 2 against hand-invented test evidence, with zero changes
  needed here — and gets `OrderController` → `PRESENTATION`,
  `OrderService` → `APPLICATION`, `OrderRepositoryImpl` → `PERSISTENCE`,
  and `BaseEntity` (no stereotype) → `UNKNOWN`. This is the first time
  in the project that role inference has run on anything other than
  hand-built test evidence, and it validates the separation §9 and this
  entire plan are built on: `DefaultSeedRules` never needed to know
  OpenRewrite would eventually exist for its evidence contract
  (`sourceAdapter`, `description`, `attributes`) to be satisfiable by a
  real adapter.
- **Open question #1 gets real data, but not a failure case — and that
  distinction matters.** The plan predicted this increment would be
  "where open question #1 finally gets real data." It does, but the
  sample's three stereotyped classes are each unambiguous under seed
  rules alone (one stereotype, no rule conflict, no missing evidence a
  neighbor's role could have filled in) — exactly the shape `R^(0)`
  already handles. Recorded in `open-questions-register.md` as *still
  open*, not resolved: a genuine counter-example needs either an
  adversarial fixture or a real cloned repository (Increment 16), not
  this one. Reporting a question as "answered" because *a* real
  datapoint now exists, when that datapoint doesn't actually test the
  hard case, would be exactly the kind of unearned confidence this
  project's discipline (§14, "verify, don't assume") exists to prevent.
- **The annotation-type stub files are themselves extracted as
  `COMPONENT` nodes** (`Kind.Type.Annotation` is one of
  `J.ClassDeclaration`'s kinds, confirmed via `javap` before writing the
  test), raising the sample's total `COMPONENT` count from 7
  (`com.example.*`) to 10. `JavaSourceExtractor` was not special-cased to
  exclude them — an annotation type is a genuine declared type this
  extractor observed, and §8 gives no basis for it to decide unilaterally
  that a real declaration is uninteresting. `doesNotEmitStereotypeEvidenceForATypeWithNoSpringAnnotation`
  (using `BaseEntity`, which carries no annotation at all) is the
  negative control proving stereotype evidence isn't emitted
  unconditionally.

## Open questions for the architecture

Open question #1 updated (see above) — recorded as still open, with the
specific gap (no adversarial/ambiguous fixture yet) named explicitly
rather than left vague. No other new entries.

## Scope check

Three named stereotypes only — no meta-annotations, no other annotation
types, no annotation arguments, no change to `DefaultSeedRules` or
`DefaultSecurityRules` themselves (both were already written to expect
exactly this evidence shape, since Amendment 5). `mvn -B test` at the
repo root passes with 203 tests total (up from 200 after Increment 14),
across all six modules including this one's 14. This is exactly
Increment 15's slice of the plan — Increment 16's pipeline runner and
CI now have real role-classified evidence to report on, and a real
cloned repository is the natural next place to look for the seed-only
failure case Increment 15 did not surface.
