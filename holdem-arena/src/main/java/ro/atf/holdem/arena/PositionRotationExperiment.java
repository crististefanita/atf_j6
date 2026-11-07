package ro.atf.holdem.arena;

import java.io.*;
import java.util.*;
import ro.atf.holdem.ai.*;
import ro.atf.holdem.core.*;

/** Replays identical six-seat card distributions with the button rotated around the table. */
public final class PositionRotationExperiment {
    private static final int SEATS = 6;
    private static final int HERO = 2;
    private static final int STARTING_STACK = 800;
    private static final BlindLevel BLINDS = new BlindLevel(10, 20, 20);
    private static final List<Scenario> SCENARIOS = List.of(
            scenario("ace-scare", new String[][]{
                    {"2c", "3d"}, {"4c", "5d"}, {"6c", "7d"},
                    {"8c", "9d"}, {"Tc", "Jd"}, {"Qc", "Kd"}},
                    new String[]{"Ac", "8h", "9s", "2h", "3s"}),
            scenario("made-straight", new String[][]{
                    {"2c", "3d"}, {"4c", "5d"}, {"Qc", "Kd"},
                    {"8c", "9d"}, {"Tc", "Jh"}, {"6c", "7d"}},
                    new String[]{"9h", "Ts", "Jc", "2h", "3s"}));

    private PositionRotationExperiment() {}

    public static int run(PrintWriter trace, TournamentPolicyConfig config) {
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(config, "config");
        List<String> styles = List.of("tag", "calling", "pressure", "loose", "bluff", "bluff-always", "equity");
        int runs = 0;
        for (Scenario scenario : SCENARIOS) {
            for (String style : styles) {
                for (int button = 0; button < SEATS; button++) {
                    play(trace, scenario, style, button, config);
                    runs++;
                }
            }
        }
        trace.flush();
        if (trace.checkError()) throw new UncheckedIOException(new IOException("Position trace write failed"));
        return runs;
    }

    private static void play(PrintWriter trace, Scenario scenario, String style, int button,
                             TournamentPolicyConfig config) {
        String id = scenario.name + "/" + style + "/b" + button;
        trace.printf("ROTATION|%s|%s|%d|%d|%d%n", scenario.name, style, button, HERO, STARTING_STACK);
        var stacks = Collections.nCopies(SEATS, STARTING_STACK);
        var hand = new HoldemHand(1, stacks, button, BLINDS, orderedDeck(scenario, button));
        // Passive, non-raising opponents keep the preflop price interpretable and make
        // checked-to postflop spots observable without hidden-state shortcuts.
        var opponents = new CallingStationPolicy();
        var heroPolicy = policy(style, config);

        while (hand.actor() >= 0) {
            int seat = hand.actor();
            HoldemObservation observation = hand.observeActor();
            verifyProjection(scenario, hand, observation, seat, button);
            Decision decision = (seat == HERO ? heroPolicy : opponents).decide(observation);
            verifyLegal(observation, decision);
            trace.printf("DECISION|%s|%d|%d|%s|%s|%s|%d|%d|%d|%s|%s|%d|%s%n",
                    id, seat, observation.positionFromButton(), observation.street(),
                    labels(observation.holeCards()), labels(observation.communityCards()),
                    observation.pot(), observation.toCall(), observation.heroStack(),
                    legal(observation.legalActions()), decision.action(), decision.amount(), decision.policyVersion());
            hand.act(seat, decision.action(), decision.amount());
        }

        List<Integer> finalStacks = hand.stacks();
        if (finalStacks.stream().mapToInt(Integer::intValue).sum() != SEATS * STARTING_STACK)
            throw new IllegalStateException("Chip conservation failed for " + id);
        if (!hand.board().equals(scenario.board.subList(0, hand.board().size())))
            throw new IllegalStateException("Actual board diverged from the fixed scenario for " + id);
        trace.printf("REVEAL|%s|%s|%d|%d|%s|%s|%s|%d%n", id, scenario.name, button, HERO,
                hands(scenario.holes), labels(scenario.board), labels(hand.board()),
                finalStacks.get(HERO) - STARTING_STACK);
    }

    private static DecisionPolicy policy(String name, TournamentPolicyConfig config) {
        return switch (name) {
            case "tag" -> new TightAggressiveBaseline();
            case "calling" -> new CallingStationPolicy();
            case "pressure" -> new PressurePolicy();
            case "loose" -> new LooseAggressivePolicy(config.looseOpenFrequencyPercent(),
                    config.looseRaiseFrequencyPercent(), config.looseMaxCallPotPercent(),
                    config.looseLatePositionAdjustmentPercent());
            case "bluff" -> new DeterministicBluffPolicy(config.bluffFrequencyPercent(), config.latePositionWindow());
            case "bluff-always" -> new DeterministicBluffPolicy(100, config.latePositionWindow());
            case "equity" -> new ContextualEquityPolicy(config.equitySamples(), config.cashCallMarginPercent(),
                    config.tournamentCallMarginPercent(), config.equityRaiseEdgePercent(),
                    config.valueBetThresholdPercent(), ContextualEquityPolicy.GameMode.TOURNAMENT,
                    config.equityContextWeights());
            default -> throw new IllegalArgumentException("Unknown style " + name);
        };
    }

