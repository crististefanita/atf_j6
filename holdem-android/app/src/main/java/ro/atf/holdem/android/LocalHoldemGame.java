package ro.atf.holdem.android;

import ro.atf.holdem.ai.ContextualEquityPolicy;
import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.HoldemHand;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;
import ro.atf.holdem.core.Tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Offline play with passive opponents and a fixed human viewer. */
final class LocalHoldemGame {
    static final List<Integer> TABLE_SIZES = List.of(2, 4, 5, 6);
    static final List<Integer> TOURNAMENT_SIZES = List.of(4, 5, 6);
    static final List<Integer> BLIND_LEVEL_MINUTES = List.of(1, 2, 3, 5);
    private static final int STARTING_STACK = 2000;
    private static final BlindLevel TRAINING_BLINDS = new BlindLevel(10, 20, 20);
    private static final List<BlindLevel> TOURNAMENT_BLINDS = List.of(
            new BlindLevel(10, 20, 20), new BlindLevel(15, 30, 30), new BlindLevel(20, 40, 50),
            new BlindLevel(30, 60, 70), new BlindLevel(50, 100, 120), new BlindLevel(75, 150, 180),
            new BlindLevel(100, 200, 250), new BlindLevel(150, 300, 350), new BlindLevel(200, 400, 500));
    private static final int ADVISOR_SAMPLES = 512;

