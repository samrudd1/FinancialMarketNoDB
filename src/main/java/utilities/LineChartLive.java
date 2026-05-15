package utilities;

import good.Good;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.DefaultHighLowDataset;
import trade.Exchange;
import trade.TradeData;

import javax.swing.*;
import java.awt.*;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.*;

public class LineChartLive extends JFrame {

    private static final int MAX_CANDLE_ROUNDS = 150;

    private final XYPlot candlePlot;
    private volatile boolean refreshPending = false;

    public LineChartLive() {
        JFreeChart chart = buildCandleChart();
        candlePlot = (XYPlot) chart.getPlot();

        setContentPane(new ChartPanel(chart));
        setTitle("Live Market Dashboard");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                Exchange.setLiveActive(false);
                Exchange.setLiveForced(false);
                dispose();
            }
        });
        setSize(1000, 650);
        setLocationRelativeTo(null);
    }

    public void startUpdating() {}

    public void stopUpdating() {}

    public void scheduleRefresh() {
        if (Exchange.isLiveForced()) {
            try {
                SwingUtilities.invokeAndWait(this::refresh);
            } catch (Exception ignored) {}
        } else if (!refreshPending) {
            refreshPending = true;
            SwingUtilities.invokeLater(() -> {
                refreshPending = false;
                refresh();
            });
        }
    }

    private void refresh() {
        updateCandleChart();
    }

    private void updateCandleChart() {
        ArrayList<TradeData> trades = Good.getTradeData();
        int sz = trades.size();
        if (sz == 0) return;

        ArrayList<TradeData> snapshot = new ArrayList<>(sz);
        for (int i = 0; i < sz && i < trades.size(); i++) {
            snapshot.add(trades.get(i));
        }
        if (snapshot.isEmpty()) return;

        TreeMap<Integer, CandleAccum> byRound = CandleAccum.binByRound(snapshot);
        while (byRound.size() > MAX_CANDLE_ROUNDS) {
            byRound.pollFirstEntry();
        }

        int n = byRound.size();
        Date[] dates = new Date[n];
        double[] high = new double[n];
        double[] low = new double[n];
        double[] open = new double[n];
        double[] close = new double[n];
        double[] volume = new double[n];

        int idx = 0;
        for (Map.Entry<Integer, CandleAccum> entry : byRound.entrySet()) {
            CandleAccum c = entry.getValue();
            dates[idx] = new Date((long) entry.getKey() * 86400000L);
            open[idx] = c.open;
            high[idx] = c.high;
            low[idx] = c.low;
            close[idx] = c.close;
            volume[idx] = c.totalVolume;
            idx++;
        }

        candlePlot.setDataset(new DefaultHighLowDataset("Price", dates, high, low, open, close, volume));
    }

    private JFreeChart buildCandleChart() {
        DefaultHighLowDataset empty = new DefaultHighLowDataset("Price",
                new Date[0], new double[0], new double[0], new double[0], new double[0], new double[0]);
        JFreeChart chart = ChartFactory.createCandlestickChart(
                "Live Candlestick (per round)", "Round", "Price (£)", empty, false);
        chart.setBackgroundPaint(Color.WHITE);

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(248, 248, 252));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        ((NumberAxis) plot.getRangeAxis()).setAutoRangeIncludesZero(false);

        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setLabel("Round");
        axis.setDateFormatOverride(new DateFormat() {
            @Override
            public StringBuffer format(Date d, StringBuffer sb, FieldPosition fp) {
                return sb.append(d.getTime() / 86400000L);
            }
            @Override
            public Date parse(String s, ParsePosition p) { return null; }
        });

        CandlestickRenderer r = (CandlestickRenderer) plot.getRenderer();
        r.setDrawVolume(true);
        r.setVolumePaint(new Color(100, 100, 200, 100));
        r.setUpPaint(new Color(50, 180, 50));
        r.setDownPaint(new Color(220, 50, 50));
        r.setUseOutlinePaint(true);
        r.setSeriesOutlinePaint(0, Color.BLACK);
        return chart;
    }

}
