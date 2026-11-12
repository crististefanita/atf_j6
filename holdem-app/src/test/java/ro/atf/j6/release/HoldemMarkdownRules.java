package ro.atf.j6.release;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Local rules for the Markdown forms used in the Hold’em documentation. */
final class HoldemMarkdownRules {
    private static final int MAX_SENTENCES = 3;
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile(
            "\\[[^]\\r\\n]*]\\((<[^>]+>|[^)\\s]+)(?:\\s+\"[^\"]*\")?\\)|^\\s*\\[[^]]+]:\\s*(<[^>]+>|\\S+)");
    private static final Pattern EXTERNAL_LINK = Pattern.compile("(?i)^(https?://|mailto:).*");
    private static final Pattern STRUCTURE = Pattern.compile("^(?:#{1,6}\\s|[-+*]\\s|\\d+[.)]\\s|[|><])");
    private static final Pattern SENTENCE = Pattern.compile("[.!?]+(?=\\s|$)");
    private static final Pattern ID = Pattern.compile("<a\\s+(?:id|name)=\"([^\"]+)\"");

    private final Path root;
    private final Map<Path, Set<String>> anchors = new HashMap<>();

    HoldemMarkdownRules(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    List<Issue> inspect(Path file) throws IOException {
        var issues = new ArrayList<Issue>();
        var lines = Files.readAllLines(file);
        var paragraphs = new StringBuilder();
        int paragraphLine = 0;
        int titleCount = 0;
        boolean inDetails = false;
        boolean inComment = false;
        Fence fence = null;
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            var fenceMatch = FENCE.matcher(raw);
            if (fence != null) {
                if (fenceMatch.matches() && fence.closes(fenceMatch.group(1), fenceMatch.group(2))) {
                    if (fence.mermaid() && !hasLegend(lines, index + 1)) {
                        issues.add(new Issue(fence.line(), "diagramă Mermaid", "adaugă o legendă imediat sub diagramă"));
                    }
                    fence = null;
                }
                continue;
            }
            if (!inComment && fenceMatch.matches()) {
                checkParagraph(issues, paragraphs, paragraphLine);
                fence = new Fence(fenceMatch.group(1), fenceMatch.group(2).strip().equals("mermaid"), index + 1);
                continue;
            }
            StringBuilder visible = new StringBuilder(raw);
            int offset = 0;
            while (offset < visible.length()) {
                int boundary = visible.indexOf(inComment ? "-->" : "<!--", offset);
                if (inComment) {
                    int end = boundary < 0 ? visible.length() : boundary + 3;
                    visible.delete(offset, end);
                } else if (boundary >= 0) {
                    offset = boundary;
                    visible.delete(boundary, boundary + 4);
                } else {
                    break;
                }
                if (boundary < 0) break;
                inComment = !inComment;
            }
            String line = visible.toString().strip();
            var heading = HEADING.matcher(line);
            if (heading.matches() && heading.group(1).length() == 1) titleCount++;
            checkLinks(file, index + 1, INLINE_CODE.matcher(line).replaceAll(""), issues);
            if (line.startsWith("<details")) inDetails = true;
            if (line.startsWith("</details")) inDetails = false;
            if (inDetails || line.isEmpty() || STRUCTURE.matcher(line).find() || raw.startsWith("    ")) {
                checkParagraph(issues, paragraphs, paragraphLine);
            } else {
                if (paragraphs.isEmpty()) paragraphLine = index + 1;
                paragraphs.append(' ').append(LINK.matcher(line).replaceAll("").strip());
            }
        }
        checkParagraph(issues, paragraphs, paragraphLine);
        if (fence != null) issues.add(new Issue(fence.line(), "bloc de cod deschis", "închide blocul cu un delimitator pereche"));
        if (titleCount != 1) issues.add(new Issue(1, "titluri H1: " + titleCount, "păstrează un singur titlu H1"));
        return List.copyOf(issues);
    }

    private void checkLinks(Path file, int line, String text, List<Issue> issues) throws IOException {
        var links = LINK.matcher(text);
        while (links.find()) {
            String destination = links.group(1) != null ? links.group(1) : links.group(2);
            if (destination.startsWith("<") && destination.endsWith(">")) {
                destination = destination.substring(1, destination.length() - 1);
            }
            if (EXTERNAL_LINK.matcher(destination).matches()) continue;
            String failure;
            try {
                failure = invalidLink(file, destination);
            } catch (IllegalArgumentException problem) {
                failure = "folosește o destinație locală validă";
            }
            if (failure != null) issues.add(new Issue(line, destination, failure));
        }
    }

    private String invalidLink(Path file, String destination) throws IOException {
        String[] parts = destination.split("#", 2);
        String relative = decode(parts[0]);
        if (relative.matches("(?i)^[a-z][a-z0-9+.-]*:.*") || relative.startsWith("/") || relative.contains("\\")) {
            return "folosește o cale relativă în repo sau un URL web";
        }
        Path target = (relative.isEmpty() ? file : file.getParent().resolve(relative)).toAbsolutePath().normalize();
        if (!target.startsWith(root)) return "păstrează destinația în repo";
        if (!Files.exists(target)) return "repară calea locală";
        if (!target.toRealPath().startsWith(root.toRealPath())) return "păstrează destinația fizică în repo";
        if (parts.length == 2 && target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md")
                && !anchors(target).contains(decode(parts[1]))) {
            return "folosește o ancoră Markdown existentă";
        }
        return null;
    }

    private Set<String> anchors(Path target) throws IOException {
        var known = anchors.get(target);
        if (known != null) return known;
        var result = new HashSet<String>();
        Fence fence = null;
        for (String line : Files.readAllLines(target)) {
            var delimiter = FENCE.matcher(line);
            if (delimiter.matches()) {
                if (fence == null) fence = new Fence(delimiter.group(1), false, 0);
                else if (fence.closes(delimiter.group(1), delimiter.group(2))) fence = null;
                continue;
            }
            if (fence != null) continue;
            var id = ID.matcher(line);
            while (id.find()) result.add(id.group(1));
            var heading = HEADING.matcher(line);
            if (heading.matches()) {
                String base = heading.group(2).toLowerCase(Locale.ROOT).replaceAll("<[^>]+>", "")
                        .replaceAll("[^\\p{L}\\p{N}\\p{M}_\\s-]", "").strip().replaceAll("\\s", "-");
                String anchor = base;
                for (int index = 1; result.contains(anchor); index++) anchor = base + "-" + index;
                result.add(anchor);
            }
        }
        anchors.put(target, result);
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static boolean hasLegend(List<String> lines, int start) {
        while (start < lines.size() && lines.get(start).isBlank()) start++;
        return start + 1 < lines.size() && lines.get(start).strip().equals("<details>")
                && lines.get(start + 1).contains("Legendă");
    }

    private static void checkParagraph(List<Issue> issues, StringBuilder text, int line) {
        long sentences = SENTENCE.matcher(text).results().count();
        if (sentences > MAX_SENTENCES) {
            issues.add(new Issue(line, sentences + " propoziții în proza de orientare",
                    "împarte explicația în paragrafe scurte, listă sau tabel"));
        }
        text.setLength(0);
    }

    record Issue(int line, String actual, String fix) {}

    private record Fence(String delimiter, boolean mermaid, int line) {
        boolean closes(String candidate, String suffix) {
            return candidate.charAt(0) == delimiter.charAt(0) && candidate.length() >= delimiter.length() && suffix.isBlank();
        }
    }
}
