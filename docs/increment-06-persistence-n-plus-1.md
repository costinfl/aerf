# Increment 6 — Basic Persistence / N+1 Heuristic

## Objective

Implement the third MVP entropy metric: the basic persistence/N+1
heuristic from AERF v0.4 §4.3 — "repeated persistence operations within
iteration or repeated execution contexts where access is not batched or
otherwise justified."

## A model gap found before any metric code, and how it was resolved

Unlike layer entropy (Increment 3) and cycle entropy (Increment 5), this
metric could not be built on the canonical model as it stood after
Increment 1. §4.3's definition depends on knowing whether a persistence
access happens **inside a loop or repeated execution context** — but
neither the canonical node model (§2.2) nor the canonical edge model
(§2.4) represents iteration, loops, or control flow in any form. This
isn't an implementation-detail ambiguity like Increment 3's layering
matrix; it's a concept §4.3 assumes exists that §2's frozen model simply
doesn't have a place for.

Per the agent instructions ("stop at the relevant boundary... propose the
smallest possible resolution; wait for architectural approval before
changing the conceptual model"), this was raised explicitly rather than
silently resolved, since fixing it means touching `aerf-model`'s already
shipped `Evidence` type, not just adding a calculator in `aerf-analysis`.
Three options were presented:

1. Extend `Evidence` with a small typed execution-context field.
2. Introduce a new first-class graph concept for iteration/loops.
3. Leave the model untouched and encode iteration as a convention in
   `Evidence`'s free-text description.

**Option 1 was chosen** (by explicit instruction). A new enum,
`ExecutionContext { SINGLE, ITERATED, UNKNOWN }`, was added to
`aerf-model`, and `Evidence` gained an `executionContext()` field
defaulting to `UNKNOWN` — chosen over defaulting to `SINGLE` because
`UNKNOWN` accurately represents "this adapter made no claim either way,"
while `SINGLE` would be a false positive claim of single execution that
no existing evidence (from Increments 1–5) actually made. All prior
`Evidence.of(...)` call sites across every earlier increment therefore
continue to compile and behave identically — they just now carry an
explicit `UNKNOWN` where nothing was said before.

## Scope

- `aerf-model`: `ExecutionContext` (new), `Evidence` extended with the
  field, two new overloaded factories that accept it explicitly, and
  `equals`/`hashCode`/`toString` updated accordingly. 3 new tests in
  `EvidenceTest`.
- `aerf-analysis`, new package `org.aerf.analysis.metrics.persistence`:
  `PersistenceEntropyCalculator` and `PersistenceEntropyResult`,
  following the same shape as `LayerEntropyCalculator`/`Result` and
  `CycleEntropyCalculator`/`Result` from Increments 3 and 5.
- 10 new tests, including one against the Increment 1 fixture graph
  (confirming its existing `CALL` edges to the repository, none of which
  claim iteration, are correctly never flagged).

## What "relevant" and "flagged" mean here

- A **relevant persistence context** is an edge whose relation is `CALL`
  (the default, and the only unambiguous shape of "invoking a persistence
  operation" — a repository/DAO method call), with both endpoints
  resolved, and whose target node has `Role.PERSISTENCE`.
- A relevant edge is **flagged** if any of its provenance carries
  `ExecutionContext.ITERATED`.

## Implementation decisions not dictated by v0.4

1. **Default relevant relation is `CALL` only**, not `READS`/`WRITES` as
   well. Unlike layer and cycle entropy (which reused §6.3's `[CALL,
   DEPENDS]` as a documented anchor), §4.3 gives no anchor at all for
   this metric's relevant relations. `CALL` was chosen as the
   unambiguous case; `READS`/`WRITES` represent data access at a
   different, more structural level and were deliberately left out of
   the default (the constructor remains open for a caller to include
   them).
2. **"Not batched or otherwise justified" is not implemented.** §4.3's
   own wording carves out an exception for intentional, justified
   repetition. This increment does not attempt to detect batching or
   represent an approved exception — every iterated persistence context
   is flagged, full stop. Modeling "this specific repetition is an
   approved exception" belongs to the invariant/exception system (§6),
   which doesn't exist yet; building a one-off exception mechanism just
   for this metric would be exactly the kind of premature, metric-specific
   abstraction the project's philosophy warns against.
3. **`value()` is a plain count ratio** (`flagged / relevant`), matching
   §4.3's own formula shape and the pattern established by
   `LayerEntropyResult`/`CycleEntropyResult`. Appendix B separately
   labels this metric "Evidence-weighted N+1 patterns / relevant
   persistence contexts" — but no weighting formula is defined anywhere
   in v0.4. This increment left that gap open; it is since resolved by
   **AERF v0.4.1 patch Amendment 3** (`docs/aerf-v0.4.1-patch.md`), which
   defines the weighting concretely and adds
   `PersistenceEntropyResult.weightedValue()` alongside the plain
   `value()` — both stay separately visible rather than one silently
   replacing the other.

## Evidence — what this increment proves or exposes about the AERF model

- **§4.3 cannot be implemented on the frozen §2 model alone.** This is a
  concrete, load-bearing gap: it's not that the model is merely
  incomplete in some minor way, but that the MVP's own metric list (§11
  freezes "basic persistence/N+1 heuristic" as in-scope) requires a
  concept absent from the same document's canonical model. Extending
  `Evidence` was the minimal fix, but the fact a fix was structurally
  necessary — not just an implementation nicety — is itself a finding
  about v0.4 worth carrying forward.
- Running the calculator against the Increment 1 fixture confirms its
  existing `CALL` edges are correctly *not* flagged (none of that
  evidence was ever iteration-related) — a free regression check that
  the model extension didn't retroactively and incorrectly start
  flagging old data.
- The "Evidence-weighted" vs. "plain ratio" mismatch between §4.3's body
  text and Appendix B's summary table is a small but real internal
  inconsistency in v0.4, resolved by v0.4.1 patch Amendment 3 (see
  above) rather than left flagging the spec's owner indefinitely.

## Open questions for the architecture

*(The "what does evidence-weighted mean" question originally recorded
here is resolved — see Decision 3 above and
`docs/aerf-v0.4.1-patch.md` Amendment 3. Remaining open items are
tracked centrally in `docs/open-questions-register.md`.)*

- **How should "batched or otherwise justified" repetition eventually be
  represented?** Once an invariant/exception model exists (§6), does an
  approved exception suppress this metric's flag entirely, or does the
  metric stay a raw, unfiltered signal while exceptions are applied only
  at the invariant/governance layer downstream? This affects whether
  `PersistenceEntropyResult` needs an "excused" bucket in addition to
  "flagged"/"not flagged."
- **Is `Role.PERSISTENCE` the right target-side filter, or should the
  source side matter too?** This increment only checks the *target's*
  role. A more precise heuristic might also require the *source* to be
  something that plausibly iterates (e.g. not itself `Role.PERSISTENCE`)
  — not attempted here, to keep the heuristic "basic" as v0.4 asks.

## Scope check

No changes to role inference, layer entropy, or cycle entropy. No
security entropy, modal entropy, or frontend/JSP entropy. No invariant
DSL, no calibration/aggregation, no OpenRewrite integration, and no
attempt to detect batching or model governance exceptions. The one model
extension (`ExecutionContext` on `Evidence`) was surfaced and confirmed
before being made, per the agent instructions, rather than decided
unilaterally.
