package netfs.ui;

import java.io.File;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import netfs.config.ServerConfig;
import netfs.net.FileSystemServer;

/**
 * "Host" tab: configures and starts a {@link FileSystemServer} that shares a local
 * folder over the network.
 */
public class HostTab {

    private final LogConsole logConsole;
    private final VBox content;

    private final TextField sharedFolderField = new TextField(
            AppSettings.getString(AppSettings.HOST_SHARED_FOLDER, System.getProperty("user.home")));
    private final TextField portField = new TextField(AppSettings.getString(AppSettings.HOST_PORT, "10002"));
    private final TextField maxThreadsField = new TextField(
            AppSettings.getString(AppSettings.HOST_MAX_THREADS, "111"));
    private final TextField maxSizeField = new TextField(
            AppSettings.getString(AppSettings.HOST_MAX_SIZE, "1000000"));

    private final Button startButton = new Button("Start Server");
    private final Button stopButton = new Button("Stop Server");
    private final Label statusLabel = new Label("Stopped");
    private final Label errorLabel = new Label();

    public HostTab(LogConsole logConsole) {
        this.logConsole = logConsole;
        this.content = build();
        stopButton.setDisable(true);
        setIdleStatus();
        hideError();
    }

    public VBox getContent() {
        return content;
    }

    private VBox build() {
        Label heading = new Label("Server Options");
        heading.getStyleClass().add("section-label");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(4, 0, 16, 0));

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> chooseDirectory(sharedFolderField));

        int row = 0;
        form.addRow(row++, new Label("Shared Folder:"), sharedFolderField, browseButton);
        form.addRow(row++, new Label("Port:"), portField);
        form.addRow(row++, new Label("Max Threads:"), maxThreadsField);
        form.addRow(row++, new Label("Max Cache Size (bytes):"), maxSizeField);

        GridPane.setHgrow(sharedFolderField, Priority.ALWAYS);
        GridPane.setHgrow(portField, Priority.ALWAYS);
        GridPane.setHgrow(maxThreadsField, Priority.ALWAYS);
        GridPane.setHgrow(maxSizeField, Priority.ALWAYS);

        startButton.getStyleClass().add("primary-button");
        stopButton.getStyleClass().add("danger-button");
        startButton.setOnAction(e -> startServer());
        stopButton.setOnAction(e -> stopServer());

        statusLabel.getStyleClass().addAll("status-pill", "status-idle");

        HBox buttons = new HBox(10, startButton, stopButton, statusLabel);

        errorLabel.getStyleClass().add("error-banner");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(14, heading, form, buttons, errorLabel);
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

    private void startServer() {
        hideError();

        long maxSize;
        int port;
        int maxThreads;
        try {
            maxSize = Long.parseLong(maxSizeField.getText().trim());
            port = Integer.parseInt(portField.getText().trim());
            maxThreads = Integer.parseInt(maxThreadsField.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Invalid numeric input: " + ex.getMessage());
            return;
        }

        File sharedFolder = new File(sharedFolderField.getText().trim());
        if (!sharedFolder.isDirectory()) {
            showError("Shared folder does not exist: " + sharedFolder);
            return;
        }

        saveSettings();
        ServerConfig config = new ServerConfig(maxSize, port, maxThreads, sharedFolder);

        startButton.setDisable(true);
        stopButton.setDisable(false);
        setPendingStatus("Starting on port " + port + "...");

        Thread serverThread = new Thread(() -> {
            try {
                Platform.runLater(() -> setRunningStatus("Running on port " + port));
                FileSystemServer.start(config);
            } catch (Throwable ex) {
                showError("Server error: " + describe(ex));
            } finally {
                Platform.runLater(() -> {
                    startButton.setDisable(false);
                    stopButton.setDisable(true);
                    setIdleStatus();
                });
            }
        }, "netfs-server-ui");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopServer() {
        FileSystemServer.signalToStop();
        logConsole.log("[UI] Stop requested. Server will stop after its next accepted connection.");
        stopButton.setDisable(true);
    }

    /** Persists the current field values so they're restored the next time the app opens. */
    void saveSettings() {
        AppSettings.setString(AppSettings.HOST_SHARED_FOLDER, sharedFolderField.getText().trim());
        AppSettings.setString(AppSettings.HOST_PORT, portField.getText().trim());
        AppSettings.setString(AppSettings.HOST_MAX_THREADS, maxThreadsField.getText().trim());
        AppSettings.setString(AppSettings.HOST_MAX_SIZE, maxSizeField.getText().trim());
    }

    private void setIdleStatus() {
        statusLabel.getStyleClass().setAll("status-pill", "status-idle");
        statusLabel.setText("Stopped");
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
}

