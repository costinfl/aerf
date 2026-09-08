# Increment 12 — The Extraction Bridge

## Objective

Second step of the ExtractionAdapter Plan: build AERF v0.4 §9's
"extraction bridge" — the technology-agnostic types a real adapter will
speak, plus the two-phase resolution logic `Graph.Builder` itself cannot
provide. **No OpenRewrite dependency anywhere in this increment**, and
the boundary that keeps it that way is now enforced by the build, not
just by convention.

## Scope

New module `aerf-extraction`, depending only on `aerf-model` (plus its
test-jar, for one acceptance test):

- `SymbolRef` — a symbolic reference an extractor emits before it can be
  checked against declared nodes.
- `NodeFact` / `EdgeFact` — declarations and relations observed, over
  `SymbolRef`s rather than resolved `NodeRef`s.
- `ExtractionRequest` / `ExtractionResult` / `SourceExtractor` — the
  contract a concrete adapter (e.g. a future `aerf-openrewrite`) will
  implement.
- `GraphAssembler` — resolves and merges facts into a `Graph`.
- `JavaNodeIds` — the Java `NodeId` conventions (module, type, method)
  the plan calls for, kept parser-agnostic so both the OpenRewrite
  adapter's declaring side and referencing side agree on the same string
  without depending on OpenRewrite itself.
- `maven-enforcer-plugin` with `bannedDependencies` on `org.openrewrite:*`,
  added to all four non-OpenRewrite modules (`aerf-model`,
  `aerf-analysis`, `aerf-report`, `aerf-extraction`).
- 23 new tests, including one that reproduces
  `CanonicalSampleGraphs.layeredOrderSlice()`'s exact node/edge shape
  from hand-built facts, and one that shuffles fact input order and
  asserts byte-for-byte identical assembler output.

## Why a fact layer at all

`Graph.Builder.addEdge` rejects a resolved endpoint that hasn't been
added yet, **eagerly**, with a message that says exactly this: *"Adapters
must not create edges to nodes they have not evidenced... use
NodeRef.unresolved(...) if the endpoint could not be identified"* (§8). A
real extractor sees a call to `com.example.Foo` before it parses
`Foo.java` — it cannot satisfy that ordering constraint in one streaming
pass. `NodeFact`/`EdgeFact`/`GraphAssembler` exist specifically to let an
extractor emit facts in whatever order it discovers them, deferring
resolution until every fact is in hand.

## The three hazards the plan flagged, and how each was closed

1. **Duplicate node facts.** `Graph.Builder.addNode` silently overwrites
   on a repeated id — a second declaration would discard the first
   node's evidence entirely. `GraphAssembler` instead merges: matching
   `NodeType` required (a mismatch throws — an extractor reporting the
   same id as two different types is a bug, not something to paper
   over), attributes unioned (a same-key-different-value conflict also
   throws, for the same reason), evidence concatenated in full.
2. **Unresolved-source edges.** Nothing filters them out. Both an edge's
   source and target resolve independently, so an edge with an
   unresolved source and a resolved target is represented exactly as
   such — dropping it would silently inflate `AnalysisConfidence` (§5.4),
   which exists precisely to measure "relations observed but not
   resolved."
3. **Determinism regardless of input order.** `GraphAssembler`'s output
   depends only on the *set* of facts supplied, never their order: nodes
   are emitted sorted by id, each node's merged evidence re-sorted by a
   total ordering over every `Evidence` field, and edges sorted by a
   canonical key over their resolved endpoints, relation, and provenance.
   `resultIsIndependentOfInputFactOrder` shuffles both fact lists with
   two different seeds and asserts identical output.

## Implementation decisions not dictated by v0.4

1. **Attribute/type conflicts throw rather than silently resolve.** A
   "last write wins" merge policy would have made the result depend on
   input order — exactly the property being eliminated — so any
   genuine disagreement between two facts about the same node is treated
   as an extractor bug to surface, not a tie to break quietly.
2. **`JavaNodeIds.type(...)` normalizes JVM binary names
   (`Outer$Inner` → `Outer.Inner`) uniformly, including anonymous
   classes (`Outer$1` → `Outer.1`).** Whether an adapter should emit a
   node for an anonymous/local class at all is left to the adapter (the
   plan's own preference — "not emitting a node over emitting an
   unstable id" — is a policy call, not a string-format one); this class
   only fixes the *format* once an adapter decides to emit one.
3. **The enforcer rule is additive per-module, not centrally
   configured.** `pluginManagement` pins only the plugin version; each
   module declares its own `<execution>`. This means a future
   `aerf-openrewrite` module simply doesn't declare the rule, rather than
   needing an override to opt out of an inherited default — the ban is
   opt-in per module, matching exactly the four modules the plan says
   must never carry OpenRewrite.

## Evidence — what this increment proves or exposes about the AERF model

- **The enforcer rule was verified to actually fail the build**, not
  just pass trivially because no module has an OpenRewrite dependency
  yet: a throwaway `org.openrewrite:rewrite-java` dependency was
  injected into `aerf-model`'s pom, `mvn validate` was run and confirmed
  to fail with the dependency (and its transitives) named in the error,
  and the injection was then reverted. §9's rule is now something a
  contributor discovers from a build failure, not something they have to
  already know to respect.
- `reproducesTheCanonicalSampleGraphsShapeFromFacts` needed adjusting
  mid-implementation: `GraphAssembler`'s canonical sort order and
  `CanonicalSampleGraphs`'s hand-authored insertion order are both
  internally deterministic but disagree with each other, so the
  comparison had to become order-independent (sorted node snapshots, a
  `Set`-based edge comparison) rather than exact-list-equality. That is
  itself a small, concrete illustration of why "deterministic" needs a
  precise definition: two equally deterministic processes over the same
  input can still produce different — merely *each individually
  reproducible* — orderings.

## Open questions for the architecture

No new entries in `open-questions-register.md`. The canonical-sort
determinism guarantee this increment establishes is exactly the
property needed before any real adapter is built; nothing here reopens
or narrows a prior open question.

## Scope check

No OpenRewrite dependency, no concrete Java parsing, no real
`SourceExtractor` implementation, no sample project, and no change to
any type in `aerf-model`, `aerf-analysis`, or `aerf-report`. This
increment is exactly the bridge the plan calls for before Increment 13's
OpenRewrite-backed adapter — buildable, tested, and (now verifiably)
isolated from the technology it will eventually front.
