package ro.atf.holdem.core;

import java.util.*;

final class PotSettlement {
    record Result(int[] awards, int[] potWon, int[] returned, List<String> journal, List<PotAward> layers) {
        Result {
            awards = awards.clone();
            potWon = potWon.clone();
            returned = returned.clone();
            journal = List.copyOf(journal);
            layers = List.copyOf(layers);
        }
        @Override public int[] awards() { return awards.clone(); }
        @Override public int[] potWon() { return potWon.clone(); }
        @Override public int[] returned() { return returned.clone(); }
    }

    private PotSettlement() {}

    static Result settle(int[] paid, int[] rank, int button) {
        int[] awards = new int[paid.length];
        int[] potWon = new int[paid.length];
        int[] returned = new int[paid.length];
        var journal = new ArrayList<String>();
        var layers = new ArrayList<PotAward>();
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
                returned[lastContributor] += layer;
                journal.add("RETURN seat=" + lastContributor + " amount=" + layer);
            } else {
                if (winners.isEmpty()) throw new IllegalStateException("Pot has no eligible seat");
                for (int i = 0; i < winners.size(); i++) {
                    int share = layer / winners.size() + (i < layer % winners.size() ? 1 : 0);
                    awards[winners.get(i)] += share;
                    potWon[winners.get(i)] += share;
                }
                layers.add(new PotAward(layer, eligible, winners));
                journal.add("POT cap=" + cap + " amount=" + layer + " eligible=" + eligible + " winners=" + winners);
            }
            previous = cap;
        }
        return new Result(awards, potWon, returned, journal, layers);
    }
}
