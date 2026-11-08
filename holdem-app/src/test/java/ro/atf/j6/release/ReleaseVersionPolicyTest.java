package ro.atf.j6.release;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
class ReleaseVersionPolicyTest {
    @Test
    void mapsEveryDeclaredChangeClassToOneDeterministicVersion() {
        assertEquals("0.1.2", ReleaseVersionPolicy.expected("0.1.1", "PATCH_FIX"));
        assertEquals("0.2.0", ReleaseVersionPolicy.expected("0.1.1", "REFACTOR_ONLY"));
        assertEquals("0.2.0", ReleaseVersionPolicy.expected("0.1.1", "COMPATIBLE_FEATURE"));
        assertEquals("1.0.0", ReleaseVersionPolicy.expected("0.1.1", "BREAKING_CHANGE"));
        assertEquals("3.5.0", ReleaseVersionPolicy.expected("3.4.5", "REFACTOR_ONLY"));
        assertEquals("4.0.0", ReleaseVersionPolicy.expected("3.4.5", "BREAKING_CHANGE"));
    }

    @Test
    void rejectsCandidateVersionDriftAndUnsupportedChangeClasses() {
        for (String change : List.of("PATCH_FIX", "REFACTOR_ONLY", "COMPATIBLE_FEATURE", "BREAKING_CHANGE")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ReleaseVersionPolicy.requireExpected("0.1.1", change, "0.10.0"), change);
        }
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersionPolicy.expected("01.2.3", "PATCH_FIX"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersionPolicy.expected("1.2.3", "DOCUMENTATION_ONLY"));
    }

    @Test
    void acceptsOnlyTheVersionImpliedByTheDeclaredChange() {
        ReleaseVersionPolicy.requireExpected("0.1.1", "REFACTOR_ONLY", "0.2.0");
        ReleaseVersionPolicy.requireExpected("2.7.4", "PATCH_FIX", "2.7.5");
    }
}
