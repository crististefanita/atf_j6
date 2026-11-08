package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public final class HoldemSessionRepositoryComponentTest {
    @Test public void durableCheckpointRestoresTheExactActorBetweenPassiveActions() throws Exception {
        var game = new LocalHoldemGame(6, 811);
        var call = game.view().legalActions().stream().filter(option -> option.type() == ActionType.CALL)
                .findFirst().orElseThrow(AssertionError::new);
        game.play(game.turn(), new LocalHoldemGame.Move(ActionType.CALL, call.amount()));
        assertTrue(game.hasPassiveTurn());
        game.advancePassiveAction(game.turn());
        var storage = new MemoryStorage();
        var repository = new HoldemSessionRepository(storage);

        repository.save(game, false);
        var restored = repository.load();

        assertFalse("A valid replay is not reported as a storage failure", restored.hasFailure());
        assertTrue("The committed session is available after repository recreation", restored.hasCheckpoint());
        assertEquals("The visible frame, stacks, actor and cards are deterministic", game.view(), restored.resumed().game().view());
        assertEquals("Automated turns resume as automated turns", game.hasPassiveTurn(), restored.resumed().game().hasPassiveTurn());
        assertEquals("Exactly the accepted events survive", 2, restored.resumed().game().save().rounds().get(0).size());
    }

    @Test public void truncatedOrEngineInvalidCheckpointIsReportedAndPreservedForDiagnosis() throws Exception {
        var storage = new MemoryStorage();
        storage.value = "not-a-checkpoint";
        var repository = new HoldemSessionRepository(storage);

        var result = repository.load();

        assertTrue("Malformed state is surfaced", result.hasFailure());
        assertFalse("Invalid data does not become a fabricated session", result.hasCheckpoint());
        assertEquals("The failed checkpoint is retained for diagnosis", "not-a-checkpoint", storage.value);
    }

    @Test public void rejectedWriteLeavesTheLastCommittedCheckpointAvailable() throws Exception {
        var storage = new MemoryStorage();
        var repository = new HoldemSessionRepository(storage);
        var game = new LocalHoldemGame(4, 710);
        repository.save(game, false);
        String committed = storage.value;
        storage.failWrites = true;

        assertThrows(IOException.class, () -> repository.save(game, false));

        assertEquals("A failed replacement cannot destroy the previous checkpoint", committed, storage.value);
        assertTrue(repository.load().hasCheckpoint());
    }

    @Test public void engineReplayRejectsAValidlyEncodedButMisorderedActor() {
        var game = new LocalHoldemGame(4, 901);
        int wrongActor = (game.view().actor() + 1) % game.view().seats().size();
        var legal = game.view().legalActions().stream().filter(option -> option.type() == ActionType.CALL)
                .findFirst().orElseThrow(AssertionError::new);
        var corrupt = new LocalHoldemGame.Saved(4, game.view().dealer(), 901, false, 1, 0,
                List.of(0L), List.of(List.of(new LocalHoldemGame.Action(wrongActor,
                        new LocalHoldemGame.Move(ActionType.CALL, legal.amount())))));
        var storage = new MemoryStorage();
        storage.value = HoldemSessionCodec.encode(new HoldemSessionCodec.Checkpoint(corrupt, false));

        var result = new HoldemSessionRepository(storage).load();

        assertTrue("Structurally valid bytes still pass through actor-order validation", result.hasFailure());
        assertFalse(result.hasCheckpoint());
    }

    @Test public void fileCheckpointReplacesRecoverablyAndRestoresTheCommittedTable() throws Exception {
        Path directory = Files.createTempDirectory("holdem-session-component-");
        try {
            var storage = new FileHoldemSessionStorage(directory.toFile());
            var repository = new HoldemSessionRepository(storage);
            var game = LocalHoldemGame.tournament(4, 72);
            repository.save(game, false);
            var before = storage.read();
            assertNotNull(before);

            game.play(game.turn(), new LocalHoldemGame.Move(ActionType.CALL,
                    game.view().legalActions().stream().filter(option -> option.type() == ActionType.CALL)
                            .findFirst().orElseThrow(AssertionError::new).amount()));
            repository.save(game, false);

            var result = new HoldemSessionRepository(new FileHoldemSessionStorage(directory.toFile())).load();

            assertFalse(result.hasFailure());
            assertEquals(game.view(), result.resumed().game().view());
            assertFalse(result.resumed().showingResult());
        } finally {
            try (var files = Files.walk(directory)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException failure) { throw new RuntimeException(failure); }
                });
            }
        }
    }

    @Test public void terminalTournamentCheckpointReopensTheResultScreen() throws Exception {
        Path directory = Files.createTempDirectory("holdem-session-result-");
        try {
            var game = LocalHoldemGame.tournament(4, 1);
            play(game, ActionType.CALL);
            play(game, ActionType.CHECK);
            play(game, ActionType.CHECK);
            play(game, ActionType.CHECK);
            game.nextHand();
            play(game, ActionType.ALL_IN);
            game.nextHand();
            advancePassiveTurns(game);
            play(game, ActionType.ALL_IN);
            assertEquals(LocalHoldemGame.Outcome.WON, game.outcome());

            new HoldemSessionRepository(new FileHoldemSessionStorage(directory.toFile())).save(game, true);
            var reopened = new HoldemSessionRepository(new FileHoldemSessionStorage(directory.toFile())).load();

            assertFalse("A terminal saved game is replayable", reopened.hasFailure());
            assertEquals("The final result route survives a process restart", true, reopened.resumed().showingResult());
            assertEquals(LocalHoldemGame.Outcome.WON, reopened.resumed().game().outcome());
            assertEquals(game.view(), reopened.resumed().game().view());
        } finally {
            try (var files = Files.walk(directory)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException failure) { throw new RuntimeException(failure); }
                });
            }
        }
    }

    @Test public void interruptedReplacementRecoversTheLastCommittedCheckpoint() throws Exception {
        Path directory = Files.createTempDirectory("holdem-session-recovery-");
        try {
            var storage = new FileHoldemSessionStorage(directory.toFile());
            var first = HoldemSessionCodec.encode(new HoldemSessionCodec.Checkpoint(
                    new LocalHoldemGame(4, 73).save(), false));
            storage.write(first);
            Path committed = directory.resolve("holdem-session.bin");
            Path backup = directory.resolve("holdem-session.previous");
            Files.move(committed, backup);
            Files.write(directory.resolve("holdem-session.pending"),
                    "partial-write".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

            assertEquals("A process interruption between renames recovers the previous complete snapshot",
                    first, storage.read());
        } finally {
            try (var files = Files.walk(directory)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException failure) { throw new RuntimeException(failure); }
                });
            }
        }
    }

    private static final class MemoryStorage implements HoldemSessionStorage {
        private String value;
        private boolean failWrites;
        @Override public String read() { return value; }
        @Override public void write(String checkpoint) throws IOException {
            if (failWrites) throw new IOException("simulated atomic storage failure");
            value = checkpoint;
        }
    }

    private static void play(LocalHoldemGame game, ActionType action) {
        advancePassiveTurns(game);
        if (game.view().street() == ro.atf.holdem.core.Street.COMPLETE) return;
        var legal = game.view().legalActions().stream().filter(option -> option.type() == action)
                .findFirst().orElseThrow(AssertionError::new);
        game.play(game.turn(), new LocalHoldemGame.Move(action, legal.amount()));
        advancePassiveTurns(game);
    }

    private static void advancePassiveTurns(LocalHoldemGame game) {
        int count = 0;
        while (game.hasPassiveTurn()) {
            if (++count > 64) throw new AssertionError("Passive action bound exceeded");
            game.advancePassiveAction(game.turn());
        }
    }
}
