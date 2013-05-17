/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is XML RenderKit for JSF.
 *
 * The Initial Developer of the Original Code is
 * Orbeon, Inc (info@orbeon.com)
 * Portions created by the Initial Developer are Copyright (C) 2002
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 */
package org.orbeon.faces.servlet;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * This class implements a simple Servlet filter that applies a series of
 * stylesheets.
 *
 * NOTE: This is an example indended to show a functional filter. There have
 * not been many considerations of performance or memory optimization.
 */
public class Filter implements javax.servlet.Filter {

    public static final boolean DEBUG = true;

    private FilterConfig filterConfig;
    private SAXTransformerFactory saxTransformerFactory;

    private Map templatesCache = new HashMap();
    private List stylesheetNames = new ArrayList();

    /**
     * Initialize the filter.
     */
    public void init(FilterConfig config) throws ServletException {
        // Save config
        filterConfig = config;
        // Get stylesheet paths (the parameters must be named stylesheet1, stylesheet2, etc.)
        for (int i = 1; ; i++) {
            String param = config.getInitParameter("stylesheet" + i);
            if (param == null)
                break;
            stylesheetNames.add("/WEB-INF/" + param);
        }
    }

    /**
     * Add this for WebLogic 6.1 that supports a draft version of the
     * Servlet API.
     */
    public void setFilterConfig(FilterConfig config) {
        try {
            init(config);
        } catch (Exception e) {
            config.getServletContext().log("Exception while setting config", e);
            throw new RuntimeException("Exception while setting config");
        }
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    /**
     * Execute the filter.
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        // Execute the rest of the filter chain, including the JSP
        ServletResponseWrapper servletResponseWrapper = new ServletResponseWrapper(filterConfig.getServletContext(), (HttpServletResponse) response);
        chain.doFilter(request, servletResponseWrapper);

        // Get each transformer and connect it
        int index = 0;
        ContentHandler initialContentHandler = null;
        TransformerHandler priorTransformerHandler = null;
        for (Iterator i = stylesheetNames.iterator(); i.hasNext(); index++) {
            String stylesheetName = (String) i.next();
            TransformerHandler transformerHandler = getTransformerHandler(stylesheetName);
            if (index == 0) {
                // First transformation: connect the first transformer to the output of the parser
                initialContentHandler = transformerHandler;
            }
            if (index == (stylesheetNames.size() - 1)) {
                // Last transformation: set the HTML output properties
                setHTMLOutputProperties(transformerHandler.getTransformer());
                // Set the result
                transformerHandler.setResult(new StreamResult(response.getWriter()));
            }
            if (index > 0 && index < stylesheetNames.size()) {
                // Intermediate transformation: connect to the prior transformation
                priorTransformerHandler.setResult(new SAXResult(transformerHandler));
            }
            priorTransformerHandler = transformerHandler;
        }
        // Set the content-type
        response.setContentType("text/html");
        // Parse the output
        servletResponseWrapper.parse(initialContentHandler);
    }

    /**
     * Destroy the filter.
     */
    public void destroy() {
    }

    /**
     * Create a TransformerHandler and make sure the stylesheet is up to date.
     */
    private synchronized TransformerHandler getTransformerHandler(String stylesheetName) throws ServletException {
        try {
            // Create and cache the factory
            if (saxTransformerFactory == null) {
                saxTransformerFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
                saxTransformerFactory.setAttribute("http://xml.apache.org/xalan/features/incremental", Boolean.FALSE);
                saxTransformerFactory.setURIResolver(new URIResolver() {

                    public Source resolve(String href, String base) throws TransformerException {
                        try {
                            URL url = filterConfig.getServletContext().getResource("/WEB-INF/" + href);
                            URLConnection connection = url.openConnection();
                            return new SAXSource(new InputSource(connection.getInputStream()));
                        } catch (IOException e) {
                            filterConfig.getServletContext().log("Exception while resolving URL", e);
                            throw new RuntimeException(e.getMessage());
                        }
                    }
                });
            }
            // Get a Connection for the stylesheet
            URL url = filterConfig.getServletContext().getResource(stylesheetName);
            URLConnection connection = url.openConnection();
            try {
                // Check if the templates must be recreated
                long lastModified = connection.getLastModified();
                TemplatesInfo templatesInfo = (TemplatesInfo) templatesCache.get(stylesheetName);
                if (templatesInfo == null || lastModified > templatesInfo.getLastModified()) {
                    // Create a SAXSource
                    SAXSource source = new SAXSource(new InputSource(connection.getInputStream()));
                    source.setSystemId(stylesheetName);
                    // Create the templates and cache it with the date of last modification
                    templatesInfo = new TemplatesInfo(lastModified, saxTransformerFactory.newTemplates(source));
                    templatesCache.put(stylesheetName, templatesInfo);
                }
                // Create a new TransformerHandler
                TransformerHandler transformerHandler = saxTransformerFactory.newTransformerHandler(templatesInfo.getTemplates());
                return transformerHandler;
            } finally {
                if (connection != null)
                    connection.getInputStream().close();
            }
        } catch (Exception e) {
            throw new ServletException("Exception caught while getting SAX transformer", e);
        }
    }

    private void setHTMLOutputProperties(Transformer transformer) {
        // Set output properties for HTML
        transformer.setOutputProperty(OutputKeys.METHOD, "html");
        transformer.setOutputProperty(OutputKeys.VERSION, "4.0");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "-//W3C//DTD HTML 4.0 Transitional//EN");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "http://www.w3.org/TR/html4/loose.dtd");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "iso-8859-1");
        transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text/html");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        //transformer.setOutputProperty(XALAN_INDENT_AMOUNT, "4");
    }

    private static class TemplatesInfo {
        private Templates templates;
        private long lastModified;

        public TemplatesInfo(long lastModified, Templates templates) {
            this.lastModified = lastModified;
            this.templates = templates;
        }

        public long getLastModified() {
            return lastModified;
        }

        public Templates getTemplates() {
            return templates;
        }
    }
}
