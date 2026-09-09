# Increment 13 — `aerf-openrewrite`, Class-Level Extraction

## Objective

Third step of the ExtractionAdapter Plan: the first concrete
`SourceExtractor`. Parse real Java source with OpenRewrite and emit
`COMPONENT` `NodeFact`s plus `EXTENDS`/`IMPLEMENTS` `EdgeFact`s, with
fidelity (`L2_SYMBOL_RESOLVED` vs `L1_SYNTAX`) following OpenRewrite's own
type attribution — the first time anything in this project has run
against parsed source rather than a hand-built or hand-authored graph.

## Scope

New module `aerf-openrewrite`, depending on `aerf-extraction` and
`org.openrewrite:rewrite-java:8.90.4` (plus `rewrite-java-21` at runtime
and `slf4j-nop`, both required per the Step 0 spike's findings). This is
the **only** AERF module permitted to import an OpenRewrite type — it
carries no `maven-enforcer` ban, unlike the other four modules.

- `JavaClassExtractor` — the `SourceExtractor` implementation: walks a
  source root for `.java` files, parses them as one OpenRewrite batch,
  and visits each `J.ClassDeclaration` to emit one `COMPONENT` `NodeFact`
  per class/interface/enum/record, plus one `EdgeFact` per
  `extends`/`implements` target.
- `TypeTreeNames` — reconstructs a type reference's as-written syntactic
  name from its parse tree, used as the `SymbolRef` key/description when
  OpenRewrite's own type attribution did not resolve it.
- A synthetic sample project under `src/test/resources/sample-project`
  (five files, parsed as data — never compiled by the build, per the
  plan's explicit placement instruction) exercising: a resolved
  `extends` (`Order` → `BaseEntity`), a resolved `implements` of a JDK
  interface (`Order` → `Serializable`), a resolved `implements` of an
  in-batch interface (`OrderRepositoryImpl` → `OrderRepository`), and a
  deliberately undeclared, off-any-classpath `extends`
  (`LegacyWidget` → `com.example.external.UnknownFramework`).
- 6 new tests in `JavaClassExtractorTest`, covering both the no-classpath
  and explicit-classpath request shapes, per the plan's "test both
  modes" instruction.

**Explicitly out of scope, deferred to a later increment:** `DEPENDS`
edges (field/parameter/return-type references beyond the type's own
supertypes). The plan's increment list names `DEPENDS` alongside
`EXTENDS`/`IMPLEMENTS` for Increment 13, but extracting it well needs
method-level traversal (field declarations, method signatures) that
Increment 14 already owns, and doing it here would mean either a second,
overlapping visitor pass now or a half-considered one bolted onto class
declarations alone. Emitting `EXTENDS`/`IMPLEMENTS` cleanly first, and
folding `DEPENDS` into Increment 14's method/field pass where the
relevant AST nodes are already being visited, keeps each increment's
diff reviewable against one clearly stated scope — the project's stated
discipline — rather than a class-level increment reaching partway into
member-level territory. Recorded here rather than silently dropped.

## Step 0 spike findings applied

The throwaway spike (not committed) that preceded this increment
established, by actually running OpenRewrite 8.90.4 rather than assuming
its API from memory:

- `JavaParser.fromJavaVersion().build()`, batch-parsed with
  `parser.parse(List<Path>, Path relativeTo, ExecutionContext)`, resolves
  cross-file references within the same batch — and JDK types (like
  `java.io.Serializable`) — from the bootstrap classpath, without any
  external classpath supplied. An external classpath is only needed for
  genuine third-party dependencies.
- Two different "unresolved" representations exist depending on tree
  position: `JavaType.Unknown` (a singleton sentinel, checked via
  `instanceof JavaType.Unknown`) for type-tree positions like
  extends/implements targets; a plain `null` for
  `MethodInvocation.getMethodType()` (relevant starting Increment 14).
- `org.slf4j:slf4j-nop` is required at runtime — without an slf4j binding
  on the classpath, OpenRewrite's internal diagnostic logging throws
  `NoClassDefFoundError` (caught internally, but floods stderr).

All three were carried into this increment's `pom.xml` and
`JavaClassExtractor` unchanged from the spike's findings.

## Implementation decisions not dictated by v0.4

1. **A class/interface/enum/record's own type must itself resolve
   before a `NodeFact` is emitted for it.** `visitClassDeclaration` only
   emits a `NodeFact` when `classDecl.getType()` is a resolved
   `JavaType.FullyQualified` (not `JavaType.Unknown`). In practice a
   type declared in the file being parsed always resolves — OpenRewrite
   attributes the type of the very declaration it is parsing — so this
   is a defensive floor, not something the sample project exercises as
   a live case; it prevents ever emitting a `NodeFact` under a
   syntactic, possibly-unstable key.
2. **An edge's fidelity is `L2_SYMBOL_RESOLVED` exactly when
   OpenRewrite's own type attribution resolved that specific target**,
   independent of whether the *declaring* class's own type resolved
   (which is already guaranteed by decision 1) and independent of
   whether the target will end up graph-resolved once `GraphAssembler`
   runs. See "Evidence" below — these are three genuinely different
   questions this increment had to keep distinct.
3. **The `SymbolRef` key is the resolved fully-qualified name when
   OpenRewrite resolved the type, and the as-written syntactic name
   (via `TypeTreeNames`) when it did not.** This matches
   `JavaNodeIds.type(...)`'s own normalization on the declaring side, so
   a resolved reference to an in-batch type always finds its
   declaration's `NodeFact` regardless of which file was parsed first.
4. **`TypeTreeNames` handles exactly the three shapes that occur in
   practice for an extends/implements target** — a simple name
   (`J.Identifier`), a dotted qualified name (`J.FieldAccess`), and a
   generic type's raw name (`J.ParameterizedType`, via its `getClazz()`)
   — falling back to the tree node's own `toString()` for anything else.
   A degraded fallback is acceptable here specifically because an
   unresolved reference this class cannot print precisely ends up
   `NodeRef.Unresolved` either way; the key only needs to be a stable,
   human-legible description, not a machine-checked identifier.
5. **Diagnostics record only the "not a compilation unit" case.**
   OpenRewrite can return a `SourceFile` that parsed to something other
   than `J.CompilationUnit` (e.g. a parse error placeholder); this
   increment does not attempt to classify or recover from that beyond
   recording it as a diagnostic string and skipping the file — a
   fuller parse-failure taxonomy is not needed until a real (non-sample)
   codebase is run, which is Increment 16's concern.

## Evidence — what this increment proves or exposes about the AERF model

- **This is the first real code AERF has ever analyzed.** Every prior
  increment's graph was hand-built or came from
  `CanonicalSampleGraphs`. `JavaClassExtractorTest` is the first test in
  the project whose graph comes from parsing `.java` text.
- **Extractor-level type resolution and graph-level fact resolution are
  two genuinely different things, and the sample project's JDK
  `implements Serializable` case is the sharpest illustration so far.**
  OpenRewrite resolves `java.io.Serializable`'s type outright (no
  external classpath needed — it's a JDK type), so the `EdgeFact`'s
  evidence correctly records `L2_SYMBOL_RESOLVED` fidelity. But
  `GraphAssembler` resolves a `SymbolRef` only against `NodeFact`s
  *this run itself declared* — and no `NodeFact` was ever emitted for
  `java.io.Serializable`, because it's a JDK type outside the extracted
  source set, not a file this extractor parsed. So the edge's target in
  the final `Graph` is `NodeRef.Unresolved("Serializable")`, evidenced
  with `L2_SYMBOL_RESOLVED` fidelity. That is not a contradiction or a
  bug: it is section 8's conservatism working exactly as designed — an
  adapter must not fabricate a node for a type it never itself observed
  a declaration for, no matter how confidently it identified that
  type's name. `recordsJdkImplementsTargetsAsL2ResolvedFidelityButGraphUnresolved`
  asserts precisely this combination, and it is the increment's most
  important finding: **`ExtractionFidelity` on an `Edge`'s evidence
  describes how well the *reference* was identified; whether the
  `NodeRef` ends up `Resolved` describes whether the *referent* was
  itself extracted. A caller reading only `NodeRef` state, without the
  evidence, would incorrectly read this as "unresolved because
  OpenRewrite couldn't figure out what Serializable is."**
- **The undeclared, off-classpath `LegacyWidget` case behaves exactly as
  designed end-to-end:** `L1_SYNTAX` fidelity (OpenRewrite genuinely
  could not resolve `com.example.external.UnknownFramework`'s type — no
  such class exists anywhere reachable), a `SymbolRef` keyed by
  `TypeTreeNames`'s reconstructed syntactic name
  (`com.example.external.UnknownFramework`), and a final
  `NodeRef.Unresolved` in the graph. Two independent reasons an edge
  ends up unresolved (JDK type resolved but not extracted; genuinely
  unresolvable type) now both have direct test coverage.
- **Supplying an (irrelevant) explicit classpath doesn't change any
  within-batch or JDK resolution result** — confirmed by
  `anExplicitClasspathDoesNotChangeWithinBatchResolutionResults`, which
  re-runs the same assertions with `ExtractionRequest`'s classpath set to
  a harmless extra entry. This is a narrow check (no test here exercises
  a *needed* classpath entry resolving a genuine third-party type,
  since the sample project intentionally has no third-party
  dependency) but it does prove the classpath wiring itself
  (`JavaParser.Builder.classpath(...)`) doesn't silently break anything
  when present.
- **Determinism holds across repeated runs** of the same sample
  (`extractionIsDeterministicAcrossRepeatedRuns`), inherited directly
  from `GraphAssembler`'s canonical sort plus this extractor's own
  sorted file-walk order (`collectJavaFiles` sorts by path string before
  parsing, so the result never depends on filesystem directory-listing
  order).
- **The two API-surface corrections needed to make the Step 0 spike's
  reasoning compile** were both minor and mechanical, not design
  changes: `J.ClassDeclaration.getKind()` already returns
  `Kind.Type` directly rather than a `Kind` object needing a further
  `.getType()` call (fixed by removing the extra call, confirmed against
  the real compiled class via `javap`). Every other API surface the
  spike verified — the `Parser.parse(Iterable<Path>, Path,
  ExecutionContext)` `null`-relativeTo call, the
  `JavaParser.Builder<? extends JavaParser, ?>` wildcard-capture
  reassignment, `TypeTree`/`Expression`/`J` hierarchy compatibility in
  `TypeTreeNames`, and `J.ParameterizedType.getClazz()`'s name —
  compiled on the first attempt.

## Open questions for the architecture

No new entries in `open-questions-register.md`. This increment answers
part of open question #1 in spirit (real extracted evidence now exists)
but not yet in substance — no role-inference-relevant evidence
(annotations, naming conventions) is emitted until Increment 15; this
increment's `NodeFact`s carry empty attribute maps.

## Scope check

Class-level `EXTENDS`/`IMPLEMENTS` only — no `DEPENDS` (deferred above),
no method-level facts, no `CALL` edges, no execution-context/loop
detection, no annotation evidence, no pipeline runner, no CI. `mvn -B
test` at the repo root passes with 195 tests total (up from 156 before
Increment 11), across all six modules including this one's 6. This is
exactly Increment 13's slice of the plan — the class-level foundation
Increment 14's method-level pass builds on next.
