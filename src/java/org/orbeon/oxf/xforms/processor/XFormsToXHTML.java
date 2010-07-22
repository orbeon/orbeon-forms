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

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.*;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.impl.DependenciesProcessorInput;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.XFormsAnnotatorContentHandler;
import org.orbeon.oxf.xforms.analysis.XFormsExtractorContentHandler;
import org.orbeon.oxf.xforms.analysis.model.Instance;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.SAXException;

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
            public void readImpl(final PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                doIt(pipelineContext, xmlReceiver, this, outputName);
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
            return new DependenciesProcessorInput(XFormsToXHTML.this, inputName, originalInput) {
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

    private static final boolean DO_TEST_STATE = false;
    private static Stage2CacheableState TEST_STATE;

    private void doIt(final PipelineContext pipelineContext, XMLReceiver xmlReceiver, final URIProcessorOutputImpl processorOutput, String outputName) {

        final ExternalContext externalContext = XFormsUtils.getExternalContext(pipelineContext);
        final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(XFormsToXHTML.logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final boolean[] cachedInput = new boolean[] { false } ;

        final Stage2CacheableState stage2CacheableState;
        if (TEST_STATE == null) {

            // Read and try to cache the complete XForms+XHTML document with annotations
            stage2CacheableState = (Stage2CacheableState) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT),
                new CacheableInputReader() {
                    public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                        // Create URIResolver
                        final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput,
                                pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);

                        // Compute annotated XForms document + static state document
                        final Stage1CacheableState stage1CacheableState = new Stage1CacheableState();
                        final Stage2CacheableState stage2CacheableState;
                        {
                            final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
                            // TODO: Use TinyTree instead of dom4j Document
                            final LocationDocumentResult documentResult = new LocationDocumentResult();
                            identity.setResult(documentResult);

                            final XFormsStaticState.Metadata metadata = new XFormsStaticState.Metadata();
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
                        // Create containing document and initialize XForms engine
                        containingDocument[0] = new XFormsContainingDocument(pipelineContext, stage2CacheableState.getXFormsStaticState(), uriResolver);

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
                }, false);

            TEST_STATE = DO_TEST_STATE ? stage2CacheableState : null;

        } else {
            stage2CacheableState = TEST_STATE;
        }

        try {
            // Create containing document if not done yet
            if (containingDocument[0] == null) {
                assert !cachedInput[0];
                // In this case, we found the static state and more in the cache, but we must now create a new XFormsContainingDocument from this information
                indentedLogger.logDebug("", "annotated document and static state obtained from cache; creating XForms document.");

                // Create URIResolver and XFormsContainingDocument
                final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);
                containingDocument[0] = new XFormsContainingDocument(pipelineContext, stage2CacheableState.getXFormsStaticState(), uriResolver);
            } else {
                assert cachedInput[0];
                indentedLogger.logDebug("", "annotated document and static state not obtained from cache.");
            }

            // Output resulting document
            if (outputName.equals("document")) {
                // Normal case where we output XHTML
                outputResponseDocument(pipelineContext, externalContext, indentedLogger, stage2CacheableState.getAnnotatedSAXStore(),
                        containingDocument[0], xmlReceiver);
            } else {
                // Output in test mode
                testOutputResponseState(pipelineContext, containingDocument[0], indentedLogger, xmlReceiver);
            }

            // Notify state manager
            XFormsStateManager.instance().afterInitialResponse(pipelineContext, containingDocument[0]);

        } catch (Throwable e) {
            indentedLogger.logDebug("", "throwable caught during initialization.");
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

        public XFormsStaticState getXFormsStaticState() {
            return xformsStaticState;
        }
    }

    private void gatherInputDependencies(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger, Stage1CacheableState stage1CacheableState) {

        final String forwardSubmissionHeaders = XFormsProperties.getForwardSubmissionHeaders(containingDocument);

        // Add static instance source dependencies for top-level models
        // TODO: check all models/instances
        final XFormsStaticState staticState = containingDocument.getStaticState();
        for (final Model model: staticState.getModelsForScope(staticState.getXBLBindings().getTopLevelScope())) {
            for (final Instance instance: model.instances.values()) {
                if (instance.dependencyURL != null) {
                    if (!instance.isCacheHint) {
                        stage1CacheableState.addReference(null, instance.dependencyURL, instance.xxformsUsername,
                                instance.xxformsPassword, instance.xxformsPassword, forwardSubmissionHeaders);

                        if (indentedLogger.isDebugEnabled())
                                indentedLogger.logDebug("", "adding document cache dependency for non-cacheable instance", "instance URI", instance.dependencyURL);

                    } else {
                        // Don't add the dependency as we don't want the instance URI to be hit
                        // For all practical purposes, globally shared instances must remain constant!
                        if (indentedLogger.isDebugEnabled())
                            indentedLogger.logDebug("", "not adding document cache dependency for cacheable instance", "instance URI", instance.dependencyURL);
                    }
                }
            }
        }

        // Set caching dependencies if the input was actually read
        // TODO: check all models/instances
        // Q: should use static dependency information instead? what about schema imports and instance replacements?
        for (final XFormsModel currentModel: containingDocument.getModels()) {
            // Add schema dependencies
            final String[] schemaURIs = currentModel.getSchemaURIs();
            // TODO: We should also use dependencies computed in XFormsModelSchemaValidator.SchemaInfo
            if (schemaURIs != null) {
                for (final String currentSchemaURI: schemaURIs) {
                    if (indentedLogger.isDebugEnabled())
                        indentedLogger.logDebug("", "adding document cache dependency for schema", "schema URI", currentSchemaURI);

                    stage1CacheableState.addReference(null, currentSchemaURI, null, null, null, forwardSubmissionHeaders);// TODO: support username / password on schema refs
                }
            }
        }
        // TODO: Add @src attributes from controls? Not used often.

        // Set caching dependencies for XBL inclusions
        {
            final XFormsStaticState.Metadata metadata = containingDocument.getStaticState().getMetadata();
            final List<String> includes = metadata.getBindingsIncludes();
            if (includes != null) {
                for (final String include: includes) {
                    stage1CacheableState.addReference(null, "oxf:" + include, null, null, null, null);
                }
            }
        }
    }

    public static void outputResponseDocument(final PipelineContext pipelineContext, final ExternalContext externalContext,
                                             final IndentedLogger indentedLogger,
                                             final SAXStore annotatedDocument, final XFormsContainingDocument containingDocument,
                                             final XMLReceiver xmlReceiver) throws SAXException, IOException {

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
            XMLUtils.streamNullDocument(xmlReceiver);
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
            controller.setOutput(new DeferredXMLReceiverImpl(xmlReceiver));
            // Set handler context
            controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, externalContext, null));
            // Process the entire input
            annotatedDocument.replay(new ExceptionWrapperXMLReceiver(controller, "converting XHTML+XForms document to XHTML"));
        }
    }

    private void testOutputResponseState(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument,
                                         final IndentedLogger indentedLogger, final XMLReceiver xmlReceiver) throws SAXException {
        // Output XML response

        XFormsServer.outputAjaxResponse(containingDocument, indentedLogger, null, pipelineContext, null, xmlReceiver, false, true);
    }
}
