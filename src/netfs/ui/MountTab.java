package netfs.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.UnaryOperator;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.DirectoryChooser;

import netfs.state.OperationLog;
import netfs.state.OperationSource;
import netfs.state.OperationStatus;
import netfs.state.TransferStats;

/**
 * "Mount" tab: configures and starts a FUSE mount that connects to a remote netfs host.
 * The actual mount runs in a separate headless child JVM process (see
 * {@link netfs.net.MountWorker}) because jnr-fuse's native FUSE callbacks reliably
 * crash the JVM with a native SIGSEGV when JavaFX is loaded in the same process
 * (see jnr-fuse GitHub issue #162, "JVM crash with jnr-fuse and javafx").
 */
public class MountTab {

    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final String OPLOG_PREFIX = "##OPLOG##";
    private static final String XFER_PREFIX = "##XFER##";

    private final LogConsole logConsole;
    private final SettingsTab settingsTab;
    private final VBox content;

    private final TextField mountPointField = new TextField(
            AppSettings.getString(AppSettings.MOUNT_POINT, System.getProperty("user.home") + "/netfs-mount"));
    private final TextField hostField = new TextField(AppSettings.getString(AppSettings.MOUNT_HOST, "127.0.0.1"));
    private final TextField portField = new TextField(AppSettings.getString(AppSettings.MOUNT_PORT, "10002"));
    private final CheckBox mountOptionsCheck = new CheckBox("Enable mount options");

    private final Button startButton = new Button("Mount");
    private final Button stopButton = new Button("Unmount");
    private final Button testButton = new Button("Test");
    private final Label statusLabel = new Label("Not mounted");
    private final Label errorLabel = new Label();

    private Process mountProcess;
    private String activeMountPoint;
    private volatile boolean unmountRequested;

    public MountTab(LogConsole logConsole, SettingsTab settingsTab) {
        this.logConsole = logConsole;
        this.settingsTab = settingsTab;
        installNumericInputFilter(portField, "10002");
        mountOptionsCheck.setSelected(AppSettings.getBoolean(AppSettings.MOUNT_OPTIONS, true));
        this.content = build();
        stopButton.setDisable(true);
        setIdleStatus();
        hideError();
    }

    public VBox getContent() {
        return content;
    }

