package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class HoldemTableTasksComponentTest {
    private static final LocalHoldemGame.Move CALL = new LocalHoldemGame.Move(ActionType.CALL, 10);
    private static final LocalHoldemGame.Move CHECK = new LocalHoldemGame.Move(ActionType.CHECK, 0);
    private final QueueExecutor worker = new QueueExecutor();
    private final QueueExecutor estimates = new QueueExecutor();
    private final QueueExecutor ui = new QueueExecutor();
    private final HoldemTableTasks tasks = new HoldemTableTasks(worker, estimates, ui);
    private final List<LocalHoldemGame.Move> played = new ArrayList<>();

    @Test public void cancelledResultCannotApplyOrClearTheReplacementTurn() {
        tasks.play(() -> CALL, () -> true, played::add, () -> fail("turn unavailable"));
        worker.runNext();
        tasks.cancelTurn();
        tasks.play(() -> CHECK, () -> true, played::add, () -> fail("turn unavailable"));

        ui.runNext();
        assertTrue(tasks.isTurnPending());
        assertTrue(played.isEmpty());
        worker.runNext();
        ui.runNext();

        assertEquals(List.of(CHECK), played);
        assertFalse(tasks.isTurnPending());
    }

    @Test public void rejectsDuplicateWorkAndDropsAResultForAnExpiredScene() {
        var current = new AtomicBoolean(true);
        tasks.play(() -> CALL, current::get, played::add, () -> fail("turn unavailable"));
        tasks.play(() -> CHECK, current::get, played::add, () -> fail("turn unavailable"));
        assertEquals(1, worker.size());
        worker.runNext();
        current.set(false);
        ui.runNext();

        assertTrue(played.isEmpty());
        assertFalse(tasks.isTurnPending());
    }

    @Test public void reportsFailureOnlyForCurrentTurnAndCanAcceptAnotherTurn() {
        var failure = new AtomicBoolean(false);
        tasks.play(() -> { throw new IllegalStateException("policy failed"); }, () -> true,
                played::add, () -> failure.set(true));
        worker.runNext();
        ui.runNext();
        assertTrue(failure.get());
        assertTrue(played.isEmpty());
        assertFalse(tasks.isTurnPending());

        tasks.play(() -> CHECK, () -> true, played::add, () -> fail("turn unavailable"));
        worker.runNext();
        ui.runNext();
        assertEquals(List.of(CHECK), played);
    }

    @Test public void onlyTheCurrentSceneReceivesTheSharedEstimate() {
        var game = new LocalHoldemGame(2, 0, 62);
        var chance = HoldemWinChance.from(game.view());
        assertNotNull(chance);
        var current = new AtomicBoolean(true);
        var results = new ArrayList<Integer>();

        tasks.estimate(chance, current::get, ignored -> fail("obsolete scene"));
        tasks.estimate(chance, () -> true, results::add);
        assertEquals(1, estimates.size());
        current.set(false);
        estimates.runNext();
        ui.drain();

        assertEquals(List.of(chance.estimatePercent()), results);
        tasks.estimate(chance, () -> true, results::add);
        assertEquals(2, results.size());
        assertEquals(0, estimates.size());
    }

    @Test public void hintIsSuppressedWhileAnAutomatedTurnIsPending() {
        var game = new LocalHoldemGame(2, 0, 62);
        var turn = game.turn();
        var hints = new ArrayList<LocalHoldemGame.AiSuggestion>();
        tasks.suggest(() -> game.suggest(turn), () -> true, hints::add);
        tasks.play(() -> CALL, () -> true, played::add, () -> fail("turn unavailable"));

        worker.runNext();
        ui.runNext();
        assertTrue(hints.isEmpty());
        worker.runNext();
        ui.runNext();
        assertEquals(List.of(CALL), played);

        tasks.suggest(() -> game.suggest(turn), () -> true, hints::add);
        worker.runNext();
        ui.runNext();
        assertEquals(List.of(game.suggest(turn)), hints);
    }

    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        @Override public void execute(Runnable action) { jobs.add(action); }
        int size() { return jobs.size(); }
        void runNext() { jobs.remove().run(); }
        void drain() { while (!jobs.isEmpty()) runNext(); }
    }
}
