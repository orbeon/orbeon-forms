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

    public static final String LOGGING_CATEGORY = "html";
    private static final Logger logger = LoggerFactory.createLogger(XFormsToXHTML.class);

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String OUTPUT_DOCUMENT = "document";

    private static final String OUTPUT_CACHE_KEY = "dynamicState";

    public XFormsToXHTML() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
        addInputInfo(new ProcessorInputOutputInfo("namespace")); // This input ensures that we depend on a portlet namespace
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DOCUMENT));
    }

    /**
     * Case where an XML response must be generated.
     */
    @Override
    public ProcessorOutput createOutput(final String outputName) {
        final ProcessorOutput output = new URIProcessorOutputImpl(XFormsToXHTML.this, outputName, INPUT_ANNOTATED_DOCUMENT) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler, this, outputName);
            }

            @Override
            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            @Override
            public KeyValidity getLocalKeyValidity(PipelineContext pipelineContext, URIReferences uriReferences) {
                // NOTE: As of 2010-03, caching of the output should never happen
                // o more work is needed to make this work properly
                // o not many use cases benefit
                return null;
            }
        };
        addOutput(outputName, output);
        return output;
    }

    @Override
    public ProcessorInput createInput(final String inputName) {
        if (inputName.equals(INPUT_ANNOTATED_DOCUMENT)) {
            // Insert processor on the fly to handle dependencies. This is a bit tricky: we used to have an
            // XSLT/XInclude before XFormsToXHTML. This step handled XBL dependencies. Now that it is removed, we
            // need a mechanism to detect dependencies. So we insert a step here.

            // Return an input which handles dependencies
            // The system actually has two processors:
            // o stage1 is the processor automatically inserted below for the purpose of handling dependencies
            // o stage2 is the actual oxf:xforms-to-xhtml which actually does XForms processing
            final ProcessorInput originalInput = super.createInput(inputName);
            return new DependenciesProcessorInput(inputName, originalInput) {
                @Override
                protected URIProcessorOutputImpl.URIReferences getURIReferences(PipelineContext pipelineContext) {
                    // Return dependencies object, set by stage2 before reading its input
                    return ((Stage2TransientState) XFormsToXHTML.this.getState(pipelineContext)).stage1CacheableState;
                }
            };
        } else {
            return super.createInput(inputName);
        }
    }

    @Override
    public void reset(PipelineContext context) {
        setState(context, new Stage2TransientState());
    }

    // State passed by the second stage to the first stage.
    // NOTE: This extends URIReferencesState because we use URIProcessorOutputImpl.
    // It is not clear that we absolutely need URIProcessorOutputImpl in the second stage, but right now we keep it,
    // because XFormsURIResolver requires URIProcessorOutputImpl.
    private class Stage2TransientState extends URIProcessorOutputImpl.URIReferencesState {
        public Stage1CacheableState stage1CacheableState;
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler, final URIProcessorOutputImpl processorOutput, String outputName) {

        final ExternalContext externalContext = XFormsUtils.getExternalContext(pipelineContext);
        final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(XFormsToXHTML.logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final XFormsState[] xformsState = new XFormsState[1];
        final boolean[] cachedInput = new boolean[] { false } ;

        // Read and try to cache the complete XForms+XHTML document with annotations
        final Stage2CacheableState stage2CacheableState = (Stage2CacheableState) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT),
                new CacheableInputReader() {
                    public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                        // Create URIResolver
                        final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput,
                                pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);

                        // Compute annotated XForms document + static state document
                        final Stage1CacheableState stage1CacheableState = new Stage1CacheableState();
                        final Stage2CacheableState stage2CacheableState;
                        {
                            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                            // TODO: Use TinyTree instead of dom4j Document
                            final LocationDocumentResult documentResult = new LocationDocumentResult();
                            identity.setResult(documentResult);

                            final XFormsAnnotatorContentHandler.Metadata metadata = new XFormsAnnotatorContentHandler.Metadata();
                            final SAXStore annotatedSAXStore = new SAXStore(new XFormsExtractorContentHandler(externalContext, identity, metadata));

                            // Store dependencies container in state before reading
                            ((Stage2TransientState) XFormsToXHTML.this.getState(pipelineContext)).stage1CacheableState = stage1CacheableState;

                            // Read the input through the annotator and gather namespace mappings
                            readInputAsSAX(pipelineContext, processorInput, new XFormsAnnotatorContentHandler(annotatedSAXStore, externalContext, metadata));

                            // Get static state document and create static state object
                            final Document staticStateDocument = documentResult.getDocument();
                            final XFormsStaticState xformsStaticState = new XFormsStaticState(pipelineContext, staticStateDocument, metadata, annotatedSAXStore);

                            // Update input dependencies object
                            stage2CacheableState = new Stage2CacheableState(annotatedSAXStore, xformsStaticState);
                        }

                        // Create document here so we can do appropriate analysis of caching dependencies
                        createCacheContainingDocument(pipelineContext, uriResolver, stage2CacheableState.getXFormsEngineStaticState(),
                                containingDocument, xformsState);

                        // Gather set caching dependencies
                        gatherInputDependencies(containingDocument[0], indentedLogger, stage1CacheableState);

                        return stage2CacheableState;
                    }

                    @Override
                    public void foundInCache() {
                        cachedInput[0] = true;
                    }

                    @Override
                    public void storedInCache() {
                        cachedInput[0] = true;
                    }
                });

        try {
            // Create containing document if not done yet
            final String staticStateUUID;
            if (containingDocument[0] == null) {
                // In this case, we found the static state and more in the cache, but we must now create a new XFormsContainingDocument from this information
                indentedLogger.logDebug("", "annotated document and static state obtained from cache; creating containing document.");

                // Create URIResolver and XFormsContainingDocument
                final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);
                createCacheContainingDocument(pipelineContext, uriResolver, stage2CacheableState.getXFormsEngineStaticState(), containingDocument, xformsState);
            } else {
                indentedLogger.logDebug("", "annotated document and static state not obtained from cache.");
            }

            // Get static state UUID
            if (cachedInput[0]) {
                staticStateUUID = stage2CacheableState.getXFormsEngineStaticState().getUUID();
                indentedLogger.logDebug("", "found cached static state UUID.");
            } else {
                staticStateUUID = null;
                indentedLogger.logDebug("", "did not find cached static state UUID.");
            }

            // Try to cache dynamic state UUID associated with the output
            // NOTE: As of 2010-03, caching of the output should never happen because we disable it
            final String dynamicStateUUID = (String) getCacheOutputObject(pipelineContext, processorOutput, OUTPUT_CACHE_KEY, new OutputObjectCreator() {
                public Object create(PipelineContext pipelineContext, ProcessorOutput processorOutput) {
                    indentedLogger.logDebug("", "caching dynamic state UUID for resulting document.");
                    return UUIDUtils.createPseudoUUID();
                }

                @Override
                public void foundInCache() {
                    indentedLogger.logDebug("", "found cached dynamic state UUID for resulting document.");
                }

                @Override
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

                outputResponseDocument(pipelineContext, externalContext, indentedLogger, stage2CacheableState.getAnnotatedSAXStore(),
                        containingDocument[0], contentHandler, encodedClientState);
            } else {
                // Output in test mode
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

    // What can be cached by the first stage: URI dependencies
    private static class Stage1CacheableState extends URIProcessorOutputImpl.URIReferences {}

    // What can be cached by the second stage: SAXStore and static state
    private static class Stage2CacheableState extends URIProcessorOutputImpl.URIReferences {

        private final SAXStore annotatedSAXStore;
        private final XFormsStaticState xformsStaticState;

        public Stage2CacheableState(SAXStore annotatedSAXStore, XFormsStaticState xformsStaticState) {
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

    private void gatherInputDependencies(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger, Stage1CacheableState stage1CacheableState) {

        // Set caching dependencies if the input was actually read
        // WIP: check all models/instances: for (Iterator i = containingDocument.getAllModels().iterator(); i.hasNext();) {
        for (final XFormsModel currentModel: containingDocument.getModels()) {
            // Add schema dependencies
            final String[] schemaURIs = currentModel.getSchemaURIs();
            // TODO: We should also use dependencies computed in XFormsModelSchemaValidator.SchemaInfo
            if (schemaURIs != null) {
                for (final String currentSchemaURI: schemaURIs) {
                    if (indentedLogger.isDebugEnabled())
                        indentedLogger.logDebug("", "adding document cache dependency for schema", "schema URI", currentSchemaURI);

                    stage1CacheableState.addReference(null, currentSchemaURI, null, null,
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

                            stage1CacheableState.addReference(null, instanceSourceURI, currentInstance.getUsername(), currentInstance.getPassword(),
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

            // TODO: Add @src attributes from controls? Not used often.
        }

        // Set caching dependencies for XBL inclusions
        {
            final XFormsAnnotatorContentHandler.Metadata metadata = containingDocument.getStaticState().getMetadata();
            final List<String> includes = metadata.getBindingsIncludes();
            if (includes != null) {
                for (final String include: includes) {
                    stage1CacheableState.addReference(null, "oxf:" + include, null, null, null);
                }
            }
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
            controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, encodedClientState, externalContext, null));
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