    enum Outcome { WON, LOST }
    record Turn(UUID handId, int revision) {}
    record Move(ActionType action, int amount) {}
    record Action(int actor, Move move) {
        Action {
            if (actor < -1 || actor >= 6 || move == null || move.amount() < 0)
                throw new IllegalArgumentException("Invalid saved action");
        }
    }
    record AiSuggestion(Move move, String policyVersion, String explanation) {}
    record Saved(int seats, int dealer, long seed, boolean tournament, int blindLevelMinutes,
                 long elapsedSeconds, List<Long> roundStartElapsedSeconds, List<List<Action>> rounds,
                 String tournamentId, String humanPlayerId, boolean showWinChance) {
        Saved(int seats, int dealer, long seed, boolean tournament, int blindLevelMinutes,
              long elapsedSeconds, List<Long> roundStartElapsedSeconds, List<List<Action>> rounds) {
            this(seats, dealer, seed, tournament, blindLevelMinutes, elapsedSeconds, roundStartElapsedSeconds, rounds,
                    tournament ? UUID.nameUUIDFromBytes(("legacy-tournament:" + seed)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString() : "", "legacy-human", false);
        }
        Saved {
            var copy = new ArrayList<List<Action>>(rounds.size());
            for (var round : rounds) copy.add(List.copyOf(round));
            rounds = List.copyOf(copy);
            roundStartElapsedSeconds = List.copyOf(roundStartElapsedSeconds);
            if (rounds.isEmpty() || (!tournament && rounds.size() != 1)
                    || rounds.size() != roundStartElapsedSeconds.size()
                    || elapsedSeconds < 0 || !BLIND_LEVEL_MINUTES.contains(blindLevelMinutes)
                    || tournament != hasParticipantId(tournamentId) || !hasParticipantId(humanPlayerId)
                    || (showWinChance && !tournament)) {
                throw new IllegalArgumentException("Invalid saved rounds");
            }
            long previous = -1;
            for (long roundStart : roundStartElapsedSeconds) {
                if (roundStart < 0 || roundStart < previous || roundStart > elapsedSeconds)
                    throw new IllegalArgumentException("Invalid tournament round time");
                previous = roundStart;
            }
            if (roundStartElapsedSeconds.get(0) != 0)
                throw new IllegalArgumentException("The first hand must start at tournament time zero");
            for (var round : rounds)
                for (var action : round)
                    if (action.actor() >= seats) throw new IllegalArgumentException("Saved actor is outside the table");
        }
    }

    private final int seats;
    private final int initialDealer;
    private final long seed;
    private final String tournamentId;
    private final String humanPlayerId;
    private final int blindLevelMinutes;
    private final LongSupplier monotonicNanos;
    private final Tournament tournament;
    private final Random dealSeeds;
    private final ContextualEquityPolicy advisor;
    private final boolean showWinChance;
    private final List<List<Action>> rounds = new ArrayList<>();
    private final List<Long> roundStartElapsedSeconds = new ArrayList<>();
    private UUID handId;
    private HoldemHand hand;
    private List<Action> actions;
    private List<Integer> openingStacks;
    private int human;
    private int currentLevelIndex;
    private long tournamentElapsedBaseSeconds;
    private long tournamentClockAnchorNanos;
    private boolean tournamentClockRunning = true;

    LocalHoldemGame(int seats, long seed) {
        this(seats, 0, seed);
    }

    LocalHoldemGame(int seats, int dealer, long seed) {
        this(seats, dealer, seed, false, 1, System::nanoTime);
    }

    static LocalHoldemGame tournament(int seats, long seed) {
        return tournament(seats, seed, 1);
    }

    static LocalHoldemGame tournament(int seats, long seed, int blindLevelMinutes) {
        return new LocalHoldemGame(seats, 0, seed, true, blindLevelMinutes, System::nanoTime);
    }

    static LocalHoldemGame tournament(int seats, long seed, int blindLevelMinutes, LongSupplier monotonicNanos) {
        return new LocalHoldemGame(seats, 0, seed, true, blindLevelMinutes, monotonicNanos);
    }

    static LocalHoldemGame tournament(int seats, long seed, int blindLevelMinutes, String humanPlayerId) {
        return tournament(seats, seed, blindLevelMinutes, humanPlayerId, false);
    }

    static LocalHoldemGame tournament(int seats, long seed, int blindLevelMinutes, String humanPlayerId,
                                      boolean showWinChance) {
        return new LocalHoldemGame(seats, 0, seed, true, blindLevelMinutes, System::nanoTime,
                humanPlayerId, UUID.randomUUID().toString(), showWinChance);
    }

    private LocalHoldemGame(int seats, int dealer, long seed, boolean tournamentMode, int blindLevelMinutes,
                            LongSupplier monotonicNanos) {
        this(seats, dealer, seed, tournamentMode, blindLevelMinutes, monotonicNanos,
                "local-human", tournamentMode ? UUID.randomUUID().toString() : "", false);
    }

    private LocalHoldemGame(int seats, int dealer, long seed, boolean tournamentMode, int blindLevelMinutes,
                            LongSupplier monotonicNanos, String humanPlayerId, String tournamentId,
                            boolean showWinChance) {
        if (!(tournamentMode ? TOURNAMENT_SIZES : TABLE_SIZES).contains(seats)) {
            throw new IllegalArgumentException("Unsupported training table size: " + seats);
        }
        if (!BLIND_LEVEL_MINUTES.contains(blindLevelMinutes))
            throw new IllegalArgumentException("Unsupported blind level interval: " + blindLevelMinutes);
        this.seats = seats;
        initialDealer = dealer;
        this.seed = seed;
        this.humanPlayerId = Objects.requireNonNull(humanPlayerId, "humanPlayerId");
        this.tournamentId = Objects.requireNonNull(tournamentId, "tournamentId");
        if (!hasParticipantId(humanPlayerId) || tournamentMode != hasParticipantId(tournamentId))
            throw new IllegalArgumentException("Invalid tournament participant identity");
        this.blindLevelMinutes = blindLevelMinutes;
        this.showWinChance = showWinChance;
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
        dealSeeds = new Random(seed);
        tournament = tournamentMode
                ? new Tournament(new Tournament.Rules(seats, STARTING_STACK, blindLevelMinutes * 60L,
                        TOURNAMENT_BLINDS), dealer)
                : null;
        advisor = new ContextualEquityPolicy(ADVISOR_SAMPLES, 2, 8, 18, 60,
                tournamentMode ? ContextualEquityPolicy.GameMode.TOURNAMENT : ContextualEquityPolicy.GameMode.CASH);
        tournamentClockAnchorNanos = monotonicNanos.getAsLong();
        startHand();
    }

    private void startHand() {
        startHandAtElapsedSeconds(tournament == null ? 0 : elapsedTournamentSeconds());
    }

    private void startHandAtElapsedSeconds(long elapsedSeconds) {
        openingStacks = tournament == null ? Collections.nCopies(seats, STARTING_STACK) : tournament.stacks();
        if (tournament == null) {
            hand = new HoldemHand(1, openingStacks, initialDealer, TRAINING_BLINDS, HoldemHand.shuffledDeck(seed));
            currentLevelIndex = 0;
        } else {
            var round = tournament.startHand(elapsedSeconds, dealSeeds.nextLong());
            hand = round.hand();
            currentLevelIndex = round.levelIndex();
            tournamentElapsedBaseSeconds = elapsedSeconds;
            tournamentClockAnchorNanos = monotonicNanos.getAsLong();
            tournamentClockRunning = true;
        }
        roundStartElapsedSeconds.add(elapsedSeconds);
        if (rounds.isEmpty()) human = seats == 2 ? 0 : hand.actor();
        handId = UUID.randomUUID();
        actions = new ArrayList<>();
        rounds.add(actions);
    }

    synchronized HoldemTableViewState view() { return hand.viewFor(human); }

    synchronized Turn turn() { return new Turn(handId, actions.size()); }

    synchronized boolean hasPassiveTurn() { return hand.actor() >= 0 && hand.actor() != human; }

    synchronized boolean isTournament() { return tournament != null; }

<<<<<<< Updated upstream
    boolean showsWinChance() { return showWinChance; }

    synchronized long displayNameSeed() { return seed; }

    private static boolean hasParticipantId(String id) {
        return id != null && id.codePoints().anyMatch(codePoint -> !Character.isWhitespace(codePoint));
    }

=======
    synchronized long displayNameSeed() { return seed; }

>>>>>>> Stashed changes
    synchronized String tournamentId() { return tournamentId; }

    synchronized String playerId(int physicalSeat) {
        if (physicalSeat < 0 || physicalSeat >= seats) throw new IllegalArgumentException("Invalid physical seat");
        return physicalSeat == human ? humanPlayerId : "builtin-ai-seat-" + physicalSeat;
    }

    synchronized List<Integer> finishingRanks() {
        return tournament == null ? List.of() : tournament.finishingRanks();
    }

    synchronized int handNumber() { return rounds.size(); }

    synchronized int blindLevelNumber() { return currentLevelIndex + 1; }

    synchronized int blindLevelMinutes() { return blindLevelMinutes; }

    synchronized BlindLevel blindLevel() { return hand.level(); }

    synchronized long elapsedTournamentSeconds() {
        if (!isTournament()) return 0;
        long runningNanos = tournamentClockRunning
                ? Math.max(0, monotonicNanos.getAsLong() - tournamentClockAnchorNanos) : 0;
        return tournamentElapsedBaseSeconds + runningNanos / 1_000_000_000L;
    }

    synchronized void pauseClock() {
        if (!isTournament() || !tournamentClockRunning) return;
        tournamentElapsedBaseSeconds = elapsedTournamentSeconds();
        tournamentClockRunning = false;
    }

    synchronized void resumeClock() {
        if (!isTournament() || tournamentClockRunning) return;
        tournamentClockAnchorNanos = monotonicNanos.getAsLong();
        tournamentClockRunning = true;
    }

    synchronized boolean eliminated(int seat) {
        return isTournament() && (openingStacks.get(seat) == 0 || (hand.street() == Street.COMPLETE && hand.stacks().get(seat) == 0));
    }

    synchronized Outcome outcome() {
        if (!isTournament() || hand.street() != Street.COMPLETE) return null;
        if (hand.stacks().stream().filter(stack -> stack > 0).count() != 1) return null;
        tournament.finish();
        return tournament.finishingRanks().get(human) == 1 ? Outcome.WON : Outcome.LOST;
    }

    synchronized void nextHand() {
        nextHandAtElapsedSeconds(elapsedTournamentSeconds());
    }

    synchronized void nextHandAtElapsedSeconds(long elapsedSeconds) {
        if (!isTournament() || hand.street() != Street.COMPLETE || outcome() != null) {
            throw new IllegalArgumentException("No next tournament hand");
        }
        startHandAtElapsedSeconds(elapsedSeconds);
    }

    synchronized void play(Turn expected, Move move) {
        if (!turn().equals(expected) || hand.actor() != human) throw new IllegalArgumentException("This turn is no longer active");
        apply(human, move);
    }

    synchronized void advancePassiveAction(Turn expected) {
        if (!turn().equals(expected) || !hasPassiveTurn())
            throw new IllegalArgumentException("This automated turn is no longer active");
        int actor = hand.actor();
        var response = hand.legal().stream()
                .filter(option -> option.type() == ActionType.CHECK || option.type() == ActionType.CALL)
                .findFirst().orElseThrow(() -> new IllegalStateException("Passive policy has no legal response"));
        apply(actor, new Move(response.type(), response.amount()));
    }

    private void apply(int actor, Move move) {
        hand.act(actor, move.action(), move.amount());
        actions.add(new Action(actor, move));
    }

    AiSuggestion suggest(Turn expected) {
        var observation = snapshotForAdvice(expected);
        Decision decision = advisor.decide(observation);
        synchronized (this) {
            if (!turn().equals(expected) || hand.actor() != human || hand.street() == Street.COMPLETE)
                throw new IllegalArgumentException("The human turn changed while AI advice was calculated");
        }
        return new AiSuggestion(new Move(decision.action(), decision.amount()),
                decision.policyVersion(), decision.explanation());
    }

    private synchronized HoldemObservation snapshotForAdvice(Turn expected) {
        if (!turn().equals(expected) || hand.actor() != human || hand.street() == Street.COMPLETE)
            throw new IllegalArgumentException("AI advice is only available for the current human turn");
        return hand.observeActor();
    }

    void autoPlayAction(Turn expected) {
        var observation = snapshotForAdvice(expected);
        Decision decision = advisor.decide(observation);
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("AI action interrupted");
        synchronized (this) {
            if (!turn().equals(expected) || hand.actor() != human || hand.street() == Street.COMPLETE)
                throw new IllegalArgumentException("The human turn changed during AI action");
            play(expected, new Move(decision.action(), decision.amount()));
        }
    }

    /** Finishes one hand by composing legal AI decisions with the existing passive-opponent policy. */
    void autoPlayHand(Turn expected) {
        synchronized (this) {
            if (!turn().equals(expected) || hand.actor() != human || hand.street() == Street.COMPLETE)
                throw new IllegalArgumentException("AI takeover requires the current human turn");
        }
        int steps = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("AI takeover interrupted");
            if (++steps > 64) throw new IllegalStateException("AI takeover exceeded the hand action limit");
            Turn currentTurn;
            boolean passive;
            synchronized (this) {
                if (hand.street() == Street.COMPLETE) return;
                currentTurn = turn();
                passive = hand.actor() != human;
                if (passive) advancePassiveAction(currentTurn);
            }
            if (!passive) autoPlayAction(currentTurn);
        }
    }

