/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ResponseWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.serializer.store.ResultStoreOutputStream;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base class for all HTTP serializers.
 */
public abstract class HttpSerializerBase extends CachedSerializer {

    protected static final int DEFAULT_STATUS_CODE = ExternalContext.SC_OK;

    private static final boolean DEFAULT_FORCE_CONTENT_TYPE = false;
    private static final boolean DEFAULT_IGNORE_DOCUMENT_CONTENT_TYPE = false;

    private static final boolean DEFAULT_FORCE_ENCODING = false;
    private static final boolean DEFAULT_IGNORE_DOCUMENT_ENCODING = false;

    private static Logger logger = LoggerFactory.createLogger(HttpSerializerBase.class);

    protected HttpSerializerBase() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, getConfigSchemaNamespaceURI()));
    }

    /**
     * Return the default content type for this serializer. Must be overridden by subclasses.
     */
    protected abstract String getDefaultContentType();

    /**
     * Return the namespace URI of the schema validating the config input. Can be overridden by
     * subclasses.
     */
    protected String getConfigSchemaNamespaceURI() {
        return SERIALIZER_CONFIG_NAMESPACE_URI;
    }

    public void start(PipelineContext pipelineContext) {
        try {
            // Read configuration input
            final Config config = readConfig(pipelineContext);

            // Get data input information
            final ProcessorInput dataInput = getInputByName(INPUT_DATA);

            ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            final ExternalContext.Response response = externalContext.getResponse();

            try {
                // Compute headers
                if (externalContext != null) {

                    // Send an error if needed and return immediately
                    int errorCode = config.errorCode;
                    if (errorCode != DEFAULT_ERROR_CODE) {
                        response.sendError(errorCode);
                        return;
                    }

                    // Get last modification date and compute last modified if possible
                    // NOTE: It is not clear if this is right! We had a discussion to "remove serializer last modified,
                    // and use oxf:request-generator default validity".
                    final long lastModified = findInputLastModified(pipelineContext, dataInput, false);

                    // Set caching headers and force revalidation
                    response.setCaching(lastModified, true, true);

                    // Check if we are processing a forward. If so, we cannot tell the client that the content has not been modified.
                    final boolean isForward = URLRewriterUtils.isForwarded(externalContext.getRequest());
                    if (!isForward) {
                        // Check If-Modified-Since (conditional GET) and don't return content if condition is met
                        if (!response.checkIfModifiedSince(lastModified, true)) {
                            response.setStatus(ExternalContext.SC_NOT_MODIFIED);
                            if (logger.isDebugEnabled())
                                logger.debug("Sending SC_NOT_MODIFIED");
                            return;
                        }
                    }

                    // Set status code
                    // STATUS CODE: Processing instruction can override this when the input is being read
                    response.setStatus(config.statusCode);

                    // Set custom headers
                    if (config.headers != null) {
                        for (Iterator<String> i = config.headers.iterator(); i.hasNext();) {
                            String name = i.next();
                            String value = i.next();
                            response.setHeader(name, value);
                        }
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
                    final ExtendedResultStoreOutputStream resultStore = (ExtendedResultStoreOutputStream) readCacheInputAsObject(pipelineContext, dataInput, new CacheableInputReader() {
                        public Object read(PipelineContext pipelineContext, ProcessorInput input) {
                            read[0] = true;
                            if (logger.isDebugEnabled())
                                logger.debug("Output not cached");
                            try {
                                final ExtendedResultStoreOutputStream resultStoreOutputStream = new ExtendedResultStoreOutputStream(httpOutputStream);
                                // NOTE: readInput will call response.setContentType(), so we intercept and save the set contentType
                                // Other headers are set above
                                readInput(pipelineContext, new ResponseWrapper(response) {
                                    public void setContentType(String contentType) {
                                        resultStoreOutputStream.setContentType(contentType);
                                        super.setContentType(contentType);
                                    }
                                }, input, config, resultStoreOutputStream);
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
                        if (externalContext != null) {
                            // Set saved content type
                            final String contentType = resultStore.getContentType();
                            if (contentType != null)
                                response.setContentType(contentType);
                            // Set length since we know it
                            response.setContentLength(resultStore.length(pipelineContext));
                        }
                        // Replay content
                        resultStore.replay(pipelineContext);
                    }
                } else {
                    // Local caching is not enabled, just read the input
                    readInput(pipelineContext, response, dataInput, config, httpOutputStream);
                    httpOutputStream.close();
                }
            } catch (java.net.SocketException e) {
                // In general there is no point doing much with such exceptions. They are thrown in particular when the
                // client has closed the connection.
                logger.info("SocketException in serializer");
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    protected Config readConfig(PipelineContext context) {
        return (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG),
                new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Element configElement = readInputAsDOM4J(context, input).getRootElement();
                        try {
                            String contentType = XPathUtils.selectStringValueNormalize(configElement, "/config/content-type");
                            Integer statusCode = XPathUtils.selectIntegerValue(configElement, "/config/status-code");
                            Integer errorCode = XPathUtils.selectIntegerValue(configElement, "/config/error-code");
                            String method = XPathUtils.selectStringValueNormalize(configElement, "/config/method");
                            String version = XPathUtils.selectStringValueNormalize(configElement, "/config/version");
                            String publicDoctype = XPathUtils.selectStringValueNormalize(configElement, "/config/public-doctype");
                            String systemDoctype = XPathUtils.selectStringValueNormalize(configElement, "/config/system-doctype");
                            String encoding = XPathUtils.selectStringValueNormalize(configElement, "/config/encoding");
                            Integer indentAmount = XPathUtils.selectIntegerValue(configElement, "/config/indent-amount");

                            Config config = new Config();

                            // HTTP-specific configuration
                            config.statusCode = statusCode == null ? DEFAULT_STATUS_CODE : statusCode;
                            config.errorCode = errorCode == null ? DEFAULT_ERROR_CODE : errorCode;
                            config.contentType = contentType;
                            config.forceContentType = ProcessorUtils.selectBooleanValue(configElement, "/config/force-content-type", DEFAULT_FORCE_CONTENT_TYPE);
                            if (config.forceContentType && (contentType == null || contentType.equals("")))
                                throw new OXFException("The force-content-type element requires a content-type element.");
                            config.ignoreDocumentContentType = ProcessorUtils.selectBooleanValue(configElement, "/config/ignore-document-content-type", DEFAULT_IGNORE_DOCUMENT_CONTENT_TYPE);
                            config.encoding = encoding;
                            config.forceEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/force-encoding", DEFAULT_FORCE_ENCODING);
                            if (config.forceEncoding && (encoding == null || encoding.equals("")))
                                throw new OXFException("The force-encoding element requires an encoding element.");
                            config.ignoreDocumentEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/ignore-document-encoding", DEFAULT_IGNORE_DOCUMENT_ENCODING);
                            // Headers
                            for (Iterator i = XPathUtils.selectIterator(configElement, "/config/header"); i.hasNext();) {
                                Element header = (Element) i.next();
                                String name = header.element("name").getTextTrim();
                                String value = header.element("value").getTextTrim();
                                config.addHeader(name, value);
                            }
                            config.empty = ProcessorUtils.selectBooleanValue(configElement, "/config/empty-content", DEFAULT_EMPTY);

                            // Cache control
                            config.cacheUseLocalCache = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/use-local-cache", DEFAULT_CACHE_USE_LOCAL_CACHE);

                            // XML / HTML / Text configuration
                            config.method = method;
                            config.version = version;
                            config.publicDoctype = publicDoctype;
                            config.systemDoctype = systemDoctype;
                            config.omitXMLDeclaration = ProcessorUtils.selectBooleanValue(configElement, "/config/omit-xml-declaration", DEFAULT_OMIT_XML_DECLARATION);
                            String standaloneString = XPathUtils.selectStringValueNormalize(configElement, "/config/standalone");
                            config.standalone = (standaloneString == null) ? null : Boolean.valueOf(standaloneString);
                            config.indent = ProcessorUtils.selectBooleanValue(configElement, "/config/indent", DEFAULT_INDENT);
                            if (indentAmount != null) config.indentAmount = indentAmount;

                            return config;
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }
                });
    }

    /**
     * Represent the complete serializer configuration.
     */
    protected static class Config {
        // HTTP-specific configuration
        public int statusCode = DEFAULT_STATUS_CODE;
        public int errorCode = DEFAULT_ERROR_CODE;
        public String contentType;
        public boolean forceContentType = DEFAULT_FORCE_CONTENT_TYPE;
        public boolean ignoreDocumentContentType = DEFAULT_IGNORE_DOCUMENT_CONTENT_TYPE;
        public String encoding = DEFAULT_ENCODING;
        public boolean forceEncoding = DEFAULT_FORCE_ENCODING;
        public boolean ignoreDocumentEncoding = DEFAULT_IGNORE_DOCUMENT_ENCODING;
        public List<String> headers;
        public boolean cacheUseLocalCache = DEFAULT_CACHE_USE_LOCAL_CACHE;
        public boolean empty = DEFAULT_EMPTY;

        // XML / HTML / Text configuration
        public String method;
        public String version;
        public String publicDoctype;
        public String systemDoctype;
        public boolean omitXMLDeclaration = DEFAULT_OMIT_XML_DECLARATION;
        public Boolean standalone;
        public boolean indent = DEFAULT_INDENT;
        public int indentAmount = DEFAULT_INDENT_AMOUNT;

        public void addHeader(String name, String value) {
            if (headers == null) headers = new ArrayList<String>();
            headers.add(name);
            headers.add(value);
        }
    }

    /**
     * Implement the content type determination algorithm.
     *
     * @param config               current HTTP serializer configuration
     * @param contentTypeAttribute content type and encoding from the input XML document, or null
     * @param defaultContentType   content type to return if none can be found
     * @return content type determined
     */
    protected static String getContentType(Config config, String contentTypeAttribute, String defaultContentType) {
        if (config.forceContentType)
            return config.contentType;

        String documentContentType = NetUtils.getContentTypeMediaType(contentTypeAttribute);
        if (!config.ignoreDocumentContentType && documentContentType != null)
            return documentContentType;

        String userContentType = config.contentType;
        if (userContentType != null)
            return userContentType;

        return defaultContentType;
    }

    /**
     * Implement the encoding determination algorithm.
     *
     * @param config               current HTTP serializer configuration
     * @param contentTypeAttribute content type and encoding from the input XML document, or null
     * @param defaultEncoding      encoding to return if none can be found
     * @return encoding determined
     */
    protected static String getEncoding(Config config, String contentTypeAttribute, String defaultEncoding) {
        if (config.forceEncoding)
            return config.encoding;

        String documentEncoding = NetUtils.getContentTypeCharset(contentTypeAttribute);
        if (!config.ignoreDocumentEncoding && documentEncoding != null)
            return documentEncoding;

        String userEncoding = config.encoding;
        if (userEncoding != null)
            return userEncoding;

        return defaultEncoding;
    }

    /**
     * ResultStoreOutputStream with additional content-type storing.
     */
    private static class ExtendedResultStoreOutputStream extends ResultStoreOutputStream {

        private String contentType;

        public ExtendedResultStoreOutputStream(OutputStream out) {
            super(out);
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
