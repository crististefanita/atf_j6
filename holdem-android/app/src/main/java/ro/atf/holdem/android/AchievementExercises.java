package ro.atf.holdem.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HandRank;

/** Deterministic exercises. Each context and stage has its own sequence of public inputs. */
final class AchievementExercises {
    record Question(String prompt, List<String> options, int correct, String explanation) {}

    private final AchievementCatalog.Entry entry;
    private final int attempt;
    private final int context;
    private final int stage;
    private final int sample;

    private AchievementExercises(AchievementCatalog.Entry entry, int attempt) {
        this.entry = entry;
        this.attempt = attempt;
        context = entry.contextIndex() - 1;
        stage = switch (entry.tier()) {
            case EASY -> 0;
            case MEDIUM -> entry.stage();
            case HARD -> 3;
        };
        sample = (int) ((attempt * 37L + context * 13 + stage * 29) % 997);
    }

    static Question question(AchievementCatalog.Entry entry, int attempt) {
        return new AchievementExercises(entry, attempt).create();
    }

    private Question create() {
        return switch (entry.family()) {
            case "pozitie" -> position();
            case "showdown" -> showdown();
            case "marime" -> sizing();
            case "blinduri" -> blinds();
            case "allin" -> reopening();
            case "poturi" -> pots();
            case "egalitate" -> split();
            case "outs" -> outs();
            case "preflop" -> preflop();
            case "textura" -> texture();
            case "range" -> range();
            case "valoare" -> value();
            case "disciplina" -> discipline();
            case "bluff" -> bluff();
            case "shortstack" -> shortStack();
            case "heads-up" -> headsUp();
            case "cash" -> cash();
            case "turneu" -> tournament();
            case "review" -> review();
            default -> throw new IllegalArgumentException("Unsupported achievement family " + entry.family());
        };
    }

    private Question position() {
        int seats = 6 - context;
        int button = (sample + attempt / 6) % seats;
        int offset = new int[]{3, 4, 0, 1, 0}[context];
        int target = (button + offset) % seats;
        String role = new String[]{"UTG", "cutoff", "buton", "small blind", "butonul care plătește small blind"}[context];
        var occupied = new ArrayList<String>();
        int chair = 1 + sample % 3;
        for (int i = 0; i < seats; i++) {
            occupied.add(Integer.toString(chair));
            chair += stage == 0 ? 1 : 1 + (sample >> (2 * i) & 3);
        }
        return choices("Locuri ocupate în sens orar: " + String.join(", ", occupied)
                        + ". Butonul e la locul " + occupied.get(button) + ". Unde este " + role + "?",
                "Parcurge doar locurile ocupate. " + role + " se află la locul " + occupied.get(target) + ".",
                occupied.get(target), occupied.get((target + 1) % seats),
                seats == 2 ? "Depinde de cărți" : occupied.get((target + 2) % seats));
    }

    private Question showdown() {
        String[][] deals = {
                {"As 9d 7c 4h 2s", "Ah Kd", "Ad Qd"},
                {"As Ks Qs Js Ts", "2c 3d", "8c 9d"},
                {"As 2d 3c 4h 8s", "5d Kc", "Ah Qc"},
                {"Ks Kd 7c 7h 2s", "Kh 3d", "7s 4c"},
                {"As 9s 7s 4h 2d", "Ks 3s", "Qs Js"}
        };
        String[] deal = deals[context];
        String board = rotateSuits(deal[0]);
        String first = rotateSuits(deal[1 + sample % 2]);
        String second = rotateSuits(deal[2 - sample % 2]);
        var left = rank(board, first);
        var right = rank(board, second);
        int comparison = Integer.compare(left.score(), right.score());
        String[] labels = {"Jucătorul A", "Jucătorul B", "Egalitate"};
        int winner = comparison > 0 ? 0 : comparison < 0 ? 1 : 2;
        return fromOptions("Board " + board + ". A are " + first + ", B are " + second + ". Cine câștigă?",
                "Compară cele mai bune cinci cărți. A: " + cards(left.bestFive()) + ". B: " + cards(right.bestFive()) + ".",
                labels, winner);
    }

