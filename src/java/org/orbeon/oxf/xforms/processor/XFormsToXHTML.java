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
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.XFormsAnnotatorContentHandler;
import org.orbeon.oxf.xforms.analysis.XFormsExtractorContentHandler;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xforms.state.XFormsDocumentCache;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.submission.AsynchronousSubmissionManager;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.io.IOException;
import java.util.List;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
public class XFormsToXHTML extends ProcessorImpl {

    private static final boolean TEST_BYPASS_INPUT = false;// TODO: for testing only

    private static final boolean ALLOW_CACHING_OUTPUT = false;

    public static final String LOGGING_CATEGORY = "html";
    private static final Logger logger = LoggerFactory.createLogger(XFormsToXHTML.class);

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String OUTPUT_DOCUMENT = "document";

    private static final String OUTPUT_CACHE_KEY = "dynamicState";

    private static final String NAMESPACE_CACHE_KEY = "containerNamespace";
    private static final Long CONSTANT_VALIDITY = (long) 0;

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
                    final String containerNamespace = XFormsUtils.getExternalContext(pipelineContext).getRequest().getContainerNamespace();

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

        final ExternalContext externalContext = XFormsUtils.getExternalContext(pipelineContext);
        final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(XFormsToXHTML.logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

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

//                        annotatedSAXStore = new SAXStore(new TeeContentHandler(new ContentHandler[] {
//                                new XFormsExtractorContentHandler(externalContext, identity)
//    //                            ,new SAXLoggerProcessor.DebugContentHandler()
//                        }));

//                        annotatedSAXStore = new SAXStore(new XFormsExtractorContentHandler(externalContext, new SAXLoggerProcessor.DebugContentHandler(identity)));

                        final XFormsAnnotatorContentHandler.Metadata metadata = new XFormsAnnotatorContentHandler.Metadata();
                        annotatedSAXStore = new SAXStore(new XFormsExtractorContentHandler(externalContext, identity, metadata.idGenerator));

                        // Read the input through the annotator and gather namespace mappings
                        readInputAsSAX(pipelineContext, processorInput, new XFormsAnnotatorContentHandler(annotatedSAXStore, externalContext, metadata));

                        // Get static state document and create static state object
                        final Document staticStateDocument = documentResult.getDocument();
//                        XFormsContainingDocument.logDebugStatic("XForms to XHTML", "static state", new String[] { "document", Dom4jUtils.domToString(staticStateDocument) });
                        xformsStaticState = new XFormsStaticState(pipelineContext, staticStateDocument, metadata, annotatedSAXStore);
                    }

                    // Create document here so we can do appropriate analysis of caching dependencies
                    createCacheContainingDocument(pipelineContext, uriResolver, xformsStaticState, containingDocument, xformsState);

                    // Set caching dependencies
                    final InputDependencies inputDependencies = new InputDependencies(annotatedSAXStore, xformsStaticState);
                    setCachingDependencies(containingDocument[0], indentedLogger, inputDependencies);

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
                indentedLogger.logDebug("", "annotated document and static state obtained from cache; creating containing document.");

