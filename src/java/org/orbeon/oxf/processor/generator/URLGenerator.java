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
package org.orbeon.oxf.processor.generator;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.oxf.Handler;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.SAXParser;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Generates SAX events from a document fetched from an URL.
 *
 * NOTE: For XML content-type and encoding related questions, check out the following draft
 * document:
 *
 *   http://www.faqs.org/rfcs/rfc3023.html
 *   http://www.ietf.org/internet-drafts/draft-murata-kohn-lilley-xml-00.txt
 */
public class URLGenerator extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(URLGenerator.class);

    private static final String DEFAULT_TEXT_ENCODING = "iso-8859-1";

    private static final boolean DEFAULT_VALIDATING = false;

    private static final boolean DEFAULT_FORCE_CONTENT_TYPE = false;
    private static final boolean DEFAULT_FORCE_ENCODING = false;
    private static final boolean DEFAULT_IGNORE_CONNECTION_ENCODING = false;

    private static final int CACHE_EXPIRATION_NO_CACHE = 0;
    private static final int CACHE_EXPIRATION_NO_EXPIRATION = -1;
    private static final int CACHE_EXPIRATION_LAST_MODIFIED = -2;

    private static final boolean DEFAULT_CACHE_USE_LOCAL_CACHE = true;
    private static final boolean DEFAULT_CACHE_ALWAYS_REVALIDATE = true;
    private static final int DEFAULT_CACHE_EXPIRATION = CACHE_EXPIRATION_LAST_MODIFIED;

    private static final String DEFAULT_TEXT_DOCUMENT_ELEMENT = "document";
    private static final String DEFAULT_TEXT_LINE_ELEMENT = "line";

    private static final String DEFAULT_BINARY_DOCUMENT_ELEMENT = "document";

    public static final String URL_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/url";
    public static final String VALIDATING_PROPERTY = "validating";

    private Config config;

    public URLGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, URL_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(String url) {
        try {
            this.config = new Config(URLFactory.createURL(url));
            addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    public URLGenerator(URL url) {
        this.config = new Config(url);
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(URL url, String contentType, boolean forceContentType) {
        this.config = new Config(url, contentType, forceContentType);
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    private static class Config {
        private URL url;
        private String contentType = ProcessorUtils.DEFAULT_CONTENT_TYPE;
        private boolean forceContentType = DEFAULT_FORCE_CONTENT_TYPE;
        private String encoding;
        private boolean forceEncoding = DEFAULT_FORCE_ENCODING;
        private boolean ignoreConnectionEncoding = DEFAULT_IGNORE_CONNECTION_ENCODING;
        private boolean validating = DEFAULT_VALIDATING;
        private Map headers;

        private boolean cacheUseLocalCache = DEFAULT_CACHE_USE_LOCAL_CACHE;
        private boolean cacheAlwaysRevalidate = DEFAULT_CACHE_ALWAYS_REVALIDATE;
        private int cacheExpiration = DEFAULT_CACHE_EXPIRATION;

        private TidyConfig tidyConfig;

        public Config(URL url) {
            this.url = url;
        }

        public Config(URL url, String contentType, boolean forceContentType) {
            this(url);
            this.forceContentType = true;
            this.contentType = contentType;
            this.forceContentType = forceContentType;
        }

        public Config(URL url, String contentType, boolean forceContentType, String encoding, boolean forceEncoding,
                      boolean ignoreConnectionEncoding, boolean validating, Map headers, boolean cacheUseLocalCache,
                      boolean cacheAlwaysRevalidate, int cacheExpiration, TidyConfig tidyConfig) {
            this.url = url;
            this.contentType = contentType;
            this.forceContentType = forceContentType;
            this.encoding = encoding;
            this.forceEncoding = forceEncoding;
            this.ignoreConnectionEncoding = ignoreConnectionEncoding;
            this.validating = validating;
            this.headers = headers;

            this.cacheUseLocalCache = cacheUseLocalCache;
            this.cacheAlwaysRevalidate = cacheAlwaysRevalidate;
            this.cacheExpiration = cacheExpiration;

            this.tidyConfig = tidyConfig;
        }

        public URL getURL() {
            return url;
        }

        public String getContentType() {
            return contentType;
        }

        public boolean isForceContentType() {
            return forceContentType;
        }

        public String getEncoding() {
            return encoding;
        }

        public boolean isForceEncoding() {
            return forceEncoding;
        }

        public boolean isIgnoreConnectionEncoding() {
            return ignoreConnectionEncoding;
        }

        public TidyConfig getTidyConfig() {
            return tidyConfig;
        }

        public boolean isValidating() {
            return validating;
        }

        public Map getHeaders() {
            return headers;
        }

        public boolean isCacheUseLocalCache() {
            return cacheUseLocalCache;
        }

//        public boolean isCacheAlwaysRevalidate() {
//            return cacheAlwaysRevalidate;
//        }

//        public int getCacheExpiration() {
//            return cacheExpiration;
//        }

        public String toString() {
            return "[" + getURL().toExternalForm() + "|" + getContentType() + "|" + getEncoding() + "|"  + isValidating() + "|" + isForceContentType()
                    + "|" + isForceEncoding() + "|" + isIgnoreConnectionEncoding() + "|" + tidyConfig + "]";
        }
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {

                // Read config input into a URL, cache if possible
                Config config = URLGenerator.this.config != null ? URLGenerator.this.config :
                        (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                            public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                                Element configElement = readInputAsDOM4J(context, input).getRootElement();

                                // shortcut if the url is direct child of config
                                String url = configElement.getTextTrim();
                                if(url != null && !url.equals("") ) {
                                    try {
                                        return new Config(URLFactory.createURL(url));
                                    } catch (MalformedURLException e) {
                                        throw new OXFException(e);
                                    }
                                }

                                // We have the /config/url syntax
                                url =  XPathUtils.selectStringValueNormalize(configElement, "/config/url");

                                // Get content-type
                                String contentType = XPathUtils.selectStringValueNormalize(configElement, "/config/content-type");
                                boolean forceContentType = ProcessorUtils.selectBooleanValue(configElement, "/config/force-content-type", DEFAULT_FORCE_CONTENT_TYPE);
                                if (forceContentType && (contentType == null || contentType.equals("")))
                                    throw new OXFException("The force-content-type element requires a content-type element.");

                                // Get encoding
                                String encoding = XPathUtils.selectStringValueNormalize(configElement, "/config/encoding");
                                boolean forceEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/force-encoding", DEFAULT_FORCE_ENCODING);
                                boolean ignoreConnectionEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/ignore-connection-encoding", DEFAULT_IGNORE_CONNECTION_ENCODING);
                                if (forceEncoding && (encoding == null || encoding.equals("")))
                                    throw new OXFException("The force-encoding element requires an encoding element.");

                                // Get headers
                                Map headers = new HashMap();
                                for (Iterator i = configElement.selectNodes("/config/header").iterator(); i.hasNext();) {
                                    Element headerElement = (Element) i.next();
                                    String name = headerElement.element("name").getStringValue();
                                    String value = headerElement.element("value").getStringValue();
                                    headers.put(name, value);
                                }

                                // Validation setting: local, then properties, then hard-coded default
                                boolean defaultValidating = getPropertySet().getBoolean(VALIDATING_PROPERTY, DEFAULT_VALIDATING).booleanValue();
                                boolean validating = ProcessorUtils.selectBooleanValue(configElement, "/config/validating", defaultValidating);

                                // Cache control
                                boolean cacheUseLocalCache = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/use-local-cache", DEFAULT_CACHE_USE_LOCAL_CACHE);
                                boolean cacheAlwaysRevalidate = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/always-revalidate", DEFAULT_CACHE_ALWAYS_REVALIDATE);
                                int cacheExpiration = ProcessorUtils.selectIntValue(configElement, "/config/cache-control/expiration", DEFAULT_CACHE_EXPIRATION);

                                // Get Tidy config (will only apply if content-type is text/html)
                                TidyConfig tidyConfig = new TidyConfig(XPathUtils.selectSingleNode(configElement, "/config/tidy-options"));

                                // Create configuration object
                                try {
                                    // Use location data if present so that relative URLs can be supported
                                    LocationData locationData = getLocationData();
                                    URL fullURL = (locationData != null && locationData.getSystemID() != null)
                                        ? URLFactory.createURL(locationData.getSystemID(), url)
                                        : URLFactory.createURL(url);

                                    // Create configuration
                                    Config config = new Config(fullURL, contentType, forceContentType, encoding, forceEncoding,
                                            ignoreConnectionEncoding, validating, headers, cacheUseLocalCache, cacheAlwaysRevalidate, cacheExpiration,
                                            tidyConfig);
                                    if (logger.isDebugEnabled())
                                        logger.debug("Read configuration: " + config.toString());
                                    return config;
                                } catch (MalformedURLException e) {
                                    throw new OXFException(e);
                                }
                            }
                        });
                try {
                    // Never accept a null url
                    if (config.getURL() == null)
                        throw new OXFException("Missing configuration.");
                    // Create unique key and validity for the document
                    CacheKey key = new InternalCacheKey(URLGenerator.this, "urlDocument", config.toString());

                    // Resource from cache
                    Object cachedResource = null;
                    // Check if we can directly serve the resource from cache
//                    if (config.cacheExpiration != CACHE_EXPIRATION_LAST_MODIFIED) {
//                        // We don't use the last-modified header, but instead we use an expiration value set by the user
//                        long cacheExpiration = (config.cacheExpiration < 0) ? config.cacheExpiration : config.cacheExpiration * 1000; // time is in msb
//                        cachedResource = ObjectCache.instance().findValidWithExpiration(context, key, cacheExpiration);
//                        if (cachedResource != null)
//                            ((SAXStore) cachedResource).replay(contentHandler);
//                    }

                    if (cachedResource == null) {
                        // We were unable to just replay from cache without accessing the resource

                        // Decide whether to use read from the special oxf: handler or the generic URL handler
                        ResourceHandler handler = Handler.PROTOCOL.equals(config.getURL().getProtocol())
                                ? (ResourceHandler) new OXFResourceHandler(config)
                                : (ResourceHandler) new URLResourceHandler(config);
                        try {
                            Object validity = handler.getValidity();
                            cachedResource = ObjectCache.instance().findValid(context, key, validity);
                            if (cachedResource != null) {
                                // Just replay the cached resource
                                // NOTE: should we do this only with config.isCacheUseLocalCache() = true?
                                ((SAXStore) cachedResource).replay(contentHandler);
                            } else {
                                // We need to read the resource

                                // Find content-type to use. If the config says to force the
                                // content-type, we use the content-type provided by the user.
                                // Otherwise, we give the priority to the content-type provided by
                                // the connection, then the content-type provided by the user, then
                                // we use the default content-type (XML). The user will have to
                                // provide a content-type for example to read HTML documents with
                                // the file: protocol.
                                String contentType;
                                if (config.isForceContentType()) {
                                    contentType = config.getContentType();
                                } else {
                                    contentType = handler.getResourceContentType();
                                    if (contentType == null)
                                        contentType = config.getContentType();
                                    if (contentType == null)
                                        contentType = ProcessorUtils.DEFAULT_CONTENT_TYPE;
                                }

                                // Create store for caching if necessary
                                ContentHandler output = config.isCacheUseLocalCache() ? new SAXStore(contentHandler) : contentHandler;

                                // Read resource
                                if (ProcessorUtils.HTML_CONTENT_TYPE.equals(contentType)) {
                                    handler.readHTML(output);
                                } else if (ProcessorUtils.isXMLContentType(contentType)) {
                                    handler.readXML(output);
                                } else if (ProcessorUtils.isTextContentType(contentType)) {
                                    handler.readText(output, contentType);
                                } else {
                                    handler.readBinary(output, contentType);
                                }

                                // Cache the resource
                                if (config.isCacheUseLocalCache())
                                    ObjectCache.instance().add(context, key, validity, output);
                            }
                        } finally {
                            if (handler != null)
                                handler.destroy();
                        }
                    }
                } catch (SAXParseException spe) {
                    throw new ValidationException(spe.getMessage(), new LocationData(spe));
                } catch (ValidationException e) {
                    LocationData locationData = e.getLocationData();
                    // The system id may not be set
                    if (locationData == null || locationData.getSystemID() == null)
                        e.setLocationData(new LocationData(config.getURL().toExternalForm(), -1, -1));

                    throw e;

                } catch (OXFException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ValidationException(e, new LocationData(config.getURL().toExternalForm(), -1, -1));
                }
            }

            private Config getConfig(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                // Make sure the input is cacheable
                OutputCacheKey outputKey = getInputKey(context, getInputByName(INPUT_CONFIG));
                if (outputKey == null) return null;
                InputCacheKey key = new InputCacheKey(getInputByName(INPUT_CONFIG), outputKey);
                Object validity = getInputValidity(context, getInputByName(INPUT_CONFIG));
                if (validity == null) return null;
                // Try to find resource manager key in cache
                Config config = (Config) ObjectCache.instance().findValid(context, key, validity);
                if (logger.isDebugEnabled())
                    if (config != null)
                        logger.debug("Config found: " + config.toString());
                    else
                        logger.debug("Config not found");
                return config;
            }

            public OutputCacheKey getKeyImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                Config config = URLGenerator.this.config != null ? URLGenerator.this.config : getConfig(context);
                return (config != null) ? new OutputCacheKey(this, config.toString()) : null;
            }

            public Object getValidityImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                Config config = URLGenerator.this.config != null ? URLGenerator.this.config : getConfig(context);
                try {
                    // We need the config to do more
                    if (config == null || config.getURL() == null)
                        return null;

                    ResourceHandler handler = Handler.PROTOCOL.equals(config.getURL().getProtocol())
                            ? (ResourceHandler) new OXFResourceHandler(config)
                            : (ResourceHandler) new URLResourceHandler(config);

                    try {
                        // FIXME: this can potentially be very slow with some URLs
                        return handler.getValidity();
                    } finally {
                        if (handler != null)
                            handler.destroy();
                    }

                } catch (IOException e) {
                    return null;
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private interface ResourceHandler {
        public Object getValidity() throws IOException;
        public String getResourceContentType() throws IOException;
        public String getConnectionEncoding() throws IOException;
        public void destroy() throws IOException;
        public void readHTML(ContentHandler output) throws IOException;
        public void readText(ContentHandler output, String contentType) throws IOException;
        public void readXML(ContentHandler output) throws IOException;
        public void readBinary(ContentHandler output, String contentType) throws IOException;
    }

    private static class OXFResourceHandler implements ResourceHandler {
        private Config config;
        private String resourceManagerKey;
        private InputStream inputStream;

        public OXFResourceHandler(Config config) {
            this.config = config;
        }

        public String getResourceContentType() throws IOException {
            // We generally don't know the "connection" content-type
            return null;
        }

        public String getConnectionEncoding() throws IOException {
            // We generally don't know the "connection" encoding
            // NOTE: We could know, if the underlying protocol was for example HTTP. But we may
            // want to abstract that anyway, so that the behavior is consistent whatever the sandbox
            // is.
            return null;
        }

        public Object getValidity() throws IOException {
            getKey();
            if (logger.isDebugEnabled())
                logger.debug("OXF Protocol: Using ResourceManager for key " + getKey());

            long result = ResourceManagerWrapper.instance().lastModified(getKey());
             // Zero and negative values often have a special meaning, make sure to normalize here
            return (result <= 0) ? null : new Long(result);
        }

        public void destroy() throws IOException {
            if (inputStream != null) {
                inputStream.close();
            }
        }

        private String getExternalEncoding() throws IOException {
            if (config.isForceEncoding())
                return config.getEncoding();

            String connectionEncoding = getConnectionEncoding();
            if (!config.isIgnoreConnectionEncoding() && connectionEncoding != null)
                return connectionEncoding;

            String userEncoding = config.getEncoding();
            if (userEncoding != null)
                return userEncoding;

            return null;
        }

        public void readHTML(ContentHandler output) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            URLResourceHandler.readHTML(inputStream, config.getTidyConfig(), getExternalEncoding(), output);
        }

        public void readText(ContentHandler output, String contentType) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            URLResourceHandler.readText(inputStream, getExternalEncoding(), output, contentType);
        }

        public void readXML(ContentHandler output) throws IOException {
            if (getExternalEncoding() != null) {
                // The encoding is set externally, either forced by the user, or set by the connection
                inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
                XMLUtils.readerToSAX(new InputStreamReader(inputStream, getExternalEncoding()), config.getURL().toExternalForm(), output, config.isValidating());
            } else {
                // Regular case, the resource manager does the job and autodetects the encoding
                ResourceManagerWrapper.instance().getContentAsSAX(getKey(), output);
            }
        }

        public void readBinary(ContentHandler output, String contentType) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            URLResourceHandler.readBinary(inputStream, output, contentType);
        }

        private String getKey() {
            if (resourceManagerKey == null)
                resourceManagerKey = config.getURL().getFile();
            return resourceManagerKey;
        }
    }

    private static class URLResourceHandler implements ResourceHandler {
        private Config config;
        private URLConnection urlConn;

        public URLResourceHandler(Config config) {
            this.config = config;
        }

        public String getResourceContentType() throws IOException {
            openConnection();
            return NetUtils.getContentTypeContentType(urlConn.getContentType());
        }

        public String getConnectionEncoding() throws IOException {
            openConnection();
            return NetUtils.getContentTypeCharset(urlConn.getContentType());
        }

        public Object getValidity() throws IOException {
            openConnection();
            long lastModified = NetUtils.getLastModified(urlConn);
            // Zero and negative values often have a special meaning, make sure to normalize here
            return lastModified <= 0 ? null : new Long(lastModified);
        }

        public void destroy() throws IOException {
            // Make sure the connection is closed because when
            // getting the last modified date, the stream is
            // actually opened. When using the file: protocol, the
            // file can be locked on disk.
            if (urlConn != null) {
                urlConn.getInputStream().close();
            }
        }

        private void openConnection() throws IOException {
            if (urlConn == null) {
                urlConn = config.getURL().openConnection();
                Map headers = config.getHeaders();
                if (headers != null) {
                    for (Iterator i = headers.keySet().iterator(); i.hasNext();) {
                        String name = (String) i.next();
                        String value = (String) config.getHeaders().get(name);
                        urlConn.setRequestProperty(name, value);
                    }
                }
            }
        }

        private String getExternalEncoding() throws IOException {
            if (config.isForceEncoding())
                return config.getEncoding();

            String connectionEncoding = getConnectionEncoding();
            if (!config.isIgnoreConnectionEncoding() && connectionEncoding != null)
                return connectionEncoding;

            String userEncoding = config.getEncoding();
            if (userEncoding != null)
                return userEncoding;

            return null;
        }

        public void readHTML(ContentHandler output) throws IOException {
            openConnection();
            readHTML(urlConn.getInputStream(), config.getTidyConfig(), getExternalEncoding(), output);
        }

        public void readText(ContentHandler output, String contentType) throws IOException {
            openConnection();
            readText(urlConn.getInputStream(), getExternalEncoding(), output, contentType);
        }

        public void readBinary(ContentHandler output, String contentType) throws IOException {
            openConnection();
            readBinary(urlConn.getInputStream(), output, contentType);
        }

        public void readXML(ContentHandler output) throws IOException {
            openConnection();
            // Read the resource from the resource manager and parse it as XML
            try {
                SAXParser parser = XMLUtils.newSAXParser(config.isValidating());
                XMLReader reader = parser.getXMLReader();
                reader.setContentHandler(output);
                reader.setEntityResolver(XMLUtils.ENTITY_RESOLVER);
                reader.setErrorHandler(XMLUtils.ERROR_HANDLER);
                InputSource inputSource;
                if (getExternalEncoding() != null) {
                    // The encoding is set externally, either force by the user, or set by the connection
                    inputSource = new InputSource(new InputStreamReader(urlConn.getInputStream(), getExternalEncoding()));
                } else {
                    // This is the regular case where the XML parser autodetects the encoding
                    inputSource = new InputSource(urlConn.getInputStream());
                }
                inputSource.setSystemId(config.getURL().toExternalForm());
                reader.parse(inputSource);
            } catch (SAXException e) {
                throw new OXFException(e);
            }
        }

        public static void readHTML(InputStream is, TidyConfig tidyConfig, String encoding, ContentHandler output) {
            Tidy tidy = new Tidy();
//          tidy.setOnlyErrors(false);
            tidy.setShowWarnings(tidyConfig.isShowWarnings());
            tidy.setQuiet(tidyConfig.isQuiet());

            // Set encoding
            // If the encoding is null, we get a default
            tidy.setCharEncoding(TidyConfig.getTidyEncoding(encoding));

            // Parse and output to SAXResult
            Document document = tidy.parseDOM(is, null);
            try {
                Transformer transformer = TransformerUtils.getIdentityTransformer();
                transformer.transform(new DOMSource(document), new SAXResult(output));
            } catch (TransformerException e) {
                throw new OXFException(e);
            }
        }

        /**
         * Generate a "standard" OXF text document.
         *
         * @param is
         * @param encoding
         * @param output
         * @param contentType
         * @throws IOException
         */
        public static void readText(InputStream is, String encoding, ContentHandler output, String contentType) throws IOException {

            if (encoding == null)
                encoding = DEFAULT_TEXT_ENCODING;

            try {
                // Create attributes for root element: xsi:type, and optional content-type
                AttributesImpl attributes = new AttributesImpl();
                output.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                output.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_STRING_QNAME.getQualifiedName());
                if (contentType != null)
                    attributes.addAttribute("", "content-type", "content-type", "CDATA", contentType);

                // Write document
                output.startDocument();
                output.startElement("", DEFAULT_TEXT_DOCUMENT_ELEMENT, DEFAULT_TEXT_DOCUMENT_ELEMENT, attributes);
                XMLUtils.readerToCharacters(new InputStreamReader(is, encoding), output);
                output.endElement("", DEFAULT_TEXT_DOCUMENT_ELEMENT, DEFAULT_TEXT_DOCUMENT_ELEMENT);
                output.endDocument();

            } catch (SAXException e) {
                throw new OXFException(e);
            }
        }

        /**
         * Generate a "standard" OXF binary document.
         *
         * @param is
         * @param output
         * @param contentType
         */
        public static void readBinary(InputStream is, ContentHandler output, String contentType) {
            try {
                // Create attributes for root element: xsi:type, and optional content-type
                AttributesImpl attributes = new AttributesImpl();
                output.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                output.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName());
                if (contentType != null)
                    attributes.addAttribute("", "content-type", "content-type", "CDATA", contentType);

                // Write document
                output.startDocument();
                output.startElement("", DEFAULT_BINARY_DOCUMENT_ELEMENT, DEFAULT_BINARY_DOCUMENT_ELEMENT, attributes);
                XMLUtils.inputStreamToBase64Characters(new BufferedInputStream(is), output);
                output.endElement("", DEFAULT_BINARY_DOCUMENT_ELEMENT, DEFAULT_BINARY_DOCUMENT_ELEMENT);
                output.endDocument();

            } catch (SAXException e) {
                throw new OXFException(e);
            }
        }
    }
}