package netfs.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
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

    private static final int MAX_VISIBLE_OPERATIONS = 500;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private final ObservableList<OperationLogEntry> operationItems = FXCollections.observableArrayList();
    private final TableView<OperationLogEntry> operationsTable = new TableView<>(operationItems);
    private final CheckBox persistCheck = new CheckBox("Persist operations log");
    private final Label countLabel = new Label("0 operations");
    private final Timeline timeline;
    private long latestRenderedId = -1;
    private String latestRenderedSignature = "";
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
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            OperationLog.sweep();
            refresh();
            refreshTransferStats();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public VBox getContent() {
        return content;
    }

    void onShown() {
        refresh();
        refreshTransferStats();
        timeline.play();
    }

    void onHidden() {
        timeline.stop();
    }

    private VBox build() {
        Label heading = new Label("Operations");
        heading.getStyleClass().add("page-title");

        buildOperationsTable();
        VBox.setVgrow(operationsTable, Priority.ALWAYS);

        persistCheck.setSelected(AppSettings.getBoolean(AppSettings.STATS_PERSIST, false));
        OperationLog.setPersist(persistCheck.isSelected());
        persistCheck.setOnAction(e -> {
            OperationLog.setPersist(persistCheck.isSelected());
            AppSettings.setBoolean(AppSettings.STATS_PERSIST, persistCheck.isSelected());
        });

        Button clearButton = new Button("Clear Log");
        clearButton.getStyleClass().addAll("danger-button", "utility-button");
        clearButton.setOnAction(e -> {
            OperationLog.clear();
            refresh();
        });

        HBox controls = new HBox(16, persistCheck, clearButton, countLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(14, heading, controls, operationsTable, buildTransferSection());
        box.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(operationsTable, Priority.ALWAYS);
        return box;
    }

    private void buildOperationsTable() {
        operationsTable.getStyleClass().add("operations-table-view");
        operationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        operationsTable.setPlaceholder(new Label("No operations yet"));
        operationsTable.setFixedCellSize(38);

        operationsTable.getColumns().clear();
        operationsTable.getColumns().add(column("Time",
                entry -> TIME_FORMAT.format(new Date(entry.getStartTime())), 0.12, null));
        operationsTable.getColumns().add(column("Mode", entry -> entry.getSource().name(), 0.10, null));
        operationsTable.getColumns().add(column("Operation", OperationLogEntry::getOperation, 0.14, null));
        operationsTable.getColumns().add(column("Detail", OperationLogEntry::getDetail, 0.32, "operations-detail-cell"));
        operationsTable.getColumns().add(column("Status", entry -> entry.getStatus().name(), 0.14,
                "operations-status-cell"));
        operationsTable.getColumns().add(column("Info", StatsTab::infoText, 0.18, "operations-info-cell"));

        operationsTable.setRowFactory(table -> {
            TableRow<OperationLogEntry> row = new TableRow<>() {
                @Override
                protected void updateItem(OperationLogEntry entry, boolean empty) {
                    super.updateItem(entry, empty);
                    getStyleClass().removeAll("operations-row-latest", "operations-row-running",
                            "operations-row-completed", "operations-row-failed");
                    if (empty || entry == null) {
                        return;
                    }
                    if (entry.getId() == latestRenderedId) {
                        getStyleClass().add("operations-row-latest");
                    }
                    getStyleClass().add(statusRowStyle(entry.getStatus()));
                }
            };
            row.indexProperty().addListener((obs, oldValue, newValue) -> followLatest =
                    newValue.intValue() >= Math.max(0, operationItems.size() - 2));
            return row;
        });
    }

    private static TableColumn<OperationLogEntry, String> column(
            String title,
            java.util.function.Function<OperationLogEntry, String> valueFactory,
            double widthRatio,
            String cellStyleClass) {
        TableColumn<OperationLogEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(valueFactory.apply(data.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                getStyleClass().removeAll("operations-detail-cell", "operations-status-cell", "operations-info-cell",
                        "status-cell-running", "status-cell-completed", "status-cell-failed");
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                setText(text);
                if (cellStyleClass != null) {
                    getStyleClass().add(cellStyleClass);
                }
                if ("operations-status-cell".equals(cellStyleClass) || "operations-info-cell".equals(cellStyleClass)) {
                    getStyleClass().add(statusCellStyle(getTableRow().getItem().getStatus()));
                }
            }
        });
        column.setReorderable(false);
        column.setResizable(true);
        column.setSortable(false);
        column.setMaxWidth(Double.MAX_VALUE);
        column.setPrefWidth(1000 * widthRatio);
        return column;
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

        return new VBox(10, heading, grid);
    }

    private static void addTransferCell(GridPane grid, Label label, int column, int row, String... styleClasses) {
        label.getStyleClass().add("transfer-cell");
        label.getStyleClass().addAll(styleClasses);
        label.setMaxWidth(Double.MAX_VALUE);
        grid.add(label, column, row);
        GridPane.setHgrow(label, Priority.ALWAYS);
    }

    private void refresh() {
        List<OperationLogEntry> snapshot = OperationLog.snapshot();
        int start = Math.max(0, snapshot.size() - MAX_VISIBLE_OPERATIONS);
        List<OperationLogEntry> visible = snapshot.subList(start, snapshot.size());
        String signature = operationSignature(visible);
        if (signature.equals(latestRenderedSignature)) {
            countLabel.setText(visible.size() + " operation" + (visible.size() == 1 ? "" : "s"));
            return;
        }

        boolean shouldFollowLatest = followLatest || latestRenderedId < 0;
        operationItems.setAll(visible);
        latestRenderedSignature = signature;
        if (!visible.isEmpty()) {
            latestRenderedId = visible.get(visible.size() - 1).getId();
            if (shouldFollowLatest) {
                operationsTable.scrollTo(operationItems.size() - 1);
                followLatest = true;
            }
        }
        countLabel.setText(visible.size() + " operation" + (visible.size() == 1 ? "" : "s"));
    }

    private static String operationSignature(List<OperationLogEntry> entries) {
        StringBuilder signature = new StringBuilder(entries.size() * 16);
        for (OperationLogEntry entry : entries) {
            signature.append(entry.getId())
                    .append(':')
                    .append(entry.getStatus())
                    .append(':')
                    .append(entry.getEndTime())
                    .append(';');
        }
        return signature.toString();
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

    private static String statusRowStyle(OperationStatus status) {
        if (status == OperationStatus.RUNNING) {
            return "operations-row-running";
        }
        if (status == OperationStatus.FAILED) {
            return "operations-row-failed";
        }
        return "operations-row-completed";
    }

    private static String statusCellStyle(OperationStatus status) {
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
