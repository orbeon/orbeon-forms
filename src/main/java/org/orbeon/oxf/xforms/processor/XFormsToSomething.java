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

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.xforms.analysis.XFormsAnnotator;
import org.orbeon.oxf.xforms.analysis.XFormsExtractor;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.DependenciesProcessorInput;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.Metadata;
import org.orbeon.oxf.xforms.analysis.model.Instance;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.state.AnnotatedTemplate;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.SAXException;
import scala.Option;

import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.util.Set;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
abstract public class XFormsToSomething extends ProcessorImpl {

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String OUTPUT_DOCUMENT = "document";

    public XFormsToSomething() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
        addInputInfo(new ProcessorInputOutputInfo("namespace")); // This input ensures that we depend on a portlet namespace
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DOCUMENT));
    }

    /**
     * Case where an XML response must be generated.
     */
    @Override
    public ProcessorOutput createOutput(final String outputName) {
        final ProcessorOutput output = new URIProcessorOutputImpl(XFormsToSomething.this, outputName, INPUT_ANNOTATED_DOCUMENT) {
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
                // - more work is needed to make this work properly
                // - not many use cases benefit
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
            // - stage1 is the processor automatically inserted below for the purpose of handling dependencies
            // - stage2 is the actual oxf:xforms-to-xhtml which actually does XForms processing
            final ProcessorInput originalInput = super.createInput(inputName);
            return new DependenciesProcessorInput(XFormsToSomething.this, inputName, originalInput) {
                @Override
                protected URIProcessorOutputImpl.URIReferences getURIReferences(PipelineContext pipelineContext) {
                    // Return dependencies object, set by stage2 before reading its input
                    return ((Stage2TransientState) XFormsToSomething.this.getState(pipelineContext)).stage1CacheableState;
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

    private void doIt(final PipelineContext pipelineContext, final XMLReceiver xmlReceiver, final URIProcessorOutputImpl processorOutput, String outputName) {

        final ExternalContext externalContext = NetUtils.getExternalContext();

        final IndentedLogger htmlLogger    = Loggers.getIndentedLogger("html");
        final IndentedLogger cachingLogger = Loggers.getIndentedLogger("cache");

        final XFormsStaticStateCache.CacheTracer cacheTracer;
        final boolean initializeXFormsDocument;
        {
            final XFormsStaticStateCache.CacheTracer customTracer =
                (XFormsStaticStateCache.CacheTracer) pipelineContext.getAttribute("orbeon.cache.test.tracer");

            if (customTracer != null)
                cacheTracer = customTracer;
            else
                cacheTracer = new LoggingCacheTracer(cachingLogger);

            final Boolean initializeXFormsDocumentOrNull =
                (Boolean) pipelineContext.getAttribute("orbeon.cache.test.initialize-xforms-document");

            initializeXFormsDocument = initializeXFormsDocumentOrNull != null ? initializeXFormsDocumentOrNull : true;
        }

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final boolean[] cachedStatus = new boolean[] { false } ;

        // Read and try to cache the complete XForms+XHTML document with annotations
        final Stage2CacheableState stage2CacheableState =
            readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT),
                new CacheableInputReader<Stage2CacheableState>() {
                    public Stage2CacheableState read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                        // Compute annotated XForms document + static state document
                        final Stage1CacheableState stage1CacheableState = new Stage1CacheableState();
                        final Stage2CacheableState stage2CacheableState;
                        final XFormsStaticState[] staticState = new XFormsStaticState[1];
                        {
                            // Store dependencies container in state before reading
                            ((Stage2TransientState) XFormsToSomething.this.getState(pipelineContext)).stage1CacheableState = stage1CacheableState;

                            // Read static state from input
                            stage2CacheableState = readStaticState(pipelineContext, cachingLogger, cacheTracer, staticState);
                        }

                        // Create containing document and initialize XForms engine
                        // NOTE: Create document here so we can do appropriate analysis of caching dependencies
                        final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToSomething.this, processorOutput,
                                pipelineContext, INPUT_ANNOTATED_DOCUMENT, XMLParsing.ParserConfiguration.PLAIN);

                        containingDocument[0] =
                            new XFormsContainingDocument(
                                staticState[0],
                                uriResolver,
                                PipelineResponse.getResponse(xmlReceiver, externalContext),
                                initializeXFormsDocument
                            );

                        // Gather set caching dependencies
                        gatherInputDependencies(containingDocument[0], cachingLogger, stage1CacheableState);

                        return stage2CacheableState;
                    }

                    @Override
                    public void foundInCache() {
                        cachedStatus[0] = true;
                    }
                });

        try {
            // Create containing document if not done yet
            if (containingDocument[0] == null) {
                assert cachedStatus[0];
                // In this case, we found the static state digest and more in the cache, but we must now create a new XFormsContainingDocument from this information
                cacheTracer.digestAndTemplateStatus(scala.Option.apply(stage2CacheableState.staticStateDigest));

                final XFormsStaticState staticState;
                {
                    final XFormsStaticState cachedState = XFormsStaticStateCache.getDocumentJava(stage2CacheableState.staticStateDigest);
                    if (cachedState != null && cachedState.topLevelPart().metadata().bindingsIncludesAreUpToDate()) {
                        // Found static state in cache
                        cacheTracer.staticStateStatus(true, cachedState.digest());

                        staticState = cachedState;
                    } else {
                        // Not found static state in cache OR it is out of date, create static state from input
                        // NOTE: In out of date case, could clone static state and reprocess instead?
                        if (cachedState != null)
                            cachingLogger.logDebug("",
                                "out-of-date static state by digest in cache due to: "
                                    + cachedState.topLevelPart().metadata().debugOutOfDateBindingsIncludesJava());

                        final StaticStateBits staticStateBits = new StaticStateBits(pipelineContext, cachingLogger, stage2CacheableState.staticStateDigest);
                        staticState = XFormsStaticStateImpl.createFromStaticStateBits(staticStateBits.staticStateDocument, stage2CacheableState.staticStateDigest,
                                staticStateBits.metadata, staticStateBits.template);

                        cacheTracer.staticStateStatus(false, staticState.digest());

                        // Store in cache
                        XFormsStaticStateCache.storeDocument(staticState);
                    }
                }

                final XFormsURIResolver uriResolver =
                    new XFormsURIResolver(XFormsToSomething.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, XMLParsing.ParserConfiguration.PLAIN);

                containingDocument[0] =
                    new XFormsContainingDocument(
                        staticState,
                        uriResolver,
                        PipelineResponse.getResponse(xmlReceiver, externalContext),
                        initializeXFormsDocument
                    );
            } else {
                assert !cachedStatus[0];
                cacheTracer.digestAndTemplateStatus(Option.<String>apply(null));
            }

            // Output resulting document
            if (initializeXFormsDocument)
                produceOutput(pipelineContext, outputName, externalContext, htmlLogger, stage2CacheableState, containingDocument[0], xmlReceiver);

            // Notify state manager
            XFormsStateManager.instance().afterInitialResponse(containingDocument[0], stage2CacheableState.template);

        } catch (Throwable e) {
            htmlLogger.logDebug("", "throwable caught during initialization.");
            throw new OXFException(e);
        }
    }

    abstract protected void produceOutput(
            PipelineContext pipelineContext,
            String outputName,
            ExternalContext externalContext,
            IndentedLogger indentedLogger,
            Stage2CacheableState stage2CacheableState,
            XFormsContainingDocument containingDocument,
            XMLReceiver xmlReceiver) throws IOException, SAXException;

    private Stage2CacheableState readStaticState(
            PipelineContext pipelineContext,
            IndentedLogger logger,
            XFormsStaticStateCache.CacheTracer cacheTracer,
            XFormsStaticState[] staticState) {

        final StaticStateBits staticStateBits = new StaticStateBits(pipelineContext, logger, null);

        {
            final XFormsStaticState cachedState = XFormsStaticStateCache.getDocumentJava(staticStateBits.staticStateDigest);
            if (cachedState != null && cachedState.topLevelPart().metadata().bindingsIncludesAreUpToDate()) {
                // Found static state in cache
                cacheTracer.staticStateStatus(true, cachedState.digest());

                staticState[0] = cachedState;
            } else {
                // Not found static state in cache OR it is out of date, create and initialize static state object
                // NOTE: In out of date case, could clone static state and reprocess instead?
                if (cachedState != null)
                    logger.logDebug("",
                        "out-of-date static state by digest in cache due to: "
                            + cachedState.topLevelPart().metadata().debugOutOfDateBindingsIncludesJava());

                staticState[0] = XFormsStaticStateImpl.createFromStaticStateBits(staticStateBits.staticStateDocument, staticStateBits.staticStateDigest,
                        staticStateBits.metadata, staticStateBits.template);

                cacheTracer.staticStateStatus(false, staticState[0].digest());

                // Store in cache
                XFormsStaticStateCache.storeDocument(staticState[0]);
            }
        }

        // Update input dependencies object
        return new Stage2CacheableState(staticStateBits.staticStateDigest, staticStateBits.template);
    }

    private class StaticStateBits {

        private final boolean isLogStaticStateInput = XFormsProperties.getDebugLogging().contains("html-static-state");

        public final Metadata metadata = new Metadata();
        public final Document staticStateDocument;
        public final AnnotatedTemplate template;
        public final String staticStateDigest;

        public StaticStateBits(PipelineContext pipelineContext, IndentedLogger logger, String existingStaticStateDigest) {

            final boolean computeDigest = isLogStaticStateInput || existingStaticStateDigest == null;

            logger.startHandleOperation("", "reading input", "existing digest", existingStaticStateDigest);

            final TransformerXMLReceiver documentReceiver = TransformerUtils.getIdentityTransformerHandler();
            final LocationDocumentResult documentResult = new LocationDocumentResult();
            documentReceiver.setResult(documentResult);

            final DigestContentHandler digestReceiver = computeDigest ? new DigestContentHandler() : null;
            final XMLReceiver extractorOutput;
            if (isLogStaticStateInput) {
                extractorOutput = computeDigest ? new TeeXMLReceiver(documentReceiver, digestReceiver, getDebugReceiver(logger)) : new TeeXMLReceiver(documentReceiver, getDebugReceiver(logger));
            } else {
                extractorOutput = computeDigest ? new TeeXMLReceiver(documentReceiver, digestReceiver) : documentReceiver;
            }

            // Read the input through the annotator and gather namespace mappings
            //
            // Output of annotator is:
            //
            // - annotated page template (TODO: this should not include model elements)
            // - extractor
            //
            // Output of extractor is:
            //
            // - static state document
            // - optionally: digest
            // - optionally: debug output
            //
            this.template = AnnotatedTemplate.applyJava(new SAXStore());
            readInputAsSAX(pipelineContext, INPUT_ANNOTATED_DOCUMENT,
                new WhitespaceXMLReceiver(
                    new XFormsAnnotator(
                        this.template.saxStore(),
                        new XFormsExtractor(
                            new WhitespaceXMLReceiver(
                                extractorOutput,
                                Whitespace.defaultBasePolicy(),
                                Whitespace.basePolicyMatcher()
                            ),
                            metadata,
                            template,
                            ".",
                            XFormsConstants.XXBLScope.inner,
                            true,
                            false,
                            false
                        ),
                        metadata,
                        true
                    ),
                    Whitespace.defaultHTMLPolicy(),
                    Whitespace.htmlPolicyMatcher()
                ));

            this.staticStateDocument = documentResult.getDocument();
            this.staticStateDigest = computeDigest ? NumberUtils.toHexString(digestReceiver.getResult()) : null;

            assert !isLogStaticStateInput || existingStaticStateDigest == null || this.staticStateDigest.equals(existingStaticStateDigest);

            logger.endHandleOperation("computed digest", this.staticStateDigest);
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
    public static class Stage2CacheableState extends URIProcessorOutputImpl.URIReferences {

        public final String staticStateDigest;
        public final AnnotatedTemplate template;

        public Stage2CacheableState(String staticStateDigest, AnnotatedTemplate template) {
            this.staticStateDigest = staticStateDigest;
            this.template = template;
        }
    }

    private void gatherInputDependencies(XFormsContainingDocument containingDocument, IndentedLogger logger, Stage1CacheableState stage1CacheableState) {

        // Add static instance source dependencies for top-level models
        // TODO: check all models/instances
        final PartAnalysis topLevelPart = containingDocument.getStaticState().topLevelPart();
        for (final Model model : topLevelPart.jGetModelsForScope(topLevelPart.startScope())) {
            for (final Instance instance: model.instancesMap().values()) {
                if (instance.dependencyURL().isDefined()) {

                    final String resolvedDependencyURL = XFormsUtils.resolveServiceURL(containingDocument, instance.element(), instance.dependencyURL().get(),
                        ExternalContext.Response.REWRITE_MODE_ABSOLUTE);

                    if (!instance.cache()) {
                        stage1CacheableState.addReference(null, resolvedDependencyURL, instance.credentialsOrNull());

                        if (logger.isDebugEnabled())
                                logger.logDebug("", "adding document cache dependency for non-cacheable instance", "instance URI", resolvedDependencyURL);

                    } else {
                        // Don't add the dependency as we don't want the instance URI to be hit
                        // For all practical purposes, globally shared instances must remain constant!
                        if (logger.isDebugEnabled())
                            logger.logDebug("", "not adding document cache dependency for cacheable instance", "instance URI", resolvedDependencyURL);
                    }
                }
            }
        }

        // Set caching dependencies if the input was actually read
        // TODO: check all models/instances
        // Q: should use static dependency information instead? what about schema imports and instance replacements?
        for (final XFormsModel currentModel: containingDocument.getModelsJava()) {
            // Add schema dependencies
            final String[] schemaURIs = currentModel.getSchemaURIs();
            // TODO: We should also use dependencies computed in XFormsModelSchemaValidator.SchemaInfo
            if (schemaURIs != null) {
                for (final String currentSchemaURI: schemaURIs) {
                    if (logger.isDebugEnabled())
                        logger.logDebug("", "adding document cache dependency for schema", "schema URI", currentSchemaURI);

                    stage1CacheableState.addReference(null, currentSchemaURI, null);// TODO: support credentials on schema refs
                }
            }
        }
        // TODO: Add @src attributes from controls? Not used often.

        // Set caching dependencies for XBL inclusions
        {
            final Metadata metadata = containingDocument.getStaticState().topLevelPart().metadata();
            final Set<String> includes = metadata.getBindingIncludesJava();
            for (final String include: includes) {
                stage1CacheableState.addReference(null, "oxf:" + include, null);
            }
        }
    }
}
