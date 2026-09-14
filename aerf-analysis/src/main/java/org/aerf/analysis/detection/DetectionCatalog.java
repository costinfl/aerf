package org.aerf.analysis.detection;

import org.aerf.analysis.metrics.security.SecurityOpportunityRule;
import org.aerf.analysis.role.GraphRoleRefinementRule;
import org.aerf.analysis.role.RoleInferenceRule;

import java.util.List;
import java.util.Objects;

/**
 * The technology knowledge a run is equipped with: how a framework's
 * conventions are recognized in extracted evidence (Increment 25, OQ-02).
 *
 * <p>This is the counterpart to {@code GovernancePolicy} and the
 * distinction between them is <em>authorship</em>, not mechanism. A
 * detection rule is written by whoever knows the technology — that a
 * Spring {@code @Controller} indicates a presentation role, that
 * extending {@code JpaRepository} indicates persistence. A governance
 * declaration is written by the organization and says what it will
 * tolerate. The project's own vocabulary already marks these three as
 * the former: every one of {@code DefaultSeedRules}, {@code
 * DefaultGraphRefinementRules} and {@code DefaultSecurityRules} calls
 * itself an "illustrative catalog... an implementation decision, not
 * part of AERF v0.4".
 *
 * <p><b>Order matters for one of the three and not the others</b>, which
 * is worth stating because it is not obvious. Seed and refinement rule
 * order is insignificant: {@code SeedRoleInferenceEngine} sorts every
 * firing rule's signal by rule name and resolves the winner purely by
 * {@code RolePrecedence}, so "the order the rule list was constructed in
 * therefore has no effect on the result". Security rule order <em>is</em>
 * significant: {@code SecurityEntropyCalculator} iterates nodes against
 * rules in list order and appends each finding, and that order reaches
 * the serialized report. All three are therefore stored order-preserving
 * — required for the third, harmless for the first two.
 *
 * <p>A known limitation, recorded rather than fixed here: a security rule
 * decides both <em>which code pattern signals a concern</em> (technology
 * knowledge, and rightly catalog-side) and <em>which concern is worth
 * reporting</em> (arguably an organizational choice). Separating them
 * would filter the security opportunity denominator, making it a policy
 * input subject to the full measurement-change checklist. See
 * {@code docs/increment-25-*.md}.
 */
public record DetectionCatalog(
        List<RoleInferenceRule> seedRules,
        List<GraphRoleRefinementRule> refinementRules,
        List<SecurityOpportunityRule> securityRules) {

    public DetectionCatalog {
        seedRules = List.copyOf(Objects.requireNonNull(seedRules, "seedRules"));
        refinementRules = List.copyOf(Objects.requireNonNull(refinementRules, "refinementRules"));
        securityRules = List.copyOf(Objects.requireNonNull(securityRules, "securityRules"));
    }
}
