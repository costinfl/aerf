package org.aerf.model;

/**
 * Whether a piece of {@link Evidence} was observed to occur inside a
 * repeated/iteration execution context (a loop, a stream's per-element
 * callback, and similar), as opposed to a single execution.
 *
 * <p>Not part of AERF v0.4's frozen canonical model (section 2): the
 * specification's N+1 heuristic (section 4.3) requires knowing whether a
 * persistence operation happens "within iteration or repeated execution
 * contexts," but neither the canonical node model (section 2.2) nor the
 * canonical edge model (section 2.4) represents iteration at all. This
 * type is the smallest addition that lets that fact be recorded as
 * ordinary evidence, without introducing a new graph node or edge
 * concept for control flow.
 *
 * <p>{@link #UNKNOWN} is the default for evidence that says nothing about
 * execution context (the overwhelming majority of evidence recorded
 * before this heuristic existed) — it must never be conflated with
 * {@link #SINGLE}, which is a positive claim that single execution was
 * observed.
 */
public enum ExecutionContext {
    SINGLE,
    ITERATED,
    UNKNOWN
}
