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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl;
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor;
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor.Listener;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Handle xforms:case.
 *
 * TODO: This currently is based on delimiters. This is wrong: should be the case, like group, only around <tr>, etc.
 */
public class XFormsCaseHandler extends XFormsControlLifecyleHandler {

    private DeferredXMLReceiver currentSavedOutput;
    private OutputInterceptor currentOutputInterceptor;
    private boolean isVisible;

    public XFormsCaseHandler() {
        super(false, true);
    }

    @Override
    protected boolean isMustOutputContainerElement() {
        // If we are the top-level of a full update, output a delimiter anyway
        return handlerContext.isFullUpdateTopLevelControl(getEffectiveId());
    }

    @Override
    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, final String effectiveId, XFormsControl control) throws SAXException {


        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

        // Determine whether this case is visible
        final XFormsCaseControl caseControl = (XFormsCaseControl) containingDocument.getControls().getObjectByEffectiveId(effectiveId);
        if (!handlerContext.isTemplate() && caseControl != null) {
            // This case is visible if it is selected or if the switch is read-only and we display read-only as static
            isVisible = caseControl.isVisible();
        } else {
            isVisible = false;
        }

        final ElementHandlerController controller = handlerContext.getController();
        currentSavedOutput = controller.getOutput();

        // Place interceptor if needed
        if (!handlerContext.isNoScript()) {

            final boolean isMustGenerateBeginEndDelimiters = !handlerContext.isFullUpdateTopLevelControl(effectiveId);

            // Classes on top-level elements and characters and on the first delimiter
            final String elementClasses;
            {
                final StringBuilder classes = new StringBuilder();
                appendControlUserClasses(attributes, control, classes);
                // Don't add MIP classes as they can conflict with classes of nested content if used outside <tr>, etc.
                elementClasses = classes.toString();
            }

            currentOutputInterceptor = new OutputInterceptor(currentSavedOutput, spanQName, new OutputInterceptor.Listener() {

                // Classes on first delimiter
                private final String firstDelimiterClasses;
                {
                    final StringBuilder classes = new StringBuilder("xforms-case-begin-end");
                    if (elementClasses.length() > 0) {
                        classes.append(' ');
                        classes.append(elementClasses);
                    }
                    firstDelimiterClasses = classes.toString();
                }
                public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                    if (isMustGenerateBeginEndDelimiters) {
                        // Delimiter: begin case
                        outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), firstDelimiterClasses,
                                "xforms-case-begin-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
                    }
                }
            });

            final String controlClasses; {
                final StringBuilder classes = new StringBuilder(isVisible ? "xforms-case-selected" : "xforms-case-deselected");
                if (elementClasses.length() > 0) {
                    classes.append(' ');
                    classes.append(elementClasses);
                }
                controlClasses = classes.toString();
            }

            currentOutputInterceptor.setAddedClasses(controlClasses);

            controller.setOutput(new DeferredXMLReceiverImpl(currentOutputInterceptor));
        } else if (!isVisible) {
            // Case not visible, set output to a black hole
            controller.setOutput(new DeferredXMLReceiverAdapter());
        }

        handlerContext.pushCaseContext(isVisible);
    }

    @Override
    protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        handlerContext.popCaseContext();

        final ElementHandlerController controller = handlerContext.getController();
        if (!handlerContext.isNoScript()) {
            currentOutputInterceptor.flushCharacters(true, true);

            // Restore output
            controller.setOutput(currentSavedOutput);

            final boolean isMustGenerateBeginEndDelimiters = !handlerContext.isFullUpdateTopLevelControl(effectiveId);
            if (isMustGenerateBeginEndDelimiters) {
                if (currentOutputInterceptor.getDelimiterNamespaceURI() != null) {
                    // Output end delimiter
                    currentOutputInterceptor.outputDelimiter(currentSavedOutput, currentOutputInterceptor.getDelimiterNamespaceURI(),
                        currentOutputInterceptor.getDelimiterPrefix(), currentOutputInterceptor.getDelimiterLocalName(), "xforms-case-begin-end",
                            "xforms-case-end-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
                } else {
                    // Output start and end delimiter using xhtml:span
                    final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                    currentOutputInterceptor.outputDelimiter(currentSavedOutput, XMLConstants.XHTML_NAMESPACE_URI,
                        xhtmlPrefix, "span", "xforms-case-begin-end", "xforms-case-begin-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
                    currentOutputInterceptor.outputDelimiter(currentSavedOutput, XMLConstants.XHTML_NAMESPACE_URI,
                        xhtmlPrefix, "span", "xforms-case-begin-end", "xforms-case-end-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
                }
            }
        } else if (!isVisible) {
            // Case not visible, restore output
            controller.setOutput(currentSavedOutput);
        }
    }

    @Override
    protected void handleLabel() throws SAXException {
        // Don't output
    }

    @Override
    protected void handleHint() throws SAXException {
        // Don't output
    }

    @Override
    protected void handleAlert() throws SAXException {
        // Don't output
    }

    @Override
    protected void handleHelp() throws SAXException {
        // Don't output
    }
}
