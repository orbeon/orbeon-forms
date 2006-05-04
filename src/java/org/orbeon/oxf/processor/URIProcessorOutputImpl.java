/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.handler.OXFHandler;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.xml.SAXStore;

import java.util.*;
import java.net.URL;
import java.net.URLConnection;

/**
 * Implementation of a caching transformer output that assumes that an output depends on a set
 * of URIs associated with an input document.
 *
 * Usage: an URIReferences object must be cached as an object associated with the config input.
 */
public abstract class URIProcessorOutputImpl extends ProcessorImpl.ProcessorOutputImpl {

    private ProcessorImpl processorImpl;
    private String configInputName;
    private URIReferences localConfigURIReferences = null; // TODO: NIY

    public URIProcessorOutputImpl(ProcessorImpl processorImpl, String name, String configInputName) {
        super(processorImpl.getClass(), name);
        this.processorImpl = processorImpl;
        this.configInputName = configInputName;
    }

//        private void log(String message) {
//            logger.info("URIProcessorOutputImpl (" + getClass().getName() + ") " + message);
//        }

    protected OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
        final URIReferences uriReferences = getCachedURIReferences(pipelineContext);
//            log("uriReferences: " + uriReferences);
        if (uriReferences == null)
            return null;

        final List keys = new ArrayList();

