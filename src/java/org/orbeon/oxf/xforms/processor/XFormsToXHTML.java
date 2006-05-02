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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xml.*;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Iterator;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
public class XFormsToXHTML extends ProcessorImpl {

    static public Logger logger = XFormsServer.logger;

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
//    private static final String INPUT_STATIC_STATE = "static-state";
    private static final String OUTPUT_DOCUMENT = "document";

    public XFormsToXHTML() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
//        addInputInfo(new ProcessorInputOutputInfo(INPUT_STATIC_STATE));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DOCUMENT));
    }

    /**
     * Case where an XML response must be generated.
     */
    public ProcessorOutput createOutput(final String outputName) {
        ProcessorOutput output = new ProcessorImpl.URIProcessorOutputImpl(getClass(), outputName, INPUT_ANNOTATED_DOCUMENT) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler);
            }
        };
        addOutput(outputName, output);
        return output;
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // What can be cached: URI dependencies + the annotated XForms document
        class Result extends URIReferences {
            private SAXStore annotatedSAXStore;

            public Result(SAXStore annotatedSAXStore) {
                this.annotatedSAXStore = annotatedSAXStore;
            }

            public SAXStore getAnnotatedSAXStore() {
                return annotatedSAXStore;
            }
        }

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final XFormsServer.XFormsState[] xformsState = new XFormsServer.XFormsState[1];

        // Read and try to cache the complete XForms+XHTML document with annotations
        final Result result = (Result) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT), new CacheableInputReader() {
            public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                // Compute two SAXStores in one pass: annotated XForms document + static state document
                final SAXStore staticStateSAXStore = new SAXStore();
                final SAXStore annotatedSAXStore = new SAXStore(new XFormsExtractor.XFormsExtractorContentHandler(pipelineContext, staticStateSAXStore));
                readInputAsSAX(pipelineContext, processorInput, annotatedSAXStore);

                final Result result = new Result(annotatedSAXStore);

                // Create document here so we can do appropriate analysis of caching dependencies
                createCacheContainingDocument(pipelineContext, staticStateSAXStore, containingDocument, xformsState);

                // If a submission took place during XForms initialization, we currently don't cache
                // TODO: Some cases could be easily handled, like GET
                if (containingDocument[0].isGotSubmission()) {
                    result.setNoCache();
                    return result;
                }

                // Set caching dependencies if the input was actually read
                for (Iterator i = containingDocument[0].getModels().iterator(); i.hasNext();) {
                    final XFormsModel currentModel = (XFormsModel) i.next();

                    // Add schema dependencies
                    final String schemaURI = currentModel.getSchemaURI();
                    if (schemaURI != null) {
                        if (logger.isDebugEnabled())
                            logger.debug("XForms - adding document cache dependency for schema: " + schemaURI);
                        result.addReference(containingDocument[0].getBaseURI(), schemaURI);
                    }

                    // Add instance source dependencies
                    for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                        final XFormsInstance currentInstance = (XFormsInstance) j.next();
                        final String instanceSourceURI = currentInstance.getInstanceSourceURI();
                        if (instanceSourceURI != null) {
                            if (logger.isDebugEnabled())
                                logger.debug("XForms - adding document cache dependency for instance: " + instanceSourceURI);
                            result.addReference(containingDocument[0].getBaseURI(), instanceSourceURI);
                        }
                    }

                    // TODO: Add @src attributes from controls
                }

                return result;
            }
        });

        try {
            // Create containing document if not done yet
            if (containingDocument[0] == null) {
                logger.debug("XForms - annotated document not cached, creating.");

                final SAXStore staticStateSAXStore = new SAXStore();
                result.getAnnotatedSAXStore().replay(new XFormsExtractor.XFormsExtractorContentHandler(pipelineContext, staticStateSAXStore));

                createCacheContainingDocument(pipelineContext, staticStateSAXStore, containingDocument, xformsState);
            } else {
                logger.debug("XForms - using cached annotated document.");
            }

            // Output resulting document
            outputResponse(pipelineContext, externalContext, result.annotatedSAXStore, containingDocument[0], contentHandler, xformsState[0]);
        } catch (Throwable e) {
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
            throw new OXFException(e);
        }
    }

    private void createCacheContainingDocument(PipelineContext pipelineContext, SAXStore staticStateSAXStore,
                                               XFormsContainingDocument[] containingDocument, XFormsServer.XFormsState[] xformsState) {

        // This is the state after XForms initialization
        boolean[] requireClientSubmission = new boolean[1];
        {
            final XFormsServer.XFormsState initialXFormsState
                    = new XFormsServer.XFormsState(XFormsUtils.encodeXML(pipelineContext, staticStateSAXStore, XFormsUtils.getEncryptionKey()), "");

            // TODO FIXME TEMP XXX
            final Document staticStateDocument = TransformerUtils.saxStoreToDom4jDocument(staticStateSAXStore);
            containingDocument[0] = XFormsServer.createXFormsContainingDocument(pipelineContext, initialXFormsState, null, staticStateDocument);

            final Document dynamicStateDocument = XFormsServer.createDynamicStateDocument(containingDocument[0], requireClientSubmission);
            xformsState[0] = new XFormsServer.XFormsState(initialXFormsState.getStaticState(), XFormsUtils.encodeXML(pipelineContext, dynamicStateDocument));
        }

        // Cache document if requested and possible
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
                                final ContentHandler contentHandler, final XFormsServer.XFormsState xformsState) throws SAXException {

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

        controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, xformsState, externalContext));

        // Process everything
        annotatedDocument.replay(controller);
    }
}
