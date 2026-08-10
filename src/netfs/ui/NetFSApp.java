package netfs.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Entry point for the Java Network FS UI. Lets the user start the app in either
 * "Host" mode (run a {@link netfs.net.FileSystemServer}) or "Mount" mode
 * (run a {@link netfs.net.FileSystemClient} that mounts a remote share via FUSE).
 */
public class NetFSApp extends Application {

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

        HostTab hostTab = new HostTab(logConsole);
        MountTab mountTab = new MountTab(logConsole);
        StatsTab statsTab = new StatsTab();

        Tab hostFxTab = new Tab("Host", hostTab.getContent());
        hostFxTab.setClosable(false);

        Tab mountFxTab = new Tab("Mount", mountTab.getContent());
        mountFxTab.setClosable(false);

        Tab statsFxTab = new Tab("Stats", statsTab.getContent());
        statsFxTab.setClosable(false);

        TabPane tabPane = new TabPane(hostFxTab, mountFxTab, statsFxTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Label title = new Label("Java Network FS");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("FUSE-backed network filesystem");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        HBox header = new HBox(titleBox);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 24, 18, 24));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(header);
        root.setCenter(tabPane);
        root.setBottom(logConsole.getContent());

        Scene scene = new Scene(root, 780, 660);
        scene.getStylesheets().add(NetFSApp.class.getResource("style.css").toExternalForm());

        stage.setTitle("Java Network FS");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            hostTab.saveSettings();
            mountTab.saveSettings();
            System.exit(0);
        });
        stage.show();
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

