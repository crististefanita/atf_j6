package ro.atf.holdem.android;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Reads the English accessibility contract used by the deterministic tournament sanity. */
final class BettingActionObservation {
    private static final Pattern ACTION = Pattern.compile(
            "(?:^|, )Action #([1-9][0-9]*) · (.+?) · ([^·]+) \\+([0-9]+) · [^·]+ · contribution ([0-9]+)$");
    private static final long POLL_MILLIS = 80;

    record Entry(int ordinal, String player, String action, int amount, int contribution, String description) { }

    /** Ordinal zero selects the latest action in the frame. A null player accepts any named player. */
    record Expectation(int ordinal, String player, String action, int amount, int contribution) {
        boolean matches(Entry entry) {
            return entry != null && (player == null || player.equals(entry.player()))
                    && action.equals(entry.action()) && amount == entry.amount() && contribution == entry.contribution();
        }

        String description() {
            return (ordinal == 0 ? "latest action" : "action #" + ordinal)
                    + " · " + (player == null ? "any named player" : player)
                    + " · " + action + " +" + amount + " · contribution " + contribution;
        }
    }

    record Result(Entry matched, Entry lastObserved, String lastUnparsedDescription) {
        String diagnostic() {
            if (matched != null) return matched.description();
            if (lastObserved != null) {
                return "last ordinal #" + lastObserved.ordinal() + " · " + lastObserved.description();
            }
            return "last ordinal unavailable · "
                    + (lastUnparsedDescription.isEmpty() ? "no accessible betting action" : lastUnparsedDescription);
        }
    }

    private BettingActionObservation() { }

    static Result await(Supplier<List<String>> readDescriptions, LongSupplier uptimeMillis, LongConsumer sleepMillis,
                        long timeoutMillis, Expectation expected) {
        long deadline = uptimeMillis.getAsLong() + timeoutMillis;
        Entry lastObserved = null;
        String lastUnparsedDescription = "";
        while (true) {
            Entry latest = null;
            Entry match = null;
            for (String description : readDescriptions.get()) {
                Entry entry = parse(description);
                if (entry == null) {
                    if (!description.isBlank()) lastUnparsedDescription = description;
                    continue;
                }
                if (latest == null || entry.ordinal() > latest.ordinal()) latest = entry;
                if (entry.ordinal() == expected.ordinal() && expected.matches(entry)) match = entry;
            }
            if (latest != null) lastObserved = latest;
            if (expected.ordinal() == 0 && expected.matches(latest)) match = latest;
            long remaining = deadline - uptimeMillis.getAsLong();
            if (match != null && remaining >= 0) return new Result(match, lastObserved, lastUnparsedDescription);
            if (remaining <= 0) return new Result(null, lastObserved, lastUnparsedDescription);
            sleepMillis.accept(Math.min(POLL_MILLIS, remaining));
        }
    }

    private static Entry parse(String description) {
        var match = ACTION.matcher(description);
        if (!match.find() || match.group(2).isBlank()) return null;
        try {
            return new Entry(Integer.parseInt(match.group(1)), match.group(2), match.group(3),
                    Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5)), description);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
