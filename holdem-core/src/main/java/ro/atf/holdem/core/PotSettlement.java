package ro.atf.holdem.core;

import java.util.*;

final class PotSettlement {
    record Result(int[] awards, List<String> journal) {}

    private PotSettlement() {}

    static Result settle(int[] paid, int[] rank, int button) {
        int[] awards = new int[paid.length];
        var journal = new ArrayList<String>();
        int previous = 0;
        for (int cap : Arrays.stream(paid).filter(n -> n > 0).distinct().sorted().toArray()) {
            int contributors = 0;
            int best = -1;
            var winners = new ArrayList<Integer>();
            var eligible = new ArrayList<Integer>();
            int lastContributor = -1;
            for (int offset = 1; offset <= paid.length; offset++) {
                int seat = (button + offset) % paid.length;
                if (paid[seat] < cap) continue;
                contributors++;
                lastContributor = seat;
                if (rank[seat] < 0) continue;
                eligible.add(seat);
                if (rank[seat] < best) continue;
                if (rank[seat] > best) { winners.clear(); best = rank[seat]; }
                winners.add(seat);
            }
            int layer = (cap - previous) * contributors;
            if (contributors == 1) {
                awards[lastContributor] += layer;
                journal.add("RETURN seat=" + lastContributor + " amount=" + layer);
            } else {
                if (winners.isEmpty()) throw new IllegalStateException("Pot has no eligible seat");
                for (int i = 0; i < winners.size(); i++) {
                    awards[winners.get(i)] += layer / winners.size() + (i < layer % winners.size() ? 1 : 0);
                }
                journal.add("POT cap=" + cap + " amount=" + layer + " eligible=" + eligible + " winners=" + winners);
            }
            previous = cap;
        }
        return new Result(awards, List.copyOf(journal));
    }
}
