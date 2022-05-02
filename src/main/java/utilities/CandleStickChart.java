package utilities;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import javax.swing.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import org.jfree.data.xy.DefaultHighLowDataset;
import trade.TradeData;

/**
 * creates a candlestick chart showing candles and volume
 * uses JFree chart library
 * https://jfree.org/jfreechart/
 * @version 1.0
 */
@SuppressWarnings("serial")
public class CandleStickChart extends JPanel {
    ArrayList<CandleData> candles; //list of CandleData objects
    DefaultHighLowDataset candlestickDataset; //dataset created from the candles list

    /**
     * public constructor to build the chart
     * @param title name of the chart
     * @param trades list of TradeData to be converted to CandleData objects
     */
    public CandleStickChart(String title, ArrayList<TradeData> trades) {
        TimeSeriesCollection volumeDataset = new TimeSeriesCollection();
        TimeSeries volumeSeries = new TimeSeries("Volume");
        addData(trades); //creates the CandleData objects and add them to candles list
        volumeDataset.addSeries(volumeSeries);

        JFreeChart chart = ChartFactory.createCandlestickChart(title, "Round", "Price", candlestickDataset, true);

        chart.setBackgroundPaint(Color.white);
        chart.setBorderVisible(false);
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinesVisible(true);
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.lightGray);
        ((NumberAxis) plot.getRangeAxis()).setAutoRangeIncludesZero(false);

        ((CandlestickRenderer) plot.getRenderer()).setDrawVolume(true);
        ((CandlestickRenderer) plot.getRenderer()).setVolumePaint(Color.blue);
        ((CandlestickRenderer) plot.getRenderer()).setUseOutlinePaint(false);
        ((CandlestickRenderer) plot.getRenderer()).setSeriesStroke(0, new BasicStroke(0.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
        ((CandlestickRenderer) plot.getRenderer()).setSeriesPaint(0, Color.black);

        JFrame myFrame = new JFrame();
        myFrame.setResizable(true);
        myFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        myFrame.add(new ChartPanel(chart), BorderLayout.CENTER);
        Toolkit kit = Toolkit.getDefaultToolkit();
        Insets insets = kit.getScreenInsets(myFrame.getGraphicsConfiguration());
        Dimension screen = kit.getScreenSize();
        myFrame.setSize((int) (screen.getWidth() - insets.left - insets.right), (int) (screen.getHeight() - insets.top - insets.bottom));
        myFrame.setLocation((int) (insets.left), (int) (insets.top));
        myFrame.setVisible(true);
    }

    public void addData(ArrayList<TradeData> trades) {
        trades.trimToSize();
        Collections.sort(trades);
        candles = CandleData.createCandles(trades); //turns all TradeData objects into CandleData objects
        int maxCandle = candles.size() - 1;

        Date[] date = new Date[maxCandle];
        double[] high = new double[maxCandle];
        double[] low = new double[maxCandle];
        double[] open = new double[maxCandle];
        double[] close = new double[maxCandle];
        double[] volume = new double[maxCandle];

        for (int i = 0; i < candles.size() - 1; i++) {
            date[i] = new Date(candles.get(i).getRound() * 86400000L); //makes each round a day
            high[i] = candles.get(i).getHigh();
            low[i] = candles.get(i).getLow();
            open[i] = candles.get(i).getOpen();
            close[i] = candles.get(i).getClose();
            volume[i] = candles.get(i).getVolume();

        }
        candlestickDataset = new DefaultHighLowDataset("", date, high, low, open, close, volume);
    }
}