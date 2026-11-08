package ro.atf.holdem.android;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

final class TournamentLeaderboardRepository {
    private final TournamentLeaderboardStorage storage;

    TournamentLeaderboardRepository(TournamentLeaderboardStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    synchronized TournamentLeaderboard.State load() throws IOException {
        String encoded = storage.read();
        if (encoded == null) return TournamentLeaderboard.State.empty();
        try { return TournamentLeaderboardCodec.decode(encoded); }
        catch (RuntimeException invalid) { throw new IOException("Tournament leaderboard is malformed", invalid); }
    }

    synchronized TournamentLeaderboard.RecordResult record(String tournamentId,
            List<TournamentLeaderboard.Placement> placements) throws IOException {
        var current = load();
        var result = TournamentLeaderboard.record(current, tournamentId, placements);
        if (result.newlyRecorded()) storage.write(TournamentLeaderboardCodec.encode(result.state()));
        return result;
    }
}
