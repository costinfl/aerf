# ExtractionAdapter Plan

Status: standing plan, written before Increment 11. Covers Increments
11–16. Amend by appending, the way `aerf-v0.4.1-patch.md` does, so the
record of why the approach changed stays intact.

## Context

Increments 1–10 completed every item on AERF v0.4 §11's MVP freeze
table: canonical graph, role inference, four entropy metrics, invariant
DSL, calibration, JSON reporting. 156 tests pass across three modules.

But **nothing in this project has ever parsed a line of real source
code.** Every graph analysed so far was hand-built in a test or came
from `CanonicalSampleGraphs`. That is the gap between "the AERF model is
expressible" and the project's actual goal — *make the model executable,
then use real code and evidence to determine whether it survives contact
with reality.*

Several entries in `open-questions-register.md` are explicitly
unanswerable without this work: #1 ("when does seed-only role
classification actually fail?" — needs a real codebase), #7 (recursive
Tarjan stack depth — only bites at real graph sizes), #13 (per-dimension
confidence).

This plan builds the extraction adapter §8 and §9 describe, as six small
increments in the established rhythm.

## Constraints this plan must respect

- **§9, the central architectural rule:** *"the AERF graph must not
  expose OpenRewrite AST types. OpenRewrite-derived evidence enters AERF
  through an extraction bridge."* AERF must not become a wrapper around
  OpenRewrite.
- **§8:** adapters are conservative — never invent a relation to
  complete a graph; every relation carries provenance.
- **§8.1:** L2 (symbol-resolved) is the target, but must tolerate
  incomplete classpaths.
- **§11:** Java only. JSP, WebFlow, Hibernate and JavaScript adapters
  are deferred and stay out of scope.
- **§14:** determinism — identical sources produce identical output.

## Decisions taken before planning

1. Validate against a synthetic sample committed to test resources
   **first**, then add a separate optional path for running against a
   real cloned repository, used by CI only — not by the normal test
   suite.
2. `Evidence` gains **structured attributes**, so adapters emit
   `annotation=org.springframework.stereotype.Controller` rather than
   prose that rules string-match. Requires a v0.4.1-patch amendment.
3. Classpath is **caller-supplied and optional**: with one →
   `L2_SYMBOL_RESOLVED` and resolved references; without one → parse
   anyway, record `L1_SYNTAX`, and emit `NodeRef.unresolved(...)`
   honestly. Both modes tested. No network access at analysis time.

## Architecture

Two new modules, following the names the original brief suggested:

| Module | Depends on | Carries OpenRewrite? |
|---|---|---|
| `aerf-extraction` | `aerf-model` only | **No** — buildable and fully testable without it |
| `aerf-openrewrite` | `aerf-extraction`, `aerf-model` | **Yes** — the only module that ever does |

That split *is* §9's bridge, made structural: the boundary is a module
boundary, not a convention. `aerf-extraction` can be completed and
proven before OpenRewrite is ever added, which de-risks the effort.

**Enforce the boundary mechanically.** Add `maven-enforcer-plugin`
(3.6.3, verified available) with `bannedDependencies` on
`org.openrewrite:*` to `aerf-model`, `aerf-analysis`, `aerf-report` and
`aerf-extraction`, pinned in the parent's `pluginManagement` alongside
the existing three plugins. The project has no enforcer today; this is
its first load-bearing use — it turns §9's prose rule into a build
failure.

## Core design: two-phase assembly

`Graph.Builder` (`aerf-model/src/main/java/org/aerf/model/Graph.java`)
rejects an edge whose resolved endpoint has not been added yet, **eagerly
at `addEdge` time**:

```
"Edge references node id '...' that has not been added to the graph.
 Adapters must not create edges to nodes they have not evidenced
 (AERF v0.4 section 8); use NodeRef.unresolved(...) if the endpoint
 could not be identified."
```

A real extractor sees a call to `com.example.Foo` before it parses
`Foo.java`, so it **cannot** build a `Graph` in one pass. Hence a fact
layer, then a resolution pass.

**New types in `aerf-extraction` (package `org.aerf.extraction`):**

- `SymbolRef` — an opaque symbolic key plus a human description; what an
  extractor emits *before* anything is resolvable to a `NodeId`.
- `NodeFact` — a declaration observed: `(NodeId, NodeType, attributes,
  evidence)`.
- `EdgeFact` — a relation observed: `(SymbolRef source, SymbolRef
  target, RelationType, provenance)`.
- `ExtractionRequest` — source roots, optional classpath, adapter config.
- `ExtractionResult` — collected facts plus diagnostics.
- `SourceExtractor` — the interface an adapter implements.
- `GraphAssembler` — facts → `Graph`.
- `JavaNodeIds` — the Java id conventions below. Parser-agnostic, so it
  belongs here rather than in the OpenRewrite module.

