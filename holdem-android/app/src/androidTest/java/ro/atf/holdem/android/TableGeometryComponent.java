package ro.atf.holdem.android;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.LegalAction;
import ro.atf.holdem.core.Street;
import ro.cardtable.ui.screens.ScreenViews;

/** Measures the real Android table component with controlled public frames and no input timing. */
final class TableGeometryComponent {
    record Probe(String name, String expected, String actual, boolean passed) { }
    private record Frame(Map<String, Rect> bounds, List<String> names) { }
    private static final List<String> NAMES = List.of("Ștefăniță Alexandru", "Élodie", "河野 絵里", "Ștefan ♠", "Ileana", "Dvořák");

    private final Activity activity;
    private final ViewGroup host;
    private final int width;
    private final int height;

    private TableGeometryComponent(Activity activity) {
        this.activity = activity;
        host = activity.findViewById(android.R.id.content);
        var config = activity.getResources().getConfiguration();
        float density = activity.getResources().getDisplayMetrics().density;
        width = host.getWidth() > 0 ? host.getWidth() : Math.round(config.screenWidthDp * density);
        height = host.getHeight() > 0 ? host.getHeight() : Math.round(config.screenHeightDp * density);
    }

    static List<Probe> inspect(Activity activity) {
        var component = new TableGeometryComponent(activity);
        var results = new ArrayList<Probe>();
        for (String language : List.of("ro", "en")) {
            for (int seats : List.of(2, 4, 5, 6)) {
                results.add(component.actorOnly(language, seats));
                results.add(component.playedHand(language, seats));
            }
            results.add(component.scrolledTurn(language));
        }
        return results;
    }

    private Probe actorOnly(String language, int seats) {
        var game = game(seats);
        var frame = game.view();
        var names = NAMES.subList(0, seats);
        var table = table(language);
        var differences = new ArrayList<String>();
        Frame original = null;
        for (int actor = 0; actor < seats; actor++) {
            var controls = actor == frame.viewer()
                    ? List.of(new LegalAction(ActionType.CHECK, 0, 0, 0)) : List.<LegalAction>of();
            var turn = new HoldemTableViewState(frame.viewer(), actor, frame.dealer(), frame.smallBlind(), frame.bigBlind(),
                    frame.street(), frame.pot(), frame.ownCards(), frame.board(), frame.seats(), controls, frame.bettingActions());
            var measured = render(table, names, game, turn, false);
            if (original == null) original = measured;
            compare(differences, "actor " + actor, original.bounds(), measured.bounds());
            if (!original.names().equals(measured.names()))
                differences.add("actor " + actor + " changes the seat name text");
            if (actor == frame.viewer())
                compare(differences, "automatic local turn", original.bounds(), render(table, names, game, turn, true).bounds());
        }
        return probe(language, seats, "actor changes with identical cards and stakes", differences,
                seats + " actors plus automatic local turn");
    }

    private Probe playedHand(String language, int seats) {
        var game = game(seats);
        var names = NAMES.subList(0, seats);
        var table = table(language);
        var differences = new ArrayList<String>();
        Map<String, Rect> original = null;
        int frames = 0;
        while (game.view().street() != Street.COMPLETE && frames < 40) {
            var frame = game.view();
            var measured = render(table, names, game, frame, false).bounds();
            if (original == null) original = measured;
            compare(differences, frame.street() + " actor " + frame.actor(), original, measured);
            var turn = game.turn();
            if (game.hasPassiveTurn()) {
                game.advancePassiveAction(turn);
            } else {
                var action = frame.legalActions().stream()
                        .filter(option -> option.type() == ActionType.CHECK || option.type() == ActionType.CALL)
                        .findFirst().orElseThrow();
                game.play(turn, new LocalHoldemGame.Move(action.type(), action.amount()));
            }
            frames++;
        }
        if (game.view().street() != Street.COMPLETE || game.view().board().size() != 5)
            differences.add("fixture did not reach showdown");
        return probe(language, seats, "table stays fixed through the played hand", differences, frames + " frames");
    }