    private static ro.atf.holdem.core.HandEvaluation rank(String board, String hand) {
        return HandRank.evaluateBestOf(Arrays.stream((board + " " + hand).split(" "))
                .map(Card::fromLabel).collect(java.util.stream.Collectors.toList()));
    }

    private static String cards(List<Card> cards) {
        return cards.stream().map(Card::label).collect(java.util.stream.Collectors.joining(" "));
    }

    private String rotateSuits(String labels) {
        int permutation = (attempt + 7 * stage + 11 * context) % 24;
        String suits = "cdhs";
        var remaining = new StringBuilder(suits);
        var order = new StringBuilder();
        while (remaining.length() != 0) {
            int next = permutation % remaining.length();
            permutation /= remaining.length();
            order.append(remaining.charAt(next));
            remaining.deleteCharAt(next);
        }
        var cards = new ArrayList<String>();
        for (String label : labels.split(" ")) {
            cards.add(label.substring(0, 1) + order.charAt(suits.indexOf(label.charAt(1))));
        }
        return String.join(" ", cards);
    }

    private Question sizing() {
        int blind = 12 + sample;
        int lastFull = blind + (stage > 0 ? blind / 2 : 0);
        int current = switch (context) {
            case 0 -> 0;
            case 1 -> blind;
            case 2 -> blind + lastFull;
            case 3 -> blind + lastFull + Math.max(1, lastFull / 3);
            default -> blind + lastFull;
        };
        int minimum = current + (context < 2 ? blind : lastFull);
        if (context == 4) {
            int stack = minimum - 1 - sample % 4;
            return number("Ai contribuit 0. Pariul curent este " + current + ", raise-ul complet cere "
                    + minimum + " în total. Stack " + stack + ". La ce total poți mări doar prin all-in?",
                    stack, "Un stack insuficient pentru minim poate mări doar angajând toate jetoanele.");
        }
        return number("Blind mare " + blind + ", pariu curent " + current + ". Ultimul increment complet "
                        + (context < 2 ? blind : lastFull) + ". La ce total este minimul legal?",
                minimum, "Minimul adaugă ultimul increment complet la totalul curent.");
    }

    private Question blinds() {
        int base = 10 + sample;
        int level = 1 + sample % (stage + 2);
        int interval = 30 + sample % 61;
        int elapsed = switch (context) {
            case 0 -> interval - 1;
            case 1, 3 -> interval * level;
            default -> interval * (level - 1) + interval / 2;
        };
        int eliminated = context == 2 || context == 3 ? 1 : 0;
        int timed = elapsed / interval;
        int index = timed + eliminated;
        return number("Regula acestei structuri: +1 nivel la fiecare " + interval + " secunde active și +1 per eliminare. "
                        + "BB începe la " + base + " și crește cu " + base + " la fiecare nivel. Timp activ " + elapsed
                        + " s, eliminări " + eliminated + (context == 4 ? ", plus o pauză care nu contează" : "")
                        + ". Cât este BB la mâna următoare?",
                base * (index + 1), "Se adună " + timed + " trepte de timp și " + eliminated + " din eliminări, apoi se aplică grila dată.");
    }

    private Question reopening() {
        int increment = 12 + sample;
        int base = increment * 3;
        int first = increment / 3;
        int second = switch (context) {
            case 1 -> Math.max(1, increment - first - 1);
            case 2 -> increment - first;
            default -> 0;
        };
        int added = context == 3 ? increment : first + second;
        boolean prior = context != 4;
        int current = base + added;
        String prompt = (prior ? "Ai răspuns la totalul " + base : "Nu ai acționat încă") + ". Ultimul raise complet "
                + increment + ". " + (context == 3 ? "Raise complet nou " + added
                : "Un all-in scurt adaugă " + first + (second > 0 ? ", iar al doilea adaugă " + second : ""))
                + ". Total curent " + current + ". Poți face raise, dacă stackul permite?";
        return choices(prompt, !prior ? "Jucătorul care nu a acționat încă își păstrează dreptul de raise."
                        : "Compară totalul nou cu ultima ta acțiune. Diferența " + added + " trebuie să atingă " + increment + ".",
                !prior || added >= increment ? "Da" : "Nu", !prior || added >= increment ? "Nu" : "Depinde de poziție",
                !prior || added >= increment ? "Depinde de poziție" : "Da");
    }

