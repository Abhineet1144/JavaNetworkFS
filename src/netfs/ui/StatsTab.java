package netfs.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import netfs.state.OperationLog;
import netfs.state.OperationLogEntry;
import netfs.state.OperationSource;
import netfs.state.OperationStatus;
import netfs.state.TransferStats;

/**
 * "Stats" tab: live view of in-flight and recent operations from both the Host (server)
 * and Mount (client) sides, backed by {@link OperationLog}.
 */
public class StatsTab {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private final ObservableList<OperationLogEntry> rows = FXCollections.observableArrayList();
    private final TableView<OperationLogEntry> table = new TableView<>(rows);
    private final CheckBox persistCheck = new CheckBox("Persist operations log");
    private final Label countLabel = new Label("0 operations");

    private final Label hostReadTotalLabel = new Label("0 B");
    private final Label hostWriteTotalLabel = new Label("0 B");
    private final Label hostReadSpeedLabel = new Label("0 B/s");
    private final Label hostWriteSpeedLabel = new Label("0 B/s");
    private final Label mountReadTotalLabel = new Label("0 B");
    private final Label mountWriteTotalLabel = new Label("0 B");
    private final Label mountReadSpeedLabel = new Label("0 B/s");
    private final Label mountWriteSpeedLabel = new Label("0 B/s");

    private long lastStatsTickMillis = System.currentTimeMillis();
    private long lastHostReadBytes = 0;
    private long lastHostWriteBytes = 0;
    private long lastMountReadBytes = 0;
    private long lastMountWriteBytes = 0;

    private final VBox content;

    public StatsTab() {
        content = build();
        startTicker();
    }

    public VBox getContent() {
        return content;
    }

    private VBox build() {
        Label heading = new Label("Operations");
        heading.getStyleClass().add("section-label");

        table.getStyleClass().add("stats-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(List.of(
                timeColumn(),
                sourceColumn(),
                operationColumn(),
                detailColumn(),
                statusColumn(),
                infoColumn()));
        VBox.setVgrow(table, Priority.ALWAYS);

        persistCheck.setSelected(AppSettings.getBoolean(AppSettings.STATS_PERSIST, false));
        OperationLog.setPersist(persistCheck.isSelected());
        persistCheck.setOnAction(e -> {
            OperationLog.setPersist(persistCheck.isSelected());
            AppSettings.setBoolean(AppSettings.STATS_PERSIST, persistCheck.isSelected());
        });

        Button clearButton = new Button("Clear Log");
        clearButton.getStyleClass().add("danger-button");
        clearButton.setOnAction(e -> {
            OperationLog.clear();
            refresh();
        });

