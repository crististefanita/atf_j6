package ro.atf.holdem.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** App-private checkpoint with a synced pending file and recoverable rename-based replacement. */
final class FileHoldemSessionStorage implements HoldemSessionStorage {
    private static final long MAX_CHECKPOINT_BYTES = 4_000_000L;
    private final File session;
    private final File pending;
    private final File backup;

    FileHoldemSessionStorage(File directory) {
        Objects.requireNonNull(directory, "directory");
        session = new File(directory, "holdem-session.bin");
        pending = new File(directory, "holdem-session.pending");
        backup = new File(directory, "holdem-session.previous");
    }

    @Override public synchronized String read() throws IOException {
        File source = session.isFile() ? session : backup.isFile() ? backup : pending.isFile() ? pending : null;
        if (source == null) return null;
        long length = source.length();
        if (length > MAX_CHECKPOINT_BYTES) throw new IOException("Session checkpoint exceeds its read limit");
        byte[] bytes = new byte[(int) length];
        try (var input = new FileInputStream(source)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) throw new IOException("Session checkpoint ended during read");
                offset += read;
            }
            if (input.read() != -1) throw new IOException("Session checkpoint changed during read");
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    @Override public synchronized void write(String checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint, "checkpoint");
        File parent = session.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()))
            throw new IOException("Could not create the private session directory");
        byte[] bytes = checkpoint.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_CHECKPOINT_BYTES) throw new IOException("Session checkpoint exceeds its write limit");
        deleteOrFail(pending, "Could not clear an unfinished checkpoint write");
        try (var output = new FileOutputStream(pending, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        } catch (IOException failure) {
            // Keep the failed pending bytes for diagnosis; the committed checkpoint remains untouched.
            throw failure;
        }

        if (session.exists()) {
            deleteOrFail(backup, "Could not clear the previous checkpoint backup");
            if (!session.renameTo(backup))
                throw new IOException("Could not preserve the committed checkpoint before replacement");
        }
        if (!pending.renameTo(session)) {
            IOException failure = new IOException("Could not commit the synced checkpoint");
            if (backup.exists() && !session.exists() && !backup.renameTo(session))
                failure.addSuppressed(new IOException("Could not restore the prior checkpoint"));
            throw failure;
        }
        // The new file is committed. A stale backup is harmless and remains a recovery source if deletion fails.
        if (backup.exists()) backup.delete();
    }

    private static void deleteOrFail(File file, String message) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException(message);
    }
}
