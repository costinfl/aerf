package org.aerf.analysis.metrics.security;

import org.aerf.analysis.metrics.security.rules.DefaultSecurityRules;
import org.aerf.model.Evidence;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OQ-11 (post-v0.4.1 backlog): a security concern is a stable identifier
 * in a documented canonical form, not an enum and not free text. See
 * {@code docs/increment-23-security-concern-identifiers.md}.
 */
class SecurityConcernTest {

    @Test
    void xssIsTheCanonicalIdentifierAndItsWireValueIsUnchanged() {
        // The constant must not quietly redefine what already ships in
        // every report's JSON. MetricsJsonTest asserts the literal
        // "concern":"xss" independently; this pins the same value at the
        // source, so the two cannot drift apart silently.
        assertEquals("xss", SecurityConcern.XSS);
    }

    @Test
    void canonicalConcernsAreAcceptedUnchanged() {
        for (String concern : List.of("xss", "csrf", "mass-assignment", "open-redirect", "sql-injection", "a1")) {
            assertEquals(concern, SecurityConcern.requireCanonical(concern));
        }
    }

    @Test
    void nonCanonicalConcernsAreRejectedRatherThanNormalized() {
        // Rejecting rather than lowercasing is deliberate: silently
        // turning "Mass Assignment" into "mass-assignment" would invent an
        // identifier the rule author never chose.
        for (String concern : List.of("XSS", "Cross Site Scripting", "mass assignment", "xss ", "-xss", "xss-",
                "mass--assignment", "xss_injection", "")) {
            assertThrows(IllegalArgumentException.class, () -> SecurityConcern.requireCanonical(concern),
                    "expected \"" + concern + "\" to be rejected as non-canonical");
        }
    }

    @Test
    void aNullConcernIsRejected() {
        assertThrows(NullPointerException.class, () -> SecurityConcern.requireCanonical(null));
    }

    @Test
    void aRuleCannotEmitANonCanonicalConcern() {
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityOpportunityRule.Finding("Cross Site Scripting", true, "rationale"));
    }

    @Test
    void aSecurityFindingCannotCarryANonCanonicalConcernEither() {
        // Before increment 23 this record validated nothing beyond
        // non-null, so a blank or mixed-case concern could reach a report
        // through this path even though Finding rejected it.
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityFinding(NodeId.of("a.jsp"), "some-rule", "  ", true, "rationale"));
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityFinding(NodeId.of("a.jsp"), "some-rule", "XSS", true, "rationale"));
    }

    @Test
    void everyConcernEmittedByTheDefaultRulesIsCanonical() {
        // The catalog-wide guard: whatever rules ship by default, none of
        // them may emit a concern a consumer cannot rely on.
        Node unescaped = Node.of(NodeId.of("a.jsp"), NodeType.VIEW, Role.PRESENTATION, Map.of(),
                List.of(Evidence.of("jsp", "renders as unescaped output", ExtractionFidelity.L1_SYNTAX)));
        Node escaped = Node.of(NodeId.of("b.jsp"), NodeType.VIEW, Role.PRESENTATION, Map.of(),
                List.of(Evidence.of("jsp", "renders as escaped output", ExtractionFidelity.L1_SYNTAX)));

        for (SecurityOpportunityRule rule : DefaultSecurityRules.illustrativeRules()) {
            for (Node node : List.of(unescaped, escaped)) {
                rule.evaluate(node).ifPresent(finding ->
                        assertEquals(finding.concern(), SecurityConcern.requireCanonical(finding.concern())));
            }
        }
    }

    @Test
    void theInvariantDslStillCannotReferenceAConcern() {
        // OQ-11's stated motivation was "so invariants can reference
        // concerns reliably". That half is NOT delivered here and must not
        // be assumed: PropertyKey has no security-related constant, and
        // InvariantEvaluator never receives a SecurityEntropyResult. This
        // test fails the moment someone adds such a key, which is the
        // point - the remaining half of OQ-11 should be reopened
        // deliberately, not discovered by accident.
        boolean anySecurityKey = java.util.Arrays.stream(org.aerf.analysis.invariant.PropertyKey.values())
                .anyMatch(key -> key.name().contains("CONCERN") || key.name().contains("SECURITY")
                        || key.name().contains("FINDING"));

        assertTrue(!anySecurityKey,
                "a security-related PropertyKey now exists - OQ-11's DSL half is no longer deferred, "
                        + "so update docs/post-v0.4.1-backlog-status.md rather than deleting this test");
    }
}
