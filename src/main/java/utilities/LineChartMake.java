package utilities;

import lombok.Setter;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * creates a line chart using a list of floats to create a dataset
 * created with JFree chart library
 * https://jfree.org/jfreechart/
 * @version 1.0
 */
public class LineChartMake extends JFrame {
    @Setter private float lowest;
    @Setter private float highest;

    /**
     * constructor that allows strict range limits on the y-axis
     * @param goodMap list of floats to create dataset
     * @param title title of the chart
     * @param xTitle description of the x-axis
     * @param yTitle description of the y-axis
     * @param low lowest point on the y-axis
     * @param high highest point on the y-axis
     */
    public LineChartMake(ArrayList<Float> goodMap, String title, String xTitle, String yTitle, float low, float high) {
        DefaultCategoryDataset dataset = createDataset(goodMap);
        JFreeChart chart = ChartFactory.createLineChart(
                title,
                xTitle,
                yTitle,
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        chart.getCategoryPlot().getRangeAxis().setRange((low * 0.99), (high * 1.01));
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        chartPanel.setBackground(Color.white);
        chartPanel.setMouseWheelEnabled(true);
        add(chartPanel);

        pack();
        setTitle(title);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    /**
     * constructor with automatic y-axis range, fits to data
     * @param goodMap list of floats as dataset
     * @param title title of the chart
     * @param xTitle description of the x-axis
     * @param yTitle description of the y-axis
     */
    public LineChartMake(ArrayList<Float> goodMap, String title, String xTitle, String yTitle) {
        DefaultCategoryDataset dataset = createDataset(goodMap);
        JFreeChart chart = ChartFactory.createLineChart(
                title,
                xTitle,
                yTitle,
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        chart.getCategoryPlot().getRangeAxis().setRange((lowest * 0.99), (highest * 1.01));
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        chartPanel.setBackground(Color.white);
        chartPanel.setMouseWheelEnabled(true);
        add(chartPanel);

        pack();
        setTitle(title);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    /**
     * creates the dataset and assigns low and high points on the chart
     * @param goodMap the lsit of floats for data
     * @return the compiled dataset
     */
    private DefaultCategoryDataset createDataset(ArrayList<Float> goodMap) {
        DefaultCategoryDataset series = new DefaultCategoryDataset();
        float dataLow = 10000000;
        float dataHigh = 1;
        for (int i = 1; i < goodMap.size(); i++) {
            series.addValue(goodMap.get(i-1), "price", (Integer)i);
            if (goodMap.get(i - 1) > dataHigh) {
                dataHigh = goodMap.get(i - 1);
            }
            if (goodMap.get(i - 1) < dataLow) {
                dataLow = goodMap.get(i - 1);
            }
            lowest = dataLow;
            highest = dataHigh;
        }
        return series;
    }
}