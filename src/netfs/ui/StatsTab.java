package netfs.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
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

    private final VBox operationRows = new VBox();
    private final ScrollPane operationScroll = new ScrollPane(operationRows);
    private final CheckBox persistCheck = new CheckBox("Persist operations log");
    private final Label countLabel = new Label("0 operations");
    private long latestRenderedId = -1;
    private boolean followLatest = true;

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
        heading.getStyleClass().add("page-title");

        VBox operationsTable = buildOperationsTable();
        VBox.setVgrow(operationsTable, Priority.ALWAYS);

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

        VBox box = new VBox(14, heading, controls, operationsTable, hint, buildTransferSection());
        box.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(operationsTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildOperationsTable() {
        GridPane header = new GridPane();
        header.getStyleClass().add("operations-header");
        addOperationHeader(header, "Time", 0, "operations-top-left");
        addOperationHeader(header, "Mode", 1);
        addOperationHeader(header, "Operation", 2);
        addOperationHeader(header, "Detail", 3);
        addOperationHeader(header, "Status", 4);
        addOperationHeader(header, "Info", 5, "operations-top-right");

        operationRows.getStyleClass().add("operations-rows");
        operationScroll.getStyleClass().add("operations-scroll");
        operationScroll.setFitToWidth(true);
        operationScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        operationScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        operationScroll.vvalueProperty().addListener((obs, oldValue, newValue) ->
                followLatest = newValue.doubleValue() <= 0.02);
        VBox.setVgrow(operationScroll, Priority.ALWAYS);

        VBox tableBox = new VBox(header, operationScroll);
        tableBox.getStyleClass().add("operations-table");
        return tableBox;
    }

    private VBox buildTransferSection() {
        Label heading = new Label("Transfer");
        heading.getStyleClass().add("section-label");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("transfer-table");
        grid.setPadding(new Insets(4, 0, 0, 0));

        addTransferCell(grid, new Label("Mode"), 0, 0, "transfer-header", "transfer-top-left");
        addTransferCell(grid, new Label("Read Total"), 1, 0, "transfer-header");
        addTransferCell(grid, new Label("Read Speed"), 2, 0, "transfer-header");
        addTransferCell(grid, new Label("Write Total"), 3, 0, "transfer-header");
        addTransferCell(grid, new Label("Write Speed"), 4, 0, "transfer-header", "transfer-top-right");

        addTransferCell(grid, new Label("Host"), 0, 1, "transfer-row-header");
        addTransferCell(grid, hostReadTotalLabel, 1, 1, "transfer-cell");
        addTransferCell(grid, hostReadSpeedLabel, 2, 1, "transfer-cell");
        addTransferCell(grid, hostWriteTotalLabel, 3, 1, "transfer-cell");
        addTransferCell(grid, hostWriteSpeedLabel, 4, 1, "transfer-cell");

        addTransferCell(grid, new Label("Mount"), 0, 2, "transfer-row-header", "transfer-bottom-left");
        addTransferCell(grid, mountReadTotalLabel, 1, 2, "transfer-cell");
        addTransferCell(grid, mountReadSpeedLabel, 2, 2, "transfer-cell");
        addTransferCell(grid, mountWriteTotalLabel, 3, 2, "transfer-cell");
        addTransferCell(grid, mountWriteSpeedLabel, 4, 2, "transfer-cell", "transfer-bottom-right");

        VBox box = new VBox(10, heading, grid);
        return box;
    }

    private static void addTransferCell(GridPane grid, Label label, int column, int row, String... styleClasses) {
        label.getStyleClass().add("transfer-cell");
        label.getStyleClass().addAll(styleClasses);
        label.setMaxWidth(Double.MAX_VALUE);
        grid.add(label, column, row);
        GridPane.setHgrow(label, Priority.ALWAYS);
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
        boolean shouldFollowLatest = followLatest || latestRenderedId < 0;

        operationRows.getChildren().clear();
        for (int i = snapshot.size() - 1, rowIndex = 0; i >= 0; i--, rowIndex++) {
            OperationLogEntry entry = snapshot.get(i);
            operationRows.getChildren().add(operationRow(entry, rowIndex % 2 == 1,
                    i == snapshot.size() - 1, i == 0));
        }
        if (!snapshot.isEmpty()) {
            latestRenderedId = snapshot.get(snapshot.size() - 1).getId();
            if (shouldFollowLatest) {
                operationScroll.setVvalue(0.0);
                followLatest = true;
            }
        }
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

    private GridPane operationRow(OperationLogEntry entry, boolean odd, boolean latest, boolean bottomRow) {
        GridPane row = new GridPane();
        row.getStyleClass().add("operations-row");
        if (odd) {
            row.getStyleClass().add("operations-row-odd");
        }
        if (latest) {
            row.getStyleClass().add("operations-row-latest");
        }
        if (bottomRow) {
            row.getStyleClass().add("operations-row-bottom");
        }
        addOperationCell(row, TIME_FORMAT.format(new Date(entry.getStartTime())), 0,
                bottomRow ? "operations-bottom-left" : null);
        addOperationCell(row, entry.getSource().name(), 1);
        addOperationCell(row, entry.getOperation(), 2);
        addOperationCell(row, entry.getDetail(), 3, "operations-detail-cell");
        addOperationCell(row, entry.getStatus().name(), 4, statusStyle(entry.getStatus()));
        addOperationCell(row, infoText(entry), 5, bottomRow ? "operations-bottom-right" : null);
        return row;
    }

    private static void addOperationHeader(GridPane row, String text, int column, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().add("operations-cell");
        label.getStyleClass().add("operations-header-cell");
        label.getStyleClass().addAll(styleClasses);
        addOperationNode(row, label, column);
    }

    private static void addOperationCell(GridPane row, String text, int column, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().add("operations-cell");
        for (String styleClass : styleClasses) {
            if (styleClass != null) {
                label.getStyleClass().add(styleClass);
            }
        }
        addOperationNode(row, label, column);
    }

    private static void addOperationNode(GridPane row, Label label, int column) {
        ensureOperationColumns(row);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        row.add(label, column, 0);
        GridPane.setHgrow(label, Priority.ALWAYS);
    }

    private static void ensureOperationColumns(GridPane row) {
        if (!row.getColumnConstraints().isEmpty()) {
            return;
        }
        double[] widths = {12, 10, 14, 32, 14, 18};
        for (double width : widths) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(width);
            constraints.setHgrow(Priority.ALWAYS);
            row.getColumnConstraints().add(constraints);
        }
    }

    private static String statusStyle(OperationStatus status) {
        if (status == OperationStatus.RUNNING) {
            return "status-cell-running";
        }
        if (status == OperationStatus.FAILED) {
            return "status-cell-failed";
        }
        return "status-cell-completed";
    }

    private static String infoText(OperationLogEntry entry) {
        if (entry.getStatus() == OperationStatus.FAILED) {
            return entry.getErrorMessage() != null ? entry.getErrorMessage() : "Failed";
        }
        if (entry.getStatus() == OperationStatus.RUNNING) {
            return entry.getDurationMillis() + " ms (running)";
        }
        return entry.getDurationMillis() + " ms";
    }
}
