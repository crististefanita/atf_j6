package ro.atf.holdem.core;

import java.util.*;

public final class Tournament {
    public record Rules(int seats, int startingStack, long levelSeconds, List<BlindLevel> levels) {
        public Rules {
            levels = List.copyOf(levels);
            if (seats < 4 || seats > 6 || startingStack < 1 || (long) seats * startingStack > Integer.MAX_VALUE
                    || levelSeconds < 1 || levels.isEmpty()) throw new IllegalArgumentException("Invalid tournament rules");
            for (int i = 1; i < levels.size(); i++) {
                BlindLevel previous = levels.get(i - 1);
                BlindLevel next = levels.get(i);
                if (next.smallBlind() <= previous.smallBlind() || next.bigBlind() <= previous.bigBlind()
                        || next.minimumOpen() <= previous.minimumOpen())
                    throw new IllegalArgumentException("Each level must increase both blinds and the minimum opening");
            }
        }
    }

    public record Round(int number, long elapsedSeconds, int eliminated, int levelIndex, HoldemHand hand) {}

    private final Rules rules;
    private List<Integer> stacks;
    private int hands, button, previousBigBlind;
    private long elapsed;
    private HoldemHand current;

    public Tournament(Rules rules, int initialButton) {
        this.rules = Objects.requireNonNull(rules);
        if (initialButton < 0 || initialButton >= rules.seats()) throw new IllegalArgumentException("Invalid button");
        button = initialButton;
        stacks = Collections.nCopies(rules.seats(), rules.startingStack());
    }

    public List<Integer> stacks() { return current == null ? stacks : current.stacks(); }

    public Round startHand(long elapsedSeconds, long dealSeed) {
        if (current != null && current.street() != Street.COMPLETE) throw new IllegalStateException("Hand still in progress");
        if (elapsedSeconds < elapsed) throw new IllegalArgumentException("Tournament time cannot go backwards");
        List<Integer> settled = stacks();
        int alive = (int) settled.stream().filter(n -> n > 0).count();
        if (alive < 2) throw new IllegalStateException("Tournament finished");
        if (hands > 0) {
            if (alive == 2) {
                int nextBigBlind = nextFunded(settled, previousBigBlind);
                button = nextFunded(settled, nextBigBlind);
            } else {
                button = nextFunded(settled, button);
            }
        }
        stacks = settled;
        elapsed = elapsedSeconds;
        int eliminated = rules.seats() - alive;
        long timedLevels = elapsedSeconds / rules.levelSeconds();
        int last = rules.levels().size() - 1;
        int index = (int) Math.min(last, Math.min(last, timedLevels) + eliminated);
        current = new HoldemHand(++hands, stacks, button, rules.levels().get(index), HoldemHand.shuffledDeck(dealSeed));
        previousBigBlind = current.bigBlindSeat();
        return new Round(hands, elapsed, eliminated, index, current);
    }

    private static int nextFunded(List<Integer> stacks, int after) {
        for (int offset = 1; offset < stacks.size(); offset++) {
            int seat = (after + offset) % stacks.size();
            if (stacks.get(seat) > 0) return seat;
        }
        throw new IllegalStateException("No funded seat");
    }
}
