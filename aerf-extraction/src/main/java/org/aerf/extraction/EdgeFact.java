package org.aerf.extraction;

import org.aerf.model.Evidence;
import org.aerf.model.RelationType;

import java.util.List;
import java.util.Objects;

/**
 * A relation an extractor observed between two (as yet unresolved)
 * symbolic references. {@link GraphAssembler} resolves both endpoints
 * independently — neither being resolvable is a reason to drop the fact,
 * only to record it as evidence of an unresolved relation (AERF v0.4
 * section 5.4's analysis confidence exists precisely to make that
 * visible, not to have it silently vanish here).
 */
public record EdgeFact(SymbolRef source, SymbolRef target, RelationType relation, List<Evidence> provenance) {

    public EdgeFact {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(relation, "relation");
        provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
    }
}
