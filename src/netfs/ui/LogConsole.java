package netfs.ui;

import java.io.OutputStream;
import java.io.PrintStream;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shared console area that mirrors {@code System.out}/{@code System.err} so the user
 * can see server/client logs directly from the UI. The line count is capped since a
 * busy server/mount can print many lines per operation, and an ever-growing JavaFX
 * TextArea gets noticeably laggy well before it reaches even a few thousand lines.
 */
public class LogConsole {

    private static final int MAX_LINES = 1500;
    private static final int TRIM_CHECK_INTERVAL = 100;

    private final TextArea textArea = new TextArea();
    private final VBox content;

    private int totalLines = 0;
    private int linesSinceTrim = 0;

    public LogConsole() {
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(10);
        textArea.getStyleClass().add("log-console");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        Label label = new Label("Console");
        label.getStyleClass().add("section-label");
        content = new VBox(6, label, textArea);
        content.setPadding(new Insets(8, 20, 16, 20));

        redirectSystemStreams();
    }

    public VBox getContent() {
        return content;
    }

    public void log(String message) {
        Platform.runLater(() -> appendLine(message));
    }

    /** Must run on the FX thread. */
    private void appendLine(String line) {
        textArea.appendText(line + System.lineSeparator());
        totalLines++;
        linesSinceTrim++;
        if (linesSinceTrim >= TRIM_CHECK_INTERVAL && totalLines > MAX_LINES) {
            trimOldLines();
            linesSinceTrim = 0;
        }
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
        System.setOut(new PrintStream(new ConsoleOutputStream(System.out), true));
        System.setErr(new PrintStream(new ConsoleOutputStream(System.err), true));
    }

    /** Tees every line written to the given stream into the console TextArea as well. */
    private class ConsoleOutputStream extends OutputStream {
        private final PrintStream original;
        private final StringBuilder buffer = new StringBuilder();

        ConsoleOutputStream(PrintStream original) {
            this.original = original;
        }

        @Override
        public void write(int b) {
            original.write(b);
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
