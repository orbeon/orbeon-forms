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
import org.dom4j.Element;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.handler.OXFHandler;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.w3c.tidy.Tidy;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Generates SAX events from a document fetched from an URL.
 *
 * NOTE: For XML content-type and encoding related questions, check out the following draft
 * document:
 *
 * http://www.faqs.org/rfcs/rfc3023.html
 * http://www.ietf.org/internet-drafts/draft-murata-kohn-lilley-xml-00.txt
 */
public class URLGenerator extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(URLGenerator.class);
    public static IndentedLogger indentedLogger = new IndentedLogger(logger, "oxf:url-generator");

    public static final boolean DEFAULT_VALIDATING = false;
    public static final boolean DEFAULT_HANDLE_XINCLUDE = false;
    public static final boolean DEFAULT_EXTERNAL_ENTITIES = false;
    public static final boolean DEFAULT_HANDLE_LEXICAL = true;

    private static final boolean DEFAULT_FORCE_CONTENT_TYPE = false;
    private static final boolean DEFAULT_FORCE_ENCODING = false;
    private static final boolean DEFAULT_IGNORE_CONNECTION_ENCODING = false;

    private static final boolean DEFAULT_CACHE_USE_LOCAL_CACHE = true;
    private static final boolean DEFAULT_ENABLE_CONDITIONAL_GET = false;

    public static final String URL_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/url";
    public static final String VALIDATING_PROPERTY = "validating";
    public static final String HANDLE_XINCLUDE_PROPERTY = "handle-xinclude";
    public static final String HANDLE_LEXICAL_PROPERTY = "handle-lexical";

    public static final String DEFAULT_ARCHIVE_DOCUMENT_ELEMENT  = "archive";
    public static final String DEFAULT_HEADER_ELEMENT = "header";
    public static final String DEFAULT_STATUS_CODE_ELEMENT = "code";
    public static final String DEFAULT_REQUEST_ELEMENT = "request";
    public static final String DEFAULT_METHOD_ELEMENT = "method";
    public static final String DEFAULT_LOCATION_ELEMENT = "location";
    public static final String DEFAULT_RESPONSE_ELEMENT = "response";

    private ConfigURIReferences localConfigURIReferences;

    public URLGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, URL_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(String url) {
        try {
            init(URLFactory.createURL(url), DEFAULT_HANDLE_XINCLUDE);
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    public URLGenerator(String url, boolean handleXInclude) {
        try {
            init(URLFactory.createURL(url), handleXInclude);
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    public URLGenerator(URL url) {
        init(url, DEFAULT_HANDLE_XINCLUDE);
    }

    public URLGenerator(URL url, boolean handleXInclude) {
        init(url, handleXInclude);
    }

    private void init(URL url, boolean handleXInclude) {
        this.localConfigURIReferences = new ConfigURIReferences(new Config(url, handleXInclude));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(URL url, String contentType, boolean forceContentType) {
        this.localConfigURIReferences = new ConfigURIReferences(new Config(url, contentType, forceContentType));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(URL url, String contentType, boolean forceContentType, String encoding, boolean forceEncoding,
                      boolean ignoreConnectionEncoding, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical,
                      String mode, Map<String, String[]> headerNameValues, String forwardHeaders, boolean cacheUseLocalCache,
                      boolean enableConditionalGET) {
        this.localConfigURIReferences = new ConfigURIReferences(new Config(url, contentType, forceContentType, encoding,
                forceEncoding, ignoreConnectionEncoding, parserConfiguration, handleLexical, mode,
                headerNameValues, forwardHeaders,
                cacheUseLocalCache, enableConditionalGET, new TidyConfig(null)));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    private static class Config {
        private URL url;
        private String contentType = ProcessorUtils.DEFAULT_CONTENT_TYPE;
        private boolean forceContentType = DEFAULT_FORCE_CONTENT_TYPE;
        private String encoding;
        private boolean forceEncoding = DEFAULT_FORCE_ENCODING;
        private boolean ignoreConnectionEncoding = DEFAULT_IGNORE_CONNECTION_ENCODING;
        private Map<String, String[]> headerNameValues;
        private String forwardHeaders;
        private XMLUtils.ParserConfiguration parserConfiguration = null;
        private boolean handleLexical = DEFAULT_HANDLE_LEXICAL;

        private String mode;

        private boolean cacheUseLocalCache = DEFAULT_CACHE_USE_LOCAL_CACHE;
        private boolean enableConditionalGET = DEFAULT_ENABLE_CONDITIONAL_GET;

        private TidyConfig tidyConfig;

        public Config(URL url) {
            this.url = url;
            this.parserConfiguration = XMLUtils.ParserConfiguration.PLAIN;
            this.tidyConfig = new TidyConfig(null);
        }

        public Config(URL url, boolean handleXInclude) {
            this.url = url;
            this.parserConfiguration = new XMLUtils.ParserConfiguration(DEFAULT_VALIDATING, handleXInclude, DEFAULT_EXTERNAL_ENTITIES);
            this.tidyConfig = new TidyConfig(null);
        }

        public Config(URL url, String contentType, boolean forceContentType) {
            this(url);
            this.forceContentType = true;
            this.contentType = contentType;
            this.forceContentType = forceContentType;
            this.tidyConfig = new TidyConfig(null);
        }

        public Config(URL url, String contentType, boolean forceContentType, String encoding, boolean forceEncoding,
                      boolean ignoreConnectionEncoding, XMLUtils.ParserConfiguration parserConfiguration,
                      boolean handleLexical, String mode, Map<String, String[]> headerNameValues, String forwardHeaders,
                      boolean cacheUseLocalCache, boolean enableConditionalGET, TidyConfig tidyConfig) {

            this.url = url;
            this.contentType = contentType;
            this.forceContentType = forceContentType;
            this.encoding = encoding;
            this.forceEncoding = forceEncoding;
            this.ignoreConnectionEncoding = ignoreConnectionEncoding;
            this.headerNameValues = headerNameValues;
            this.forwardHeaders = forwardHeaders;
            this.parserConfiguration = parserConfiguration;
            this.handleLexical = handleLexical;

            this.mode = mode;

            // Local cache required for conditional GET
            this.cacheUseLocalCache = cacheUseLocalCache || enableConditionalGET;

            // NOTE: Hard to handle this if XInclude is enabled as we would need to conditional-GET all dependencies,
            // and then cache all individually-included documents. Or, store the non-XInclude-processed document in
            // cache. Either way, it's complicated. So we disable conditional GET if XInclude is enabled for now. This
            // could be easier if we had a real HTTP client document cache.
            this.enableConditionalGET = enableConditionalGET && ! parserConfiguration.handleXInclude;

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

        public XMLUtils.ParserConfiguration getParserConfiguration() {
            return parserConfiguration;
        }

        public boolean isHandleLexical() {
            return handleLexical;
        }

        public String getMode() {
            return mode;
        }

        public Map<String, String[]> getHeaderNameValues() {
            return headerNameValues;
        }

        public String getForwardHeaders() {
            return forwardHeaders;
        }

        public boolean isCacheUseLocalCache() {
            return cacheUseLocalCache;
        }

        public boolean isEnableConditionalGET() {
            return enableConditionalGET;
        }

        @Override
        public String toString() {
            return "[" + getURL().toExternalForm() + "|" + getContentType() + "|" + getEncoding() + "|" + parserConfiguration.getKey() + "|" + isHandleLexical() + "|" + isForceContentType()
                    + "|" + isForceEncoding() + "|" + isIgnoreConnectionEncoding() + "|" + tidyConfig + "]";
        }
    }

    private static class ConfigURIReferences {
        public ConfigURIReferences(Config config) {
            this.config = config;
        }

        public Config config;
        public List<URIProcessorOutputImpl.URIReference> uriReferences;
    }

    @Override
    public ProcessorOutput createOutput(final String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(URLGenerator.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {

                makeSureStateIsSet(pipelineContext);

                // Read config input into a URL, cache if possible
                final ConfigURIReferences configURIReferences = URLGenerator.this.localConfigURIReferences != null ? localConfigURIReferences :
                        (ConfigURIReferences) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                            public Object read(PipelineContext context, ProcessorInput input) {
                                final Element configElement = readInputAsDOM4J(context, input).getRootElement();

                                // Processor location data
                                final LocationData locationData = URLGenerator.this.getLocationData();

                                // Shortcut if the url is direct child of config
                                {
                                    final String url = configElement.getTextTrim();
                                    if (url != null && !url.equals("")) {
                                        try {
                                            // Legacy, don't even care about handling relative URLs
                                            return new ConfigURIReferences(new Config(URLFactory.createURL(url)));
                                        } catch (MalformedURLException e) {
                                            throw new ValidationException(e, locationData);
                                        }
                                    }
                                }

                                // We have the /config/url syntax
                                final String url = XPathUtils.selectStringValueNormalize(configElement, "/config/url");
                                if (url == null) {
                                    throw new ValidationException("URL generator found null URL for config:\n" + Dom4jUtils.domToString(configElement), locationData);
                                }

                                // Get content-type
                                final String contentType = XPathUtils.selectStringValueNormalize(configElement, "/config/content-type");
                                final boolean forceContentType = ProcessorUtils.selectBooleanValue(configElement, "/config/force-content-type", DEFAULT_FORCE_CONTENT_TYPE);
                                if (forceContentType && (contentType == null || contentType.equals("")))
                                    throw new ValidationException("The force-content-type element requires a content-type element.", locationData);

                                // Get encoding
                                final String encoding = XPathUtils.selectStringValueNormalize(configElement, "/config/encoding");
                                final boolean forceEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/force-encoding", DEFAULT_FORCE_ENCODING);
                                final boolean ignoreConnectionEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/ignore-connection-encoding", DEFAULT_IGNORE_CONNECTION_ENCODING);
                                if (forceEncoding && (encoding == null || encoding.equals("")))
                                    throw new ValidationException("The force-encoding element requires an encoding element.", locationData);

                                // Get headers
                                Map<String, String[]> headerNameValues = null;
                                for (Object o: configElement.selectNodes("/config/header")) {
                                    final Element currentHeaderElement = (Element) o;
                                    final String currentHeaderName = currentHeaderElement.element("name").getStringValue();
                                    final String currentHeaderValue = currentHeaderElement.element("value").getStringValue();

                                    if (headerNameValues == null) {
                                        // Lazily create collections
                                        headerNameValues = new LinkedHashMap<String, String[]>();
                                    }

                                    headerNameValues.put(currentHeaderName, new String[]{currentHeaderValue});
                                }

                                final String forwardHeaders; {
                                    // Get from configuration first, otherwise use global default
                                    final org.dom4j.Node configForwardHeaders = XPathUtils.selectSingleNode(configElement, "/config/forward-headers");
                                    forwardHeaders = configForwardHeaders != null ? XPathUtils.selectStringValue(configForwardHeaders, ".") : Connection.getForwardHeaders();
                                }

                                // Validation setting: local, then properties, then hard-coded default
                                final boolean defaultValidating = getPropertySet().getBoolean(VALIDATING_PROPERTY, DEFAULT_VALIDATING);
                                final boolean validating = ProcessorUtils.selectBooleanValue(configElement, "/config/validating", defaultValidating);

                                // XInclude handling
                                final boolean defaultHandleXInclude = getPropertySet().getBoolean(HANDLE_XINCLUDE_PROPERTY, DEFAULT_HANDLE_XINCLUDE);
                                final boolean handleXInclude = ProcessorUtils.selectBooleanValue(configElement, "/config/handle-xinclude", defaultHandleXInclude);

                                // External entities
                                final boolean externalEntities = ProcessorUtils.selectBooleanValue(configElement, "/config/external-entities", DEFAULT_EXTERNAL_ENTITIES);

                                final boolean defaultHandleLexical = getPropertySet().getBoolean(HANDLE_LEXICAL_PROPERTY, DEFAULT_HANDLE_LEXICAL);
                                final boolean handleLexical = ProcessorUtils.selectBooleanValue(configElement, "/config/handle-lexical", defaultHandleLexical);

                                // Output mode
                                final String mode = XPathUtils.selectStringValueNormalize(configElement, "/config/mode");

                                // Cache control
                                final boolean cacheUseLocalCache = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/use-local-cache", DEFAULT_CACHE_USE_LOCAL_CACHE);
                                final boolean enableConditionalGET = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/conditional-get", DEFAULT_ENABLE_CONDITIONAL_GET);

                                // Get Tidy config (will only apply if content-type is text/html)
                                final TidyConfig tidyConfig = new TidyConfig(XPathUtils.selectSingleNode(configElement, "/config/tidy-options"));

                                // Create configuration object
                                try {
                                    // Use location data if present so that relative URLs can be supported
                                    // NOTE: We check whether there is a protocol, because we have
                                    // some Java location data which are NOT to be interpreted as
                                    // base URIs
                                    final URL fullURL = (locationData != null && locationData.getSystemID() != null && NetUtils.urlHasProtocol(locationData.getSystemID()))
                                            ? URLFactory.createURL(locationData.getSystemID(), url)
                                            : URLFactory.createURL(url);

                                    // Create configuration
                                    final Config config = new Config(fullURL, contentType, forceContentType, encoding, forceEncoding,
                                            ignoreConnectionEncoding, new XMLUtils.ParserConfiguration(validating, handleXInclude, externalEntities), handleLexical, mode,
                                            headerNameValues, forwardHeaders,
                                            cacheUseLocalCache, enableConditionalGET, tidyConfig);
                                    if (logger.isDebugEnabled())
                                        logger.debug("Read configuration: " + config.toString());
                                    return new ConfigURIReferences(config);
                                } catch (MalformedURLException e) {
                                    throw new ValidationException(e, locationData);
                                }
                            }
                        });
                try {
                    // Never accept a null URL
                    if (configURIReferences.config.getURL() == null)
                        throw new OXFException("Missing configuration.");

                    // We use the same validity as for the output
                    final boolean isUseLocalCache = configURIReferences.config.isCacheUseLocalCache();
                    final CacheKey localCacheKey;
                    final Object localCacheValidity;
                    if (isUseLocalCache) {
                        localCacheKey = new InternalCacheKey(URLGenerator.this, "urlDocument", configURIReferences.config.toString());
                        localCacheValidity = getValidityImpl(pipelineContext);
                    } else {
                        localCacheKey = null;
                        localCacheValidity = null;
                    }

                    // Decide whether to use read from the special oxf: handler or the generic URL handler
                    final URLGeneratorState state = (URLGenerator.URLGeneratorState) URLGenerator.this.getState(pipelineContext);
                    if (state.getDocument() != null) {
                        // Document was found when retrieving validity in conditional get
                        // NOTE: This only happens if isCacheUseLocalCache() == true
                        // NOTE: Document was re-added to cache in getValidityImpl()
                        state.getDocument().replay(xmlReceiver);
                    } else {
                        final Object cachedResource = (localCacheKey == null) ? null : ObjectCache.instance().findValid(localCacheKey, localCacheValidity);
                        if (cachedResource != null) {
                            // Just replay the cached resource
                            ((SAXStore) cachedResource).replay(xmlReceiver);
                        } else {
                            final ResourceHandler handler = state.ensureMainResourceHandler(pipelineContext, configURIReferences.config);
                            try {
                                // We need to read the resource

                                // Find content-type to use. If the config says to force the
                                // content-type, we use the content-type provided by the user.
                                // Otherwise, we give the priority to the content-type provided by
                                // the connection, then the content-type provided by the user, then
                                // we use the default content-type (XML). The user will have to
                                // provide a content-type for example to read HTML documents with
                                // the file: protocol.
                                String contentType;
                                if (configURIReferences.config.isForceContentType()) {
                                    contentType = configURIReferences.config.getContentType();
                                } else {
                                    contentType = handler.getResourceMediaType();
                                    if (contentType == null)
                                        contentType = configURIReferences.config.getContentType();
                                    if (contentType == null)
                                        contentType = ProcessorUtils.DEFAULT_CONTENT_TYPE;
                                }

                                // Get and cache validity as the handler is open, as validity is likely to be used later
                                // again for caching reasons
                                final Long validity = (Long) getHandlerValidity(pipelineContext, configURIReferences.config, configURIReferences.config.getURL(), handler);

                                // Create store for caching if necessary
                                final XMLReceiver output = isUseLocalCache ? new SAXStore(xmlReceiver) : xmlReceiver;

                                // Handle mode
                                String mode = configURIReferences.config.getMode();
                                if (mode == null) {
                                    // Mode is inferred from content-type
                                    if (ProcessorUtils.HTML_CONTENT_TYPE.equals(contentType))
                                        mode = "html";
                                    else if (XMLUtils.isXMLMediatype(contentType))
                                        mode = "xml";
                                    else if (XMLUtils.isTextOrJSONContentType(contentType))
                                        mode = "text";
                                    else
                                        mode = "binary";
                                }

                                // Read resource
                                if (mode.equals("html")) {
                                    // HTML mode
                                    handler.readHTML(output);
                                    configURIReferences.uriReferences = null;
                                } else if (mode.equals("xml")) {
                                    // XML mode
                                    final URIProcessorOutputImpl.URIReferences uriReferences = new URIProcessorOutputImpl.URIReferences();
                                    handler.readXML(pipelineContext, output, uriReferences);
                                    configURIReferences.uriReferences = uriReferences.getReferences();
                                } else if (mode.equals("text")) {
                                    // Text mode
                                    handler.readText(output, contentType, validity);
                                    configURIReferences.uriReferences = null;
                                } else if (mode.equals("archive")) {
                                    // Archive mode
                                    handler.readArchive(output, contentType, validity);
                                    configURIReferences.uriReferences = null;
                                } else {
                                    // Binary mode
                                    handler.readBinary(output, contentType, validity);
                                    configURIReferences.uriReferences = null;
                                }

                                // Cache the resource if requested
                                if (isUseLocalCache) {
                                    // Make sure SAXStore loses its reference on its output so that we don't clutter the cache
                                    ((SAXStore) output).setXMLReceiver(null);
                                    // Add to cache
                                    ObjectCache.instance().add(localCacheKey, localCacheValidity, output);
                                }
                            } finally {
                                handler.destroy();
                            }
                        }
                    }
                } catch (SAXParseException spe) {
                    throw new ValidationException(spe.getMessage(), new LocationData(spe));
                } catch (ValidationException e) {
                    final LocationData locationData = e.getLocationData();
                    // The system id may not be set
                    if (locationData == null || locationData.getSystemID() == null)
                        throw ValidationException.wrapException(e, new LocationData(configURIReferences.config.getURL().toExternalForm(), -1, -1));
                    else
                        throw e;
                } catch (OXFException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ValidationException(e, new LocationData(configURIReferences.config.getURL().toExternalForm(), -1, -1));
                }
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                makeSureStateIsSet(pipelineContext);
                try {
                    final ConfigURIReferences configURIReferences = getConfigURIReferences(pipelineContext);
                    if (configURIReferences == null) {
                        return null;
                    }

                    final int keyCount = 1 + ((localConfigURIReferences == null) ? 1 : 0)
                            + ((configURIReferences.uriReferences != null) ? configURIReferences.uriReferences.size() : 0);
                    final CacheKey[] outputKeys = new CacheKey[keyCount];

                    // Handle config if read as input
                    int keyIndex = 0;
                    if (localConfigURIReferences == null) {
                        KeyValidity configKeyValidity = getInputKeyValidity(pipelineContext, INPUT_CONFIG);
                        if (configKeyValidity == null) {
                            return null;
                        }
                        outputKeys[keyIndex++] = configKeyValidity.key;
                    }
                    // Handle main document and config
                    outputKeys[keyIndex++] = new SimpleOutputCacheKey(getProcessorClass(), name, configURIReferences.config.toString());
                    // Handle dependencies if any
                    if (configURIReferences.uriReferences != null) {
                        for (URIProcessorOutputImpl.URIReference uriReference : configURIReferences.uriReferences) {
                            outputKeys[keyIndex++] = new InternalCacheKey(URLGenerator.this, "urlReference", URLFactory.createURL(uriReference.context, uriReference.spec).toExternalForm());
                        }
                    }
                    return new CompoundOutputCacheKey(getProcessorClass(), name, outputKeys);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            }

            @Override
            public Object getValidityImpl(PipelineContext pipelineContext) {
                makeSureStateIsSet(pipelineContext);
                try {
                    ConfigURIReferences configURIReferences = getConfigURIReferences(pipelineContext);
                    if (configURIReferences == null)
                        return null;

                    List<Object> validities = new ArrayList<Object>();

                    // Handle config if read as input
                    if (localConfigURIReferences == null) {
                        KeyValidity configKeyValidity = getInputKeyValidity(pipelineContext, INPUT_CONFIG);
                        if (configKeyValidity == null)
                            return null;
                        validities.add(configKeyValidity.validity);
                    }

                    // Handle main document and config
                    final URLGeneratorState state = (URLGenerator.URLGeneratorState) URLGenerator.this.getState(pipelineContext);
                    final ResourceHandler resourceHandler = state.ensureMainResourceHandler(pipelineContext, configURIReferences.config);
                    validities.add(getHandlerValidity(pipelineContext, configURIReferences.config, configURIReferences.config.getURL(), resourceHandler));

                    // Handle dependencies if any
                    if (configURIReferences.uriReferences != null) {
                        for (URIProcessorOutputImpl.URIReference uriReference: configURIReferences.uriReferences) {
                            validities.add(getHandlerValidity(pipelineContext, configURIReferences.config, URLFactory.createURL(uriReference.context, uriReference.spec), null));
                        }
                    }
                    return validities;
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private Long getHandlerValidity(PipelineContext pipelineContext, Config config, URL url, ResourceHandler handler) {
                final URLGeneratorState state = (URLGenerator.URLGeneratorState) URLGenerator.this.getState(pipelineContext);
                final String urlString = url.toExternalForm();
                if (state.isLastModifiedSet(urlString)) {
                    // Found value in state cache
                    return state.getLastModified(urlString);
                } else {
                    // Get value and cache it in state
                    try {
                        final Long validity;
                        if (handler == null) {
                            // Dependency

                            // Create handler right here
                            handler = OXFHandler.PROTOCOL.equals(url.getProtocol())
                                ? new OXFResourceHandler(new Config(url)) // Should use full config so that headers are forwarded?
                                : new URLResourceHandler(pipelineContext, new Config(url));// Should use full config so that headers are forwarded?

                            try {
                                // FIXME: this can potentially be very slow with some URLs like HTTP URLs. We try to
                                // optimized this by keeping the URLConnection for the main document, but dependencies may
                                // cause multiple requests to the same URL.
                                validity = handler.getValidity();
                            } finally {
                                // Destroy handler
                                handler.destroy();
                            }
                        } else {
                            // Main handler

                            // Try to see what we have in cache
                            final CacheEntry cacheEntry;
                            if (config.isEnableConditionalGET()) {
                                // NOTE: This *could* be transparently handled by HttpClient cache if configured properly:
                                // http://hc.apache.org/httpcomponents-client-ga/httpclient-cache/apidocs/org/apache/http/impl/client/cache/CachingHttpClient.html
                                // Although, this would only cache the bytes, and probably not provide us with a
                                // readily-parsed SAXStore.
                                final CacheKey localCacheKey = new InternalCacheKey(URLGenerator.this, "urlDocument", config.toString());
                                cacheEntry = ObjectCache.instance().findAny(localCacheKey);
                            } else {
                                cacheEntry = null;
                            }

                            if (cacheEntry != null) {
                                // Found some entry in cache for the key
                                final long lastModified = findLastModified(cacheEntry.validity);
                                // This returns the validity and, possibly, stores the document in the state
                                validity = handler.getConditional(lastModified);
                                if (handler.getConnectionStatusCode() == 304) {
                                    // The server responded that the resource hasn't changed

                                    // Update the entry in cache
                                    ObjectCache.instance().add(cacheEntry.key, lastModified, cacheEntry.cacheable);

                                    // Remember the document for the rest of this request
                                    state.setDocument((SAXStore) cacheEntry.cacheable);
                                }
                            } else {
                                validity = handler.getValidity();
                            }
                        }
                        state.setLastModified(urlString, validity);
                        return validity;

                    } catch (Exception e) {
                        // If the file no longer exists, for example, we don't want to throw, just to invalidate
                        // An exception will be thrown if necessary when the document is actually read
                        return null;
                    }
                }
            }

            private ConfigURIReferences getConfigURIReferences(PipelineContext context) {
                // Check if config is external
                if (localConfigURIReferences != null)
                    return localConfigURIReferences;

                // Make sure the config input is cacheable
                final KeyValidity keyValidity = getInputKeyValidity(context, INPUT_CONFIG);
                if (keyValidity == null) {
                    return null;
                }

                // Try to find resource manager key in cache
                final ConfigURIReferences config = (ConfigURIReferences) ObjectCache.instance().findValid(keyValidity.key, keyValidity.validity);
                if (logger.isDebugEnabled()) {
                    if (config != null)
                        logger.debug("Config found: " + config.toString());
                    else
                        logger.debug("Config not found");
                }
                return config;
            }
        };
        addOutput(name, output);
        return output;
    }

    private interface ResourceHandler {
        Long getValidity() throws IOException;
        Long getConditional(Long lastModified) throws IOException;
        String getResourceMediaType() throws IOException;
        String getConnectionEncoding() throws IOException;
        int getConnectionStatusCode() throws IOException;
        void destroy() throws IOException;
        void readHTML(XMLReceiver xmlReceiver) throws IOException;
        void readText(ContentHandler output, String contentType, Long lastModified) throws IOException;
        void readXML(PipelineContext pipelineContext, XMLReceiver xmlReceiver, URIProcessorOutputImpl.URIReferences uriReferences) throws IOException;
        void readBinary(ContentHandler output, String contentType, Long lastModified) throws IOException;
        void readArchive(ContentHandler output, String contentType, Long lastModified) throws IOException;
    }

    private static class OXFResourceHandler implements ResourceHandler {
        private Config config;
        private String resourceManagerKey;
        private InputStream inputStream;

        public OXFResourceHandler(Config config) {
            this.config = config;
        }

        public String getResourceMediaType() throws IOException {
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

        public int getConnectionStatusCode() throws IOException {
            return -1;
        }

        public Long getValidity() throws IOException {
            getKey();
            if (logger.isDebugEnabled())
                logger.debug("OXF Protocol: Using ResourceManager for key " + getKey());

            long result = ResourceManagerWrapper.instance().lastModified(getKey(), false);
            // Zero and negative values often have a special meaning, make sure to normalize here
            return (result <= 0) ? null : result;
        }

        public Long getConditional(Long lastModified) throws IOException {
            return getValidity();
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

        public void readHTML(XMLReceiver xmlReceiver) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            URLResourceHandler.readHTML(inputStream, config.getTidyConfig(), getExternalEncoding(), xmlReceiver);
        }

        public void readText(ContentHandler output, String contentType, Long lastModified) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            ProcessorUtils.readText(inputStream, getExternalEncoding(), output, contentType, lastModified, getConnectionStatusCode());
        }

        public void readXML(PipelineContext pipelineContext, XMLReceiver xmlReceiver, URIProcessorOutputImpl.URIReferences uriReferences) throws IOException {
            final XMLUtils.ParserConfiguration parserConfiguration = new XMLUtils.ParserConfiguration(config.getParserConfiguration(), uriReferences);
            if (getExternalEncoding() != null) {
                // The encoding is set externally, either forced by the user, or set by the connection
                inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
                XMLUtils.readerToSAX(new InputStreamReader(inputStream, getExternalEncoding()), config.getURL().toExternalForm(),
                        xmlReceiver, parserConfiguration, config.isHandleLexical());
            } else {
                // Regular case, the resource manager does the job and autodetects the encoding
                ResourceManagerWrapper.instance().getContentAsSAX(getKey(),
                        xmlReceiver, parserConfiguration, config.isHandleLexical());
            }
        }

        public void readBinary(ContentHandler output, String contentType, Long lastModified) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            ProcessorUtils.readBinary(inputStream, output, contentType, lastModified, getConnectionStatusCode());
        }

        public void readArchive(ContentHandler output, String contentType, Long lastModified) throws IOException {
            throw new ValidationException("Archive mode is not supported for the oxf: protocol", new LocationData(config.getURL().toExternalForm(), -1, -1));
        }

        private String getKey() {
            if (resourceManagerKey == null)
                resourceManagerKey = config.getURL().getFile();
            return resourceManagerKey;
        }
    }

    private static class URLResourceHandler implements ResourceHandler {
        private PipelineContext pipelineContext;
        private Config config;
        private ConnectionResult connectionResult;
        private InputStream inputStream;
        private Map<String, String[]> requestHeadersMap;

        public URLResourceHandler(PipelineContext pipelineContext, Config config) {
            this.pipelineContext = pipelineContext;
            this.config = config;
        }

        public String getResourceMediaType() throws IOException {
            // Return null for file protocol, as it returns incorrect content types
            if ("file".equals(config.getURL().getProtocol()))
                return null;
            // Otherwise, try URLConnection
            openConnection();
            return connectionResult.getResponseMediaType();
        }

        public String getConnectionEncoding() throws IOException {
            // Return null for file protocol, as it returns incorrect content types
            if ("file".equals(config.getURL().getProtocol()))
                return null;
            // Otherwise, try URLConnection
            openConnection();
            return NetUtils.getContentTypeCharset(connectionResult.getResponseContentType());
        }

        public int getConnectionStatusCode() throws IOException {
            // Return -1 for file protocol, as it returns nothing significant
            if ("file".equals(config.getURL().getProtocol()))
                return -1;
            // Otherwise, try URLConnection
            openConnection();
            return connectionResult.statusCode;
        }

        public Long getValidity() throws IOException {
            openConnection();
            return connectionResult.getLastModified();
        }

        public Long getConditional(Long lastModified) throws IOException {
            openConnection(lastModified);
            return connectionResult.getLastModified();
        }

        public void destroy() throws IOException {
            // Make sure the connection is closed because when
            // getting the last modified date, the stream is
            // actually opened. When using the file: protocol, the
            // file can be locked on disk.
            if (inputStream != null) {
                inputStream.close();
            }

            // Just in case - although URLResourceHandler should be gc'ed quickly
            pipelineContext = null;
            config = null;
            connectionResult = null;
            inputStream = null;
        }

        private void openConnection() throws IOException {
            openConnection(null);
        }

        private void openConnection(Long lastModified) throws IOException {
            if (connectionResult == null) {
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                // TODO: pass logging callback

                final Map<String, String[]> newHeaders;
                if (lastModified != null) {
                    // A conditional GET is requested
                    newHeaders = new HashMap<String, String[]>();
                    if (config.getHeaderNameValues() != null)
                        newHeaders.putAll(config.getHeaderNameValues());
                    newHeaders.put("If-Modified-Since", new String[] { ISODateUtils.getRFC1123Date(lastModified) });
                } else {
                    // Regular GET
                    newHeaders = config.getHeaderNameValues();
                }

                Connection connection = new Connection();
                connectionResult = connection.open(externalContext, indentedLogger, false, Connection.Method.GET.name(),
                        config.getURL(), null, null, null, null, null, newHeaders, config.getForwardHeaders());
                requestHeadersMap = connection.getHeadersMap();
                inputStream = connectionResult.getResponseInputStream(); // empty stream if conditional GET succeeded
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

        public void readHTML(XMLReceiver xmlReceiver) throws IOException {
            openConnection();
            checkStatusCode();
            readHTML(inputStream, config.getTidyConfig(), getExternalEncoding(), xmlReceiver);
        }

        public void readText(ContentHandler output, String contentType, Long lastModified) throws IOException {
            openConnection();
            ProcessorUtils.readText(inputStream, getExternalEncoding(), output, contentType, lastModified, getConnectionStatusCode());
        }

        public void readBinary(ContentHandler output, String contentType, Long lastModified) throws IOException {
            openConnection();
            ProcessorUtils.readBinary(inputStream, output, contentType, lastModified, getConnectionStatusCode());
        }

        public void readArchive(ContentHandler output, String contentType, Long lastModified) throws IOException {
            openConnection();
            try {
                output.startDocument();
                output.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                output.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                output.startElement("", DEFAULT_ARCHIVE_DOCUMENT_ELEMENT, DEFAULT_ARCHIVE_DOCUMENT_ELEMENT, new AttributesImpl());

                output.startElement("", DEFAULT_REQUEST_ELEMENT, DEFAULT_REQUEST_ELEMENT, new AttributesImpl());

                output.startElement("", DEFAULT_METHOD_ELEMENT, DEFAULT_METHOD_ELEMENT, new AttributesImpl());
                output.characters("GET".toCharArray(), 0, "GET".length());
                output.endElement("", DEFAULT_METHOD_ELEMENT, DEFAULT_METHOD_ELEMENT);

                output.startElement("", DEFAULT_LOCATION_ELEMENT, DEFAULT_LOCATION_ELEMENT, new AttributesImpl());
                String location = config.getURL().toString();
                output.characters(location .toCharArray(), 0, location.length());
                output.endElement("", DEFAULT_LOCATION_ELEMENT, DEFAULT_LOCATION_ELEMENT);

                if (requestHeadersMap != null ) {

                    for (Map.Entry<String,String[]> currentEntry: requestHeadersMap.entrySet()) {
                        final String currentHeaderName = currentEntry.getKey();
                        final String[] currentHeaderValues = currentEntry.getValue();
                        if (currentHeaderValues != null) {
                            final AttributesImpl attributes = new AttributesImpl();
                            attributes.addAttribute("", "name", "name", "CDATA", currentHeaderName);
                            for (String currentHeaderValue: currentHeaderValues) {
                                output.startElement("", DEFAULT_HEADER_ELEMENT, DEFAULT_HEADER_ELEMENT, attributes);
                                output.characters(currentHeaderValue.toCharArray(), 0, currentHeaderValue.length());
                                output.endElement("", DEFAULT_HEADER_ELEMENT, DEFAULT_HEADER_ELEMENT);
                            }
                        }
                    }
                }
                output.endElement("", DEFAULT_REQUEST_ELEMENT, DEFAULT_REQUEST_ELEMENT);

                output.startElement("", DEFAULT_RESPONSE_ELEMENT, DEFAULT_RESPONSE_ELEMENT, new AttributesImpl());

                output.startElement("", DEFAULT_STATUS_CODE_ELEMENT, DEFAULT_STATUS_CODE_ELEMENT, new AttributesImpl());
                String statusCode = Integer.toString(getConnectionStatusCode());
                output.characters(statusCode.toCharArray(), 0, statusCode.length());
                output.endElement("", DEFAULT_STATUS_CODE_ELEMENT, DEFAULT_STATUS_CODE_ELEMENT);


                Map<String, List<String>> responseHeaders = connectionResult.getResponseHeaders();
                if (responseHeaders != null ) {

                    for (Map.Entry<String, List<String>> currentEntry: responseHeaders.entrySet()) {
                        final String currentHeaderName = currentEntry.getKey();
                        final List<String> currentHeaderValues = currentEntry.getValue();
                        if (currentHeaderValues != null) {
                            final AttributesImpl attributes = new AttributesImpl();
                            attributes.addAttribute("", "name", "name", "CDATA", currentHeaderName);
                            for (String currentHeaderValue: currentHeaderValues) {
                                output.startElement("", DEFAULT_HEADER_ELEMENT, DEFAULT_HEADER_ELEMENT, attributes);
                                output.characters(currentHeaderValue.toCharArray(), 0, currentHeaderValue.length());
                                output.endElement("", DEFAULT_HEADER_ELEMENT, DEFAULT_HEADER_ELEMENT);
                            }
                        }
                    }

                }


                final AttributesImpl attributes = new AttributesImpl();
                attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName());
                if (contentType != null)
                    attributes.addAttribute("", "content-type", "content-type", "CDATA", contentType);
                if (lastModified != null)
                    attributes.addAttribute("", "last-modified", "last-modified", "CDATA", ISODateUtils.getRFC1123Date(lastModified));                
                output.startElement("", ProcessorUtils.DEFAULT_BINARY_DOCUMENT_ELEMENT, ProcessorUtils.DEFAULT_BINARY_DOCUMENT_ELEMENT, attributes);
                XMLUtils.inputStreamToBase64Characters(inputStream, output);
                output.endElement("", ProcessorUtils.DEFAULT_BINARY_DOCUMENT_ELEMENT, ProcessorUtils.DEFAULT_BINARY_DOCUMENT_ELEMENT);

                output.endElement("", DEFAULT_RESPONSE_ELEMENT, DEFAULT_RESPONSE_ELEMENT);

                output.endElement("", DEFAULT_ARCHIVE_DOCUMENT_ELEMENT, DEFAULT_ARCHIVE_DOCUMENT_ELEMENT);
                output.endPrefixMapping(XMLConstants.XSD_PREFIX);
                output.endPrefixMapping(XMLConstants.XSI_PREFIX);
                output.endDocument();
            } catch (SAXException e) {
               throw new OXFException(e);
            }           
         }

        public void readXML(PipelineContext pipelineContext, XMLReceiver xmlReceiver, URIProcessorOutputImpl.URIReferences uriReferences) throws IOException {
            openConnection();
            checkStatusCode();

            final XMLUtils.ParserConfiguration parserConfiguration = new XMLUtils.ParserConfiguration(config.getParserConfiguration(), uriReferences);
            try {
                final XMLReader reader = XMLUtils.newXMLReader(parserConfiguration);
                reader.setContentHandler(xmlReceiver);
                // TODO: lexical handler?
                final InputSource inputSource;
                if (getExternalEncoding() != null) {
                    // The encoding is set externally, either force by the user, or set by the connection
                    inputSource = new InputSource(new InputStreamReader(inputStream, getExternalEncoding()));
                } else {
                    // This is the regular case where the XML parser autodetects the encoding
                    inputSource = new InputSource(inputStream);
                }
                inputSource.setSystemId(config.getURL().toExternalForm());
                reader.parse(inputSource);
            } catch (SAXException e) {
                throw new OXFException(e);
            }
        }

        private void checkStatusCode() throws IOException {
            final int statusCode = getConnectionStatusCode();
            if (statusCode > 0 && (statusCode < 200 || statusCode >= 300))
                throw new ValidationException("Got non-success status code: " + statusCode, new LocationData(config.getURL().toExternalForm(), -1, -1));
        }

        public static void readHTML(InputStream is, TidyConfig tidyConfig, String encoding, XMLReceiver output) {
            Tidy tidy = new Tidy();
//          tidy.setOnlyErrors(false);
            tidy.setShowWarnings(tidyConfig.isShowWarnings());
            tidy.setQuiet(tidyConfig.isQuiet());

            // Set encoding
            // If the encoding is null, we get a default
            tidy.setInputEncoding(TidyConfig.getTidyEncoding(encoding));

            // Parse and output to SAXResult
            TransformerUtils.sourceToSAX(new DOMSource(tidy.parseDOM(is, null)), output);
        }

    }

    // The idea of URLGeneratorState is that, during a pipeline execution with a given PipelineContext, there is typically:
    //
    // o a call to getValidity()
    // o followed by a call to read()
    //
    // In order to avoid dereferencing the URL twice, the handler is stored in the state so it can be accessed by read().
    private static class URLGeneratorState {

        private ResourceHandler mainResourceHandler;
        private Map<String, Object> map;
        private SAXStore document;

        public void setLastModified(String urlString, Long lastModified) {
            if (map == null)
                map = new HashMap<String, Object>();
            map.put(urlString, lastModified == null ? "" : lastModified);
        }

        public boolean isLastModifiedSet(String urlString) {
            return map != null && map.get(urlString) != null;
        }

        public Long getLastModified(String urlString) {
            final Object result = map.get(urlString);
            return (result instanceof String) ? null : (Long) result;
        }

        public ResourceHandler ensureMainResourceHandler(PipelineContext pipelineContext, Config config) {
            if (mainResourceHandler == null) {
                // Create and remember handler
                mainResourceHandler = OXFHandler.PROTOCOL.equals(config.getURL().getProtocol())
                        ? new OXFResourceHandler(config)
                        : new URLResourceHandler(pipelineContext, config);
                // Make sure it is destroyed when the pipeline ends at the latest
                pipelineContext.addContextListener(new PipelineContext.ContextListener() {
                    public void contextDestroyed(boolean success) {
                        try {
                            mainResourceHandler.destroy();
                        } catch (IOException e) {
                            logger.error("Exception caught while destroying ResourceHandler", e);
                        }
                    }
                });
            }
            return mainResourceHandler;
        }

        public void setDocument(SAXStore document) {
            this.document = document;
        }

        public SAXStore getDocument() {
            return document;
        }
    }

    private void makeSureStateIsSet(PipelineContext pipelineContext) {
        if (!hasState(pipelineContext))
            setState(pipelineContext, new URLGeneratorState());
    }

    @Override
    public void reset(PipelineContext pipelineContext) {
        setState(pipelineContext, new URLGeneratorState());
    }
}