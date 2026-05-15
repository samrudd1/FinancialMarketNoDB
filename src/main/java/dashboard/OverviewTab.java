package dashboard;

import good.Good;
import javafx.scene.Node;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.Range;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import trade.Exchange;
import utilities.CandleAccum;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the headline "Overview" tab: one stacked, synchronised
 * {@link CombinedDomainXYPlot} sharing a round-axis across four subplots —
 * candles + VWAP overlay (with strong-sentiment markers), sentiment heatmap
 * strip, volume bars, and RSI 14 / RSI 10 with their respective threshold
 * bands. Box-zoom on any subplot keeps every panel in lockstep because the
 * domain axis is shared.
 */
final class OverviewTab {

    /** One round encoded as one day so DateAxis can cleanly separate ticks. */
    private static final long MS_PER_ROUND = 86_400_000L;

    private OverviewTab() {}

    static Node build() {
        TreeMap<Integer, CandleAccum> byRound = CandleAccum.binByRound(Good.getTradeData());

        DateAxis sharedX = new DateAxis("Round");
        sharedX.setDateFormatOverride(roundFormat());
        sharedX.setLowerMargin(0.02);
        sharedX.setUpperMargin(0.02);

        // isRangeZoomable() override prevents ChartViewer from calling zoomRangeAxes(),
        // so box-zoom only moves the shared X axis. The domain-axis listener below
        // (added after the price plot is built) then re-configures the price Y axis
        // so it auto-fits to the visible window rather than the full dataset.
        CombinedDomainXYPlot combined = new CombinedDomainXYPlot(sharedX) {
            @Override public boolean isRangeZoomable() { return false; }
        };
        combined.setOrientation(PlotOrientation.VERTICAL);
        combined.setGap(8.0);

        XYPlot pricePlot = buildPricePlot(byRound, sharedX);
        combined.add(pricePlot, 16);
        combined.add(buildSentimentStrip(), 1);
        combined.add(buildRsiPlot(), 4);

        // When sharedX changes (box-zoom), kick the price Y axis to re-fit to visible data.
        sharedX.addChangeListener(e -> pricePlot.getRangeAxis().configure());

        JFreeChart chart = new JFreeChart("Run Overview", JFreeChart.DEFAULT_TITLE_FONT, combined, false);
        chart.setBackgroundPaint(Color.WHITE);

        ChartViewer viewer = new ChartViewer(chart);
        // Once the JavaFX scene has laid out, force the shared X axis back to auto-range —
        // ChartViewer's first layout pass can leave the axis on the partial range it
        // computed before the data area has its real size, which presents as "starts zoomed in".
        javafx.application.Platform.runLater(() -> {
            sharedX.setAutoRange(true);
            pricePlot.getRangeAxis().setAutoRange(true);
        });
        return viewer;
    }

    // ----- Subplot 1: candles + VWAP overlay + strong-sentiment markers -----
    private static XYPlot buildPricePlot(TreeMap<Integer, CandleAccum> byRound, DateAxis sharedX) {
        DefaultHighLowDataset ohlc = toOhlcDataset(byRound);

        NumberAxis priceAxis = new NumberAxis("Price (£)");
        priceAxis.setAutoRange(true);
        priceAxis.setAutoRangeIncludesZero(false);
        priceAxis.setAutoRangeStickyZero(false); // allows min to float, not just max

        CandlestickRenderer candles = new CandlestickRenderer();
        candles.setUpPaint(new Color(50, 180, 50));
        candles.setDownPaint(new Color(220, 50, 50));
        candles.setUseOutlinePaint(false);       // no border around candle body
        candles.setSeriesPaint(0, Color.BLACK);  // getItemPaint() → wick colour
        candles.setAutoWidthFactor(0.9);         // fill 90% of per-round space; scales with X zoom
        // Default cap is 20h, but each round occupies 24h on our DateAxis — without
        // raising this, candles plateau at ~83% of slot width even fully zoomed in.
        candles.setMaxCandleWidthInMilliseconds(MS_PER_ROUND);
        candles.setDrawVolume(true);

        // Auto-range on a JFreeChart axis fits the full dataset, not the visible domain
        // window. Override getDataRange so Y re-fits to candles + VWAP currently inside
        // the domain axis range — this is what gives X-only box-zoom a "Y follows" feel.
        XYPlot plot = new XYPlot(ohlc, null, priceAxis, candles) {
            @Override
            public Range getDataRange(ValueAxis axis) {
                if (axis != getRangeAxis()) return super.getDataRange(axis);
                // Subplot.getDomainAxis() is null inside a CombinedDomainXYPlot — use shared.
                Range domain = sharedX.getRange();
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                XYDataset ds0 = getDataset(0);
                if (ds0 instanceof OHLCDataset ohlcDs) {
                    for (int s = 0; s < ohlcDs.getSeriesCount(); s++) {
                        for (int i = 0; i < ohlcDs.getItemCount(s); i++) {
                            double x = ohlcDs.getXValue(s, i);
                            if (!domain.contains(x)) continue;
                            double h = ohlcDs.getHighValue(s, i);
                            double l = ohlcDs.getLowValue(s, i);
                            if (h > max) max = h;
                            if (l < min) min = l;
                        }
                    }
                }
                XYDataset ds1 = getDataset(1);
                if (ds1 != null) {
                    for (int s = 0; s < ds1.getSeriesCount(); s++) {
                        for (int i = 0; i < ds1.getItemCount(s); i++) {
                            double x = ds1.getXValue(s, i);
                            if (!domain.contains(x)) continue;
                            double y = ds1.getYValue(s, i);
                            if (Double.isNaN(y)) continue;
                            if (y > max) max = y;
                            if (y < min) min = y;
                        }
                    }
                }
                if (min == Double.POSITIVE_INFINITY) return super.getDataRange(axis);
                double pad = Math.max((max - min) * 0.05, 0.5);
                return new Range(min - pad, max + pad);
            }
        };
        plot.setBackgroundPaint(new Color(248, 248, 252));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // VWAP overlay on dataset 1
        XYSeries vwapSeries = new XYSeries("VWAP");
        List<Float> vwapList = Exchange.getVwapList();
        for (int i = 0; i < vwapList.size(); i++) {
            vwapSeries.add(roundToMs(i + 1), vwapList.get(i));
        }
        plot.setDataset(1, new XYSeriesCollection(vwapSeries));
        XYLineAndShapeRenderer vwapRenderer = new XYLineAndShapeRenderer(true, false);
        vwapRenderer.setSeriesPaint(0, new Color(180, 100, 0));
        vwapRenderer.setSeriesStroke(0, new BasicStroke(1.6f));
        plot.setRenderer(1, vwapRenderer);

        // Strong-sentiment markers on top of the candles
        addSentimentMarkers(plot, byRound);

        return plot;
    }

