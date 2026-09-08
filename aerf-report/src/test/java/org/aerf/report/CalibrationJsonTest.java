package org.aerf.report;

import org.aerf.analysis.calibration.MaturityLevel;
import org.aerf.report.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalibrationJsonTest {

    @Test
    void aDefinedTotalEntropySerializesAsANumber() {
        assertEquals("0.5", JsonWriter.write(CalibrationJson.totalEntropy(OptionalDouble.of(0.5))));
    }

    @Test
    void anUndefinedTotalEntropySerializesAsNullNotZero() {
        assertEquals("null", JsonWriter.write(CalibrationJson.totalEntropy(OptionalDouble.empty())));
    }

    @Test
    void maturityLevelSerializesAsItsName() {
        assertEquals("\"L1_REACTIVE\"", JsonWriter.write(CalibrationJson.maturityLevel(MaturityLevel.L1_REACTIVE)));
    }
}
