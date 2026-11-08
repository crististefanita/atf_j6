package ro.atf.holdem.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure cumulative points policy for completed local tournaments. */
final class TournamentLeaderboard {
    static final int DISPLAY_LIMIT = 10;
    static final String POINTS_VERSION = "normalized-placement-v1";
    private static final int MAX_PLAYERS = 10_000;
    private static final int MAX_TOURNAMENTS = 100_000;

    record Placement(String playerId, String displayName, int rank) {
        Placement {
            if (playerId == null || playerId.isBlank() || displayName == null || displayName.isBlank() || rank < 1)
                throw new IllegalArgumentException("Invalid tournament placement");
        }
    }

    record Entry(String playerId, String displayName, long points, int tournaments, int bestRank, int lastRank) {
        Entry {
            if (playerId == null || playerId.isBlank() || displayName == null || displayName.isBlank()
                    || points < 0 || tournaments < 1 || bestRank < 1 || lastRank < 1)
                throw new IllegalArgumentException("Invalid leaderboard entry");
        }
    }

    record State(List<Entry> entries, Set<String> completedTournamentIds) {
        State {
            entries = Collections.unmodifiableList(entries.stream()
                    .sorted(Comparator.comparing(Entry::playerId)).collect(Collectors.toList()));
            completedTournamentIds = Set.copyOf(completedTournamentIds);
            if (entries.size() > MAX_PLAYERS || completedTournamentIds.size() > MAX_TOURNAMENTS)
                throw new IllegalArgumentException("Leaderboard exceeds local history limits");
            if (entries.stream().map(Entry::playerId).distinct().count() != entries.size()
                    || completedTournamentIds.stream().anyMatch(id -> id == null || id.isBlank()))
                throw new IllegalArgumentException("Leaderboard contains duplicate or empty identities");
        }

        static State empty() { return new State(List.of(), Set.of()); }

        List<Entry> top() {
            return Collections.unmodifiableList(entries.stream().sorted(ORDER)
                    .limit(DISPLAY_LIMIT).collect(Collectors.toList()));
        }
    }

    record RecordResult(State state, boolean newlyRecorded, Map<String, Integer> pointsAwarded) {
        RecordResult { pointsAwarded = Map.copyOf(pointsAwarded); }
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::points).reversed()
            .thenComparing(Comparator.comparingInt(Entry::tournaments).reversed())
            .thenComparingInt(Entry::bestRank)
            .thenComparing(Entry::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::playerId);

    private TournamentLeaderboard() { }

    static int rankAt(List<Entry> sorted, int index) {
        if (index < 0 || index >= sorted.size()) throw new IndexOutOfBoundsException(index);
        long points = sorted.get(index).points();
        while (index > 0 && sorted.get(index - 1).points() == points) index--;
        return index + 1;
    }

    static RecordResult record(State state, String tournamentId, List<Placement> placements) {
        Objects.requireNonNull(state, "state");
        if (tournamentId == null || tournamentId.isBlank() || tournamentId.length() > 128)
            throw new IllegalArgumentException("Invalid tournament identity");
        placements = List.copyOf(placements);
        int players = placements.size();
        if (players < 4 || players > 6 || placements.stream().map(Placement::playerId).distinct().count() != players)
            throw new IllegalArgumentException("A tournament needs four to six distinct participants");
        if (state.completedTournamentIds().contains(tournamentId)) return new RecordResult(state, false, Map.of());
        if (state.completedTournamentIds().size() >= MAX_TOURNAMENTS)
            throw new IllegalStateException("Local tournament history is full");
        for (var placement : placements)
            if (placement.rank() > players) throw new IllegalArgumentException("Placement exceeds table size");
        validateCompetitionRanks(placements);

        var countsByRank = new HashMap<Integer, Integer>();
        for (var placement : placements) countsByRank.merge(placement.rank(), 1, Integer::sum);
        var awarded = new HashMap<String, Integer>();
        for (var placement : placements) awarded.put(placement.playerId(),
                pointsForPlacement(placement.rank(), countsByRank.get(placement.rank()), players));

        var entriesById = new HashMap<String, Entry>();
        for (var entry : state.entries()) entriesById.put(entry.playerId(), entry);
        for (var placement : placements) {
            int points = awarded.get(placement.playerId());
            var previous = entriesById.get(placement.playerId());
            long total = Math.addExact(previous == null ? 0 : previous.points(), points);
            int count = Math.addExact(previous == null ? 0 : previous.tournaments(), 1);
            int best = previous == null ? placement.rank() : Math.min(previous.bestRank(), placement.rank());
            entriesById.put(placement.playerId(), new Entry(placement.playerId(), placement.displayName(), total,
                    count, best, placement.rank()));
        }
        if (entriesById.size() > MAX_PLAYERS) throw new IllegalStateException("Local player history is full");
        var tournamentIds = new HashSet<>(state.completedTournamentIds());
        tournamentIds.add(tournamentId);
        return new RecordResult(new State(new ArrayList<>(entriesById.values()), tournamentIds), true, awarded);
    }

    static int pointsForPlacement(int competitionRank, int tieCount, int players) {
        if (players < 2 || competitionRank < 1 || tieCount < 1 || competitionRank + tieCount - 1 > players)
            throw new IllegalArgumentException("Invalid tied placement");
        double averageRank = competitionRank + (tieCount - 1) / 2d;
        return (int) Math.round(100d * (players - averageRank) / (players - 1));
    }

    private static void validateCompetitionRanks(List<Placement> placements) {
        var sorted = placements.stream().map(Placement::rank).sorted().collect(Collectors.toList());
        int index = 0;
        int expected = 1;
        while (index < sorted.size()) {
            int rank = sorted.get(index);
            if (rank != expected) throw new IllegalArgumentException("Ranks must use competition placement for ties");
            int end = index + 1;
            while (end < sorted.size() && sorted.get(end) == rank) end++;
            expected += end - index;
            index = end;
        }
    }
}
