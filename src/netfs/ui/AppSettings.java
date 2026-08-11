package netfs.ui;

import java.util.prefs.Preferences;

/**
 * Persists UI field values across app restarts using the JDK {@link Preferences} API
 * (stored per-user, e.g. under {@code ~/.java/.userPrefs} on Linux).
 */
final class AppSettings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(AppSettings.class);

    // Host tab keys
    static final String HOST_SHARED_FOLDER = "host.sharedFolder";
    static final String HOST_PORT = "host.port";
    static final String HOST_MAX_THREADS = "host.maxThreads";
    static final String HOST_MAX_SIZE = "host.maxSize";

    // Mount tab keys
    static final String MOUNT_POINT = "mount.mountPoint";
    static final String MOUNT_HOST = "mount.host";
    static final String MOUNT_PORT = "mount.port";
    static final String MOUNT_OPTIONS = "mount.options";
    static final String MOUNT_CACHE_SIZE = "mount.cacheSize";
    static final String MOUNT_MAX_FILE_CACHE = "mount.maxFileCache";
    static final String MOUNT_MAX_SERVER_CONNECTOR = "mount.maxServerConnector";

    // Stats tab keys
    static final String STATS_PERSIST = "stats.persist";

    // App behavior keys
    static final String APP_CLOSE_TO_TRAY = "app.closeToTray";
    static final String APP_START_ON_LOGIN = "app.startOnLogin";
    static final String APP_START_MINIMIZED = "app.startMinimized";

    private AppSettings() {
    }

    static String getString(String key, String defaultValue) {
        return PREFS.get(key, defaultValue);
    }

    static void setString(String key, String value) {
        PREFS.put(key, value == null ? "" : value);
    }

    static boolean getBoolean(String key, boolean defaultValue) {
        return PREFS.getBoolean(key, defaultValue);
    }

    static void setBoolean(String key, boolean value) {
        PREFS.putBoolean(key, value);
    }
}