**`GraphAssembler` rules, all testable without a parser:**

- **Resolution.** A `SymbolRef` whose key matches a declared `NodeFact`
  becomes `NodeRef.resolved(id)`; otherwise `NodeRef.unresolved(description)`
  carrying *why* it could not be resolved. This is §8's conservatism made
  executable.
- **Duplicate merge policy.** `Graph.Builder.addNode` does
  `nodes.put(id, node)` — a second declaration **silently discards the
  first node's evidence**. The assembler must own de-duplication: merge
  attributes and concatenate evidence deterministically rather than
  letting the builder overwrite.
- **Unresolved-source edges are kept, not dropped.** They can never be
  found by `edgesFrom`/`edgesTo` and no metric counts them — but
  discarding them would silently inflate `AnalysisConfidence` (§5.4),
  which measures precisely "relations we saw but could not resolve."
  Keeping them is what makes confidence honest.
- **Determinism regardless of filesystem order.** Sort nodes by id and
  edges by a canonical key *before* building, so two runs over the same
  sources produce byte-identical JSON even when the OS walks files in a
  different order. Note also that a multi-entry `Map.of(...)` has
  unspecified iteration order — the assembler must hand `Node.of` an
  order-stable map, or it silently defeats the guarantee `Node`
  documents.

## Java node-id conventions (`JavaNodeIds`)

§2.2 deliberately leaves id derivation to the adapter, so this is a
documented implementation decision:

- **MODULE** — Maven coordinates, `groupId:artifactId`.
- **COMPONENT** — fully-qualified type name, `com.example.OrderService`.
  Nested types use source form `Outer.Inner`, not `Outer$Inner`.
- **FUNCTION** — `com.example.OrderService#find(java.lang.Long)`, with
  **erased** parameter types so overloads are distinct and stable.
- Anonymous and local classes need an explicit rule: ordinal-based ids
  are position-dependent and shift under unrelated edits. Decide in
  Increment 13, document it, and prefer *not emitting a node* over
  emitting an unstable id.

## Increments

Each increment = code + tests + `docs/increment-NN-*.md` + one commit.

**Increment 11 — `Evidence` structured attributes.** Model change only.
Add an order-stable attribute map to
`aerf-model/src/main/java/org/aerf/model/Evidence.java`. It already has
four static factories; a sixth field would mean eight — so **introduce a
builder** rather than more overloads, keeping the existing factories
working. All 65 current `Evidence.of(...)` call sites are in **test**
sources (verified: no production code constructs `Evidence`), so this is
purely additive. Update `DefaultSeedRules` and `DefaultSecurityRules` to
match structured keys, keeping prose matching as a fallback. Record as
**Amendment 5** in `aerf-v0.4.1-patch.md`.

**Increment 12 — `aerf-extraction`: the bridge.** All types above, plus
the enforcer boundary rules. **No OpenRewrite.** Tests use a
hand-written `FakeExtractor` emitting canned facts: resolution,
unresolved fallback, duplicate merge, unresolved-source retention, and a
determinism test that shuffles fact order and asserts an identical
graph. Strongest acceptance test: reproduce
`CanonicalSampleGraphs.layeredOrderSlice()`'s exact shape from facts.

**Step 0 spike, before Increment 13 — done, throwaway, not committed.**
Ran against real OpenRewrite 8.90.4 (`javap` against the resolved jars,
then a small standalone program actually parsing sample sources) rather
than trusting method signatures alone. Findings that change Increment
13's design from what was originally assumed:

- **`org.slf4j:slf4j-nop` (or an equivalent binding) is a required
  runtime dependency**, not just a transitive nicety. Without one on the
  classpath, OpenRewrite's internal javac invocation throws
  `NoClassDefFoundError` while trying to log a diagnostic; OpenRewrite
  catches this itself and continues, but stderr fills with unrelated
  stack traces that would be easy to mistake for a real parsing failure.
  Must be added to `aerf-openrewrite`'s pom.
- **Resolution quality is a function of the parse *batch*, not only
  whether a classpath was supplied.** Parsing several related source
  files together in one `JavaParser.parse(...)` call resolves
  cross-references between them — `extends`, `implements`, a method
  call's declaring type and parameter types — with **zero external
  classpath**, and JDK types (`java.lang.Long`, `java.io.Serializable`)
  resolve automatically via the bootstrap classpath regardless. An
  explicit classpath is only needed for types belonging to genuine
  external dependencies (real Spring classes, say) that are not part of
  the source set being parsed. This means Increment 13's "with/without
  classpath" test matrix needs a third real case in between — parsing a
  project's own multi-file source set together, no external classpath —
  since that case already gets L2-quality resolution for in-project
  references, distinct from both the fully-isolated single-file case and
  the has-a-real-classpath case.
