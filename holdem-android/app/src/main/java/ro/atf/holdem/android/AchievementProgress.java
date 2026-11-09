package ro.atf.holdem.android;

final class AchievementProgress {
    interface Storage {
        String read(String key);
        void write(String key, String value);
    }

    enum Answer { PROFIT, BREAK_EVEN, LOSS }

    record State(int attempts, int answered, int correct, int passedLots) {
        private static final State EMPTY = new State(0, 0, 0, 0);
    }

    record Question(long pot, long call, int equity) {
        Answer solution() {
            long edge = (pot + call) * equity - call * 100;
            return edge > 0 ? Answer.PROFIT : edge < 0 ? Answer.LOSS : Answer.BREAK_EVEN;
        }
    }

    private final AchievementCatalog catalog;
    private final Storage storage;
    private final String prefix;

    AchievementProgress(AchievementCatalog catalog, Storage storage, String profileId) {
        this.catalog = catalog;
        this.storage = storage;
        prefix = "achievements-v1:" + profileId + ":";
    }

    State state(AchievementCatalog.Entry entry) {
        String value = storage.read(prefix + entry.trackKey());
        if (value == null) return State.EMPTY;
        String[] parts = value.split(":", -1);
        if (parts.length != 4) throw new IllegalStateException("Invalid achievement progress");
        var state = new State(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        if (state.attempts < state.answered || state.answered < state.correct || state.correct < 0
                || state.passedLots < 0) throw new IllegalStateException("Invalid achievement counters");
        return state;
    }

    boolean completed(AchievementCatalog.Entry entry, State state) {
        return entry.tier() == AchievementCatalog.Tier.EASY
                ? state.correct >= entry.required() : state.passedLots >= entry.lots();
    }

    boolean available(AchievementCatalog.Entry entry) {

        var previous = catalog.find(entry.prerequisite());
        return previous == null || completed(previous, state(previous));
    }

    Question question(AchievementCatalog.Entry entry, int attempt) {
        // Each stage and context gets its own sequence. The call amount identifies each question within its sequence.
        int stage = entry.tier() == AchievementCatalog.Tier.EASY ? 0
                : entry.tier() == AchievementCatalog.Tier.HARD ? 3 : entry.stage();
        long unit = 10L + (long) attempt * 20 + (entry.contextIndex() - 1L) * 4 + stage;
        int totalUnits = 4 + (attempt % 7);
        int callUnits = 1 + ((attempt / 3 + entry.contextIndex()) % (totalUnits - 1));
        long call = unit;
        long pot = call * (totalUnits - callUnits) / callUnits;
        int threshold = (int) (100 * call / (pot + call));
        int equity = switch (attempt % 5) {
            case 0 -> Math.min(99, threshold + 8);
            case 1 -> Math.max(1, threshold - 4);
            case 2 -> threshold;
            case 3 -> threshold + 1;
            default -> Math.max(1, threshold - 11);
        };
        return new Question(pot, call, equity);
    }

    boolean submit(AchievementCatalog.Entry entry, int expectedAttempt, int choice) {
        if (choice < 0 || choice >= 3) throw new IllegalArgumentException("Invalid answer choice");
        State before = state(entry);
        if (before.attempts != expectedAttempt || !available(entry) || completed(entry, before))
            throw new IllegalStateException("Achievement question is no longer active");
        int solution = entry.usesPotOdds() ? question(entry, expectedAttempt).solution().ordinal()
                : AchievementExercises.question(entry, expectedAttempt).correct();
        boolean correct = solution == choice;
        int answers = before.answered + 1;
        int score = before.correct + (correct ? 1 : 0);
        int lots = before.passedLots;
        if (entry.tier() != AchievementCatalog.Tier.EASY && answers == entry.size()) {
            if (score >= entry.required()) lots++;
            answers = 0;
            score = 0;
        }
        storage.write(prefix + entry.trackKey(), (expectedAttempt + 1) + ":" + answers + ":" + score + ":" + lots);
        return correct;
    }
}
