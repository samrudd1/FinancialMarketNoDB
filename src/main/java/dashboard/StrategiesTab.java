package dashboard;

import agent.Agent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import session.Session;
import trade.Exchange;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-strategy summary tab: a bar chart of trade counts on top, mean
 * percentage P/L (mark-to-market on close) on the bottom. Both share strategy
 * names on the X axis so the same column position lines up vertically.
 */
final class StrategiesTab {

    private StrategiesTab() {}

    static Node build() {
        Map<Integer, String> names = strategyNames();

        ChartViewer counts = new ChartViewer(buildCountsChart(names));
        ChartViewer pnl = new ChartViewer(buildPnlChart(names));

        SplitPane split = new SplitPane(counts, pnl);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.5);
        split.setPadding(new Insets(4));
        return split;
    }

    private static JFreeChart buildCountsChart(Map<Integer, String> names) {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        ds.addValue(Exchange.getDefaultCount(), "Trades", names.get(0));
        ds.addValue(Exchange.getSentCount(),    "Trades", names.get(2));
        ds.addValue(Exchange.getOfferCount(),      "Trades", names.get(3));
        ds.addValue(Exchange.getRsiCount(),        "Trades", names.get(4));
        ds.addValue(Exchange.getRsi10Count(),      "Trades", names.get(5));
        ds.addValue(Exchange.getBothCount(),       "Trades", names.get(6));
        if (Agent.isVolatility()) {
            ds.addValue(Exchange.getVwapCount(),    "Trades", names.get(7));
            ds.addValue(Exchange.getMomCount(),     "Trades", names.get(8));
            ds.addValue(Exchange.getVwapMRCount(), "Trades", names.get(9));
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Trades executed per strategy",
                "Strategy", "Trades", ds,
                PlotOrientation.VERTICAL, false, true, false);
        styleBarChart(chart, new Color(40, 90, 200));

        return chart;
    }

    private static JFreeChart buildPnlChart(Map<Integer, String> names) {
        Map<Integer, double[]> agg = new LinkedHashMap<>(); // id → [sumPct, count]
        for (Map.Entry<Integer, String> e : names.entrySet()) agg.put(e.getKey(), new double[]{0, 0});

        for (Agent agent : Session.getAgents().values()) {
            if (agent.getId() == 1) continue;
            float starting = agent.getStartingFunds();
            if (starting <= 0) continue;
            float ending = agent.getFunds();
            if (!agent.getGoodsOwned().isEmpty()) {
                ending += agent.getGoodsOwned().get(0).getNumOwned() * Exchange.lastPrice;
            }
            float pct = (ending / starting - 1f) * 100f;
            double[] bucket = agg.get(agent.getStrategy());
            if (bucket == null) continue;
            bucket[0] += pct;
            bucket[1] += 1;
        }

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (Map.Entry<Integer, double[]> e : agg.entrySet()) {
            double[] v = e.getValue();
            double mean = v[1] > 0 ? v[0] / v[1] : 0.0;
            ds.addValue(mean, "Mean P/L %", names.get(e.getKey()));
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Mean P/L per strategy (mark-to-market on close)",
                "Strategy", "P/L %", ds,
                PlotOrientation.VERTICAL, false, true, false);
        styleBarChart(chart, new Color(50, 160, 80));
        return chart;
    }

    private static void styleBarChart(JFreeChart chart, Color barColour) {
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(248, 248, 252));
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        ((NumberAxis) plot.getRangeAxis()).setAutoRangeIncludesZero(true);
        CategoryAxis x = plot.getDomainAxis();
        x.setCategoryLabelPositions(org.jfree.chart.axis.CategoryLabelPositions.UP_45);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, barColour);
    }

    private static Map<Integer, String> strategyNames() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0, "Default");
        m.put(2, "Sentiment");
        m.put(3, "Offer only");
        m.put(4, "RSI 14");
        m.put(5, "RSI 10");
        m.put(6, "Both RSI");
        if (Agent.isVolatility()) {
            m.put(7, "VWAP");
            m.put(8, "Momentum");
            m.put(9, "VWAP MR");
        }
        return m;
    }
}
