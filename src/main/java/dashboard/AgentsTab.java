package dashboard;

import agent.Agent;
import agent.Snapshot;
import agent.Trade;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import session.Session;
import trade.Exchange;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent inspector — replaces the legacy Scanner-driven {@code runAgentChartLoop}.
 *
 * <p>Layout: agent list on the left; on the right, a vertical split with two
 * stacked charts on top (portfolio value vs price overlay, then a stacked
 * cash/shares-value area showing how the portfolio is composed each round)
 * and the agent's trade history table at the bottom.
 */
final class AgentsTab {

    private AgentsTab() {}

    static Node build() {
        ObservableList<Agent> agents = FXCollections.observableArrayList(loadAgents());

        ListView<Agent> list = new ListView<>(agents);
        list.setCellFactory(v -> new AgentCell());
        list.setPrefWidth(260);

        // ----- Top chart: portfolio value (left axis) + price (right axis) -----
        XYSeries portfolioSeries = new XYSeries("Portfolio");
        XYSeriesCollection portfolioDs = new XYSeriesCollection(portfolioSeries);
        XYSeries priceSeries = new XYSeries("Price");
        XYSeriesCollection priceDs = new XYSeriesCollection(priceSeries);

        NumberAxis portfolioAxis = new NumberAxis("Portfolio (£)");
        portfolioAxis.setAutoRangeIncludesZero(false);
        NumberAxis priceAxis = new NumberAxis("Price (£)");
        priceAxis.setAutoRangeIncludesZero(false);

        XYLineAndShapeRenderer portfolioRenderer = new XYLineAndShapeRenderer(true, false);
        portfolioRenderer.setSeriesPaint(0, new Color(40, 90, 200));
        portfolioRenderer.setSeriesStroke(0, new BasicStroke(1.8f));

        XYLineAndShapeRenderer priceRenderer = new XYLineAndShapeRenderer(true, false);
        priceRenderer.setSeriesPaint(0, new Color(150, 150, 150));
        priceRenderer.setSeriesStroke(0, new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));

        XYPlot topPlot = new XYPlot(portfolioDs, new NumberAxis("Round"), portfolioAxis, portfolioRenderer);
        topPlot.setDataset(1, priceDs);
        topPlot.setRangeAxis(1, priceAxis);
        topPlot.mapDatasetToRangeAxis(1, 1);
        topPlot.setRenderer(1, priceRenderer);
        styleSubPlot(topPlot);
        JFreeChart topChart = new JFreeChart("Select an agent", JFreeChart.DEFAULT_TITLE_FONT, topPlot, true);
        topChart.setBackgroundPaint(Color.WHITE);
        ChartViewer topViewer = new ChartViewer(topChart);

        // ----- Bottom chart: stacked area of cash + shares-value (£) -----
        // DefaultTableXYDataset auto-pads X across member series, so the dataset is
        // rebuilt from scratch on each agent selection rather than mutating two
        // shared XYSeries (which trips a duplicate-X exception).
        StackedXYAreaRenderer2 stackRenderer = new StackedXYAreaRenderer2();
        stackRenderer.setSeriesPaint(0, new Color(140, 190, 230, 200)); // available cash – light blue
        stackRenderer.setSeriesPaint(1, new Color(80,  130, 190, 200)); // cash in bids – mid blue
        stackRenderer.setSeriesPaint(2, new Color(230, 160,  90, 200)); // available shares – orange
        stackRenderer.setSeriesPaint(3, new Color(180,  90,  40, 200)); // shares in asks – dark orange
        NumberAxis compAxis = new NumberAxis("£");
        compAxis.setAutoRangeIncludesZero(true);
        XYPlot bottomPlot = new XYPlot(new DefaultTableXYDataset(), new NumberAxis("Round"), compAxis, stackRenderer);
        styleSubPlot(bottomPlot);
        JFreeChart bottomChart = new JFreeChart("Portfolio composition", JFreeChart.DEFAULT_TITLE_FONT, bottomPlot, true);
        bottomChart.setBackgroundPaint(Color.WHITE);
        ChartViewer bottomViewer = new ChartViewer(bottomChart);

