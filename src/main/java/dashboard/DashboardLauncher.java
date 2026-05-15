package dashboard;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.CountDownLatch;

/**
 * Bootstraps the JavaFX runtime from the CLI process and shows the post-run
 * dashboard. {@link #show()} blocks until the user closes the window.
 *
 * <p>Currently a placeholder Stage so the pom dependency wiring can be smoke
 * tested. The real dashboard scene graph is wired up in subsequent steps.
 */
public final class DashboardLauncher {

    private DashboardLauncher() {}

    public static void show() {
        if (GraphicsEnvironment.isHeadless()) return;

        Platform.setImplicitExit(false);
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
            // FX runtime already up — fine, fall through.
        }

        CountDownLatch closed = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setTitle("Post-Run Dashboard");
            stage.setScene(new Scene(Dashboard.build(), 1280, 800));
            stage.setOnHidden(e -> closed.countDown());
            stage.show();
        });

        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
