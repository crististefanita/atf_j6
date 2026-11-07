package ro.atf.holdem.android;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.LegalAction;
import ro.atf.holdem.core.Street;
import ro.cardtable.ui.cards.CardFace;
import ro.cardtable.ui.cards.CardFaceMetrics;
import ro.cardtable.ui.hand.CenteredHandView;
import ro.cardtable.ui.hand.HandCard;
import ro.cardtable.ui.hand.HandFan;
import ro.cardtable.ui.hand.HandLayoutMetrics;
import ro.cardtable.ui.screens.ScreenViews;
import ro.cardtable.ui.seat.PlayerSeatFrame;
import ro.cardtable.ui.seat.PlayerSeatView;
import ro.cardtable.ui.seat.SeatContent;
import ro.cardtable.ui.table.BoardLayout;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableViewportPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;

/** Hold'em composes the center, facts and navigation around the common Android table. */
final class HoldemTableView {
    // BoardLayout gives this center the full measured width. Its width hint only has to fit that region.
    private static final int FULL_WIDTH_CENTER_HINT_DP = 1;
    interface Text { String get(int resource, Object... arguments); }

    private final ScreenViews views;
    private final Text text;

    HoldemTableView(ScreenViews views, Text text) {
        this.views = views;
        this.text = text;
    }

