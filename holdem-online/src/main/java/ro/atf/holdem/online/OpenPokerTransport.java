package ro.atf.holdem.online;

import java.util.Objects;

/** Boundary for the authorized bot protocol. It intentionally does not join a lobby by construction. */
public final class OpenPokerTransport {
    private final OpenPokerConfig config;

    public OpenPokerTransport(OpenPokerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public String endpoint() {
        return config.endpoint().toString();
    }

    public boolean hasCredential() {
        return config.apiKey().isPresent();
    }

    public boolean dryRun() {
        return config.dryRun();
    }
}
