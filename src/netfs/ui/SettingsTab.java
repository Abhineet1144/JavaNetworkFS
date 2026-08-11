package netfs.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Central place for advanced Host and Mount tuning values used by the action tabs.
 */
public class SettingsTab {

    private static final long BYTES_PER_MB = 1024L * 1024L;
    private VBox content;
    private ScrollPane scrollPane;

    private final TextField hostMaxThreadsField = new TextField(
            AppSettings.getString(AppSettings.HOST_MAX_THREADS, "111"));
    private final TextField hostMaxSizeField = new TextField(
            settingMegabytes(AppSettings.HOST_MAX_SIZE, "1"));
    private final TextField mountCacheSizeField = new TextField(
            settingMegabytes(AppSettings.MOUNT_CACHE_SIZE, "1"));
    private final TextField mountMaxFileCacheField = new TextField(
            AppSettings.getString(AppSettings.MOUNT_MAX_FILE_CACHE, "30"));
    private final TextField mountMaxServerConnectorField = new TextField(
            AppSettings.getString(AppSettings.MOUNT_MAX_SERVER_CONNECTOR, "3"));
    private final CheckBox hostStartOnLaunchCheck = new CheckBox();
    private final CheckBox closeToTrayCheck = new CheckBox();
    private final CheckBox startOnLoginCheck = new CheckBox();
    private final CheckBox startMinimizedCheck = new CheckBox();
    private final Label hostMaxThreadsNote = restartNote("Restart host to apply changes");
    private final Label hostMaxSizeNote = restartNote("Restart host to apply changes");
    private final Label mountCacheSizeNote = restartNote("Remount to apply changes");
    private final Label mountMaxFileCacheNote = restartNote("Remount to apply changes");
    private final Label mountMaxServerConnectorNote = restartNote("Remount to apply changes");

    private String savedHostMaxThreads;
    private String savedHostMaxSize;
    private String savedMountCacheSize;
    private String savedMountMaxFileCache;
    private String savedMountMaxServerConnector;
    private boolean hostActive;
    private boolean mountActive;

    public SettingsTab() {
        hostStartOnLaunchCheck.setSelected(AppSettings.getBoolean(AppSettings.HOST_START_ON_LAUNCH, false));
        closeToTrayCheck.setSelected(AppSettings.getBoolean(AppSettings.APP_CLOSE_TO_TRAY, true));
        startOnLoginCheck.setSelected(AppSettings.getBoolean(AppSettings.APP_START_ON_LOGIN, false));
        startMinimizedCheck.setSelected(AppSettings.getBoolean(AppSettings.APP_START_MINIMIZED, false));
        installNumericInputFilters();
        syncSavedSettingsSnapshot();
        installRestartNoticeListeners();
        refreshRestartNotices();
    }

    public ScrollPane getContent() {
        if (scrollPane == null) {
            content = build();
            scrollPane = new ScrollPane(content);
            scrollPane.getStyleClass().add("settings-scroll");
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        }
        return scrollPane;
    }

    int getHostMaxThreads() {
        return parsePositiveInt(hostMaxThreadsField, "Host Max Threads");
    }

    long getHostMaxSize() {
        return parseMegabytesAsBytes(hostMaxSizeField, "Host Max Cache Size");
    }

    int getMountCacheSize() {
        long bytes = parseMegabytesAsBytes(mountCacheSizeField, "Mount Cache Size");
        if (bytes > Integer.MAX_VALUE) {
            throw new NumberFormatException("Mount Cache Size is too large");
        }
        return (int) bytes;
    }

    int getMountMaxFileCache() {
        return parsePositiveInt(mountMaxFileCacheField, "Mount Max File Cache");
    }

    int getMountMaxServerConnector() {
        return parsePositiveInt(mountMaxServerConnectorField, "Mount Max Server Connections");
    }

    boolean isCloseToTrayEnabled() {
        return closeToTrayCheck.isSelected();
    }

    boolean isStartMinimizedEnabled() {
        return startMinimizedCheck.isSelected();
    }

    boolean isHostStartOnLaunchEnabled() {
        return hostStartOnLaunchCheck.isSelected();
    }

    void setHostActive(boolean active) {
        hostActive = active;
        refreshRestartNotices();
    }

    void setMountActive(boolean active) {
        mountActive = active;
        refreshRestartNotices();
    }

    void saveSettings() {
        AppSettings.setString(AppSettings.HOST_MAX_THREADS, hostMaxThreadsField.getText().trim());
        AppSettings.setString(AppSettings.HOST_MAX_SIZE, hostMaxSizeField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_CACHE_SIZE, mountCacheSizeField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_MAX_FILE_CACHE, mountMaxFileCacheField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_MAX_SERVER_CONNECTOR, mountMaxServerConnectorField.getText().trim());
        AppSettings.setBoolean(AppSettings.HOST_START_ON_LAUNCH, hostStartOnLaunchCheck.isSelected());
        AppSettings.setBoolean(AppSettings.APP_CLOSE_TO_TRAY, closeToTrayCheck.isSelected());
        AppSettings.setBoolean(AppSettings.APP_START_ON_LOGIN, startOnLoginCheck.isSelected());
        AppSettings.setBoolean(AppSettings.APP_START_MINIMIZED, startMinimizedCheck.isSelected());
        updateAutostart(startOnLoginCheck.isSelected());
        syncSavedSettingsSnapshot();
        refreshRestartNotices();
    }

