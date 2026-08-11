package netfs.ui;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;

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
import javafx.scene.image.Image;
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
    private static final int SINGLE_INSTANCE_PORT = 37683;
    private static final byte[] SHOW_COMMAND = "SHOW\n".getBytes(StandardCharsets.UTF_8);

    private static ServerSocket singleInstanceSocket;
    private static FileChannel singleInstanceLockChannel;
    private static FileLock singleInstanceLock;
    private static volatile NetFSApp activeApp;
    private static volatile boolean pendingShowRequest;

    private AppIndicatorTray desktopTrayIcon;
    private LogConsole logConsole;
    private HostTab hostTab;
    private MountTab mountTab;
    private StatsTab statsTab;
    private SettingsTab settingsTab;
    private double lastStageX;
    private double lastStageY;
    private double lastStageWidth;
    private double lastStageHeight;
    private boolean hasSavedStageBounds;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        activeApp = this;
        primaryStage = stage;
        logConsole = new LogConsole();

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

        StackPane contentPane = new StackPane(hostTab.getContent());
        contentPane.getStyleClass().add("content-pane");
        ToggleButton hostButton = navButton("Host", hostTab::getContent, contentPane, this::hideStatsTab);
        ToggleButton mountButton = navButton("Mount", () -> getMountTab().getContent(), contentPane, this::hideStatsTab);
        ToggleButton statsButton = navButton("Stats", () -> getStatsTab().getContent(), contentPane,
                () -> getStatsTab().onShown());
        ToggleButton consoleButton = navButton("Console", logConsole::getContent, contentPane, this::hideStatsTab);
        ToggleButton settingsButton = iconNavButton(settingsTab::getContent, contentPane, this::hideStatsTab);

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

        installLocalAppIcon();
        installLocalDesktopEntry();
        stage.setTitle("NetFS");
        loadWindowIcon("/assets/icons/netfs-24.png").ifPresent(stage.getIcons()::add);
        loadWindowIcon("/assets/icons/netfs-256.png").ifPresent(stage.getIcons()::add);
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
        showStage(stage);
        installDesktopTrayIcon(stage, logConsole);
        if (settingsTab.isStartMinimizedEnabled()) {
            hideStage(stage);
        }
        if (settingsTab.isHostStartOnLaunchEnabled()) {
            Platform.runLater(hostTab::startServerOnLaunch);
        }
        if (pendingShowRequest) {
            pendingShowRequest = false;
            Platform.runLater(() -> showStage(stage));
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }

    private static java.util.Optional<Image> loadWindowIcon(String resourcePath) {
        InputStream stream = NetFSApp.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            return java.util.Optional.empty();
        }
        try (stream) {
            return java.util.Optional.of(new Image(stream));
        } catch (Exception ex) {
            System.err.println("[UI] Failed to load window icon: " + describe(ex));
            return java.util.Optional.empty();
        }
    }

    private static void installLocalAppIcon() {
        installLocalIconResource("/assets/icons/netfs-24.png", "24x24", "netfs.png");
        installLocalIconResource("/assets/icons/netfs-256.png", "256x256", "netfs.png");
        installLocalIconResource("/assets/icons/netfs.svg", "scalable", "netfs.svg");
    }

    private static void installLocalIconResource(String resourcePath, String sizeDirectory, String fileName) {
        try (InputStream stream = NetFSApp.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return;
            }
            Path iconFile = Path.of(
                    System.getProperty("user.home"),
                    ".local",
                    "share",
                    "icons",
                    "hicolor",
                    sizeDirectory,
                    "apps",
                    fileName);
            Files.createDirectories(iconFile.getParent());
            Files.copy(stream, iconFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            System.err.println("[UI] Failed to install app icon " + resourcePath + ": " + describe(ex));
        }
    }

    private static void installLocalDesktopEntry() {
        try {
            Path desktopFile = Path.of(
                    System.getProperty("user.home"),
                    ".local",
                    "share",
                    "applications",
                    "netfs.desktop");
            Files.createDirectories(desktopFile.getParent());
            Files.writeString(desktopFile, desktopEntry(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            System.err.println("[UI] Failed to install desktop entry: " + describe(ex));
        }
    }

    private static String desktopEntry() {
        return String.join("\n",
                "[Desktop Entry]",
                "Type=Application",
                "Name=NetFS",
                "Comment=Java Network FS",
                "Exec=" + desktopExecCommand(),
                "Icon=netfs",
                "Terminal=false",
                "StartupWMClass=netfs.ui.NetFSApp",
                "Categories=Utility;",
                "");
    }

    private static String desktopExecCommand() {
        if (Boolean.getBoolean("netfs.installed")
                || System.getProperty("java.class.path", "").contains("/usr/share/netfs/app")) {
            return "netfs-ui";
        }
        Path runScript = Path.of(System.getProperty("user.dir"), "run-ui.sh").toAbsolutePath().normalize();
        if (Files.isRegularFile(runScript)) {
            return runScript.toString();
        }
        return "netfs-ui";
    }

    private MountTab getMountTab() {
        if (mountTab == null) {
            mountTab = new MountTab(logConsole, settingsTab);
        }
        return mountTab;
    }

    private StatsTab getStatsTab() {
        if (statsTab == null) {
            statsTab = new StatsTab();
        }
        return statsTab;
    }

    private void hideStatsTab() {
        if (statsTab != null) {
            statsTab.onHidden();
        }
    }

    private static ToggleButton navButton(
            String text,
            Supplier<Node> contentSupplier,
            StackPane contentPane,
            Runnable onSelected) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("nav-button");
        button.setOnAction(event -> {
            contentPane.getChildren().setAll(contentSupplier.get());
            onSelected.run();
        });
        return button;
    }

    private static ToggleButton iconNavButton(
            Supplier<Node> contentSupplier,
            StackPane contentPane,
            Runnable onSelected) {
        ToggleButton button = navButton("", contentSupplier, contentPane, onSelected);
        SVGPath icon = new SVGPath();
        icon.setContent("M19.43,12.98c0.04,-0.32 0.07,-0.65 0.07,-0.98s-0.02,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.12,-0.22 -0.37,-0.31 -0.6,-0.22l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98L14.5,2.42C14.47,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.5,0.42L9.12,5.07c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.23,-0.08 -0.48,0 -0.6,0.22l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65C4.52,11.34 4.5,11.67 4.5,12s0.02,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.12,0.22 0.37,0.31 0.6,0.22l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.04,0.24 0.25,0.42 0.5,0.42h4c0.25,0 0.46,-0.18 0.5,-0.42l0.38,-2.65c0.61,-0.25 1.17,-0.58 1.69,-0.98l2.49,1c0.23,0.08 0.48,0 0.6,-0.22l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65zM12,15.5A3.5,3.5 0,1 1,12,8a3.5,3.5 0,0 1,0,7.5z");
        icon.getStyleClass().add("nav-icon");
        button.setGraphic(icon);
        button.setAccessibleText("Settings");
        button.getStyleClass().add("nav-icon-button");
        return button;
    }

    private void installDesktopTrayIcon(Stage stage, LogConsole logConsole) {
        desktopTrayIcon = AppIndicatorTray.install(
                () -> showStage(stage),
                () -> hideStage(stage),
                this::quitFromTray,
                message -> logConsole.log("[UI] Failed to install desktop tray icon: " + message));
    }

    private void removeDesktopTrayIcon() {
        if (desktopTrayIcon != null) {
            desktopTrayIcon.close();
            desktopTrayIcon = null;
        }
    }

    private void quitFromTray() {
        quitApplication();
    }

    private void quitApplication() {
        saveAllSettings();
        removeDesktopTrayIcon();
        closeSingleInstanceSocket();
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
        stage.setIconified(false);
        stage.show();
        bringStageToFront(stage);
    }

    private void showExistingWindow() {
        if (primaryStage == null) {
            pendingShowRequest = true;
            return;
        }
        showStage(primaryStage);
    }

    private static void bringStageToFront(Stage stage) {
        Platform.runLater(() -> {
            stage.setAlwaysOnTop(true);
            stage.toFront();
            stage.requestFocus();
            Platform.runLater(() -> stage.setAlwaysOnTop(false));
        });
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

    @Override
    public void stop() {
        closeSingleInstanceSocket();
    }

    static boolean claimSingleInstance() {
        if (!claimSingleInstanceLock()) {
            sendShowRequestToExistingInstance();
            return false;
        }
        try {
            ServerSocket serverSocket = new ServerSocket(SINGLE_INSTANCE_PORT, 1, singleInstanceAddress());
            singleInstanceSocket = serverSocket;
            Thread listener = new Thread(() -> listenForShowRequests(serverSocket), "netfs-single-instance");
            listener.setDaemon(true);
            listener.start();
            return true;
        } catch (Exception bindFailure) {
            if (sendShowRequestToExistingInstance()) {
                return false;
            }
            System.err.println("[UI] Single-instance control unavailable: " + describe(bindFailure));
            return true;
        }
    }

    private static boolean claimSingleInstanceLock() {
        try {
            Path lockFile = Path.of(
                    System.getProperty("user.home"),
                    ".cache",
                    "netfs",
                    "netfs.lock");
            Files.createDirectories(lockFile.getParent());
            singleInstanceLockChannel = FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            singleInstanceLock = singleInstanceLockChannel.tryLock();
            if (singleInstanceLock == null) {
                closeSingleInstanceLock();
                return false;
            }
            return true;
        } catch (Exception ex) {
            System.err.println("[UI] Single-instance lock unavailable: " + describe(ex));
            closeSingleInstanceLock();
            return true;
        }
    }

    private static void listenForShowRequests(ServerSocket serverSocket) {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(1000);
                byte[] buffer = socket.getInputStream().readNBytes(SHOW_COMMAND.length);
                if (java.util.Arrays.equals(buffer, SHOW_COMMAND)) {
                    requestShowExistingWindow();
                }
            } catch (SocketException ex) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[UI] Single-instance listener stopped: " + describe(ex));
                }
                return;
            } catch (Exception ex) {
                System.err.println("[UI] Single-instance request failed: " + describe(ex));
            }
        }
    }

    private static void requestShowExistingWindow() {
        NetFSApp app = activeApp;
        if (app == null) {
            pendingShowRequest = true;
            return;
        }
        Platform.runLater(app::showExistingWindow);
    }

    private static boolean sendShowRequestToExistingInstance() {
        try (Socket socket = new Socket(singleInstanceAddress(), SINGLE_INSTANCE_PORT)) {
            OutputStream output = socket.getOutputStream();
            output.write(SHOW_COMMAND);
            output.flush();
            return true;
        } catch (Exception ex) {
            System.err.println("[UI] Existing-instance signal failed: " + describe(ex));
            return false;
        }
    }

    private static void closeSingleInstanceSocket() {
        ServerSocket serverSocket = singleInstanceSocket;
        singleInstanceSocket = null;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
                // The process is exiting; closing the control socket is best-effort.
            }
        }
        closeSingleInstanceLock();
    }

    private static void closeSingleInstanceLock() {
        FileLock lock = singleInstanceLock;
        singleInstanceLock = null;
        if (lock != null && lock.isValid()) {
            try {
                lock.release();
            } catch (Exception ignored) {
                // The process is exiting; releasing the lock is best-effort.
            }
        }
        FileChannel channel = singleInstanceLockChannel;
        singleInstanceLockChannel = null;
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (Exception ignored) {
                // The process is exiting; closing the lock channel is best-effort.
            }
        }
    }

    private static InetAddress singleInstanceAddress() throws java.net.UnknownHostException {
        return InetAddress.getByName("127.0.0.1");
    }

    public static void main(String[] args) {
        NetFSLauncher.main(args);
    }
}
