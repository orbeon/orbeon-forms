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
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.serializer.store.ResultStore;
import org.orbeon.oxf.processor.serializer.store.ResultStoreOutputStream;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class HttpSerializer extends CachedSerializer {

    static private Logger logger = LoggerFactory.createLogger(HttpSerializer.class);

    protected abstract String getDefaultContentType();

    protected HttpSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, SERIALIZER_CONFIG_NAMESPACE_URI));
    }

    public void start(PipelineContext context) {
        try {

            final Config config = readConfig(context);

            ProcessorInput dataInput = getInputByName(INPUT_DATA);

            ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            ExternalContext.Response response = externalContext.getResponse();

            // Compute headers
            if (externalContext != null) {

                // Send an error if needed and return immediately
                int errorCode = config.errorCode;
                if (errorCode != DEFAULT_ERROR_CODE) {
                    response.sendError(errorCode);
                    return;
                }

                // Content type / encoding and status
                // charset info is not set if we are a BinarySerializer (PDF, Image, etc..)
                String contentTypeHeader = config.contentType;
                if(!(this instanceof HttpBinarySerializer))
                    contentTypeHeader = contentTypeHeader + "; charset=" + config.encoding;
                int statusCode = config.statusCode;

                // Get last modification date and compute last modified if possible
                Object validity = getInputValidity(context, dataInput);
                long lastModified = (validity != null) ? findLastModified(validity) : 0;

                if (logger.isDebugEnabled())
                    logger.debug("Last modified: " + lastModified);

                // Set headers
                response.setContentType(contentTypeHeader);
                response.setStatus(statusCode);
                // Set caching headers and force revalidation
                response.setCaching(lastModified, true, true);

                // Check If-Modified-Since (conditional GET) and don't return content if condition is met
                if (!response.checkIfModifiedSince(lastModified, true)) {
                    response.setStatus(ExternalContext.SC_NOT_MODIFIED);
                    if (logger.isDebugEnabled())
                        logger.debug("Sending SC_NOT_MODIFIED");
                    return;
                }

                // Set custom headers
                for(Iterator i = config.headers.iterator(); i.hasNext();){
                    String name = (String) i.next();
                    String value = (String) i.next();
                    response.setHeader(name, value);
                }
            }

            // If we have an empty body, return w/o reading the data input
            if (config.empty)
                return;

            final OutputStream httpOutputStream = response.getOutputStream();

            if (config.cacheUseLocalCache) {
                // If local caching of the data is enabled, use the caching API
                // We return a ResultStore
                final boolean[] read = new boolean[1];
                ResultStore resultStore = (ResultStore) readCacheInputAsObject(context, dataInput, new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        read[0] = true;
                        if (logger.isDebugEnabled())
                            logger.debug("Output not cached");
                        try {
                            ResultStoreOutputStream resultStoreOutputStream = new ResultStoreOutputStream(httpOutputStream);
                            readInput(context, input, config, resultStoreOutputStream);
                            resultStoreOutputStream.close();
                            return resultStoreOutputStream;
                        } catch (IOException e) {
                            throw new OXFException(e);
                        }
                    }
                });

                // If the output was obtained from the cache, just write it
                if (!read[0]) {
                    if (logger.isDebugEnabled())
                        logger.debug("Serializer output cached");
                    if (externalContext != null)
                        response.setContentLength(resultStore.length(context));
                    resultStore.replay(context);
                }
            } else {
                // Local caching is not enabled
                readInput(context, dataInput, config, httpOutputStream);
                httpOutputStream.close();
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private Config readConfig(PipelineContext context) {
        final Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG),
                new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Element configElement = readInputAsDOM4J(context, input).getRootElement();
                        try {
                            String contentType = XPathUtils.selectStringValueNormalize(configElement, "/config/content-type");
                            Integer statusCode = XPathUtils.selectIntegerValue(configElement, "/config/status-code");
                            Boolean empty = XPathUtils.selectBooleanValue(configElement, "/config/empty-content");
                            Integer errorCode = XPathUtils.selectIntegerValue(configElement, "/config/error-code");
                            String method = XPathUtils.selectStringValueNormalize(configElement, "/config/method");
                            String version = XPathUtils.selectStringValueNormalize(configElement, "/config/version");
                            String publicDoctype = XPathUtils.selectStringValueNormalize(configElement, "/config/public-doctype");
                            String systemDoctype = XPathUtils.selectStringValueNormalize(configElement, "/config/system-doctype");
                            String encoding = XPathUtils.selectStringValueNormalize(configElement, "/config/encoding");
                            String indent = XPathUtils.selectStringValueNormalize(configElement, "/config/indent");
                            Integer indentAmount = XPathUtils.selectIntegerValue(configElement, "/config/indent-amount");
                            String omitXMLDeclaration = XPathUtils.selectStringValueNormalize(configElement, "/config/omit-xml-declaration");
                            String standalone = XPathUtils.selectStringValueNormalize(configElement, "/config/standalone");

                            Config config = new Config();
                            config.contentType = contentType == null ? getDefaultContentType() : contentType;
                            config.statusCode = statusCode == null ? DEFAULT_STATUS_CODE : statusCode.intValue();
                            config.empty = empty == null ? DEFAULT_EMPTY : empty.booleanValue();
                            config.errorCode = errorCode == null ? DEFAULT_ERROR_CODE : errorCode.intValue();
                            config.method = method;
                            config.version = version;
                            config.publicDoctype = publicDoctype;
                            config.systemDoctype = systemDoctype;
                            if (encoding != null)
                                config.encoding = encoding;
                            if (indent != null)
                                config.indent = new Boolean(indent).booleanValue();
                            if (indentAmount != null)
                                config.indentAmount = indentAmount.intValue();
                            if (omitXMLDeclaration != null)
                                config.omitXMLDeclaration = new Boolean(omitXMLDeclaration).booleanValue();
                            if (standalone != null)
                                config.standalone = new Boolean(standalone).booleanValue();

                            // Cache control
                            config.cacheUseLocalCache = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/use-local-cache", DEFAULT_CACHE_USE_LOCAL_CACHE);

                            // Headers
                            for(Iterator i = XPathUtils.selectIterator(configElement, "/config/header"); i.hasNext();) {
                                Element header = (Element)i.next();
                                String name = header.element("name").getTextTrim();
                                String value = header.element("value").getTextTrim();
                                config.addHeader(name, value);
                            }

                            return config;
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }
                });
        return config;
    }


    protected Writer getWriter(OutputStream outputStream, Config config) {
        try {
            return new OutputStreamWriter(outputStream, config.encoding);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    protected static class Config {
        String contentType;
        int statusCode;
        boolean empty;
        int errorCode;
        boolean indent = DEFAULT_INDENT;
        int indentAmount = DEFAULT_INDENT_AMOUNT;
        String method;
        String version;
        String publicDoctype;
        String systemDoctype;
        String encoding = DEFAULT_ENCODING;
        boolean omitXMLDeclaration;
        boolean standalone;
        boolean cacheUseLocalCache;
        List headers = new ArrayList();

        public void addHeader(String name, String value){
            headers.add(name);
            headers.add(value);
        }
    }
}