    private static void addSentimentMarkers(XYPlot plot, TreeMap<Integer, CandleAccum> byRound) {
        List<Integer> sentiment = Exchange.getSentimentList();
        for (int i = 0; i < sentiment.size(); i++) {
            int s = sentiment.get(i);
            if (s != 15 && s != 26) continue;

            int roundNum = i + 1;
            CandleAccum c = byRound.get(roundNum);
            if (c == null || c.isEmpty()) continue;

            double x = roundToMs(roundNum);
            boolean bullish = (s == 26);
            double y = bullish ? c.high : c.low;
            double angle = bullish ? -Math.PI / 2.0 : Math.PI / 2.0;

            XYPointerAnnotation arrow = new XYPointerAnnotation(bullish ? "▲" : "▼", x, y, angle);
            arrow.setTipRadius(2.0);
            arrow.setBaseRadius(14.0);
            arrow.setFont(new Font("SansSerif", Font.BOLD, 10));
            arrow.setPaint(bullish ? new Color(0, 130, 0) : new Color(170, 0, 0));
            arrow.setArrowPaint(bullish ? new Color(0, 130, 0) : new Color(170, 0, 0));
            plot.addAnnotation(arrow);
        }
    }

    // ----- Subplot 2: sentiment heatmap strip -----
    private static XYPlot buildSentimentStrip() {
        List<Integer> sentiment = Exchange.getSentimentList();
        int n = sentiment.size();

        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = roundToMs(i + 1);
            ys[i] = 0.0;
            zs[i] = sentiment.get(i);
        }
        DefaultXYZDataset ds = new DefaultXYZDataset();
        ds.addSeries("Sentiment", new double[][]{xs, ys, zs});

        NumberAxis stripAxis = new NumberAxis("Sentiment");
        stripAxis.setRange(-0.5, 0.5);
        stripAxis.setTickLabelsVisible(false);
        stripAxis.setTickMarksVisible(false);

        XYBlockRenderer renderer = new XYBlockRenderer();
        renderer.setBlockWidth(MS_PER_ROUND);
        renderer.setBlockHeight(1.0);
        renderer.setBlockAnchor(RectangleAnchor.CENTER);
        renderer.setPaintScale(buildSentimentPaintScale());

        XYPlot plot = new XYPlot(ds, null, stripAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        return plot;
    }

    private static LookupPaintScale buildSentimentPaintScale() {
        // 15 = bearish (deep red) → 20 = neutral grey → 26 = bullish (deep green).
        // mutate() also produces 16,17,18,19,21,22,23,24 so each integer step has
        // its own colour for a readable strip.
        LookupPaintScale scale = new LookupPaintScale(15, 27, new Color(220, 220, 220));
        scale.add(15, new Color(160, 0, 0));
        scale.add(16, new Color(190, 50, 50));
        scale.add(17, new Color(215, 100, 100));
        scale.add(18, new Color(230, 150, 150));
        scale.add(19, new Color(240, 200, 200));
        scale.add(20, new Color(220, 220, 220));
        scale.add(21, new Color(200, 230, 200));
        scale.add(22, new Color(160, 215, 160));
        scale.add(23, new Color(110, 195, 110));
        scale.add(24, new Color(70, 170, 70));
        scale.add(25, new Color(40, 145, 40));
        scale.add(26, new Color(0, 120, 0));
        return scale;
    }

