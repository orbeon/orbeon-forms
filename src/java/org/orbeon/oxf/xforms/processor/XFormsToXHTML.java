/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.XHTMLBodyHandler;
import org.orbeon.oxf.xforms.processor.handlers.XHTMLHeadHandler;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Locator;

import javax.xml.transform.sax.TransformerHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
public class XFormsToXHTML extends ProcessorImpl {

    private static final boolean TEST_BYPASS_INPUT = false;// TODO: for testing only
//    private static final boolean IS_MIGRATE_TO_SESSION = false;// TODO: for testing only

    public static Logger logger = XFormsServer.logger;

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String OUTPUT_DOCUMENT = "document";

    private static final String OUTPUT_CACHE_KEY = "dynamicState";

    private static final String NAMESPACE_CACHE_KEY = "containerNamespace";
    private static final Long CONSTANT_VALIDITY = new Long(0);

    private static InputDependencies testCachingStaticStateInputDependencies;

//    private static final KeyValidity CONSTANT_KEY_VALIDITY
//            = new KeyValidity(new InternalCacheKey("sessionId", "NO_SESSION_DEPENDENCY"), new Long(0));

    public XFormsToXHTML() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DOCUMENT));
    }

    /**
     * Case where an XML response must be generated.
     */
    public ProcessorOutput createOutput(final String outputName) {
        final ProcessorOutput output = new URIProcessorOutputImpl(XFormsToXHTML.this, outputName, INPUT_ANNOTATED_DOCUMENT) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler, this, outputName);
            }

