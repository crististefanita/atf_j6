package ro.atf.holdem.android;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class TournamentLeaderboardTest {
    @Test public void placementPointsNormalizeToFieldSizeAndAverageSimultaneousTies() {
        assertEquals(100, TournamentLeaderboard.pointsForPlacement(1, 1, 4));
        assertEquals(67, TournamentLeaderboard.pointsForPlacement(2, 1, 4));
        assertEquals(33, TournamentLeaderboard.pointsForPlacement(3, 1, 4));
        assertEquals(0, TournamentLeaderboard.pointsForPlacement(4, 1, 4));
        assertEquals(17, TournamentLeaderboard.pointsForPlacement(3, 2, 4));
        assertEquals(90, TournamentLeaderboard.pointsForPlacement(1, 2, 6));
    }

    @Test public void repeatedTournamentResultIsIdempotentAndStablePlayersAccumulateAcrossMatches() {
        var state = TournamentLeaderboard.State.empty();
        var first = TournamentLeaderboard.record(state, "tournament-1", placements(4, 1, 2, 3, 3));
        assertTrue(first.newlyRecorded());
        assertEquals(100, points(first.state(), "player-1"));
        assertEquals(17, points(first.state(), "player-3"));
        assertEquals(1, first.state().completedTournamentIds().size());

        var duplicate = TournamentLeaderboard.record(first.state(), "tournament-1", placements(4, 4, 3, 2, 1));
        assertFalse(duplicate.newlyRecorded());
        assertEquals(first.state(), duplicate.state());

        var second = TournamentLeaderboard.record(first.state(), "tournament-2", placements(4, 2, 1, 3, 3));
        assertEquals(167, points(second.state(), "player-1"));
        assertEquals(167, points(second.state(), "player-2"));
        assertEquals(2, second.state().entries().get(0).tournaments());
        assertEquals(2, second.state().completedTournamentIds().size());
    }

    @Test public void equalCumulativePointsReceiveTheSameCompetitionRank() {
        var entries = TournamentLeaderboard.record(TournamentLeaderboard.State.empty(), "ties",
                List.of(new TournamentLeaderboard.Placement("a", "Ada", 1),
                        new TournamentLeaderboard.Placement("b", "Bogdan", 1),
                        new TournamentLeaderboard.Placement("c", "Cora", 3),
                        new TournamentLeaderboard.Placement("d", "Doru", 3))).state().top();
        assertEquals(1, TournamentLeaderboard.rankAt(entries, 0));
        assertEquals(1, TournamentLeaderboard.rankAt(entries, 1));
        assertEquals(3, TournamentLeaderboard.rankAt(entries, 2));
        assertEquals(3, TournamentLeaderboard.rankAt(entries, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> TournamentLeaderboard.rankAt(entries, -1));
    }

    @Test public void topTenIsAViewAndDoesNotDiscardPlayersWhoMayClimbLater() {
        var state = TournamentLeaderboard.State.empty();
        for (int index = 0; index < 12; index++) {
            var participants = new ArrayList<TournamentLeaderboard.Placement>();
            boolean userWins = index < 11;
            participants.add(new TournamentLeaderboard.Placement("user-" + index, "User " + index,
                    userWins ? 1 : 4));
            for (int opponent = 0; opponent < 3; opponent++) {
                int rank = userWins ? opponent + 2 : opponent + 1;
                participants.add(new TournamentLeaderboard.Placement("bot-" + index + "-" + opponent,
                        "AI " + opponent, rank));
            }
            state = TournamentLeaderboard.record(state, "match-" + index, participants).state();
        }
        assertEquals(48, state.entries().size());
        assertEquals(10, state.top().size());
        assertFalse(state.top().stream().anyMatch(entry -> entry.playerId().equals("user-11")));
        var userElevenWins = List.of(new TournamentLeaderboard.Placement("user-11", "User 11", 1),
                new TournamentLeaderboard.Placement("new-ai-1", "AI 1", 2),
                new TournamentLeaderboard.Placement("new-ai-2", "AI 2", 3),
                new TournamentLeaderboard.Placement("new-ai-3", "AI 3", 4));
        state = TournamentLeaderboard.record(state, "match-12", userElevenWins).state();
        assertTrue(state.top().stream().anyMatch(entry -> entry.playerId().equals("user-11")));
    }

    @Test public void codecRoundTripsLocalizedNamesAndRejectsTamperedBytes() {
        var recorded = TournamentLeaderboard.record(TournamentLeaderboard.State.empty(), "turneu-1",
                List.of(new TournamentLeaderboard.Placement("user-1", "Ștefan · You", 1),
                        new TournamentLeaderboard.Placement("ai-1", "AI 1", 2),
                        new TournamentLeaderboard.Placement("ai-2", "AI 2", 3),
                        new TournamentLeaderboard.Placement("ai-3", "AI 3", 4))).state();
        String encoded = TournamentLeaderboardCodec.encode(recorded);
        assertTrue(encoded.startsWith("HOLDEM_TOURNAMENT_LEADERBOARD\t1\t"
                + TournamentLeaderboard.POINTS_VERSION + "\n"));
        assertEquals(recorded, TournamentLeaderboardCodec.decode(encoded));
        assertThrows(IllegalArgumentException.class,
                () -> TournamentLeaderboardCodec.decode(encoded.replace("\nP\t", "\nX\t")));
    }

    @Test public void emptyLeaderboardUsesStableLowercaseSha256Encoding() {
        assertEquals("HOLDEM_TOURNAMENT_LEADERBOARD\t1\tnormalized-placement-v1\n"
                        + "H\tf6d50cb61f709e469a473eefaf382df417c1069516ede2191d3c039de7ceb6ae\n",
                TournamentLeaderboardCodec.encode(TournamentLeaderboard.State.empty()));
    }

    @Test public void repositoryWritesOnceAndPreservesItsLastCommittedStateOnDuplicate() throws Exception {
        var storage = new MemoryStorage();
        var repository = new TournamentLeaderboardRepository(storage);
        var placements = placements(4, 1, 2, 3, 4);
        assertTrue(repository.record("one", placements).newlyRecorded());
        String committed = storage.content;
        assertFalse(repository.record("one", placements).newlyRecorded());
        assertEquals(1, storage.writes);
        assertEquals(committed, storage.content);
        assertEquals(1, repository.load().completedTournamentIds().size());
    }

    @Test public void reopenedRepositoryReloadsTheLastCommittedLeaderboardWithoutRecordingAgain() throws Exception {
        var storage = new MemoryStorage();
        var firstSession = new TournamentLeaderboardRepository(storage);
        var placements = placements(4, 1, 2, 3, 4);
        assertTrue(firstSession.record("restart-check", placements).newlyRecorded());

        var reopenedSession = new TournamentLeaderboardRepository(storage);
        var restored = reopenedSession.load();
        assertEquals(firstSession.load(), restored);
        assertFalse(reopenedSession.record("restart-check", placements).newlyRecorded());
        assertEquals(1, storage.writes);
    }

    @Test public void invalidRanksAndDuplicateParticipantIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TournamentLeaderboard.record(
                TournamentLeaderboard.State.empty(), "bad-gap", placements(4, 1, 3, 3, 4)));
        assertThrows(IllegalArgumentException.class, () -> TournamentLeaderboard.record(
                TournamentLeaderboard.State.empty(), "duplicate", List.of(
                        new TournamentLeaderboard.Placement("same", "A", 1),
                        new TournamentLeaderboard.Placement("same", "B", 2),
                        new TournamentLeaderboard.Placement("C", "C", 3),
                        new TournamentLeaderboard.Placement("D", "D", 4))));
    }

    private static List<TournamentLeaderboard.Placement> placements(int players, int... ranks) {
        var result = new ArrayList<TournamentLeaderboard.Placement>();
        for (int seat = 0; seat < players; seat++)
            result.add(new TournamentLeaderboard.Placement("player-" + (seat + 1), "Player " + (seat + 1), ranks[seat]));
        return result;
    }

    private static long points(TournamentLeaderboard.State state, String player) {
        return state.entries().stream().filter(entry -> entry.playerId().equals(player)).findFirst().orElseThrow().points();
    }

    private static final class MemoryStorage implements TournamentLeaderboardStorage {
        private String content;
        private int writes;
        @Override public String read() { return content; }
        @Override public void write(String encoded) { content = encoded; writes++; }
    }
}
