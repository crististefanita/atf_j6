package ro.atf.j6.release;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class PublishAchievementCalibrationExtentTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void acceptsUnqualifiedActiveObjectivesOnlyWithAnExplicitReason() {
        assertTrue(PublishAchievementCalibrationExtent.validVerdict(false, "NECALIFICAT",
                "Simulatorul nu expune provenienta manuala a titlului."));
        assertFalse(PublishAchievementCalibrationExtent.validVerdict(false, "NECALIFICAT", " "));
        assertFalse(PublishAchievementCalibrationExtent.validVerdict(false, "RETRAS", "motiv"));
    }

    @Test
    void keepsRetirementAndCalibrationVerdictsDisjoint() {
        assertTrue(PublishAchievementCalibrationExtent.validVerdict(true, "RETRAS", "Obiectiv retras."));
        assertFalse(PublishAchievementCalibrationExtent.validVerdict(true, "CONFIRMAT", "motiv"));
        assertFalse(PublishAchievementCalibrationExtent.validVerdict(true, "RAR", "motiv"));
        assertFalse(PublishAchievementCalibrationExtent.validVerdict(false, "UNKNOWN", "motiv"));
    }

    @Test
    void pendingSimulatorFactsStayUnmeasuredAndCarryTheirReason() {
        var pending = JSON.createObjectNode().put("evidence", "pending")
                .put("reason", "Simulatorul nu păstrează identitatea profilului între turnee.");
        assertTrue(PublishAchievementCalibrationExtent.validMeasurement(pending));
        assertEquals("not measured: Simulatorul nu păstrează identitatea profilului între turnee.",
                PublishAchievementCalibrationExtent.compact(pending));
        pending.put("reason", " ");
        assertFalse(PublishAchievementCalibrationExtent.validMeasurement(pending));
    }

    @Test
    void rejectsUnknownMeasurementProvenanceInsteadOfTreatingItAsZero() {
        var unknown = JSON.createObjectNode().put("evidence", "unreviewed");
        assertFalse(PublishAchievementCalibrationExtent.validMeasurement(unknown));
    }
}
