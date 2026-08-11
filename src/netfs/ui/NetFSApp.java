package netfs.ui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

/**
 * Entry point for the Java Network FS UI. Lets the user start the app in either
 * "Host" mode (run a {@link netfs.net.FileSystemServer}) or "Mount" mode
 * (run a {@link netfs.net.FileSystemClient} that mounts a remote share via FUSE).
 */
public class NetFSApp extends Application {

    private static final double DEFAULT_STAGE_WIDTH = 780;
    private static final double DEFAULT_STAGE_HEIGHT = 800;
    private static final double MIN_RESTORED_STAGE_WIDTH = DEFAULT_STAGE_WIDTH;
    private static final double MIN_RESTORED_STAGE_HEIGHT = DEFAULT_STAGE_HEIGHT;

    private TrayIcon desktopTrayIcon;
    private HostTab hostTab;
    private MountTab mountTab;
    private SettingsTab settingsTab;
    private double lastStageX;
    private double lastStageY;
    private double lastStageWidth;
    private double lastStageHeight;
    private boolean hasSavedStageBounds;

    @Override
    public void start(Stage stage) {
        LogConsole logConsole = new LogConsole();

        // Safety net: any exception on a background/worker thread (e.g. connection pool
        // workers) gets logged instead of silently killing that thread or the whole app.
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[UI] Unhandled error on thread '" + thread.getName() + "': "
                    + describe(throwable));
            throwable.printStackTrace();
        });

        Platform.setImplicitExit(false);

        settingsTab = new SettingsTab();
        hostTab = new HostTab(logConsole, settingsTab);
        mountTab = new MountTab(logConsole, settingsTab);
        StatsTab statsTab = new StatsTab();

        StackPane contentPane = new StackPane(hostTab.getContent());
        contentPane.getStyleClass().add("content-pane");
        ToggleButton hostButton = navButton("Host", hostTab.getContent(), contentPane);
        ToggleButton mountButton = navButton("Mount", mountTab.getContent(), contentPane);
        ToggleButton statsButton = navButton("Stats", statsTab.getContent(), contentPane);
        ToggleButton consoleButton = navButton("Console", logConsole.getContent(), contentPane);
        ToggleButton settingsButton = iconNavButton(settingsTab.getContent(), contentPane);

        ToggleGroup navGroup = new ToggleGroup();
        hostButton.setToggleGroup(navGroup);
        mountButton.setToggleGroup(navGroup);
        statsButton.setToggleGroup(navGroup);
        consoleButton.setToggleGroup(navGroup);
        settingsButton.setToggleGroup(navGroup);
        navGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            }
        });
        hostButton.setSelected(true);

        HBox mainNav = new HBox(4, hostButton, mountButton, statsButton, consoleButton);
        mainNav.setAlignment(Pos.CENTER);

        BorderPane navBar = new BorderPane();
        navBar.getStyleClass().add("nav-bar");
        navBar.setPadding(new Insets(9, 16, 9, 16));
        navBar.setCenter(mainNav);
        navBar.setRight(settingsButton);
        BorderPane.setAlignment(settingsButton, Pos.CENTER_RIGHT);

        VBox mainContent = new VBox(navBar, contentPane);
        VBox.setVgrow(contentPane, javafx.scene.layout.Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setCenter(mainContent);

        Scene scene = new Scene(root, DEFAULT_STAGE_WIDTH, DEFAULT_STAGE_HEIGHT);
        scene.getStylesheets().add(NetFSApp.class.getResource("style.css").toExternalForm());

        stage.setTitle("NetFS");
        stage.setMinWidth(MIN_RESTORED_STAGE_WIDTH);
        stage.setMinHeight(MIN_RESTORED_STAGE_HEIGHT);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            event.consume();
            saveAllSettings();
            if (settingsTab.isCloseToTrayEnabled()) {
                hideStage(stage);
            } else {
                quitApplication();
            }
        });
        stage.show();
        installDesktopTrayIcon(stage, logConsole);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }

    private static ToggleButton navButton(String text, Node content, StackPane contentPane) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("nav-button");
        button.setOnAction(event -> contentPane.getChildren().setAll(content));
        return button;
    }

    private static ToggleButton iconNavButton(Node content, StackPane contentPane) {
        ToggleButton button = navButton("", content, contentPane);
        SVGPath icon = new SVGPath();
        icon.setContent("M19.43,12.98c0.04,-0.32 0.07,-0.65 0.07,-0.98s-0.02,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.12,-0.22 -0.37,-0.31 -0.6,-0.22l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98L14.5,2.42C14.47,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.5,0.42L9.12,5.07c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.23,-0.08 -0.48,0 -0.6,0.22l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65C4.52,11.34 4.5,11.67 4.5,12s0.02,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.12,0.22 0.37,0.31 0.6,0.22l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.04,0.24 0.25,0.42 0.5,0.42h4c0.25,0 0.46,-0.18 0.5,-0.42l0.38,-2.65c0.61,-0.25 1.17,-0.58 1.69,-0.98l2.49,1c0.23,0.08 0.48,0 0.6,-0.22l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65zM12,15.5A3.5,3.5 0,1 1,12,8a3.5,3.5 0,0 1,0,7.5z");
        icon.getStyleClass().add("nav-icon");
        button.setGraphic(icon);
        button.setAccessibleText("Settings");
        button.getStyleClass().add("nav-icon-button");
        return button;
    }

    private void installDesktopTrayIcon(Stage stage, LogConsole logConsole) {
        if (!SystemTray.isSupported()) {
            logConsole.log("[UI] Desktop system tray is not supported by this environment.");
            return;
        }

        PopupMenu menu = new PopupMenu();
        MenuItem showItem = new MenuItem("Show");
        showItem.addActionListener(event -> Platform.runLater(() -> showStage(stage)));
        MenuItem hideItem = new MenuItem("Hide");
        hideItem.addActionListener(event -> Platform.runLater(() -> hideStage(stage)));
        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(event -> Platform.runLater(this::quitFromTray));
        menu.add(showItem);
        menu.add(hideItem);
        menu.addSeparator();
        menu.add(quitItem);

        desktopTrayIcon = new TrayIcon(createTrayImage(), "Java Network FS", menu);
        desktopTrayIcon.setImageAutoSize(false);
        desktopTrayIcon.addActionListener(event -> Platform.runLater(() -> showStage(stage)));

        try {
            SystemTray.getSystemTray().add(desktopTrayIcon);
        } catch (AWTException | SecurityException ex) {
            desktopTrayIcon = null;
            logConsole.log("[UI] Failed to install desktop tray icon: " + describe(ex));
        }
    }

    private void removeDesktopTrayIcon() {
        if (desktopTrayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(desktopTrayIcon);
            desktopTrayIcon = null;
        }
    }

    private void quitFromTray() {
        quitApplication();
    }

    private void quitApplication() {
        saveAllSettings();
        removeDesktopTrayIcon();
        Platform.exit();
        System.exit(0);
    }

    private void saveAllSettings() {
        if (hostTab != null) {
            hostTab.saveSettings();
        }
        if (mountTab != null) {
            mountTab.saveSettings();
        }
        if (settingsTab != null) {
            settingsTab.saveSettings();
        }
    }

    private void hideStage(Stage stage) {
        saveStageBounds(stage);
        stage.hide();
    }

    private void showStage(Stage stage) {
        restoreStageBounds(stage);
        stage.show();
        stage.setIconified(false);
        stage.toFront();
    }

    private void saveStageBounds(Stage stage) {
        if (stage.isIconified()) {
            return;
        }
        double width = stage.getWidth();
        double height = stage.getHeight();
        if (width < MIN_RESTORED_STAGE_WIDTH || height < MIN_RESTORED_STAGE_HEIGHT) {
            return;
        }
        lastStageX = stage.getX();
        lastStageY = stage.getY();
        lastStageWidth = width;
        lastStageHeight = height;
        hasSavedStageBounds = true;
    }

    private void restoreStageBounds(Stage stage) {
        if (!hasSavedStageBounds) {
            stage.setWidth(DEFAULT_STAGE_WIDTH);
            stage.setHeight(DEFAULT_STAGE_HEIGHT);
            return;
        }
        stage.setX(lastStageX);
        stage.setY(lastStageY);
        stage.setWidth(Math.max(MIN_RESTORED_STAGE_WIDTH, lastStageWidth));
        stage.setHeight(Math.max(MIN_RESTORED_STAGE_HEIGHT, lastStageHeight));
    }

    private static BufferedImage createTrayImage() {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(233, 84, 32));
        graphics.fillRect(0, 0, 24, 24);
        graphics.setColor(new Color(233, 84, 32));
        int squareSize = 8;
        int squareOffset = (24 - squareSize) / 2;
        graphics.fillRect(squareOffset, squareOffset, squareSize, squareSize);
        graphics.dispose();
        return image;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