    private VBox build() {
        Label heading = new Label("Mount Options");
        heading.getStyleClass().add("page-title");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(4, 0, 16, 0));

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> chooseDirectory(mountPointField));
        browseButton.getStyleClass().add("form-side-button");
        testButton.getStyleClass().add("form-side-button");

        int row = 0;
        form.addRow(row++, formLabel("Mount Point"), mountPointField, browseButton);
        form.addRow(row++, formLabel("Host"), hostField, testButton);
        form.addRow(row++, formLabel("Port"), portField);
        form.addRow(row++, new Label(""), mountOptionsCheck);

        GridPane.setHgrow(mountPointField, Priority.ALWAYS);
        GridPane.setHgrow(hostField, Priority.ALWAYS);
        GridPane.setHgrow(portField, Priority.ALWAYS);
        GridPane.setValignment(browseButton, VPos.CENTER);
        GridPane.setValignment(testButton, VPos.CENTER);

        startButton.getStyleClass().add("primary-button");
        stopButton.getStyleClass().add("danger-button");
        startButton.setGraphic(icon("M12,3l5,5h-3v5h-4V8H7z M5,15h14v4H5z"));
        stopButton.setGraphic(icon("M7,4h10v2H7z M12,21l-5,-5h3v-5h4v5h3z"));
        testButton.setAccessibleText("Test host connection");
        startButton.setOnAction(e -> mount());
        stopButton.setOnAction(e -> unmount());
        testButton.setOnAction(e -> testConnection());

        statusLabel.getStyleClass().addAll("status-pill", "status-idle");

        HBox buttons = new HBox(10, startButton, stopButton);
        buttons.setAlignment(Pos.CENTER);

        VBox actions = new VBox(10, statusLabel, buttons);
        actions.setAlignment(Pos.CENTER);

        errorLabel.getStyleClass().add("error-banner");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);

        VBox panel = new VBox(14, form, actions, errorLabel);
        panel.getStyleClass().add("action-panel");

        VBox box = new VBox(14, heading, panel);
        box.setPadding(new Insets(20, 24, 20, 24));
        return box;
    }

    private void chooseDirectory(TextField target) {
        DirectoryChooser chooser = new DirectoryChooser();
        File initial = new File(target.getText().trim());
        if (initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        File selected = chooser.showDialog(content.getScene() != null ? content.getScene().getWindow() : null);
        if (selected != null) {
            target.setText(selected.getAbsolutePath());
        }
    }

    private void mount() {
        hideError();

        int port;
        int cacheSize;
        int maxFileCache;
        int maxServerConnector;
        try {
            port = Integer.parseInt(portField.getText().trim());
            cacheSize = settingsTab.getMountCacheSize();
            maxFileCache = settingsTab.getMountMaxFileCache();
            maxServerConnector = settingsTab.getMountMaxServerConnector();
        } catch (NumberFormatException ex) {
            showError("Invalid numeric input: " + ex.getMessage());
            return;
        }

        if (cacheSize <= 0) {
            showError("Cache Size must be greater than 0 (reads request 0 bytes otherwise).");
            return;
        }

        String mountPoint = mountPointField.getText().trim();
        String host = hostField.getText().trim();

        File mountDir = new File(mountPoint);
        if (!mountDir.isDirectory()) {
            showError("Mount point does not exist: " + mountDir);
            return;
        }
        if (host.isEmpty()) {
            showError("Host is required.");
            return;
        }

        saveSettings();
        settingsTab.saveSettings();
        startButton.setDisable(true);
        testButton.setDisable(true);
        setPendingStatus("Connecting to " + host + ":" + port + "...");
        activeMountPoint = mountPoint;
        unmountRequested = false;

        Thread launcherThread = new Thread(() -> {
            try {
                // Fail fast with a friendly message instead of mounting FUSE against a
                // server that isn't reachable (e.g. "Connection refused").
                try {
                    probeHost(host, port);
                } catch (IOException ex) {
                    showError("Cannot reach " + host + ":" + port + " - " + describe(ex));
                    return;
                }

                cleanupMountPoint(mountPoint);

                // The mount itself runs in a separate headless JVM process (MountWorker)
                // rather than in-process, since jnr-fuse's native FUSE callbacks crash the
                // JVM when JavaFX is loaded in the same process.
                String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                List<String> command = List.of(javaBin,
                        "-Djnr.ffi.asm.enabled=false",
                        "-cp", System.getProperty("java.class.path"),
                        "netfs.net.MountWorker",
                        mountPoint, host, String.valueOf(port),
                        String.valueOf(mountOptionsCheck.isSelected()),
                        String.valueOf(cacheSize), String.valueOf(maxFileCache), String.valueOf(maxServerConnector));

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.environment().put("GLIBC_TUNABLES", "glibc.cpu.hwcaps=-SHSTK,-IBT");
                processBuilder.redirectErrorStream(true);
                mountProcess = processBuilder.start();

                Platform.runLater(() -> {
                    settingsTab.setMountActive(true);
                    setRunningStatus("Mounted at " + mountPoint);
                    stopButton.setDisable(false);
                });

                streamProcessOutput(mountProcess);
                int exitCode = mountProcess.waitFor();
                if (exitCode != 0 && !unmountRequested) {
                    showError("Mount process exited unexpectedly (code " + exitCode + "). Check the console below.");
                }
            } catch (Throwable ex) {
                showError("Mount failed: " + describe(ex));
            } finally {
                mountProcess = null;
                Platform.runLater(() -> {
                    settingsTab.setMountActive(false);
                    startButton.setDisable(false);
                    testButton.setDisable(false);
                    stopButton.setDisable(true);
                    if (!errorLabel.isVisible()) {
                        setIdleStatus();
                    }
                });
            }
        }, "netfs-mount-launcher");
        launcherThread.setDaemon(true);
        launcherThread.start();
    }

    private void testConnection() {
        hideError();

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Invalid port: " + ex.getMessage());
            return;
        }

        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            showError("Host is required.");
            return;
        }

        saveSettings();
        startButton.setDisable(true);
        testButton.setDisable(true);
        setPendingStatus("Testing " + host + ":" + port + "...");

        Thread testThread = new Thread(() -> {
            try {
                probeHost(host, port);
                logConsole.log("[UI] Reachable: " + host + ":" + port);
                Platform.runLater(() -> setRunningStatus("Reachable " + host + ":" + port));
            } catch (IOException ex) {
                showError("Cannot reach " + host + ":" + port + " - " + describe(ex));
            } finally {
                Platform.runLater(() -> {
                    startButton.setDisable(false);
                    testButton.setDisable(false);
                });
            }
        }, "netfs-mount-test");
        testThread.setDaemon(true);
        testThread.start();
    }

    private static void probeHost(String host, int port) throws IOException {
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        }
    }

    /**
     * Reads the child mount process's combined stdout/stderr. Regular lines go to the
     * shared console; lines prefixed with {@code ##OPLOG##} are structured operation
     * reports (see {@link netfs.client.ServerConnection}) that get mirrored into this
     * process's own {@link OperationLog} so they show up in the Stats tab, since
     * OperationLog is per-process and the mount runs in a separate process.
     */
    private void streamProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(OPLOG_PREFIX)) {
                    applyRemoteOperationLog(line);
                } else if (line.startsWith(XFER_PREFIX)) {
                    applyRemoteTransferStats(line);
                } else {
                    logConsole.log(line);
                }
            }
        }
    }

    private void applyRemoteOperationLog(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 7) {
            return;
        }
        try {
            OperationSource source = OperationSource.valueOf(parts[1]);
            String operation = parts[2];
            String detail = parts[3];
            OperationStatus status = OperationStatus.valueOf(parts[4]);
            long durationMillis = Long.parseLong(parts[5]);
            String errorMessage = parts[6].isEmpty() ? null : parts[6];
            OperationLog.recordRemote(source, operation, detail, status, durationMillis, errorMessage);
        } catch (IllegalArgumentException ignored) {
            // Malformed line - ignore rather than crash the reader loop.
        }
    }

    /**
     * Mirrors the Mount worker process's cumulative read/write byte totals (see
     * {@code ServerConnection.trackAndEmitTransfer}) into this process's own
     * {@link TransferStats}, since static state isn't shared across processes.
     */
    private void applyRemoteTransferStats(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 3) {
            return;
        }
        try {
            long readBytes = Long.parseLong(parts[1]);
            long writeBytes = Long.parseLong(parts[2]);
            TransferStats.setReadBytes(OperationSource.MOUNT, readBytes);
            TransferStats.setWriteBytes(OperationSource.MOUNT, writeBytes);
        } catch (NumberFormatException ignored) {
            // Malformed line - ignore rather than crash the reader loop.
        }
    }

    private void unmount() {
        String mountPoint = activeMountPoint;
        unmountRequested = true;
        try {
            if (mountPoint != null) {
                cleanupMountPoint(mountPoint);
            }
            if (mountProcess != null) {
                mountProcess.destroy();
            }
            logConsole.log("[UI] Unmount requested.");
        } catch (Throwable ex) {
            showError("Unmount failed: " + describe(ex));
        } finally {
            settingsTab.setMountActive(false);
            stopButton.setDisable(true);
            startButton.setDisable(false);
            testButton.setDisable(false);
            setIdleStatus();
        }
    }

    /** Tries regular then lazy unmount to release stale FUSE mount state. */
    private void cleanupMountPoint(String mountPoint) {
        if (runUnmountCommand(mountPoint, "-u")) {
            return;
        }
        runUnmountCommand(mountPoint, "-uz");
    }

    private boolean runUnmountCommand(String mountPoint, String option) {
        for (String fusermount : List.of("fusermount3", "fusermount")) {
            try {
                Process process = new ProcessBuilder(fusermount, option, mountPoint)
                        .redirectErrorStream(true)
                        .start();
                streamUnmountOutput(process);
                if (process.waitFor() == 0) {
                    return true;
                }
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                // Try the next candidate.
            }
        }
        return false;
    }

    private void streamUnmountOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                // Drain output so fusermount cannot block on a full pipe.
            }
        }
    }

    private void setIdleStatus() {
        statusLabel.getStyleClass().setAll("status-pill", "status-idle");
        statusLabel.setText("Not mounted");
    }

    /** Persists the current field values so they're restored the next time the app opens. */
    void saveSettings() {
        AppSettings.setString(AppSettings.MOUNT_POINT, mountPointField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_HOST, hostField.getText().trim());
        AppSettings.setString(AppSettings.MOUNT_PORT, portField.getText().trim());
        AppSettings.setBoolean(AppSettings.MOUNT_OPTIONS, mountOptionsCheck.isSelected());
    }

    private void setPendingStatus(String text) {
        statusLabel.getStyleClass().setAll("status-pill", "status-pending");
        statusLabel.setText(text);
    }

    private void setRunningStatus(String text) {
        statusLabel.getStyleClass().setAll("status-pill", "status-running");
        statusLabel.setText(text);
    }

    private void showError(String message) {
        logConsole.log("[UI] " + message);
        Platform.runLater(() -> {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            statusLabel.getStyleClass().setAll("status-pill", "status-error");
            statusLabel.setText("Error");
            startButton.setDisable(false);
            testButton.setDisable(false);
            stopButton.setDisable(true);
        });
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }

    private static Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private static SVGPath icon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("button-icon");
        return icon;
    }

    private static void installNumericInputFilter(TextField field, String fallback) {
        String trimmed = field.getText() == null ? "" : field.getText().trim();
        field.setText(trimmed.matches("\\d+") ? trimmed : fallback);
        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().matches("\\d*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(filter));
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                field.setText(newValue.replaceAll("\\D", ""));
            }
        });
    }
}