    private static void verifyProjection(Scenario scenario, HoldemHand hand, HoldemObservation observation,
                                         int actor, int button) {
        int expectedPosition = Math.floorMod(actor - button, SEATS);
        if (observation.heroSeat() != actor || observation.playerCount() != SEATS
                || observation.positionFromButton() != expectedPosition
                || !observation.holeCards().equals(scenario.holes.get(actor))
                || !observation.communityCards().equals(scenario.board.subList(0, hand.board().size())))
            throw new IllegalStateException("Actor projection mismatch; policy must receive only its real observation");
    }

    private static void verifyLegal(HoldemObservation observation, Decision decision) {
        if (observation.legalActions().stream().noneMatch(action -> action.type() == decision.action()
                && decision.amount() >= action.minAmount() && decision.amount() <= action.maxAmount()))
            throw new IllegalStateException("Policy returned an illegal decision: " + decision);
    }

    private static List<Card> orderedDeck(Scenario scenario, int button) {
        var ordered = new ArrayList<Card>(52);
        var used = new HashSet<Card>();
        int first = (button + 1) % SEATS;
        for (int round = 0; round < 2; round++) {
            for (int offset = 0; offset < SEATS; offset++) {
                int seat = (first + offset) % SEATS;
                addUnique(ordered, used, scenario.holes.get(seat).get(round));
            }
        }
        addBurn(ordered, used);
        scenario.board.subList(0, 3).forEach(card -> addUnique(ordered, used, card));
        addBurn(ordered, used);
        addUnique(ordered, used, scenario.board.get(3));
        addBurn(ordered, used);
        addUnique(ordered, used, scenario.board.get(4));
        for (Card card : standardDeck()) if (used.add(card)) ordered.add(card);
        if (ordered.size() != 52) throw new IllegalStateException("Fixture did not produce a full deck");
        return List.copyOf(ordered);
    }

    private static void addBurn(List<Card> ordered, Set<Card> used) {
        Card burn = standardDeck().stream().filter(card -> !used.contains(card)).findFirst().orElseThrow();
        addUnique(ordered, used, burn);
    }

    private static void addUnique(List<Card> ordered, Set<Card> used, Card card) {
        if (!used.add(card)) throw new IllegalArgumentException("Duplicate card in fixed deal: " + card.label());
        ordered.add(card);
    }

    private static List<Card> standardDeck() {
        var deck = new ArrayList<Card>(52);
        for (Card.Suit suit : Card.Suit.values())
            for (Card.Rank rank : Card.Rank.values()) deck.add(new Card(rank, suit));
        return deck;
    }

    private static Scenario scenario(String name, String[][] rawHoles, String[] rawBoard) {
        List<List<Card>> holes = Arrays.stream(rawHoles).map(cards -> Arrays.stream(cards).map(PositionRotationExperiment::card).toList()).toList();
        List<Card> board = Arrays.stream(rawBoard).map(PositionRotationExperiment::card).toList();
        var seen = new HashSet<Card>();
        holes.forEach(seat -> seat.forEach(card -> {
            if (!seen.add(card)) throw new IllegalArgumentException("Duplicate private card in " + name);
        }));
        board.forEach(card -> {
            if (!seen.add(card)) throw new IllegalArgumentException("Board overlaps a private card in " + name);
        });
        return new Scenario(name, holes, board);
    }

    private static Card card(String label) {
        char rank = label.charAt(0), suit = label.charAt(1);
        Card.Rank r = switch (rank) {
            case '2' -> Card.Rank.TWO; case '3' -> Card.Rank.THREE; case '4' -> Card.Rank.FOUR;
            case '5' -> Card.Rank.FIVE; case '6' -> Card.Rank.SIX; case '7' -> Card.Rank.SEVEN;
            case '8' -> Card.Rank.EIGHT; case '9' -> Card.Rank.NINE; case 'T' -> Card.Rank.TEN;
            case 'J' -> Card.Rank.JACK; case 'Q' -> Card.Rank.QUEEN; case 'K' -> Card.Rank.KING;
            case 'A' -> Card.Rank.ACE; default -> throw new IllegalArgumentException(label);
        };
        Card.Suit s = switch (suit) {
            case 'c' -> Card.Suit.CLUBS; case 'd' -> Card.Suit.DIAMONDS;
            case 'h' -> Card.Suit.HEARTS; case 's' -> Card.Suit.SPADES;
            default -> throw new IllegalArgumentException(label);
        };
        return new Card(r, s);
    }

    private static String labels(List<Card> cards) {
        return cards.isEmpty() ? "-" : String.join(",", cards.stream().map(Card::label).toList());
    }

    private static String hands(List<List<Card>> hands) {
        return String.join("/", hands.stream().map(PositionRotationExperiment::labels).toList());
    }

    private static String legal(List<LegalAction> actions) {
        return String.join(",", actions.stream().map(action -> action.type() + ":" + action.minAmount() + ":" + action.maxAmount()).toList());
    }

    private record Scenario(String name, List<List<Card>> holes, List<Card> board) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PositionRotationExperiment policy.yaml trace.psv");
        var config = TournamentPolicyConfig.load(java.nio.file.Path.of(args[0]));
        try (var trace = new PrintWriter(java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of(args[1]),
                java.nio.file.StandardOpenOption.CREATE_NEW))) {
            System.out.printf("Completed %d fixed-deal/button-rotation hands%n", run(trace, config));
        }
    }
}