    void show(HoldemTableViewState frame, List<String> names, LocalHoldemGame game, Runnable home, Runnable newHand,
              Predicate<LocalHoldemGame.Move> submit) {
        var activity = views.activity();
        views.applyTableWindowChrome();
        var viewport = activity.getResources().getConfiguration();
        var policy = new TableViewportPolicy(viewport.screenHeightDp, viewport.fontScale);
        boolean compact = policy.isCompact() || viewport.screenWidthDp < 340 || names.size() == 2;
        LinearLayout root = views.gamePage();
        var scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        var content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        var header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        var menu = views.actionButton("☰", text.get(R.string.home), true, home, false, 20);
        menu.setContentDescription(text.get(R.string.home));
        menu.setTextColor(Color.WHITE);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setStateListAnimator(null);
        header.addView(menu, new LinearLayout.LayoutParams(views.dp(48), views.dp(48)));
        boolean complete = frame.street() == Street.COMPLETE;
        String street = streetName(frame.street());
        String fullTitle = text.get(R.string.table_title, street);
        if (game.isTournament()) {
            fullTitle = complete ? text.get(R.string.tournament_settled_title, game.handNumber())
                    : text.get(R.string.tournament_table_title, game.handNumber(), street);
        }
        var title = views.text(compact && !game.isTournament() ? street : fullTitle, compact ? 16 : 18, Color.WHITE, true);
        if (compact && !game.isTournament()) title.setContentDescription(fullTitle);
        title.setPadding(views.dp(10), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(header, views.matchWrap());

        var seatFrame = new PlayerSeatFrame(activity, compact);
        var seats = new ArrayList<View>(names.size());
        for (int display = 0; display < names.size(); display++) {
            int seat = frame.physicalSeat(display);
            var player = frame.seats().get(seat);
            boolean eliminated = game.eliminated(seat);
            var stack = new SeatContent.Fact(text.get(R.string.stack), Integer.toString(player.stack()));
            var chips = new SeatContent.Fact(text.get(complete ? R.string.received : R.string.committed),
                    Integer.toString(complete ? player.received() : player.contributed()));
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
            boolean active = seat == frame.actor();
            String description = name + (roles.length() == 0 ? "" : ", " + roles)
                    + ", " + stack.inlineText() + ", " + chips.inlineText()
                    + (eliminated ? ", " + text.get(R.string.eliminated) : player.folded() ? ", " + text.get(R.string.folded) : "")
                    + (active ? ", " + text.get(R.string.actor) : "");
            String heading = shortRoles.length() == 0 ? name : name + " · " + shortRoles;
            if (eliminated) heading = name + " · " + text.get(R.string.out_badge);
            if (compact && active) heading = "▸ " + heading;
            var seatContent = new SeatContent(seat, heading, List.of(stack, chips), active, description);
            var seatView = seatFrame.buildInlineFacts(seatContent, compact ? 1 : policy.seatVerticalPaddingDp());
            if (player.folded() || eliminated) seatView.setAlpha(.65f);
            seats.add(compact ? seatView : new PlayerSeatView(activity, seatView, text.get(R.string.actor), active, seat, false));
        }
        View center = board(frame, compact);
        View table = compact ? compactTable(seats, center) : sharedTable(seats, center, policy);
        content.addView(table, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (compact) {
            var hand = new LinearLayout(activity);
            hand.setGravity(Gravity.CENTER);
            for (Card card : frame.ownCards()) addCard(hand, card, 56, R.string.hand);
            content.addView(hand, views.matchWrap());
        } else {
            addHandFan(content, frame.ownCards());
        }

        if (complete) {
            boolean finished = game.outcome() != null;
            views.addButton(content, text.get(finished ? R.string.tournament_result : R.string.new_hand),
                    finished ? "tournament-result" : "new-hand", true, newHand, true);
        } else {
            actions(content, frame.legalActions(), compact, submit);
        }
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

    private View sharedTable(List<View> seats, View center, TableViewportPolicy policy) {
        var board = new BoardLayout(views.activity(), seats.size(), FULL_WIDTH_CENTER_HINT_DP, policy,
                true, TableGeometry.CenterAlignment.BALANCED);
        seats.forEach(board::addView);
        board.addView(center);
        return board;
    }

    private View compactTable(List<View> seats, View center) {
        var table = new LinearLayout(views.activity());
        table.setOrientation(LinearLayout.VERTICAL);
        var rows = CompactSeatRows.forSeats(seats.size());
        table.addView(seatRow(seats, rows.top()), views.matchWrap());
        table.addView(new Space(views.activity()), new LinearLayout.LayoutParams(1, 0, 1));
        table.addView(center, views.matchWrap());
        table.addView(new Space(views.activity()), new LinearLayout.LayoutParams(1, 0, 1));
        table.addView(seatRow(seats, rows.bottom()), views.matchWrap());
        return table;
    }

    private View seatRow(List<View> seats, int[] positions) {
        var row = new LinearLayout(views.activity());
        row.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        for (int seat : positions) {
            View child = seat == CompactSeatRows.EMPTY ? new Space(views.activity()) : seats.get(seat);
            var slot = positions.length == 1
                    ? new LinearLayout.LayoutParams(views.dp(148), ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            slot.setMargins(views.dp(2), 0, views.dp(2), 0);
            row.addView(child, slot);
        }
        return row;
    }

    private View board(HoldemTableViewState frame, boolean compact) {
        var activity = views.activity();
        var center = new LinearLayout(activity);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(0, 0, 0, views.dp(compact ? 2 : 5));
        var pot = views.text(text.get(frame.street() == Street.COMPLETE ? R.string.settled_pot : R.string.pot, frame.pot()),
                compact ? 15 : 20, Color.WHITE, true);
        pot.setGravity(Gravity.CENTER);
        center.addView(pot, views.matchWrap());
        var row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER);
        for (int index = 0; index < 5; index++) {
            Card card = index < frame.board().size() ? frame.board().get(index) : null;
            addCard(row, card, 48, R.string.board);
        }
        center.addView(row, views.matchWrap());
        return center;
    }

    private void addCard(LinearLayout row, Card card, int height, int description) {
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
        var metrics = CardFaceMetrics.atHeight(height);
        var box = new LinearLayout.LayoutParams(views.dp(metrics.widthDp()), views.dp(metrics.heightDp()));
        box.setMargins(views.dp(2), views.dp(4), views.dp(2), views.dp(2));
        row.addView(cell, box);
    }

    private String cardDescription(int description, CardFace face) {
        return text.get(description) + " · " + face.rankLabel() + face.suitSymbol();
    }

    private void actions(LinearLayout parent, List<LegalAction> legal, boolean compact,
                         Predicate<LocalHoldemGame.Move> submit) {
        LinearLayout row = null;
        for (int i = 0; i < legal.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(views.activity());
                if (compact) row.setBaselineAligned(false);
                parent.addView(row, views.matchWrap());
            }
            var option = legal.get(i);
            boolean sized = option.minAmount() != option.maxAmount();
            String title = text.get(actionLabel(option));
            String label = !sized && option.amount() > 0 ? text.get(R.string.action_amount, title, option.amount()) : title;
            boolean primary = switch (option.type()) {
                case CHECK, CALL -> true;
                default -> false;
            };
            var button = views.actionButton(label, option.type().name(), true, () -> {
                if (sized) requestAmount(title, option, compact, submit);
                else submit.test(new LocalHoldemGame.Move(option.type(), option.amount()));
            }, primary, 14);
            if (compact) {
                button.setPadding(views.dp(4), 0, views.dp(4), 0);
                button.setIncludeFontPadding(false);
                button.setMaxLines(1);
                button.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            }
            var box = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int verticalMargin = views.dp(compact ? 1 : 3);
            box.setMargins(views.dp(3), verticalMargin, views.dp(3), verticalMargin);
            row.addView(button, box);
        }
    }

    private void requestAmount(String title, LegalAction option, boolean compact,
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
        var dialog = builder.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        return switch (option.type()) {
            case FOLD -> R.string.fold;
            case CHECK -> R.string.check;
            case CALL -> R.string.call;
            case BET -> R.string.bet;
            case RAISE -> R.string.raise;
            case ALL_IN -> R.string.all_in;
        };
    }

    private String streetName(Street street) {
        return switch (street) {
            case PREFLOP -> "Preflop";
            case FLOP -> "Flop";
            case TURN -> "Turn";
            case RIVER -> "River";
            case SHOWDOWN -> "Showdown";
            case COMPLETE -> text.get(R.string.hand_complete);
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
