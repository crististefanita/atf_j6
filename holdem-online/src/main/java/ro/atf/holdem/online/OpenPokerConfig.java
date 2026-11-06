package ro.atf.holdem.online;

import java.net.URI;
import java.util.Optional;

public record OpenPokerConfig(URI endpoint, Optional<String> apiKey, boolean dryRun) {
    public static OpenPokerConfig fromEnvironment() {
        String raw = System.getenv("OPEN_POKER_API_KEY");
        return new OpenPokerConfig(URI.create("wss://openpoker.ai/ws"), Optional.ofNullable(raw).filter(value -> !value.isBlank()), true);
    }
}
