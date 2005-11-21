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

import orbeon.apache.xml.utils.NamespaceSupport2;
import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsServer;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.XHTMLBodyHandler;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation of XForm + XHTML.
 */
public class XFormsToXHTML extends ProcessorImpl {

    static public Logger logger = XFormsServer.logger;

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String INPUT_STATIC_STATE = "static-state";
    private static final String OUTPUT_DOCUMENT = "document";

    public XFormsToXHTML() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_STATIC_STATE));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DOCUMENT));
    }

    /**
     * Case where an XML response must be generated.
     */
    public ProcessorOutput createOutput(final String outputName) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), outputName) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler);
            }
        };
        addOutput(outputName, output);
        return output;
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // XForms containing document
        final XFormsContainingDocument containingDocument;
        // This is the state after XForms initialization
        final NewXFormsServer.XFormsState xformsState;
        {
            final Document staticStateDocument = readInputAsDOM4J(pipelineContext, INPUT_STATIC_STATE);
            final NewXFormsServer.XFormsState initialXFormsState = new NewXFormsServer.XFormsState(XFormsUtils.encodeXML(pipelineContext, staticStateDocument, XFormsUtils.getEncryptionKey()), "");
            containingDocument = NewXFormsServer.createXFormsContainingDocument(pipelineContext, initialXFormsState, null, staticStateDocument);

            final Document dynamicStateDocument = NewXFormsServer.createDynamicStateDocument(containingDocument, new boolean[1]);
            xformsState = new NewXFormsServer.XFormsState(initialXFormsState.getStaticState(), XFormsUtils.encodeXMLAsDOM(pipelineContext, dynamicStateDocument));
        }

        try {
            // Output resulting document
            outputResponse(pipelineContext, externalContext, containingDocument, contentHandler, xformsState);
        } catch (Throwable e) {
            // If an exception is caught, we need to discard the object as its state may be inconsistent
            final ObjectPool sourceObjectPool = containingDocument.getSourceObjectPool();
            if (sourceObjectPool != null) {
                logger.debug("XForms - containing document cache: throwable caught, discarding document from pool.");
                try {
                    sourceObjectPool.invalidateObject(containingDocument);
                    containingDocument.setSourceObjectPool(null);
                } catch (Exception e1) {
                    throw new OXFException(e1);
                }
            }
            throw new OXFException(e);
        }
    }

    private void outputResponse(final PipelineContext pipelineContext, final ExternalContext externalContext, final XFormsContainingDocument containingDocument, final ContentHandler contentHandler, final NewXFormsServer.XFormsState xformsState) {

        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        readInputAsSAX(pipelineContext, INPUT_ANNOTATED_DOCUMENT, new ForwardingContentHandler(contentHandler) {

            private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();
            private HandlerContext handlerContext;
            private ElementHandler bodyElementHandler;

            public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                namespaceSupport.pushContext();

                if (XMLConstants.XHTML_NAMESPACE_URI.equals(uri)) {

                    if (localname.equals("body")) {
                        // xhtml:body

                        // Hook-up the xhtml:body element handler
                        handlerContext = new HandlerContext(pipelineContext, containingDocument,
                                xformsState, externalContext, namespaceSupport, new DeferredContentHandlerImpl(contentHandler));

                        bodyElementHandler = new XHTMLBodyHandler(handlerContext);
                        setContentHandler(bodyElementHandler);

                        bodyElementHandler.start(uri, localname, qName, attributes);

                    } else if (localname.equals("head")) {
                        super.startElement(uri, localname, qName, attributes);
                        // TODO: TEMP
//                        NewXFormsServer.diffControlsState(new ContentHandlerHelper(contentHandler), null, xformsControls.getCurrentControlsState().getChildren());
                    } else if (localname.equals("style")) {
                        super.startElement(uri, localname, qName, attributes);
                    } else {
                        super.startElement(uri, localname, qName, attributes);
                    }

                } else {
                    super.startElement(uri, localname, qName, attributes);
                }
            }

            public void endElement(String uri, String localname, String qName) throws SAXException {

                if (XMLConstants.XHTML_NAMESPACE_URI.equals(uri)) {
                    if (localname.equals("body")) {
                        // xhtml:body
                        bodyElementHandler.end(uri, localname, qName);

                        // Restore contentHandler
                        setContentHandler(contentHandler);
                    } else if (localname.equals("style")) {
                        super.endElement(uri, localname, qName);
                    } else {
                        super.endElement(uri, localname, qName);
                    }
                } else {
                    super.endElement(uri, localname, qName);
                }

                namespaceSupport.popContext();
            }

            public void startPrefixMapping(String prefix, String uri) throws SAXException {
                // Update global NamespaceSupport
                namespaceSupport.declarePrefix(prefix, uri);
                super.startPrefixMapping(prefix, uri);
            }
        });
    }
}
