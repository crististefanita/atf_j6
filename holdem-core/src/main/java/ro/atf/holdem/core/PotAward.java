package ro.atf.holdem.core;

import java.util.List;

/** One awarded layer in button order. Uncalled returns are tracked separately. */
public record PotAward(int amount, List<Integer> eligible, List<Integer> winners) {
    public PotAward {
        eligible = List.copyOf(eligible);
        winners = List.copyOf(winners);
    }
}
