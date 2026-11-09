package ro.atf.holdem.android;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.Assert.*;

public class AchievementProgressComponentTest {
    private final AchievementCatalog catalog = loadCatalog();
    private final HashMap<String, String> saved = new HashMap<>();
    private final AchievementProgress.Storage storage = new AchievementProgress.Storage() {
        @Override public String read(String key) { return saved.get(key); }
        @Override public void write(String key, String value) { saved.put(key, value); }
    };

    @Test public void catalogOrdersFundamentalsBeforeSpecialistPlayAndKeepsTheThreeTierCounts() {
        assertEquals(500, catalog.entries(AchievementCatalog.Tier.EASY).size());
        assertEquals(200, catalog.entries(AchievementCatalog.Tier.MEDIUM).size());
        assertEquals(100, catalog.entries(AchievementCatalog.Tier.HARD).size());
        var easy = catalog.entries(AchievementCatalog.Tier.EASY);
        assertEquals("pozitie-1-u1", easy.get(0).id());
        assertEquals("pozitie-1-u5", easy.get(4).id());
        assertEquals("review-5-u5", easy.get(499).id());
        assertFalse(profile("one").available(easy.get(0)));
    }

    @Test public void savedEasyAnswersUnlockInOrderAndStayWithTheProfile() {
        var first = catalog.find("potodds-1-u1");
        var second = catalog.find("potodds-1-u2");
        var progress = profile("one");
        assertFalse(progress.available(second));
        assertTrue(progress.submit(first, 0, progress.question(first, 0).solution()));
        var restored = profile("one");
        assertTrue(restored.completed(first, restored.state(first)));
        assertTrue(restored.available(second));
        assertEquals(1, restored.state(second).correct());
        assertThrows(IllegalStateException.class,
                () -> restored.submit(second, 0, AchievementProgress.Answer.PROFIT));
        assertFalse(profile("two").available(second));
    }

    @Test public void mediumKeepsTheDenominatorAndHardRequiresThreeIndependentPassingLots() {
        var progress = profile("one");
        for (int i = 1; i <= 5; i++) {
            var step = catalog.find("potodds-1-u" + i);
            answer(progress, step, 1, 1);
        }
        var medium = catalog.find("potodds-1-m1");
        answer(progress, medium, 10, 7);
        assertFalse(progress.completed(medium, progress.state(medium)));
        answer(progress, medium, 4, 4);
        progress = profile("one");
        assertEquals(4, progress.state(medium).answered());
        answer(progress, medium, 6, 4);
        assertTrue(progress.completed(medium, progress.state(medium)));
        answer(progress, catalog.find("potodds-1-m2"), 15, 12);

        var hard = catalog.find("potodds-1-g1");
        assertTrue(progress.available(hard));
        var firstQuestion = progress.question(hard, 0);
        answer(progress, hard, 20, 20);
        assertFalse(progress.completed(hard, progress.state(hard)));
        assertNotEquals(firstQuestion, progress.question(hard, progress.state(hard).attempts()));
        answer(progress, hard, 10, 8);
        assertTrue(progress.completed(hard, profile("one").state(hard)));
    }

    @Test public void expectedValueUsesTheEligiblePotAndExactBreakEvenArithmetic() {
        assertEquals(AchievementProgress.Answer.PROFIT, new AchievementProgress.Question(90, 10, 11).solution());
        assertEquals(AchievementProgress.Answer.BREAK_EVEN, new AchievementProgress.Question(90, 10, 10).solution());
        assertEquals(AchievementProgress.Answer.LOSS, new AchievementProgress.Question(90, 10, 9).solution());
    }

    private AchievementProgress profile(String id) { return new AchievementProgress(catalog, storage, id); }

    private static void answer(AchievementProgress progress, AchievementCatalog.Entry entry, int count, int right) {
        for (int i = 0; i < count; i++) {
            int attempt = progress.state(entry).attempts();
            var solution = progress.question(entry, attempt).solution();
            var wrong = AchievementProgress.Answer.values()[(solution.ordinal() + 1) % 3];
            progress.submit(entry, attempt, i < right ? solution : wrong);
        }
    }

    private static AchievementCatalog loadCatalog() {
        try (var reader = Files.newBufferedReader(Path.of("../../docs/feedback/J6-PROD-01-ACHIEVEMENT-CATALOG.csv"))) {
            return new AchievementCatalog(reader);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }
}
