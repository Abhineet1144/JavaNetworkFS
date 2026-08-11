package netfs.ui;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * Shared console area that mirrors {@code System.out}/{@code System.err} so the user
 * can see server/client logs directly from the UI. Logs are stored in a bounded,
 * virtualized list so noisy server/mount output does not grow the UI indefinitely.
 */
public class LogConsole {

    private static final int MAX_LINES = 800;
    private static final int MAX_BATCH_SIZE = 250;
    private static final int FLUSH_DELAY_MS = 50;
    private static final int MAX_LINE_CHARS = 1200;
    private static final boolean MIRROR_TO_ORIGINAL_STREAMS = false;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<String> logLines = FXCollections.observableArrayList();
    private final Queue<String> pendingLines = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService flushExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "netfs-console-flush");
        thread.setDaemon(true);
        return thread;
    });

    private ListView<String> logView;
    private Label emptyLabel;
    private final CheckBox autoScrollCheck = new CheckBox("Auto-scroll");
    private VBox content;

    public LogConsole() {
        redirectSystemStreams();
    }

    public VBox getContent() {
        if (content == null) {
            content = buildContent();
        }
        updateEmptyState();
        return content;
    }

    private VBox buildContent() {
        logView = new ListView<>(logLines);
        logView.getStyleClass().add("log-console");
        logView.setCellFactory(view -> new WrappedLogCell());
        logView.setFixedCellSize(20);

        emptyLabel = new Label("No logs yet");
        emptyLabel.getStyleClass().add("console-empty-state");
        emptyLabel.setMouseTransparent(true);

        StackPane consolePane = new StackPane(logView, emptyLabel);
        StackPane.setAlignment(emptyLabel, Pos.CENTER);
        VBox.setVgrow(consolePane, Priority.ALWAYS);

        Button clearButton = new Button("Clear");
        clearButton.getStyleClass().addAll("danger-button", "console-clear-button");
        clearButton.setGraphic(trashIcon());
        clearButton.setAccessibleText("Clear console");
        clearButton.setOnAction(event -> clear());

        autoScrollCheck.setSelected(AppSettings.getBoolean(AppSettings.CONSOLE_AUTO_SCROLL, true));
        autoScrollCheck.getStyleClass().add("console-toggle");
        autoScrollCheck.selectedProperty().addListener((obs, wasSelected, isSelected) ->
                AppSettings.setBoolean(AppSettings.CONSOLE_AUTO_SCROLL, isSelected));
        if (autoScrollCheck.isSelected() && !logLines.isEmpty()) {
            Platform.runLater(() -> logView.scrollTo(logLines.size() - 1));
        }

        HBox footer = new HBox(10, clearButton, autoScrollCheck);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, consolePane, footer);
        box.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    public void log(String message) {
        enqueueLine(message);
    }

    private void clear() {
        pendingLines.clear();
        logLines.clear();
        updateEmptyState();
    }

    private void enqueueLine(String line) {
        String cleanLine = normalizeLine(line);
        pendingLines.add("[" + TIME_FORMAT.format(LocalTime.now()) + "] " + cleanLine);
        scheduleFlush();
    }

    private void updateEmptyState() {
        if (emptyLabel == null) {
            return;
        }
        boolean empty = logLines.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);
    }

    private void scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) {
            return;
        }
        flushExecutor.schedule(() -> Platform.runLater(this::flushPendingLines), FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Must run on the FX thread. */
    private void flushPendingLines() {
        List<String> batch = new ArrayList<>(MAX_BATCH_SIZE);
        for (int i = 0; i < MAX_BATCH_SIZE; i++) {
            String line = pendingLines.poll();
            if (line == null) {
                break;
            }
            batch.add(line);
        }

        if (!batch.isEmpty()) {
            logLines.addAll(batch);
            int overflow = logLines.size() - MAX_LINES;
            if (overflow > 0) {
                logLines.remove(0, overflow);
            }
            if (autoScrollCheck.isSelected() && logView != null) {
                logView.scrollTo(logLines.size() - 1);
            }
            updateEmptyState();
        }

        flushScheduled.set(false);
        if (!pendingLines.isEmpty()) {
            scheduleFlush();
        }
    }

    private void redirectSystemStreams() {
        System.setOut(new PrintStream(new ConsoleOutputStream(System.out, MIRROR_TO_ORIGINAL_STREAMS), true));
        System.setErr(new PrintStream(new ConsoleOutputStream(System.err, MIRROR_TO_ORIGINAL_STREAMS), true));
    }

    private static SVGPath trashIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent("M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM8,9h8v10H8V9zM15.5,4l-1,-1h-5l-1,1H5v2h14V4h-3.5z");
        icon.getStyleClass().add("button-icon");
        return icon;
    }

    private static String normalizeLine(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String cleaned = line.replace('\t', ' ').strip();
        if (cleaned.length() <= MAX_LINE_CHARS) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_LINE_CHARS) + " ...";
    }

    private static class WrappedLogCell extends ListCell<String> {
        private final Label label = new Label();

        WrappedLogCell() {
            label.getStyleClass().add("log-line");
            label.setWrapText(false);
            label.setMinWidth(Label.USE_PREF_SIZE);
            setMinHeight(20);
            setPrefHeight(20);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                label.setText(item);
                setText(null);
                setGraphic(label);
            }
        }
    }

    /** Captures every line written to the given stream and mirrors it into the console. */
    private class ConsoleOutputStream extends OutputStream {
        private final PrintStream original;
        private final boolean mirrorToOriginal;
        private byte[] buffer = new byte[1024];
        private int bufferLength;

        ConsoleOutputStream(PrintStream original, boolean mirrorToOriginal) {
            this.original = original;
            this.mirrorToOriginal = mirrorToOriginal;
        }

        @Override
        public void write(int b) {
            if (mirrorToOriginal) {
                original.write(b);
            }
            if (b == '\n') {
                flushBufferAsLine();
            } else if (b != '\r') {
                appendByte((byte) b);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (mirrorToOriginal) {
                original.write(bytes, offset, length);
            }
            for (int i = offset; i < offset + length; i++) {
                int value = bytes[i] & 0xff;
                if (value == '\n') {
                    flushBufferAsLine();
                } else if (value != '\r') {
                    appendByte((byte) value);
                }
            }
        }

        private void appendByte(byte value) {
            if (bufferLength == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            buffer[bufferLength++] = value;
        }

        private void flushBufferAsLine() {
            String line = decodeBuffer();
            bufferLength = 0;
            enqueueLine(line);
        }

        private String decodeBuffer() {
            try {
                CharBuffer decoded = StandardCharsets.UTF_8
                        .newDecoder()
                        .decode(ByteBuffer.wrap(buffer, 0, bufferLength));
                return decoded.toString();
            } catch (CharacterCodingException ex) {
                return new String(buffer, 0, bufferLength, StandardCharsets.UTF_8);
            }
        }
    }
}
