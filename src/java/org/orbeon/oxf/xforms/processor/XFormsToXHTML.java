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
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xml.DeferredContentHandlerImpl;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
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
        final XFormsServer.XFormsState xformsState;
        {
            final Document staticStateDocument = readInputAsDOM4J(pipelineContext, INPUT_STATIC_STATE);
            final XFormsServer.XFormsState initialXFormsState = new XFormsServer.XFormsState(XFormsUtils.encodeXML(pipelineContext, staticStateDocument, XFormsUtils.getEncryptionKey()), "");
            containingDocument = XFormsServer.createXFormsContainingDocument(pipelineContext, initialXFormsState, null, staticStateDocument);

            final Document dynamicStateDocument = XFormsServer.createDynamicStateDocument(containingDocument, new boolean[1]);
            xformsState = new XFormsServer.XFormsState(initialXFormsState.getStaticState(), XFormsUtils.encodeXML(pipelineContext, dynamicStateDocument));
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

    private void outputResponse(final PipelineContext pipelineContext, final ExternalContext externalContext, final XFormsContainingDocument containingDocument, final ContentHandler contentHandler, final XFormsServer.XFormsState xformsState) {

        final ElementHandlerController controller = new ElementHandlerController();

        // Register handlers on controller
        controller.registerHandler(XFormsInputHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "input");
        controller.registerHandler(XFormsOutputHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output");
        controller.registerHandler(XFormsTriggerHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger");
        controller.registerHandler(XFormsSubmitHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "submit");
        controller.registerHandler(XFormsSecretHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "secret");
        controller.registerHandler(XFormsTextareaHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "textarea");
        controller.registerHandler(XFormsUploadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "upload");
        controller.registerHandler(XFormsRangeHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "range");
        controller.registerHandler(XFormsSelectHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select");
        controller.registerHandler(XFormsSelect1Handler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select1");

        controller.registerHandler(XFormsGroupHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group");
        controller.registerHandler(XFormsSwitchHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "switch");
        controller.registerHandler(XFormsRepeatHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "repeat");

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
        readInputAsSAX(pipelineContext, INPUT_ANNOTATED_DOCUMENT, controller);
    }
}
