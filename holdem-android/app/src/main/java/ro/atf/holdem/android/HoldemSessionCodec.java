package ro.atf.holdem.android;

import ro.atf.holdem.core.ActionType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Versioned, bounded checkpoint encoding; engine replay remains the final integrity check. */
final class HoldemSessionCodec {
    private static final int MAGIC = 0x484C534A;
    private static final int SCHEMA = 4;
    private static final int MAX_ENCODED_CHARS = 4_000_000;
    private static final int MAX_ROUNDS = 20_000;
    private static final int MAX_ACTIONS_PER_ROUND = 2_000;
    private static final int MAX_TOTAL_ACTIONS = 200_000;

    record Checkpoint(LocalHoldemGame.Saved game, boolean showingResult) { }

    private HoldemSessionCodec() { }

    static String encode(Checkpoint checkpoint) {
        if (checkpoint == null || checkpoint.game() == null) throw new IllegalArgumentException("Missing checkpoint");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                var saved = checkpoint.game();
                output.writeInt(MAGIC);
                output.writeInt(SCHEMA);
                output.writeBoolean(checkpoint.showingResult());
                output.writeInt(saved.seats());
                output.writeInt(saved.dealer());
                output.writeLong(saved.seed());
                output.writeBoolean(saved.tournament());
                output.writeInt(saved.blindLevelMinutes());
                output.writeLong(saved.elapsedSeconds());
                output.writeUTF(saved.tournamentId());
                output.writeUTF(saved.humanPlayerId());
                output.writeBoolean(saved.showWinChance());
                output.writeBoolean(saved.difficulty() == LocalHoldemGame.Difficulty.HARD);
                output.writeInt(saved.rounds().size());
                for (int round = 0; round < saved.rounds().size(); round++) {
                    output.writeLong(saved.roundStartElapsedSeconds().get(round));
                    var actions = saved.rounds().get(round);
                    output.writeInt(actions.size());
                    for (var action : actions) {
                        output.writeInt(action.actor());
                        output.writeInt(action.move().action().ordinal());
                        output.writeInt(action.move().amount());
                    }
                }
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            if (encoded.length() > MAX_ENCODED_CHARS) throw new IllegalArgumentException("Checkpoint exceeds its storage limit");
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory checkpoint encoding failed", impossible);
        }
    }

    static Checkpoint decode(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_ENCODED_CHARS)
            throw new IllegalArgumentException("Invalid checkpoint size");
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readInt() != MAGIC) throw new IllegalArgumentException("Checkpoint signature mismatch");
                int schema = input.readInt();
                if (schema < 1 || schema > SCHEMA) throw new IllegalArgumentException("Unsupported checkpoint version");
                boolean showingResult = input.readBoolean();
                int seats = input.readInt();
                int dealer = input.readInt();
                long seed = input.readLong();
                boolean tournament = input.readBoolean();
                int blindLevelMinutes = input.readInt();
                long elapsedSeconds = input.readLong();
                String tournamentId = schema >= 2 ? input.readUTF() : null;
                String humanPlayerId = schema >= 2 ? input.readUTF() : null;
                boolean showWinChance = schema >= 3 && input.readBoolean();
                var difficulty = schema >= 4 && input.readBoolean()
                        ? LocalHoldemGame.Difficulty.HARD : LocalHoldemGame.Difficulty.NORMAL;
                if (schema >= 2 && (tournamentId.length() > 128 || humanPlayerId.isBlank() || humanPlayerId.length() > 128))
                    throw new IllegalArgumentException("Invalid checkpoint participant identity");
                int roundCount = input.readInt();
                if (roundCount < 1 || roundCount > MAX_ROUNDS) throw new IllegalArgumentException("Invalid checkpoint round count");
                var startTimes = new ArrayList<Long>(roundCount);
                var rounds = new ArrayList<List<LocalHoldemGame.Action>>(roundCount);
                int totalActions = 0;
                for (int round = 0; round < roundCount; round++) {
                    startTimes.add(input.readLong());
                    int actionCount = input.readInt();
                    if (actionCount < 0 || actionCount > MAX_ACTIONS_PER_ROUND
                            || (totalActions += actionCount) > MAX_TOTAL_ACTIONS)
                        throw new IllegalArgumentException("Invalid checkpoint action count");
                    var actions = new ArrayList<LocalHoldemGame.Action>(actionCount);
                    for (int index = 0; index < actionCount; index++) {
                        int actor = input.readInt();
                        int actionOrdinal = input.readInt();
                        int amount = input.readInt();
                        if (actor < 0 || actor >= seats || actionOrdinal < 0 || actionOrdinal >= ActionType.values().length || amount < 0)
                            throw new IllegalArgumentException("Invalid checkpoint action");
                        actions.add(new LocalHoldemGame.Action(actor,
                                new LocalHoldemGame.Move(ActionType.values()[actionOrdinal], amount)));
                    }
                    rounds.add(List.copyOf(actions));
                }
                if (input.available() != 0) throw new IllegalArgumentException("Checkpoint has trailing bytes");
                var saved = schema >= 2
                        ? new LocalHoldemGame.Saved(seats, dealer, seed, tournament, blindLevelMinutes,
                        elapsedSeconds, startTimes, rounds, tournamentId, humanPlayerId, showWinChance, difficulty)
                        : new LocalHoldemGame.Saved(seats, dealer, seed, tournament, blindLevelMinutes,
                        elapsedSeconds, startTimes, rounds);
                return new Checkpoint(saved, showingResult);
            }
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Checkpoint is incomplete or malformed", invalid);
        }
    }
}
