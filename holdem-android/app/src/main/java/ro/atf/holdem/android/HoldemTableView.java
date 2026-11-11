package ro.atf.holdem.android;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HandRank;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BettingAction;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.LegalAction;
import ro.atf.holdem.core.Street;
import ro.cardtable.ui.cards.CardFace;
import ro.cardtable.ui.cards.CardFaceMetrics;
import ro.cardtable.ui.cards.CardHighlightView;
import ro.cardtable.ui.hand.CenteredHandView;
import ro.cardtable.ui.hand.HandCard;
import ro.cardtable.ui.hand.HandFan;
import ro.cardtable.ui.hand.HandLayoutMetrics;
import ro.cardtable.ui.screens.GameTitleBar;
import ro.cardtable.ui.screens.ScreenViews;
import ro.cardtable.ui.seat.PlayerSeatFrame;
import ro.cardtable.ui.seat.PlayerSeatView;
import ro.cardtable.ui.seat.SeatContent;
import ro.cardtable.ui.table.BoardLayout;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableViewportPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Hold'em composes the center, facts and navigation around the common Android table. */
final class HoldemTableView {
    // BoardLayout gives this center the full measured width. Its width hint only has to fit that region.
    private static final int FULL_WIDTH_CENTER_HINT_DP = 1;
    private record ViewportPosition(int tableY, boolean historyExpanded, int historyY) {
        private static final ViewportPosition TOP = new ViewportPosition(0, false, 0);
    }
    record Labels(TextView advice, TextView winChance, android.widget.FrameLayout achievementSlot) { }
    interface Text { String get(int resource, Object... arguments); }

    private final ScreenViews views;
    private final Text text;
    private ViewGroup contentRoot;
    private HoldemViewport compactViewport;
    private HoldemTableViewState settlementFrame;
    private LocalHoldemGame displayedGame;
    private int displayedHand;
    private ValueAnimator settlementAnimator;

    private record AwardMotion(View marker, float startX, float startY, float endX, float endY) { }
    record SettlementAnimation(boolean started, Runnable cancel) { }

    HoldemTableView(ScreenViews views, Text text) {
        this.views = views;
        this.text = text;
    }