    synchronized Saved save() {
        return new Saved(seats, initialDealer, seed, isTournament(), blindLevelMinutes,
                elapsedTournamentSeconds(), roundStartElapsedSeconds, rounds, tournamentId, humanPlayerId, showWinChance);
    }

    static LocalHoldemGame restore(Saved saved) {
        return restore(saved, System::nanoTime);
    }

    static LocalHoldemGame restore(Saved saved, LongSupplier monotonicNanos) {
        var game = new LocalHoldemGame(saved.seats(), saved.dealer(), saved.seed(), saved.tournament(),
                saved.blindLevelMinutes(), monotonicNanos, saved.humanPlayerId(), saved.tournamentId(),
                saved.showWinChance());
        for (int index = 0; index < saved.rounds().size(); index++) {
            if (index > 0) game.nextHandAtElapsedSeconds(saved.roundStartElapsedSeconds().get(index));
            var round = saved.rounds().get(index);
            for (Action action : round) {
                if (action.actor() == -1) {
                    while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
                    if (game.hand.actor() != game.human)
                        throw new IllegalArgumentException("Legacy replay did not reach the human turn");
                    game.play(game.turn(), action.move());
                } else {
                    if (game.hand.actor() != action.actor())
                        throw new IllegalArgumentException("Saved actor order differs from the engine");
                    game.apply(action.actor(), action.move());
                }
            }
        }
        if (game.isTournament()) {
            game.tournamentElapsedBaseSeconds = saved.elapsedSeconds();
            game.tournamentClockAnchorNanos = monotonicNanos.getAsLong();
            game.tournamentClockRunning = true;
        }
        return game;
    }
}
