package ro.atf.holdem.arena;

import atf.testkit.AtfComponentTest;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class PositionRotationExperimentTest {
    @Test void sameFixedDealsRotateEveryStyleThroughEverySeatPositionWithoutIllegalActions() {
        var output = new StringWriter();
        var config = new TournamentPolicyConfig(18, 2, 45, 20, 35);
        assertEquals(84, PositionRotationExperiment.run(new PrintWriter(output), config));
        var lines = output.toString().lines().toList();
        assertEquals(84, lines.stream().filter(line -> line.startsWith("ROTATION|")).count());
        assertEquals(84, lines.stream().filter(line -> line.startsWith("REVEAL|")).count());
        assertTrue(lines.stream().filter(line -> line.startsWith("DECISION|")).count() > 60);
        for (String line : lines) {
            if (line.startsWith("ROTATION|")) assertEquals(6, line.split("\\|", -1).length);
            if (line.startsWith("DECISION|")) assertEquals(14, line.split("\\|", -1).length);
        }
    }
}
