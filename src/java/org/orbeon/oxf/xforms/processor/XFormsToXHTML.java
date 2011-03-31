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
import org.orbeon.oxf.externalcontext.ResponseAdapter;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.DependenciesProcessorInput;
import org.orbeon.oxf.processor.serializer.CachedSerializer;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.XFormsAnnotatorContentHandler;
import org.orbeon.oxf.xforms.analysis.XFormsExtractorContentHandler;
import org.orbeon.oxf.xforms.analysis.model.Instance;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

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

    private void doIt(final PipelineContext pipelineContext, final XMLReceiver xmlReceiver, final URIProcessorOutputImpl processorOutput, String outputName) {

        final ExternalContext externalContext = NetUtils.getExternalContext();
        final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(XFormsToXHTML.logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final boolean[] cachedStatus = new boolean[] { false } ;

        final Stage2CacheableState stage2CacheableState;
        if (TEST_STATE == null) {

            // Read and try to cache the complete XForms+XHTML document with annotations
            stage2CacheableState = (Stage2CacheableState) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT),
                new CacheableInputReader() {
                    public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                        // Compute annotated XForms document + static state document
                        final Stage1CacheableState stage1CacheableState = new Stage1CacheableState();
                        final Stage2CacheableState stage2CacheableState;
                        final XFormsStaticState[] staticState = new XFormsStaticState[1];
                        {
                            // Store dependencies container in state before reading
                            ((Stage2TransientState) XFormsToXHTML.this.getState(pipelineContext)).stage1CacheableState = stage1CacheableState;

                            // Read static state from input
                            stage2CacheableState = readStaticState(pipelineContext, indentedLogger, staticState);
                        }

                        // Create containing document and initialize XForms engine
                        // NOTE: Create document here so we can do appropriate analysis of caching dependencies
                        final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput,
                                pipelineContext, INPUT_ANNOTATED_DOCUMENT, XMLUtils.ParserConfiguration.PLAIN);
                        containingDocument[0] = new XFormsContainingDocument(staticState[0], stage2CacheableState.getAnnotatedTemplate(), uriResolver, getResponse(xmlReceiver, externalContext));

                        // Gather set caching dependencies
                        gatherInputDependencies(pipelineContext, containingDocument[0], indentedLogger, stage1CacheableState);

                        return stage2CacheableState;
                    }

                    @Override
                    public void foundInCache() {
                        cachedStatus[0] = true;
                    }
                }, false);

            TEST_STATE = DO_TEST_STATE ? stage2CacheableState : null;

        } else {
            stage2CacheableState = TEST_STATE;
        }

        try {
            // Create containing document if not done yet
            if (containingDocument[0] == null) {
                assert cachedStatus[0];
                // In this case, we found the static state digest and more in the cache, but we must now create a new XFormsContainingDocument from this information
                indentedLogger.logDebug("", "annotated document and static state digest obtained from cache", "digest", stage2CacheableState.getStaticStateDigest());

                final XFormsStaticState staticState;
                {
                    final XFormsStaticState cachedState = XFormsStaticStateCache.instance().getDocument(stage2CacheableState.getStaticStateDigest());
                    if (cachedState != null && cachedState.getMetadata().checkBindingsIncludes()) {
                        // Found static state in cache
                        indentedLogger.logDebug("", "found up-to-date static state by digest in cache");

                        staticState = cachedState;
                    } else {
                        // Not found static state in cache OR it is out of date, create static state from input
                        // NOTE: In out of date case, could clone static state and reprocess instead?
                        if (cachedState != null)
                            indentedLogger.logDebug("", "found out-of-date static state by digest in cache");
                        else
                            indentedLogger.logDebug("", "did not find static state by digest in cache");

                        final StaticStateBits staticStateBits = new StaticStateBits(pipelineContext, indentedLogger,  stage2CacheableState.getStaticStateDigest());
                        staticState = new XFormsStaticState(staticStateBits.staticStateDocument, stage2CacheableState.getStaticStateDigest(), staticStateBits.metadata);

                        // Store in cache
                        XFormsStaticStateCache.instance().storeDocument(staticState);
                    }
                }

                final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, XMLUtils.ParserConfiguration.PLAIN);
                containingDocument[0] = new XFormsContainingDocument(staticState, stage2CacheableState.getAnnotatedTemplate(), uriResolver, getResponse(xmlReceiver, externalContext));
            } else {
                assert !cachedStatus[0];
                indentedLogger.logDebug("", "annotated document and static state digest not obtained from cache.");
            }

            // Output resulting document
            if (outputName.equals("document")) {
                // Normal case where we output XHTML
                outputResponseDocument(pipelineContext, externalContext, indentedLogger, stage2CacheableState.getAnnotatedTemplate(),
                        containingDocument[0], xmlReceiver);
            } else {
                // Output in test mode
                testOutputResponseState(pipelineContext, containingDocument[0], indentedLogger, xmlReceiver);
            }

            // Notify state manager
            XFormsStateManager.instance().afterInitialResponse(containingDocument[0]);

        } catch (Throwable e) {
            indentedLogger.logDebug("", "throwable caught during initialization.");
            throw new OXFException(e);
        }
    }

    private Stage2CacheableState readStaticState(PipelineContext pipelineContext, IndentedLogger indentedLogger, XFormsStaticState[] staticState) {

        final StaticStateBits staticStateBits = new StaticStateBits(pipelineContext, indentedLogger, null);

        {
            final XFormsStaticState cachedState = XFormsStaticStateCache.instance().getDocument(staticStateBits.staticStateDigest);
            if (cachedState != null && cachedState.getMetadata().checkBindingsIncludes()) {
                // Found static state in cache
                indentedLogger.logDebug("", "found up-to-date static state by digest in cache");

                staticState[0] = cachedState;
            } else {
                // Not found static state in cache OR it is out of date, create and initialize static state object
                // NOTE: In out of date case, could clone static state and reprocess instead?
                if (cachedState != null)
                    indentedLogger.logDebug("", "found out-of-date static state by digest in cache");
                else
                    indentedLogger.logDebug("", "did not find static state by digest in cache");
                
                staticState[0] = new XFormsStaticState(staticStateBits.staticStateDocument, staticStateBits.staticStateDigest, staticStateBits.metadata);

                // Store in cache
                XFormsStaticStateCache.instance().storeDocument(staticState[0]);
            }
        }

        // Update input dependencies object
        return new Stage2CacheableState(staticStateBits.annotatedTemplate, staticStateBits.staticStateDigest);
    }

    private class StaticStateBits {

        private final boolean isLogStaticStateInput = XFormsProperties.getDebugLogging().contains("html-static-state");

        public final XFormsStaticState.Metadata metadata = new XFormsStaticState.Metadata();
        public final SAXStore annotatedTemplate = new SAXStore();

        public final Document staticStateDocument;
        public final String staticStateDigest;

        public StaticStateBits(PipelineContext pipelineContext, IndentedLogger indentedLogger, String existingStaticStateDigest) {

            final boolean computeDigest = isLogStaticStateInput || existingStaticStateDigest == null;

            indentedLogger.startHandleOperation("", "reading input", "existing digest", existingStaticStateDigest);

            final TransformerXMLReceiver documentReceiver = TransformerUtils.getIdentityTransformerHandler();
            final LocationDocumentResult documentResult = new LocationDocumentResult();
            documentReceiver.setResult(documentResult);

            final XMLUtils.DigestContentHandler digestReceiver = computeDigest ? new XMLUtils.DigestContentHandler("MD5") : null;
            final XMLReceiver extractorOutput;
            if (isLogStaticStateInput) {
                extractorOutput = computeDigest ? new TeeXMLReceiver(documentReceiver, digestReceiver, getDebugReceiver(indentedLogger)) : new TeeXMLReceiver(documentReceiver, getDebugReceiver(indentedLogger));
            } else {
                extractorOutput = computeDigest ? new TeeXMLReceiver(documentReceiver, digestReceiver) : documentReceiver;
            }

            // Read the input through the annotator and gather namespace mappings
            //
            // Output of annotator is:
            //
            // o annotated page template (TODO: this should not include model elements)
            // o extractor
            //
            // Output of extractor is:
            //
            // o static state document
            // o optionally: digest
            // o optionally: debug output
            //
            readInputAsSAX(pipelineContext, INPUT_ANNOTATED_DOCUMENT,
                    new XFormsAnnotatorContentHandler(annotatedTemplate, new XFormsExtractorContentHandler(extractorOutput, metadata), metadata));

            this.staticStateDocument = documentResult.getDocument();
            this.staticStateDigest = computeDigest ? NumberUtils.toHexString(digestReceiver.getResult()) : null;

            assert !isLogStaticStateInput || existingStaticStateDigest == null || this.staticStateDigest.equals(existingStaticStateDigest);

            indentedLogger.endHandleOperation("computed digest", this.staticStateDigest);
        }

        private XMLReceiver getDebugReceiver(final IndentedLogger indentedLogger) {
            final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
            final StringBuilderWriter writer = new StringBuilderWriter();
            identity.setResult(new StreamResult(writer));

            return new ForwardingXMLReceiver(identity) {
                @Override
                public void endDocument() throws SAXException {
                    super.endDocument();
                    // Log out at end of document
                    indentedLogger.logDebug("", "static state input", "input", writer.toString());
                }
            };
        }
    }

    // What can be cached by the first stage: URI dependencies
    private static class Stage1CacheableState extends URIProcessorOutputImpl.URIReferences {}

    // What can be cached by the second stage: SAXStore and static state
    private static class Stage2CacheableState extends URIProcessorOutputImpl.URIReferences {

        private final SAXStore annotatedTemplate;
        private final String staticStateDigest;

        public Stage2CacheableState(SAXStore annotatedTemplate, String staticStateDigest) {
            this.annotatedTemplate = annotatedTemplate;
            this.staticStateDigest = staticStateDigest;
        }

        public SAXStore getAnnotatedTemplate() {
            return annotatedTemplate;
        }

        public String getStaticStateDigest() {
            return staticStateDigest;
        }
    }

    private void gatherInputDependencies(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, IndentedLogger indentedLogger, Stage1CacheableState stage1CacheableState) {

        final String forwardSubmissionHeaders = XFormsProperties.getForwardSubmissionHeaders(containingDocument);

        // Add static instance source dependencies for top-level models
        // TODO: check all models/instances
        final XFormsStaticState staticState = containingDocument.getStaticState();
        for (final Model model: staticState.getModelsForScope(staticState.getXBLBindings().getTopLevelScope())) {
            for (final Instance instance: model.instancesMap().values()) {
                if (instance.dependencyURL() != null) {

                    final String resolvedDependencyURL = XFormsUtils.resolveServiceURL(containingDocument, instance.element(), instance.dependencyURL(),
                        ExternalContext.Response.REWRITE_MODE_ABSOLUTE);

                    if (!instance.isCacheHint()) {
                        stage1CacheableState.addReference(null, resolvedDependencyURL, instance.xxformsUsername(),
                                instance.xxformsPassword(), instance.xxformsPassword(), forwardSubmissionHeaders);

                        if (indentedLogger.isDebugEnabled())
                                indentedLogger.logDebug("", "adding document cache dependency for non-cacheable instance", "instance URI", resolvedDependencyURL);

                    } else {
                        // Don't add the dependency as we don't want the instance URI to be hit
                        // For all practical purposes, globally shared instances must remain constant!
                        if (indentedLogger.isDebugEnabled())
                            indentedLogger.logDebug("", "not adding document cache dependency for cacheable instance", "instance URI", resolvedDependencyURL);
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
            final Set<String> includes = metadata.getBindingsIncludes();
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
            externalContext.getResponse().sendRedirect(redirectResource, null, false, false);

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

        containingDocument.afterInitialResponse();
    }

    private void testOutputResponseState(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument,
                                         final IndentedLogger indentedLogger, final XMLReceiver xmlReceiver) throws SAXException {
        // Output XML response

        XFormsServer.outputAjaxResponse(containingDocument, indentedLogger, null, pipelineContext, null, xmlReceiver, false, true);
    }

    public static ExternalContext.Response getResponse(ContentHandler contentHandler, final ExternalContext externalContext) {
        ExternalContext.Response response;
        if (contentHandler != null) {
            // If a response is written, it will be through a conversion to XML first
            final ContentHandlerOutputStream contentHandlerOutputStream = new ContentHandlerOutputStream(contentHandler);
            response = new ResponseAdapter() {

                private String charset;
                private PrintWriter printWriter;
                private ExternalContext.Response originalResponse = externalContext.getResponse();

                @Override
                public OutputStream getOutputStream() throws IOException {
                    return contentHandlerOutputStream;
                }

                @Override
                public PrintWriter getWriter() throws IOException {
                    // Return this just because Tomcat 5.5, when doing a servlet forward, may ask for one, just to close it!
                    if (printWriter == null) {
                        printWriter = new PrintWriter(new OutputStreamWriter(contentHandlerOutputStream, charset != null ? charset : CachedSerializer.DEFAULT_ENCODING));
                    }
                    return printWriter;
                }

                @Override
                public void setContentType(String contentType) {
                    try {
                        // Assume that content type is always set, otherwise this won't work
                        charset = NetUtils.getContentTypeCharset(contentType);
                        contentHandlerOutputStream.startDocument(contentType);
                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }
                }

                @Override
                public Object getNativeResponse() {
                    return externalContext.getNativeResponse();
                }

                @Override
                public void setHeader(String name, String value) {
                    // TODO: It is not sound that we output headers here as they should be passed to the
                    // binary document in the pipeline instead.
                    externalContext.getResponse().setHeader(name, value);
                }

                @Override
                public void setTitle(String title) {
                    originalResponse.setTitle(title);
                }

                @Override
                public String getNamespacePrefix() {
                    return originalResponse.getNamespacePrefix();
                }

                @Override
                public String rewriteResourceURL(String urlString, int rewriteMode) {
                    return originalResponse.rewriteResourceURL(urlString, rewriteMode);
                }

                @Override
                public String rewriteResourceURL(String urlString, boolean absolute) {
                    return originalResponse.rewriteResourceURL(urlString, absolute);
                }

                @Override
                public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
                    return originalResponse.rewriteRenderURL(urlString, portletMode, windowState);
                }

                @Override
                public String rewriteActionURL(String urlString, String portletMode, String windowState) {
                    return originalResponse.rewriteActionURL(urlString, portletMode, windowState);
                }

                @Override
                public String rewriteRenderURL(String urlString) {
                    return originalResponse.rewriteRenderURL(urlString);
                }

                @Override
                public String rewriteActionURL(String urlString) {
                    return originalResponse.rewriteActionURL(urlString);
                }
            };
        } else {
            // We get the actual output response
            response = externalContext.getResponse();
        }
        return response;
    }
}