    private VBox build() {
        Label heading = new Label("Settings");
        heading.getStyleClass().add("page-title");

        Label hostHeading = new Label("Host");
        hostHeading.getStyleClass().add("settings-section-title");

        VBox hostForm = settingsGroup(
                settingRow("Start Server on Launch", "Start hosting automatically when NetFS opens.",
                        hostStartOnLaunchCheck),
                settingRow("Max Threads", "Maximum number of server handler threads.", hostMaxThreadsField,
                        hostMaxThreadsNote),
                settingRow("Max Cache Size", "Maximum host-side cache size.", hostMaxSizeField, "MB",
                        hostMaxSizeNote));

        Label mountHeading = new Label("Mount");
        mountHeading.getStyleClass().add("settings-section-title");

        VBox mountForm = settingsGroup(
                settingRow("Cache Size", "Read cache block size used by the mounted client.",
                        mountCacheSizeField, "MB", mountCacheSizeNote),
                settingRow("Max File Cache", "Maximum number of files kept in the client cache.",
                        mountMaxFileCacheField, mountMaxFileCacheNote),
                settingRow("Max Server Connections", "Number of worker connections opened to the host.",
                        mountMaxServerConnectorField, mountMaxServerConnectorNote));

        Label appHeading = new Label("App");
        appHeading.getStyleClass().add("settings-section-title");

        VBox appForm = settingsGroup(
                settingRow("Close to Tray", "Keep NetFS running in the background when the window is closed.",
                        closeToTrayCheck),
                settingRow("Start on Login", "Launch NetFS automatically after signing in.", startOnLoginCheck),
                settingRow("Start Minimized", "Open NetFS in the tray when launched on login.", startMinimizedCheck));

        Label aboutHeading = new Label("About");
        aboutHeading.getStyleClass().add("settings-section-title");

        VBox aboutForm = settingsGroup(
                infoRow("Version", "0.0.1"),
                infoRow("Authors", "Tejas, Abhineet"));

        VBox box = new VBox(14, heading, appHeading, appForm, hostHeading, hostForm, mountHeading, mountForm,
                aboutHeading, aboutForm);
        box.setPadding(new Insets(20, 24, 20, 24));
        refreshRestartNotices();
        return box;
    }

    private static VBox settingsGroup(BorderPane... rows) {
        VBox group = new VBox(rows);
        group.getStyleClass().add("settings-group");
        return group;
    }

    private static BorderPane settingRow(String title, String description, TextField field) {
        return settingRow(title, description, field, null, null);
    }

    private static BorderPane settingRow(String title, String description, TextField field, Label note) {
        return settingRow(title, description, field, null, note);
    }

    private static BorderPane settingRow(String title, String description, TextField field, String unit) {
        return settingRow(title, description, field, unit, null);
    }

    private static BorderPane settingRow(String title, String description, TextField field, String unit, Label note) {
        field.getStyleClass().add("settings-input");
        field.setPrefWidth(92);
        field.setMaxWidth(92);

        HBox control = new HBox(8, field);
        control.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        if (unit != null) {
            Label unitLabel = new Label(unit);
            unitLabel.getStyleClass().add("setting-unit");
            control.getChildren().add(unitLabel);
        }
        return settingRow(title, description, control, note);
    }

    private static BorderPane settingRow(String title, String description, Node control) {
        return settingRow(title, description, control, null);
    }

    private static BorderPane settingRow(String title, String description, Node control, Label note) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("setting-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("setting-description");
        descriptionLabel.setWrapText(true);

        HBox titleRow = new HBox(6, titleLabel);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (note != null) {
            titleRow.getChildren().add(note);
        }

        VBox text = new VBox(3, titleRow, descriptionLabel);
        BorderPane row = new BorderPane();
        row.getStyleClass().add("setting-row");
        row.setLeft(text);

        row.setRight(control);

        BorderPane.setMargin(control, new Insets(0, 0, 0, 24));
        BorderPane.setMargin(text, new Insets(0, 16, 0, 0));
        BorderPane.setAlignment(control, javafx.geometry.Pos.CENTER_RIGHT);
        VBox.setVgrow(row, Priority.NEVER);
        return row;
    }

