package netfs.ui;

import static org.purejava.appindicator.app_indicator_h_14.gtk_widget_show_all;
import static org.purejava.appindicator.app_indicator_h_15.gtk_menu_new;
import static org.purejava.appindicator.app_indicator_h_15.gtk_menu_shell_append;
import static org.purejava.appindicator.app_indicator_h_16.gtk_menu_item_new_with_label;
import static org.purejava.appindicator.app_indicator_h_17.gtk_init_check;
import static org.purejava.appindicator.app_indicator_h_17.gtk_main;
import static org.purejava.appindicator.app_indicator_h_17.gtk_main_quit;
import static org.purejava.appindicator.app_indicator_h_18.gtk_separator_menu_item_new;
import static org.purejava.appindicator.app_indicator_h_20.APP_INDICATOR_CATEGORY_APPLICATION_STATUS;
import static org.purejava.appindicator.app_indicator_h_20.APP_INDICATOR_STATUS_ACTIVE;
import static org.purejava.appindicator.app_indicator_h_20.APP_INDICATOR_STATUS_PASSIVE;
import static org.purejava.appindicator.app_indicator_h_20.app_indicator_new_with_path;
import static org.purejava.appindicator.app_indicator_h_20.app_indicator_set_icon;
import static org.purejava.appindicator.app_indicator_h_20.app_indicator_set_icon_theme_path;
import static org.purejava.appindicator.app_indicator_h_20.app_indicator_set_menu;
import static org.purejava.appindicator.app_indicator_h_20.app_indicator_set_status;
import static org.purejava.appindicator.app_indicator_h_20.app_indicator_set_title;
import static org.purejava.appindicator.app_indicator_h_6.g_signal_connect_data;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import org.purejava.appindicator.GCallback;

final class AppIndicatorTray implements AutoCloseable {

    private static final String ICON_NAME = "netfs-tray";
    private static final int TRAY_ICON_SIZE = 48;

    private final Arena arena;
    private volatile boolean closed;
    private volatile boolean arenaClosed;
    private volatile MemorySegment indicator = MemorySegment.NULL;

    private AppIndicatorTray(Arena arena) {
        this.arena = arena;
    }

    static AppIndicatorTray install(
            Runnable showAction,
            Runnable hideAction,
            Runnable quitAction,
            Consumer<String> failureHandler) {
        Arena arena = Arena.ofShared();
        AppIndicatorTray tray = new AppIndicatorTray(arena);
        Thread gtkThread = new Thread(
                () -> tray.installOnGtkThread(showAction, hideAction, quitAction, failureHandler),
                "netfs-appindicator");
        gtkThread.setDaemon(true);
        gtkThread.start();
        return tray;
    }

    private void installOnGtkThread(
            Runnable showAction,
            Runnable hideAction,
            Runnable quitAction,
            Consumer<String> failureHandler) {
        try {
            Path iconDir = writeTrayIcon();

            if (gtk_init_check(MemorySegment.NULL, MemorySegment.NULL) == 0) {
                throw new IllegalStateException("GTK could not be initialized");
            }

            MemorySegment menu = gtk_menu_new();
            appendMenuItem(arena, menu, "Show", showAction);
            appendMenuItem(arena, menu, "Hide", hideAction);
            gtk_menu_shell_append(menu, gtk_separator_menu_item_new());
            appendMenuItem(arena, menu, "Quit", quitAction);
            gtk_widget_show_all(menu);

            indicator = app_indicator_new_with_path(
                    arena.allocateUtf8String("netfs"),
                    arena.allocateUtf8String(ICON_NAME),
                    APP_INDICATOR_CATEGORY_APPLICATION_STATUS(),
                    arena.allocateUtf8String(iconDir.toString()));
            if (indicator.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("AppIndicator returned a null indicator");
            }
            app_indicator_set_icon_theme_path(indicator, arena.allocateUtf8String(iconDir.toString()));
            app_indicator_set_icon(indicator, arena.allocateUtf8String(ICON_NAME));
            app_indicator_set_title(indicator, arena.allocateUtf8String("NetFS"));
            app_indicator_set_menu(indicator, menu);
            app_indicator_set_status(indicator, APP_INDICATOR_STATUS_ACTIVE());

            gtk_main();
        } catch (Throwable ex) {
            String message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            System.err.println("[UI] Failed to install desktop tray icon: " + message);
            Platform.runLater(() -> failureHandler.accept(message));
            closeArena();
        }
    }

    private static void appendMenuItem(Arena arena, MemorySegment menu, String label, Runnable action) {
        MemorySegment item = gtk_menu_item_new_with_label(arena.allocateUtf8String(label));
        MemorySegment callback = GCallback.allocate(() -> Platform.runLater(action), arena);
        g_signal_connect_data(
                item,
                arena.allocateUtf8String("activate"),
                callback,
                MemorySegment.NULL,
                MemorySegment.NULL,
                0);
        gtk_menu_shell_append(menu, item);
    }

    private static Path writeTrayIcon() throws IOException {
        Path iconDir = Path.of(
                System.getProperty("user.home"),
                ".local",
                "share",
                "icons",
                "hicolor",
                TRAY_ICON_SIZE + "x" + TRAY_ICON_SIZE,
                "apps");
        Files.createDirectories(iconDir);
        Path iconFile = iconDir.resolve(ICON_NAME + ".png");

        try (InputStream stream = AppIndicatorTray.class.getResourceAsStream("/assets/icons/netfs-256.png")) {
            BufferedImage source = stream != null ? ImageIO.read(stream) : null;
            if (source != null) {
                writeScaledTrayIcon(source, iconFile);
                return iconDir;
            }
        }

        BufferedImage image = new BufferedImage(TRAY_ICON_SIZE, TRAY_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(233, 84, 32));
        int squareSize = 28;
        int squareOffset = (TRAY_ICON_SIZE - squareSize) / 2;
        graphics.fillRect(squareOffset, squareOffset, squareSize, squareSize);
        graphics.dispose();

        ImageIO.write(image, "png", iconFile.toFile());
        return iconDir;
    }

    private static void writeScaledTrayIcon(BufferedImage source, Path iconFile) throws IOException {
        BufferedImage trimmed = trimTransparentPadding(source);
        BufferedImage image = new BufferedImage(TRAY_ICON_SIZE, TRAY_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(trimmed, 0, 0, TRAY_ICON_SIZE, TRAY_ICON_SIZE, null);
        graphics.dispose();
        ImageIO.write(image, "png", iconFile.toFile());
    }

    private static BufferedImage trimTransparentPadding(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!indicator.equals(MemorySegment.NULL)) {
            app_indicator_set_status(indicator, APP_INDICATOR_STATUS_PASSIVE());
        }
        gtk_main_quit();
        closeArena();
    }

    private void closeArena() {
        if (!arenaClosed) {
            arenaClosed = true;
            arena.close();
        }
    }
}
