package org.aerf.report;

import org.aerf.analysis.calibration.InvariantAggregate;
import org.aerf.analysis.calibration.InvariantContribution;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Increment 29 (OQ-15): E_inv serialized beside the terms that produced it. */
class InvariantAggregateJsonTest {

    @Test
    void anUndeclaredAggregateSerializesAsNullNotZero() {
        // Declaring no importances leaves E_inv undefined. Coercing that
        // to 0.0 would read as "no weighted violations", which is a claim
        // nothing supports.
        assertEquals("{\"value\":null,\"contributions\":[],\"unevaluatedWeightedInvariants\":[]}",
                JsonWriter.write(InvariantAggregateJson.aggregate(InvariantAggregate.undeclared())));
    }

    @Test
    void everyTermIsSerializedBesideTheTotal() {
        String json = JsonWriter.write(InvariantAggregateJson.aggregate(new InvariantAggregate(
                OptionalDouble.of(2.0),
                List.of(new InvariantContribution("no_presentation_to_persistence", 2.0, 1, 2.0),
                        new InvariantContribution("entropy_budget", 5.0, 0, 0.0)),
                List.of())));

        assertEquals("{\"value\":2.0,\"contributions\":["
                        + "{\"invariantName\":\"no_presentation_to_persistence\",\"weight\":2.0,"
                        + "\"indicatorValue\":1.0,\"contribution\":2.0},"
                        + "{\"invariantName\":\"entropy_budget\",\"weight\":5.0,"
                        + "\"indicatorValue\":0.0,\"contribution\":0.0}],"
                        + "\"unevaluatedWeightedInvariants\":[]}",
                json);
    }

    @Test
    void anUndefinedTotalCarriesTheNamesThatMadeItUndefined() {
        String json = JsonWriter.write(InvariantAggregateJson.aggregate(new InvariantAggregate(
                OptionalDouble.empty(), List.of(), List.of("entropy_budget"))));

        assertTrue(json.contains("\"value\":null"), json);
        assertTrue(json.contains("\"unevaluatedWeightedInvariants\":[\"entropy_budget\"]"), json);
    }

    @Test
    void theSerializedTotalEqualsTheSumOfTheSerializedTerms() {
        InvariantAggregate aggregate = new InvariantAggregate(
                OptionalDouble.of(3.5),
                List.of(new InvariantContribution("a", 2.0, 1, 2.0),
                        new InvariantContribution("b", 1.5, 1, 1.5)),
                List.of());

        assertEquals(aggregate.value().getAsDouble(),
                aggregate.contributions().stream().mapToDouble(InvariantContribution::contribution).sum());
        assertTrue(JsonWriter.write(InvariantAggregateJson.aggregate(aggregate)).contains("\"value\":3.5"));
    }
}
