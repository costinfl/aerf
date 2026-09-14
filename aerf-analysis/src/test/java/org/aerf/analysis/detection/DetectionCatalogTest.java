package org.aerf.analysis.detection;

import org.aerf.analysis.metrics.security.SecurityOpportunityRule;
import org.aerf.analysis.role.seed.DefaultSeedRules;
import org.aerf.analysis.role.RoleInferenceRule;
import org.aerf.analysis.role.SeedRoleInferenceEngine;
import org.aerf.analysis.role.graph.DefaultGraphRefinementRules;
import org.aerf.analysis.metrics.security.rules.DefaultSecurityRules;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Increment 25 (OQ-02): the engineering side of the authorship boundary.
 * See {@code docs/increment-25-governance-policy-boundary.md}.
 */
class DetectionCatalogTest {

    @Test
    void allThreeCatalogsAreRequiredButMayBeEmpty() {
        assertThrows(NullPointerException.class, () -> new DetectionCatalog(null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new DetectionCatalog(List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> new DetectionCatalog(List.of(), List.of(), null));

        DetectionCatalog empty = new DetectionCatalog(List.of(), List.of(), List.of());
        assertTrue(empty.seedRules().isEmpty());
        assertTrue(empty.refinementRules().isEmpty());
        assertTrue(empty.securityRules().isEmpty());
    }

    @Test
    void securityRuleOrderIsPreservedBecauseItDecidesOpportunityOrderInTheReport() {
        // SecurityEntropyCalculator iterates nodes against rules in list
        // order and appends each finding, and that order reaches
        // MetricsJson - so this list is output-significant, unlike the
        // other two.
        List<SecurityOpportunityRule> declared = List.copyOf(DefaultSecurityRules.illustrativeRules());
        DetectionCatalog catalog = new DetectionCatalog(List.of(), List.of(), declared);

        assertEquals(declared, catalog.securityRules());
    }

    @Test
    void seedRuleOrderIsIrrelevantToInferenceButIsStillPreservedVerbatim() {
        // SeedRoleInferenceEngine sorts every firing rule's signal by rule
        // name and resolves by RolePrecedence, so construction order has
        // no effect on the inferred role. The catalog still stores what it
        // was given rather than re-sorting: a catalog that quietly
        // reordered its caller's declaration would be deriving, and the
        // engine's independence is the engine's guarantee to make, not
        // this record's.
        List<RoleInferenceRule> forwards = List.copyOf(DefaultSeedRules.illustrativeRules());
        List<RoleInferenceRule> backwards = new ArrayList<>(forwards);
        java.util.Collections.reverse(backwards);

        assertEquals(backwards, new DetectionCatalog(backwards, List.of(), List.of()).seedRules());

        Node node = Node.of(NodeId.of("x"), NodeType.COMPONENT, Role.UNKNOWN, Map.of(), List.of());
        assertEquals(
                new SeedRoleInferenceEngine(forwards).inferSeedRole(node).role(),
                new SeedRoleInferenceEngine(backwards).inferSeedRole(node).role(),
                "whatever order the catalog carries, inference is unaffected by it");
    }

    @Test
    void theIllustrativeCatalogsStillFitTheShapeThisRecordDeclares() {
        DetectionCatalog catalog = new DetectionCatalog(
                DefaultSeedRules.illustrativeRules(),
                DefaultGraphRefinementRules.illustrativeRules(),
                DefaultSecurityRules.illustrativeRules());

        assertTrue(catalog.seedRules().size() > 0);
        assertTrue(catalog.refinementRules().size() > 0);
        assertTrue(catalog.securityRules().size() > 0);
    }

    @Test
    void aCatalogListCannotBeMutatedThroughItsAccessor() {
        DetectionCatalog catalog = new DetectionCatalog(List.of(), List.of(), List.of());

        assertThrows(UnsupportedOperationException.class, () -> catalog.securityRules().add(null));
    }
}
