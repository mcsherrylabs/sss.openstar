package sss.ui;

/**
 * Created by alan on 11/15/16.
 */

import com.vaadin.ui.Component;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.*;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;

import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.XYSeries;
import org.vaadin.addon.JFreeChartWrapper;


/**
 * Simple Vaadin app for testing purposes that displays some ugly charts with
 * {@link JFreeChartWrapper}.
 */
public class StatisticsChart extends VerticalLayout {

    public StatisticsChart() {

        addComponent(getLevelChart());
    }


    private static Component getLevelChart() {


        DefaultTableXYDataset ds = new DefaultTableXYDataset();
        NumberAxis y = new NumberAxis("Txs, Coinbase, etc.");

        XYSeries series;


        series = new XYSeries("Coinbase", false, false);
        series.add(1, 130942);
        series.add(2, 78730);
        series.add(3, 86895);
        ds.addSeries(series);

        series = new XYSeries("Txs", false, false);
        series.add(1, 207740);
        series.add(2, 152144);
        series.add(3, 130942);
        ds.addSeries(series);

        series = new XYSeries("Identities", false, false);
        series.add(1, 217047);
        series.add(2, 129870);
        series.add(3, 174850);
        ds.addSeries(series);

        series = new XYSeries("Time", false, false);
        ds.addSeries(series);


        NumberAxis x = new NumberAxis("Block Height");

        XYPlot plot2 = new XYPlot(ds, x, y, new XYAreaRenderer(
                XYAreaRenderer.AREA_AND_SHAPES));
        plot2.setForegroundAlpha(0.5f);

        JFreeChart c = new JFreeChart(plot2);

        return new JFreeChartWrapper(c);
    }

}
