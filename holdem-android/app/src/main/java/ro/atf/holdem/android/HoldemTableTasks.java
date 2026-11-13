package ro.atf.holdem.android;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs table calculations off the UI thread and publishes results to the scene that requested them. */
final class HoldemTableTasks {
    private final Executor decisions;
    private final Executor estimates;
    private final Executor ui;
    private TurnTask pendingTurn;
    private HoldemWinChance lastChance;
    private CompletableFuture<Integer> chanceResult;

    HoldemTableTasks(Executor decisions, Executor estimates, Executor ui) {
        this.decisions = decisions;
        this.estimates = estimates;
        this.ui = ui;
    }

    boolean isTurnPending() {
        return pendingTurn != null;
    }

    void cancelTurn() {
        if (pendingTurn != null) pendingTurn.cancel(true);
        pendingTurn = null;
    }

    void suggest(Supplier<LocalHoldemGame.AiSuggestion> compute, BooleanSupplier current,
                 Consumer<LocalHoldemGame.AiSuggestion> show) {
        decisions.execute(() -> {
            var result = computeOrUnavailable(compute);
            ui.execute(() -> {
                if (current.getAsBoolean() && !isTurnPending()) show.accept(result);
            });
        });
    }

    void play(Supplier<LocalHoldemGame.Move> compute, BooleanSupplier current,
              Consumer<LocalHoldemGame.Move> accept, Runnable unavailable) {
        if (isTurnPending()) return;
        var turn = new TurnTask(compute, current, accept, unavailable);
        pendingTurn = turn;
        decisions.execute(turn);
    }

    private final class TurnTask extends FutureTask<LocalHoldemGame.Move> {
        private final BooleanSupplier current;
        private final Consumer<LocalHoldemGame.Move> accept;
        private final Runnable unavailable;

        TurnTask(Supplier<LocalHoldemGame.Move> compute, BooleanSupplier current,
                 Consumer<LocalHoldemGame.Move> accept, Runnable unavailable) {
            super(() -> computeOrUnavailable(compute));
            this.current = current;
            this.accept = accept;
            this.unavailable = unavailable;
        }

        @Override protected void done() {
            if (!isCancelled()) ui.execute(this::publish);
        }

        private void publish() {
            if (pendingTurn != this) return;
            pendingTurn = null;
            if (!current.getAsBoolean()) return;
            LocalHoldemGame.Move move;
            try {
                move = get();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                move = null;
            } catch (ExecutionException failure) {
                move = null;
            }
            if (move == null) unavailable.run();
            else accept.accept(move);
        }
    }

    void estimate(HoldemWinChance chance, BooleanSupplier current, Consumer<Integer> show) {
        if (chance == null) {
            show.accept(null);
            return;
        }
        if (!chance.equals(lastChance)) {
            lastChance = chance;
            chanceResult = CompletableFuture.supplyAsync(chance::estimatePercent, estimates);
        }
        if (chanceResult.isDone()) {
            show.accept(chanceResult.isCompletedExceptionally() ? null : chanceResult.join());
            return;
        }
        chanceResult.whenComplete((percent, failure) -> ui.execute(() -> {
            if (current.getAsBoolean()) show.accept(percent);
        }));
    }

    private static <T> T computeOrUnavailable(Supplier<T> compute) {
        try {
            return compute.get();
        } catch (RuntimeException failure) {
            return null;
        }
    }
}
