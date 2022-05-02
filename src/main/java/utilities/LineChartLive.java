package utilities;

import good.Good;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * creates a live action stock price chart that is updated by an independent thread
 * uses JFree line chart library
 * https://jfree.org/jfreechart/
 * @version 1.0
 */
public class LineChartLive extends JFrame implements Runnable {
    boolean active = false;
    JFreeChart chart;
    ChartPanel chartPanel;
    float lowest = 0;
    float highest = 100;
    float dataLow;
    float dataHigh;
    final int CHARTSIZE = 1500;
    public LineChartLive() {
        initUI(Good.getPriceList());
    }

    /**
     * run method so the chart can be updated with independent threads
     */
    @Override
    public synchronized void run() {
        if (!active) {
            active = true;
            chart.getCategoryPlot().setDataset(createDataset(Good.getAvgPriceList())); //updates data on chart
            chart.getCategoryPlot().getRangeAxis().setRange((lowest * 0.99), (highest * 1.01)); //automatically updates range
            active = false;
        }
        return;
    }

    /**
     * creates the initial chart layout
     * @param goodMap list of floats to create dataset
     */
    private void initUI(ArrayList<Float> goodMap) {
        DefaultCategoryDataset dataset = createDataset(goodMap);
        chart = ChartFactory.createLineChart(
                "Stock price",
                "Trades",
                "Price ($)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        chartPanel = new ChartPanel(chart);
        chartPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chartPanel.setBackground(Color.white);
        add(chartPanel);

        pack();
        setTitle("Line chart");
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    /**
     * creates dataset from list
     * @param goodMap list of prices
     * @return the dataset
     */
    private DefaultCategoryDataset createDataset(ArrayList<Float> goodMap) {
        DefaultCategoryDataset series = new DefaultCategoryDataset();
        goodMap.trimToSize();
        int startNum = 1;
        //only shows 1500 trades
        if (goodMap.size() > CHARTSIZE) {
            dataHigh = 1;
            dataLow = 10000;
            startNum = (goodMap.size() - CHARTSIZE);
            //calculates dataset with last 1500 values
            for (int i = startNum; i < goodMap.size(); i++) {
                series.addValue(goodMap.get(i - 1), "price", (Integer) i);
                if (goodMap.get(i - 1) > dataHigh) {
                    dataHigh = goodMap.get(i - 1);
                }
                if (goodMap.get(i - 1) < dataLow) {
                    dataLow = goodMap.get(i - 1);
                }
            }
        } else {
            if (goodMap.size() > 4) {
                dataHigh = 1;
                dataLow = 10000;
            } else {
                dataLow = 0;
                dataHigh = 100;
           }
            for (int i = startNum; i < goodMap.size(); i++) {
                series.addValue(goodMap.get(i - 1), "price", (Integer) i);
                if (goodMap.get(i - 1) > dataHigh) {
                    dataHigh = goodMap.get(i - 1);
                }
                if (goodMap.get(i - 1) < dataLow) {
                    dataLow = goodMap.get(i - 1);
                }
            }
        }
        lowest = dataLow;
        highest = dataHigh;
        return series;
    }
}