- **Unresolved type representation differs by tree position — both
  checks are needed, not just one:** `J.ClassDeclaration.getExtends().getType()`
  for an unresolvable supertype returns the sentinel
  `JavaType.Unknown.getInstance()`; `J.MethodInvocation.getMethodType()`
  for an unresolvable call returns `null` instead. A fidelity-detection
  implementation checking only for `null` would silently miss the
  `Unknown`-sentinel case on type-tree positions (extends/implements/field
  types) — confirmed by parsing a class extending a type declared nowhere
  in the batch.
- `JavaIsoVisitor`'s `visitClassDeclaration` / `visitMethodInvocation` /
  `visitForEachLoop` (and by extension `visitForLoop` /
  `visitWhileLoop` / `visitDoWhileLoop`, present in the same visitor)
  behave exactly as assumed — confirmed by an actual traversal, not just
  by the method signatures `javap` shows.
- The `org.openrewrite.ExecutionContext` / `org.aerf.model.ExecutionContext`
  name clash is real and was hit directly: the spike needed
  `org.openrewrite.ExecutionContext` for the parser call. Increment 13's
  code will need one of the two fully qualified wherever both are used
  in the same file, exactly as anticipated above.

**Increment 13 — `aerf-openrewrite`, class level.** Parse a source set;
emit `COMPONENT` nodes for types and `EXTENDS`/`IMPLEMENTS`/`DEPENDS`
edges. Record `L2_SYMBOL_RESOLVED` when OpenRewrite resolved a type
(checking both the `null` and `JavaType.Unknown` cases above) and
`L1_SYNTAX` when it did not. Test all three cases the spike identified:
a fully isolated single file, a project's own multi-file source set
parsed together with no external classpath, and a source set plus a
real external classpath.
*Sample project placement:* its `.java` files go under
`src/test/resources` (the repo's first `resources` directory), **not**
`src/test/java` — deliberately legacy-flavoured, defect-carrying sample
code must be parsed as data, never compiled by the build.
*Name clash to handle:* `org.openrewrite.ExecutionContext` (the parser
context) collides by simple name with `org.aerf.model.ExecutionContext`;
one must be fully qualified throughout this module.

**Increment 14 — method level and execution context.** `FUNCTION` nodes,
`CALL` edges, and loop-nesting detection setting
`ExecutionContext.ITERATED`. This makes the N+1 heuristic from Increment
6 run against real code for the first time.

**Increment 15 — Spring and annotation semantic evidence.** Emit
structured annotation attributes so role inference works on real source,
replacing Increment 2's invented test strings with adapter-produced
evidence. This is where open question #1 finally gets real data.

**Increment 16 — pipeline runner and CI.** An end-to-end entry point
(extraction → role inference → metrics → invariants → calibration →
JSON), plus a GitHub Actions workflow. The repo has **no CI at all**
today, so this adds the first: build and test on push, plus a separate
optional job running the analysis against a real cloned repository.

## Risks

1. **OpenRewrite API uncertainty** — highest risk. Mitigated by the Step
   0 spike and by pinning exactly 8.90.4. The LST API is stable within
   8.x but not across majors.
2. **No type attribution means almost everything is unresolved.** This
   is *correct* AERF behaviour, not a bug, and demonstrates §3.5 and
   §5.4 — but tests must cover both modes so it is never mistaken for
   breakage.
3. **Recursive Tarjan (open question #7) becomes real.**
   `StronglyConnectedComponents` will overflow the stack on deep real
   graphs. Treat an iterative rewrite as a gate before Increment 16's
   real-repository run.
4. **Memory and time on large codebases** — OpenRewrite parsing is
   heavy. Keep the synthetic sample as the correctness oracle; treat
   real-repository runs as observation, not assertion.
5. **Maven Central rate-limiting** (HTTP 429 observed through this
   environment's proxy) — first dependency resolution may need retries.
6. **Scope creep** — Java only; §11 defers the rest.

## Verification

- `mvn -B test` at the repo root stays green at every increment; the
  test count rises from the current 156.
- The boundary is verified by build failure: adding an
  `org.openrewrite` dependency to `aerf-model` must fail the enforcer,
  not merely be discouraged by convention.
- Determinism: extract the sample twice and assert byte-identical JSON
  through the existing `aerf-report` writers.
- End-to-end (Increment 16): run the pipeline over the synthetic sample
  and confirm it finds the defects deliberately planted there — a direct
  Presentation→Persistence layering violation, an N+1 loop, and an
  inheritance chain requiring multi-pass role refinement.
