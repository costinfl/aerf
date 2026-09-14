package org.aerf.pipeline;

import org.aerf.analysis.detection.DetectionCatalog;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.extraction.ExtractionRequest;

import java.util.Objects;

/**
 * Everything one {@link Pipeline#run(PipelineConfig)} call needs, in
 * three parts that differ by <em>who authors them</em> (Increment 25,
 * OQ-02):
 *
 * <ul>
 *   <li>{@link #extraction()} — engineering input: what to look at, and
 *       with how much type information. Supplied by whoever runs the scan.
 *   <li>{@link #detection()} — technology knowledge: how a framework's
 *       conventions are recognized. Authored by whoever maintains the
 *       adapter or knows the stack.
 *   <li>{@link #governance()} — the organization's own declaration of
 *       what it will tolerate. Authored by governance.
 * </ul>
 *
 * <p>Until Increment 25 these nine values were one flat list, so the
 * boundary between an organization's declarations and source-derived
 * engineering evidence existed only in prose. It is now in the type
 * system, which is what OQ-02's "governance input is represented
 * explicitly" and "its boundary from source-derived engineering evidence
 * is clear" ask for.
 *
 * <p>There is deliberately no default for any of the three, and none
 * inside {@link GovernancePolicy} either — see its documentation on why
 * a governance choice must never be made silently on an organization's
 * behalf. {@link ExtractionRequest} is reused rather than reinvented: it
 * already carries exactly {@code (sourceRoots, classpath)} and already
 * enforces a non-empty source root, so that check is not duplicated here.
 *
 * <p>A fourth class of input exists but is not represented here at all:
 * <em>measurement definition</em> — which relations each dimension
 * measures over, the four dimension names, {@code MEMBER_OF}'s exclusion
 * from confidence. Those are fixed by AERF v0.4 section 4 rather than
 * declared by anyone, and {@link Pipeline} composes them directly. See
 * {@code docs/increment-25-*.md}.
 */
public record PipelineConfig(
        ExtractionRequest extraction,
        DetectionCatalog detection,
        GovernancePolicy governance) {

    public PipelineConfig {
        Objects.requireNonNull(extraction, "extraction");
        Objects.requireNonNull(detection, "detection");
        Objects.requireNonNull(governance, "governance");
    }
}
