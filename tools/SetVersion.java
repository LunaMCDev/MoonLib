import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Changes MoonLib's version everywhere in one go.
 *
 * <p>The version lives in exactly one place that the build reads: {@code <version>} in the root
 * {@code pom.xml}. The library writes it into its jar, so {@code MoonLib.VERSION} and the
 * shared-mode version check follow automatically. What cannot read the pom (the install snippets in
 * the README and docs, SECURITY.md and the bug-report template) is updated by this tool. Plugins,
 * including MoonLib-Example and MoonLib-Template, set the MoonLib version they use in their own
 * {@code <moonlib.version>}.</p>
 *
 * <pre>
 *   java tools/SetVersion.java 1.3.0-beta     set a new version everywhere
 *   java tools/SetVersion.java --check        fail if anything disagrees with pom.xml (used by CI)
 *   ant set-version                           the same, asking for the version (IntelliJ: Ant window)
 *   mvn validate -Pset-version -Dto=1.3.0     the same, through Maven
 * </pre>
 */
public final class SetVersion {

    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?");
    private static final String V = "(\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?)";

    /** One kind of place a version appears: a regex whose group 1 is the version. */
    private static final class Rule {
        final String files;
        final Pattern pattern;
        final boolean minorOnly;

        Rule(String files, String regex, boolean minorOnly) {
            this.files = files;
            this.pattern = Pattern.compile(regex);
            this.minorOnly = minorOnly;
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // the source of truth: the library's own <version>
        RULES.add(new Rule("pom.xml", "<artifactId>moonlib</artifactId>\\s*<version>" + V + "</version>", false));
        // install snippets in the docs (Maven and Gradle, JitPack and local coordinates)
        String docs = "README.md|docs/*.md";
        RULES.add(new Rule(docs, "<artifactId>(?:MoonLib|moonlib)</artifactId>\\s*<version>" + V + "</version>", false));
        RULES.add(new Rule(docs, "(?:com\\.github\\.LunaMCDev:MoonLib|com\\.lunamcdev:moonlib):" + V, false));
        RULES.add(new Rule(docs, "<moonlib\\.version>" + V + "</moonlib\\.version>", false));
        // the supported-versions table and the bug report placeholder
        RULES.add(new Rule("SECURITY.md", "\\| (\\d+\\.\\d+)\\.x \\| ✅", true));
        RULES.add(new Rule(".github/ISSUE_TEMPLATE/bug_report.yml", "placeholder: \"" + V + "\"", false));
    }

    public static void main(String[] args) throws IOException {
        Path root = findRoot();
        boolean check = args.length == 1 && args[0].equals("--check");
        if (args.length != 1 || (!check && !VERSION.matcher(args[0]).matches())) {
            System.err.println("Usage: java tools/SetVersion.java <version>   e.g. 1.3.0 or 1.3.0-beta");
            System.err.println("       java tools/SetVersion.java --check");
            System.exit(2);
        }
        String target = check ? currentVersion(root) : args[0];
        int changed = 0;
        List<String> problems = new ArrayList<>();
        for (Rule rule : RULES) {
            for (Path file : files(root, rule.files)) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                String wanted = rule.minorOnly ? target.replaceAll("^(\\d+\\.\\d+).*", "$1") : target;
                Matcher m = rule.pattern.matcher(text);
                StringBuffer out = new StringBuffer();
                int found = 0;
                while (m.find()) {
                    found++;
                    if (!m.group(1).equals(wanted)) {
                        problems.add(root.relativize(file) + ": " + m.group(1) + " (expected " + wanted + ")");
                        String replaced = m.group().substring(0, m.start(1) - m.start()) + wanted
                                + m.group().substring(m.end(1) - m.start());
                        m.appendReplacement(out, Matcher.quoteReplacement(replaced));
                    } else {
                        m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                    }
                }
                m.appendTail(out);
                if (found == 0 && !rule.files.contains("*") && !rule.files.contains("|")) {
                    problems.add(root.relativize(file) + ": no version found where one was expected");
                }
                if (!check && !out.toString().equals(text)) {
                    Files.write(file, out.toString().getBytes(StandardCharsets.UTF_8));
                    changed++;
                    System.out.println("  updated " + root.relativize(file));
                }
            }
        }
        if (check) {
            if (problems.isEmpty()) {
                System.out.println("Version " + target + " is consistent everywhere.");
                return;
            }
            System.err.println("These places do not match pom.xml (" + target + "). Run: java tools/SetVersion.java " + target);
            problems.forEach(p -> System.err.println("  " + p));
            System.exit(1);
        }
        System.out.println("MoonLib is now version " + target + " (" + changed + " file(s) changed).");
        System.out.println("Next: add a \"## [" + target + "]\" section to CHANGELOG.md, commit, and push the tag " + target
                + " for JitPack. Then bump <moonlib.version> in your plugins.");
    }

    static String currentVersion(Path root) throws IOException {
        String pom = new String(Files.readAllBytes(root.resolve("pom.xml")), StandardCharsets.UTF_8);
        Matcher m = RULES.get(0).pattern.matcher(pom);
        if (!m.find()) {
            throw new IllegalStateException("Could not find <version> after <artifactId>moonlib</artifactId> in pom.xml");
        }
        return m.group(1);
    }

    private static List<Path> files(Path root, String spec) throws IOException {
        List<Path> out = new ArrayList<>();
        for (String part : spec.split("\\|")) {
            if (part.endsWith("/*.md")) {
                Path dir = root.resolve(part.substring(0, part.length() - "/*.md".length()));
                if (Files.isDirectory(dir)) {
                    try (Stream<Path> s = Files.list(dir)) {
                        out.addAll(s.filter(p -> p.toString().endsWith(".md")).sorted().collect(Collectors.toList()));
                    }
                }
            } else if (Files.isRegularFile(root.resolve(part))) {
                out.add(root.resolve(part));
            }
        }
        return out;
    }

    private static Path findRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("tools/SetVersion.java")) && Files.isRegularFile(dir.resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Run this from inside the MoonLib repository");
    }
}
