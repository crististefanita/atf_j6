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
        assertTrue(profile("one").available(easy.get(0)));
    }

    @Test public void savedEasyAnswersUnlockInOrderAndStayWithTheProfile() {
        var first = catalog.find("potodds-1-u1");
        var second = catalog.find("potodds-1-u2");
        var progress = profile("one");
        assertFalse(progress.available(second));
        assertTrue(progress.submit(first, 0, progress.question(first, 0).solution().ordinal()));
        var restored = profile("one");
        assertTrue(restored.completed(first, restored.state(first)));
        assertTrue(restored.available(second));
        assertEquals(1, restored.state(second).correct());
        assertThrows(IllegalStateException.class,
                () -> restored.submit(second, 0, 0));
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

    @Test public void everyCatalogEntryCanBeEarnedAndRestoredInCurriculumOrder() {
        var progress = profile("complete");
        for (var tier : AchievementCatalog.Tier.values()) {
            for (var entry : catalog.entries(tier)) {
                assertTrue(entry.id(), progress.available(entry));
                int questions = tier == AchievementCatalog.Tier.EASY ? 1 : entry.size();
                int right = tier == AchievementCatalog.Tier.EASY ? 1 : entry.required();
                for (int lot = 0; lot < entry.lots(); lot++) {
                    for (int i = 0; i < questions; i++) {
                        int attempt = progress.state(entry).attempts();
                        if (!entry.usesPotOdds()) {
                            var exercise = AchievementExercises.question(entry, attempt);
                            assertFalse(entry.id(), exercise.prompt().isBlank());
                            assertFalse(entry.id(), exercise.explanation().isBlank());
                            assertEquals(entry.id(), 3, new java.util.HashSet<>(exercise.options()).size());
                        }
                        answer(progress, entry, 1, i < right ? 1 : 0);
                    }
                    progress = profile("complete");
                }
                assertTrue(entry.id(), progress.completed(entry, progress.state(entry)));
            }
        }
    }

    @Test public void rulesExercisesResolveSidePotsAndReopeningBoundaries() {
        var side = AchievementExercises.question(catalog.find("poturi-2-u1"), 0);
        assertEquals("36", side.options().get(side.correct()));
        var shortAllIn = AchievementExercises.question(catalog.find("allin-2-u1"), 0);
        var cumulative = AchievementExercises.question(catalog.find("allin-3-u1"), 0);
        assertEquals("Nu", shortAllIn.options().get(shortAllIn.correct()));
        assertEquals("Da", cumulative.options().get(cumulative.correct()));
        var boardPlays = AchievementExercises.question(catalog.find("showdown-2-u1"), 0);
        assertEquals("Egalitate", boardPlays.options().get(boardPlays.correct()));
    }

    @Test public void mockedProgressActivatesNextStageAndKeepsLockedRowsOutOfPractice() {
        saved.put("achievements-v1:one:pozitie-1-easy", "1:1:1:0");
        var progress = profile("one");
        var tab = AchievementPresentation.tab(catalog, progress, AchievementCatalog.Tier.EASY, false);
        assertEquals(500, tab.total());
        assertEquals(1, tab.unlocked());
        assertEquals(AchievementPresentation.Status.COMPLETE, tab.rows().get(0).status());
        assertEquals(AchievementPresentation.Status.IN_PROGRESS, tab.rows().get(1).status());
        assertEquals(AchievementPresentation.Status.LOCKED, tab.rows().get(2).status());
        assertEquals(AchievementPresentation.Status.READY, tab.rows().get(5).status());

        var filtered = AchievementPresentation.tab(catalog, progress, AchievementCatalog.Tier.EASY, true);
        assertEquals(500, filtered.total());
        assertEquals(1, filtered.unlocked());
        assertEquals("pozitie-1-u2", filtered.rows().get(0).entry().id());
        assertTrue(filtered.rows().get(0).startsFamily());
        assertFalse(filtered.rows().get(1).startsFamily());
        assertTrue(filtered.rows().stream().allMatch(AchievementPresentation.Row::canPractice));
    }

    @Test public void mockedHardLotShowsBothCountersAfterReload() {
        saved.put("achievements-v1:one:pozitie-1-easy", "5:5:5:0");
        saved.put("achievements-v1:one:pozitie-1-m1", "10:0:0:1");
        saved.put("achievements-v1:one:pozitie-1-m2", "15:0:0:1");
        saved.put("achievements-v1:one:pozitie-1-g1", "17:7:5:1");
        var tab = AchievementPresentation.tab(catalog, profile("one"), AchievementCatalog.Tier.HARD, true);
        assertEquals(100, tab.total());
        assertEquals(0, tab.unlocked());
        assertEquals(1, tab.rows().size());
        var row = tab.rows().get(0);
        assertEquals(AchievementPresentation.Status.IN_PROGRESS, row.status());
        assertTrue(row.startsFamily());
        assertTrue(row.showsCurrentLot());
        assertEquals(1, row.progress().passedLots());
        assertEquals(7, row.progress().answered());
        assertEquals(5, row.progress().correct());

        var other = AchievementPresentation.tab(catalog, profile("other"), AchievementCatalog.Tier.HARD, true);
        assertTrue(other.rows().isEmpty());
    }

    @Test public void mockUnlockUpdatesTheDisplayedSummaryAndNextPracticeRowAfterAnAnswer() {
        var entry = catalog.find("pozitie-1-u1");
        var progress = profile("one");
        assertEquals(AchievementPresentation.Status.READY, AchievementPresentation.row(entry, progress, true).status());
        assertTrue(progress.submit(entry, 0, AchievementExercises.question(entry, 0).correct()));
        var tab = AchievementPresentation.tab(catalog, profile("one"), AchievementCatalog.Tier.EASY, true);
        assertEquals(1, tab.unlocked());
        assertEquals("pozitie-1-u2", tab.rows().get(0).entry().id());
    }

    private AchievementProgress profile(String id) { return new AchievementProgress(catalog, storage, id); }

    private static void answer(AchievementProgress progress, AchievementCatalog.Entry entry, int count, int right) {
        for (int i = 0; i < count; i++) {
            int attempt = progress.state(entry).attempts();
            int solution = entry.usesPotOdds() ? progress.question(entry, attempt).solution().ordinal()
                    : AchievementExercises.question(entry, attempt).correct();
            assertEquals(i < right, progress.submit(entry, attempt, i < right ? solution : (solution + 1) % 3));
        }
    }

    private static AchievementCatalog loadCatalog() {
        try (var reader = Files.newBufferedReader(Path.of("src/main/assets/training-achievements.csv"))) {
            return new AchievementCatalog(reader);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }
}
