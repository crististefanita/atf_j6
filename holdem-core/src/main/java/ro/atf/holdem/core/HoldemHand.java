package ro.atf.holdem.core;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** One No-Limit hand. Amounts are chips added by the actor now. */
public final class HoldemHand {
    private final int number, button, smallBlindSeat, bigBlindSeat;
    private final BlindLevel level;
    private final List<Card> deck;
    private final List<List<Card>> hole;
    private final List<Integer> seats;
    private final List<Card> board = new ArrayList<>(5);
    private final List<String> history = new ArrayList<>();
    private final int[] stacks, paid, streetPaid, actedAt;
    private final boolean[] folded;
    private int cursor, actor, price, increment;
    private int[] awards;
    private Street street = Street.PREFLOP;

    public HoldemHand(int number, List<Integer> initial, int button, BlindLevel level, List<Card> deck) {
        this.number = number;
        this.button = button;
        this.level = Objects.requireNonNull(level);
        this.deck = List.copyOf(deck);
        if (this.deck.size() != 52 || new HashSet<>(this.deck).size() != 52)
            throw new IllegalArgumentException("Expected a complete deck");
        stacks = initial.stream().mapToInt(Integer::intValue).toArray();
        if (number < 1 || stacks.length < 2 || stacks.length > 6 || button < 0
                || button >= stacks.length || stacks[button] < 1 || Arrays.stream(stacks).anyMatch(n -> n < 0)
                || Arrays.stream(stacks).asLongStream().sum() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid table");
        seats = IntStream.range(0, stacks.length).filter(i -> stacks[i] > 0).boxed().collect(Collectors.toList());
        if (seats.size() < 2) throw new IllegalArgumentException("Expected at least two funded seats");
        history.add("SEATS " + seats);
        paid = new int[stacks.length];
        streetPaid = new int[stacks.length];
        actedAt = new int[stacks.length];
        Arrays.fill(actedAt, -1);
        folded = new boolean[stacks.length];
        hole = new ArrayList<>(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            hole.add(new ArrayList<>(2));
            folded[i] = stacks[i] == 0;
        }
        smallBlindSeat = seats.size() == 2 ? button : next(button, i -> !folded[i]);
        bigBlindSeat = next(smallBlindSeat, i -> !folded[i]);
        int firstDealt = seats.size() == 2 ? bigBlindSeat : smallBlindSeat;
        for (int round = 0; round < 2; round++) {
            int seat = firstDealt;
            for (int i = 0; i < seats.size(); i++) {
                hole.get(seat).add(this.deck.get(cursor++));
                seat = next(seat, s -> !folded[s]);
            }
        }
        post(smallBlindSeat, level.smallBlind(), "SB");
        post(bigBlindSeat, level.bigBlind(), "BB");
        price = level.bigBlind();
        increment = level.bigBlind();
        advance(bigBlindSeat);
    }

    public static List<Card> shuffledDeck(long seed) {
        var cards = new ArrayList<Card>(52);
        for (var suit : Card.Suit.values()) for (var rank : Card.Rank.values()) cards.add(new Card(rank, suit));
        var random = new SplittableRandom(seed);
        for (int i = cards.size() - 1; i > 0; i--) Collections.swap(cards, i, random.nextInt(i + 1));
        return List.copyOf(cards);
    }

    public int actor() { return actor; }
    public int button() { return button; }
    public int bigBlindSeat() { return bigBlindSeat; }
    public Street street() { return street; }
    public BlindLevel level() { return level; }
    public List<Integer> stacks() { return List.copyOf(Arrays.stream(stacks).boxed().collect(Collectors.toList())); }
    public List<Integer> contributions() { return List.copyOf(Arrays.stream(paid).boxed().collect(Collectors.toList())); }
    public List<Card> board() { return List.copyOf(board); }
    public List<String> history() { return List.copyOf(history); }

    public HoldemObservation observeActor() {
        return observeActor(List.of());
    }

    public HoldemObservation observeActor(List<PublicOpponentTendency> publicTendencies) {
        if (actor < 0) throw new IllegalStateException("Hand is complete");
        Objects.requireNonNull(publicTendencies, "publicTendencies");
        int localSeat = seats.indexOf(actor);
        int position = Math.floorMod(localSeat - seats.indexOf(button), seats.size());
        var tendencyBySeat = new HashMap<Integer, PublicOpponentTendency>();
        for (var tendency : publicTendencies) {
            Objects.requireNonNull(tendency, "public tendency");
            if (tendency.physicalSeat() >= stacks.length)
                throw new IllegalArgumentException("Public tendency seat is outside the table: " + tendency.physicalSeat());
            if (tendencyBySeat.put(tendency.physicalSeat(), tendency) != null)
                throw new IllegalArgumentException("Duplicate public tendency seat " + tendency.physicalSeat());
        }
        var visibleTendencies = seats.stream().map(seat -> tendencyBySeat.getOrDefault(seat,
                new PublicOpponentTendency(seat, 0, 0))).toList();
        return new HoldemObservation(number, localSeat, seats.size(), count(i -> !folded[i]) - 1,
                street, List.copyOf(hole.get(actor)),
                board(), Arrays.stream(paid).sum(), toCall(), stacks[actor], position,
                seats.stream().map(i -> stacks[i]).collect(Collectors.toList()), legal(), history(), actor,
                level.bigBlind(), seats.stream().map(i -> paid[i]).collect(Collectors.toList()), visibleTendencies);
    }

    public HoldemTableViewState viewFor(int viewer) {
        if (!seats.contains(viewer)) throw new IllegalArgumentException("Viewer did not enter this hand");
        var visibleSeats = new ArrayList<HoldemTableViewState.Seat>(stacks.length);
        for (int seat = 0; seat < stacks.length; seat++) {
            visibleSeats.add(new HoldemTableViewState.Seat(stacks[seat], paid[seat], folded[seat],
                    awards == null ? 0 : awards[seat]));
        }
        return new HoldemTableViewState(viewer, actor, button, smallBlindSeat, bigBlindSeat, street, Arrays.stream(paid).sum(),
                hole.get(viewer), board, visibleSeats, viewer == actor ? legal() : List.of());
    }

    public List<LegalAction> legal() {
        if (actor < 0) return List.of();
        var actions = new ArrayList<LegalAction>();
        int call = toCall();
        int stack = stacks[actor];
        if (call == 0) {
            actions.add(LegalAction.noAmount(ActionType.CHECK));
        } else {
            actions.add(LegalAction.noAmount(ActionType.FOLD));
            int amount = Math.min(call, stack);
            actions.add(new LegalAction(ActionType.CALL, amount, amount, amount));
        }
        boolean canRaise = count(this::canAct) > 1 && (actedAt[actor] < 0 || price - actedAt[actor] >= increment);
        long minimum = (long) call + increment;
        if (canRaise && minimum <= stack) {
            actions.add(new LegalAction(price == 0 ? ActionType.BET : ActionType.RAISE, (int) minimum, (int) minimum, stack));
        }
        if (stack <= call || canRaise) actions.add(new LegalAction(ActionType.ALL_IN, stack, stack, stack));
        return List.copyOf(actions);
    }

    public void act(int seat, ActionType action, int amount) {
        if (actor < 0 || seat != actor || legal().stream().noneMatch(option -> option.type() == action
                && amount >= option.minAmount() && amount <= option.maxAmount()))
            throw new IllegalArgumentException("Illegal action for seat " + seat + ": " + action + " " + amount);
        if (action == ActionType.FOLD) folded[seat] = true;
        moveChips(seat, amount);
        if (streetPaid[seat] > price) {
            int raise = streetPaid[seat] - price;
            if (raise >= increment) increment = raise;
            price = streetPaid[seat];
        }
        actedAt[seat] = price;
        history.add(street + " " + seat + " " + action + " " + amount);
        advance(seat);
    }

    private void post(int seat, int nominal, String blind) {
        int amount = Math.min(stacks[seat], nominal);
        moveChips(seat, amount);
        history.add(blind + " " + seat + " " + amount);
    }

    private void moveChips(int seat, int amount) {
        stacks[seat] -= amount;
        streetPaid[seat] += amount;
        paid[seat] += amount;
    }

    private int toCall() { return Math.max(0, price - streetPaid[actor]); }
    private boolean canAct(int seat) { return !folded[seat] && stacks[seat] > 0; }
    private int count(IntPredicate test) { return (int) IntStream.range(0, stacks.length).filter(test).count(); }

    private int next(int after, IntPredicate test) {
        for (int offset = 1; offset <= stacks.length; offset++) {
            int seat = (after + offset) % stacks.length;
            if (test.test(seat)) return seat;
        }
        return -1;
    }

    private void advance(int previousActor) {
        while (true) {
            if (count(i -> !folded[i]) == 1) { settle(false); return; }
            int able = count(this::canAct);
            if (able < 2) {
                // With nobody able to raise, only an actual unmatched contribution requires a response.
                price = Arrays.stream(streetPaid).max().orElseThrow(IllegalStateException::new);
                actor = next(previousActor, i -> canAct(i) && streetPaid[i] < price);
            } else {
                actor = next(previousActor, i -> canAct(i) && actedAt[i] < price);
            }
            if (actor >= 0) return;
            if (street == Street.RIVER) { settle(true); return; }
            cursor++; // burn
            int cards = street == Street.PREFLOP ? 3 : 1;
            for (int i = 0; i < cards; i++) board.add(deck.get(cursor++));
            street = Street.values()[street.ordinal() + 1];
            history.add(street + " " + board.stream().map(Card::label).collect(Collectors.toList()));
            Arrays.fill(streetPaid, 0);
            Arrays.fill(actedAt, -1);
            price = 0;
            increment = level.minimumOpen();
            previousActor = button;
        }
    }

    private void settle(boolean showdown) {
        int[] ranks = new int[stacks.length];
        Arrays.fill(ranks, -1);
        for (int i : seats) {
            if (folded[i]) continue;
            if (showdown) {
                var cards = new ArrayList<>(board);
                cards.addAll(hole.get(i));
                ranks[i] = HandRank.bestOf(cards);
            } else {
                ranks[i] = 0;
            }
        }
        var settlement = PotSettlement.settle(paid, ranks, button);
        awards = settlement.awards();
        for (int i = 0; i < stacks.length; i++) stacks[i] += awards[i];
        history.addAll(settlement.journal());
        history.add((showdown ? "SHOWDOWN " : "FOLD_WIN ") + Arrays.toString(awards));
        actor = -1;
        street = Street.COMPLETE;
    }
}
