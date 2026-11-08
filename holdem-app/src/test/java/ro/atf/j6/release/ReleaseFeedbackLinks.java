package ro.atf.j6.release;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Resolves the local inline Markdown links used by the feedback documents. */
final class ReleaseFeedbackLinks {
    private static final Pattern LINK = Pattern.compile(
            "(?<![!\\\\])\\[([^\\]\\r\\n]+)]\\(\\s*(?:<([^>]+)>|((?:[^\\s()]|\\([^()\\s]*\\))+))"
                    + "(?:\\s+\"[^\"]*\")?\\s*\\)");
    private static final Pattern INLINE_CODE = Pattern.compile("(`+).*?\\1");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?(?:-->|\\z)");
    private static final Pattern HEADING = Pattern.compile("^ {0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern SETEXT = Pattern.compile("^ {0,3}(?:=+|-+)\\s*$");
    private static final Pattern CUSTOM_ANCHOR = Pattern.compile("(?i)<a\\b[^>]*\\s(?:name|id)=[\"']([^\"']+)[\"']");
    private static final Pattern EMPHASIS = Pattern.compile("(?<![\\p{L}\\p{N}])_+([^_]+)_+(?![\\p{L}\\p{N}])");

    private ReleaseFeedbackLinks() { }

    static void appendFindings(ArrayNode findings, Path root, Set<String> tracked, List<Feedback> documents,
                               String commit, UnaryOperator<String> redact) throws Exception {
        var targets = new HashMap<String, Target>();
        for (Feedback feedback : documents) {
            String[] original = feedback.content().split("\\R", -1);
            String[] lines = visibleLines(feedback.content());
            for (int i = 0; i < lines.length; i++) {
                var links = LINK.matcher(INLINE_CODE.matcher(lines[i]).replaceAll("code"));
                while (links.find()) {
                    String raw = links.group(2) != null ? links.group(2) : links.group(3);
                    URI link = localLink(raw);
                    if (link == null) continue;
                    String targetPath = resolve(feedback.path(), link.getPath());
                    String fragment = link.getFragment();
                    Target target = null;
                    String kind;
                    if (!tracked.contains(targetPath)) {
                        String prefix = targetPath + "/";
                        if (targetPath.isEmpty() || tracked.stream().anyMatch(path -> path.startsWith(prefix))) continue;
                        kind = "MISSING_FEEDBACK_LINK";
                    } else {
                        if (fragment == null || fragment.isEmpty() || !targetPath.endsWith(".md")) continue;
                        if (!targets.containsKey(targetPath)) targets.put(targetPath, readTarget(root, targetPath));
                        target = targets.get(targetPath);
                        if (target == null || target.anchors().contains(fragment)) continue;
                        kind = "MISSING_FEEDBACK_ANCHOR";
                    }
                    var finding = findings.addObject();
                    finding.put("kind", kind);
                    finding.put("targetPath", redact.apply(targetPath));
                    if (fragment != null && !fragment.isEmpty()) finding.put("fragment", redact.apply(fragment));
                    var citations = finding.putArray("citations");
                    var source = citations.addObject();
                    source.put("role", "feedback");
                    source.put("path", feedback.path());
                    source.put("startLine", i + 1);
                    source.put("endLine", i + 1);
                    source.put("sourceSha256", feedback.digest());
                    source.put("indexedCommit", commit);
                    source.put("excerpt", redact.apply(original[i].strip()));
                    if (target != null) {
                        var destination = citations.addObject();
                        destination.put("role", "target");
                        destination.put("path", redact.apply(targetPath));
                        destination.put("sourceSha256", target.digest());
                        destination.put("indexedCommit", commit);
                    }
                }
            }
        }
    }

    private static Target readTarget(Path root, String targetPath) throws Exception {
        try {
            Path file = root.resolve(targetPath).toRealPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
            byte[] bytes = Files.readAllBytes(file);
            return new Target(anchors(new String(bytes, StandardCharsets.UTF_8)), ReleaseImpactRag.digest(bytes));
        } catch (IOException unavailable) {
            return null;
        }
    }

    private static URI localLink(String raw) {
        try {
            URI link = URI.create(raw.replace(" ", "%20"));
            return link.getScheme() == null && link.getRawAuthority() == null ? link : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String resolve(String source, String path) {
        if (path == null || path.isEmpty()) return source;
        try {
            Path relative = path.startsWith("/") ? Path.of(path.substring(1)) : Path.of(source).getParent().resolve(path);
            return relative.normalize().toString().replace('\\', '/');
        } catch (InvalidPathException invalid) {
            return path;
        }
    }

    private static String[] visibleLines(String content) {
        String prose = String.join("\n", ReleaseDocumentReview.proseLines(content));
        return COMMENT.matcher(prose).replaceAll(match -> "\n".repeat((int) match.group().chars()
                .filter(ch -> ch == '\n').count())).split("\n", -1);
    }

    private static Set<String> anchors(String content) {
        var generated = new HashSet<String>();
        var explicit = new HashSet<String>();
        String previous = "";
        for (String line : visibleLines(content)) {
            var custom = CUSTOM_ANCHOR.matcher(INLINE_CODE.matcher(line).replaceAll(""));
            while (custom.find()) explicit.add(custom.group(1));
            var heading = HEADING.matcher(line);
            String title = heading.matches() ? heading.group(1) : SETEXT.matcher(line).matches() ? previous : "";
            if (!title.isEmpty()) {
                String slug = EMPHASIS.matcher(LINK.matcher(title).replaceAll("$1")).replaceAll("$1")
                        .toLowerCase(Locale.ROOT).replace(" ", "-").replaceAll("[^\\p{L}\\p{N}\\p{M}_-]", "");
                if (!slug.isEmpty()) {
                    String anchor = slug;
                    for (int suffix = 1; !generated.add(anchor); suffix++) anchor = slug + "-" + suffix;
                }
            }
            previous = heading.matches() || SETEXT.matcher(line).matches() ? "" : line.strip();
        }
        generated.addAll(explicit);
        return generated;
    }

    record Feedback(String path, String digest, String content) { }
    private record Target(Set<String> anchors, String digest) { }
}