    // ----- Subplot 3: RSI 14 (20/80) + RSI 10 (30/70) overlay -----
    private static XYPlot buildRsiPlot() {
        // RSI 14 first appears once roundFinalPrice has 16 entries; map index i → round 16 + i.
        // RSI 10 first appears once roundFinalPrice has 151 entries; map index i → round 151 + i.
        XYSeries rsi14 = new XYSeries("RSI 14");
        List<Float> rsiList = Exchange.getRsiList();
        for (int i = 0; i < rsiList.size(); i++) {
            rsi14.add(roundToMs(16 + i), rsiList.get(i));
        }
        XYSeries rsi10 = new XYSeries("RSI 10");
        List<Float> rsiPList = Exchange.getRsiPList();
        for (int i = 0; i < rsiPList.size(); i++) {
            rsi10.add(roundToMs(151 + i), rsiPList.get(i));
        }
        DefaultXYDataset ds = new DefaultXYDataset();
        ds.addSeries("RSI 14", toXY(rsi14));
        ds.addSeries("RSI 10", toXY(rsi10));

        NumberAxis rsiAxis = new NumberAxis("RSI");
        rsiAxis.setRange(0, 100);

        Color rsi14Colour = new Color(40, 90, 200);
        Color rsi10Colour = new Color(220, 130, 0);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, rsi14Colour);
        renderer.setSeriesPaint(1, rsi10Colour);
        renderer.setSeriesStroke(0, new BasicStroke(1.6f));
        renderer.setSeriesStroke(1, new BasicStroke(1.6f));

        XYPlot plot = new XYPlot(ds, null, rsiAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // All four threshold lines go to renderer 0 — there is only one renderer on
        // this plot; addRangeMarker(int rendererIndex, ...) silently ignores markers
        // for non-existent renderer indices.
        plot.addRangeMarker(0, thresholdMarker(20, rsi14Colour, "RSI14 20"), Layer.BACKGROUND);
        plot.addRangeMarker(0, thresholdMarker(80, rsi14Colour, "RSI14 80"), Layer.BACKGROUND);
        plot.addRangeMarker(0, thresholdMarker(30, rsi10Colour, "RSI10 30"), Layer.BACKGROUND);
        plot.addRangeMarker(0, thresholdMarker(70, rsi10Colour, "RSI10 70"), Layer.BACKGROUND);

        return plot;
    }

    private static ValueMarker thresholdMarker(double value, Color base, String label) {
        ValueMarker m = new ValueMarker(value);
        m.setPaint(new Color(base.getRed(), base.getGreen(), base.getBlue(), 90));
        m.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{6f, 4f}, 0f));
        m.setLabel(label);
        m.setLabelFont(new Font("SansSerif", Font.PLAIN, 9));
        m.setLabelPaint(new Color(base.getRed(), base.getGreen(), base.getBlue(), 200));
        return m;
    }

    // ----- helpers -----
    private static DefaultHighLowDataset toOhlcDataset(TreeMap<Integer, CandleAccum> byRound) {
        int n = byRound.size();
        Date[] dates = new Date[n];
        double[] high = new double[n];
        double[] low = new double[n];
        double[] open = new double[n];
        double[] close = new double[n];
        double[] volume = new double[n];
        int idx = 0;
        for (Map.Entry<Integer, CandleAccum> e : byRound.entrySet()) {
            CandleAccum c = e.getValue();
            dates[idx] = new Date(roundToMs(e.getKey()));
            open[idx] = c.open;
            high[idx] = c.high;
            low[idx] = c.low;
            close[idx] = c.close;
            volume[idx] = c.totalVolume;
            idx++;
        }
        return new DefaultHighLowDataset("Price", dates, high, low, open, close, volume);
    }

    private static double[][] toXY(XYSeries s) {
        int n = s.getItemCount();
        double[][] data = new double[2][n];
        for (int i = 0; i < n; i++) {
            data[0][i] = s.getX(i).doubleValue();
            data[1][i] = s.getY(i).doubleValue();
        }
        return data;
    }

    private static long roundToMs(int round) {
        return (long) round * MS_PER_ROUND;
    }

    private static DateFormat roundFormat() {
        return new DateFormat() {
            @Override
            public StringBuffer format(Date d, StringBuffer sb, FieldPosition fp) {
                return sb.append(d.getTime() / MS_PER_ROUND);
            }
            @Override
            public Date parse(String s, ParsePosition p) { return null; }
        };
    }
}
