/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.serializer;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardPieItemLabelGenerator;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.*;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XPathUtils;

import java.awt.*;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JFreeChartSerializer extends HttpBinarySerializer {

    private static Logger logger = LoggerFactory.createLogger(JFreeChartSerializer.class);

    public static String DEFAULT_CONTENT_TYPE = "image/png";
    public static final String INPUT_CHART = "chart";

    public static final Color DEFAULT_BACKGROUND_COLOR = getRGBColor("#FFFFFF");
    public static final Color DEFAULT_TITLE_COLOR = getRGBColor("#000000");

    public JFreeChartSerializer() {
        super();
        addInputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(INPUT_CHART, CachedSerializer.JFCHART_SERIALIZER_CONFIG_NAMESPACE_URI));
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext context, ProcessorInput input, Config config, OutputStream outputStream) {
        try {
            ChartConfig chartConfig = (ChartConfig) readCacheInputAsObject(context, getInputByName(INPUT_CHART),
                    new org.orbeon.oxf.processor.CacheableInputReader() {
                        public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, org.orbeon.oxf.processor.ProcessorInput input) {
                            return createChartConfig(context, input);
                        }
                    });
            Document data = readInputAsDOM4J(context, input);

            Dataset ds;
            if (chartConfig.getType() == ChartConfig.PIE_TYPE ||
                    chartConfig.getType() == ChartConfig.PIE3D_TYPE)
                ds = createPieDataset(chartConfig, data);
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
        chart.setxSize(XPathUtils.selectIntegerValue(doc, "/chart/x-size").intValue());
        chart.setySize(XPathUtils.selectIntegerValue(doc, "/chart/y-size").intValue());

        String bgColor = XPathUtils.selectStringValueNormalize(doc, "/chart/background-color");
        String tColor = XPathUtils.selectStringValueNormalize(doc, "/chart/title-color");
        String tickUnit = XPathUtils.selectStringValueNormalize(doc, "/chart/tick-unit");
        String categoryLabelAngle = XPathUtils.selectStringValueNormalize(doc, "/chart/category-label-angle");


        chart.setBackgroundColor(bgColor == null ? DEFAULT_BACKGROUND_COLOR : getRGBColor(bgColor));
        chart.setTitleColor(tColor == null ? DEFAULT_TITLE_COLOR : getRGBColor(tColor));

        if (tickUnit != null)
            chart.setValueTickUnit(Double.parseDouble(tickUnit));

        if (categoryLabelAngle != null) {
            double angle = Double.parseDouble(categoryLabelAngle);
            chart.setCategoryLabelPosition(CategoryLabelPositions.createUpRotationLabelPositions(angle * (Math.PI / 180)));
        }


        String margin = XPathUtils.selectStringValueNormalize(doc, "/chart/bar-margin");
        if (margin != null)
            chart.setBarMargin(Double.parseDouble(margin));

        // legend
        CustomLegend legend = new CustomLegend();
        Boolean legendVis = XPathUtils.selectBooleanValue(doc, "/chart/legend/@visible = 'true'");
        legend.setVisible(legendVis.booleanValue());

        String pos = XPathUtils.selectStringValueNormalize(doc, "/chart/legend/@position");
        if ("north".equals(pos))
            legend.setAnchor(Legend.NORTH);
        else if ("east".equals(pos))
            legend.setAnchor(Legend.EAST);
        else if ("south".equals(pos))
            legend.setAnchor(Legend.SOUTH);
        else if ("west".equals(pos))
            legend.setAnchor(Legend.WEST);

        for (Iterator i = XPathUtils.selectIterator(doc, "/chart/legend/item"); i.hasNext();) {
            Element el = (Element) i.next();
            Color color = getRGBColor(el.attributeValue("color"));
            String label = el.attributeValue("label");
            legend.addItem(label, color);
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
        CategoryAxis categoryAxis = new CategoryAxis(chartConfig.getCategoryTitle());
        categoryAxis.setCategoryLabelPositions(chartConfig.getCategoryLabelPosition());
        ValueAxis valueAxis = new NumberAxis(chartConfig.getSerieTitle());


        CategoryItemRenderer renderer = null;
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

                plot = new CategoryPlot((CategoryDataset) ds, categoryAxis, valueAxis, renderer);

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
                plot = new CategoryPlot((CategoryDataset) ds, categoryAxis, valueAxis, renderer);

                if(chartConfig.getType() == ChartConfig.STACKED_VERTICAL_BAR_TYPE)
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.VERTICAL);
                else
                    ((CategoryPlot) plot).setOrientation(PlotOrientation.HORIZONTAL);

                break;
            case ChartConfig.LINE_TYPE:
                renderer = (ds instanceof ItemPaintCategoryDataset) ? new LineAndShapeRenderer(LineAndShapeRenderer.SHAPES_AND_LINES) {
                    public Paint getItemPaint(int row, int column) {
                        Paint p = ((ItemPaintCategoryDataset) ds).getItemPaint(row, column);
                        if (p != null)
                            return p;
                        else
                            return getSeriesPaint(row);
                    }
                } : new LineAndShapeRenderer(LineAndShapeRenderer.SHAPES_AND_LINES);
                plot = new CategoryPlot((CategoryDataset) ds, categoryAxis, valueAxis, renderer);
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
                plot = new CategoryPlot((CategoryDataset) ds, categoryAxis, valueAxis, renderer);
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
                plot = new CategoryPlot((CategoryDataset) ds, categoryAxis, valueAxis, renderer);

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
                plot = new CategoryPlot((CategoryDataset) ds, categoryAxis, valueAxis, renderer);

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
                pp.setLabelGenerator(new StandardPieItemLabelGenerator());

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
            default:
                throw new OXFException("Chart Type not supported");
        }

        if (categoryAxis != null) {
            categoryAxis.setLabelPaint(chartConfig.getTitleColor());
            categoryAxis.setTickLabelPaint(chartConfig.getTitleColor());
            categoryAxis.setTickMarkPaint(chartConfig.getTitleColor());
            if (chartConfig.getCategoryMargin() != 0)
                categoryAxis.setCategoryMargin(chartConfig.getCategoryMargin());
        }

        if (valueAxis != null) {
            valueAxis.setLabelPaint(chartConfig.getTitleColor());
            valueAxis.setTickLabelPaint(chartConfig.getTitleColor());
            valueAxis.setTickMarkPaint(chartConfig.getTitleColor());
            if (valueAxis instanceof NumberAxis && chartConfig.getValueTickUnit() != 0)
                ((NumberAxis) valueAxis).setTickUnit(new NumberTickUnit(chartConfig.getValueTickUnit()));

        }

        if (renderer != null) {
            renderer.setLabelGenerator(new StandardCategoryItemLabelGenerator());
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

        chart = new JFreeChart(plot);
        chart.setBackgroundPaint(chartConfig.getBackgroundColor());
        chart.setTitle(new TextTitle(chartConfig.getTitle(), TextTitle.DEFAULT_FONT, chartConfig.getTitleColor()));

        CustomLegend legend = chartConfig.getLegendConfig();
        if (legend.isVisible())
            if (legend.hasItems()) {
                chart.setLegend(legend);
            } else {
                Legend l = Legend.createInstance(chart);
                l.setAnchor(legend.getAnchor());
                chart.setLegend(l);
            }

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


        private int type;
        private String title;
        private String map;
        private String categoryTitle;
        private String serieTitle;
        private double categoryMargin;
        private Color titleColor;
        private Color backgroundColor;
        private double barMargin;
        private double valueTickUnit = 0;
        private CategoryLabelPositions categoryLabelPosition = CategoryLabelPositions.STANDARD;
        private CustomLegend legendConfig;

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

        public double getValueTickUnit() {
            return valueTickUnit;
        }

        public void setValueTickUnit(double valueTickUnit) {
            this.valueTickUnit = valueTickUnit;
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

    protected static class CustomLegend extends Legend {
        private boolean visible = true;
        private LegendItemCollection legendItems = new LegendItemCollection();;

        public CustomLegend() {
            super(null);
        }

        public void addItem(String label, Color color) {
            legendItems.add(new LegendItem(label, label,
                    AbstractRenderer.DEFAULT_SHAPE,
                    color,
                    AbstractRenderer.DEFAULT_OUTLINE_PAINT,
                    AbstractRenderer.DEFAULT_STROKE));
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
            boolean horizontal = (getAnchor() & HORIZONTAL) != 0;
            boolean inverted = (getAnchor() & INVERTED) != 0;
            ;
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
                    double xstart = available.getX() + StandardLegend.DEFAULT_OUTER_GAP.getLeftSpace(availableWidth);
                    double xlimit = available.getMaxX()
                            + StandardLegend.DEFAULT_OUTER_GAP.getRightSpace(availableWidth) - 1;
                    double maxRowWidth = 0;
                    double xoffset = 0;
                    double rowHeight = 0;
                    double totalHeight = 0;
                    boolean startingNewRow = true;


                    if (title != null && !title.equals("")) {

                        g2.setFont(StandardLegend.DEFAULT_TITLE_FONT);

                        LegendItem titleItem = new LegendItem(title,
                                title,
                                null,
                                Color.black,
                                StandardLegend.DEFAULT_OUTLINE_PAINT,
                                StandardLegend.DEFAULT_OUTLINE_STROKE);

                        legendTitle = createDrawableLegendItem(g2, titleItem,
                                xoffset,
                                totalHeight);

                        rowHeight = Math.max(rowHeight, legendTitle.getHeight());
                        xoffset += legendTitle.getWidth();
                    }

                    g2.setFont(StandardLegend.DEFAULT_ITEM_FONT);
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
                            - StandardLegend.DEFAULT_OUTER_GAP.getBottomSpace(availableHeight)
                            : available.getY() + StandardLegend.DEFAULT_OUTER_GAP.getTopSpace(availableHeight);
                    double xloc = available.getX() + available.getWidth() / 2 - maxRowWidth / 2;

                    // Create the translation point
                    translation = new Point2D.Double(xloc, yloc);
                } else {  // vertical...
                    double totalHeight = 0;
                    double maxWidth = 0;

                    if (title != null && !title.equals("")) {

                        g2.setFont(StandardLegend.DEFAULT_TITLE_FONT);

                        LegendItem titleItem = new LegendItem(title,
                                title,
                                null,
                                Color.black,
                                StandardLegend.DEFAULT_OUTLINE_PAINT,
                                StandardLegend.DEFAULT_OUTLINE_STROKE);

                        legendTitle = createDrawableLegendItem(g2, titleItem, 0,
                                totalHeight);

                        totalHeight += legendTitle.getHeight();
                        maxWidth = Math.max(maxWidth, legendTitle.getWidth());
                    }

                    g2.setFont(StandardLegend.DEFAULT_ITEM_FONT);
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
                            ? available.getMaxX() - maxWidth - StandardLegend.DEFAULT_OUTER_GAP.getRightSpace(availableWidth)
                            : available.getX() + StandardLegend.DEFAULT_OUTER_GAP.getLeftSpace(availableWidth);
                    double yloc = available.getY() + (available.getHeight() / 2) - (totalHeight / 2);

                    // Create the translation point
                    translation = new Point2D.Double(xloc, yloc);
                }

                // Move the origin of the drawing to the appropriate location
                g2.translate(translation.getX(), translation.getY());

                // Draw the legend's bounding box
                g2.setPaint(StandardLegend.DEFAULT_BACKGROUND_PAINT);
                g2.fill(legendArea);
                g2.setPaint(StandardLegend.DEFAULT_OUTLINE_PAINT);
                g2.setStroke(StandardLegend.DEFAULT_OUTLINE_STROKE);
                g2.draw(legendArea);

                // draw legend title
                if (legendTitle != null) {
                    // XXX dsm - make title bold?
                    g2.setPaint(legendTitle.getItem().getPaint());
                    g2.setPaint(Color.black);
                    g2.setFont(StandardLegend.DEFAULT_TITLE_FONT);
                    g2.drawString(legendTitle.getItem().getLabel(),
                            (float) legendTitle.getLabelPosition().getX(),
                            (float) legendTitle.getLabelPosition().getY());
                }

                // Draw individual series elements
                for (int i = 0; i < items.length; i++) {
                    g2.setPaint(items[i].getItem().getPaint());
                    Shape keyBox = items[i].getMarker();
                    g2.fill(keyBox);
                    g2.setPaint(Color.black);
                    g2.setFont(StandardLegend.DEFAULT_ITEM_FONT);
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
                            + StandardLegend.DEFAULT_OUTER_GAP.getBottomSpace(availableHeight);

                    // return the remaining available drawing area
                    return new Rectangle2D.Double(available.getX(), yloc, availableWidth,
                            availableHeight - legendArea.getHeight()
                            - StandardLegend.DEFAULT_OUTER_GAP.getTopSpace(availableHeight)
                            - StandardLegend.DEFAULT_OUTER_GAP.getBottomSpace(availableHeight));
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
                            + StandardLegend.DEFAULT_OUTER_GAP.getLeftSpace(availableWidth)
                            + StandardLegend.DEFAULT_OUTER_GAP.getRightSpace(availableWidth);


                    // return the remaining available drawing area
                    return new Rectangle2D.Double(xloc, available.getY(),
                            availableWidth - legendArea.getWidth()
                            - StandardLegend.DEFAULT_OUTER_GAP.getLeftSpace(availableWidth)
                            - StandardLegend.DEFAULT_OUTER_GAP.getRightSpace(availableWidth),
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
}