                // Create URIResolver and XFormsContainingDocument
                final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);
                createCacheContainingDocument(pipelineContext, uriResolver, inputDependencies.getXFormsEngineStaticState(), containingDocument, xformsState);
            } else {
                indentedLogger.logDebug("", "annotated document and static state not obtained from cache.");
            }

            // Get static state UUID
            if (cachedInput[0]) {
                staticStateUUID = inputDependencies.getXFormsEngineStaticState().getUUID();
                indentedLogger.logDebug("", "found cached static state UUID.");
            } else {
                staticStateUUID = null;
                indentedLogger.logDebug("", "did not find cached static state UUID.");
            }

            // Try to cache dynamic state UUID associated with the output
            final String dynamicStateUUID = (String) getCacheOutputObject(pipelineContext, processorOutput, OUTPUT_CACHE_KEY, new OutputObjectCreator() {
                public Object create(PipelineContext pipelineContext, ProcessorOutput processorOutput) {
                    indentedLogger.logDebug("", "caching dynamic state UUID for resulting document.");
                    return UUIDUtils.createPseudoUUID();
                }

                public void foundInCache() {
                    indentedLogger.logDebug("", "found cached dynamic state UUID for resulting document.");
                }

                public void unableToCache() {
                    indentedLogger.logDebug("", "cannot cache dynamic state UUID for resulting document.");
                }
            });

            // Output resulting document
            if (outputName.equals("document")) {
                // Normal case where we output XHTML

                // Get encoded state for the client
                final XFormsState encodedClientState = XFormsStateManager.getInitialEncodedClientState(containingDocument[0],
                        externalContext, xformsState[0], staticStateUUID, dynamicStateUUID);

                outputResponseDocument(pipelineContext, externalContext, indentedLogger, inputDependencies.getAnnotatedSAXStore(),
                        containingDocument[0], contentHandler, encodedClientState);
            } else {
                // Test only
                testOutputResponseState(pipelineContext, containingDocument[0], indentedLogger, contentHandler,
                        new XFormsStateManager.XFormsDecodedClientState(xformsState[0], staticStateUUID, dynamicStateUUID));
            }

        } catch (Throwable e) {
            if (containingDocument[0] != null) {
                // If an exception is caught, we need to discard the object as its state may be inconsistent
                final ObjectPool sourceObjectPool = containingDocument[0].getSourceObjectPool();
                if (sourceObjectPool != null) {
                    indentedLogger.logDebug("", "containing document cache: throwable caught, discarding document from pool.");
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

        private final SAXStore annotatedSAXStore;
        private final XFormsStaticState xformsStaticState;

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

    private void setCachingDependencies(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger, InputDependencies inputDependencies) {

        // If a submission took place during XForms initialization, we currently don't cache
        // TODO: Some cases could be easily handled, like GET
        if (containingDocument.isGotSubmission()) {
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("", "submission occurred during XForms initialization, disabling caching of output.");
            inputDependencies.setNoCache();
            return;
        }

        // Set caching dependencies if the input was actually read
        // WIP INSTANCE INSPECTOR: for (Iterator i = containingDocument.getAllModels().iterator(); i.hasNext();) {
        for (final XFormsModel currentModel: containingDocument.getModels()) {
            // Add schema dependencies
            final String[] schemaURIs = currentModel.getSchemaURIs();
            // TODO: We should also use dependencies computed in XFormsModelSchemaValidator.SchemaInfo
            if (schemaURIs != null) {
                for (final String currentSchemaURI: schemaURIs) {
                    if (indentedLogger.isDebugEnabled())
                        indentedLogger.logDebug("", "adding document cache dependency for schema", "schema URI", currentSchemaURI);
                    inputDependencies.addReference(null, currentSchemaURI, null, null,
                            XFormsProperties.getForwardSubmissionHeaders(containingDocument));// TODO: support username / password on schema refs
                }
            }

            // Add instance source dependencies
            if (currentModel.getInstances() != null) {
                for (final XFormsInstance currentInstance: currentModel.getInstances()) {
                    final String instanceSourceURI = currentInstance.getSourceURI();

                    if (instanceSourceURI != null) {
                        if (!currentInstance.isCache()) {
                            // Add dependency only for instances that are not globally shared
                            if (indentedLogger.isDebugEnabled())
                                indentedLogger.logDebug("", "adding document cache dependency for non-cacheable instance", "instance URI", instanceSourceURI);
                            inputDependencies.addReference(null, instanceSourceURI, currentInstance.getUsername(), currentInstance.getPassword(),
                                    XFormsProperties.getForwardSubmissionHeaders(containingDocument));
                        } else {
                            // Don't add the dependency as we don't want the instance URI to be hit
                            // For all practical purposes, globally shared instances must remain constant!
                            if (indentedLogger.isDebugEnabled())
                                indentedLogger.logDebug("", "not adding document cache dependency for cacheable instance", "instance URI", instanceSourceURI);
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

            // This is the state after XForms initialization
            xformsState[0] = containingDocument[0].getXFormsState(pipelineContext);
        }

        // Cache ContainingDocument if requested and possible
        {
            if (XFormsProperties.isCacheDocument()) {
                XFormsDocumentCache.instance().add(pipelineContext, xformsState[0], containingDocument[0]);
            }
        }
    }

    public static void outputResponseDocument(final PipelineContext pipelineContext, final ExternalContext externalContext,
                                final IndentedLogger indentedLogger,
                                final SAXStore annotatedDocument, final XFormsContainingDocument containingDocument,
                                final ContentHandler contentHandler, final XFormsState encodedClientState) throws SAXException, IOException {

        final List<XFormsContainingDocument.Load> loads = containingDocument.getLoadsToRun();
        if (containingDocument.isGotSubmissionReplaceAll()) {
            // 1. Got a submission with replace="all"

            // NOP: Response already sent out by a submission
            // TODO: modify XFormsModelSubmission accordingly
            indentedLogger.logDebug("", "handling response for submission with replace=\"all\"");
        } else if (loads != null && loads.size() > 0) {
            // 2. Got at least one xforms:load

            // Send redirect out

            // Get first load only
            final XFormsContainingDocument.Load load = loads.get(0);

            // Send redirect
            final String redirectResource = load.getResource();
            indentedLogger.logDebug("", "handling redirect response for xforms:load", "url", redirectResource);
            // Set isNoRewrite to true, because the resource is either a relative path or already contains the servlet context
            externalContext.getResponse().sendRedirect(redirectResource, null, false, false, true);

            // Still send out a null document to signal that no further processing must take place
            XMLUtils.streamNullDocument(contentHandler);
        } else {
            // 3. Regular case: produce an XHTML document out

            final ElementHandlerController controller = new ElementHandlerController();

            // Register handlers on controller (the other handlers are registered by the body handler)
            {
                controller.registerHandler(XHTMLHeadHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "head");
                controller.registerHandler(XHTMLBodyHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "body");

                // Register a handler for AVTs on HTML elements
                final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
                if (hostLanguageAVTs) {
                    controller.registerHandler(XXFormsAttributeHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute");
                    controller.registerHandler(XHTMLElementHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI);
                }

                // Swallow XForms elements that are unknown
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI);
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XBL_NAMESPACE_URI);
            }

            // Set final output
            controller.setOutput(new DeferredContentHandlerImpl(contentHandler));
            // Set handler context
            controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, encodedClientState, externalContext));
            // Process the entire input
            annotatedDocument.replay(new ExceptionWrapperContentHandler(controller, "converting XHTML+XForms document to XHTML"));

            // Process foreground asynchronous submissions
            // NOTE: Given the complexity of the epilogue, this could cause the page to stop loading until all submissions
            // are processed, even though that is not meant to happen.
            final AsynchronousSubmissionManager asynchronousSubmissionManager = containingDocument.getAsynchronousSubmissionManager(false);
            if (asynchronousSubmissionManager != null)
                asynchronousSubmissionManager.processForegroundAsynchronousSubmissions();
        }
    }

    private void testOutputResponseState(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument,
                                         final IndentedLogger indentedLogger, final ContentHandler contentHandler,
                                         final XFormsStateManager.XFormsDecodedClientState xformsDecodedClientState) throws SAXException {
        // Output XML response
        XFormsServer.outputAjaxResponse(containingDocument, indentedLogger, null, pipelineContext, contentHandler, xformsDecodedClientState, null, false, false, false, true);
    }
}
