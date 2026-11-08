package ro.atf.holdem.android;

import java.io.IOException;
import java.util.Objects;

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
}
