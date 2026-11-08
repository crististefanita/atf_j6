package ro.atf.holdem.android;

import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Crash-safe app-private storage for the cumulative tournament table. */
final class AtomicFileTournamentLeaderboardStorage implements TournamentLeaderboardStorage {
    private static final int MAX_BYTES = 4_000_000;
    private final AtomicFile file;

    AtomicFileTournamentLeaderboardStorage(File directory) {
        Objects.requireNonNull(directory, "directory");
        this.file = new AtomicFile(new File(directory, "tournament-leaderboard.dat"));
    }

    @Override public synchronized String read() throws IOException {
        FileInputStream source;
        try { source = file.openRead(); }
        catch (java.io.FileNotFoundException missing) { return null; }
        try (FileInputStream input = source; var bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_BYTES) throw new IOException("Leaderboard file exceeds its read limit");
                bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Override public synchronized void write(String encoded) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Leaderboard file exceeds its write limit");
        FileOutputStream output = file.startWrite();
        try {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            file.finishWrite(output);
        } catch (IOException | RuntimeException failure) {
            file.failWrite(output);
            if (failure instanceof IOException io) throw io;
            throw new IOException("Leaderboard write failed", failure);
        }
    }
}
