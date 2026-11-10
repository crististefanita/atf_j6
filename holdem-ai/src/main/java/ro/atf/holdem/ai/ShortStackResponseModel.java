package ro.atf.holdem.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.PublicOpponentTendency;

/** Lab response frequencies for short-stack heads-up preflop re-raise quotes. */
public final class ShortStackResponseModel {
    public enum Coverage { FUNDED, COVERED }
    public enum Style { RESTRAINED, UNKNOWN, PUSHY }
    public enum Price { UNDER20, FROM20_TO26, FROM27_TO33, AT_LEAST34 }
    public enum Detail { POOLED, PRICE, PRICE_AND_STYLE }

    public record Cell(Coverage coverage, Price price, Style style) {
        public Cell {
            Objects.requireNonNull(coverage, "coverage");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(style, "style");
        }
    }

    public record Quote(Cell cell, int pricePercent, int effectiveStack) {}

    public record Counts(int fold, int call, int raise) {
        public Counts {
            if (fold < 0 || call < 0 || raise < 0) throw new IllegalArgumentException("Negative response count");
        }

        long total() { return (long) fold + call + raise; }

        Counts plus(Counts other) {
            return new Counts(Math.addExact(fold, other.fold), Math.addExact(call, other.call),
                    Math.addExact(raise, other.raise));
        }
    }

    public record Probabilities(double fold, double call, double raise) {}

    private record PriceCell(Coverage coverage, Price price) {}

    private final Map<Cell, Counts> byCell;
    private final Map<PriceCell, Counts> byPrice;
    private final Map<Coverage, Counts> pooled;
    private final int minPool;
    private final int pricePrior;
    private final int stylePrior;

    public ShortStackResponseModel(Map<Cell, Counts> counts, int minPool, int pricePrior, int stylePrior) {
        if (minPool < 1 || pricePrior < 1 || stylePrior < 1)
            throw new IllegalArgumentException("Expected positive support and priors");
        this.minPool = minPool;
        this.pricePrior = pricePrior;
        this.stylePrior = stylePrior;
        byCell = Map.copyOf(counts);
        var prices = new HashMap<PriceCell, Counts>();
        var totals = new HashMap<Coverage, Counts>();
        byCell.forEach((key, value) -> {
            if (key.coverage() == Coverage.COVERED && value.raise() != 0)
                throw new IllegalArgumentException("A covered opponent cannot increase the wager");
            prices.merge(new PriceCell(key.coverage(), key.price()), value, Counts::plus);
            totals.merge(key.coverage(), value, Counts::plus);
        });
        byPrice = Map.copyOf(prices);
        pooled = Map.copyOf(totals);
    }

    public Probabilities estimate(Cell cell, Detail detail) {
        var root = pooled.get(cell.coverage());
        if (root == null || root.total() < minPool) return null;
        double total = root.total();
        var result = new Probabilities(root.fold() / total, root.call() / total, root.raise() / total);
        if (detail == Detail.POOLED) return result;
        result = shrink(byPrice.get(new PriceCell(cell.coverage(), cell.price())), result, pricePrior);
        return detail == Detail.PRICE || cell.style() == Style.UNKNOWN
                ? result : shrink(byCell.get(cell), result, stylePrior);
    }

    private static Probabilities shrink(Counts counts, Probabilities parent, int weight) {
        if (counts == null || counts.total() == 0) return parent;
        double n = counts.total() + (double) weight;
        return new Probabilities((counts.fold() + weight * parent.fold()) / n,
                (counts.call() + weight * parent.call()) / n, (counts.raise() + weight * parent.raise()) / n);
    }

    public static Quote quote(HoldemObservation observation, int amount) {
        if (observation.legalActions().stream().noneMatch(action -> action.type() == ActionType.RAISE
                && amount >= action.minAmount() && amount <= action.maxAmount())) return null;
        var offer = PreflopResponseModel.quote(observation, amount, true);
        if (offer == null || offer.spot().sequence() != PreflopResponseModel.Sequence.RERAISE
                || offer.effectiveStack() > 10L * observation.bigBlind())
            return null;
        int price = (int) (100L * offer.opponentPrice()
                / (observation.pot() + (long) amount + offer.opponentPrice()));
        var coverage = offer.covered() ? Coverage.COVERED : Coverage.FUNDED;
        var tendency = observation.publicOpponentTendencies().stream()
                .filter(entry -> entry.physicalSeat() == offer.opponentPhysicalSeat()).findFirst().orElse(null);
        return new Quote(new Cell(coverage, priceBand(price), style(tendency)), price, offer.effectiveStack());
    }

    private static Price priceBand(int percent) {
        if (percent < 20) return Price.UNDER20;
        if (percent < 27) return Price.FROM20_TO26;
        if (percent < 34) return Price.FROM27_TO33;
        return Price.AT_LEAST34;
    }

    private static Style style(PublicOpponentTendency tendency) {
        if (tendency == null || tendency.shoveOpportunities() < 4) return Style.UNKNOWN;
        long evidence = 100L * (tendency.allInRaises() + 1L);
        long opportunities = tendency.shoveOpportunities() + 5L;
        if (evidence >= 30 * opportunities) return Style.PUSHY;
        if (evidence <= 15 * opportunities) return Style.RESTRAINED;
        return Style.UNKNOWN;
    }
}
