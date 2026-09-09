# Increment 14 — Method-Level Extraction and Execution Context

## Objective

Fourth step of the ExtractionAdapter Plan: extend the OpenRewrite adapter
to method level — `FUNCTION` nodes, `CALL` edges, and loop-nesting
detection setting `ExecutionContext.ITERATED` — making AERF v0.4 §4.3's
N+1 heuristic runnable against real code for the first time. Also closes
Increment 13's deferred scope item: `DEPENDS` edges from field
declarations.

## Scope

`JavaClassExtractor` is renamed to `JavaSourceExtractor` (see "Why a
rename" below) and its single visitor extended, in the same one-parse
traversal, to also emit:

- `FUNCTION` `NodeFact`s for each resolved method declaration (including
  constructors — no special-casing needed, since `JavaType.Method`
  already carries a name and declaring type for a constructor the same
  way it does for any other method).
- `DEPENDS` `EdgeFact`s from a class to each of its **field** declarations'
  types (method parameters and local variables are explicitly excluded —
  see "Field vs. local variable" below).
- `CALL` `EdgeFact`s from the enclosing method to each method invocation
  observed in its body, evidenced with `ExecutionContext.ITERATED` when
  the call site is lexically inside a `for`, for-each, `while`, or
  `do`-`while` loop, and left at the default `ExecutionContext.UNKNOWN`
  otherwise (never `SINGLE` — see "Why not SINGLE" below).
- A sixth sample file, `OrderService.java`, adding: a field-level
  `DEPENDS` on an in-batch interface (`repository: OrderRepository`) and
  an in-batch class (`widget: LegacyWidget`); a resolved `CALL` outside
  any loop (`placeOrder` → `OrderRepository#findById`); the same target
  called again from inside a for-each loop (`reprocessAll`), the
  `ITERATED` fixture; and a call to a method that cannot resolve
  (`widget.legacyOperation()`, since `LegacyWidget`'s own supertype is
  the Increment 13 sample's deliberately-unresolvable one), the L1
  `CALL` fixture.
- 6 new tests in `JavaSourceExtractorTest` (was `JavaClassExtractorTest`).

**Explicitly out of scope, deferred to a later increment:** method
parameter and return-type `DEPENDS` (only field types are covered — see
below); constructor calls made with `new` (`J.NewClass`, not
`J.MethodInvocation` — no `CALL` edge is emitted for `new Order()` in
`OrderRepositoryImpl`); array-typed and multi-argument generic field
types (`TypeTreeNames` does not special-case `J.ArrayType`, so an array
field would degrade to an L1 `toString()` fallback rather than crash —
untested here since the sample has none); static/instance initializer
blocks (their local variables would be misclassified as fields by the
"not inside a method" heuristic — see below; none occur in the sample).

## Why a rename

`JavaClassExtractor` was accurate for Increment 13's scope. Increment
14 broadens the same class to also extract methods, in the same parse
batch and the same visitor pass (re-parsing the batch a second time for
a separate `aerf-openrewrite`-internal extractor, or maintaining two
`SourceExtractor`s a caller would have to remember to run together and
merge diagnostics for, was rejected as needless duplication for no
benefit — nothing about method-level facts requires a second parse).
Renaming to `JavaSourceExtractor` keeps the name accurate rather than
leaving a class called "ClassExtractor" that also extracts methods —
this project's stated preference for verified, honest naming over
convenient inertia. `TypeTreeNames` needed no changes; it operates on
`TypeTree`, which both class-level (`extends`/`implements`) and
member-level (a field's type) call sites already share.

## Implementation decisions not dictated by v0.4

1. **Field vs. local variable, distinguished by "not inside a method,"
   not by AST node type.** `J.VariableDeclarations` represents a field,
   a method parameter, and a local variable identically — OpenRewrite
   gives no dedicated "this is a field" node. Since the visitor already
   tracks `currentMethodId` (null only when not inside any method body),
   reusing that flag to gate `DEPENDS` emission was the smallest correct
   distinction available, and it is exactly right for the sample's
   shapes (fields, parameters, one loop body's local `id` variable in
   `reprocessAll`). Its known gap — a static/instance initializer
   block's own local variables would be misclassified as fields, since
   they too sit outside any `currentMethodId` — is recorded above as an
   explicit scope limit rather than solved speculatively, since AERF v0.4
   §14's discipline favors a documented gap over unverified generality.
2. **A `DEPENDS` edge is skipped entirely for a primitive field type**
   (`int`, `boolean`, ...), checked via `JavaType.Primitive`, rather than
   emitted and then failing to resolve. `NodeType` has no primitive
   variant — a primitive is not a structural artifact this graph model
   represents at all, so recording a "dependency" on `int` would not be
   conservative under-claiming (§8) but a category error.
3. **A `CALL` target's fidelity, key, and merged `ExecutionContext`
   evidence are computed once per invocation, in `visitMethodInvocation`,
   using the same `functionId(...)`/`erasedTypeName(...)` helpers the
   declaring side (`visitMethodDeclaration`) uses.** This guarantees a
   resolved call and its resolved callee's own `NodeFact` agree on the
   exact same `NodeId` string whenever both are in the parsed batch —
   the method-level analogue of Increment 13's `JavaNodeIds.type(...)`
   agreement between a class's own declaration and a reference to it.
4. **Why not `ExecutionContext.SINGLE` for a non-looped call.** The
   `Evidence` javadoc is explicit that `SINGLE` "is itself a positive
   observation," not merely "not `ITERATED`." A call site outside a
   syntactic loop can still execute many times — the enclosing method
   could itself be invoked repeatedly, or called from a loop elsewhere —
   so this extractor has no basis for the positive claim `SINGLE` would
   make. `ExecutionContext.UNKNOWN` (the default) is the honest,
   conservative choice; only `PersistenceEntropyCalculator`'s
   `anyMatch(... == ITERATED)` check (verified by reading
   `PersistenceEntropyCalculator.java` directly, not assumed) actually
   depends on this distinction, and it only ever looks for `ITERATED`.
5. **Loop depth resets to zero at each method boundary**
   (`visitMethodDeclaration` saves and resets `loopDepth`, mirroring how
   it already saved/restored `currentMethodId`), so a call inside a
   nested method-like construct never inherits an outer method's loop
   nesting it does not lexically sit inside. A call inside a lambda body
   *is* still attributed to the lambda's enclosing method (no
   `currentMethodId` reset for `J.Lambda`, since the visitor has no
   override for it and OpenRewrite's default traversal simply recurses
   through) — a deliberate simplification recorded here rather than
   left implicit: this project has no `FUNCTION` node for a lambda
   itself, and attributing its calls to the nearest enclosing named
   method is more informative than dropping them.
6. **An unresolved `CALL` target's `SymbolRef` key is the invocation's
   simple name alone** (`legacyOperation`, not `?#legacyOperation()`),
   since — unlike the class-level `TypeTreeNames` case — no owner type
   is available at all when `getMethodType()` returns `null` (§8.1's
   spike finding: `null`, not `JavaType.Unknown`, for this tree
   position). A bare simple name can never coincide with a real
   `JavaNodeIds.method(...)`-format id, so it stays honestly
   unresolvable rather than colliding with an unrelated method sharing
   the name.

## Evidence — what this increment proves or exposes about the AERF model

- **The N+1 heuristic's evidence now has a real, non-hand-built source.**
  `marksACallEdgeInsideAForEachLoopAsIterated` is the first test in the
  project where `ExecutionContext.ITERATED` comes from parsing actual
  loop syntax rather than being asserted directly in a test fixture.
  `PersistenceEntropyCalculator` itself is unchanged — it already only
  reads `Evidence.executionContext()`, agnostic to where the evidence
  came from — which is exactly the separation of concerns §4.3 and §9
  both call for: the metric doesn't know or care that OpenRewrite exists.
- **The extractor-vs-graph resolution distinction Increment 13 found for
  types (the JDK `Serializable` case) recurs identically for methods.**
  `resolvesACallEdgeToAnInBatchMethodWithoutIteratedContextOutsideALoop`
  and `marksACallEdgeInsideAForEachLoopAsIterated` both resolve fully at
  the graph level only because `OrderRepository#findById(java.lang.Long)`
  is itself declared in the same batch and gets its own `NodeFact`; had
  `findById` been a JDK or external-library method instead, the same
  `L2_SYMBOL_RESOLVED`-fidelity-but-graph-`Unresolved` pattern from
  Increment 13 would recur. Not separately tested here since it would be
  a repeat of an already-proven mechanism, not a new one.
- **Two independent "unresolved" fixtures now exist side by side for the
  same underlying cause** (`LegacyWidget`'s deliberately unresolvable
  supertype): Increment 13's `EXTENDS` edge (`JavaType.Unknown` sentinel,
  a type-tree position) and this increment's `CALL` edge
  (`getMethodType()` returning `null`, a different tree position) — the
  Step 0 spike's finding that these two unresolved representations
  genuinely differ by tree position, not just in theory, now has direct
  coverage of both from the same root cause in one sample.
- **The rename compiled and ran correctly on the first attempt** for
  every carried-over Increment 13 test, confirming the merge of the two
  visitors' state (owner tracking, now joined by method/loop tracking)
  didn't disturb the existing class-level behavior.

## Open questions for the architecture

No new entries in `open-questions-register.md`. Open question #1 (real
role-inference data) still awaits Increment 15's annotation evidence —
this increment's `NodeFact`s still carry empty attribute maps, same as
Increment 13's.

## Scope check

Method-level `FUNCTION`/`CALL`/field-`DEPENDS` only — no annotation
evidence, no parameter/return-type `DEPENDS`, no lambda `FUNCTION` nodes,
no `new`-expression edges, no pipeline runner, no CI. `mvn -B test` at
the repo root passes with 200 tests total (up from 195 after Increment
13), across all six modules including this one's 11. This is exactly
Increment 14's slice of the plan — real `ITERATED` evidence now exists
for Increment 16's end-to-end N+1 verification, and Increment 15's
annotation work has real `FUNCTION`/`COMPONENT` nodes to attach
structured evidence to next.
