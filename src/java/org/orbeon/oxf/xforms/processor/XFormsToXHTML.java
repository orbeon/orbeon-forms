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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.XHTMLBodyHandler;
import org.orbeon.oxf.xforms.processor.handlers.XHTMLHeadHandler;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.TransformerHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
public class XFormsToXHTML extends ProcessorImpl {

    static public Logger logger = XFormsServer.logger;

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String OUTPUT_DOCUMENT = "document";

    private static final KeyValidity CONSTANT_KEY_VALIDITY
            = new KeyValidity(new InternalCacheKey("sessionId", "NO_SESSION_DEPENDENCY"), new Long(0));

    public XFormsToXHTML() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DOCUMENT));
    }

    /**
     * Case where an XML response must be generated.
     */
    public ProcessorOutput createOutput(final String outputName) {
        ProcessorOutput output = new URIProcessorOutputImpl(XFormsToXHTML.this, outputName, INPUT_ANNOTATED_DOCUMENT) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler, this);
            }

            protected boolean supportsLocalKeyValidity() {
                return false;
            }

            public KeyValidity getLocalKeyValidity(PipelineContext pipelineContext, URIReferences uriReferences) {
                final InputDependencies inputDependencies = (XFormsToXHTML.InputDependencies) uriReferences;

                if (inputDependencies.isDependsOnSession()) {

                    // For now, cannot cache output when we depend on the session.
                    return null;

//                    // Make sure the session is created. It will be used anyway.
//                    final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
//                    final ExternalContext.Session session = externalContext.getSession(true);
//                    final String sessionId = session.getId();
//
//                    if (logger.isDebugEnabled())
//                        logger.debug("XForms - checking dependency on session id: " + sessionId);
//
//                    // Add dependency on session id
//                    return new KeyValidity(new InternalCacheKey(XFormsToXHTML.this, "sessionId", sessionId), new Long(0));
                } else {
                    return CONSTANT_KEY_VALIDITY;
                }
            }
        };
        addOutput(outputName, output);
        return output;
    }

    public void reset(PipelineContext context) {
        setState(context, new URIProcessorOutputImpl.URIReferencesState());
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler, ProcessorOutputImpl xhtmlOutput) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final XFormsServer.XFormsState[] xformsState = new XFormsServer.XFormsState[1];
        final boolean[] cachedInput = new boolean[1];

        // Read and try to cache the complete XForms+XHTML document with annotations
        final InputDependencies inputDependencies = (InputDependencies) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT), new CacheableInputReader() {
            public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                // Compute annotated XForms document + static state document
                final SAXStore annotatedSAXStore;
                final XFormsEngineStaticState xformsEngineStaticState;
                {
                    final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                    final LocationDocumentResult documentResult = new LocationDocumentResult();
                    identity.setResult(documentResult);

                    annotatedSAXStore = new SAXStore(new XFormsExtractor.XFormsExtractorContentHandler(pipelineContext, identity));
                    readInputAsSAX(pipelineContext, processorInput, annotatedSAXStore);
                    final Document staticStateDocument = documentResult.getDocument();

                    xformsEngineStaticState = new XFormsEngineStaticState(pipelineContext, staticStateDocument);
                }

                // Create document here so we can do appropriate analysis of caching dependencies
                createCacheContainingDocument(pipelineContext, xformsEngineStaticState, containingDocument, xformsState);

                // Set caching dependencies
                final InputDependencies inputDependencies = new InputDependencies(annotatedSAXStore, xformsEngineStaticState);
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

        try {
            // Create containing document if not done yet
            final String staticStateUUID;
            if (containingDocument[0] == null) {
                logger.debug("XForms - annotated document and static state obtained from cache; creating containing document.");
                createCacheContainingDocument(pipelineContext, inputDependencies.getXFormsEngineStaticState(), containingDocument, xformsState);
            } else {
                logger.debug("XForms - annotated document and static state not obtained from cache.");
            }

            if (cachedInput[0]) {
                staticStateUUID = inputDependencies.getUUID();
            } else {
                staticStateUUID = null;
            }

            // Try to cache dynamic state UUID associated with the output
            final String dynamicStateUUID = (String) getCacheOutputObject(pipelineContext, xhtmlOutput, "dynamicState", new OutputObjectCreator() {
                public Object create(PipelineContext pipelineContext, ProcessorOutput processorOutput) {
                    logger.debug("XForms - caching UUID for resulting document.");
                    return UUIDUtils.createPseudoUUID();
                }

                public void foundInCache() {
                    logger.debug("XForms - found cached UUID for resulting document.");
                }

                public void unableToCache() {
                    logger.debug("XForms - cannot cache UUID for resulting document.");
                }
            });

            // Output resulting document
            outputResponse(pipelineContext, externalContext, inputDependencies.getAnnotatedSAXStore(), containingDocument[0], contentHandler, xformsState[0], staticStateUUID, dynamicStateUUID);
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
        private XFormsEngineStaticState xformsEngineStaticState;
        private boolean dependsOnSession;

        private String uuid = UUIDUtils.createPseudoUUID();

        public InputDependencies(SAXStore annotatedSAXStore, XFormsEngineStaticState xformsEngineStaticState) {
            this.annotatedSAXStore = annotatedSAXStore;
            this.xformsEngineStaticState = xformsEngineStaticState;
        }

        public SAXStore getAnnotatedSAXStore() {
            return annotatedSAXStore;
        }

        public XFormsEngineStaticState getXFormsEngineStaticState() {
            return xformsEngineStaticState;
        }

        public boolean isDependsOnSession() {
            return dependsOnSession;
        }

        public void setDependsOnSession(boolean dependsOnSession) {
            this.dependsOnSession = dependsOnSession;
        }

        public String getUUID() {
            return uuid;
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
                inputDependencies.addReference(null, schemaURI);
            }

            // Add instance source dependencies
            for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) j.next();
                final String instanceSourceURI = currentInstance.getInstanceSourceURI();

                // For now we are not able to cache when an instance load uses a username and password
                if (currentInstance.isHasUsername()) {
                    if (logger.isDebugEnabled())
                        logger.debug("XForms - found instance load using username and password, disabling caching of output.");
                    inputDependencies.setNoCache();
                    return;
                }

                // Add dependency
                if (instanceSourceURI != null) {
                    if (logger.isDebugEnabled())
                        logger.debug("XForms - adding document cache dependency for instance: " + instanceSourceURI);
                    inputDependencies.addReference(null, instanceSourceURI);
                }
            }

            // TODO: Add @src attributes from controls
        }

        // Handle dependency on session id
        if (!containingDocument.getStateHandling().equals(XFormsConstants.XXFORMS_STATE_HANDLING_CLIENT_VALUE)) {
            inputDependencies.setDependsOnSession(true);
        }
    }

    private void createCacheContainingDocument(final PipelineContext pipelineContext, XFormsEngineStaticState xformsEngineStaticState,
                                               XFormsContainingDocument[] containingDocument, XFormsServer.XFormsState[] xformsState) {

        boolean[] requireClientSubmission = new boolean[1];
        {
            // Create initial state, before XForms initialization
            final XFormsServer.XFormsState initialXFormsState = new XFormsServer.XFormsState(xformsEngineStaticState.getEncodedStaticState(), "");

            // Create URIResolver
            final TransformerURIResolver uriResolver = new TransformerURIResolver(XFormsToXHTML.this, pipelineContext, INPUT_ANNOTATED_DOCUMENT, false) {
                public Source resolve(String href, String base) throws TransformerException {

                    final URL url;
                    try {
                        url = URLFactory.createURL(base, href);
                    } catch (MalformedURLException e) {
                        throw new OXFException(e);
                    }
                    final String urlString = url.toExternalForm();
                    final URIProcessorOutputImpl.URIReferencesState state = (URIProcessorOutputImpl.URIReferencesState) XFormsToXHTML.this.getState(pipelineContext);
                    if (state.isDocumentSet(urlString)) {
                        // This means the document requested is already available. We use the cached document.
                        final XMLReader xmlReader = new XMLFilterImpl() {
                            public void parse(String systemId) throws SAXException {
                                state.getDocument(urlString).replay(getContentHandler());
                            }

                            // FIXME: Is this necessary?
                            public void setFeature(String name, boolean state) throws SAXNotRecognizedException {
                                // We allow these two features
                                if (name.equals("http://xml.org/sax/features/namespaces") && state)
                                    return;
                                if (name.equals("http://xml.org/sax/features/namespace-prefixes") && !state)
                                    return;

                                // Otherwise we throw
                                throw new SAXNotRecognizedException("Feature: " + name);
                            }
                        };

                        if (logger.isDebugEnabled())
                            logger.debug("XForms - resolving resource through initialization resolver for URI: " + urlString);

                        return new SAXSource(xmlReader, new InputSource(urlString));
                    } else {
                        // Use parent resolver
                        return super.resolve(href, base);
                    }
                }
            };

            // Create containing document and initialize XForms engine
            containingDocument[0] = XFormsServer.createXFormsContainingDocument(pipelineContext, initialXFormsState, null, xformsEngineStaticState, uriResolver);

            // The URIResolver above doesn't make any sense anymore past initialization
            containingDocument[0].setURIResolver(null);

            // This is the state after XForms initialization
            final Document dynamicStateDocument = XFormsServer.createDynamicStateDocument(containingDocument[0], requireClientSubmission);
            xformsState[0] = new XFormsServer.XFormsState(initialXFormsState.getStaticState(), XFormsUtils.encodeXML(pipelineContext, dynamicStateDocument));
        }

        // Cache ContainingDocument if requested and possible
        {
            if (XFormsUtils.isCacheDocument()) {
                if (!requireClientSubmission[0]) {
                    // NOTE: We check on requireClientSubmission because the event is encoded
                    // in the dynamic state. But if we stored the event separately, then we
                    // could still cache the containing document.
                    XFormsServerDocumentCache.instance().add(pipelineContext, xformsState[0], containingDocument[0]);
                } else {
                    // Since we cannot cache the result, we have to get the object out of its current pool
                    final ObjectPool objectPool = containingDocument[0].getSourceObjectPool();
                    if (objectPool != null) {
                        logger.debug("XForms - containing document cache: discarding non-cacheable document from pool.");
                        try {
                            objectPool.invalidateObject(containingDocument);
                            containingDocument[0].setSourceObjectPool(null);
                        } catch (Exception e1) {
                            throw new OXFException(e1);
                        }
                    }
                }
            }
        }
    }

    private void outputResponse(final PipelineContext pipelineContext, final ExternalContext externalContext,
                                final SAXStore annotatedDocument, final XFormsContainingDocument containingDocument,
                                final ContentHandler contentHandler, final XFormsServer.XFormsState xformsState,
                                final String staticStateUUID, String dynamicStateUUID) throws SAXException {

        final ElementHandlerController controller = new ElementHandlerController();

        // Register handlers on controller (the other handlers are registered by the body handler)
        controller.registerHandler(XHTMLHeadHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "head");
        controller.registerHandler(XHTMLBodyHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "body");

        // Set final output with output to filter remaining xforms:* elements
        controller.setOutput(new DeferredContentHandlerImpl(new ForwardingContentHandler(contentHandler) {

            private int level = 0;
            private int xformsLevel = -1;

            public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                if (xformsLevel == -1) {
                    if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                        xformsLevel = level;
                    } else {
                        super.startElement(uri, localname, qName, attributes);
                    }
                }

                level++;
            }

            public void endElement(String uri, String localname, String qName) throws SAXException {
                level--;

                if (xformsLevel == level) {
                    xformsLevel = -1;
                } else if (xformsLevel == -1) {
                    super.endElement(uri, localname, qName);
                }
            }

            public void startPrefixMapping(String s, String s1) throws SAXException {
                if (xformsLevel == -1)
                    super.startPrefixMapping(s, s1);
            }

            public void endPrefixMapping(String s) throws SAXException {
                if (xformsLevel == -1)
                    super.endPrefixMapping(s);
            }

            public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
                if (xformsLevel == -1)
                    super.ignorableWhitespace(chars, start, length);
            }

            public void characters(char[] chars, int start, int length) throws SAXException {
                if (xformsLevel == -1)
                    super.characters(chars, start, length);
            }

            public void skippedEntity(String s) throws SAXException {
                if (xformsLevel == -1)
                    super.skippedEntity(s);
            }

            public void processingInstruction(String s, String s1) throws SAXException {
                if (xformsLevel == -1)
                    super.processingInstruction(s, s1);
            }

        }));

        controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, xformsState, staticStateUUID, dynamicStateUUID, externalContext));

        // Process everything
        annotatedDocument.replay(controller);
    }
}
