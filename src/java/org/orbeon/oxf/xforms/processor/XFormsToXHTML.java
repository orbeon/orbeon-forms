/**
 *  Copyright (C) 2005-2008 Orbeon, Inc.
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
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.util.URLRewriter;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xforms.state.XFormsDocumentCache;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.io.IOException;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
public class XFormsToXHTML extends ProcessorImpl {

    private static final boolean TEST_BYPASS_INPUT = false;// TODO: for testing only

    private static final boolean ALLOW_CACHING_OUTPUT = false;

    public static Logger logger = XFormsServer.logger;

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String OUTPUT_DOCUMENT = "document";

    private static final String OUTPUT_CACHE_KEY = "dynamicState";

    private static final String NAMESPACE_CACHE_KEY = "containerNamespace";
    private static final Long CONSTANT_VALIDITY = new Long(0);

    private static InputDependencies testCachingStaticStateInputDependencies;

    public XFormsToXHTML() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
        addInputInfo(new ProcessorInputOutputInfo("namespace")); // This input ensures that we depend on a portlet namespace
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

            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            public KeyValidity getLocalKeyValidity(PipelineContext pipelineContext, URIReferences uriReferences) {
                if (ALLOW_CACHING_OUTPUT) {
                    // Use the container namespace as a dependency
                    final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                    final String containerNamespace = externalContext.getRequest().getContainerNamespace();

                    return new KeyValidity(new InternalCacheKey(XFormsToXHTML.this, NAMESPACE_CACHE_KEY, containerNamespace), CONSTANT_VALIDITY);
                } else {
                    // Disable caching of the output
                    return null;
                }
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
        final boolean[] cachedInput = new boolean[] { TEST_BYPASS_INPUT } ;

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

                        annotatedSAXStore = new SAXStore(new TeeContentHandler(new ContentHandler[] {
                                new XFormsExtractorContentHandler(pipelineContext, identity)
    //                            ,new SAXLoggerProcessor.DebugContentHandler()
                        }));

                        // Read the input through the annotator and gather namespace mappings
                        final Map namespaceMappings = new HashMap();
                        readInputAsSAX(pipelineContext, processorInput, new XFormsDocumentAnnotatorContentHandler(annotatedSAXStore, externalContext, namespaceMappings));

                        // Get static state document and create static state object
                        final Document staticStateDocument = documentResult.getDocument();
//                        XFormsContainingDocument.logDebugStatic("XForms to XHTML", "static state", new String[] { "document", Dom4jUtils.domToString(staticStateDocument) });
                        xformsStaticState = new XFormsStaticState(pipelineContext, staticStateDocument, namespaceMappings, annotatedSAXStore);
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
            if (outputName.equals("document")) {
                // Normal case where we output XHTML

                // Get encoded state for the client
                final XFormsState encodedClientState = XFormsStateManager.getInitialEncodedClientState(containingDocument[0],
                        externalContext, xformsState[0], staticStateUUID, dynamicStateUUID);

                outputResponseDocument(pipelineContext, externalContext, inputDependencies.getAnnotatedSAXStore(), containingDocument[0], contentHandler, encodedClientState);
            } else {
                // Test only
                testOutputResponseState(pipelineContext, containingDocument[0], contentHandler, new XFormsStateManager.XFormsDecodedClientState(xformsState[0], staticStateUUID, dynamicStateUUID));
            }

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
        // TODO: Nested containers
        for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();

            // Add schema dependencies
            final String[] schemaURIs = currentModel.getSchemaURIs();
            // TODO: We should also use dependencies computed in XFormsModelSchemaValidator.SchemaInfo
            if (schemaURIs != null) {
                for (int j = 0; j < schemaURIs.length; j++) {
                    final String currentSchemaURI = schemaURIs[j];
                    if (logger.isDebugEnabled())
                        logger.debug("XForms - adding document cache dependency for schema: " + currentSchemaURI);
                    inputDependencies.addReference(null, currentSchemaURI, null, null,
                            XFormsProperties.getForwardSubmissionHeaders(containingDocument));// TODO: support username / password on schema refs
                }
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
                            inputDependencies.addReference(null, instanceSourceURI, currentInstance.getUsername(), currentInstance.getPassword(),
                                    XFormsProperties.getForwardSubmissionHeaders(containingDocument));
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
    }

    private void createCacheContainingDocument(final PipelineContext pipelineContext, XFormsURIResolver uriResolver, XFormsStaticState xformsStaticState,
                                               XFormsContainingDocument[] containingDocument, XFormsState[] xformsState) {
        {
            // Create containing document and initialize XForms engine
            containingDocument[0] = new XFormsContainingDocument(pipelineContext, xformsStaticState, uriResolver);

            // Make sure we have up to date controls before creating state below
            final XFormsControls xformsControls = containingDocument[0].getControls();
            xformsControls.updateControlBindingsIfNeeded(pipelineContext);

            // This is the state after XForms initialization
            xformsState[0] = new XFormsState(xformsStaticState.getEncodedStaticState(pipelineContext),
                    containingDocument[0].createEncodedDynamicState(pipelineContext, false));
        }

        // Cache ContainingDocument if requested and possible
        {
            if (XFormsProperties.isCacheDocument()) {
                XFormsDocumentCache.instance().add(pipelineContext, xformsState[0], containingDocument[0]);
            }
        }
    }

    public static void outputResponseDocument(final PipelineContext pipelineContext, final ExternalContext externalContext,
                                final SAXStore annotatedDocument, final XFormsContainingDocument containingDocument,
                                final ContentHandler contentHandler, final XFormsState encodedClientState) throws SAXException, IOException {

        final ElementHandlerController controller = new ElementHandlerController();

        // Make sure we have up to date controls
        final XFormsControls xformsControls = containingDocument.getControls();
        xformsControls.updateControlBindingsIfNeeded(pipelineContext);
        xformsControls.evaluateControlValuesIfNeeded(pipelineContext);

        final List loads = containingDocument.getLoadsToRun();
        if (containingDocument.isGotSubmissionReplaceAll()) {
            // 1. Got a submission with replace="all"

            // NOP: Response already sent out by a submission
            // TODO: modify XFormsModelSubmission accordingly
            containingDocument.logDebug("XForms initialization", "handling response for submission with replace=\"all\"");
        } else if (loads != null && loads.size() > 0) {
            // 2. Got at least one xforms:load

            // Send redirect out

            // Get first load only
            final XFormsContainingDocument.Load load = (XFormsContainingDocument.Load) loads.get(0);

            // Send redirect
            final String absoluteURL = URLRewriter.rewriteURL(externalContext.getRequest(), load.getResource(), ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
            containingDocument.logDebug("XForms initialization", "handling response for xforms:load", new String[] { "url", absoluteURL });
            externalContext.getResponse().sendRedirect(absoluteURL, null, false, false);

            // Still send out a null document to signal that no further processing must take place
            XMLUtils.streamNullDocument(contentHandler);
        } else {
            // 3. Regular case: produce an XHTML document out

            // Register handlers on controller (the other handlers are registered by the body handler)
            {
                controller.registerHandler(XHTMLHeadHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "head");
                controller.registerHandler(XHTMLBodyHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "body");

                // Register a handler for AVTs on HTML elements
                final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
                if (hostLanguageAVTs) {
                    controller.registerHandler(XXFormsAttributeHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute");
                    controller.registerHandler(XHTMLElementHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, null);
                }

                // Swallow XForms elements that are unknown
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, null);
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, null);
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XBL_NAMESPACE_URI, null);
            }

            // Set final output
            controller.setOutput(new DeferredContentHandlerImpl(contentHandler));
            // Set handler context
            controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, encodedClientState, externalContext));
            // Process the entire input
            annotatedDocument.replay(new ExceptionWrapperContentHandler(controller, "converting XHTML+XForms document to XHTML"));

            // Process asynchronous submissions
            // NOTE: Given the complexity of the epilogue, this could cause the page to stop loading until all submissions
            // are processed.
            containingDocument.processAsynchronousSubmissions();
        }
    }

    private void testOutputResponseState(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument,
                                         final ContentHandler contentHandler, final XFormsStateManager.XFormsDecodedClientState xformsDecodedClientState) throws SAXException {

        // Make sure we have up to date controls
        final XFormsControls xformsControls = containingDocument.getControls();
        xformsControls.updateControlBindingsIfNeeded(pipelineContext);
        xformsControls.evaluateControlValuesIfNeeded(pipelineContext);

        // Output XML response
        XFormsServer.outputAjaxResponse(containingDocument, null, pipelineContext, contentHandler, xformsDecodedClientState, null, false, false, false, true);
    }

}
