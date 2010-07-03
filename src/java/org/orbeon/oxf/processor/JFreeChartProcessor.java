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
package org.orbeon.oxf.processor;

import org.dom4j.Document;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.servlet.ServletUtilities;
import org.jfree.data.general.Dataset;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.*;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.processor.serializer.legacy.JFreeChartSerializer;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.portlet.PortletSession;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.*;

/**
 * NOTE: This generator depends on the Servlet API.
 */
public class JFreeChartProcessor extends JFreeChartSerializer {

    public JFreeChartProcessor() {
        super();
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }


    public ProcessorOutput createOutput(String name) {
        return new ProcessorOutputImpl(JFreeChartProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                JFreeChartSerializer.ChartConfig chartConfig = readChartConfig(context);
                ChartInfo info = createChart(context, chartConfig);
                try {
                    xmlReceiver.startDocument();
                    xmlReceiver.startElement("", "chart-info", "chart-info", new AttributesImpl());

                    xmlReceiver.startElement("", "file", "file", new AttributesImpl());
                    xmlReceiver.characters(info.file.toCharArray(), 0, info.file.length());
                    xmlReceiver.endElement("", "file", "file");

                    if (chartConfig.getMap() != null) {
                        AttributesImpl atts = new AttributesImpl();

                        atts.addAttribute("", "name", "name", "CDATA", chartConfig.getMap());
                        xmlReceiver.startElement("", "map", "map", atts);

                        EntityCollection entities = info.getInfo().getEntityCollection();

                        Iterator iterator = entities.iterator();
                        while (iterator.hasNext()) {
                            ChartEntity entity = (ChartEntity) iterator.next();
                            AttributesImpl attr = new AttributesImpl();
                            attr.addAttribute("", "shape", "shape", "CDATA", entity.getShapeType());
                            attr.addAttribute("", "coords", "coords", "CDATA", entity.getShapeCoords());

                            if (entity.getURLText() != null && !entity.getURLText().equals("")) {
                                attr.addAttribute("", "href", "href", "CDATA", entity.getURLText());
                            }
                            if (entity.getToolTipText() != null && !entity.getToolTipText().equals("")) {
                                attr.addAttribute("", "title", "title", "CDATA", entity.getToolTipText());

                            }
                            xmlReceiver.startElement("", "area", "area", attr);
                            xmlReceiver.endElement("", "area", "area");
                        }

                        xmlReceiver.endElement("", "map", "map");
                    }
                    xmlReceiver.endElement("", "chart-info", "chart-info");
                    xmlReceiver.endDocument();

                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
    }


    protected ChartInfo createChart(org.orbeon.oxf.pipeline.api.PipelineContext context, JFreeChartSerializer.ChartConfig chartConfig) {

        Document data = readInputAsDOM4J(context, getInputByName(INPUT_DATA));

        Dataset ds;
        if (chartConfig.getType() == JFreeChartSerializer.ChartConfig.PIE_TYPE ||
                chartConfig.getType() == JFreeChartSerializer.ChartConfig.PIE3D_TYPE)
            ds = createPieDataset(chartConfig, data);
        else if(chartConfig.getType() == ChartConfig.XY_LINE_TYPE)
            ds = createXYDataset(chartConfig, data);
        else if(chartConfig.getType() == ChartConfig.TIME_SERIES_TYPE)
            ds = createTimeSeriesDataset(chartConfig, data);
        else
            ds = createDataset(chartConfig, data);

        JFreeChart chart = drawChart(chartConfig, ds);
        ChartRenderingInfo info = new ChartRenderingInfo();

        String file;
        try {
            final ExternalContext.Session session =
                    ((ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT)).getSession(true);
            file = ServletUtilities.saveChartAsPNG(chart, chartConfig.getxSize(), chartConfig.getySize(), info,
                    new HttpSession() {
                        public Object getAttribute(String s) {
                            return session.getAttributesMap(PortletSession.APPLICATION_SCOPE).get(s);
                        }

                        public Enumeration getAttributeNames() {
                            return Collections.enumeration(session.getAttributesMap(PortletSession.APPLICATION_SCOPE).keySet());
                        }

                        public long getCreationTime() {
                            return session.getCreationTime();
                        }

                        public String getId() {
                            return session.getId();
                        }

                        public long getLastAccessedTime() {
                            return session.getLastAccessedTime();
                        }

                        public int getMaxInactiveInterval() {
                            return session.getMaxInactiveInterval();
                        }

                        public ServletContext getServletContext() {
                            return null;
                        }

                        public HttpSessionContext getSessionContext() {
                            return null;
                        }

                        public Object getValue(String s) {
                            return getAttribute(s);
                        }

                        public String[] getValueNames() {
                            List list  = new ArrayList();
                            for(Enumeration e = getAttributeNames(); e.hasMoreElements();){
                                list.add(e.nextElement());
                            }
                            String[] array = new String[list.size()];
                            list.toArray(array);
                            return array;
                        }

                        public void invalidate() {
                            session.invalidate();
                        }

                        public boolean isNew() {
                            return session.isNew();
                        }

                        public void putValue(String s, Object o) {
                            setAttribute(s, o);
                        }

                        public void removeAttribute(String s) {
                            session.getAttributesMap(PortletSession.APPLICATION_SCOPE).remove(s);
                        }

                        public void removeValue(String s) {
                            removeAttribute(s);
                        }

                        public void setAttribute(String s, Object o) {
                            session.getAttributesMap(PortletSession.APPLICATION_SCOPE).put(s, o);
                        }

                        public void setMaxInactiveInterval(int i) {
                            session.setMaxInactiveInterval(i);
                        }
                    });
        } catch (Exception e) {
            throw new OXFException(e);
        }
        return new ChartInfo(info, file);
    }

    protected JFreeChartSerializer.ChartConfig readChartConfig(PipelineContext context) {
        return (JFreeChartSerializer.ChartConfig) readCacheInputAsObject(context, getInputByName(INPUT_CHART),
                new CacheableInputReader() {
                    public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                        return createChartConfig(context, input);
                    }

                });
    }


    class ChartInfo {
        private ChartRenderingInfo info;
        private String file;

        public ChartInfo(ChartRenderingInfo info, String file) {
            this.info = info;
            this.file = file;
        }

        public ChartRenderingInfo getInfo() {
            return info;
        }

        public void setInfo(ChartRenderingInfo info) {
            this.info = info;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }
}