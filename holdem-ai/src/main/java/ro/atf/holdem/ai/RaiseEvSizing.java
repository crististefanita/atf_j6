package ro.atf.holdem.ai;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.LegalAction;

/** Compares bounded heads-up preflop raise amounts in chips from the current decision. */
public final class RaiseEvSizing {
    public record Settings(int calledEquityDiscountPp, int minEdgePercentOfBigBlind, List<Integer> potPercents) {
        public Settings {
            potPercents = List.copyOf(potPercents);
            if (calledEquityDiscountPp < 0 || calledEquityDiscountPp > 25
                    || minEdgePercentOfBigBlind < 1 || minEdgePercentOfBigBlind > 200
                    || potPercents.isEmpty() || potPercents.size() > 5
                    || potPercents.stream().anyMatch(percent -> percent < 25 || percent > 200)
                    || !potPercents.equals(potPercents.stream().distinct().sorted().toList()))
                throw new IllegalArgumentException("Invalid raise EV settings");
        }
    }

    public record Offer(int amount, int opponentPrice, PreflopResponseModel.Spot spot) {}
    record Value(Offer offer, double fold, double reraise, double chips) {}
    record Comparison(Value reference, Value selected) {}

    private RaiseEvSizing() {}

    public static List<Offer> offers(HoldemObservation observation, LegalAction legal, int cap,
                                     int referenceAmount, Settings settings) {
        if (legal.type() != ActionType.RAISE || referenceAmount < legal.minAmount()
                || referenceAmount > Math.min(cap, legal.maxAmount()))
            return List.of();
        var candidates = new TreeSet<Integer>();
        candidates.add(referenceAmount);
        candidates.add(legal.minAmount());
        long potAfterCall = (long) observation.pot() + observation.toCall();
        for (int fraction : settings.potPercents()) {
            long amount = observation.toCall() + potAfterCall * fraction / 100;
            if (amount >= legal.minAmount() && amount <= Math.min(cap, legal.maxAmount()))
                candidates.add(Math.toIntExact(amount));
        }
        // Keep the reference in its band and the nearest offer in any other band.
        var bands = new LinkedHashMap<Boolean, Offer>();
        candidates.stream().sorted(Comparator.comparingInt(amount -> Math.abs(amount - referenceAmount)))
                .forEach(amount -> {
                    var quote = PreflopResponseModel.quote(observation, amount);
                    if (quote != null) bands.putIfAbsent(quote.spot().cheap(),
                            new Offer(amount, quote.opponentPrice(), quote.spot()));
                });
        return bands.values().stream().findFirst().filter(offer -> offer.amount() == referenceAmount).isPresent()
                ? List.copyOf(bands.values()) : List.of();
    }

    static Comparison compare(HoldemObservation observation, List<Offer> offers, double conservativeEquity,
                              PreflopResponseModel response, Settings settings) {
        if (offers.size() < 2) return null;
        double calledEquity = Math.max(0, conservativeEquity - settings.calledEquityDiscountPp() / 100.0);
        Value reference = value(observation.pot(), offers.get(0), calledEquity, response);
        if (reference == null) return null;
        Value alternative = value(observation.pot(), offers.get(1), calledEquity, response);
        if (alternative == null) return null;
        double margin = observation.bigBlind() * (settings.minEdgePercentOfBigBlind() / 100.0);
        Value selected = alternative.chips() - reference.chips() >= margin ? alternative : reference;
        return new Comparison(reference, selected);
    }

    private static Value value(int pot, Offer offer, double calledEquity, PreflopResponseModel response) {
        var probabilities = response.estimate(offer.spot());
        if (probabilities == null) return null;
        return new Value(offer, probabilities.fold(), probabilities.raise(),
                expectedChips(pot, offer.amount(), offer.opponentPrice(), calledEquity,
                        probabilities.fold(), probabilities.raise()));
    }

    static double expectedChips(int pot, int amount, int opponentPrice, double calledEquity,
                                double fold, double reraise) {
        double call = Math.max(0, 1 - fold - reraise);
        return fold * pot + call * (calledEquity * ((long) pot + amount + opponentPrice) - amount)
                - reraise * amount;
    }
}