    private Question pots() {
        int a = 10 + sample;
        int b = 5 + sample % 17;
        int c = 3 + sample % 11;
        String stakes = "A a contribuit " + a + ", B " + (a + b) + ", C " + (a + b + c);
        return switch (context) {
            case 0 -> number(stakes + ". Cât este potul principal?", 3 * a,
                    "Primul strat include câte " + a + " de la toți cei trei.");
            case 1 -> number(stakes + ". Cât are potul secundar disputat de B și C?", 2 * b,
                    "B și C contribuie peste plafonul lui A până la plafonul lui B.");
            case 2 -> number(stakes + ", D " + (a + b + c) + ". Cât are al doilea pot secundar?", 2 * c,
                    "Deasupra plafonului lui B rămân C și D, fiecare cu câte " + c + ".");
            case 3 -> number(stakes + ". Câte jetoane i se restituie lui C ca pariu neacoperit?", c,
                    "Niciun alt jucător nu acoperă stratul de deasupra contribuției lui B.");
            default -> number(stakes + ", D " + a + " și a dat fold. Cât este potul principal?", 4 * a,
                    "Jetoanele deja plătite de D intră în pot, dar D nu poate primi potul.");
        };
    }

    private Question split() {
        int share = 7 + sample;
        int seats = context == 1 ? 3 : 2;
        int remainder = context >= 2 && context <= 3 ? 1 : 0;
        if (context == 4) {
            int main = 3 * share;
            int side = 2 * (share + stage + 1);
            return number("A câștigă potul principal " + main + ". B și C împart potul secundar " + side
                    + ". Cât primește B din decontare?", side / 2, "Fiecare pot se decontează separat.");
        }
        int pot = share * seats + remainder;
        return number((context == 3 ? "Pot secundar" : "Pot principal") + " de " + pot + ", egalitate între " + seats
                        + " jucători. A este primul dintre câștigători după buton. Cât primește A?",
                share + remainder, "Împarte întreg la " + seats + ". Primul câștigător eligibil după buton ia jetonul impar, dacă există.");
    }

    private Question outs() {
        int clean = new int[]{9, 8, 4, 15, 9}[context];
        int spoiled = context == 4 ? 1 + sample % (2 + stage) : stage == 0 ? 0 : sample % (stage + 1);
        int overlap = context == 3 ? 2 : 0;
        int shown = clean + overlap;
        return number("Pe turn ai " + shown + " cărți candidate"
                        + (overlap == 0 ? "" : ", dintre care " + overlap + " sunt numărate de două ori")
                        + ". " + spoiled + " dintre cărțile distincte nu ar produce mâna câștigătoare. Câte outs curate rămân?",
                clean - spoiled, "Deduplică întâi, apoi scade cărțile care îmbunătățesc mâna fără să o facă câștigătoare.");
    }

    private Question number(String prompt, int answer, String explanation) {
        int step = 1 + sample % 5;
        return choices(prompt, explanation + " Răspuns: " + answer + ".", Integer.toString(answer),
                Integer.toString(answer + step), Integer.toString(answer > step ? answer - step : answer + 2 * step));
    }

    private Question choices(String prompt, String explanation, String correct, String first, String second) {
        var options = new ArrayList<>(List.of(correct, first, second));
        int shift = (sample + attempt / 2 + stage) % options.size();
        Collections.rotate(options, shift);
        return new Question(prompt, Collections.unmodifiableList(options), shift, explanation);
    }

    private Question fromOptions(String prompt, String explanation, String[] labels, int correct) {
        return choices(prompt, explanation, labels[correct], labels[(correct + 1) % 3], labels[(correct + 2) % 3]);
    }

