package ro.atf.holdem.android;

import ro.atf.holdem.core.Street;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Replays durable actions through Hold'em rules before exposing a resumed game to the UI. */
final class HoldemSessionRepository {
    record Resumed(LocalHoldemGame game, boolean showingResult) { }
    record LoadResult(Resumed resumed, Exception failure) {
        boolean hasCheckpoint() { return resumed != null; }
        boolean hasFailure() { return failure != null; }
    }

    private final HoldemSessionStorage storage;

    HoldemSessionRepository(HoldemSessionStorage storage) { this.storage = Objects.requireNonNull(storage); }

    synchronized LoadResult load() {
        try {
            String encoded = storage.read();
            if (encoded == null) return new LoadResult(null, null);
            var checkpoint = HoldemSessionCodec.decode(encoded);
            var game = LocalHoldemGame.restore(checkpoint.game());
            if (checkpoint.showingResult() && game.outcome() == null)
                throw new IllegalArgumentException("Saved result screen does not match the tournament state");
            return new LoadResult(new Resumed(game, checkpoint.showingResult()), null);
        } catch (IOException | RuntimeException failure) {
            return new LoadResult(null, failure);
        }
    }

    synchronized void save(LocalHoldemGame game, boolean showingResult) throws IOException {
        storage.write(HoldemSessionCodec.encode(new HoldemSessionCodec.Checkpoint(game.save(), showingResult)));
    }

    /** Commits the destination before the caller acknowledges a continuation from the settled table. */
    synchronized Resumed continueAfterSettlement(LocalHoldemGame current, LocalHoldemGame.Turn expected,
                                                 LongSupplier trainingSeed) throws IOException {
        var frame = current.view();
        if (!current.turn().equals(expected) || frame.street() != Street.COMPLETE)
            throw new IllegalArgumentException("Continuation requires the current settled hand");
        var source = current.save();
        String encoded = storage.read();
        if (encoded == null) throw new IllegalStateException("The settled hand has no committed checkpoint");
        var checkpoint = HoldemSessionCodec.decode(encoded);
        if (checkpoint.showingResult() || !samePosition(source, checkpoint.game()))
            throw new IllegalStateException("The settled checkpoint was replaced");

        boolean result = current.outcome() != null;
        LocalHoldemGame destination = current;
        if (!result && current.isTournament()) {
            destination = LocalHoldemGame.restore(source);
            destination.pauseClock();
            destination.nextHandAtElapsedSeconds(source.elapsedSeconds());
        } else if (!result) {
            int seats = frame.seats().size();
            int dealer = seats == 2 ? frame.bigBlind() : frame.dealer();
            destination = LocalHoldemGame.table(seats, dealer, trainingSeed.getAsLong(), source.humanPlayerId());
        }
        save(destination, result);
        return new Resumed(destination, result);
    }

    private static boolean samePosition(LocalHoldemGame.Saved expected, LocalHoldemGame.Saved stored) {
        // Elapsed foreground time can advance between settlement and the player's tap.
        return expected.seed() == stored.seed() && expected.seats() == stored.seats()
                && expected.dealer() == stored.dealer() && expected.tournamentId().equals(stored.tournamentId())
                && expected.humanPlayerId().equals(stored.humanPlayerId())
                && expected.blindLevelMinutes() == stored.blindLevelMinutes()
                && expected.difficulty() == stored.difficulty()
                && expected.roundStartElapsedSeconds().equals(stored.roundStartElapsedSeconds())
                && expected.rounds().equals(stored.rounds());
    }
}
