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
package org.orbeon.oxf.processor.generator;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ResponseWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;

public class NewServletIncludeGenerator extends ProcessorImpl {

    private static Logger logger = LoggerFactory.createLogger(ServletIncludeGenerator.class);

    public static final String SERVLET_INCLUDE_NAMESPACE_URI = "http://www.orbeon.org/oxf/servlet-include";

    public NewServletIncludeGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, SERVLET_INCLUDE_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(NewServletIncludeGenerator.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

                // Read config
                final Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Document configNode = readInputAsDOM4J(context, input);
                        String servletName = XPathUtils.selectStringValueNormalize(configNode, "/config/servlet-name");
                        String path = XPathUtils.selectStringValueNormalize(configNode, "/config/path");
                        String contextPath = XPathUtils.selectStringValueNormalize(configNode, "/config/context-uripath");

                        // Get Tidy config (will only apply if content-type is text/html)
                        TidyConfig tidyConfig = new TidyConfig(XPathUtils.selectSingleNode(configNode, "/config/tidy-options"));

                        return new Config(servletName, path, contextPath, tidyConfig);
                    }
                });

                ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                if (externalContext == null)
                    throw new OXFException("Can't find external context in pipeline context");

                // Include the result as XML
                ServletIncludeResponseWrapper wrapper = new ServletIncludeResponseWrapper(externalContext.getResponse());
                try {
                    getRequestDispatcher(externalContext, config).include(externalContext.getRequest(), wrapper);
                } catch (IOException e) {
                    throw new OXFException(e);
                }

                // Parse the result
                wrapper.parse(xmlReceiver, config.getTidyConfig());
            }

        };
        addOutput(name, output);
        return output;
    }

    private ExternalContext.RequestDispatcher getRequestDispatcher(ExternalContext externalContext, Config config) {

        String name = "";
        if (config.getContextPath() != null) {
            name += config.getContextPath() + '/';
        }

        ExternalContext.RequestDispatcher requestDispatcher = null;
        if (config.getServletName() != null) {
            requestDispatcher = externalContext.getNamedDispatcher(config.getServletName());
            name += config.getServletName();
        } else if (config.getPath() != null) {
            requestDispatcher = externalContext.getRequestDispatcher(config.getPath(), true);
            name += config.getPath();
        } else
            throw new OXFException("servlet-name or path must be present in config");

        logger.info("Including from: " + name);

        return requestDispatcher;
    }

    private static class Config {
        private String servletName;
        private String path;
        private String contextPath;
        private TidyConfig tidyConfig;

        public Config(String contextPath, String path, String servletName, TidyConfig tidyConfig) {
            this.contextPath = contextPath;
            this.path = path;
            this.servletName = servletName;
            this.tidyConfig = tidyConfig;
        }

        public String getContextPath() {
            return contextPath;
        }

        public String getPath() {
            return path;
        }

        public String getServletName() {
            return servletName;
        }

        public TidyConfig getTidyConfig() {
            return tidyConfig;
        }
    }
}

class ServletIncludeResponseWrapper extends ResponseWrapper {
    private PrintWriter printWriter;
    private StreamInterceptor streamInterceptor = new StreamInterceptor();

    public ServletIncludeResponseWrapper(ExternalContext.Response response) {
        super(response);
    }

    @Override
    public OutputStream getOutputStream() {
        return streamInterceptor.getOutputStream();
    }

    @Override
    public PrintWriter getWriter() {
        if (printWriter == null)
            printWriter = new PrintWriter(streamInterceptor.getWriter());
        return printWriter;
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void reset() {
    }

    @Override
    public void sendError(int len) {
        // NOTE: Should do something?
    }

    @Override
    public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal, boolean isNoRewrite) throws IOException {
    }

    @Override
    public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
    }

    @Override
    public void setResourceCaching(long lastModified, long expires) {
    }

    @Override
    public void setContentLength(int len) {
    }

    @Override
    public void setContentType(String contentType) {
        streamInterceptor.setEncoding(NetUtils.getContentTypeCharset(contentType));
        streamInterceptor.setContentType(NetUtils.getContentTypeMediaType(contentType));
    }

    @Override
    public void setHeader(String name, String value) {
    }

    @Override
    public void addHeader(String name, String value) {
    }

    @Override
    public void setStatus(int status) {
    }

    @Override
    public void setTitle(String title) {
    }

    public void parse(XMLReceiver xmlReceiver) {
        parse(xmlReceiver, null);
    }

    public void parse(XMLReceiver xmlReceiver, TidyConfig tidyConfig) {
        streamInterceptor.parse(xmlReceiver, tidyConfig, false);
    }
}
