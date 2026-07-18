package net.ess3.userdata.internal;

import java.io.InputStream;
import java.util.Properties;

/** Build-time coordinates written by Maven/CI (public release metadata). */
public final class BuildInfo {

    private static final Properties PROPS = load();

    private BuildInfo() {
    }

    public static String version() {
        return PROPS.getProperty("version", "0.0.0");
    }

    public static String repository() {
        return PROPS.getProperty("repository", "");
    }

    public static String tag() {
        String t = PROPS.getProperty("tag", "");
        if (t == null || t.isBlank()) {
            return "v" + version();
        }
        return t;
    }

    public static String releaseAssetUrl(String fileName) {
        String repo = repository();
        if (repo == null || repo.isBlank() || repo.startsWith("owner/")) {
            return "";
        }
        return "https://github.com/" + repo + "/releases/download/" + tag() + "/" + fileName;
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getClassLoader().getResourceAsStream("build-info.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        return p;
    }
}
