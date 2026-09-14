package org.aerf.pipeline;

import org.aerf.analysis.calibration.CalibrationProfile;
import org.aerf.analysis.calibration.LinearCalibration;
import org.aerf.analysis.calibration.WeightedDimension;
import org.aerf.analysis.detection.DetectionCatalog;
import org.aerf.analysis.governance.GovernancePolicy;
import org.aerf.analysis.metrics.layer.LayerPolicy;
import org.aerf.extraction.ExtractionRequest;
import org.aerf.model.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Increment 25 (OQ-02). Nothing asserted this record's contract before —
 * neither its null checks nor its source-root guard had a test.
 */
class PipelineConfigTest {

    @Test
    void everyComponentIsRequiredAndNothingIsDefaultedWhenOmitted() {
        assertThrows(NullPointerException.class, () -> new PipelineConfig(null, detection(), governance()));
        assertThrows(NullPointerException.class, () -> new PipelineConfig(extraction(), null, governance()));
        assertThrows(NullPointerException.class, () -> new PipelineConfig(extraction(), detection(), null));
    }

    @Test
    void anEmptySourceRootListIsRejectedByExtractionRequestRatherThanReCheckedHere() {
        // PipelineConfig used to carry its own duplicate of this guard.
        // Reusing ExtractionRequest means there is one definition of "a
        // scan needs somewhere to look", not two that could drift.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new ExtractionRequest(List.of(), List.of()));

        assertEquals("sourceRoots must not be empty", thrown.getMessage());
    }

    @Test
    void governanceIsOneNamedValueRatherThanFourComponentsScatteredAmongEngineeringInputs() {
        // OQ-02's "governance input is represented explicitly" and "its
        // boundary from source-derived engineering evidence is clear",
        // made executable: three components, each naming one authorship
        // class. Before Increment 25 this record had nine, with the
        // organization's declarations interleaved among the rest.
        List<String> components = java.util.Arrays.stream(PipelineConfig.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertEquals(List.of("extraction", "detection", "governance"), components);
    }

    @Test
    void theThreeComponentsHaveDistinctTypesSoNoTwoCanBeSwappedPositionally() {
        // The old nine-component form had sourceRoots and classpath
        // adjacent and both List<Path>, so a caller could transpose them
        // silently. Distinct types make that unrepresentable.
        List<Class<?>> types = java.util.Arrays.stream(PipelineConfig.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();

        assertEquals(types.size(), Set.copyOf(types).size(), types.toString());
    }

    private static ExtractionRequest extraction() {
        return new ExtractionRequest(List.of(Path.of("src")), List.of());
    }

    private static DetectionCatalog detection() {
        return new DetectionCatalog(List.of(), List.of(), List.of());
    }

    private static GovernancePolicy governance() {
        return GovernancePolicy.withOneLayerMatrix(
                LayerPolicy.of(Set.of(Role.PRESENTATION), Map.of()),
                false,
                CalibrationProfile.of(List.of(new WeightedDimension("layer", 1.0, new LinearCalibration()))),
                List.of());
    }
}
