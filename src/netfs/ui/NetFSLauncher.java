package netfs.ui;

import javafx.application.Application;

public final class NetFSLauncher {

    private NetFSLauncher() {
    }

    public static void main(String[] args) {
        if (!NetFSApp.claimSingleInstance()) {
            return;
        }
        Application.launch(NetFSApp.class, args);
    }
}