    private Probe scrolledTurn(String language) {
        int seats = 6;
        var game = game(seats);
        var table = table(language);
        var names = NAMES.subList(0, seats);
        var frame = game.view();
        render(table, names, game, frame, false);
        var viewport = viewport();
        View historyToggle = viewport.findViewWithTag("betting-history-toggle");
        historyToggle.performClick();
        layoutPage();
        viewport.scrollTo(0, Math.round(activity.getResources().getDisplayMetrics().density * 30));
        int scroll = viewport.getScrollY();
        var before = measure(names.size(), frame);
        if (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
        else {
            var action = frame.legalActions().stream().filter(option -> option.type() == ActionType.CALL)
                    .findFirst().orElseThrow();
            game.play(game.turn(), new LocalHoldemGame.Move(action.type(), action.amount()));
        }
        var after = render(table, names, game, game.view(), false);
        var differences = new ArrayList<String>();
        compare(differences, "next actor", before.bounds(), after.bounds());
        if (viewport().getScrollY() != scroll)
            differences.add("viewport scroll " + scroll + " → " + viewport().getScrollY());
        if (!viewport().findViewWithTag("betting-history-list").isShown())
            differences.add("expanded panel closed on redraw");
        return probe(language, seats, "redraw keeps the viewport position", differences, "scroll=" + scroll);
    }

    private static LocalHoldemGame game(int seats) {
        return seats == 2 ? new LocalHoldemGame(seats, 19L)
                : LocalHoldemGame.tournament(seats, 19L, 1, "local-human", true);
    }

    private HoldemTableView table(String language) {
        var configuration = new android.content.res.Configuration(activity.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language));
        var localized = activity.createConfigurationContext(configuration);
        var views = new ScreenViews(activity, value -> value, view -> { });
        return new HoldemTableView(views, localized::getString);
    }

    private Frame render(HoldemTableView table, List<String> names, LocalHoldemGame game,
                         HoldemTableViewState frame, boolean automatic) {
        table.show(frame, names, game, () -> { }, () -> { }, move -> false, automatic, () -> { });
        layoutPage();
        return measure(names.size(), frame);
    }

    private ScrollView viewport() {
        return host.findViewWithTag("holdem-game-viewport");
    }

    private void layoutPage() {
        View page = host.getChildAt(0);
        page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, width, height);
    }

    private Frame measure(int seatCount, HoldemTableViewState frame) {
        View page = host.getChildAt(0);
        var measured = new LinkedHashMap<String, Rect>();
        for (String tag : List.of("turn-status", "best-hand-status", "settlement-target:pot"))
            measured.put(tag, bounds(page.findViewWithTag(tag), page));
        View pot = page.findViewWithTag("settlement-target:pot");
        var center = (ViewGroup) pot.getParent();
        var board = (ViewGroup) center.getChildAt(center.getChildCount() - 1);
        for (int i = 0; i < board.getChildCount(); i++) measured.put("board " + i, bounds(board.getChildAt(i), page));
        for (var card : frame.ownCards()) {
            String tag = HoldemTablePresentation.handCardTag(card.label());
            var view = page.findViewWithTag(tag);
            if (view != null) measured.put(tag, bounds(view, page));
        }
        var seatNames = new ArrayList<String>();
        for (int i = 0; i < seatCount; i++) {
            measured.put("seat " + i, bounds(page.findViewWithTag("settlement-target:seat:" + i), page));
            TextView name = page.findViewWithTag("seat-name:" + i);
            measured.put("name " + i, bounds(name, page));
            seatNames.add(name.getText().toString());
        }
        return new Frame(measured, seatNames);
    }

    private static Rect bounds(View view, View page) {
        var box = new Rect(0, 0, view.getWidth(), view.getHeight());
        if (box.isEmpty()) throw new AssertionError("Unmeasured table region " + view.getTag());
        int[] origin = new int[2];
        int[] pageOrigin = new int[2];
        view.getLocationOnScreen(origin);
        page.getLocationOnScreen(pageOrigin);
        box.offset(origin[0] - pageOrigin[0], origin[1] - pageOrigin[1]);
        return box;
    }

    private static void compare(List<String> differences, String step, Map<String, Rect> original, Map<String, Rect> current) {
        original.forEach((part, box) -> {
            if (!box.equals(current.get(part)) && differences.size() < 5)
                differences.add(step + " " + part + " " + box + " → " + current.get(part));
        });
    }

    private static Probe probe(String language, int seats, String scenario, List<String> differences, String samples) {
        return new Probe(language + " / " + seats + " seats / " + scenario, "identical pixel bounds",
                differences.isEmpty() ? samples : String.join(" | ", differences), differences.isEmpty());
    }
}
