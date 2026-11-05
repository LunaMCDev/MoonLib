import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets up the local Paper test server used by the "Dev Server" run configuration.
 *
 * <p>Run through Maven ({@code mvn validate -Psetup-server}) or directly with Java 11+:</p>
 * <pre>
 *   java tools/DevServer.java setup [--version=latest|1.21.4|26.1.2] [--dir=run] [--force=true]
 * </pre>
 *
 * <p>It downloads the newest stable Paper build from PaperMC's Fill v3 API, checks its SHA-256,
 * and writes a development-friendly {@code server.properties} and {@code eula.txt}. Running it
 * again keeps your world and settings and only replaces {@code server.jar} if a newer build
 * exists (or {@code --force=true}).</p>
 *
 * <p>This file is a development tool: it is not part of the plugin jar.</p>
 */
public final class DevServer {

    private static final String API = "https://fill.papermc.io/v3/projects/paper";
    private static final String USER_AGENT = "MoonTemplate-DevServer/1.0 (+https://github.com/LunaMCDev/MoonLib)";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) throws Exception {
        Map<String, String> options = new HashMap<>();
        String command = "setup";
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                options.put(arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
            } else {
                command = arg;
            }
        }
        if (!command.equals("setup")) {
            System.err.println("Unknown command '" + command + "'. Usage: java tools/DevServer.java setup [--version=latest]");
            System.exit(2);
        }
        Path dir = Paths.get(options.getOrDefault("dir", "run")).toAbsolutePath().normalize();
        String version = options.getOrDefault("version", "latest");
        boolean force = Boolean.parseBoolean(options.getOrDefault("force", "false"));
        String pluginName = options.getOrDefault("plugin", "your plugin");
        try {
            setup(dir, version, force, pluginName);
        } catch (IOException | IllegalStateException e) {
            System.err.println();
            System.err.println("[DevServer] Setup failed: " + e.getMessage());
            System.err.println("[DevServer] You can also download Paper by hand from https://papermc.io/downloads/paper");
            System.err.println("[DevServer] and save it as " + dir.resolve("server.jar"));
            System.exit(1);
        }
    }

    private static void setup(Path dir, String requested, boolean force, String pluginName) throws Exception {
        Files.createDirectories(dir.resolve("plugins"));
        log("Server folder: " + dir);

        Build build = findBuild(requested);
        log("Paper " + build.version + " build #" + build.id + " (" + build.channel + ")");

        Path jar = dir.resolve("server.jar");
        Path marker = dir.resolve(".paper-build");
        String installed = Files.exists(marker) ? Files.readString(marker).trim() : "";
        String wanted = build.version + "#" + build.id;
        if (!force && Files.exists(jar) && installed.equals(wanted)) {
            log("server.jar is already up to date");
        } else {
            download(build, jar);
            Files.writeString(marker, wanted);
        }

        Path eula = dir.resolve("eula.txt");
        if (!Files.exists(eula)) {
            Files.writeString(eula, "# Written by tools/DevServer.java for local plugin testing.\n"
                    + "# Running this server means you accept the Minecraft EULA: https://aka.ms/MinecraftEULA\n"
                    + "eula=true\n");
            log("Wrote eula.txt. Running the server means you accept the Minecraft EULA (https://aka.ms/MinecraftEULA)");
        }

        Path properties = dir.resolve("server.properties");
        if (!Files.exists(properties)) {
            Files.writeString(properties, String.join("\n",
                    "# Development defaults written by tools/DevServer.java. Edit freely; setup never overwrites this file.",
                    "motd=\\u00a7d" + pluginName + " \\u00a77dev server",
                    "server-port=25565",
                    "online-mode=true",
                    "spawn-protection=0",
                    "view-distance=6",
                    "simulation-distance=6",
                    "max-players=10",
                    "difficulty=peaceful",
                    "allow-flight=true",
                    "enable-command-block=true",
                    "level-name=world",
                    "sync-chunk-writes=false",
                    ""));
            log("Wrote server.properties with development defaults");
        }

        int javaNeeded = javaFor(build.version);
        int javaRunning = Runtime.version().feature();
        log("");
        log("Done. This Paper version needs Java " + javaNeeded + "+.");
        if (javaRunning < javaNeeded) {
            log("Note: this JDK is Java " + javaRunning + "; pick a Java " + javaNeeded + " JetBrains Runtime for the Dev Server run configuration.");
        }
        log("Next: in IntelliJ choose the \"Dev Server\" run configuration and press Debug.");
    }

    // ------------------------------------------------------------------ Fill API

    private static final class Build {
        String version;
        long id;
        String channel;
        String url;
        String name;
        String sha256;
    }

    @SuppressWarnings("unchecked")
    private static Build findBuild(String requested) throws Exception {
        List<String> candidates = new ArrayList<>();
        if (requested == null || requested.isEmpty() || requested.equalsIgnoreCase("latest")) {
            Map<String, Object> project = (Map<String, Object>) Json.parse(get(API));
            Map<String, Object> groups = (Map<String, Object>) project.get("versions");
            if (groups == null) {
                throw new IllegalStateException("Unexpected response from " + API);
            }
            for (Object group : groups.values()) {
                for (Object v : (List<Object>) group) {
                    candidates.add(String.valueOf(v));
                }
            }
            candidates.sort((a, b) -> compareVersions(b, a));
        } else {
            candidates.add(requested);
        }
        for (String version : candidates) {
            List<Object> builds;
            try {
                builds = (List<Object>) Json.parse(get(API + "/versions/" + version + "/builds"));
            } catch (IllegalStateException e) {
                if (candidates.size() == 1) {
                    throw new IllegalStateException("Paper has no version '" + version + "' (" + e.getMessage() + ")");
                }
                continue;
            }
            for (Object entry : builds) {
                Map<String, Object> b = (Map<String, Object>) entry;
                if (!"STABLE".equalsIgnoreCase(String.valueOf(b.get("channel")))) {
                    continue;
                }
                Map<String, Object> downloads = (Map<String, Object>) b.get("downloads");
                Map<String, Object> server = downloads == null ? null : (Map<String, Object>) downloads.get("server:default");
                if (server == null) {
                    continue;
                }
                Build build = new Build();
                build.version = version;
                build.id = ((Number) b.get("id")).longValue();
                build.channel = String.valueOf(b.get("channel"));
                build.url = String.valueOf(server.get("url"));
                build.name = String.valueOf(server.get("name"));
                Map<String, Object> checksums = (Map<String, Object>) server.get("checksums");
                build.sha256 = checksums == null ? null : (String) checksums.get("sha256");
                return build;
            }
            if (candidates.size() == 1) {
                throw new IllegalStateException("Paper " + version + " has no stable build yet; try another --version");
            }
        }
        throw new IllegalStateException("No Paper version with a stable build was found");
    }

    private static void download(Build build, Path target) throws Exception {
        log("Downloading " + build.name + " ...");
        HttpRequest request = HttpRequest.newBuilder(URI.create(build.url))
                .header("User-Agent", USER_AGENT).timeout(Duration.ofMinutes(5)).build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Download failed with HTTP " + response.statusCode());
        }
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = response.body()) {
            byte[] buffer = new byte[1 << 16];
            try (var out = Files.newOutputStream(temp)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    sha.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
        }
        String actual = hex(sha.digest());
        if (build.sha256 != null && !build.sha256.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("Checksum mismatch for " + build.name + " (expected " + build.sha256
                    + ", got " + actual + "); nothing was replaced");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        log("Saved " + target.getFileName() + " (sha256 verified)");
    }

    private static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT).header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    // ------------------------------------------------------------------ helpers

    /** Minecraft 26.1+ needs Java 25, 1.20.5+ needs Java 21, 1.18+ needs 17. */
    static int javaFor(String version) {
        if (compareVersions(version, "26") >= 0) {
            return 25;
        }
        if (compareVersions(version, "1.20.5") >= 0) {
            return 21;
        }
        if (compareVersions(version, "1.18") >= 0) {
            return 17;
        }
        return compareVersions(version, "1.17") >= 0 ? 16 : 8;
    }

    static int compareVersions(String a, String b) {
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? number(pa[i]) : 0;
            int y = i < pb.length ? number(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;   // pre-release tags such as "pre1" or "rc1" sort below releases
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void log(String message) {
        System.out.println("[DevServer] " + message);
    }

    /** A minimal JSON reader, enough for the Fill API. */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            Object value = json.value();
            json.skip();
            if (json.i != json.s.length()) {
                throw new IllegalStateException("Trailing data in JSON at " + json.i);
            }
            return value;
        }

        private Object value() {
            skip();
            if (i >= s.length()) {
                throw new IllegalStateException("Unexpected end of JSON");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{': {
                    i++;
                    Map<String, Object> map = new LinkedHashMap<>();
                    skip();
                    if (peek('}')) {
                        return map;
                    }
                    do {
                        skip();
                        String key = string();
                        skip();
                        expect(':');
                        map.put(key, value());
                        skip();
                    } while (peek(','));
                    expect('}');
                    return map;
                }
                case '[': {
                    i++;
                    List<Object> list = new ArrayList<>();
                    skip();
                    if (peek(']')) {
                        return list;
                    }
                    do {
                        list.add(value());
                        skip();
                    } while (peek(','));
                    expect(']');
                    return list;
                }
                case '"':
                    return string();
                case 't':
                    i += 4;
                    return Boolean.TRUE;
                case 'f':
                    i += 5;
                    return Boolean.FALSE;
                case 'n':
                    i += 4;
                    return null;
                default: {
                    int start = i;
                    while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                        i++;
                    }
                    String number = s.substring(start, i);
                    if (number.isEmpty()) {
                        throw new IllegalStateException("Unexpected '" + c + "' in JSON at " + start);
                    }
                    return number.contains(".") || number.contains("e") || number.contains("E")
                            ? (Object) Double.parseDouble(number) : (Object) Long.parseLong(number);
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalStateException("Unterminated string in JSON");
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private boolean peek(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalStateException("Expected '" + c + "' in JSON at " + i);
            }
        }
    }
}
