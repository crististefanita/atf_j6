package ro.atf.holdem.android;

import ro.atf.holdem.ai.ContextualEquityPolicy;
import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.ai.DecisionPolicy;
import ro.atf.holdem.ai.TournamentPlayStyles;
import ro.atf.holdem.ai.TournamentPlayStyles.Style;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.HoldemHand;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.PublicShoveHistory;
import ro.atf.holdem.core.Street;
import ro.atf.holdem.core.Tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Offline play with a fixed human viewer and stable tournament opponent styles. */
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
    enum Difficulty { NORMAL, HARD }
    enum ActionSource { UNKNOWN, MANUAL, AUTO_PLAY, OPPONENT }
    record Turn(UUID handId, int revision) {}
    record Move(ActionType action, int amount) {}
    record Action(int actor, Move move, ActionSource source) {
        Action(int actor, Move move) {
            this(actor, move, ActionSource.UNKNOWN);
        }

        Action {
            if (actor < -1 || actor >= 6 || move == null || move.amount() < 0 || source == null
                    || (actor == -1 && source != ActionSource.UNKNOWN))
                throw new IllegalArgumentException("Invalid saved action");
        }
    }
    record AiSuggestion(Move move, String policyVersion, String explanation) {}
    record Saved(int seats, int dealer, long seed, boolean tournament, int blindLevelMinutes,
                 long elapsedSeconds, List<Long> roundStartElapsedSeconds, List<List<Action>> rounds,
                 String tournamentId, String humanPlayerId, boolean showWinChance, Difficulty difficulty) {
        Saved(int seats, int dealer, long seed, boolean tournament, int blindLevelMinutes,
              long elapsedSeconds, List<Long> roundStartElapsedSeconds, List<List<Action>> rounds) {
            this(seats, dealer, seed, tournament, blindLevelMinutes, elapsedSeconds, roundStartElapsedSeconds, rounds,
                    tournament ? UUID.nameUUIDFromBytes(("legacy-tournament:" + seed)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString() : "", "legacy-human", false,
                    Difficulty.NORMAL);
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
                    || difficulty == null || (!tournament && (showWinChance || difficulty == Difficulty.HARD))) {
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
    private final DecisionPolicy advisor;
    private final Map<Style, DecisionPolicy> hardOpponents;
    private final Difficulty difficulty;
    private final PublicShoveHistory shoveHistory;
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
    private boolean shortStackSeenAtMultiway;
    private boolean lastStackSeenAtMultiway;
    private boolean headsUpDeficitSeen;

    LocalHoldemGame(int seats, long seed) {
        this(seats, 0, seed);
    }

    LocalHoldemGame(int seats, int dealer, long seed) {
        this(seats, dealer, seed, false, 1, System::nanoTime);
    }

    static LocalHoldemGame table(int seats, int dealer, long seed, String humanPlayerId) {
        return new LocalHoldemGame(seats, dealer, seed, false, 1, System::nanoTime, humanPlayerId, "", false,
                Difficulty.NORMAL);
    }

    static Tournament.Rules tournamentRules(int seats, int blindLevelMinutes) {
        return new Tournament.Rules(seats, STARTING_STACK, blindLevelMinutes * 60L, TOURNAMENT_BLINDS);
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
        return tournament(seats, seed, blindLevelMinutes, humanPlayerId, showWinChance, Difficulty.NORMAL);
    }

    static LocalHoldemGame tournament(int seats, long seed, int blindLevelMinutes, String humanPlayerId,
                                      boolean showWinChance, Difficulty difficulty) {
        return new LocalHoldemGame(seats, 0, seed, true, blindLevelMinutes, System::nanoTime,
                humanPlayerId, UUID.randomUUID().toString(), showWinChance, difficulty);
    }

    private LocalHoldemGame(int seats, int dealer, long seed, boolean tournamentMode, int blindLevelMinutes,
                            LongSupplier monotonicNanos) {
        this(seats, dealer, seed, tournamentMode, blindLevelMinutes, monotonicNanos,
                "local-human", tournamentMode ? UUID.randomUUID().toString() : "", false, Difficulty.NORMAL);
    }

    static LocalHoldemGame tournament(int seats, long seed, Difficulty difficulty,
                                      LongSupplier monotonicNanos, int policySamples) {
        return new LocalHoldemGame(seats, 0, seed, true, 1, monotonicNanos,
                "calibration-player", "calibration-" + seed, false, difficulty, policySamples);
    }

    private LocalHoldemGame(int seats, int dealer, long seed, boolean tournamentMode, int blindLevelMinutes,
                            LongSupplier monotonicNanos, String humanPlayerId, String tournamentId,
                            boolean showWinChance, Difficulty difficulty) {
        this(seats, dealer, seed, tournamentMode, blindLevelMinutes, monotonicNanos, humanPlayerId, tournamentId,
                showWinChance, difficulty, ADVISOR_SAMPLES);
    }

    private LocalHoldemGame(int seats, int dealer, long seed, boolean tournamentMode, int blindLevelMinutes,
                            LongSupplier monotonicNanos, String humanPlayerId, String tournamentId,
                            boolean showWinChance, Difficulty difficulty, int policySamples) {
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
        this.difficulty = Objects.requireNonNull(difficulty);
        shoveHistory = difficulty == Difficulty.HARD ? new PublicShoveHistory(seats) : null;
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
        dealSeeds = new Random(seed);
        tournament = tournamentMode
                ? new Tournament(tournamentRules(seats, blindLevelMinutes), dealer)
                : null;
        hardOpponents = difficulty == Difficulty.HARD ? hardOpponentPolicies(policySamples) : Map.of();
        advisor = difficulty == Difficulty.HARD
                ? hardOpponents.get(TournamentPlayStyles.hardAutoPlayStyle())
                : new ContextualEquityPolicy(policySamples, 2, 8, 18, 60,
                        tournamentMode ? ContextualEquityPolicy.GameMode.TOURNAMENT
                                : ContextualEquityPolicy.GameMode.CASH);
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
        observeTournamentAchievementContext();
        handId = UUID.randomUUID();
        actions = new ArrayList<>();
        rounds.add(actions);
    }

    synchronized HoldemTableViewState view() { return hand.viewFor(human); }

    synchronized Turn turn() { return new Turn(handId, actions.size()); }

    synchronized boolean hasPassiveTurn() { return hand.actor() >= 0 && hand.actor() != human; }
    Difficulty difficulty() { return difficulty; }

    synchronized boolean isTournament() { return tournament != null; }

    boolean showsWinChance() { return showWinChance; }

    synchronized long displayNameSeed() { return seed; }

    private static boolean hasParticipantId(String id) {
        return id != null && id.codePoints().anyMatch(codePoint -> !Character.isWhitespace(codePoint));
    }

    synchronized String tournamentId() { return tournamentId; }

    synchronized String playerId(int physicalSeat) {
        if (physicalSeat < 0 || physicalSeat >= seats) throw new IllegalArgumentException("Invalid physical seat");
        return physicalSeat == human ? humanPlayerId : "builtin-ai-seat-" + physicalSeat;
    }

    synchronized List<Integer> finishingRanks() {
        return tournament == null ? List.of() : tournament.finishingRanks();
    }

    synchronized int settledRank() { return tournament == null ? 0 : tournament.settledRank(human); }
    synchronized List<ro.atf.holdem.core.PotAward> potAwards() { return hand.potAwards(); }

    synchronized GameplayAchievements.HandContext achievementContext() {
        int activePlayers = (int) openingStacks.stream().filter(stack -> stack > 0).count();
        int knockedOut = 0;
        var settledSeats = hand.viewFor(human).seats();
        for (int seat = 0; seat < seats; seat++) {
            if (openingStacks.get(seat) > 0 && settledSeats.get(seat).stack() == 0) knockedOut++;
        }
        return new GameplayAchievements.HandContext(seats, Math.max(1, activePlayers),
                openingStacks.get(human) > 0, shortStackSeenAtMultiway, lastStackSeenAtMultiway,
                headsUpDeficitSeen, tournamentTitleWasPlayedManually(), difficulty == Difficulty.HARD, knockedOut);
    }

    private void observeTournamentAchievementContext() {
        if (tournament == null || openingStacks.get(human) <= 0) return;
        int activePlayers = (int) openingStacks.stream().filter(stack -> stack > 0).count();
        long chipsInPlay = openingStacks.stream().mapToLong(Integer::longValue).sum();
        if (activePlayers >= 3) {
            long humanStack = openingStacks.get(human);
            shortStackSeenAtMultiway |= humanStack <= 10L * hand.level().bigBlind();
            lastStackSeenAtMultiway |= java.util.stream.IntStream.range(0, seats)
                    .filter(other -> other != human && openingStacks.get(other) > 0)
                    .allMatch(other -> openingStacks.get(other) > humanStack);
        } else if (activePlayers == 2) {
            headsUpDeficitSeen |= 3L * openingStacks.get(human) <= chipsInPlay;
        }
    }

    private boolean tournamentTitleWasPlayedManually() {
        boolean madeDecision = false;
        for (var round : rounds) {
            for (var action : round) {
                if (action.actor() == -1) return false;
                if (action.actor() != human) continue;
                madeDecision = true;
                if (action.source() != ActionSource.MANUAL) return false;
            }
        }
        return madeDecision;
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
        playHuman(expected, move, ActionSource.MANUAL);
    }

    synchronized void playDelegated(Turn expected, Move move) {
        playHuman(expected, move, ActionSource.AUTO_PLAY);
    }

    private void playHuman(Turn expected, Move move, ActionSource source) {
        if (!turn().equals(expected) || hand.actor() != human) throw new IllegalArgumentException("This turn is no longer active");
        apply(human, move, source);
    }

    synchronized void advancePassiveAction(Turn expected) {
        if (difficulty != Difficulty.NORMAL || !turn().equals(expected) || !hasPassiveTurn())
            throw new IllegalArgumentException("This automated turn is no longer active");
        int actor = hand.actor();
        var response = hand.legal().stream()
                .filter(option -> option.type() == ActionType.CHECK || option.type() == ActionType.CALL)
                .findFirst().orElseThrow(() -> new IllegalStateException("Passive policy has no legal response"));
        apply(actor, new Move(response.type(), response.amount()), ActionSource.OPPONENT);
    }

    private static Map<Style, DecisionPolicy> hardOpponentPolicies(int policySamples) {
        var policies = new EnumMap<Style, DecisionPolicy>(Style.class);
        for (Style style : Style.values()) {
            policies.put(style, style.policy(policySamples));
        }
        return Map.copyOf(policies);
    }

    Move suggestOpponent(Turn expected) {
        HoldemObservation observation;
        DecisionPolicy policy;
        synchronized (this) {
            requireHardOpponentTurn(expected);
            observation = observeActor();
            policy = hardOpponents.get(TournamentPlayStyles.hardOpponentStyle(seats, human, hand.actor()));
        }
        Decision decision = policy.decide(observation);
        return new Move(decision.action(), decision.amount());
    }

    synchronized void playOpponent(Turn expected, Move move) {
        requireHardOpponentTurn(expected);
        apply(hand.actor(), move, ActionSource.OPPONENT);
    }

    private void requireHardOpponentTurn(Turn expected) {
        if (difficulty != Difficulty.HARD || !turn().equals(expected) || !hasPassiveTurn())
            throw new IllegalArgumentException("This Hard opponent turn is no longer active");
    }

    private void apply(int actor, Move move, ActionSource source) {
        boolean opponent = actor != human;
        if (source != ActionSource.UNKNOWN && opponent != (source == ActionSource.OPPONENT))
            throw new IllegalArgumentException("Saved action source disagrees with the acting seat");
        var before = shoveHistory == null ? null : hand.observeActor();
        hand.act(actor, move.action(), move.amount());
        if (shoveHistory != null) {
            shoveHistory.recordAccepted(before, move.action(), move.amount());
            if (hand.street() == Street.COMPLETE) shoveHistory.settleHand();
        }
        actions.add(new Action(actor, move, source));
    }

    private HoldemObservation observeActor() {
        return shoveHistory == null ? hand.observeActor() : hand.observeActor(shoveHistory.snapshot());
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
        return observeActor();
    }

    void autoPlayAction(Turn expected) {
        var observation = snapshotForAdvice(expected);
        Decision decision = advisor.decide(observation);
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("AI action interrupted");
        playDelegated(expected, new Move(decision.action(), decision.amount()));
    }

    /** Finishes one hand by composing the human auto-play policy with the configured opponents. */
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
            }
            if (!passive) autoPlayAction(currentTurn);
            else if (difficulty == Difficulty.HARD) playOpponent(currentTurn, suggestOpponent(currentTurn));
            else advancePassiveAction(currentTurn);
        }
    }

    synchronized Saved save() {
        return new Saved(seats, initialDealer, seed, isTournament(), blindLevelMinutes,
                elapsedTournamentSeconds(), roundStartElapsedSeconds, rounds, tournamentId, humanPlayerId, showWinChance,
                difficulty);
    }

    static LocalHoldemGame restore(Saved saved) {
        return restore(saved, System::nanoTime);
    }

    static LocalHoldemGame restore(Saved saved, LongSupplier monotonicNanos) {
        var game = new LocalHoldemGame(saved.seats(), saved.dealer(), saved.seed(), saved.tournament(),
                saved.blindLevelMinutes(), monotonicNanos, saved.humanPlayerId(), saved.tournamentId(),
                saved.showWinChance(), saved.difficulty());
        for (int index = 0; index < saved.rounds().size(); index++) {
            if (index > 0) game.nextHandAtElapsedSeconds(saved.roundStartElapsedSeconds().get(index));
            var round = saved.rounds().get(index);
            for (Action action : round) {
                if (action.actor() == -1) {
                    while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
                    if (game.hand.actor() != game.human)
                        throw new IllegalArgumentException("Legacy replay did not reach the human turn");
                    game.apply(game.human, action.move(), ActionSource.UNKNOWN);
                } else {
                    if (game.hand.actor() != action.actor())
                        throw new IllegalArgumentException("Saved actor order differs from the engine");
                    game.apply(action.actor(), action.move(), action.source());
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
