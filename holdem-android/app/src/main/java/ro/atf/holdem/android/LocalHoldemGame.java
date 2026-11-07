package ro.atf.holdem.android;

import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.HoldemHand;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;
import ro.atf.holdem.core.Tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Offline play with passive opponents and a fixed human viewer. */
final class LocalHoldemGame {
    static final List<Integer> TABLE_SIZES = List.of(2, 4, 5, 6);
    static final List<Integer> TOURNAMENT_SIZES = List.of(4, 5, 6);
    private static final int STARTING_STACK = 2000;
    private static final BlindLevel TRAINING_BLINDS = new BlindLevel(10, 20, 20);

    enum Outcome { WON, LOST }
    record Turn(UUID handId, int revision) {}
    record Move(ActionType action, int amount) {}
    record Saved(int seats, int dealer, long seed, boolean tournament, List<List<Move>> rounds) {
        Saved {
            var copy = new ArrayList<List<Move>>(rounds.size());
            for (var round : rounds) copy.add(List.copyOf(round));
            rounds = List.copyOf(copy);
            if (rounds.isEmpty() || (!tournament && rounds.size() != 1)) {
                throw new IllegalArgumentException("Invalid saved rounds");
            }
        }
    }

    private final int seats;
    private final int initialDealer;
    private final long seed;
    private final Tournament tournament;
    private final Random dealSeeds;
    private final List<List<Move>> rounds = new ArrayList<>();
    private UUID handId;
    private HoldemHand hand;
    private List<Move> moves;
    private List<Integer> openingStacks;
    private int human;

    LocalHoldemGame(int seats, long seed) {
        this(seats, 0, seed);
    }

    LocalHoldemGame(int seats, int dealer, long seed) {
        this(seats, dealer, seed, false);
    }

    static LocalHoldemGame tournament(int seats, long seed) {
        return new LocalHoldemGame(seats, 0, seed, true);
    }

    private LocalHoldemGame(int seats, int dealer, long seed, boolean tournamentMode) {
        if (!(tournamentMode ? TOURNAMENT_SIZES : TABLE_SIZES).contains(seats)) {
            throw new IllegalArgumentException("Unsupported training table size: " + seats);
        }
        this.seats = seats;
        initialDealer = dealer;
        this.seed = seed;
        dealSeeds = new Random(seed);
        tournament = tournamentMode
                ? new Tournament(new Tournament.Rules(seats, STARTING_STACK, 1, List.of(TRAINING_BLINDS)), dealer)
                : null;
        startHand();
    }

    private void startHand() {
        openingStacks = tournament == null ? Collections.nCopies(seats, STARTING_STACK) : tournament.stacks();
        hand = tournament == null
                ? new HoldemHand(1, openingStacks, initialDealer, TRAINING_BLINDS, HoldemHand.shuffledDeck(seed))
                : tournament.startHand(0, dealSeeds.nextLong()).hand();
        if (rounds.isEmpty()) human = seats == 2 ? 0 : hand.actor();
        handId = UUID.randomUUID();
        moves = new ArrayList<>();
        rounds.add(moves);
        playOpponents();
    }

    HoldemTableViewState view() { return hand.viewFor(human); }

    Turn turn() { return new Turn(handId, moves.size()); }

    boolean isTournament() { return tournament != null; }

    int handNumber() { return rounds.size(); }

    boolean eliminated(int seat) {
        return isTournament() && (openingStacks.get(seat) == 0 || (hand.street() == Street.COMPLETE && hand.stacks().get(seat) == 0));
    }

    Outcome outcome() {
        if (!isTournament() || hand.street() != Street.COMPLETE) return null;
        if (eliminated(human)) return Outcome.LOST;
        return hand.stacks().stream().filter(stack -> stack > 0).count() == 1 ? Outcome.WON : null;
    }

    void nextHand() {
        if (!isTournament() || hand.street() != Street.COMPLETE || outcome() != null) {
            throw new IllegalArgumentException("No next tournament hand");
        }
        startHand();
    }

    void play(Turn expected, Move move) {
        if (!turn().equals(expected) || hand.actor() != human) throw new IllegalArgumentException("This turn is no longer active");
        hand.act(human, move.action(), move.amount());
        moves.add(move);
        playOpponents();
    }

    private void playOpponents() {
        while (hand.actor() >= 0 && hand.actor() != human) {
            var response = hand.legal().stream()
                    .filter(option -> option.type() == ActionType.CHECK || option.type() == ActionType.CALL)
                    .findFirst().orElseThrow(IllegalStateException::new);
            hand.act(hand.actor(), response.type(), response.amount());
        }
    }

    Saved save() { return new Saved(seats, initialDealer, seed, isTournament(), rounds); }

    static LocalHoldemGame restore(Saved saved) {
        var game = new LocalHoldemGame(saved.seats(), saved.dealer(), saved.seed(), saved.tournament());
        for (var round : saved.rounds()) {
            for (Move move : round) game.play(game.turn(), move);
            if (game.handNumber() < saved.rounds().size()) game.nextHand();
        }
        return game;
    }
}
