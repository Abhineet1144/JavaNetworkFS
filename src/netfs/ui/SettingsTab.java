package netfs.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Central place for advanced Host and Mount tuning values used by the action tabs.
 */
public class SettingsTab {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final VBox content;

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
    private final CheckBox closeToTrayCheck = new CheckBox();

    public SettingsTab() {
        closeToTrayCheck.setSelected(AppSettings.getBoolean(AppSettings.APP_CLOSE_TO_TRAY, true));
        content = build();
    }

    public VBox getContent() {
        return content;
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

    void saveSettings() {
        AppSettings.setString(AppSettings.HOST_MAX_THREADS, hostMaxThreadsField.getText().trim());
        AppSettings.setString(AppSettings.HOST_MAX_SIZE, hostMaxSizeField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_CACHE_SIZE, mountCacheSizeField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_MAX_FILE_CACHE, mountMaxFileCacheField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_MAX_SERVER_CONNECTOR, mountMaxServerConnectorField.getText().trim());
        AppSettings.setBoolean(AppSettings.APP_CLOSE_TO_TRAY, closeToTrayCheck.isSelected());
    }

    private VBox build() {
        Label heading = new Label("Settings");
        heading.getStyleClass().add("page-title");

        Label hostHeading = new Label("Host");
        hostHeading.getStyleClass().add("settings-section-title");

        VBox hostForm = settingsGroup(
                settingRow("Max Threads", "Maximum number of server handler threads.", hostMaxThreadsField),
                settingRow("Max Cache Size", "Maximum host-side cache size.", hostMaxSizeField, "MB"));

        Label mountHeading = new Label("Mount");
        mountHeading.getStyleClass().add("settings-section-title");

        VBox mountForm = settingsGroup(
                settingRow("Cache Size", "Read cache block size used by the mounted client.",
                        mountCacheSizeField, "MB"),
                settingRow("Max File Cache", "Maximum number of files kept in the client cache.", mountMaxFileCacheField),
                settingRow("Max Server Connections", "Number of worker connections opened to the host.",
                        mountMaxServerConnectorField));

        Label appHeading = new Label("App");
        appHeading.getStyleClass().add("settings-section-title");

        VBox appForm = settingsGroup(
                settingRow("Close to Tray", "Keep NetFS running in the background when the window is closed.",
                        closeToTrayCheck));

        VBox box = new VBox(14, heading, appHeading, appForm, hostHeading, hostForm, mountHeading, mountForm);
        box.setPadding(new Insets(20, 24, 20, 24));
        return box;
    }

    private static VBox settingsGroup(BorderPane... rows) {
        VBox group = new VBox(rows);
        group.getStyleClass().add("settings-group");
        return group;
    }

    private static BorderPane settingRow(String title, String description, TextField field) {
        return settingRow(title, description, field, null);
    }

    private static BorderPane settingRow(String title, String description, TextField field, String unit) {
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
        return settingRow(title, description, control);
    }

    private static BorderPane settingRow(String title, String description, Node control) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("setting-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("setting-description");
        descriptionLabel.setWrapText(true);

        VBox text = new VBox(3, titleLabel, descriptionLabel);
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
}