    private static Label restartNote(String text) {
        Label label = new Label("i");
        label.getStyleClass().add("setting-restart-note");
        Tooltip tooltip = new Tooltip(text);
        tooltip.getStyleClass().add("setting-restart-tooltip");
        tooltip.setShowDelay(Duration.ZERO);
        tooltip.setShowDuration(Duration.INDEFINITE);
        tooltip.setHideDelay(Duration.millis(80));
        Tooltip.install(label, tooltip);
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    private void installRestartNoticeListeners() {
        hostMaxThreadsField.textProperty().addListener((obs, oldValue, newValue) -> refreshRestartNotices());
        hostMaxSizeField.textProperty().addListener((obs, oldValue, newValue) -> refreshRestartNotices());
        mountCacheSizeField.textProperty().addListener((obs, oldValue, newValue) -> refreshRestartNotices());
        mountMaxFileCacheField.textProperty().addListener((obs, oldValue, newValue) -> refreshRestartNotices());
        mountMaxServerConnectorField.textProperty().addListener((obs, oldValue, newValue) -> refreshRestartNotices());
    }

    private void installNumericInputFilters() {
        installNumericInputFilter(hostMaxThreadsField, "111");
        installNumericInputFilter(hostMaxSizeField, "1");
        installNumericInputFilter(mountCacheSizeField, "1");
        installNumericInputFilter(mountMaxFileCacheField, "30");
        installNumericInputFilter(mountMaxServerConnectorField, "3");
    }

    private static void installNumericInputFilter(TextField field, String fallback) {
        String initial = field.getText() == null ? "" : field.getText().trim();
        field.setText(initial.matches("\\d+") ? initial : fallback);
        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().matches("\\d*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(filter));
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                field.setText(newValue.replaceAll("\\D", ""));
            }
        });
    }

    private void syncSavedSettingsSnapshot() {
        savedHostMaxThreads = normalized(hostMaxThreadsField);
        savedHostMaxSize = normalized(hostMaxSizeField);
        savedMountCacheSize = normalized(mountCacheSizeField);
        savedMountMaxFileCache = normalized(mountMaxFileCacheField);
        savedMountMaxServerConnector = normalized(mountMaxServerConnectorField);
    }

    private void refreshRestartNotices() {
        setVisibleManaged(hostMaxThreadsNote,
                hostActive && !normalized(hostMaxThreadsField).equals(savedHostMaxThreads));
        setVisibleManaged(hostMaxSizeNote,
                hostActive && !normalized(hostMaxSizeField).equals(savedHostMaxSize));
        setVisibleManaged(mountCacheSizeNote,
                mountActive && !normalized(mountCacheSizeField).equals(savedMountCacheSize));
        setVisibleManaged(mountMaxFileCacheNote,
                mountActive && !normalized(mountMaxFileCacheField).equals(savedMountMaxFileCache));
        setVisibleManaged(mountMaxServerConnectorNote,
                mountActive && !normalized(mountMaxServerConnectorField).equals(savedMountMaxServerConnector));
    }

    private static void setVisibleManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static String normalized(TextField field) {
        return field.getText().trim();
    }

    private static BorderPane infoRow(String title, String value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("setting-title");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("setting-value");

        BorderPane row = new BorderPane();
        row.getStyleClass().add("setting-row");
        row.setLeft(titleLabel);
        row.setRight(valueLabel);

        BorderPane.setMargin(valueLabel, new Insets(0, 0, 0, 24));
        BorderPane.setAlignment(valueLabel, javafx.geometry.Pos.CENTER_RIGHT);
        VBox.setVgrow(row, Priority.NEVER);
        return row;
    }

    private static int parsePositiveInt(TextField field, String label) {
        int value = Integer.parseInt(field.getText().trim());
        if (value <= 0) {
            throw new NumberFormatException(label + " must be greater than 0");
        }
        return value;
    }

    private static long parsePositiveLong(TextField field, String label) {
        long value = Long.parseLong(field.getText().trim());
        if (value <= 0) {
            throw new NumberFormatException(label + " must be greater than 0");
        }
        return value;
    }

    private static long parseMegabytesAsBytes(TextField field, String label) {
        return Math.multiplyExact(parsePositiveLong(field, label), BYTES_PER_MB);
    }

    private static String settingMegabytes(String key, String defaultMegabytes) {
        String value = AppSettings.getString(key, defaultMegabytes).trim();
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 1024) {
                return String.valueOf(Math.max(1, Math.round(parsed / (double) BYTES_PER_MB)));
            }
        } catch (NumberFormatException ignored) {
            return defaultMegabytes;
        }
        return value;
    }

    private static void updateAutostart(boolean enabled) {
        Path autostartFile = autostartFile();
        try {
            if (enabled) {
                Files.createDirectories(autostartFile.getParent());
                Files.writeString(autostartFile, desktopEntry(), StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(autostartFile);
            }
        } catch (IOException ex) {
            System.err.println("[UI] Failed to update autostart setting: " + ex.getMessage());
        }
    }

    private static Path autostartFile() {
        return Path.of(System.getProperty("user.home"), ".config", "autostart", "netfs.desktop");
    }

    private static String desktopEntry() {
        return String.join("\n",
                "[Desktop Entry]",
                "Type=Application",
                "Name=NetFS",
                "Comment=Java Network FS",
                "Exec=netfs-ui",
                "Icon=netfs",
                "Terminal=false",
                "StartupWMClass=netfs.ui.NetFSApp",
                "X-GNOME-Autostart-enabled=true",
                "");
    }
}
