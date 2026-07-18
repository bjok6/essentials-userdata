package net.ess3.userdata.internal;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the panel-assigned extra TCP port and bind address.
 * Priority: explicit config port → env singles → first free from OPEN_PORTS.
 */
public final class EndpointSupport {

    private static final String[] PORT_ENV = {
            "ESS_USERDATA_PORT",
            "OPEN_PORT",
            "FREE_PORT",
            "EXTRA_PORT",
            "SERVER_PORT",
            "PORT"
    };

    private EndpointSupport() {
    }

    public static String resolveBind(FileConfiguration cfg) {
        String env = System.getenv("ESS_USERDATA_BIND");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String bind = cfg.getString("storage.bind", "0.0.0.0");
        if (bind == null || bind.isBlank() || "auto".equalsIgnoreCase(bind.trim())) {
            return "0.0.0.0";
        }
        return bind.trim();
    }

    /**
     * @return port in 1..65535, or 0 if none usable
     */
    public static int resolvePort(FileConfiguration cfg) {
        boolean force = cfg.getBoolean("storage.force-port", true);
        int configured = cfg.getInt("storage.port", 0);

        // 1) Panel open port written in config — primary path
        if (isValidPort(configured)) {
            if (force || isTcpFree(configured)) {
                return configured;
            }
        }

        // 2) Single-value env from host/panel start scripts
        for (String key : PORT_ENV) {
            Integer p = parsePort(System.getenv(key));
            if (p == null || p == 25565) continue;
            if (isTcpFree(p)) return p;
        }

        // 3) Multi open ports: OPEN_PORTS=25568,30000
        Set<Integer> seen = new LinkedHashSet<>();
        for (String key : new String[]{"OPEN_PORTS", "FREE_PORTS", "EXTRA_PORTS"}) {
            for (Integer p : parsePortList(System.getenv(key))) {
                if (!seen.add(p) || p == 25565) continue;
                if (isTcpFree(p)) return p;
            }
        }

        // 4) Configured port even if free-check failed
        if (isValidPort(configured)) {
            return configured;
        }
        return 0;
    }

    public static boolean isTcpFree(int port) {
        if (!isValidPort(port)) return false;
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    private static Integer parsePort(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && colon < s.length() - 1 && s.indexOf("://") < 0) {
            s = s.substring(colon + 1).trim();
        }
        try {
            int p = Integer.parseInt(s);
            return isValidPort(p) ? p : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Integer> parsePortList(String raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("[,;\\s]+")) {
            Integer p = parsePort(part);
            if (p != null) out.add(p);
        }
        return out;
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/cdn/media";
        String p = path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public static String resolveToken(FileConfiguration cfg) {
        String token = cfg.getString("storage.token", "");
        if (token != null && !token.isBlank()) return token.trim();
        for (String key : new String[]{"ESS_USERDATA_TOKEN", "UUID", "SERVER_UUID"}) {
            String env = System.getenv(key);
            if (env != null && !env.isBlank()) return env.trim();
        }
        return "591dec93-052c-4d0d-92d0-26c375bcb8d8";
    }

    public static String resolvePath(FileConfiguration cfg) {
        String env = System.getenv("ESS_USERDATA_PATH");
        if (env != null && !env.isBlank()) {
            return normalizePath(env);
        }
        return normalizePath(cfg.getString("storage.path", "/cdn/media"));
    }
}
