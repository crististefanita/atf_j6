package ro.atf.holdem.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Keeps the settled checkpoint available until its tournament has been recorded. */
final class HoldemProgressStore {
    private final HoldemSessionRepository sessions;
    private final TournamentLeaderboardRepository leaderboard;

    private final GameplayAchievements achievements;

    HoldemProgressStore(HoldemSessionRepository sessions, TournamentLeaderboardRepository leaderboard,
                        GameplayAchievements achievements) {
        this.achievements = achievements;
        this.sessions = sessions;
        this.leaderboard = leaderboard;
    }

    TournamentLeaderboard.State checkpoint(LocalHoldemGame game, boolean showingResult, String humanName) throws IOException {
        return checkpoint(game, showingResult, humanName, changes -> { });
    }

    TournamentLeaderboard.State checkpoint(LocalHoldemGame game, boolean showingResult, String humanName,
            Consumer<List<GameplayAchievements.Change>> onAchievementProgress) throws IOException {
        sessions.save(game, showingResult);
        var changes = achievements.recordSettled(game);
        if (!changes.isEmpty()) onAchievementProgress.accept(changes);
        return recordLeaderboard(game, humanName);
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
        achievements.recordSettled(previous);
        sessions.save(next, false);
    }

    TournamentLeaderboard.State recordCompleted(LocalHoldemGame game, String humanName) throws IOException {
        achievements.recordSettled(game);
        return recordLeaderboard(game, humanName);
    }

    private TournamentLeaderboard.State recordLeaderboard(LocalHoldemGame game, String humanName) throws IOException {
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