    private Question preflop() {
        String[] actions = switch (context) {
            case 0, 1, 2 -> new String[]{"Fold", "Limp", "Open raise"};
            case 3 -> new String[]{"Fold", "Apărare prin call", "3-bet"};
            default -> new String[]{"Fold", "Call la 3-bet", "4-bet"};
        };
        int unit = 1 + sample % (5 + stage * 3);
        int passive = 2 * unit * (sample % 7 - 3);
        int aggressive = 2 * unit * ((sample + 3) % 7 - 3) + 1;
        int[] ev = {0, passive == 0 ? -2 : passive, aggressive};
        return bestEv("Stack efectiv " + (30 + sample % 90)
                + " BB. Modelul de range și reacții estimează următoarele valori nete în zecimi de BB.", actions, ev);
    }

    private Question texture() {
        String[][] boards = {
                {"As 8d 3c", "Kd 7c 2h", "Qs 9d 4h"},
                {"8s 9d Tc", "5d 6c 7h", "Js Qd Kc"},
                {"As 9s 3s", "Kd 7d 2d", "Qh Th 6h"},
                {"8s 9d 2c Th", "5d 6c Kh 7s", "Js Qd 3c Kh"},
                {"As 8d 3c Jc 8h", "Kd 7c 2h Qs 7d", "Qs 9d 4h Ac 4c"}
        };
        int variant = (sample + attempt / 4) % 3;
        String actual = rotateSuits(boards[context][variant]);
        if (stage == 0) {
            String[] labels = {"Flop uscat", "Flop conectat", "Flop monotone", "Turn conectat", "River pereche"};
            return choices("Clasifică textura boardului " + actual + ".",
                    "Caută distanțele între ranguri, culorile comune și perechile adăugate pe strada curentă.",
                    labels[context], labels[(context + 1) % 5], labels[(context + 3) % 5]);
        }
        int first = 3 + sample % 17;
        int second = 3 + (sample / 2 + 4) % 17;
        if (first == second) second++;
        return choices("Pe boardul " + actual + ", două range-uri au câte 40 de combinații. Range-ul A are "
                        + first + " combinații de vârf, iar B are " + second + ". Cine are avantaj la vârful range-ului?",
                "Cu același număr total de combinații, compară direct numărul de mâini din vârf.",
                first > second ? "A" : "B", first > second ? "B" : "A", "Egalitate");
    }

    private Question range() {
        int strong = 2 + sample % (8 + stage * 4);
        int medium = 2 + (sample / 3) % (12 + stage * 4);
        int bluff = 2 + (sample / 5) % (14 + stage * 4);
        int strongRate = 1 + sample % 8;
        int mediumRate = 1 + (sample + 2) % 8;
        int bluffRate = 1 + (sample + 5) % 8;
        int[] weights = {strong * strongRate, medium * mediumRate, bluff * bluffRate};
        if (weights[0] == weights[1] || weights[0] == weights[2] || weights[1] == weights[2]) {
            return number("Modelul estimează " + strong + " combinații puternice, cu frecvența acțiunii "
                    + strongRate * 10 + "%. Care este ponderea lor, în zecimi de combinație?",
                    weights[0], "Ponderea înmulțește combinațiile cu frecvența acțiunii.");
        }
        return bestEv("După acțiunea adversarului, folosește ponderea combinații × frecvență. "
                        + "Puternice " + strong + " × " + strongRate * 10 + "%, medii " + medium + " × "
                        + mediumRate * 10 + "%, bluff " + bluff + " × " + bluffRate * 10
                        + "%. Care grup are ponderea cea mai mare? Modelul exclude blocaje suplimentare.",
                new String[]{"Puternice", "Medii", "Bluff"}, weights, false);
    }

    private Question value() {
        int bet = 20 + sample;
        int weakCalls = 15 + (sample * 3) % 65;
        int betterCalls = 1 + (sample * 7) % (99 - weakCalls);
        if (weakCalls == betterCalls) betterCalls++;
        return choices("Pe river compari check și bet " + bet + " jetoane. Din 100 de cazuri, "
                        + weakCalls + " sunt call-uri de la mâini mai slabe și " + betterCalls
                        + " de la mâini mai bune. Folosește doar diferența dată de call-uri, fără fold equity, raise sau egalități. "
                        + "Ce alegi?",
                "Câștigi mărimea pariului la call-urile mai slabe și o pierzi la cele mai bune. Bilanțul pe 100 de cazuri este "
                        + (weakCalls - betterCalls) * bet + ".",
                weakCalls > betterCalls ? "Bet" : "Check", weakCalls > betterCalls ? "Check" : "Bet", "Echivalente");
    }

