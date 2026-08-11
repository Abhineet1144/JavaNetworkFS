package netfs.ui;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * Shared console area that mirrors {@code System.out}/{@code System.err} so the user
 * can see server/client logs directly from the UI. The line count is capped since a
 * busy server/mount can print many lines per operation, and an ever-growing JavaFX
 * TextArea gets noticeably laggy well before it reaches even a few thousand lines.
 */
public class LogConsole {

    private static final int MAX_LINES = 1500;
    private static final int TRIM_CHECK_INTERVAL = 100;
    private static final boolean MIRROR_TO_ORIGINAL_STREAMS = false;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TextArea textArea = new TextArea();
    private final VBox content;

    private int totalLines = 0;
    private int linesSinceTrim = 0;

    public LogConsole() {
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.getStyleClass().add("log-console");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        Button clearButton = new Button("Clear");
        clearButton.getStyleClass().addAll("danger-button", "console-clear-button");
        clearButton.setGraphic(trashIcon());
        clearButton.setAccessibleText("Clear console");
        clearButton.setOnAction(event -> clear());

        HBox footer = new HBox(clearButton);
        footer.setAlignment(Pos.CENTER_LEFT);

        content = new VBox(10, textArea, footer);
        content.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(content, Priority.ALWAYS);

        redirectSystemStreams();
    }

    public VBox getContent() {
        return content;
    }

    public void log(String message) {
        Platform.runLater(() -> appendLine(message));
    }

    private void clear() {
        textArea.clear();
        totalLines = 0;
        linesSinceTrim = 0;
    }

    /** Must run on the FX thread. */
    private void appendLine(String line) {
        textArea.appendText("[" + TIME_FORMAT.format(LocalTime.now()) + "] " + line + System.lineSeparator());
        totalLines++;
        linesSinceTrim++;
        if (linesSinceTrim >= TRIM_CHECK_INTERVAL && totalLines > MAX_LINES) {
            trimOldLines();
            linesSinceTrim = 0;
        }
        textArea.setScrollTop(Double.MAX_VALUE);
    }

    /** Drops the oldest lines so the TextArea never holds much more than MAX_LINES. Must run on the FX thread. */
    private void trimOldLines() {
        String text = textArea.getText();
        int linesToRemove = totalLines - MAX_LINES;
        int index = 0;
        for (int i = 0; i < linesToRemove; i++) {
            int next = text.indexOf('\n', index);
            if (next < 0) {
                index = text.length();
                break;
            }
            index = next + 1;
        }
        if (index > 0) {
            textArea.deleteText(0, index);
        }
        totalLines = MAX_LINES;
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

    /** Captures every line written to the given stream and mirrors it into the console TextArea. */
    private class ConsoleOutputStream extends OutputStream {
        private final PrintStream original;
        private final boolean mirrorToOriginal;
        private final StringBuilder buffer = new StringBuilder();

        ConsoleOutputStream(PrintStream original, boolean mirrorToOriginal) {
            this.original = original;
            this.mirrorToOriginal = mirrorToOriginal;
        }

        @Override
        public void write(int b) {
            if (mirrorToOriginal) {
                original.write(b);
            }
            char c = (char) b;
            if (c == '\n') {
                String line = buffer.toString();
                buffer.setLength(0);
                Platform.runLater(() -> appendLine(line));
            } else if (c != '\r') {
                buffer.append(c);
            }
        }
    }
}