//            protected OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
//                final OutputCacheKey outputCacheKey = super.getKeyImpl(pipelineContext);
//
//                if (IS_MIGRATE_TO_SESSION && outputCacheKey != null) {
//                    final InputDependencies inputDependencies = (InputDependencies) getCachedInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT));
//                    if (inputDependencies != null && inputDependencies.isDependsOnSession()) {
//                        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
//                        final ExternalContext.Session session = externalContext.getSession(true);
//
//                        // Find cached info
//                        final XFormsEngineStaticState staticState = inputDependencies.getXFormsEngineStaticState();
//                        final String staticStateUUID = staticState.getUUID();
//                        final String encodedStaticState = staticState.getEncodedStaticState();
//
//                        final String dynamicStateUUID = (String) getOutputObject(pipelineContext, this, OUTPUT_CACHE_KEY,
//                                new KeyValidity(outputCacheKey, getValidityImpl(pipelineContext)));
//
//                        // Migrate data to current session
//                    }
//                }
//
//                return outputCacheKey;
//            }

            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            public KeyValidity getLocalKeyValidity(PipelineContext pipelineContext, URIReferences uriReferences) {

                // Use the container namespace as a dependency
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                final String containerNamespace = externalContext.getRequest().getContainerNamespace();

                return new KeyValidity(new InternalCacheKey(XFormsToXHTML.this, NAMESPACE_CACHE_KEY, containerNamespace), CONSTANT_VALIDITY);
            }
        };
        addOutput(outputName, output);
        return output;
    }

    public void reset(PipelineContext context) {
        setState(context, new URIProcessorOutputImpl.URIReferencesState());
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler, final URIProcessorOutputImpl processorOutput, String outputName) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final XFormsState[] xformsState = new XFormsState[1];
        final boolean[] cachedInput = new boolean[1];

        // Read and try to cache the complete XForms+XHTML document with annotations
        final InputDependencies inputDependencies;
        if (testCachingStaticStateInputDependencies == null) {

            inputDependencies = (InputDependencies) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT), new CacheableInputReader() {
                public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                    // Create URIResolver
                    final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);

                    // Compute annotated XForms document + static state document
                    final SAXStore annotatedSAXStore;
                    final XFormsStaticState xformsStaticState;
                    {
                        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                        // TODO: Use TinyTree instead of dom4j Document
                        final LocationDocumentResult documentResult = new LocationDocumentResult();
                        identity.setResult(documentResult);

                        // TODO: Digest not used at this point
    //                    final XMLUtils.DigestContentHandler digestContentHandler = new XMLUtils.DigestContentHandler("MD5");

                        annotatedSAXStore = new SAXStore(new TeeContentHandler(new ContentHandler[] {
                                new XFormsExtractorContentHandler(pipelineContext, identity, uriResolver)
    //                            ,digestContentHandler
    //                            ,new SAXLoggerProcessor.DebugContentHandler()
                        }));

                        // Read the input
                        readInputAsSAX(pipelineContext, processorInput, annotatedSAXStore);

                        // Get the results
                        final Document staticStateDocument = documentResult.getDocument();
                        // TODO: Digest not used at this point
    //                    final String digest = Base64.encode(digestContentHandler.getResult());
    //                    if (XFormsServer.logger.isDebugEnabled())
    //                        XFormsServer.logger.debug("XForms - created digest for static state: " + digest);
    //                    xformsEngineStaticState = new XFormsStaticState(pipelineContext, staticStateDocument, digest);

                        xformsStaticState = new XFormsStaticState(staticStateDocument);
                    }

                    // Create document here so we can do appropriate analysis of caching dependencies
                    createCacheContainingDocument(pipelineContext, uriResolver, xformsStaticState, containingDocument, xformsState);

                    // Set caching dependencies
                    final InputDependencies inputDependencies = new InputDependencies(annotatedSAXStore, xformsStaticState);
                    setCachingDependencies(containingDocument[0], inputDependencies);

                    return inputDependencies;
                }

                public void foundInCache() {
                    cachedInput[0] = true;
                }

                public void storedInCache() {
                    cachedInput[0] = true;
                }
            });
            // For testing only
            if (TEST_BYPASS_INPUT)
                testCachingStaticStateInputDependencies = inputDependencies;
        } else {
            // For testing only
            inputDependencies = testCachingStaticStateInputDependencies;
        }

        try {
            // Create containing document if not done yet
            final String staticStateUUID;
            if (containingDocument[0] == null) {
                // In this case, we found the static state and more in the cache, but we must now create a new XFormsContainingDocument from this information
                logger.debug("XForms - annotated document and static state obtained from cache; creating containing document.");

                // Create URIResolver and XFormsContainingDocument
                final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);
                createCacheContainingDocument(pipelineContext, uriResolver, inputDependencies.getXFormsEngineStaticState(), containingDocument, xformsState);
            } else {
                logger.debug("XForms - annotated document and static state not obtained from cache.");
            }

            // Get static state UUID
            if (cachedInput[0]) {
                staticStateUUID = inputDependencies.getXFormsEngineStaticState().getUUID();
                logger.debug("XForms - found cached static state UUID.");
            } else {
                staticStateUUID = null;
                logger.debug("XForms - did not find cached static state UUID.");
            }

            // Try to cache dynamic state UUID associated with the output
            final String dynamicStateUUID = (String) getCacheOutputObject(pipelineContext, processorOutput, OUTPUT_CACHE_KEY, new OutputObjectCreator() {
                public Object create(PipelineContext pipelineContext, ProcessorOutput processorOutput) {
                    logger.debug("XForms - caching dynamic state UUID for resulting document.");
                    return UUIDUtils.createPseudoUUID();
                }

                public void foundInCache() {
                    logger.debug("XForms - found cached dynamic state UUID for resulting document.");
                }

                public void unableToCache() {
                    logger.debug("XForms - cannot cache dynamic state UUID for resulting document.");
                }
            });

            // Output resulting document
            if (outputName.equals("document"))
                outputResponseDocument(pipelineContext, externalContext, inputDependencies.getAnnotatedSAXStore(), containingDocument[0], contentHandler, xformsState[0], staticStateUUID, dynamicStateUUID);
            else
                testOutputResponseState(pipelineContext, externalContext, inputDependencies.getAnnotatedSAXStore(), containingDocument[0], contentHandler, xformsState[0], staticStateUUID, dynamicStateUUID);

        } catch (Throwable e) {
            if (containingDocument[0] != null) {
                // If an exception is caught, we need to discard the object as its state may be inconsistent
                final ObjectPool sourceObjectPool = containingDocument[0].getSourceObjectPool();
                if (sourceObjectPool != null) {
                    logger.debug("XForms - containing document cache: throwable caught, discarding document from pool.");
                    try {
                        sourceObjectPool.invalidateObject(containingDocument);
                        containingDocument[0].setSourceObjectPool(null);
                    } catch (Exception e1) {
                        throw new OXFException(e1);
                    }
                }
            }
            throw new OXFException(e);
        }
    }

    // What can be cached: URI dependencies + the annotated XForms document
    private static class InputDependencies extends URIProcessorOutputImpl.URIReferences {

        private SAXStore annotatedSAXStore;
        private XFormsStaticState xformsStaticState;
        private boolean dependsOnSession;

        public InputDependencies(SAXStore annotatedSAXStore, XFormsStaticState xformsStaticState) {
            this.annotatedSAXStore = annotatedSAXStore;
            this.xformsStaticState = xformsStaticState;
        }

        public SAXStore getAnnotatedSAXStore() {
            return annotatedSAXStore;
        }

        public XFormsStaticState getXFormsEngineStaticState() {
            return xformsStaticState;
        }

        public boolean isDependsOnSession() {
            return dependsOnSession;
        }

        public void setDependsOnSession(boolean dependsOnSession) {
            this.dependsOnSession = dependsOnSession;
        }
    }

    private void setCachingDependencies(XFormsContainingDocument containingDocument, InputDependencies inputDependencies) {

        // If a submission took place during XForms initialization, we currently don't cache
        // TODO: Some cases could be easily handled, like GET
        if (containingDocument.isGotSubmission()) {
            if (logger.isDebugEnabled())
                logger.debug("XForms - submission occurred during XForms initialization, disabling caching of output.");
            inputDependencies.setNoCache();
            return;
        }

        // Set caching dependencies if the input was actually read
        for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();

            // Add schema dependencies
            final String schemaURI = currentModel.getSchemaURI();
            if (schemaURI != null) {
                if (logger.isDebugEnabled())
                    logger.debug("XForms - adding document cache dependency for schema: " + schemaURI);
                inputDependencies.addReference(null, schemaURI, null, null);// TODO: support username / password on schema refs
            }

            // Add instance source dependencies
            if (currentModel.getInstances() != null) {
                for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                    final XFormsInstance currentInstance = (XFormsInstance) j.next();
                    final String instanceSourceURI = currentInstance.getSourceURI();

                    if (instanceSourceURI != null) {
                        if (!currentInstance.isApplicationShared()) {
                            // Add dependency only for instances that are not globally shared
                            if (logger.isDebugEnabled())
                                logger.debug("XForms - adding document cache dependency for instance: " + instanceSourceURI);
                            inputDependencies.addReference(null, instanceSourceURI, currentInstance.getUsername(), currentInstance.getPassword());
                        } else {
                            // Don't add the dependency as we don't want the instance URI to be hit
                            // For all practical purposes, globally shared instances must remain constant!
                            if (logger.isDebugEnabled())
                                logger.debug("XForms - not adding document cache dependency for application shared instance: " + instanceSourceURI);
                        }
                    }
                }
            }

            // TODO: Add @src attributes from controls?
        }

        // Handle dependency on session id
        if (containingDocument.isSessionStateHandling()) {
            inputDependencies.setDependsOnSession(true);
        }
    }

    private void createCacheContainingDocument(final PipelineContext pipelineContext, XFormsURIResolver uriResolver, XFormsStaticState xformsStaticState,
                                               XFormsContainingDocument[] containingDocument, XFormsState[] xformsState) {
        {
            // Create containing document and initialize XForms engine
            containingDocument[0] = new XFormsContainingDocument(pipelineContext, xformsStaticState, uriResolver);

            // Make sure we have up to date controls before creating state below
            final XFormsControls xformsControls = containingDocument[0].getXFormsControls();
            xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);

            // This is the state after XForms initialization
            xformsState[0] = new XFormsState(xformsStaticState.getEncodedStaticState(pipelineContext),
                    containingDocument[0].createEncodedDynamicState(pipelineContext));
        }

        // Cache ContainingDocument if requested and possible
        {
            if (XFormsUtils.isCacheDocument()) {
                XFormsServerDocumentCache.instance().add(pipelineContext, xformsState[0], containingDocument[0]);
            }
        }
    }

    private void outputResponseDocument(final PipelineContext pipelineContext, final ExternalContext externalContext,
                                final SAXStore annotatedDocument, final XFormsContainingDocument containingDocument,
                                final ContentHandler contentHandler, final XFormsState xformsState,
                                final String staticStateUUID, String dynamicStateUUID) throws SAXException {

        final ElementHandlerController controller = new ElementHandlerController();

        // Make sure we have up to date controls (should already be the case)
        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);
        xformsControls.evaluateAllControlsIfNeeded(pipelineContext);

        // Register handlers on controller (the other handlers are registered by the body handler)
        controller.registerHandler(XHTMLHeadHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "head");
        controller.registerHandler(XHTMLBodyHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "body");

        // Set final output with output to filter remaining xforms:* elements if any
        // TODO: Remove this filter once the "exception elements" below are filtered at the source.
        controller.setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(contentHandler)));

        controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, xformsState, staticStateUUID, dynamicStateUUID, externalContext));

        // Process everything
        annotatedDocument.replay(new ElementFilterContentHandler(controller) {
            protected boolean isFilterElement(String uri, String localname, String qName, Attributes attributes) {
                // We filter everything that is not a control
                // TODO: There are some temporary exceptions, but those should actually be handled by the ControlInfo in the first place
                return (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri) && !(localname.equals("img") || localname.equals("dialog")))
                        || (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)
                            && !(XFormsControls.isActualControl(localname) || exceptionXFormsElements.get(localname) != null));
            }

            // Below we wrap all the exceptions to try to add location information
            private Locator locator;

            public void setDocumentLocator(Locator locator) {
                super.setDocumentLocator(locator);
                this.locator = locator;
            }

            public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                try {
                    super.startElement(uri, localname, qName, attributes);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void endElement(String uri, String localname, String qName) throws SAXException {
                try {
                    super.endElement(uri, localname, qName);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void characters(char[] chars, int start, int length) throws SAXException {
                try {
                    super.characters(chars, start, length);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void startPrefixMapping(String s, String s1) throws SAXException {
                try {
                    super.startPrefixMapping(s, s1);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void endPrefixMapping(String s) throws SAXException {
                try {
                    super.endPrefixMapping(s);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
                try {
                    super.ignorableWhitespace(chars, start, length);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void skippedEntity(String s) throws SAXException {
                try {
                    super.skippedEntity(s);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void processingInstruction(String s, String s1) throws SAXException {
                try {
                    super.processingInstruction(s, s1);
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void endDocument() throws SAXException {
                try {
                    super.endDocument();
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            public void startDocument() throws SAXException {
                try {
                    super.startDocument();
                } catch (RuntimeException e) {
                    wrapException(e);
                }
            }

            private void wrapException(Exception e) throws SAXException {
                if (locator != null)
                    throw ValidationException.wrapException(e, new ExtendedLocationData(locator, "converting XHTML+XForms document to XHTML"));
                else if (e instanceof SAXException)
                    throw (SAXException) e;
                else if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                else
                    throw new OXFException(e);// this should not happen
            }
        });
    }

    private static final Map exceptionXFormsElements = new HashMap();

    static {
        exceptionXFormsElements.put("item", "");
        exceptionXFormsElements.put("itemset", "");
        exceptionXFormsElements.put("choices", "");
        exceptionXFormsElements.put("value", "");
        exceptionXFormsElements.put("label", "");
        exceptionXFormsElements.put("hint", "");
        exceptionXFormsElements.put("help", "");
        exceptionXFormsElements.put("alert", "");
    }

    private void testOutputResponseState(final PipelineContext pipelineContext, final ExternalContext externalContext,
                                     final SAXStore annotatedDocument, final XFormsContainingDocument containingDocument,
                                     final ContentHandler contentHandler, final XFormsState xformsState,
                                     final String staticStateUUID, String dynamicStateUUID) throws SAXException {

        // Make sure we have up to date controls
        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);

        // Output XML response
        XFormsServer.outputResponse(containingDocument, false, null, pipelineContext, contentHandler, staticStateUUID, dynamicStateUUID, externalContext, xformsState, false, true);
    }
}