        HBox controls = new HBox(16, persistCheck, clearButton, countLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(
                "Completed operations are cleared automatically unless \"Persist\" is checked. "
                        + "Failed operations always stay listed until cleared.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        VBox box = new VBox(14, heading, controls, table, hint, buildTransferSection());
        box.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private VBox buildTransferSection() {
        Label heading = new Label("Transfer");
        heading.getStyleClass().add("section-label");

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(8);
        grid.setPadding(new Insets(4, 0, 0, 0));

        grid.add(new Label(""), 0, 0);
        grid.add(boldLabel("Read Total"), 1, 0);
        grid.add(boldLabel("Read Speed"), 2, 0);
        grid.add(boldLabel("Write Total"), 3, 0);
        grid.add(boldLabel("Write Speed"), 4, 0);

        grid.add(boldLabel("Host"), 0, 1);
        grid.add(hostReadTotalLabel, 1, 1);
        grid.add(hostReadSpeedLabel, 2, 1);
        grid.add(hostWriteTotalLabel, 3, 1);
        grid.add(hostWriteSpeedLabel, 4, 1);

        grid.add(boldLabel("Mount"), 0, 2);
        grid.add(mountReadTotalLabel, 1, 2);
        grid.add(mountReadSpeedLabel, 2, 2);
        grid.add(mountWriteTotalLabel, 3, 2);
        grid.add(mountWriteSpeedLabel, 4, 2);

        VBox box = new VBox(10, heading, grid);
        return box;
    }

    private static Label boldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private void startTicker() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            OperationLog.sweep();
            refresh();
            refreshTransferStats();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void refresh() {
        List<OperationLogEntry> snapshot = OperationLog.snapshot();
        rows.setAll(snapshot);
        table.refresh();
        countLabel.setText(snapshot.size() + " operation" + (snapshot.size() == 1 ? "" : "s"));
    }

    /** Pulls the latest cumulative totals from {@link TransferStats} and derives
     * read/write throughput from the delta since the last tick (~1 second). */
    private void refreshTransferStats() {
        long now = System.currentTimeMillis();
        double elapsedSeconds = Math.max(0.001, (now - lastStatsTickMillis) / 1000.0);

        long hostRead = TransferStats.getReadBytes(OperationSource.HOST);
        long hostWrite = TransferStats.getWriteBytes(OperationSource.HOST);
        long mountRead = TransferStats.getReadBytes(OperationSource.MOUNT);
        long mountWrite = TransferStats.getWriteBytes(OperationSource.MOUNT);

        hostReadTotalLabel.setText(formatBytes(hostRead));
        hostWriteTotalLabel.setText(formatBytes(hostWrite));
        mountReadTotalLabel.setText(formatBytes(mountRead));
        mountWriteTotalLabel.setText(formatBytes(mountWrite));

        hostReadSpeedLabel.setText(formatSpeed(hostRead - lastHostReadBytes, elapsedSeconds));
        hostWriteSpeedLabel.setText(formatSpeed(hostWrite - lastHostWriteBytes, elapsedSeconds));
        mountReadSpeedLabel.setText(formatSpeed(mountRead - lastMountReadBytes, elapsedSeconds));
        mountWriteSpeedLabel.setText(formatSpeed(mountWrite - lastMountWriteBytes, elapsedSeconds));

        lastHostReadBytes = hostRead;
        lastHostWriteBytes = hostWrite;
        lastMountReadBytes = mountRead;
        lastMountWriteBytes = mountWrite;
        lastStatsTickMillis = now;
    }

    private static String formatSpeed(long deltaBytes, double elapsedSeconds) {
        long perSecond = (long) Math.max(0, deltaBytes / elapsedSeconds);
        return formatBytes(perSecond) + "/s";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }

    private TableColumn<OperationLogEntry, String> timeColumn() {
        TableColumn<OperationLogEntry, String> column = new TableColumn<>("Time");
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                TIME_FORMAT.format(new Date(data.getValue().getStartTime()))));
        column.setMinWidth(80);
        column.setMaxWidth(90);
        return column;
    }

    private TableColumn<OperationLogEntry, String> sourceColumn() {
        TableColumn<OperationLogEntry, String> column = new TableColumn<>("Mode");
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getSource().name()));
        column.setMinWidth(70);
        column.setMaxWidth(90);
        return column;
    }

    private TableColumn<OperationLogEntry, String> operationColumn() {
        TableColumn<OperationLogEntry, String> column = new TableColumn<>("Operation");
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getOperation()));
        column.setMinWidth(90);
        column.setMaxWidth(110);
        return column;
    }

    private TableColumn<OperationLogEntry, String> detailColumn() {
        TableColumn<OperationLogEntry, String> column = new TableColumn<>("Detail");
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getDetail()));
        return column;
    }

    private TableColumn<OperationLogEntry, String> statusColumn() {
        TableColumn<OperationLogEntry, String> column = new TableColumn<>("Status");
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getStatus().name()));
        column.setMinWidth(90);
        column.setMaxWidth(110);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-cell-running", "status-cell-completed", "status-cell-failed");
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(status);
                if (OperationStatus.RUNNING.name().equals(status)) {
                    getStyleClass().add("status-cell-running");
                } else if (OperationStatus.FAILED.name().equals(status)) {
                    getStyleClass().add("status-cell-failed");
                } else {
                    getStyleClass().add("status-cell-completed");
                }
            }
        });
        return column;
    }

    private TableColumn<OperationLogEntry, String> infoColumn() {
        TableColumn<OperationLogEntry, String> column = new TableColumn<>("Info");
        column.setCellValueFactory(data -> {
            OperationLogEntry entry = data.getValue();
            String text;
            if (entry.getStatus() == OperationStatus.FAILED) {
                text = entry.getErrorMessage() != null ? entry.getErrorMessage() : "Failed";
            } else if (entry.getStatus() == OperationStatus.RUNNING) {
                text = entry.getDurationMillis() + " ms (running)";
            } else {
                text = entry.getDurationMillis() + " ms";
            }
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        return column;
    }
}
