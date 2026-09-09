# Increment 17 — Iterative `StronglyConnectedComponents`

## Objective

Close open question #7: rewrite `StronglyConnectedComponents`'s Tarjan
implementation from recursive to iterative, so it cannot stack-overflow
on a deep (but perfectly ordinary, non-cyclic) dependency chain. Pure
implementation debt, not a spec question — flagged as a gate since
Increment 5, and load-bearing for real since Increment 16's own Risks
section named it as the reason the ExtractionAdapter Plan's
real-cloned-repository CI job was deliberately not added.

## Scope

- `StronglyConnectedComponents.strongConnect` rewritten to use an
  explicit work stack of `Frame` objects (node + how many of its
  adjacency-list children have been examined so far) instead of the JVM
  call stack. `find`'s public behavior, `buildAdjacency`, and every other
  method are unchanged.
- 2 new tests: a 200,000-node linear chain (proving no stack overflow),
  and a 200,000-node chain feeding into a 2-node cycle at its far end
  (proving the rewrite is still *correct* at depth, not merely
  non-crashing — the cycle must still be found and `lowlink` must still
  propagate correctly across 200,000 frames' worth of unwinding).
- `docs/open-questions-register.md`: #7 moved to Resolved.
- `.github/workflows/ci.yml`'s comment updated: the gate this increment
  existed to close is now closed, but the real-cloned-repository CI job
  itself remains a separate, not-yet-requested increment — closing the
  gate is not the same as deciding to walk through it.

**Explicitly out of scope:** actually adding the real-cloned-repository
CI job (see the workflow comment above); any change to `CycleEntropyCalculator`,
`CycleEntropyResult`, or any other caller of `StronglyConnectedComponents`
— none needed, since the rewrite's whole point is that callers cannot
tell the difference.

## Why a mechanical translation, not a different algorithm

Tarjan's algorithm is usually rewritten iteratively by simulating the
call stack directly: one frame per in-progress recursive call, each
frame remembering exactly where in its node's adjacency list it had
gotten to. That is what `Frame` does here (`nextChildIndex`), and the
work-stack loop performs, in order, exactly what `strongConnect(v)`
would have done at the same point recursively:

1. **First visit** (`!frame.initialized`): assign `index`/`lowlink`,
   push onto the Tarjan stack, mark `onStack` — identical to the first
   four lines of the old recursive method.
2. **One child examined per loop iteration**, in adjacency-list order:
   an unvisited child becomes a new frame pushed on top (the iterative
   analogue of "recurse"); an already-on-stack child updates `lowlink`
   directly (the iterative analogue of the recursive method's `else if`
   branch) — both exactly where the recursive version made the same
   decision, at the same point in the same loop.
3. **Finish, only once every child has been examined**
   (`nextChildIndex == children.size()`): pop the frame, extract an SCC
   if `v` is its own root (byte-for-byte the same `do`/`while` loop the
   recursive version used), then — if a parent frame remains — propagate
   `v`'s final `lowlink` to it. This last step is the line a recursive
   caller runs immediately after `strongConnect(w)` returns; here it
   runs immediately after popping `w`'s frame, with the parent frame
   already back on top of the work stack.

Framed this way, the rewrite has no independent logic to get wrong: it
is the same fifteen or so lines of Tarjan, restated so that "call
strongConnect(w) and wait for it to return" becomes "push a frame for w
and let the loop reach it eventually," which is what makes it provably
equivalent rather than merely similarly-shaped.

## Evidence — what this increment proves

- **The claim actually justifying this increment was itself verified,
  not assumed.** Before writing the "this would stack-overflow" test
  comment, a throwaway spike (structurally identical recursion — same
  per-node map lookups, same loop shape) was run at depths 5,000 (completes),
  10,000 and 20,000 (`StackOverflowError`) against the *old* recursive
  algorithm, confirming the default JVM stack's real limit falls
  somewhere in that range before ever writing a test asserting 200,000
  is "safely beyond it."
- **Every pre-existing `StronglyConnectedComponentsTest` and
  `CycleEntropyCalculatorTest` passed unchanged** against the rewrite —
  the two-node-cycle, linear-chain, feeds-in-but-not-part-of-cycle,
  irrelevant-relation, and unresolved-endpoint cases all produce
  byte-identical results to the recursive version, exactly as "a
  mechanical translation, not a different algorithm" predicts.
- **The new 200,000-node tests both pass**, completing in low single-digit
  seconds: the chain-only case confirms no overflow and the correct
  all-trivial-singletons shape; the chain-plus-cycle case confirms the
  2-node cycle at the far end is still found as the sole non-trivial SCC,
  and that the total SCC count (`chainLength + 1`) is exactly right —
  proof the rewrite is correct at depth, not just survives it.

## Open questions for the architecture

Closes #7 (moved to Resolved in `open-questions-register.md`). No new
entries.

## Scope check

One class rewritten, its two existing tests unchanged, two new tests,
one open question closed, one stale CI comment corrected. No change to
any caller, any other module, or any other analysis behavior. `mvn -B
test` at the repo root passes with 211 tests total (up from 209 after
Increment 16), across all seven modules.
