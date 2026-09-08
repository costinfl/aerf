package org.aerf.extraction;

/**
 * A technology-specific extraction adapter (AERF v0.4 section 9's
 * "extraction bridge"). Implementations depend on this module, never the
 * reverse — this interface, {@link ExtractionRequest}, and
 * {@link ExtractionResult} are the entire surface a concrete adapter
 * (e.g. an OpenRewrite-backed one) needs to speak, so the canonical
 * model never has to know a concrete parsing technology exists.
 */
public interface SourceExtractor {

    ExtractionResult extract(ExtractionRequest request);
}
