package ro.atf.holdem.ai;

import ro.atf.holdem.ai.ContextualEquityPolicy.ContextWeights;
import ro.atf.holdem.ai.ContextualEquityPolicy.GameMode;
import ro.atf.holdem.ai.ContextualEquityPolicy.HardPressure;
import ro.atf.holdem.ai.ContextualEquityPolicy.RaiseContext;

import java.util.List;

/** Policy presets and the stable clockwise roster of a local Hard tournament. */
public final class TournamentPlayStyles {
    public enum Style {
        BALANCED(8, 60, HardPressure.TOURNAMENT),
        PRUDENT(11, 64, new HardPressure(1, 82, 90, 97, false,
                new RaiseContext(10, 6, 3, 4, 4, 7, 10, 70))),
        ACTIVE(7, 58, new HardPressure(4, 74, 86, 95, false,
                new RaiseContext(6, 5, 2, 3, 3, 6, 8, 74))),
        POSITIONAL(9, 61, new HardPressure(3, 78, 88, 96, false,
                new RaiseContext(8, 5, 5, 3, 3, 6, 8, 75))),
        NORMAL(0, 0, null);

        private final int callMargin;
        private final int valueThreshold;
        private final HardPressure pressure;

        Style(int callMargin, int valueThreshold, HardPressure pressure) {
            this.callMargin = callMargin;
            this.valueThreshold = valueThreshold;
            this.pressure = pressure;
        }

        public DecisionPolicy policy(int samples) {
            if (this == NORMAL) return new CallingStationPolicy();
            return new ContextualEquityPolicy(samples, 2, callMargin, 18, valueThreshold,
                    GameMode.TOURNAMENT, ContextWeights.defaults(), pressure);
        }
    }

    private static final List<Style> HARD_ROSTER = List.of(
            Style.BALANCED, Style.PRUDENT, Style.ACTIVE, Style.POSITIONAL, Style.NORMAL);

    private TournamentPlayStyles() {}

    public static Style hardAutoPlayStyle() {
        return Style.BALANCED;
    }

    public static Style hardOpponentStyle(int seats, int human, int opponent) {
        if (seats < 4 || seats > 6 || human < 0 || human >= seats
                || opponent < 0 || opponent >= seats || human == opponent)
            throw new IllegalArgumentException("Invalid Hard tournament seats");
        return HARD_ROSTER.get(Math.floorMod(opponent - human, seats) - 1);
    }
}
