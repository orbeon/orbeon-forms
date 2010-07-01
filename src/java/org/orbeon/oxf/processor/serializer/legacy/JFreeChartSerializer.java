/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.serializer.legacy;

import org.dom4j.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.labels.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.AbstractRenderer;
import org.jfree.chart.renderer.category.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.*;
import org.jfree.data.time.*;
import org.jfree.data.xy.*;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.xml.XPathUtils;

import java.awt.*;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class JFreeChartSerializer extends HttpBinarySerializer {

    public static final String CHART_CONVERTER_CHART_NAMESPACE_URI = "http://www.orbeon.com/oxf/converter/chart-chart";

    public static String DEFAULT_CONTENT_TYPE = "image/png";
    public static final String INPUT_CHART = "chart";

    public static final Color DEFAULT_BACKGROUND_COLOR = getRGBColor("#FFFFFF");
    public static final Color DEFAULT_TITLE_COLOR = getRGBColor("#000000");

    public JFreeChartSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CHART, CHART_CONVERTER_CHART_NAMESPACE_URI));
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext pipelineContext, ProcessorInput input, Config config, OutputStream outputStream) {
        try {
            ChartConfig chartConfig = (ChartConfig) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CHART),
                    new org.orbeon.oxf.processor.CacheableInputReader() {
                        public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, org.orbeon.oxf.processor.ProcessorInput input) {
                            return createChartConfig(context, input);
                        }
                    });
            Document data = readInputAsDOM4J(pipelineContext, (input != null) ? input : getInputByName(INPUT_DATA));

            Dataset ds;
            if (chartConfig.getType() == ChartConfig.PIE_TYPE ||
                    chartConfig.getType() == ChartConfig.PIE3D_TYPE)
                ds = createPieDataset(chartConfig, data);
            else if(chartConfig.getType() == ChartConfig.XY_LINE_TYPE)
                ds = createXYDataset(chartConfig, data);
            else if(chartConfig.getType() == ChartConfig.TIME_SERIES_TYPE)
                ds = createTimeSeriesDataset(chartConfig, data);
            else
                ds = createDataset(chartConfig, data);
            JFreeChart chart = drawChart(chartConfig, ds);
            ChartRenderingInfo info = new ChartRenderingInfo();
            ChartUtilities.writeChartAsPNG(outputStream, chart, chartConfig.getxSize(), chartConfig.getySize(), info, true, 5);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    protected PieDataset createPieDataset(ChartConfig chartConfig, Document data) {
        ExtendedPieDataset ds = new ExtendedPieDataset();
        Value value = (Value) chartConfig.getValueIterator().next();

        Iterator cats = XPathUtils.selectIterator(data, value.getCategories());
        Iterator series = XPathUtils.selectIterator(data, value.getSeries());
        Iterator colors = null;
        if (value.getColors() != null)
            colors = XPathUtils.selectIterator(data, value.getColors());

        Iterator explodedPercents = null;
        if (value.getExplodedPercents() != null)
            explodedPercents = XPathUtils.selectIterator(data, value.getExplodedPercents());

        while (cats.hasNext() && series.hasNext()) {
            Element s = (Element) series.next();
            Element c = (Element) cats.next();
            Double d = new Double(s.getText().trim());
            Paint p = null;
            double ep = 0;

            if (colors != null) {
                Element col = (Element) colors.next();
                p = getRGBColor(col.getText());
            }

            if (explodedPercents != null) {
                Element e = (Element) explodedPercents.next();
                ep = Double.parseDouble(e.getText());
            }

            ds.setValue(c.getText(), d, p, ep);
        }
        return ds;
    }

    protected CategoryDataset createDataset(ChartConfig chartConfig, Document data) {
        ItemPaintCategoryDataset ds = new ItemPaintCategoryDataset();
        for (Iterator i = chartConfig.getValueIterator(); i.hasNext();) {
            Value value = (Value) i.next();
            Iterator cats = XPathUtils.selectIterator(data, value.getCategories());
            Iterator series = XPathUtils.selectIterator(data, value.getSeries());

            Iterator colors = null;
            if (value.getColors() != null)
                colors = XPathUtils.selectIterator(data, value.getColors());

            while (cats.hasNext() && series.hasNext()) {
                Node s = (Node) series.next();
                Node c = (Node) cats.next();
                Double d = new Double(s.getStringValue());
                if (colors != null) {
                    Node col = (Node) colors.next();
                    Color color = getRGBColor(col.getStringValue());
                    ds.addValue(d, color, value.getTitle(), c.getStringValue());
                } else
                    ds.addValue(d, value.getTitle(), c.getStringValue());
            }
        }
        return ds;
    }

    protected XYSeriesCollection createXYDataset(ChartConfig chartConfig, Document data) {
        XYSeriesCollection ds = new XYSeriesCollection();
        for (Iterator i = chartConfig.getValueIterator(); i.hasNext();) {
            Value value = (Value) i.next();
            String title = value.getTitle();
            Iterator x = XPathUtils.selectIterator(data, value.getCategories());
            Iterator y = XPathUtils.selectIterator(data, value.getSeries());

            XYSeries xyseries = new XYSeries(title);
            while (x.hasNext() && y.hasNext()) {
                Node s = (Node) y.next();
                Node c = (Node) x.next();
                Double abcissa = new Double(c.getStringValue());
                Double ordinate = new Double(s.getStringValue());
                xyseries.add(abcissa, ordinate);
            }
            ds.addSeries(xyseries);
        }
        return ds;
    }

    protected TimeSeriesCollection createTimeSeriesDataset(ChartConfig chartConfig, Document data) {
        TimeSeriesCollection ds = new TimeSeriesCollection();
        for (Iterator i = chartConfig.getValueIterator(); i.hasNext();) {
            Value value = (Value) i.next();
            String title = value.getTitle();
            Iterator x = XPathUtils.selectIterator(data, value.getCategories());
            Iterator y = XPathUtils.selectIterator(data, value.getSeries());

            TimeSeries timeSeries = new TimeSeries(title, FixedMillisecond.class );
            while (x.hasNext() && y.hasNext()) {
                Node s = (Node) y.next();
                Node c = (Node) x.next();
                SimpleDateFormat sdf = new SimpleDateFormat(chartConfig.getDateFormat());
                FixedMillisecond fm;
                try {
                    fm = new FixedMillisecond(sdf.parse(c.getStringValue()).getTime());
                }
                catch(java.text.ParseException pe) {
                    throw new OXFException("Date Format " + chartConfig.getDateFormat() + " does not match with the date data", pe);
                }
                Double ordinate = new Double(s.getStringValue());
                timeSeries.add(fm,ordinate);
            }
            ds.addSeries(timeSeries);
        }
        return ds;
    }
    protected ChartConfig createChartConfig(org.orbeon.oxf.pipeline.api.PipelineContext context, org.orbeon.oxf.processor.ProcessorInput input) {
        ChartConfig chart = new ChartConfig();
        Document doc = readInputAsDOM4J(context, input);
        DefaultDrawingSupplier defaults = new DefaultDrawingSupplier();

        String type = XPathUtils.selectStringValueNormalize(doc, "/chart/type");
        if (type.equals("vertical-bar"))
            chart.setType(ChartConfig.VERTICAL_BAR_TYPE);
        else if (type.equals("horizontal-bar"))
            chart.setType(ChartConfig.HORIZONTAL_BAR_TYPE);
        else if (type.equals("line"))
            chart.setType(ChartConfig.LINE_TYPE);
        else if (type.equals("area"))
            chart.setType(ChartConfig.AREA_TYPE);
        else if (type.equals("stacked-vertical-bar"))
            chart.setType(ChartConfig.STACKED_VERTICAL_BAR_TYPE);
        else if (type.equals("stacked-horizontal-bar"))
            chart.setType(ChartConfig.STACKED_HORIZONTAL_BAR_TYPE);
        else if (type.equals("stacked-vertical-bar-3d"))
            chart.setType(ChartConfig.STACKED_VERTICAL_BAR3D_TYPE);
        else if (type.equals("stacked-horizontal-bar-3d"))
            chart.setType(ChartConfig.STACKED_HORIZONTAL_BAR3D_TYPE);
        else if (type.equals("vertical-bar-3d"))
            chart.setType(ChartConfig.VERTICAL_BAR3D_TYPE);
        else if (type.equals("horizontal-bar-3d"))
            chart.setType(ChartConfig.HORIZONTAL_BAR3D_TYPE);
        else if (type.equals("pie"))
            chart.setType(ChartConfig.PIE_TYPE);
        else if (type.equals("pie-3d"))
            chart.setType(ChartConfig.PIE3D_TYPE);
        else if (type.equals("xy-line"))
            chart.setType(ChartConfig.XY_LINE_TYPE);
        else if (type.equals("time-series"))
            chart.setType(ChartConfig.TIME_SERIES_TYPE);
        else
            throw new OXFException("Chart type " + type + " is not supported");

        String title = XPathUtils.selectStringValueNormalize(doc, "/chart/title");
        chart.setTitle(title == null ? "" : title);
        chart.setMap(XPathUtils.selectStringValueNormalize(doc, "/chart/map"));
        chart.setCategoryTitle(XPathUtils.selectStringValueNormalize(doc, "/chart/category-title"));

        String catMargin = XPathUtils.selectStringValueNormalize(doc, "/chart/category-margin");
        if (catMargin != null)
            chart.setCategoryMargin(Double.parseDouble(catMargin));

        chart.setSerieTitle(XPathUtils.selectStringValueNormalize(doc, "/chart/serie-title"));
        chart.setSerieAutoRangeIncludeZero(XPathUtils.selectBooleanValue(doc, "not(/chart/serie-auto-range-include-zero = 'false')").booleanValue());
        chart.setxSize(XPathUtils.selectIntegerValue(doc, "/chart/x-size").intValue());
        chart.setySize(XPathUtils.selectIntegerValue(doc, "/chart/y-size").intValue());

        String bgColor = XPathUtils.selectStringValueNormalize(doc, "/chart/background-color");
        String tColor = XPathUtils.selectStringValueNormalize(doc, "/chart/title-color");
        Integer maxNumOfTickUnit = XPathUtils.selectIntegerValue(doc, "/chart/max-number-of-labels");
        String sDateFormat = XPathUtils.selectStringValueNormalize(doc, "/chart/date-format");
        String categoryLabelAngle = XPathUtils.selectStringValueNormalize(doc, "/chart/category-label-angle");

        chart.setBackgroundColor(bgColor == null ? DEFAULT_BACKGROUND_COLOR : getRGBColor(bgColor));
        chart.setTitleColor(tColor == null ? DEFAULT_TITLE_COLOR : getRGBColor(tColor));
        if(maxNumOfTickUnit != null)
            chart.setMaxNumOfLabels(maxNumOfTickUnit.intValue());
        if(sDateFormat != null)
            chart.setDateFormat(sDateFormat);

        if (categoryLabelAngle != null) {
            double angle = Double.parseDouble(categoryLabelAngle);
            chart.setCategoryLabelPosition(CategoryLabelPositions.createUpRotationLabelPositions(angle * (Math.PI / 180)));
            chart.setCategoryLabelAngle(angle);
        }

        String margin = XPathUtils.selectStringValueNormalize(doc, "/chart/bar-margin");
        if (margin != null)
            chart.setBarMargin(Double.parseDouble(margin));

        // legend
        CustomLegend legend = new CustomLegend(null);
        Boolean legendVis = XPathUtils.selectBooleanValue(doc, "/chart/legend/@visible = 'true'");
        legend.setVisible(legendVis.booleanValue());
        if(legend.isVisible()) {
            String pos = XPathUtils.selectStringValueNormalize(doc, "/chart/legend/@position");

            if ("north".equals(pos)) {
                legend.setPosition(RectangleEdge.TOP);
            }
            else if ("east".equals(pos)) {
                legend.setPosition(RectangleEdge.RIGHT);
            }
            else if ("south".equals(pos)) {
                legend.setPosition(RectangleEdge.BOTTOM);
            }
            else if ("west".equals(pos)) {
                legend.setPosition(RectangleEdge.LEFT);
            }
            for (Iterator i = XPathUtils.selectIterator(doc, "/chart/legend/item"); i.hasNext();) {
                Element el = (Element) i.next();
                Color color = getRGBColor(el.attributeValue("color"));
                String label = el.attributeValue("label");
                legend.addItem(label, color);
            }
        }
        chart.setLegendConfig(legend);

        for (Iterator i = XPathUtils.selectIterator(doc, "/chart/*[name()='value']"); i.hasNext();) {
            Element el = (Element) i.next();
            String c = el.attributeValue("color");
            Paint color;
            if (c != null)
                color = getRGBColor(c);
            else
                color = defaults.getNextPaint();

            chart.addValue(el.attributeValue("title"),
                    el.attributeValue("categories"),
                    el.attributeValue("series"),
                    el.attributeValue("colors"),
                    el.attributeValue("exploded-percents"),
                    color);
        }
        return chart;
    }


    protected JFreeChart drawChart(ChartConfig chartConfig, final Dataset ds) {
        JFreeChart chart = null;
        Axis categoryAxis = null;
        if(ds instanceof XYSeriesCollection) {
            categoryAxis = new RestrictedNumberAxis(chartConfig.getCategoryTitle());
        }
        else if(ds instanceof TimeSeriesCollection) {
            categoryAxis = new DateAxis(chartConfig.getCategoryTitle());
            ((DateAxis)categoryAxis).setDateFormatOverride(new SimpleDateFormat(chartConfig.getDateFormat()));
            if (chartConfig.getCategoryLabelAngle() == 90) {
                ((DateAxis)categoryAxis).setVerticalTickLabels(true);
            } else {
                if (chartConfig.getCategoryLabelAngle() != 0)
                    throw new OXFException("The only supported values of category-label-angle for time-series charts are 0 or 90");
            }
        }
        else {
            categoryAxis = new CategoryAxis(chartConfig.getCategoryTitle());
            ((CategoryAxis)categoryAxis).setCategoryLabelPositions(chartConfig.getCategoryLabelPosition());
        }
        NumberAxis valueAxis = new RestrictedNumberAxis(chartConfig.getSerieTitle());
        valueAxis.setAutoRangeIncludesZero(chartConfig.getSerieAutoRangeIncludeZero());
        AbstractRenderer renderer = null;
        Plot plot = null;

        switch (chartConfig.getType()) {
            case ChartConfig.VERTICAL_BAR_TYPE:
            case ChartConfig.HORIZONTAL_BAR_TYPE:
                renderer = (ds instanceof ItemPaintCategoryDataset) ? new BarRenderer() {
                    public Paint getItemPaint(int row, int column) {
                        Paint p = ((ItemPaintCategoryDataset) ds).getItemPaint(row, column);
                        if (p != null)
                            return p;
                        else
                            return getSeriesPaint(row);
                    }
                } : new BarRenderer();

                plot = new CategoryPlot((CategoryDataset) ds, (CategoryAxis)categoryAxis, (ValueAxis)valueAxis, (CategoryItemRenderer)renderer);

                if(chartConfig.getType() == ChartConfig.VERTICAL_BAR_TYPE)
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.VERTICAL);
                else
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.HORIZONTAL);

                break;
            case ChartConfig.STACKED_VERTICAL_BAR_TYPE:
            case ChartConfig.STACKED_HORIZONTAL_BAR_TYPE:
                renderer = (ds instanceof ItemPaintCategoryDataset) ? new StackedBarRenderer() {
                    public Paint getItemPaint(int row, int column) {
                        Paint p = ((ItemPaintCategoryDataset) ds).getItemPaint(row, column);
                        if (p != null)
                            return p;
                        else
                            return getSeriesPaint(row);
                    }
                } : new StackedBarRenderer();
                plot = new CategoryPlot((CategoryDataset) ds, (CategoryAxis)categoryAxis, (ValueAxis)valueAxis,(CategoryItemRenderer)renderer);

                if(chartConfig.getType() == ChartConfig.STACKED_VERTICAL_BAR_TYPE)
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.VERTICAL);
                else
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.HORIZONTAL);
                break;
            case ChartConfig.LINE_TYPE:
                renderer = (ds instanceof ItemPaintCategoryDataset) ? new LineAndShapeRenderer(true,false) {
                    public Paint getItemPaint(int row, int column) {
                        Paint p = ((ItemPaintCategoryDataset) ds).getItemPaint(row, column);
                        if (p != null)
                            return p;
                        else
                            return getSeriesPaint(row);
                    }
                } : (new LineAndShapeRenderer(true,false));
                plot = new CategoryPlot((CategoryDataset) ds, (CategoryAxis)categoryAxis, (ValueAxis)valueAxis, (CategoryItemRenderer)renderer);
                ((CategoryPlot) plot).setOrientation(PlotOrientation.VERTICAL);
                break;
            case ChartConfig.AREA_TYPE:
                renderer = (ds instanceof ItemPaintCategoryDataset) ? new AreaRenderer() {
                    public Paint getItemPaint(int row, int column) {
                        Paint p = ((ItemPaintCategoryDataset) ds).getItemPaint(row, column);
                        if (p != null)
                            return p;
                        else
                            return getSeriesPaint(row);
                    }
                } : new AreaRenderer();
                plot = new CategoryPlot((CategoryDataset) ds, (CategoryAxis)categoryAxis, (ValueAxis)valueAxis, (CategoryItemRenderer)renderer);
                ((CategoryPlot) plot).setOrientation(PlotOrientation.VERTICAL);
                break;
            case ChartConfig.VERTICAL_BAR3D_TYPE:
            case ChartConfig.HORIZONTAL_BAR3D_TYPE:
                categoryAxis = new CategoryAxis3D(chartConfig.getCategoryTitle());
                valueAxis = new NumberAxis3D(chartConfig.getSerieTitle());
                renderer = (ds instanceof ItemPaintCategoryDataset) ? new BarRenderer3D() {
                    public Paint getItemPaint(int row, int column) {
                        Paint p = ((ItemPaintCategoryDataset) ds).getItemPaint(row, column);
                        if (p != null)
                            return p;
                        else
                            return getSeriesPaint(row);
                    }
                } : new BarRenderer3D();
                plot = new CategoryPlot((CategoryDataset) ds, (CategoryAxis)categoryAxis, (ValueAxis)valueAxis, (CategoryItemRenderer)renderer);

                if(chartConfig.getType() == ChartConfig.VERTICAL_BAR3D_TYPE)
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.VERTICAL);
                else
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.HORIZONTAL);

                break;
            case ChartConfig.STACKED_VERTICAL_BAR3D_TYPE:
            case ChartConfig.STACKED_HORIZONTAL_BAR3D_TYPE:
                categoryAxis = new CategoryAxis3D(chartConfig.getCategoryTitle());
                valueAxis = new NumberAxis3D(chartConfig.getSerieTitle());
                renderer = (ds instanceof ItemPaintCategoryDataset) ? new StackedBarRenderer3D() {
                    public Paint getItemPaint(int row, int column) {
                        Paint p = ((ItemPaintCategoryDataset) ds).getItemPaint(row, column);
                        if (p != null)
                            return p;
                        else
                            return getSeriesPaint(row);
                    }
                } : new StackedBarRenderer3D();
                plot = new CategoryPlot((CategoryDataset) ds, (CategoryAxis)categoryAxis, (ValueAxis)valueAxis,(CategoryItemRenderer) renderer);

                if(chartConfig.getType() == ChartConfig.STACKED_VERTICAL_BAR3D_TYPE)
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.VERTICAL);
                else
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.HORIZONTAL);

                break;
            case ChartConfig.PIE_TYPE:
            case ChartConfig.PIE3D_TYPE:
                categoryAxis = null;
                valueAxis = null;
                renderer = null;
                ExtendedPieDataset pds = (ExtendedPieDataset) ds;

                plot = chartConfig.getType() == ChartConfig.PIE_TYPE ? new PiePlot(pds) : new PiePlot3D(pds);

                PiePlot pp = (PiePlot) plot;
                pp.setLabelGenerator(new StandardPieSectionLabelGenerator());

                for (int i = 0; i < pds.getItemCount(); i++) {
                    Paint p = pds.getPaint(i);
                    if (p != null)
                        pp.setSectionPaint(i, p);

                    pp.setExplodePercent(i, pds.getExplodePercent(i));

                    Paint paint = pds.getPaint(i);
                    if (paint != null)
                        pp.setSectionPaint(i, paint);
                }
                break;
            case ChartConfig.XY_LINE_TYPE:
                renderer =  new XYLineAndShapeRenderer(true,false);
                plot = new XYPlot((XYDataset) ds, (ValueAxis)categoryAxis, (ValueAxis)valueAxis, (XYLineAndShapeRenderer) renderer);
                break;
            case ChartConfig.TIME_SERIES_TYPE:
                renderer =  new XYLineAndShapeRenderer(true,false);
                plot = new XYPlot((XYDataset) ds, (DateAxis)categoryAxis, (ValueAxis)valueAxis, (XYLineAndShapeRenderer) renderer);
                break;
            default:
                throw new OXFException("Chart Type not supported");
        }

        if (categoryAxis != null) {
            categoryAxis.setLabelPaint(chartConfig.getTitleColor());
            categoryAxis.setTickLabelPaint(chartConfig.getTitleColor());
            categoryAxis.setTickMarkPaint(chartConfig.getTitleColor());
            if(categoryAxis instanceof RestrictedNumberAxis) {
                ((RestrictedNumberAxis)categoryAxis).setMaxTicks(chartConfig.getMaxNumOfLabels());
            }
            if (categoryAxis instanceof CategoryAxis && chartConfig.getCategoryMargin() != 0)
                ((CategoryAxis)categoryAxis).setCategoryMargin(chartConfig.getCategoryMargin());
        }

        if (valueAxis != null) {
            valueAxis.setLabelPaint(chartConfig.getTitleColor());
            valueAxis.setTickLabelPaint(chartConfig.getTitleColor());
            valueAxis.setTickMarkPaint(chartConfig.getTitleColor());
            ((RestrictedNumberAxis)valueAxis).setMaxTicks(chartConfig.getMaxNumOfLabels());
        }

        if (renderer != null) {
            if(renderer instanceof XYLineAndShapeRenderer) {
                ((XYLineAndShapeRenderer) renderer).setBaseItemLabelGenerator(new StandardXYItemLabelGenerator());
            }
            else {
                ((CategoryItemRenderer) renderer).setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
            }
            if (renderer instanceof BarRenderer)
                ((BarRenderer) renderer).setItemMargin(chartConfig.getBarMargin());

            int j = 0;
            for (Iterator i = chartConfig.getValueIterator(); i.hasNext();) {
                Value v = (Value) i.next();
                renderer.setSeriesPaint(j, v.getColor());
                j++;
            }
        }

        plot.setOutlinePaint(chartConfig.getTitleColor());
        CustomLegend legend = chartConfig.getLegendConfig();
        chart = new JFreeChart(chartConfig.getTitle(), TextTitle.DEFAULT_FONT, plot, false);
        if(legend.isVisible()) {
            legend.setSources(new LegendItemSource[] {plot});
            chart.addLegend(legend);
        }
        chart.setBackgroundPaint(chartConfig.getBackgroundColor());
        TextTitle textTitle = new TextTitle(chartConfig.getTitle(),TextTitle.DEFAULT_FONT,chartConfig.getTitleColor(),
                TextTitle.DEFAULT_POSITION, TextTitle.DEFAULT_HORIZONTAL_ALIGNMENT,TextTitle.DEFAULT_VERTICAL_ALIGNMENT,
                TextTitle.DEFAULT_PADDING);
        chart.setTitle(textTitle);
        return chart;
    }


    protected static class ChartConfig {
        public static final int VERTICAL_BAR_TYPE = 0;
        public static final int HORIZONTAL_BAR_TYPE = 1;

        public static final int STACKED_VERTICAL_BAR_TYPE = 2;
        public static final int STACKED_HORIZONTAL_BAR_TYPE = 3;

        public static final int LINE_TYPE = 4;
        public static final int AREA_TYPE = 5;

        public static final int VERTICAL_BAR3D_TYPE = 6;
        public static final int HORIZONTAL_BAR3D_TYPE = 7;

        public static final int STACKED_VERTICAL_BAR3D_TYPE = 8;
        public static final int STACKED_HORIZONTAL_BAR3D_TYPE = 9;

        public static final int PIE_TYPE = 10;
        public static final int PIE3D_TYPE = 11;
        public static final int XY_LINE_TYPE = 12;
        public static final int TIME_SERIES_TYPE = 13;


        private int type;
        private String title;
        private String map;
        private String categoryTitle;
        private String serieTitle;
        private boolean serieAutoRangeIncludeZero;
        private double categoryMargin;
        private Color titleColor;
        private Color backgroundColor;
        private double barMargin;
        private int maxNumOfLabels = 10;
        private String dateFormat;
        private CategoryLabelPositions categoryLabelPosition = CategoryLabelPositions.STANDARD;
        private CustomLegend legendConfig;
        private double categoryLabelAngle = 0;

        private List values = new ArrayList();

        private int xSize;
        private int ySize;


        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCategoryTitle() {
            return categoryTitle;
        }

        public void setCategoryTitle(String categoryTitle) {
            this.categoryTitle = categoryTitle;
        }

        public String getSerieTitle() {
            return serieTitle;
        }

        public void setSerieTitle(String serieTitle) {
            this.serieTitle = serieTitle;
        }

        public boolean getSerieAutoRangeIncludeZero() {
            return serieAutoRangeIncludeZero;
        }

        public void setSerieAutoRangeIncludeZero(boolean serieAutoRangeIncludeZero) {
            this.serieAutoRangeIncludeZero = serieAutoRangeIncludeZero;
        }

        public int getxSize() {
            return xSize;
        }

        public void setxSize(int xSize) {
            this.xSize = xSize;
        }

        public int getySize() {
            return ySize;
        }

        public void setySize(int ySize) {
            this.ySize = ySize;
        }

        public Color getTitleColor() {
            return titleColor;
        }

        public void setTitleColor(Color titleColor) {
            this.titleColor = titleColor;
        }

        public Color getBackgroundColor() {
            return backgroundColor;
        }

        public void setBackgroundColor(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
        }

        public void addValue(String title, String categories, String series, String colors, String explodedPercent, Paint color) {
            values.add(new Value(title, categories, series, colors, explodedPercent, color));
        }

        public Iterator getValueIterator() {
            return values.iterator();
        }

        public String getMap() {
            return map;
        }

        public void setMap(String map) {
            this.map = map;
        }

        public double getBarMargin() {
            return barMargin;
        }

        public void setBarMargin(double barMargin) {
            this.barMargin = barMargin;
        }

        public CustomLegend getLegendConfig() {
            return legendConfig;
        }

        public void setLegendConfig(CustomLegend legendConfig) {
            this.legendConfig = legendConfig;
        }

        public double getCategoryMargin() {
            return categoryMargin;
        }

        public void setCategoryMargin(double categoryMargin) {
            this.categoryMargin = categoryMargin;
        }

        public CategoryLabelPositions getCategoryLabelPosition() {
            return categoryLabelPosition;
        }

        public void setCategoryLabelPosition(CategoryLabelPositions categoryLabelPosition) {
            this.categoryLabelPosition = categoryLabelPosition;
        }

        public int getMaxNumOfLabels() {
            return maxNumOfLabels;
        }

        public void setMaxNumOfLabels(int maxNumOfLabels) {
            this.maxNumOfLabels = maxNumOfLabels;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }

        public double getCategoryLabelAngle() {
            return categoryLabelAngle;
        }

        public void setCategoryLabelAngle(double categoryLabelAngle) {
            this.categoryLabelAngle = categoryLabelAngle;
        }
    }

    protected static class Value {
        private String title;
        private String categories;
        private String series;
        private String colors;
        private String explodedPercents;
        private Paint color;

        public Value(String title, String categories, String series, String colors, String explodedPercents, Paint color) {
            this.title = title;
            this.categories = categories;
            this.series = series;
            this.colors = colors;
            this.explodedPercents = explodedPercents;
            this.color = color;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCategories() {
            return categories;
        }

        public void setCategories(String categories) {
            this.categories = categories;
        }

        public String getSeries() {
            return series;
        }

        public void setSeries(String series) {
            this.series = series;
        }

        public Paint getColor() {
            return color;
        }

        public void setColor(Paint color) {
            this.color = color;
        }

        public String getColors() {
            return colors;
        }

        public void setColors(String colors) {
            this.colors = colors;
        }

        public String getExplodedPercents() {
            return explodedPercents;
        }

        public void setExplodedPercents(String explodedPercents) {
            this.explodedPercents = explodedPercents;
        }
    }

    protected static class CustomLegend extends LegendTitle {
        private boolean visible = true;
        private LegendItemCollection legendItems = new LegendItemCollection();
        public static final Font DEFAULT_TITLE_FONT = new Font("SansSerif", 1, 11);
        public static Paint DEFAULT_BACKGROUND_PAINT = Color.white;
        public static Paint DEFAULT_OUTLINE_PAINT = Color.gray;
        public static final Stroke DEFAULT_OUTLINE_STROKE = new BasicStroke();

        public CustomLegend(Plot plot) {
            super(plot);
        }

        public void addItem(String label, Color color) {
            LegendItem legendItem = new LegendItem(label,label,label,label,AbstractRenderer.DEFAULT_SHAPE,
                    color,AbstractRenderer.DEFAULT_OUTLINE_STROKE,
                    AbstractRenderer.DEFAULT_OUTLINE_PAINT);
            legendItems.add(legendItem);
        }

        public boolean hasItems() {
            return legendItems.getItemCount() > 0;
        }

        public Iterator itemIterator() {
            return legendItems.iterator();
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public Rectangle2D draw(Graphics2D g2, Rectangle2D available, ChartRenderingInfo info) {

            boolean horizontal = RectangleEdge.isLeftOrRight(getPosition());
            boolean inverted = RectangleEdge.isTopOrBottom(getPosition());
            RectangleInsets insets = getFrame().getInsets();
            String title = null;

            if ((legendItems != null) && (legendItems.getItemCount() > 0)) {

                DrawableLegendItem legendTitle = null;

                Rectangle2D legendArea = new Rectangle2D.Double();
                double availableWidth = available.getWidth();
                double availableHeight = available.getHeight();

                // the translation point for the origin of the drawing system
                Point2D translation = new Point2D.Double();

                // Create buffer for individual rectangles within the legend
                DrawableLegendItem[] items = new DrawableLegendItem[legendItems.getItemCount()];

                // Compute individual rectangles in the legend, translation point as well
                // as the bounding box for the legend.
                if (horizontal) {

                    double xstart = available.getX() ;
                    double xlimit = available.getMaxX();
                    double maxRowWidth = 0;
                    double xoffset = 0;
                    double rowHeight = 0;
                    double totalHeight = 0;
                    boolean startingNewRow = true;


                    if (title != null && !title.equals("")) {
                        g2.setFont(CustomLegend.DEFAULT_TITLE_FONT);
                        LegendItem titleItem = new LegendItem(title,title,title,title,AbstractRenderer.DEFAULT_SHAPE,
                                Color.black,AbstractRenderer.DEFAULT_OUTLINE_STROKE,
                                AbstractRenderer.DEFAULT_OUTLINE_PAINT);

                        legendTitle = createDrawableLegendItem(g2, titleItem,xoffset,totalHeight);
                        rowHeight = Math.max(rowHeight, legendTitle.getHeight());
                        xoffset += legendTitle.getWidth();
                    }

                    g2.setFont(LegendTitle.DEFAULT_ITEM_FONT);
                    for (int i = 0; i < legendItems.getItemCount(); i++) {
                        items[i] = createDrawableLegendItem(g2, legendItems.get(i),
                                xoffset, totalHeight);
                        if ((!startingNewRow)
                                && (items[i].getX() + items[i].getWidth() + xstart > xlimit)) {

                            maxRowWidth = Math.max(maxRowWidth, xoffset);
                            xoffset = 0;
                            totalHeight += rowHeight;
                            i--;
                            startingNewRow = true;

                        } else {
                            rowHeight = Math.max(rowHeight, items[i].getHeight());
                            xoffset += items[i].getWidth();
                            startingNewRow = false;
                        }
                    }

                    maxRowWidth = Math.max(maxRowWidth, xoffset);
                    totalHeight += rowHeight;

                    // Create the bounding box
                    legendArea = new Rectangle2D.Double(0, 0, maxRowWidth, totalHeight);

                    // The yloc point is the variable part of the translation point
                    // for horizontal legends. xloc is constant.

                    double yloc = (inverted)
                            ? available.getMaxY() - totalHeight
                            - insets.calculateBottomOutset(availableHeight)
                            : available.getY() +  insets.calculateTopOutset(availableHeight);
                    double xloc = available.getX() + available.getWidth() / 2 - maxRowWidth / 2;

                    // Create the translation point
                    translation = new Point2D.Double(xloc, yloc);
                } else {  // vertical...
                    double totalHeight = 0;
                    double maxWidth = 0;

                    if (title != null && !title.equals("")) {

                        g2.setFont(CustomLegend.DEFAULT_TITLE_FONT);
                        LegendItem titleItem = new LegendItem(title,title,title,title,AbstractRenderer.DEFAULT_SHAPE,
                                Color.black,AbstractRenderer.DEFAULT_OUTLINE_STROKE,
                                AbstractRenderer.DEFAULT_OUTLINE_PAINT);
                        legendTitle = createDrawableLegendItem(g2, titleItem, 0,totalHeight);
                        totalHeight += legendTitle.getHeight();
                        maxWidth = Math.max(maxWidth, legendTitle.getWidth());
                    }

                    g2.setFont(LegendTitle.DEFAULT_ITEM_FONT);
                    for (int i = 0; i < items.length; i++) {
                        items[i] = createDrawableLegendItem(g2, legendItems.get(i),
                                0, totalHeight);
                        totalHeight += items[i].getHeight();
                        maxWidth = Math.max(maxWidth, items[i].getWidth());
                    }

                    // Create the bounding box
                    legendArea = new Rectangle2D.Float(0, 0, (float) maxWidth, (float) totalHeight);

                    // The xloc point is the variable part of the translation point
                    // for vertical legends. yloc is constant.
                    double xloc = (inverted)
                            ? available.getMaxX() - maxWidth - insets.calculateRightOutset(availableWidth)
                            : available.getX() + insets.calculateLeftOutset(availableWidth);
                    double yloc = available.getY() + (available.getHeight() / 2) - (totalHeight / 2);

                    // Create the translation point
                    translation = new Point2D.Double(xloc, yloc);
                }

                // Move the origin of the drawing to the appropriate location
                g2.translate(translation.getX(), translation.getY());

                // Draw the legend's bounding box
                g2.setPaint(CustomLegend.DEFAULT_BACKGROUND_PAINT);
                g2.fill(legendArea);
                g2.setPaint(CustomLegend.DEFAULT_OUTLINE_PAINT);
                g2.setStroke(CustomLegend.DEFAULT_OUTLINE_STROKE);
                g2.draw(legendArea);

                // draw legend title
                if (legendTitle != null) {
                    // XXX dsm - make title bold?
                    g2.setPaint(legendTitle.getItem().getFillPaint());
                    g2.setPaint(Color.black);
                    g2.setFont(CustomLegend.DEFAULT_TITLE_FONT);
                    g2.drawString(legendTitle.getItem().getLabel(),
                            (float) legendTitle.getLabelPosition().getX(),
                            (float) legendTitle.getLabelPosition().getY());
                }

                // Draw individual series elements
                for (int i = 0; i < items.length; i++) {
                    g2.setPaint(items[i].getItem().getFillPaint());
                    Shape keyBox = items[i].getMarker();
                    g2.fill(keyBox);
                    g2.setPaint(Color.black);
                    g2.setFont(LegendTitle.DEFAULT_ITEM_FONT);
                    g2.drawString(items[i].getItem().getLabel(),
                            (float) items[i].getLabelPosition().getX(),
                            (float) items[i].getLabelPosition().getY());
                }

                // translate the origin back to what it was prior to drawing the legend
                g2.translate(-translation.getX(), -translation.getY());

                if (horizontal) {
                    // The remaining drawing area bounding box will have the same
                    // x origin, width and height independent of the anchor's
                    // location. The variable is the y coordinate. If the anchor is
                    // SOUTH, the y coordinate is simply the original y coordinate
                    // of the available area. If it is NORTH, we adjust original y
                    // by the total height of the legend and the initial gap.
                    double yy = available.getY();
                    double yloc = (inverted) ? yy
                            : yy + legendArea.getHeight()
                            + insets.calculateBottomOutset(availableHeight);

                    // return the remaining available drawing area
                    return new Rectangle2D.Double(available.getX(), yloc, availableWidth,
                            availableHeight - legendArea.getHeight()
                                    - insets.calculateTopOutset(availableHeight)
                                    - insets.calculateBottomOutset(availableHeight));
                } else {
                    // The remaining drawing area bounding box will have the same
                    // y  origin, width and height independent of the anchor's
                    // location. The variable is the x coordinate. If the anchor is
                    // EAST, the x coordinate is simply the original x coordinate
                    // of the available area. If it is WEST, we adjust original x
                    // by the total width of the legend and the initial gap.
                    double xloc = (inverted) ? available.getX()
                            : available.getX()
                            + legendArea.getWidth()
                            + insets.calculateLeftOutset(availableWidth)
                            + insets.calculateRightOutset(availableWidth);


                    // return the remaining available drawing area
                    return new Rectangle2D.Double(xloc, available.getY(),
                            availableWidth - legendArea.getWidth()
                                    - insets.calculateLeftOutset(availableWidth)
                                    - insets.calculateRightOutset(availableWidth),
                            availableHeight);
                }
            } else {
                return available;
            }
        }

        private DrawableLegendItem createDrawableLegendItem(Graphics2D graphics,
                                                            LegendItem legendItem,
                                                            double x, double y) {

            int innerGap = 2;
            FontMetrics fm = graphics.getFontMetrics();
            LineMetrics lm = fm.getLineMetrics(legendItem.getLabel(), graphics);
            float textAscent = lm.getAscent();
            float lineHeight = textAscent + lm.getDescent() + lm.getLeading();

            DrawableLegendItem item = new DrawableLegendItem(legendItem);

            float xloc = (float) (x + innerGap + 1.15f * lineHeight);
            float yloc = (float) (y + innerGap + 0.15f * lineHeight + textAscent);

            item.setLabelPosition(new Point2D.Float(xloc, yloc));

            float boxDim = lineHeight * 0.70f;
            xloc = (float) (x + innerGap + 0.15f * lineHeight);
            yloc = (float) (y + innerGap + 0.15f * lineHeight);

            item.setMarker(new Rectangle2D.Float(xloc, yloc, boxDim, boxDim));

            float width = (float) (item.getLabelPosition().getX() - x
                    + fm.getStringBounds(legendItem.getLabel(), graphics).getWidth()
                    + 0.5 * textAscent);

            float height = (2 * innerGap + lineHeight);
            item.setBounds(x, y, width, height);
            return item;

        }
    }

    protected static class ExtendedPieDataset extends AbstractDataset implements PieDataset {

        private List data = new ArrayList();

        class Value {
            public Comparable key;
            public Number value;
            public Paint paint = null;
            public double explodePercent = 0;
        }

        public void setValue(Comparable key, Number value, Paint paint, double explode) {
            Value v = new Value();
            v.key = key;
            v.value = value;
            v.paint = paint;
            v.explodePercent = explode;

            int keyIndex = getIndex(key);
            if (keyIndex >= 0) {
                data.set(keyIndex, v);
            } else {
                data.add(v);
            }

            fireDatasetChanged();
        }


        public Comparable getKey(int index) {
            return ((Value) data.get(index)).key;
        }

        public int getIndex(Comparable key) {

            int result = -1;
            int i = 0;
            Iterator iterator = this.data.iterator();
            while (iterator.hasNext()) {
                Value v = (Value) iterator.next();
                if (v.key.equals(key)) {
                    result = i;
                }
                i++;
            }
            return result;

        }

        public List getKeys() {
            List keys = new ArrayList();
            for (Iterator i = data.iterator(); i.hasNext();)
                keys.add(((Value) i.next()).key);
            return keys;
        }

        public Number getValue(Comparable key) {
            int i = getIndex(key);
            if (i != -1)
                return ((Value) data.get(i)).value;
            else
                return null;
        }

        public int getItemCount() {
            return data.size();
        }

        public Number getValue(int item) {
            return ((Value) data.get(item)).value;
        }

        public Paint getPaint(int item) {
            return ((Value) data.get(item)).paint;
        }

        public double getExplodePercent(int item) {
            return ((Value) data.get(item)).explodePercent;
        }

    }

    protected static class ItemPaintCategoryDataset extends DefaultCategoryDataset {
        private List rowValues = new ArrayList();

        public void addValue(double value, Paint paint, Comparable rowKey, Comparable columnKey) {
            super.addValue(value, rowKey, columnKey);
            addPaintValue(paint, rowKey, columnKey);
        }

        public void addValue(Number value, Paint paint, Comparable rowKey, Comparable columnKey) {
            super.addValue(value, rowKey, columnKey);
            addPaintValue(paint, rowKey, columnKey);
        }

        public Paint getItemPaint(int rowIndex, int columnIndex) {
            try {
                List columnValues = (List) rowValues.get(rowIndex);
                return (Paint) columnValues.get(columnIndex);
            } catch (IndexOutOfBoundsException e) {
                return null;
            }
        }

        private void addPaintValue(Paint paint, Comparable rowKey, Comparable columnKey) {
            int rowIndex = getRowIndex(rowKey);
            int colIndex = getColumnIndex(columnKey);
            List colValues;
            if(rowIndex < rowValues.size())
                colValues = (List) rowValues.get(rowIndex);
            else {
                colValues = new ArrayList();
                rowValues.add(rowIndex, colValues);
            }
            colValues.add(colIndex, paint);
        }
    }

    protected static Color getRGBColor(String rgb) {
        try {
            return new Color(Integer.parseInt(rgb.substring(1), 16));
        } catch (NumberFormatException e) {
            throw new OXFException("Can't parse RGB color: " + rgb, e);
        }
    }

    protected static class RestrictedNumberAxis extends NumberAxis {

        /**
         * Creates a Number axis with the specified label.
         *
         * @param label  the axis label (<code>null</code> permitted).
         */
        public RestrictedNumberAxis(String label) {
            super(label);
        }

        public void setMaxTicks(int maxTicks) {
            super.setTickUnit(new NumberTickUnit(getRange().getLength() / maxTicks));
        }
    }
}