    Labels show(HoldemTableViewState frame, List<String> names, LocalHoldemGame game, Runnable home,
                  Runnable newHand, Predicate<LocalHoldemGame.Move> submit, boolean autoPlay,
                  Consumer<Boolean> onAutoPlay) {
        var activity = views.activity();
        views.applyTableWindowChrome();
        var viewport = activity.getResources().getConfiguration();
        var policy = new TableViewportPolicy(viewport.screenHeightDp, viewport.fontScale);
        boolean compact = policy.isCompact() || viewport.screenWidthDp < 340 || names.size() == 2;
        var position = viewportPosition(game, frame.street());
        LinearLayout root = views.gamePage();
        var scroll = new HoldemViewport(activity);
        compactViewport = compact ? scroll : null;
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        var content = new LinearLayout(activity);
        contentRoot = content;
        settlementFrame = frame;
        displayedGame = game;
        displayedHand = game.handNumber();
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        restoreScrollOnLayout(scroll, position.tableY());

        var menu = views.actionButton("☰", text.get(R.string.home), true, home, false, 20);
        menu.setContentDescription(text.get(R.string.home));
        menu.setTextColor(Color.WHITE);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setStateListAnimator(null);
        boolean complete = frame.street() == Street.COMPLETE;
        boolean alignSeatHands = HoldemTablePresentation.alignTerminalSeatHands(frame);
        var title = views.text(game.isTournament() ? text.get(R.string.tournament_hand_title, game.handNumber())
                : text.get(R.string.local_table_name), compact ? 16 : 18, Color.WHITE, true);
        title.setTag("table-title");
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        singleLine(title);
        boolean canDelegate = HoldemTablePresentation.canDelegate(frame);
        var control = complete ? null : new GameTitleBar.AutoPlay(text.get(R.string.auto_play),
                text.get(R.string.auto_play_start), text.get(R.string.auto_play_stop),
                canDelegate && autoPlay, canDelegate, Color.WHITE, onAutoPlay);
        content.addView(new GameTitleBar(activity, menu, title, control), views.matchWrap());

        var details = new LinearLayout(activity);
        details.setGravity(Gravity.CENTER_VERTICAL);
        var street = views.text(streetName(frame.street()), 13, 0xFFDDF4E9, true);
        street.setTag("table-street");
        singleLine(street);
        street.setContentDescription(tableTitle(game, frame.street()));
        details.addView(street, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView winChance = null;
        if (game.showsWinChance() && !complete) {
            winChance = views.text(text.get(R.string.win_chance_pending), 12, 0xFFDDF4E9, true);
            winChance.setTag("win-chance");
            singleLine(winChance);
            winChance.setPadding(views.dp(6), views.dp(3), views.dp(6), views.dp(3));
            winChance.setBackground(views.roundedBackground(Color.rgb(32, 91, 73), ScreenViews.MUTED, 8));
            int chanceWidth = widestText(winChance, List.of(text.get(R.string.win_chance_pending),
                    text.get(R.string.win_chance, 100), text.get(R.string.win_chance_unavailable)));
            details.addView(winChance, new LinearLayout.LayoutParams(chanceWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        content.addView(details, views.matchWrap());

        var seatFrame = new PlayerSeatFrame(activity, compact);
        var seats = new ArrayList<View>(names.size());
        var latestActions = HoldemTablePresentation.latestActionsByPhysicalSeat(frame, names);
        int reservedDigits = complete ? 0 : HoldemTablePresentation.chipFieldDigits(frame.seats());
        int latestActionOrdinal = frame.bettingActions().isEmpty() ? -1
                : frame.bettingActions().get(frame.bettingActions().size() - 1).ordinal();
        for (int display = 0; display < names.size(); display++) {
            int seat = frame.physicalSeat(display);
            var player = frame.seats().get(seat);
            boolean eliminated = game.eliminated(seat);
            var facts = HoldemTablePresentation.seatMetrics(player, complete).stream()
                    .map(metric -> new SeatContent.Fact(text.get(switch (metric.kind()) {
                        case STACK -> R.string.stack;
                        case COMMITTED -> R.string.committed;
                        case WON -> R.string.won;
                        case RETURNED -> R.string.returned;
                    }), Integer.toString(metric.amount())))
                    .collect(java.util.stream.Collectors.toList());
            String name = names.get(display);
            var shortRoles = new StringJoiner("/");
            var roles = new StringJoiner(", ");
            if (seat == frame.dealer()) {
                shortRoles.add("D");
                roles.add(text.get(R.string.dealer));
            }
            if (seat == frame.smallBlind()) {
                shortRoles.add("SB");
                roles.add(text.get(R.string.small_blind));
            }
            if (seat == frame.bigBlind()) {
                shortRoles.add("BB");
                roles.add(text.get(R.string.big_blind));
            }
            boolean active = HoldemTablePresentation.activeSeat(frame, seat);
            var latestForSeat = latestActions.get(seat);
            String description = name + (roles.length() == 0 ? "" : ", " + roles)
                    + facts.stream().map(SeatContent.Fact::inlineText).collect(java.util.stream.Collectors.joining(", ", ", ", ""))
                    + (eliminated ? ", " + text.get(R.string.eliminated) : player.folded() ? ", " + text.get(R.string.folded) : "")
                    + (active ? ", " + text.get(R.string.actor) : "")
                    + (latestForSeat == null ? "" : ", " + seatActionDescription(latestForSeat));
            String heading = HoldemTablePresentation.seatHeading(name, shortRoles.toString(), player.folded(),
                    eliminated, text.get(R.string.folded_badge), text.get(R.string.out_badge));
            if (compact) heading = "▸ " + heading;
            var seatContent = new SeatContent(seat, heading, List.of(), active, description);
            var seatView = seatFrame.buildInlineFacts(seatContent,
                    compact ? 1 : Math.max(0, policy.seatVerticalPaddingDp() - 1));
            if (Build.VERSION.SDK_INT >= 28) seatView.setScreenReaderFocusable(true);
            TextView seatName = seatView.findViewWithTag("seat-name:" + seat);
            singleLine(seatName);
            if (compact) {
                // The same glyph occupies the same space when the turn passes to another seat.
                var markedHeading = new SpannableString(heading);
                int markerColor = active ? seatName.getCurrentTextColor() : Color.TRANSPARENT;
                markedHeading.setSpan(new ForegroundColorSpan(markerColor), 0, 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                seatName.setText(markedHeading);
            }
            addSeatFacts(seatView, facts, active, compact, reservedDigits);
            addSeatAction(seatView, latestForSeat,
                    latestForSeat != null && latestForSeat.ordinal() == latestActionOrdinal, active, compact, !complete);
            View framedSeat = seatView;
            if (!compact) {
                framedSeat = new PlayerSeatView(activity, seatView, text.get(R.string.actor), active, seat, false);
                framedSeat.findViewWithTag("seat-turn-badge:" + seat)
                        .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            View withCards = withTerminalCards(framedSeat, compact, seat, frame, alignSeatHands);
            seatView.setTag("settlement-target:seat:" + seat);
            seats.add(withCards);
        }
        View center = board(frame, names, game, compact);
        View table = compact ? compactTable(seats, center, alignSeatHands) : sharedTable(seats, center, policy);
        content.addView(table, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!alignSeatHands) {
            if (complete) {
                addTerminalHand(content, frame, HoldemTablePresentation.terminalCardHeightDp(compact));
            } else if (compact) {
                var hand = new LinearLayout(activity);
                hand.setGravity(Gravity.CENTER);
                for (Card card : frame.ownCards()) addCard(hand, card, 56, R.string.hand,
                        HoldemTablePresentation.handCardTag(card.label()));
                content.addView(hand, views.matchWrap());
            } else {
                addHandFan(content, frame.ownCards());
            }
        }

        addBettingLedger(content, frame, names, compact, position);

        TextView advice = null;
        android.widget.FrameLayout achievementSlot = null;
        if (complete) {
            achievementSlot = new android.widget.FrameLayout(activity);
            content.addView(achievementSlot, views.matchWrap());
            boolean finished = game.outcome() != null;
            views.addButton(content, text.get(finished ? R.string.tournament_result : R.string.new_hand),
                    finished ? "tournament-result" : "new-hand", true, newHand, true);
        } else {
            boolean humanTurn = frame.actor() == frame.viewer();
            boolean manual = humanTurn && !autoPlay;
            var adviceSlot = views.text(text.get(humanTurn && autoPlay ? R.string.ai_playing : R.string.ai_thinking),
                    compact ? 12 : 13, ScreenViews.MUTED, false);
            adviceSlot.setGravity(Gravity.CENTER);
            adviceSlot.setLines(HoldemTablePresentation.ADVICE_LINES);
            adviceSlot.setEllipsize(android.text.TextUtils.TruncateAt.END);
            adviceSlot.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            adviceSlot.setVisibility(humanTurn ? View.VISIBLE : View.INVISIBLE);
            content.addView(adviceSlot, views.matchWrap());
            if (manual) advice = adviceSlot;

            actions(content, manual ? frame.legalActions() : List.of(), compact, submit);
        }
        return new Labels(advice, winChance, achievementSlot);
    }

    private String tableTitle(LocalHoldemGame game, Street street) {
        if (game.isTournament()) {
            return street == Street.COMPLETE ? text.get(R.string.tournament_settled_title, game.handNumber())
                    : text.get(R.string.tournament_table_title, game.handNumber(), streetName(street));
        }
        return text.get(R.string.table_title, streetName(street));
    }

    private static int widestText(TextView view, List<String> labels) {
        float width = 0;
        for (String label : labels) width = Math.max(width, view.getPaint().measureText(label));
        return (int) Math.ceil(width) + view.getCompoundPaddingLeft() + view.getCompoundPaddingRight();
    }

    private ViewportPosition viewportPosition(LocalHoldemGame game, Street street) {
        if (game != displayedGame || displayedHand != game.handNumber() || street == Street.COMPLETE
                || settlementFrame.street() == Street.COMPLETE || !contentRoot.isAttachedToWindow())
            return ViewportPosition.TOP;
        var viewport = (ScrollView) contentRoot.getParent();
        ScrollView history = contentRoot.findViewWithTag("betting-history-list");
        return new ViewportPosition(viewport.getScrollY(), history.getVisibility() == View.VISIBLE, history.getScrollY());
    }

    private static void restoreScrollOnLayout(ScrollView scroll, int y) {
        if (y == 0) return;
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                                 int oldLeft, int oldTop, int oldRight, int oldBottom) {
                scroll.removeOnLayoutChangeListener(this);
                scroll.scrollTo(0, y);
            }
        });
    }

    void renderWinChance(TextView label, Integer percent) {
        label.setText(percent == null ? text.get(R.string.win_chance_unavailable) : text.get(R.string.win_chance, percent));
        label.setContentDescription(percent == null ? text.get(R.string.win_chance_unavailable_description)
                : text.get(R.string.win_chance_description, percent));
    }

    private static void singleLine(TextView label) {
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    private void addSeatFacts(LinearLayout seat, List<SeatContent.Fact> facts, boolean active, boolean compact,
                              int reservedDigits) {
        var style = HoldemTablePresentation.seatFactStyle(active, compact);
        for (var fact : facts) {
            var label = views.text(fact.label(), style.labelSp(), style.labelColor(), false);
            var value = views.text(fact.value(), style.valueSp(), style.valueColor(), true);
            seat.addView(SeatFactRow.of(label, value, reservedDigits), views.matchWrap());
        }
    }

    private void addSeatAction(LinearLayout seat, HoldemTablePresentation.ActionRow row,
                              boolean latest, boolean active, boolean compact, boolean reserve) {
        if (row == null && !reserve) return;
        var style = HoldemTablePresentation.seatActionStyle(compact, latest, active);
        String label = row == null ? "" : text.get(R.string.seat_latest_action, row.ordinal(),
                text.get(seatActionLabel(row.kind())), row.amount());
        var badge = views.text(label, style.textSp(), style.textColor(), true);
        if (row == null) badge.setVisibility(View.INVISIBLE);
        else badge.setTag("latest-seat-action:" + row.physicalSeat());
        singleLine(badge);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(views.dp(3), views.dp(1), views.dp(3), views.dp(1));
        badge.setContentDescription(null);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        badge.setAccessibilityLiveRegion(latest
                ? View.ACCESSIBILITY_LIVE_REGION_POLITE : View.ACCESSIBILITY_LIVE_REGION_NONE);
        badge.setBackground(views.roundedBackground(style.backgroundColor(), style.borderColor(), 4));
        var params = views.matchWrap();
        params.topMargin = views.dp(1);
        seat.addView(badge, params);
    }

    private String seatActionDescription(HoldemTablePresentation.ActionRow row) {
        return text.get(R.string.seat_action_description, row.ordinal(), row.player(),
                text.get(actionLabel(row.kind())), row.amount(), text.get(streetLabel(row.street())),
                row.totalContribution());
    }

    void renderAdvice(TextView target, LocalHoldemGame.AiSuggestion suggestion) {
        if (target == null) return;
        if (suggestion == null) {
            target.setText(text.get(R.string.ai_action_error));
            target.setContentDescription(text.get(R.string.ai_action_error));
            return;
        }
        String action = text.get(actionLabel(suggestion.move().action()));
        if (suggestion.move().amount() > 0)
            action = text.get(R.string.action_amount, action, suggestion.move().amount());
        String equity = metric(suggestion.explanation(), "equity=");
        String price = metric(suggestion.explanation(), "potOdds=");
        String required = metric(suggestion.explanation(), "required=");
        String uncertainty = metric(suggestion.explanation(), "se=");
        String samples = metric(suggestion.explanation(), "samples=");
        target.setText(compactViewport == null
                ? text.get(R.string.ai_hint, action, equity, price, required,
                        suggestion.policyVersion(), samples, uncertainty)
                : text.get(R.string.ai_hint_compact, action, equity, price));
        target.setContentDescription(text.get(R.string.ai_hint_description, action, equity, price, required,
                suggestion.policyVersion(), samples, uncertainty));
    }

    void addPostGameReview(ViewGroup parent, HoldemTableViewState frame, List<String> names,
                           LocalHoldemGame game) {
        var title = views.text(text.get(R.string.post_game_review), 16, ScreenViews.INK, true);
        title.setPadding(views.dp(4), views.dp(10), views.dp(4), views.dp(4));
        parent.addView(title, views.matchWrap());
        for (int display = 0; display < names.size(); display++) {
            int physical = frame.physicalSeat(display);
            var seat = frame.seats().get(physical);
            var entry = new LinearLayout(views.activity());
            entry.setGravity(Gravity.CENTER_VERTICAL);
            String status = seat.folded() ? text.get(R.string.folded_badge)
                    : game.eliminated(physical) ? text.get(R.string.out_badge) : "";
            String name = status.isEmpty() ? names.get(display) : names.get(display) + " · " + status;
            var label = views.text(name, 13, seat.folded() || game.eliminated(physical)
                    ? ScreenViews.MUTED : ScreenViews.INK, false);
            entry.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            String result = seat.handEvaluation() == null
                    ? seat.potWon() > 0 ? text.get(R.string.uncontested_win)
                    : seat.returned() > 0 ? text.get(R.string.returned) : ""
                    : text.get(categoryLabel(seat.handEvaluation().category()));
            if (!result.isEmpty()) {
                var summary = views.text(result, 11, ScreenViews.MUTED, false);
                entry.addView(summary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            var cards = new LinearLayout(views.activity());
            cards.setGravity(Gravity.CENTER);
            int height = HoldemTablePresentation.terminalCardHeightDp(false);
            for (Card card : seat.shownCards()) {
                TextView view = addCard(cards, card, height, R.string.opponent_cards);
                if (HoldemTablePresentation.isWinningCard(frame, physical, card)) highlightWinningCard(view, card, height);
            }
            if (seat.shownCards().isEmpty()) {
                var unavailable = views.text(text.get(R.string.no_cards_in_last_hand), 11, ScreenViews.MUTED, false);
                cards.addView(unavailable, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            entry.addView(cards, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.addView(entry, views.matchWrap());
        }
    }

    private static String metric(String explanation, String key) {
        int start = explanation.indexOf(key);
        if (start < 0) return "—";
        start += key.length();
        int end = explanation.indexOf(' ', start);
        return explanation.substring(start, end < 0 ? explanation.length() : end);
    }

    private void addHandFan(LinearLayout parent, List<Card> ownCards) {
        var hand = new ArrayList<HandCard>();
        for (Card card : ownCards) {
            var face = face(card);
            hand.add(new HandCard(card.label(), face, cardDescription(R.string.hand, face), false));
        }
        var fan = new HandFan(views.activity(), hand, 0, false, ignored -> { });
        var centered = new CenteredHandView(views.activity(), views.dp(290), fan::build);
        var handBox = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                views.dp(HandLayoutMetrics.cardHeightDp()));
        handBox.gravity = Gravity.CENTER_HORIZONTAL;
        handBox.topMargin = views.dp(6);
        parent.addView(centered, handBox);
    }

    private void addTerminalHand(LinearLayout parent, HoldemTableViewState frame, int height) {
        var hand = new LinearLayout(views.activity());
        hand.setGravity(Gravity.CENTER);
        var selected = frame.seats().get(frame.viewer()).handEvaluation();
        for (Card card : frame.ownCards()) {
            var view = addCard(hand, card, height, R.string.hand,
                    HoldemTablePresentation.handCardTag(card.label()));
            if (selected != null && selected.bestFive().contains(card)) highlightWinningCard(view, card, height);
        }
        parent.addView(hand, views.matchWrap());
    }

    private View withTerminalCards(View seatView, boolean compact, int physicalSeat, HoldemTableViewState frame,
                                   boolean alignSeatHands) {
        var hand = HoldemTablePresentation.terminalCardsAtSeat(frame, physicalSeat);
        if (hand.isEmpty() && !alignSeatHands) return seatView;
        var group = new LinearLayout(views.activity());
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER_HORIZONTAL);
        group.addView(seatView, alignSeatHands
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)
                : views.matchWrap());
        var cards = new LinearLayout(views.activity());
        cards.setGravity(Gravity.CENTER);
        cards.setTag("settlement-hand:seat:" + physicalSeat);
        int height = HoldemTablePresentation.terminalCardHeightDp(compact);
        boolean viewer = physicalSeat == frame.viewer();
        var ownEvaluation = viewer ? frame.seats().get(physicalSeat).handEvaluation() : null;
        for (int index = 0; index < 2; index++) {
            Card card = hand.isEmpty() ? null : hand.get(index);
            String tag = viewer && card != null ? HoldemTablePresentation.handCardTag(card.label())
                    : HoldemTablePresentation.opponentCardTag(physicalSeat, index);
            TextView view = addCard(cards, card, height, viewer ? R.string.hand : R.string.opponent_cards, tag);
            boolean highlighted = card != null && (viewer ? ownEvaluation != null && ownEvaluation.bestFive().contains(card)
                    : HoldemTablePresentation.isWinningCard(frame, physicalSeat, card));
            if (highlighted) {
                highlightWinningCard(view, card, height);
            }
            if (alignSeatHands) {
                var params = (LinearLayout.LayoutParams) view.getLayoutParams();
                params.topMargin = views.dp(Math.max(4, Math.round(height * .08f)) + 1);
            }
        }
        if (hand.isEmpty()) cards.setVisibility(View.INVISIBLE);
        group.addView(cards, views.matchWrap());
        return group;
    }

    private View sharedTable(List<View> seats, View center, TableViewportPolicy policy) {
        var board = new BoardLayout(views.activity(), seats.size(), FULL_WIDTH_CENTER_HINT_DP, policy,
                true, TableGeometry.CenterAlignment.BALANCED);
        seats.forEach(board::addView);
        board.addView(center);
        return board;
    }

    private View compactTable(List<View> seats, View center, boolean alignSeatHands) {
        var table = new LinearLayout(views.activity());
        table.setOrientation(LinearLayout.VERTICAL);
        var rows = CompactSeatRows.forSeats(seats.size());
        table.addView(seatRow(seats, rows.top(), alignSeatHands), views.matchWrap());
        table.addView(new Space(views.activity()), new LinearLayout.LayoutParams(1, 0, 1));
        table.addView(center, views.matchWrap());
        table.addView(new Space(views.activity()), new LinearLayout.LayoutParams(1, 0, 1));
        table.addView(seatRow(seats, rows.bottom(), alignSeatHands), views.matchWrap());
        return table;
    }

    private View seatRow(List<View> seats, int[] positions, boolean alignSeatHands) {
        var row = new LinearLayout(views.activity());
        row.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        if (alignSeatHands) row.setBaselineAligned(false);
        int slotHeight = alignSeatHands ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        for (int seat : positions) {
            View child = seat == CompactSeatRows.EMPTY ? new Space(views.activity()) : seats.get(seat);
            var slot = positions.length == 1
                    ? new LinearLayout.LayoutParams(views.dp(148), slotHeight)
                    : new LinearLayout.LayoutParams(0, slotHeight, 1);
            slot.setMargins(views.dp(2), 0, views.dp(2), 0);
            row.addView(child, slot);
        }
        return row;
    }

    private View board(HoldemTableViewState frame, List<String> names, LocalHoldemGame game, boolean compact) {
        var activity = views.activity();
        var center = new LinearLayout(activity);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(0, 0, 0, views.dp(compact ? 2 : 5));
        String actor = frame.actor() < 0 ? "" : actorName(frame, names);
        var turn = views.text(frame.actor() < 0 ? "" : text.get(R.string.turn_status, actor), compact ? 11 : 14,
                frame.actor() == frame.viewer() ? 0xfff0d486 : ScreenViews.MUTED, true);
        turn.setTag("turn-status");
        singleLine(turn);
        turn.setGravity(Gravity.CENTER);
        if (frame.actor() < 0) turn.setVisibility(View.GONE);
        turn.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        center.addView(turn, views.matchWrap());
        var bestHand = views.text(bestHandLabel(frame), compact ? 11 : 13, Color.WHITE, true);
        bestHand.setTag("best-hand-status");
        singleLine(bestHand);
        bestHand.setGravity(Gravity.CENTER);
        var blindLevel = game.isTournament()
                ? text.get(R.string.tournament_blinds, game.blindLevelNumber(), game.blindLevel().smallBlind(),
                game.blindLevel().bigBlind(), game.blindLevelMinutes())
                : text.get(R.string.fixed_blinds, game.blindLevel().smallBlind(), game.blindLevel().bigBlind());
        var blinds = views.text(blindLevel, compact ? 10 : 12, ScreenViews.MUTED, false);
        blinds.setGravity(Gravity.CENTER);
        singleLine(blinds);
        var pot = views.text(text.get(frame.street() == Street.COMPLETE ? R.string.settled_pot : R.string.pot,
                HoldemTablePresentation.displayedPot(frame)),
                compact ? 15 : 20, Color.WHITE, true);
        pot.setGravity(Gravity.CENTER);
        pot.setTag("settlement-target:pot");
        TextView summary = frame.street() == Street.COMPLETE ? potAwardSummary(frame, names, compact) : null;
        boolean prioritizeAwards = compact && summary != null;
        if (prioritizeAwards) {
            center.addView(pot, views.matchWrap());
            center.addView(summary, views.matchWrap());
        }
        center.addView(bestHand, views.matchWrap());
        center.addView(blinds, views.matchWrap());
        if (!prioritizeAwards) {
            center.addView(pot, views.matchWrap());
            if (summary != null) center.addView(summary, views.matchWrap());
        }
        var row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER);
        for (int index = 0; index < 5; index++) {
            Card card = index < frame.board().size() ? frame.board().get(index) : null;
            if (card == null) addCard(row, null, 48, R.string.board);
            else {
                TextView view = addCard(row, card, 48, R.string.board, HoldemTablePresentation.boardCardTag(index));
                if (HoldemTablePresentation.boardCardsToHighlight(frame).contains(card)) highlightWinningCard(view, card, 48);
            }
        }
        center.addView(row, views.matchWrap());
        return center;
    }

    private TextView potAwardSummary(HoldemTableViewState frame, List<String> displayNames, boolean compact) {
        var awards = HoldemTablePresentation.potAwards(frame);
        if (awards.size() < 2) return null;
        var names = HoldemTablePresentation.physicalSeatNames(frame, displayNames);
        var entries = new StringJoiner(" · ");
        for (var award : awards) entries.add(text.get(R.string.pot_award_entry, names.get(award.physicalSeat()), award.chips()));
        var summary = views.text(text.get(R.string.pot_awards, entries.toString()),
                compact ? 10 : 12, Color.WHITE, false);
        summary.setGravity(Gravity.CENTER);
        summary.setTag("settlement-award-summary");
        return summary;
    }

    private String actorName(HoldemTableViewState frame, List<String> names) {
        for (int displayed = 0; displayed < names.size(); displayed++) {
            if (frame.physicalSeat(displayed) == frame.actor()) return names.get(displayed);
        }
        return text.get(R.string.unknown_player);
    }

    private String bestHandLabel(HoldemTableViewState frame) {
        if (frame.seats().get(frame.viewer()).folded()) return "";
        if (frame.street() == Street.COMPLETE) {
            var evaluation = frame.seats().get(frame.viewer()).handEvaluation();
            return evaluation == null ? "" : text.get(R.string.best_hand, text.get(categoryLabel(evaluation.category())));
        }
        var cards = new ArrayList<Card>(frame.ownCards());
        cards.addAll(frame.board());
        if (cards.size() < 5) return text.get(R.string.best_hand_waiting);
        return text.get(R.string.best_hand, text.get(categoryLabel(HandRank.categoryOf(cards))));
    }

    private void addBettingLedger(LinearLayout parent, HoldemTableViewState frame, List<String> names, boolean compact,
                                  ViewportPosition position) {
        var section = new LinearLayout(views.activity());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(views.dp(8), views.dp(2), views.dp(8), views.dp(2));
        var title = views.text(text.get(R.string.betting_history_title), compact ? 10 : 11, ScreenViews.MUTED, true);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setTag("betting-history-toggle");
        title.setClickable(true);
        title.setFocusable(true);
        title.setPadding(views.dp(4), views.dp(4), views.dp(4), views.dp(4));
        title.setContentDescription(text.get(position.historyExpanded()
                ? R.string.betting_history_hide : R.string.betting_history_show));
        title.setSelected(position.historyExpanded());
        section.addView(title, views.matchWrap());
        var entries = new LinearLayout(views.activity());
        entries.setOrientation(LinearLayout.VERTICAL);
        var rows = HoldemTablePresentation.actionRows(frame, names);
        var style = HoldemTablePresentation.ledgerStyle(compact);
        for (var row : rows) {
            String label = text.get(actionLabel(row.kind()));
            int street = streetLabel(row.street());
            String entry = text.get(R.string.betting_ledger_entry, row.ordinal(), row.player(), label, row.amount(),
                    text.get(street), row.totalContribution());
            var line = views.text(entry, style.textSp(), style.textColor(), true);
            line.setTag("betting-action:" + row.ordinal());
            line.setPadding(views.dp(5), views.dp(2), views.dp(5), views.dp(2));
            line.setBackground(views.roundedBackground(0x1affffff, style.borderColor(), 4));
            line.setContentDescription(entry);
            line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            entries.addView(line, views.matchWrap());
        }
        var history = new ScrollView(views.activity());
        history.setTag("betting-history-list");
        history.setContentDescription(text.get(R.string.betting_history_list_description));
        history.setFillViewport(false);
        history.setVerticalScrollBarEnabled(true);
        history.setNestedScrollingEnabled(true);
        history.addView(entries, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(history, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                views.dp(compact ? 92 : 116)));
        history.setVisibility(position.historyExpanded() ? View.VISIBLE : View.GONE);
        restoreScrollOnLayout(history, position.historyY());
        title.setOnClickListener(ignored -> {
            boolean expanded = history.getVisibility() != View.VISIBLE;
            history.setVisibility(expanded ? View.VISIBLE : View.GONE);
            title.setContentDescription(text.get(expanded
                    ? R.string.betting_history_hide : R.string.betting_history_show));
            title.setSelected(expanded);
            if (expanded) history.post(() -> history.fullScroll(View.FOCUS_DOWN));
        });
        if (frame.bettingActions().isEmpty()) section.setVisibility(View.INVISIBLE);
        parent.addView(section, views.matchWrap());
    }

    private int streetLabel(Street street) {
        return switch (street) {
            case PREFLOP -> R.string.street_preflop;
            case FLOP -> R.string.street_flop;
            case TURN -> R.string.street_turn;
            case RIVER -> R.string.street_river;
            case SHOWDOWN, COMPLETE -> R.string.settled;
        };
    }

    private static int categoryLabel(HandRank.Category category) {
        return switch (category) {
            case HIGH_CARD -> R.string.hand_high_card;
            case ONE_PAIR -> R.string.hand_one_pair;
            case TWO_PAIR -> R.string.hand_two_pair;
            case THREE_OF_A_KIND -> R.string.hand_three_of_a_kind;
            case STRAIGHT -> R.string.hand_straight;
            case FLUSH -> R.string.hand_flush;
            case FULL_HOUSE -> R.string.hand_full_house;
            case FOUR_OF_A_KIND -> R.string.hand_four_of_a_kind;
            case STRAIGHT_FLUSH -> R.string.hand_straight_flush;
        };
    }

    private TextView addCard(LinearLayout row, Card card, int height, int description) {
        return addCard(row, card, height, description, null);
    }

    private TextView addCard(LinearLayout row, Card card, int height, int description, String tag) {
        boolean revealed = card != null;
        var face = revealed ? face(card) : null;
        String label = revealed ? face.rankLabel() + "\n" + face.suitSymbol() : "";
        int ink = revealed && face.red() ? Color.rgb(173, 57, 58) : ScreenViews.INK;
        var cell = views.text(label, 14, ink, true);
        cell.setGravity(Gravity.CENTER);
        cell.setIncludeFontPadding(false);
        cell.setAutoSizeTextTypeUniformWithConfiguration(8, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        cell.setBackground(views.roundedBackground(revealed ? ScreenViews.SURFACE : Color.TRANSPARENT,
                revealed ? ScreenViews.SURFACE : 0xff56937c, 5));
        cell.setImportantForAccessibility(revealed ? View.IMPORTANT_FOR_ACCESSIBILITY_YES : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (revealed) cell.setContentDescription(cardDescription(description, face));
        if (revealed && tag != null) cell.setTag(tag);
        var metrics = CardFaceMetrics.atHeight(height);
        var box = new LinearLayout.LayoutParams(views.dp(metrics.widthDp()), views.dp(metrics.heightDp()));
        box.setMargins(views.dp(2), views.dp(4), views.dp(2), views.dp(2));
        row.addView(cell, box);
        if (compactViewport != null) compactViewport.fitCard(cell, height);
        return cell;
    }

    private void highlightWinningCard(TextView card, Card identity, int height) {
        CardHighlightView.raise(card, height);
        String label = card.getContentDescription() == null ? "" : card.getContentDescription().toString();
        card.setContentDescription(label + " · " + text.get(R.string.winning_card));
        card.setTag("winning-card:" + identity.label());
    }

    /** Shows a temporary chip award moving from the pot to each actual recipient. */
    SettlementAnimation animateSettlement(long remainingMillis, Runnable completed) {
        if (contentRoot == null || settlementFrame == null || remainingMillis <= 0
                || !ValueAnimator.areAnimatorsEnabled()) return new SettlementAnimation(false, () -> { });
        View target = contentRoot.findViewWithTag("settlement-target:pot");
        if (!hasVisibleCenter(target)) return new SettlementAnimation(false, () -> { });
        var awards = HoldemTablePresentation.potAwards(settlementFrame);
        if (awards.isEmpty()) return new SettlementAnimation(false, () -> { });
        View decor = views.activity().getWindow().getDecorView();
        if (!(decor instanceof ViewGroup overlay)) return new SettlementAnimation(false, () -> { });
        int[] origin = new int[2];
        target.getLocationOnScreen(origin);
        int[] overlayOrigin = new int[2];
        overlay.getLocationOnScreen(overlayOrigin);
        float startX = origin[0] - overlayOrigin[0] + target.getWidth() / 2f;
        float startY = origin[1] - overlayOrigin[1] + target.getHeight() / 2f;
        var motions = new ArrayList<AwardMotion>(awards.size());
        for (var award : awards) {
            View destination = contentRoot.findViewWithTag("settlement-target:seat:" + award.physicalSeat());
            if (!hasVisibleCenter(destination)) continue;
            var marker = views.text(text.get(R.string.chip_award, award.chips()),
                    15, Color.WHITE, true);
            marker.setGravity(Gravity.CENTER);
            marker.setTag("settlement-award:" + award.physicalSeat());
            marker.setVisibility(View.INVISIBLE);
            marker.setPadding(views.dp(8), views.dp(4), views.dp(8), views.dp(4));
            marker.setBackground(views.roundedBackground(0xff99702b, 0xffefcc72, 18));
            marker.setElevation(views.dp(8));
            marker.setContentDescription(text.get(R.string.chip_award_description,
                    award.chips()));
            overlay.addView(marker, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int[] destinationLocation = new int[2];
            destination.getLocationOnScreen(destinationLocation);
            motions.add(new AwardMotion(marker, startX, startY,
                    destinationLocation[0] - overlayOrigin[0] + destination.getWidth() / 2f,
                    destinationLocation[1] - overlayOrigin[1] + destination.getHeight() / 2f));
        }
        if (motions.isEmpty()) return new SettlementAnimation(false, () -> { });
        var animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Math.max(1, Math.min(650, remainingMillis)));
        animator.setInterpolator(input -> input);
        animator.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            for (var motion : motions) {
                var marker = motion.marker();
                marker.setX(motion.startX() + (motion.endX() - motion.startX()) * progress - marker.getWidth() / 2f);
                marker.setY(motion.startY() + (motion.endY() - motion.startY()) * progress - marker.getHeight() / 2f);
                marker.setScaleX(1f - .35f * progress);
                marker.setScaleY(1f - .35f * progress);
                marker.setAlpha(1f - .15f * progress);
            }
        });
        Runnable clear = () -> motions.forEach(motion -> overlay.removeView(motion.marker()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (cancelled) return;
                // Keep the terminal position through a draw before clearing the overlay.
                overlay.postOnAnimation(() -> {
                    clear.run();
                    if (settlementAnimator != animator) return;
                    settlementAnimator = null;
                    completed.run();
                });
            }
        });
        settlementAnimator = animator;
        var firstDraw = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                overlay.getViewTreeObserver().removeOnPreDrawListener(this);
                if (settlementAnimator != animator) return true;
                for (var motion : motions) {
                    var marker = motion.marker();
                    marker.setX(motion.startX() - marker.getWidth() / 2f);
                    marker.setY(motion.startY() - marker.getHeight() / 2f);
                    marker.setVisibility(View.VISIBLE);
                }
                overlay.postOnAnimation(() -> { if (settlementAnimator == animator) animator.start(); });
                return true;
            }
        };
        overlay.getViewTreeObserver().addOnPreDrawListener(firstDraw);
        return new SettlementAnimation(true, () -> {
            if (settlementAnimator != animator) return;
            settlementAnimator = null;
            overlay.getViewTreeObserver().removeOnPreDrawListener(firstDraw);
            animator.cancel();
            clear.run();
        });
    }

    private static boolean hasVisibleCenter(View view) {
        if (view == null || !view.isShown()) return false;
        var visible = new Rect();
        return view.getLocalVisibleRect(visible) && HoldemTablePresentation.containsVisibleCenter(
                view.getWidth(), view.getHeight(), visible.left, visible.top, visible.right, visible.bottom);
    }

    private String cardDescription(int description, CardFace face) {
        return text.get(description) + " · " + face.rankLabel() + face.suitSymbol();
    }

    private void actions(LinearLayout parent, List<LegalAction> legal, boolean compact,
                         Predicate<LocalHoldemGame.Move> submit) {
        int rowCount = HoldemTablePresentation.actionRowCount(legal.size());
        var rows = new ArrayList<LinearLayout>(rowCount);
        int targetHeight = views.dp(HoldemTablePresentation.ACTION_TARGET_DP);
        for (int index = 0; index < rowCount; index++) {
            var row = new LinearLayout(views.activity());
            row.setBaselineAligned(false);
            parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeight));
            rows.add(row);
        }
        for (int i = 0; i < legal.size(); i++) {
            var option = legal.get(i);
            boolean sized = option.minAmount() != option.maxAmount();
            String title = text.get(actionLabel(option));
            String label = !sized && option.amount() > 0 ? text.get(R.string.action_amount, title, option.amount()) : title;
            boolean primary = switch (option.type()) {
                case CHECK, CALL -> true;
                default -> false;
            };
            var button = views.actionButton(label, option.type().name(), true, () -> { }, primary, compact ? 13 : 14);
            button.setOnClickListener(opener -> {
                if (sized) requestAmount(opener, title, option, compact, submit);
                else submit.test(new LocalHoldemGame.Move(option.type(), option.amount()));
            });
            HoldemTableButton.applyTableAction(views, button, primary);
            button.setPadding(views.dp(4), 0, views.dp(4), 0);
            button.setIncludeFontPadding(false);
            button.setMaxLines(1);
            button.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            var box = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            float density = views.activity().getResources().getDisplayMetrics().density;
            var margins = HoldemTablePresentation.actionMargins(density, i % 2 == 0, i + 1 < legal.size());
            box.setMarginStart(margins.start());
            box.setMarginEnd(margins.end());
            rows.get(i / 2).addView(button, box);
        }
    }