        // Handle config if read as input
        if (localConfigURIReferences == null) {
            final ProcessorImpl.KeyValidity configKeyValidity = processorImpl.getInputKeyValidity(pipelineContext, configInputName);
//                log("configKeyValidity: " + configKeyValidity);
            if (configKeyValidity == null)
                return null;
            keys.add(configKeyValidity.key);
        }
        // Handle local key
        final ProcessorImpl.KeyValidity localKeyValidity = uriReferences.getLocalKeyValidity();
        if (localKeyValidity != null)
            keys.add(localKeyValidity.key);
        // Handle dependencies if any
//            log("uriReferences.getReferences(): " + uriReferences.getReferences());
        if (uriReferences.getReferences() != null) {
            for (Iterator i = uriReferences.getReferences().iterator(); i.hasNext();) {
                final URIReference uriReference = (URIReference) i.next();
                if (uriReference == null)
                    return null;
                final CacheKey uriKey = getURIKey(pipelineContext, uriReference);
//                    log("key: " + uriKey);
                keys.add(uriKey);
            }
        }
        final CacheKey[] outKys = new CacheKey[keys.size()];
        keys.toArray(outKys);
        return new CompoundOutputCacheKey(getProcessorClass(), getName(), outKys);
    }

    protected Object getValidityImpl(PipelineContext pipelineContext) {
        final URIReferences uriReferences = getCachedURIReferences(pipelineContext);
//            log("uriReferences: " + uriReferences);
        if (uriReferences == null)
            return null;

        final List validities = new ArrayList();

        // Handle config if read as input
        if (localConfigURIReferences == null) {
            final ProcessorImpl.KeyValidity configKeyValidity = processorImpl.getInputKeyValidity(pipelineContext, configInputName);
//                log("configKeyValidity: " + configKeyValidity);
            if (configKeyValidity == null)
                return null;
            validities.add(configKeyValidity.validity);
        }
        // Handle local validity
        final ProcessorImpl.KeyValidity localKeyValidity = uriReferences.getLocalKeyValidity();
        if (localKeyValidity != null)
            validities.add(localKeyValidity.validity);
        // Handle dependencies if any
//            log("uriReferences.getReferences(): " + uriReferences.getReferences());
        if (uriReferences.getReferences() != null) {
            for (Iterator i = uriReferences.getReferences().iterator(); i.hasNext();) {
                final URIReference uriReference = (URIReference) i.next();
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
     * This method returns the key associated with an URI. This is a default implementation
     * which works for "input:", "oxf:" and other URLs. However it is possible to override this
     * method, for example to optimize HTTP access.
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
                return new InternalCacheKey(processorImpl, "urlReference", URLFactory.createURL(uriReference.context, uriReference.spec).toExternalForm());
            }
        } catch (Exception e) {
            // If the file no longer exists, for example, we don't want to throw, just to invalidate
            // An exception will be thrown if necessary when the document is actually read
//                log("exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * This method returns the validity associated with an URI. This is a default implementation
     * which works for "input:", "oxf:" and other URLs. However it is possible to override this
     * method, for example to optimize HTTP access.
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
                    final long result = ResourceManagerWrapper.instance().lastModified(key);
                    // Zero and negative values often have a special meaning, make sure to normalize here
                    return (result <= 0) ? null : new Long(result);
                } else if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) {
                    // HTTP and HTTPS protocols: read and keep document

                    // Other URLs
                    final String urlString = url.toExternalForm();

                    // NOTE: We cache for this execution of this processor, so we don't make multiple accesses. The
                    // cache is discarded once the processor gets out of scope.

                    // Use cached state if possible
                    final URIReferencesState state = (URIReferencesState) processorImpl.getState(pipelineContext);
                    if (state.isDocumentSet(urlString))
                        return state.getLastModified(urlString);

                    final URLConnection urlConnection = url.openConnection();
                    try {
                        final long lastModified = urlConnection.getLastModified();
                        // We read the document and store it temporarily, since it will likely be read just after this anyway
                        final SAXStore documentSAXStore;
                        {
                            final URLGenerator urlGenerator = new URLGenerator(urlString);
                            final SAXStoreSerializer saxStoreSerializer = new SAXStoreSerializer();
                            PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, saxStoreSerializer, ProcessorImpl.INPUT_DATA);
                            final PipelineContext tempPipelineContext = new PipelineContext();
                            saxStoreSerializer.start(tempPipelineContext);
                            documentSAXStore = saxStoreSerializer.getSAXStore();
                        }
                        // Zero and negative values often have a special meaning, make sure to normalize here
                        final Long lastModifiedLong = lastModified <= 0 ? null : new Long(lastModified);

                        // Cache document and last modified
                        state.setDocument(urlString, documentSAXStore, lastModifiedLong);

                        return lastModifiedLong;
                    } finally {
                        if (urlConnection != null) {
                            urlConnection.getInputStream().close();
                        }
                    }

                } else  {
                    // Other URLs
                    final URLConnection urlConn = url.openConnection();
                    try {
                        long lastModified = NetUtils.getLastModified(urlConn);
                        // Zero and negative values often have a special meaning, make sure to normalize here
                        return lastModified <= 0 ? null : new Long(lastModified);
                    } finally {
                        if (urlConn != null) {
                            urlConn.getInputStream().close();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If the file no longer exists, for example, we don't want to throw, just to invalidate
            // An exception will be thrown if necessary when the document is actually read
//                log("exception: " + e.getMessage());
            return null;
        }
    }

    public SAXStore getDocument(PipelineContext pipelineContext, String urlString) {
        // Use cached state if possible
        // NOTE: We cache just for this execution of this processor, so we don't make multiple accesses
        final URIReferencesState state = (URIReferencesState) processorImpl.getState(pipelineContext);
        if (state.isDocumentSet(urlString))
            return state.getDocument(urlString);
        else
            return null;
    }

    private URIReferences getCachedURIReferences(PipelineContext pipelineContext) {
        // Check if config is external
        if (localConfigURIReferences != null)
            return localConfigURIReferences;

        // Make sure the config input is cacheable
        final ProcessorImpl.KeyValidity keyValidity = processorImpl.getInputKeyValidity(pipelineContext, configInputName);
        if (keyValidity == null)
            return null;

        // Try to find resource manager key in cache
        final URIReferences config = (URIReferences) ObjectCache.instance().findValid(pipelineContext, keyValidity.key, keyValidity.validity);
        if (ProcessorImpl.logger.isDebugEnabled()) {
            if (config != null)
                ProcessorImpl.logger.debug("Config (URIReferences) found: " + config.toString());
            else
                ProcessorImpl.logger.debug("Config (URIReferences) not found");
        }
        return config;
    }

    public static class URIReference {
        public URIReference(String context, String spec) {
            this.context = context;
            this.spec = spec;
        }

        public String context;
        public String spec;

        public String toString() {
            return "[" + context + ", " + spec + "]";
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

        private Map map;

        public void setDocument(String urlString, SAXStore documentSAXStore, Long lastModified) {
            if (map == null)
                map = new HashMap();
            map.put(urlString, new DocumentInfo(documentSAXStore, lastModified));
        }

        public boolean isDocumentSet(String urlString) {
            if (map == null)
                return false;
            return map.get(urlString) != null;
        }

        public Long getLastModified(String urlString) {
            final DocumentInfo documentInfo = (URIProcessorOutputImpl.DocumentInfo) map.get(urlString);
            return documentInfo.lastModified;
        }

        public SAXStore getDocument(String urlString) {
            final DocumentInfo documentInfo = (URIProcessorOutputImpl.DocumentInfo) map.get(urlString);
            return documentInfo.saxStore;
        }
    }

    /**
     * This is the object that must be associated with the configuration input and cached. Users can
     * derive from this and store their own configuration inside.
     */
    public static class URIReferences {

        private List references;
        private ProcessorImpl.KeyValidity localKeyValidity;

        /**
         * Add a URL reference.
         *
         * @param context   optional context (can be null)
         * @param spec      URL spec
         */
        public void addReference(String context, String spec) {
            if (references == null)
                references = new ArrayList();

//            logger.info("URIProcessorOutputImpl: adding reference: context = " + context + ", spec = " + spec);

            references.add(new URIReference(context, spec));
        }

        /**
         * Calling this makes sure the associated output cannot be cached.
         */
        public void setNoCache() {
            // Make sure we have an empty list of references
            if (references == null)
                references = new ArrayList();
            else
                references.clear();

            // Store a null reference to prevent caching
            references.add(null);
        }

        /**
         * Get URI references.
         *
         * @return  references or null if none
         */
        public List getReferences() {
            return references;
        }

        public void setLocalKeyValidity(ProcessorImpl.KeyValidity localKeyValidity) {
            this.localKeyValidity = localKeyValidity;
        }

        public ProcessorImpl.KeyValidity getLocalKeyValidity() {
            return localKeyValidity;
        }
    }
}
