package ro.atf.holdem.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static ro.atf.holdem.android.GameplayAchievementBrowser.Status.*;
import static ro.atf.holdem.android.GameplayAchievementCatalog.Tier.*;

import java.util.List;
import org.junit.Test;
import ro.atf.holdem.android.GameplayAchievementBrowser.Entry;
import ro.atf.holdem.android.GameplayAchievementBrowser.Query;
import ro.atf.holdem.android.GameplayAchievementCatalog.Milestone;
import ro.atf.holdem.android.GameplayAchievementCatalog.Tier;

public class GameplayAchievementBrowserComponentTest {
    @Test public void combinesTextTierAndStatusWhileKeepingCatalogOrderAndStableIds() {
        var first = entry(EASY, "first-pot", 1, 1, "Câștigă o mână", "Win a poker hand");
        var series = entry(EASY, "three-pots", 1, 3, "Câștigă trei mâini", "Win three hands");
        var title = entry(MEDIUM, "first-title", 0, 1, "Câștigă un turneu", "Win a tournament");
        var catalog = List.of(first, series, title);

        assertEquals(List.of(first, series, title),
                GameplayAchievementBrowser.visible(catalog, new Query("  CASTIGA  ", null, null)));
        assertEquals(List.of(series),
                GameplayAchievementBrowser.visible(catalog, new Query("three hands", EASY, IN_PROGRESS)));
        assertEquals(List.of(first),
                GameplayAchievementBrowser.visible(catalog, new Query("win", EASY, EARNED)));
        assertEquals(List.of(title),
                GameplayAchievementBrowser.visible(catalog, new Query("turneu", MEDIUM, LOCKED)));
        assertEquals(List.of(),
                GameplayAchievementBrowser.visible(catalog, new Query("turneu", EASY, LOCKED)));
        assertEquals(catalog, GameplayAchievementBrowser.visible(catalog, new Query("uncalled", null, null)));
        assertEquals(catalog, GameplayAchievementBrowser.visible(catalog, new Query("", null, null)));
    }

    @Test public void emptyQueryShowsCatalogAndEarnedIncludesProgressFromAHigherPreviousThreshold() {
        var entry = entry(HARD, "previous-target", 7, 3, "Titluri", "Titles");
        assertEquals(EARNED, entry.status());
        assertFalse(new Query(null, null, null).active());
        assertEquals(List.of(entry), GameplayAchievementBrowser.visible(List.of(entry), new Query(null, null, null)));
    }

    @Test public void searchesRenderedThresholdsInBothLanguagesWithoutExposingTheYamlPlaceholder() {
        var milestone = GameplayAchievementCatalog.forTier(EASY).stream()
                .filter(item -> item.id().equals("three-pots"))
                .findFirst().orElseThrow();
        var objective = new Entry(EASY, milestone, 1, "Câștigă 3 mâini diferite", "Win 3 different hands");
        var catalog = List.of(objective);

        assertEquals(catalog, GameplayAchievementBrowser.visible(catalog,
                new Query("in 3 maini distincte", EASY, IN_PROGRESS)));
        assertEquals(catalog, GameplayAchievementBrowser.visible(catalog,
                new Query("in 3 distinct tournament hands", EASY, IN_PROGRESS)));
        assertEquals(List.of(), GameplayAchievementBrowser.visible(catalog,
                new Query("in 3 distinct tournament hands", EASY, LOCKED)));
        assertEquals(List.of(), GameplayAchievementBrowser.visible(catalog,
                new Query("{target}", null, null)));
    }

    private static Entry entry(Tier tier, String id, int current, int target, String title, String alternate) {
        return new Entry(tier, new Milestone(id, GameplayAchievements.Metric.POT, target, 0, false,
                "Jetoane restituite", "Uncalled chips"),
                current, title, alternate);
    }
}
