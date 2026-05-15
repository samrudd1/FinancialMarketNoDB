package dashboard;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import org.controlsfx.control.StatusBar;
import session.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Assembles the post-run dashboard scene graph: summary cards on top, a
 * three-tab pane in the middle (Overview / Agents / Strategies), and a
 * ControlsFX {@link StatusBar} at the bottom. The first tab is built eagerly
 * so the user sees content immediately; the others build on first selection.
 */
final class Dashboard {

    private Dashboard() {}

    static BorderPane build() {
        BorderPane root = new BorderPane();
        root.setTop(SummaryCards.build());

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                eagerTab("Overview", OverviewTab.build()),
                lazyTab("Agents", AgentsTab::build),
                lazyTab("Strategies", StrategiesTab::build));
        root.setCenter(tabs);

        StatusBar bar = new StatusBar();
        bar.setText(String.format(
                "Run completed %s   |   %d rounds   |   %d agents",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                Session.getNumOfRounds(),
                Session.getNumAgents()));
        root.setBottom(bar);

        return root;
    }

    private static Tab eagerTab(String title, Node content) {
        Tab tab = new Tab(title, content);
        return tab;
    }

    private static Tab lazyTab(String title, Supplier<Node> factory) {
        Tab tab = new Tab(title);
        tab.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow && tab.getContent() == null) tab.setContent(factory.get());
        });
        return tab;
    }
}
