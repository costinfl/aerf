package org.aerf.model;

/**
 * Extraction fidelity level, AERF v0.4 section 8.1. Recorded per
 * {@link Evidence} item so downstream consumers can judge how much to
 * trust a given fact.
 */
public enum ExtractionFidelity {
    L1_SYNTAX,
    L2_SYMBOL_RESOLVED,
    L3_DATA_FLOW_AWARE
}