    private void requestAmount(View opener, String title, LegalAction option, boolean compact,
                               Predicate<LocalHoldemGame.Move> submit) {
        var input = new EditText(views.activity());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (compact) {
            input.setTextSize(16);
            input.setIncludeFontPadding(false);
            input.setPadding(input.getPaddingLeft(), views.dp(2), input.getPaddingRight(), views.dp(2));
        }
        String range = text.get(compact ? R.string.amount_range_compact : R.string.amount_range,
                option.minAmount(), option.maxAmount());
        input.setContentDescription(range);
        input.setText(text.get(R.string.chip_number, option.amount()));
        input.setSelectAllOnFocus(true);
        var form = new LinearLayout(views.activity());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setFocusableInTouchMode(true);
        form.setPadding(views.dp(20), 0, views.dp(20), 0);
        views.addText(form, range, 14, ScreenViews.INK, false, 0, 0);
        form.addView(input, views.matchWrap());
        String errorMessage = compact ? text.get(R.string.invalid_amount_compact, option.minAmount(), option.maxAmount())
                : text.get(R.string.invalid_amount);
        var error = views.text(errorMessage, 14, Color.rgb(164, 36, 48), false);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        form.addView(error, views.matchWrap());
        var scroll = new ScrollView(views.activity());
        scroll.addView(form, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        error.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (view.isShown()) view.post(() -> {
                var bounds = new Rect();
                view.getDrawingRect(bounds);
                view.requestRectangleOnScreen(bounds, true);
            });
        });
        var builder = new AlertDialog.Builder(views.activity()).setView(scroll)
                .setNegativeButton(text.get(R.string.close), null).setPositiveButton(title, null);
        if (compact) {
            var heading = views.text(title, 16, ScreenViews.INK, true);
            heading.setPadding(views.dp(20), views.dp(8), views.dp(20), 0);
            builder.setCustomTitle(heading);
        } else {
            builder.setTitle(title);
        }
        var dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        form.requestFocus();
        dialog.setOnDismissListener(ignored -> opener.post(() -> {
            if (!opener.isAttachedToWindow()) return;
            if (!opener.isInTouchMode()) opener.requestFocus();
            opener.requestRectangleOnScreen(new Rect(0, 0, opener.getWidth(), opener.getHeight()), true);
        }));
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            try {
                int amount = Integer.parseInt(input.getText().toString());
                if (submit.test(new LocalHoldemGame.Move(option.type(), amount))) {
                    dialog.dismiss();
                    return;
                }
            } catch (NumberFormatException invalid) {
                // Invalid text never reaches the engine.
            }
            error.setVisibility(View.VISIBLE);
        });
    }

    private static int actionLabel(LegalAction option) {
        return actionLabel(option.type());
    }

    private static int actionLabel(ActionType action) {
        return switch (action) {
            case FOLD -> R.string.fold;
            case CHECK -> R.string.check;
            case CALL -> R.string.call;
            case BET -> R.string.bet;
            case RAISE -> R.string.raise;
            case ALL_IN -> R.string.all_in;
        };
    }

    private static int actionLabel(BettingAction.Kind action) {
        return switch (action) {
            case SMALL_BLIND -> R.string.ledger_small_blind;
            case BIG_BLIND -> R.string.ledger_big_blind;
            case FOLD -> R.string.fold;
            case CHECK -> R.string.check;
            case CALL -> R.string.call;
            case BET -> R.string.bet;
            case RAISE -> R.string.raise;
            case ALL_IN -> R.string.all_in;
        };
    }

    private static int seatActionLabel(BettingAction.Kind action) {
        return switch (action) {
            case SMALL_BLIND -> R.string.small_blind_short;
            case BIG_BLIND -> R.string.big_blind_short;
            case FOLD, CHECK, CALL, BET, RAISE, ALL_IN -> actionLabel(action);
        };
    }

    private String streetName(Street street) {
        return switch (street) {
            case PREFLOP -> "Preflop";
            case FLOP -> "Flop";
            case TURN -> "Turn";
            case RIVER -> "River";
            case SHOWDOWN -> "Showdown";
            case COMPLETE -> text.get(R.string.settled);
        };
    }

    static CardFace face(Card card) {
        String rank = card.rank() == Card.Rank.TEN ? "10" : card.label().substring(0, 1);
        return switch (card.suit()) {
            case CLUBS -> new CardFace(rank, "♣", false);
            case DIAMONDS -> new CardFace(rank, "♦", true);
            case HEARTS -> new CardFace(rank, "♥", true);
            case SPADES -> new CardFace(rank, "♠", false);
        };
    }
}
