package sss.ui;

/**
 * Created by alan on 11/15/16.
 */

import com.vaadin.ui.VerticalLayout;
import org.vaadin.addon.JFreeChartWrapper;

import java.awt.Color;
import java.awt.GradientPaint;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.Regression;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYSeries;


import com.vaadin.ui.Component;
import com.vaadin.ui.Panel;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Simple Vaadin app for testing purposes that displays some ugly charts with
 * {@link JFreeChartWrapper}.
 */
public class JFreeChartWrapperSample extends VerticalLayout {

    public JFreeChartWrapperSample() {
        setStyleName(ValoTheme.PANEL_BORDERLESS);
        //setScrollable(true);
        addComponent(createBasicDemo());

        addComponent(getLevelChart());

        addComponent(regressionChart());
    }

    public static JFreeChartWrapper createBasicDemo() {
        JFreeChart createchart = createchart(createDataset());
        return new JFreeChartWrapper(createchart);
    }

    /**
     * Returns a sample dataset.
     *
     * @return The dataset.
     */
    private static CategoryDataset createDataset() {

        // row keys...
        String y2009 = "2009";
        String y2008 = "2008";
        String y2007 = "2007";

        // column keys...
        String under5 = "< 5";
        String between5_9 = "5-9";
        String between10_14 = "10-14";
        String between15_19 = "15-19";
        String between20_24 = "20-24";

        // create the dataset...
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        dataset.addValue(21299656, y2009, under5);
        dataset.addValue(20609634, y2009, between5_9);
        dataset.addValue(19973564, y2009, between10_14);
        dataset.addValue(21537837, y2009, between15_19);
        dataset.addValue(21539559, y2009, between20_24);

        dataset.addValue(21005852, y2008, under5);
        dataset.addValue(20065249, y2008, between5_9);
        dataset.addValue(20054627, y2008, between10_14);
        dataset.addValue(21514358, y2008, between15_19);
        dataset.addValue(21058981, y2008, between20_24);

        dataset.addValue(20724125, y2007, under5);
        dataset.addValue(19849628, y2007, between5_9);
        dataset.addValue(20314309, y2007, between10_14);
        dataset.addValue(21473690, y2007, between15_19);
        dataset.addValue(21032396, y2007, between20_24);

        return dataset;

    }

    /**
     * Creates a sample chart.
     *
     * @param dataset the dataset.
     * @return The chart.
     */
    private static JFreeChart createchart(CategoryDataset dataset) {

        // create the chart...
        JFreeChart chart = ChartFactory.createBarChart("Bar Chart Demo 1", // chart
                // title
                "Age group", // domain axis label
                "Number of members", // range axis label
                dataset, // data
                PlotOrientation.VERTICAL, // orientation
                true, // include legend
                true, // tooltips?
                false // URLs?
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...

        // set the background color for the chart...
        chart.setBackgroundPaint(Color.white);

        // get a reference to the plot for further customisation...
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.white);

        // ******************************************************************
        // More than 150 demo applications are included with the JFreeChart
        // Developer Guide...for more information, see:
        //
        // > http://www.object-refinery.com/jfreechart/guide.html
        //
        // ******************************************************************

        // set the range axis to display integers only...
        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        // disable bar outlines...
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        // renderer.setDrawBarOutline(false);

        // set up gradient paints for series...
        GradientPaint gp0 = new GradientPaint(0.0f, 0.0f, Color.blue, 0.0f,
                0.0f, new Color(0, 0, 64));
        GradientPaint gp1 = new GradientPaint(0.0f, 0.0f, Color.green, 0.0f,
                0.0f, new Color(0, 64, 0));
        GradientPaint gp2 = new GradientPaint(0.0f, 0.0f, Color.red, 0.0f,
                0.0f, new Color(64, 0, 0));
        renderer.setSeriesPaint(0, gp0);
        renderer.setSeriesPaint(1, gp1);
        renderer.setSeriesPaint(2, gp2);

        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions
                .createUpRotationLabelPositions(Math.PI / 6.0));
        // OPTIONAL CUSTOMISATION COMPLETED.

        return chart;

    }

    private static Component getLevelChart() {

        DefaultTableXYDataset ds = new DefaultTableXYDataset();
        NumberAxis y = new NumberAxis("Sales in thousands of $");

        XYSeries series;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2006);
        long y2007 = cal.getTimeInMillis();
        cal.set(Calendar.YEAR, 2007);
        long y2008 = cal.getTimeInMillis();
        cal.set(Calendar.YEAR, 2008);
        long y2009 = cal.getTimeInMillis();

        series = new XYSeries("Asia", false, false);
        series.add(y2007, 130942);
        series.add(y2008, 78730);
        series.add(y2009, 86895);
        ds.addSeries(series);

        series = new XYSeries("Europe", false, false);
        series.add(y2007, 207740);
        series.add(y2008, 152144);
        series.add(y2009, 130942);
        ds.addSeries(series);

        series = new XYSeries("USA", false, false);
        series.add(y2007, 217047);
        series.add(y2008, 129870);
        series.add(y2009, 174850);
        ds.addSeries(series);

        // Paint p = new Color(0, 0, 0, Color.OPAQUE);
        // r.setSeriesPaint(0, p);
        // BasicStroke s = new BasicStroke(2);
        // r.setSeriesStroke(0, s);

        DateAxis x = new DateAxis("Year");
        x.setDateFormatOverride(new SimpleDateFormat("yyyy"));
        x.setTickUnit(new DateTickUnit(DateTickUnitType.YEAR, 1));

        XYPlot plot2 = new XYPlot(ds, x, y, new XYAreaRenderer(
                XYAreaRenderer.AREA_AND_SHAPES));
        plot2.setForegroundAlpha(0.5f);

        JFreeChart c = new JFreeChart(plot2);

        return new JFreeChartWrapper(c);
    }

    private static Component regressionChart() {

        DefaultTableXYDataset ds = new DefaultTableXYDataset();

        XYSeries series;

        series = new XYSeries("Measured difference", false, false);
        series.add(1, 1);
        series.add(2, 4);
        series.add(3, 6);
        series.add(4, 9);
        series.add(5, 9);
        series.add(6, 11);
        ds.addSeries(series);

        JFreeChart scatterPlot = ChartFactory.createScatterPlot("Regression",
                "cm", "Measuring checkpoint", ds, PlotOrientation.HORIZONTAL,
                true, false, false);

        XYPlot plot = (XYPlot) scatterPlot.getPlot();

        double[] regression = Regression.getOLSRegression(ds, 0);

        // regression line points

        double v1 = regression[0] + regression[1] * 1;
        double v2 = regression[0] + regression[1] * 6;

        DefaultXYDataset ds2 = new DefaultXYDataset();
        ds2.addSeries("regline", new double[][]{new double[]{1, 6},
                new double[]{v1, v2}});
        plot.setDataset(1, ds2);
        plot.setRenderer(1, new XYLineAndShapeRenderer(true, false));

        JFreeChart c = new JFreeChart(plot);

        return new JFreeChartWrapper(c);
    }

}
