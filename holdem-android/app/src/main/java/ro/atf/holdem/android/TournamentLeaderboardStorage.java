package ro.atf.holdem.android;

import java.io.IOException;

interface TournamentLeaderboardStorage {
    String read() throws IOException;
    void write(String encoded) throws IOException;
}
