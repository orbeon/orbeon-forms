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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.NullHandler;
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLBodyHandler;
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLElementHandler;
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLHeadHandler;
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XXFormsAttributeHandler;
import org.orbeon.oxf.xforms.processor.handlers.xml.*;
import org.orbeon.oxf.xforms.state.AnnotatedTemplate;
import org.orbeon.oxf.xml.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
public class XFormsToXHTML extends XFormsToSomething {

    protected void produceOutput(PipelineContext pipelineContext,
                                 String outputName,
                                 ExternalContext externalContext,
                                 IndentedLogger indentedLogger,
                                 Stage2CacheableState stage2CacheableState,
                                 XFormsContainingDocument containingDocument,
                                 XMLReceiver xmlReceiver) throws IOException, SAXException {
            // Output resulting document
            if (outputName.equals("document")) {
                // Normal case where we output XHTML
                outputResponseDocument(externalContext, indentedLogger, stage2CacheableState.template,
                        containingDocument, xmlReceiver);
            } else {
                // Output in test mode
                testOutputResponseState(containingDocument, indentedLogger, xmlReceiver);
            }
    }

    public static void outputResponseDocument(final ExternalContext externalContext,
                                              final IndentedLogger indentedLogger,
                                              final AnnotatedTemplate template, final XFormsContainingDocument containingDocument,
                                              final XMLReceiver xmlReceiver) throws SAXException, IOException {

        final List<XFormsContainingDocument.Load> loads = containingDocument.getLoadsToRun();
        if (containingDocument.isGotSubmissionReplaceAll()) {
            // 1. Got a submission with replace="all"

            // NOP: Response already sent out by a submission
            indentedLogger.logDebug("", "handling response for submission with replace=\"all\"");
        } else if (loads != null && loads.size() > 0) {
            // 2. Got at least one xf:load

            // Send redirect out

            // Get first load only
            final XFormsContainingDocument.Load load = loads.get(0);

            // Send redirect
            final String redirectResource = load.getResource();
            indentedLogger.logDebug("", "handling redirect response for xf:load", "url", redirectResource);
            // Set isNoRewrite to true, because the resource is either a relative path or already contains the servlet context
            externalContext.getResponse().sendRedirect(redirectResource, null, false, false);

            // Still send out a null document to signal that no further processing must take place
            XMLUtils.streamNullDocument(xmlReceiver);
        } else {
            // 3. Regular case: produce an XHTML document out

            final ElementHandlerController controller = new ElementHandlerController();

            // Register handlers on controller (the other handlers are registered by the body handler)
            {
            	final boolean isHTMLDocument = containingDocument.getStaticState().isHTMLDocument();
				if (isHTMLDocument) {
	                controller.registerHandler(XHTMLHeadHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "head");
	                controller.registerHandler(XHTMLBodyHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "body");
            	} else {
            		controller.registerHandler(XFormsDefaultControlHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "input", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsDefaultControlHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "secret", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsDefaultControlHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "range", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsDefaultControlHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "textarea", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsDefaultControlHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsDefaultControlHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsDefaultControlHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "submit", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsSelectHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsSelectHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select1", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsGroupHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsCaseHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "case", XHTMLBodyHandler.ANY_MATCHER);
            		controller.registerHandler(XFormsRepeatHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "repeat", XHTMLBodyHandler.ANY_MATCHER);
            	}

                // Register a handler for AVTs on HTML elements
                final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
                if (hostLanguageAVTs) {
                    controller.registerHandler(XXFormsAttributeHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute");
                    if (isHTMLDocument) {
                    	controller.registerHandler(XHTMLElementHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI);
                    }

                    for(String additionalAvtElementNamespace: XFormsProperties.getAdditionalAvtElementNamespaces()) {
                    	controller.registerHandler(ElementHandlerXML.class.getName(), additionalAvtElementNamespace);
                    }
                }

            	if (isHTMLDocument) {
	                // Swallow XForms elements that are unknown
	                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
	                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI);
	                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XBL_NAMESPACE_URI);
            	}
            }

            // Set final output
            controller.setOutput(new DeferredXMLReceiverImpl(xmlReceiver));
            // Set handler context
            controller.setElementHandlerContext(new HandlerContext(controller, containingDocument, externalContext, null));
            // Process the entire input
            template.saxStore().replay(new ExceptionWrapperXMLReceiver(controller, "converting XHTML+XForms document to XHTML"));
        }

        containingDocument.afterInitialResponse();
    }

    private void testOutputResponseState(final XFormsContainingDocument containingDocument, final IndentedLogger indentedLogger,
                                         final XMLReceiver xmlReceiver) throws SAXException {
        // Output XML response
        XFormsServer.outputAjaxResponse(containingDocument, indentedLogger, null, null, null, null, xmlReceiver, false, true);
    }

}
