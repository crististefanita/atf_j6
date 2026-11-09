package ro.atf.holdem.core;

import java.util.*;
import java.util.stream.Collectors;

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
    private final int[] finishingRanks;
    private int hands, button, previousBigBlind;
    private long elapsed;
    private HoldemHand current;
    private boolean finalized;

    public Tournament(Rules rules, int initialButton) {
        this.rules = Objects.requireNonNull(rules);
        if (initialButton < 0 || initialButton >= rules.seats()) throw new IllegalArgumentException("Invalid button");
        button = initialButton;
        stacks = Collections.nCopies(rules.seats(), rules.startingStack());
        finishingRanks = new int[rules.seats()];
    }

    public List<Integer> stacks() { return current == null ? stacks : current.stacks(); }
    public List<Integer> finishingRanks() {
        return List.copyOf(Arrays.stream(finishingRanks).boxed().collect(Collectors.toList()));
    }

    public Round startHand(long elapsedSeconds, long dealSeed) {
        if (current != null && current.street() != Street.COMPLETE) throw new IllegalStateException("Hand still in progress");
        if (elapsedSeconds < elapsed) throw new IllegalArgumentException("Tournament time cannot go backwards");
        List<Integer> settled = stacks();
        if (hands > 0) recordEliminations(settled);
        int alive = (int) settled.stream().filter(n -> n > 0).count();
        if (alive < 2) {
            if (alive == 1) markChampion(settled);
            throw new IllegalStateException("Tournament finished");
        }
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

    /** Finalizes placements after the last hand; calling it again is safe. */
    public void finish() {
        if (current == null || current.street() != Street.COMPLETE)
            throw new IllegalStateException("The current hand must be settled before the tournament can finish");
        if (finalized) return;
        List<Integer> settled = current.stacks();
        recordEliminations(settled);
        if (settled.stream().filter(stack -> stack > 0).count() != 1)
            throw new IllegalStateException("A tournament ends with exactly one funded player");
        markChampion(settled);
    }

    /** The viewer's final rank when locked by a settled hand, including shared places. */
    public int settledRank(int seat) {
        Objects.checkIndex(seat, rules.seats());
        if (finishingRanks[seat] != 0) return finishingRanks[seat];
        if (current == null || current.street() != Street.COMPLETE) return 0;
        var settled = current.stacks();
        int rank = eliminationRank(settled);
        if (settled.get(seat) > 0) return rank == 2 ? 1 : 0;
        return stacks.get(seat) > 0 ? rank : 0;
    }

    private static int eliminationRank(List<Integer> settled) {
        return 1 + (int) settled.stream().filter(stack -> stack > 0).count();
    }

    private void recordEliminations(List<Integer> settled) {
        int rank = eliminationRank(settled);
        for (int seat = 0; seat < stacks.size(); seat++)
            if (stacks.get(seat) > 0 && settled.get(seat) == 0) finishingRanks[seat] = rank;
    }

    private void markChampion(List<Integer> settled) {
        for (int seat = 0; seat < settled.size(); seat++) {
            if (settled.get(seat) > 0) {
                finishingRanks[seat] = 1;
                finalized = true;
                return;
            }
        }
        throw new IllegalStateException("Tournament has no champion");
    }

    private static int nextFunded(List<Integer> stacks, int after) {
        for (int offset = 1; offset < stacks.size(); offset++) {
            int seat = (after + offset) % stacks.size();
            if (stacks.get(seat) > 0) return seat;
        }
        throw new IllegalStateException("No funded seat");
    }
}
