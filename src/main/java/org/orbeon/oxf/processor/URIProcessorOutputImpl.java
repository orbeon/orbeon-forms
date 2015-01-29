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

import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.Credentials;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.handler.OXFHandler;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XMLParsing;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

/**
 * Implementation of a caching transformer output that assumes that an output depends on a set
 * of URIs associated with an input document.
 *
 * Usage: an URIReferences object must be cached as an object associated with the config input.
 */
public abstract class URIProcessorOutputImpl extends ProcessorOutputImpl {

    public static Logger logger = LoggerFactory.createLogger(URIProcessorOutputImpl.class);

    private ProcessorImpl processorImpl;
    private String configInputName;
    private URIReferences localConfigURIReferences = null; // TODO: NIY

    public URIProcessorOutputImpl(ProcessorImpl processorImpl, String name, String configInputName) {
        super(processorImpl, name);
        this.processorImpl = processorImpl;
        this.configInputName = configInputName;
    }

//        private void log(String message) {
//            logger.info("URIProcessorOutputImpl (" + getClass().getName() + ") " + message);
//        }

    @Override
    public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
        final URIReferences uriReferences = getCachedURIReferences(pipelineContext);
//            log("uriReferences: " + uriReferences);
        if (uriReferences == null)
            return null;

        final List<CacheKey> keys = new ArrayList<CacheKey>();

        // Handle config if read as input
        if (localConfigURIReferences == null) {
            final ProcessorImpl.KeyValidity configKeyValidity = processorImpl.getInputKeyValidity(pipelineContext, configInputName);
//                log("configKeyValidity: " + configKeyValidity);
            if (configKeyValidity == null)
                return null;
            keys.add(configKeyValidity.key);
        }

        // Add local key if needed
        if (supportsLocalKeyValidity()) {
            final ProcessorImpl.KeyValidity keyValidity = getLocalKeyValidity(pipelineContext, uriReferences);
            if (keyValidity == null)
                return null;
            keys.add(keyValidity.key);
        }

        // Handle local key
//        final ProcessorImpl.KeyValidity localKeyValidity = uriReferences.getLocalKeyValidity();
//        if (localKeyValidity != null)
//            keys.add(localKeyValidity.key);