    private Question discipline() {
        int cost = 10 + sample;
        int pot = cost * (2 + context);
        int threshold = 100 * cost / (pot + cost);
        int equity = Math.max(1, threshold - (1 + sample % (9 - stage)));
        return number("Call-ul costă " + cost + " pentru potul eligibil de " + pot + ". Echitate " + equity
                        + "%. Fold are EV zero. Cu câte sutimi de jeton este fold mai bun decât call?",
                100 * cost - (pot + cost) * equity,
                "EV(call) = echitate × (pot + cost) − cost. Exprimă diferența față de zero în sutimi.");
    }

    private Question bluff() {
        int pot = 30 + sample;
        int bet = 15 + (sample + context * 11) % 180;
        int folds = 15 + (sample * 7) % 71;
        int equity = context == 1 ? 20 + sample % 12 : 0;
        int value = folds * pot * 100 + (100 - folds) * (equity * (pot + 2 * bet) - 100 * bet);
        String right = value > 0 ? "Bluff" : value < 0 ? "Renunță" : "Echivalente";
        String[] options = {"Bluff", "Renunță", "Echivalente"};
        int index = Arrays.asList(options).indexOf(right);
        return fromOptions("Pot " + pot + ", bet " + bet + ", fold adversar " + folds
                        + "%, echitate la call " + equity + "%. Linia pasivă are EV zero în model. Ce alegi?",
                "La fold câștigi potul. La call valoarea este echitate × (pot + 2 × bet) − bet. Media ponderată decide.",
                options, index);
    }

    private Question shortStack() {
        int[] stacks = {3 + sample % 3, 6 + sample % 3, 9 + sample % 4, 6 + sample % 7, 5 + sample % 8};
        int stack = stacks[context];
        int dead = 2 + (sample / 7) % 3;
        int equity = 24 + sample % 31;
        int foldRate = context == 4 ? 0 : 12 + (sample * 3) % 55;
        int ev = foldRate * dead * 100 + (100 - foldRate) * (equity * (dead + 2 * stack) - 100 * stack);
        String push = context == 4 ? "Call all-in" : context == 3 ? "Reshove" : "Shove";
        return fromOptions("Risc efectiv " + stack + " BB, jetoane moarte " + dead + " BB în afara celor două mize de "
                        + stack + " BB. Fold adversar " + foldRate + "%, echitate dacă se ajunge la showdown "
                        + equity + "%. Compară cu fold (EV zero) în modelul chip EV.",
                "Ponderează rezultatul imediat cu probabilitatea de fold și EV-ul potului final cu probabilitatea de showdown.",
                new String[]{push, "Fold", "Echivalente"}, ev > 0 ? 0 : ev < 0 ? 1 : 2);
    }

    private Question headsUp() {
        if (context < 3) {
            boolean postflop = context == 2 || stage > 0 && sample % 2 == 1;
            String[] roles = {"Butonul (SB)", "Big blind", "Acționează simultan"};
            int button = 1 + sample % 9;
            int other = 1 + (button + 1 + sample / 9) % 9;
            if (other == button) other = other % 9 + 1;
            roles[0] = "Locul " + button;
            roles[1] = "Locul " + other;
            return fromOptions("Heads-up. Buton la locul " + button + ", adversar la locul " + other
                            + ". Cine acționează primul " + (postflop ? "postflop" : "preflop") + "?",
                    "În heads-up, butonul plătește SB și acționează primul preflop. Big blind acționează primul postflop.",
                    roles, postflop ? 1 : 0);
        }
        int effective = context == 3 ? 70 + sample % 40 : 4 + sample % 8;
        int cover = effective + 1 + (sample / 5) % 40;
        boolean buttonShort = sample % 2 == 0;
        return number("Heads-up. Buton " + (buttonShort ? effective : cover) + " BB, big blind "
                        + (buttonShort ? cover : effective) + " BB înaintea pariului. Care este stackul efectiv?",
                effective, "La doi jucători, fiecare poate risca cel mult stackul celui mai scurt.");
    }

