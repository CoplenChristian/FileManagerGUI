package CoplenChristian.FileManagerGUI.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads configuration from application.properties (working dir or classpath). */
public final class AppConfig {

    private static final String FILE_NAME = "application.properties";
    private static final Properties PROPS = load();

    private AppConfig() {}

    public static long cacheTtlMillis() {
        return getLong("cache.ttlMillis", 30_000L); // default 30s
    }

    public static int cacheMaxEntries() {
        return getInt("cache.maxEntries", 2_000);   // default 2000
    }

    // ---------------- internals ----------------
    private static Properties load() {
        Properties p = new Properties();

        // 1) working dir
        Path workFile = Path.of(System.getProperty("user.dir"), FILE_NAME);
        if (Files.isRegularFile(workFile)) {
            try (InputStream in = Files.newInputStream(workFile)) {
                p.load(in);
                return p;
            } catch (IOException ignored) {}
        }

        // 2) classpath (src/main/resources)
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
            if (in != null) {
                p.load(in);
                return p;
            }
        } catch (IOException ignored) {}

        return p; // empty, fall back to defaults
    }

    private static long getLong(String key, long def) {
        String v = PROPS.getProperty(key);
        if (v == null) return def;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static int getInt(String key, int def) {
        String v = PROPS.getProperty(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }
}
