package ro.atf.holdem.core;

import java.util.*;

public final class HandRank {
    private HandRank() {}

    public enum Category {
        HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH,
        FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH
    }

    public static Category categoryOf(List<Card> cards) {
        return categoryOfScore(bestOf(cards));
    }

    public static Category categoryOfScore(int score) {
        int category = score >>> 20;
        if (category >= Category.values().length)
            throw new IllegalArgumentException("Unknown packed hand category: " + category);
        return Category.values()[category];
    }

    /** Higher scores win. Suits never break ties. */
    public static int bestOf(List<Card> cards) {
        if (cards.size() < 5 || cards.size() > 7 || new HashSet<>(cards).size() != cards.size())
            throw new IllegalArgumentException("Expected five to seven distinct cards");
        int best = 0;
        int limit = 1 << cards.size();
        for (int mask = 0; mask < limit; mask++) {
            if (Integer.bitCount(mask) != 5) continue;
            int[] counts = new int[15];
            Card.Suit suit = null;
            boolean flush = true;
            for (int i = 0; i < cards.size(); i++) {
                if ((mask & (1 << i)) == 0) continue;
                Card card = cards.get(i);
                counts[card.rank().value()]++;
                if (suit == null) suit = card.suit();
                else if (suit != card.suit()) flush = false;
            }
            best = Math.max(best, five(counts, flush));
        }
        return best;
    }

    private static int five(int[] counts, boolean flush) {
        var singles = new ArrayList<Integer>(5);
        var pairs = new ArrayList<Integer>(2);
        int triple = 0, four = 0, mask = 0;
        for (int rank = 14; rank >= 2; rank--) {
            if (counts[rank] > 0) mask |= 1 << rank;
            switch (counts[rank]) {
                case 1 -> singles.add(rank);
                case 2 -> pairs.add(rank);
                case 3 -> triple = rank;
                case 4 -> four = rank;
                default -> { }
            }
        }
        if ((mask & (1 << 14)) != 0) mask |= 2;
        int straight = 0;
        for (int high = 14; high >= 5; high--) {
            int run = 31 << (high - 4);
            if ((mask & run) == run) { straight = high; break; }
        }
        if (flush && straight > 0) return pack(8, List.of(straight));
        if (four > 0) return pack(7, List.of(four, singles.get(0)));
        if (triple > 0 && !pairs.isEmpty()) return pack(6, List.of(triple, pairs.get(0)));
        if (flush) return pack(5, singles);
        if (straight > 0) return pack(4, List.of(straight));
        if (triple > 0) { singles.add(0, triple); return pack(3, singles); }
        if (pairs.size() == 2) return pack(2, List.of(pairs.get(0), pairs.get(1), singles.get(0)));
        if (pairs.size() == 1) { singles.add(0, pairs.get(0)); return pack(1, singles); }
        return pack(0, singles);
    }

    private static int pack(int category, List<Integer> ranks) {
        int result = category;
        for (int i = 0; i < 5; i++) result = (result << 4) | (i < ranks.size() ? ranks.get(i) : 0);
        return result;
    }
}