    private Question cash() {
        int stack = new int[]{40, 100, 100, 200, 160}[context];
        int pot = 6 + sample % 37;
        int cost = 2 + (sample / 4) % (pot + stage + 3);
        int fee = stage == 0 ? 0 : 1 + sample % 2;
        int equity = 15 + (sample * 3) % 70;
        int edge = equity * (pot + cost - fee) - 100 * cost;
        return fromOptions("Cash " + stack + " BB. Pot eligibil " + pot + " BB, call " + cost + " BB, cost fix "
                        + fee + " BB din potul final dacă mergi la showdown. Echitate " + equity + "%. Compară cu fold.",
                "EV(call) în sutimi de BB este " + edge + ". Scade costul din potul final înainte să aplici echitatea.",
                new String[]{"Call", "Fold", "Echivalente"}, edge > 0 ? 0 : edge < 0 ? 1 : 2);
    }

    private Question tournament() {
        int[] counts = {6, 5, 4, 3, 2};
        int survival = 15 + sample % 60;
        int win = survival + 5 + (sample / 3) % 20;
        int lose = context == 3 ? 0 : sample % (survival / 2);
        int equity = 35 + sample % 60;
        int diff = equity * win + (100 - equity) * lose - 100 * survival;
        return fromOptions("Turneu cu " + counts[context]
                        + " jucători. Modelul de premii estimează valoarea fold-ului la " + survival
                        + " puncte, victoria după call la " + win + ", iar înfrângerea la " + lose
                        + ". Șansă de victorie " + equity + "%. Care este linia cu valoarea premiilor mai mare?",
                "Valoarea turneului este dată de modelul de premii. Media ponderată pentru call este "
                        + (equity * win + (100 - equity) * lose) + " sutimi de punct.",
                new String[]{"Call", "Fold", "Echivalente"}, diff > 0 ? 0 : diff < 0 ? 1 : 2);
    }

    private Question review() {
        int x = 8 + sample;
        int y = 1 + sample % 13;
        return switch (context) {
            case 0 -> number("Jurnal preflop: contribui deja " + y + " și dai call la totalul " + x
                            + ". Câte jetoane noi trebuie să apară în jurnal?",
                    x - y, "Jurnalul notează delta, nu totalul contribuției.");
            case 1 -> number("Jurnal flop: total curent " + x + ", ultimul increment complet " + y
                            + ". Ce total minim trebuie să permită editorul pentru un raise complet?",
                    x + y, "Adaugă incrementul la pariul curent.");
            case 2 -> number("Jurnal turn: pot eligibil " + 3 * x + ", cost call " + x
                            + ". Care este echitatea minimă exactă în procente?",
                    25, "Call-ul reprezintă un sfert din potul final.");
            case 3 -> number("Jurnal river: pot eligibil " + 2 * x + ", call " + x
                            + ", echitate 40%. Care este EV(call) în sutimi de jeton?",
                    20 * x, "40% din potul final, minus costul call-ului.");
            default -> number("Decontare: total contribuit " + (3 * x + y) + ", pariu neacoperit "
                            + y + " restituit. Câte jetoane trebuie să însumeze premiile?",
                    3 * x, "Premiile și restituirile trebuie să refacă totalul contribuit.");
        };
    }

    private Question bestEv(String prompt, String[] labels, int[] values) {
        return bestEv(prompt, labels, values, true);
    }

    private Question bestEv(String prompt, String[] labels, int[] values, boolean showValues) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        String breakdown = labels[0] + " " + values[0] + ", " + labels[1] + " " + values[1] + ", "
                + labels[2] + " " + values[2] + ".";
        return fromOptions(showValues ? prompt + " " + breakdown + " Alege valoarea maximă." : prompt,
                "Valorile comparate sunt " + breakdown, labels, best);
    }
}