        // Handle dependencies if any
//            log("uriReferences.getReferences(): " + uriReferences.getReferences());
        if (uriReferences.getReferences() != null) {
            for (final URIReference uriReference: uriReferences.getReferences()) {
                if (uriReference == null)
                    return null;
                final CacheKey uriKey = getURIKey(pipelineContext, uriReference);
//                    log("key: " + uriKey);
                keys.add(uriKey);
            }
        }
        final CacheKey[] outKeys = new CacheKey[keys.size()];
        keys.toArray(outKeys);
        return new CompoundOutputCacheKey(getProcessorClass(), getName(), outKeys);
    }

    @Override
    protected Object getValidityImpl(PipelineContext pipelineContext) {
        final URIReferences uriReferences = getCachedURIReferences(pipelineContext);
//            log("uriReferences: " + uriReferences);
        if (uriReferences == null)
            return null;

        final List<Object> validities = new ArrayList<Object>();

        // Handle config if read as input
        if (localConfigURIReferences == null) {
            final ProcessorImpl.KeyValidity configKeyValidity = processorImpl.getInputKeyValidity(pipelineContext, configInputName);
//                log("configKeyValidity: " + configKeyValidity);
            if (configKeyValidity == null)
                return null;
            validities.add(configKeyValidity.validity);
        }
        // Handle local validity
//        final ProcessorImpl.KeyValidity localKeyValidity = uriReferences.getLocalKeyValidity();
//        if (localKeyValidity != null)
//            validities.add(localKeyValidity.validity);

        // Add local validity if needed
        if (supportsLocalKeyValidity()) {
            final ProcessorImpl.KeyValidity keyValidity = getLocalKeyValidity(pipelineContext, uriReferences);
            if (keyValidity == null)
                return null;
            validities.add(keyValidity.validity);
        }

        // Handle dependencies if any
//            log("uriReferences.getReferences(): " + uriReferences.getReferences());
        if (uriReferences.getReferences() != null) {
            for (final URIReference uriReference: uriReferences.getReferences()) {
                if (uriReference == null)
                    return null;

                final Object uriValidity = getURIValidity(pipelineContext, uriReference);
//                    log("validity: " + uriValidity);
                validities.add(uriValidity);
            }
        }
        return validities;
    }

    /**
     * This method returns the key associated with an URI.
     *
     * @param pipelineContext   current pipeline context
     * @param uriReference      URIReference object containing the URI to process
     * @return                  key of the URI (including null)
     */
    protected CacheKey getURIKey(PipelineContext pipelineContext, URIReference uriReference) {

        try {
            final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(uriReference.spec);
            if (inputName != null) {
                // input: URIs
                return ProcessorImpl.getInputKey(pipelineContext, processorImpl.getInputByName(inputName));
            } else {
                // Other URIs
                final String keyString = buildURIUsernamePasswordString(URLFactory.createURL(uriReference.context, uriReference.spec).toExternalForm(), uriReference.credentials);
                return new InternalCacheKey(processorImpl, "urlReference", keyString);
            }
        } catch (Exception e) {
            // If the file no longer exists, for example, we don't want to throw, just to invalidate
            // An exception will be thrown if necessary when the document is actually read
//                log("exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * This method returns the validity associated with an URI.
     *
     * @param pipelineContext   current pipeline context
     * @param uriReference      URIReference object containing the URI to process
     * @return                  validity of the URI (including null)
     */
    protected Object getURIValidity(PipelineContext pipelineContext, URIReference uriReference) {
        try {
            final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(uriReference.spec);
            if (inputName != null) {
                // input: URIs
                return ProcessorImpl.getInputValidity(pipelineContext, processorImpl.getInputByName(inputName));
            } else {

                final URL url = URLFactory.createURL(uriReference.context, uriReference.spec);
                if (OXFHandler.PROTOCOL.equals(url.getProtocol())) {
                    // oxf: URLs
                    final String key = url.getFile();
                    final long result = ResourceManagerWrapper.instance().lastModified(key, false);
                    // Zero and negative values often have a special meaning, make sure to normalize here
                    return (result <= 0) ? null : result;
                } else if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) {
                    // HTTP and HTTPS protocols: read and keep document

                    // NOTE: We cache for this execution of this processor, so we don't make multiple accesses. The
                    // cache is discarded once the processor gets out of scope.

                    final URIReferencesState state = (URIReferencesState) processorImpl.getState(pipelineContext);
                    final String urlString = url.toExternalForm();
                    readURLToStateIfNeeded(pipelineContext, url, state, uriReference.credentials);
                    return state.getLastModified(urlString, uriReference.credentials);

                } else  {
                    // Other URLs
                    return NetUtils.getLastModifiedAsLong(url);
                }
            }
        } catch (Exception e) {
            // If the file no longer exists, for example, we don't want to throw, just to invalidate
            // An exception will be thrown if necessary when the document is actually read
//                log("exception: " + e.getMessage());
            return null;
        }
    }

//    public SAXStore getDocument(PipelineContext pipelineContext, String urlString) {
//        // Use cached state if possible
//        // NOTE: We cache just for this execution of this processor, so we don't make multiple accesses
//        final URIReferencesState state = (URIReferencesState) processorImpl.getState(pipelineContext);
//        if (state.isDocumentSet(urlString))
//            return state.getDocument(urlString);
//        else
//            return null;
//    }

    private URIReferences getCachedURIReferences(PipelineContext pipelineContext) {
        // Check if config is external
        if (localConfigURIReferences != null)
            return localConfigURIReferences;

        // Make sure the config input is cacheable
        final ProcessorImpl.KeyValidity keyValidity = processorImpl.getInputKeyValidity(pipelineContext, configInputName);

        if (keyValidity == null)
            return null;

        // Try to find resource manager key in cache
        final URIReferences config = (URIReferences) ObjectCache.instance().findValid(keyValidity.key, keyValidity.validity);
        if (ProcessorImpl.logger.isDebugEnabled()) {
            if (config != null)
                ProcessorImpl.logger.debug("Config (URIReferences) found: " + config.toString());
            else
                ProcessorImpl.logger.debug("Config (URIReferences) not found");
        }
        return config;
    }

    public static class URIReference {

        public final String context;
        public final String spec;
        public final Credentials credentials;

        public URIReference(String context, String spec, Credentials credentials) {
            this.context = context;
            this.spec = spec;
            this.credentials = credentials;
        }

        @Override
        public String toString() {
            return "[" + context + ", " + spec + ", " + credentials + "]";
        }
    }

    private static class DocumentInfo {
        public SAXStore saxStore;
        public Long lastModified;

        public DocumentInfo(SAXStore saxStore, Long lastModified) {
            this.saxStore = saxStore;
            this.lastModified = lastModified;
        }
    }

    public static class URIReferencesState {

        private Map<String, DocumentInfo> map;

        public void setDocument(String urlString, Credentials credentials, SAXStore documentSAXStore, Long lastModified) {
            if (map == null)
                map = new HashMap<String, DocumentInfo>();
            map.put(buildURIUsernamePasswordString(urlString, credentials), new DocumentInfo(documentSAXStore, lastModified));
        }

        public boolean isDocumentSet(String urlString, Credentials credentials) {
            return map != null && map.get(buildURIUsernamePasswordString(urlString, credentials)) != null;
        }

        public Long getLastModified(String urlString, Credentials credentials) {
            final DocumentInfo documentInfo = map.get(buildURIUsernamePasswordString(urlString, credentials));
            return documentInfo.lastModified;
        }

        public SAXStore getDocument(String urlString, Credentials credentials) {
            final DocumentInfo documentInfo = map.get(buildURIUsernamePasswordString(urlString, credentials));
            return documentInfo.saxStore;
        }
    }

    /**
     * This is the object that must be associated with the configuration input and cached. Users can
     * derive from this and store their own configuration inside.
     */
    public static class URIReferences {

        private List<URIReference> references;

        /**
         * Add a URL reference.
         *
         * @param context           optional context (can be null)
         * @param spec              URL spec
         * @param credentials       optional credentials
         */
        public void addReference(String context, String spec, Credentials credentials) {
            if (references == null)
                references = new ArrayList<URIReference>();

            references.add(new URIReference(context, spec, credentials));
        }

        /**
         * Get URI references.
         *
         * @return  references or null if none
         */
        public List<URIReference> getReferences() {
            return references;
        }
    }

    protected boolean supportsLocalKeyValidity() {
        return false;
    }

    public ProcessorImpl.KeyValidity getLocalKeyValidity(PipelineContext pipelineContext, URIReferences uriReferences) {
        throw new UnsupportedOperationException();
    }

    /**
     * This is called to handle "http:", "https:" and other URLs (but not "oxf:" and "input:"). It is possible to
     * override this method, for example to optimize HTTP access.
     *
     * @param pipelineContext   current context
     * @param url               URL to read
     * @param state             state to read to
     * @param credentials       optional credentials
     */
    public void readURLToStateIfNeeded(PipelineContext pipelineContext, URL url, URIReferencesState state, Credentials credentials) {

        final String urlString = url.toExternalForm();

        // Use cached state if possible
        if (!state.isDocumentSet(urlString, credentials)) {
            // We read the document and store it temporarily, since it will likely be read just after this anyway
            final SAXStore documentSAXStore;
            final Long lastModifiedLong;
            {
                // Perform connection
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

                // Compute absolute submission URL
                final URI submissionURL;
                try {
                    submissionURL = new URI(
                        URLRewriterUtils.rewriteServiceURL(externalContext.getRequest(),
                        urlString,
                        ExternalContext.Response.REWRITE_MODE_ABSOLUTE)
                    );
                } catch (URISyntaxException e) {
                    throw new OXFException(e);
                }

                // Open connection
                final IndentedLogger indentedLogger = new IndentedLogger(logger, "");
                final scala.collection.immutable.Map<String, scala.collection.immutable.List<String>> headers =
                    Connection.jBuildConnectionHeadersLowerIfNeeded(
                        submissionURL.getScheme(),
                        credentials != null,
                        null,
                        Connection.jHeadersToForward(),
                        indentedLogger
                    );

                final ConnectionResult connectionResult
                    = Connection.jApply("GET", submissionURL, credentials, null, headers, true, false, indentedLogger).connect(true);

                // Throw if connection failed (this is caught by the caller)
                if (connectionResult.statusCode() != 200)
                    throw new OXFException("Got invalid return code while loading URI: " + urlString + ", " + connectionResult.statusCode());

                // Read connection into SAXStore
                documentSAXStore = new SAXStore();

                ConnectionResult.withSuccessConnection(connectionResult, true, new Function1Adapter<InputStream, Object>() {
                    public Object apply(InputStream is) {
                        XMLParsing.inputStreamToSAX(is, connectionResult.url(), documentSAXStore, XMLParsing.ParserConfiguration.PLAIN, true);
                        return null;
                    }
                });

                // Obtain last modified
                lastModifiedLong = connectionResult.lastModifiedJava();
            }

            // Cache document and last modified
            state.setDocument(urlString, credentials, documentSAXStore, lastModifiedLong);
        }
    }

    private static String buildURIUsernamePasswordString(String uriString, Credentials credentials) {
        // We don't care that the result is an actual URI
        if (credentials != null)
            return credentials.getPrefix() + uriString;
        else
            return uriString;
    }
}
