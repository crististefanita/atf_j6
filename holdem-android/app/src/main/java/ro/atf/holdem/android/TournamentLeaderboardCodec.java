package ro.atf.holdem.android;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Deterministic, checksummed, UTF-8-safe local format for the tournament leaderboard. */
final class TournamentLeaderboardCodec {
    private static final String HEADER = "HOLDEM_TOURNAMENT_LEADERBOARD\t1\t"
            + TournamentLeaderboard.POINTS_VERSION + "\n";
    private static final int MAX_CHARS = 4_000_000;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TournamentLeaderboardCodec() { }

    static String encode(TournamentLeaderboard.State state) {
        var body = new StringBuilder(HEADER);
        state.completedTournamentIds().stream().sorted().forEach(id -> body.append("T\t").append(text(id)).append('\n'));
        state.entries().stream().sorted(Comparator.comparing(TournamentLeaderboard.Entry::playerId)).forEach(entry ->
                body.append("P\t").append(text(entry.playerId())).append('\t').append(text(entry.displayName()))
                        .append('\t').append(entry.points()).append('\t').append(entry.tournaments())
                        .append('\t').append(entry.bestRank()).append('\t').append(entry.lastRank()).append('\n'));
        String result = body + "H\t" + checksum(body.toString()) + "\n";
        if (result.length() > MAX_CHARS) throw new IllegalArgumentException("Leaderboard file exceeds its size limit");
        return result;
    }

    static TournamentLeaderboard.State decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_CHARS || !encoded.startsWith(HEADER))
            throw new IllegalArgumentException("Invalid leaderboard file header or size");
        int hashLine = encoded.lastIndexOf("H\t");
        if (hashLine < HEADER.length() || hashLine > 0 && encoded.charAt(hashLine - 1) != '\n')
            throw new IllegalArgumentException("Leaderboard checksum is missing");
        String body = encoded.substring(0, hashLine);
        String checksumLine = encoded.substring(hashLine).stripTrailing();
        String[] checksumFields = checksumLine.split("\\t", -1);
        if (checksumFields.length != 2 || !MessageDigest.isEqual(
                checksumFields[1].getBytes(StandardCharsets.US_ASCII), checksum(body).getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Leaderboard checksum mismatch");

        var tournamentIds = new HashSet<String>();
        var entries = new ArrayList<TournamentLeaderboard.Entry>();
        String[] lines = body.substring(HEADER.length()).split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            String[] fields = lines[index].split("\\t", -1);
            if (fields.length == 2 && fields[0].equals("T")) {
                if (!tournamentIds.add(untext(fields[1]))) throw new IllegalArgumentException("Duplicate tournament identity");
            } else if (fields.length == 7 && fields[0].equals("P")) {
                entries.add(new TournamentLeaderboard.Entry(untext(fields[1]), untext(fields[2]),
                        parseLong(fields[3]), parseInt(fields[4]), parseInt(fields[5]), parseInt(fields[6])));
            } else {
                throw new IllegalArgumentException("Malformed leaderboard record");
            }
        }
        return new TournamentLeaderboard.State(entries, tournamentIds);
    }

    private static String text(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String untext(String value) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            if (decoded.isBlank() || decoded.length() > 256) throw new IllegalArgumentException("Invalid text field");
            return decoded;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Malformed text field", invalid);
        }
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid integer", invalid); }
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid long", invalid); }
    }

    private static String checksum(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var encoded = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                int unsigned = item & 0xff;
                encoded.append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0x0f]);
            }
            return encoded.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
