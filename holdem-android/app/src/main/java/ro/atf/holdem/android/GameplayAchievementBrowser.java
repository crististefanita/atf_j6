package ro.atf.holdem.android;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class GameplayAchievementBrowser {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    enum Status { LOCKED, IN_PROGRESS, EARNED }

    record Query(String text, GameplayAchievementCatalog.Tier tier, Status status) {
        Query {
            text = text == null ? "" : text.trim();
        }

        boolean active() {
            return !text.isEmpty() || tier != null || status != null;
        }
    }

    record Entry(GameplayAchievementCatalog.Tier tier, GameplayAchievementCatalog.Milestone milestone,
                 int current, String title, String alternateTitle) {
        Status status() {
            if (current >= milestone.target()) return Status.EARNED;
            return current == 0 ? Status.LOCKED : Status.IN_PROGRESS;
        }
    }

    private GameplayAchievementBrowser() {}

    static List<Entry> visible(List<Entry> catalog, Query query) {
        var result = new ArrayList<Entry>();
        String search = normalize(query.text());
        for (Entry entry : catalog) {
            if (query.tier() != null && query.tier() != entry.tier()) continue;
            if (query.status() != null && query.status() != entry.status()) continue;
            if (!search.isEmpty() && !normalize(entry.title()).contains(search)
                    && !normalize(entry.alternateTitle()).contains(search)
                    && !normalize(entry.milestone().ruleRo()).contains(search)
                    && !normalize(entry.milestone().ruleEn()).contains(search)) continue;
            result.add(entry);
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }
}
