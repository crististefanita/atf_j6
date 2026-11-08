package ro.atf.holdem.android;

import java.io.IOException;

/** Durable storage port for the current private game checkpoint. */
interface HoldemSessionStorage {
    String read() throws IOException;
    void write(String checkpoint) throws IOException;
}