        VBox charts = new VBox(topViewer, bottomViewer);
        VBox.setVgrow(topViewer, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(bottomViewer, javafx.scene.layout.Priority.ALWAYS);

        // ----- Trade history table -----
        TableView<Trade> tradeTable = buildTradeTable();

        Label summary = new Label("Pick an agent on the left to see their portfolio history.");
        summary.setFont(Font.font("SansSerif", 12));
        summary.setPadding(new Insets(6, 12, 6, 12));
        summary.setWrapText(true);

        BorderPane chartsPane = new BorderPane(charts);
        chartsPane.setBottom(summary);

        SplitPane rightSplit = new SplitPane(chartsPane, tradeTable);
        rightSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rightSplit.setDividerPositions(0.72);

        // ----- Selection wiring -----
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, agent) -> {
            if (agent == null) return;
            populate(agent, topPlot, portfolioSeries, priceSeries, bottomPlot, tradeTable);
            String title = agent.getName() + "  (id " + agent.getId() + ", strategy " + agent.getStrategy() + ")";
            topChart.setTitle(title);
            summary.setText(buildSummary(agent));
        });

        if (!agents.isEmpty()) list.getSelectionModel().select(0);

        SplitPane outer = new SplitPane(list, rightSplit);
        outer.setDividerPositions(0.20);
        return outer;
    }

    private static void populate(Agent agent,
                                 XYPlot topPlot,
                                 XYSeries portfolio, XYSeries price,
                                 XYPlot compositionPlot,
                                 TableView<Trade> tradeTable) {
        portfolio.clear();
        price.clear();

        List<Snapshot> snaps = agent.getSnapshots();
        List<Float> roundPrice = Exchange.getRoundFinalPrice();

        portfolio.setKey(agent.getName());

        // Build the four composition series in isolation (not yet attached to a
        // TableXYDataset), then swap in a fresh DefaultTableXYDataset. This avoids
        // the dataset's auto-X-padding which otherwise injects duplicate X values.
        XYSeries cashSeries         = new XYSeries("Available cash",   true, false);
        XYSeries cashInBidsSeries   = new XYSeries("Cash in bids",     true, false);
        XYSeries sharesAvailSeries  = new XYSeries("Available shares", true, false);
        XYSeries sharesInAsksSeries = new XYSeries("Shares in asks",   true, false);

        // X axis = the actual round the snapshot was taken in (Snapshot.round). This
        // keeps late-joining agents (spawned by mutate()) starting at their join round
        // and prevents gaps for strategies that don't fire every round.
        for (Snapshot s : snaps) {
            portfolio.add(s.round(), s.fund());
            cashSeries.add(s.round(), s.cash());
            cashInBidsSeries.add(s.round(), s.cashInBids());
            float availShares = Math.max(0f, s.fund() - s.cash() - s.cashInBids() - s.sharesInAsksValue());
            sharesAvailSeries.add(s.round(), availShares);
            sharesInAsksSeries.add(s.round(), s.sharesInAsksValue());
        }
        // Price overlay aligned to the same round axis: roundFinalPrice[i] == round i+1's close.
        for (int i = 0; i < roundPrice.size(); i++) {
            price.add(i + 1, roundPrice.get(i));
        }

        DefaultTableXYDataset compDs = new DefaultTableXYDataset();
        compDs.addSeries(cashSeries);
        compDs.addSeries(cashInBidsSeries);
        compDs.addSeries(sharesAvailSeries);
        compDs.addSeries(sharesInAsksSeries);
        compositionPlot.setDataset(compDs);

        tradeTable.setItems(FXCollections.observableArrayList(agent.getTradeHistory()));

        // Reset zoom each time the user picks a new agent — JFreeChart leaves the
        // previous explicit axis bounds in place after a box-zoom, otherwise.
        topPlot.getDomainAxis().setAutoRange(true);
        topPlot.getRangeAxis(0).setAutoRange(true);
        topPlot.getRangeAxis(1).setAutoRange(true);
        compositionPlot.getDomainAxis().setAutoRange(true);
        compositionPlot.getRangeAxis().setAutoRange(true);
    }

    @SuppressWarnings("unchecked")
    private static TableView<Trade> buildTradeTable() {
        TableView<Trade> table = new TableView<>();
        table.setPlaceholder(new Label("No trades."));

        // PropertyValueFactory needs JavaBean-style getXxx accessors; Trade is a Java
        // record, whose accessors are bare round()/side()/etc., so wire each column
        // with an explicit lambda instead.
        TableColumn<Trade, Number> roundCol = new TableColumn<>("Round");
        roundCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().round()));
        roundCol.setPrefWidth(70);

        TableColumn<Trade, String> sideCol = new TableColumn<>("Side");
        sideCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().side().name()));
        sideCol.setPrefWidth(60);

        TableColumn<Trade, Number> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().amount()));
        amountCol.setPrefWidth(80);

        TableColumn<Trade, String> priceCol = new TableColumn<>("Price (£)");
        priceCol.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(String.format("%,.2f", c.getValue().price())));
        priceCol.setPrefWidth(90);

        TableColumn<Trade, String> totalCol = new TableColumn<>("Total (£)");
        totalCol.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(String.format("%,.2f", c.getValue().total())));
        totalCol.setPrefWidth(110);

        TableColumn<Trade, String> counterCol = new TableColumn<>("Counterparty");
        counterCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().counterpartyName()));
        counterCol.setPrefWidth(220);

        table.getColumns().addAll(roundCol, sideCol, amountCol, priceCol, totalCol, counterCol);
        return table;
    }

    private static List<Agent> loadAgents() {
        return Session.getAgents().values().stream()
                .filter(a -> a.getId() != 1) // exclude company
                .sorted(Comparator.comparingInt(Agent::getId))
                .collect(Collectors.toList());
    }

    private static String buildSummary(Agent agent) {
        float starting = agent.getStartingFunds();
        float ending = agent.getFunds();
        if (!agent.getGoodsOwned().isEmpty()) {
            ending += agent.getGoodsOwned().get(0).getNumOwned() * Exchange.lastPrice;
        }
        float pct = starting > 0f ? (ending / starting - 1f) * 100f : 0f;
        return String.format(
                "Strategy: %d   |   Starting: £%,.2f   |   Final: £%,.2f   |   Change: %+.2f%%   |   Trades: %d",
                agent.getStrategy(), starting, ending, pct, agent.getTradeHistory().size());
    }

    private static void styleSubPlot(XYPlot plot) {
        plot.setBackgroundPaint(new Color(248, 248, 252));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
    }

    private static final class AgentCell extends ListCell<Agent> {
        @Override
        protected void updateItem(Agent agent, boolean empty) {
            super.updateItem(agent, empty);
            if (empty || agent == null) {
                setText(null);
                return;
            }
            float starting = agent.getStartingFunds();
            float ending = agent.getFunds();
            if (!agent.getGoodsOwned().isEmpty()) {
                ending += agent.getGoodsOwned().get(0).getNumOwned() * Exchange.lastPrice;
            }
            float pct = starting > 0f ? (ending / starting - 1f) * 100f : 0f;
            setText(String.format("%-3d  %-22s  %+6.1f%%", agent.getId(), agent.getName(), pct));
            setFont(Font.font("Monospaced", 11));
        }
    }
}
