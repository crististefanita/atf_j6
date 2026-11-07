package ro.atf.holdem.arena;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import ro.atf.holdem.core.*;

import static org.junit.jupiter.api.Assertions.*;

class ControlledChallengeTest {
    private static final Path CHALLENGES = Path.of("config", "j6-allin-challenges.yaml");

    @Test void controlledShortAllInTournamentSpotReopensAndSettlesEveryOddSidePot() throws IOException {
        var root = load();
        assertEquals(1, integer(root.get("schemaVersion")));
        var challenges = sequence(root.get("challenges"));
        assertFalse(challenges.isEmpty());
        var transcript = new StringBuilder();

        for (Object raw : challenges) {
            var challenge = mapping(raw, Set.of("id", "button", "stacks", "blinds", "deckPrefix",
                    "reopenBeforeAction", "reopenActor", "minimumRaise", "actions", "expectedStacks",
                    "expectedSettlement"));
            String id = string(challenge.get("id"));
            int button = integer(challenge.get("button"));
            List<Integer> stacks = integers(challenge.get("stacks"));
            var blindMap = mapping(challenge.get("blinds"), Set.of("small", "big", "minimumOpen"));
            var level = new BlindLevel(integer(blindMap.get("small")), integer(blindMap.get("big")),
                    integer(blindMap.get("minimumOpen")));
            List<Card> prefix = sequence(challenge.get("deckPrefix")).stream().map(value -> card(string(value))).toList();
            assertTrue(prefix.size() >= 20, "Fixture must include all dealt holes, burn slots and board cards");
            var deck = new ArrayList<>(prefix);
            HoldemHand.shuffledDeck(7).stream().filter(card -> !prefix.contains(card)).forEach(deck::add);
            assertEquals(52, deck.size());
            var hand = new HoldemHand(1, stacks, button, level, deck);
            transcript.append("CHALLENGE id=").append(id).append(" button=").append(button)
                    .append(" stacks=").append(csv(stacks)).append(" blinds=")
                    .append(level.smallBlind()).append(',').append(level.bigBlind()).append(',')
                    .append(level.minimumOpen()).append(" deckPrefix=")
                    .append(String.join(",", prefix.stream().map(Card::label).toList())).append('\n');

            List<?> actions = sequence(challenge.get("actions"));
            int reopenIndex = integer(challenge.get("reopenBeforeAction"));
            for (int index = 0; index < actions.size(); index++) {
                var action = mapping(actions.get(index), Set.of("seat", "type", "amount"));
                int seat = integer(action.get("seat"));
                ActionType type = ActionType.valueOf(string(action.get("type")));
                int amount = integer(action.get("amount"));
                assertEquals(seat, hand.actor(), "Actor mismatch at action " + index + " in " + id);
                if (index == reopenIndex) {
                    assertEquals(integer(challenge.get("reopenActor")), hand.actor());
                    var raise = hand.legal().stream().filter(option -> option.type() == ActionType.RAISE)
                            .findFirst().orElseThrow(() -> new AssertionError("Cumulative short raises did not reopen"));
                    assertEquals(integer(challenge.get("minimumRaise")), raise.minAmount());
                }
                assertTrue(hand.legal().stream().anyMatch(option -> option.type() == type
                                && amount >= option.minAmount() && amount <= option.maxAmount()),
                        "Illegal scripted action " + type + " " + amount + " at " + index);
                transcript.append("ACTION seat=").append(seat).append(" type=").append(type)
                        .append(" amount=").append(amount).append('\n');
                hand.act(seat, type, amount);
            }
            assertEquals(Street.COMPLETE, hand.street());
            assertEquals(integers(challenge.get("expectedStacks")), hand.stacks(), "Final stacks for " + id);
            List<String> actual = hand.history().stream()
                    .filter(event -> event.startsWith("POT ") || event.startsWith("RETURN ")
                            || event.startsWith("SHOWDOWN ") || event.startsWith("FOLD_WIN ")).toList();
            List<String> expected = sequence(challenge.get("expectedSettlement")).stream()
                    .map(ControlledChallengeTest::string).toList();
            assertEquals(expected, actual, "Settlement for " + id);
            assertEquals(stacks.stream().mapToInt(Integer::intValue).sum(),
                    hand.stacks().stream().mapToInt(Integer::intValue).sum(), "Chip conservation for " + id);
            hand.history().forEach(event -> transcript.append("ENGINE ").append(event).append('\n'));
            transcript.append("FINAL stacks=").append(csv(hand.stacks())).append('\n');
        }

        String output = System.getProperty("j6.challenge.trace");
        if (output != null && !output.isBlank()) {
            Path target = Path.of(output);
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, transcript.toString(), StandardOpenOption.CREATE_NEW);
        }
    }

    private static Map<?, ?> load() throws IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        Path local = Files.exists(CHALLENGES) ? CHALLENGES : Path.of("..", "config", CHALLENGES.getFileName().toString());
        try (var input = Files.newBufferedReader(local)) {
            return mapping(new Yaml(new SafeConstructor(options)).load(input), Set.of("schemaVersion", "challenges"));
        }
    }

    private static Map<?, ?> mapping(Object raw, Set<String> keys) {
        if (raw instanceof Map<?, ?> map && map.keySet().equals(keys)) return map;
        throw new IllegalArgumentException("Expected exact YAML keys " + keys);
    }

    private static List<?> sequence(Object raw) {
        if (raw instanceof List<?> list) return list;
        throw new IllegalArgumentException("Expected YAML sequence");
    }

    private static List<Integer> integers(Object raw) {
        return sequence(raw).stream().map(ControlledChallengeTest::integer).toList();
    }

    private static int integer(Object raw) {
        if (raw instanceof Integer value) return value;
        throw new IllegalArgumentException("Expected YAML integer, got " + raw);
    }

    private static String string(Object raw) {
        if (raw instanceof String value) return value;
        throw new IllegalArgumentException("Expected YAML string, got " + raw);
    }

    private static String csv(List<?> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    private static Card card(String label) {
        if (label.length() != 2) throw new IllegalArgumentException("Invalid card " + label);
        Card.Rank rank = switch (label.charAt(0)) {
            case '2' -> Card.Rank.TWO; case '3' -> Card.Rank.THREE; case '4' -> Card.Rank.FOUR;
            case '5' -> Card.Rank.FIVE; case '6' -> Card.Rank.SIX; case '7' -> Card.Rank.SEVEN;
            case '8' -> Card.Rank.EIGHT; case '9' -> Card.Rank.NINE; case 'T' -> Card.Rank.TEN;
            case 'J' -> Card.Rank.JACK; case 'Q' -> Card.Rank.QUEEN; case 'K' -> Card.Rank.KING;
            case 'A' -> Card.Rank.ACE; default -> throw new IllegalArgumentException("Invalid card " + label);
        };
        Card.Suit suit = switch (label.charAt(1)) {
            case 'c' -> Card.Suit.CLUBS; case 'd' -> Card.Suit.DIAMONDS;
            case 'h' -> Card.Suit.HEARTS; case 's' -> Card.Suit.SPADES;
            default -> throw new IllegalArgumentException("Invalid card " + label);
        };
        return new Card(rank, suit);
    }
}
