package ro.atf.holdem.android;

import java.io.IOException;
import java.util.ArrayList;

/** Keeps the settled checkpoint available until its tournament has been recorded. */
final class HoldemProgressStore {
    private final HoldemSessionRepository sessions;
    private final TournamentLeaderboardRepository leaderboard;

    HoldemProgressStore(HoldemSessionRepository sessions, TournamentLeaderboardRepository leaderboard) {
        this.sessions = sessions;
        this.leaderboard = leaderboard;
    }

    TournamentLeaderboard.State checkpoint(LocalHoldemGame game, boolean showingResult, String humanName) throws IOException {
        sessions.save(game, showingResult);
        return recordCompleted(game, humanName);
    }

    void start(LocalHoldemGame previous, LocalHoldemGame next, String humanName) throws IOException {
        var saved = sessions.load();
        if (saved.hasFailure()) throw new IOException("The previous checkpoint could not be reconciled", saved.failure());
        var durable = saved.hasCheckpoint() ? saved.resumed().game() : null;
        var recorded = recordCompleted(durable, humanName);
        if (previous != null && previous.outcome() != null
                && (recorded == null || !recorded.completedTournamentIds().contains(previous.tournamentId()))) {
            // A previous checkpoint write may have failed before the UI reached the terminal table.
            checkpoint(previous, false, humanName);
        }
        sessions.save(next, false);
    }

    TournamentLeaderboard.State recordCompleted(LocalHoldemGame game, String humanName) throws IOException {
        if (game == null || game.outcome() == null) return null;
        var frame = game.view();
        var names = HoldemTablePresentation.playerNames(frame.seats().size(), humanName, game.displayNameSeed());
        var physicalNames = HoldemTablePresentation.physicalSeatNames(frame, names);
        var ranks = game.finishingRanks();
        var placements = new ArrayList<TournamentLeaderboard.Placement>(ranks.size());
        for (int seat = 0; seat < ranks.size(); seat++) {
            placements.add(new TournamentLeaderboard.Placement(game.playerId(seat), physicalNames.get(seat), ranks.get(seat)));
        }
        return leaderboard.record(game.tournamentId(), placements).state();
    }
}
