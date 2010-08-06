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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:case.
 */
public class XFormsCaseHandler extends XFormsControlLifecyleHandler {

    private DeferredXMLReceiver currentSavedOutput;
    private OutputInterceptor currentOutputInterceptor;
    private String currentCaseEffectiveId;
    private boolean isVisible;

    public XFormsCaseHandler() {
        super(false, true);
    }

    @Override
    protected boolean isMustOutputContainerElement() {
        // Don't output a container element unless in full update
        return handlerContext.isFullUpdate();
    }

    @Override
    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        currentCaseEffectiveId = handlerContext.getEffectiveId(attributes);

        // Determine whether this case is visible
        final XFormsCaseControl caseControl = (XFormsCaseControl) containingDocument.getControls().getObjectByEffectiveId (currentCaseEffectiveId);
        if (!handlerContext.isTemplate() && caseControl != null) {
            // This case is visible if it is selected or if the switch is read-only and we display read-only as static
            isVisible = caseControl.isVisible();
        } else {
            isVisible = false;
        }

        currentSavedOutput = handlerContext.getController().getOutput();

        // Place interceptor if needed
        if (!handlerContext.isNoScript()) {

            final boolean isMustGenerateBeginEndDelimiters = !handlerContext.isFullUpdateTopLevelControl(effectiveId);

            // Find classes to add
            final StringBuilder classes = getInitialClasses(uri, localname, attributes, null);

            final AttributesImpl newAttributes = getAttributes(attributes, classes.toString(), currentCaseEffectiveId);
            newAttributes.addAttribute("", "style", "style", ContentHandlerHelper.CDATA, "display: " + (isVisible ? "block" : "none"));

            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

            currentOutputInterceptor = new OutputInterceptor(currentSavedOutput, spanQName, new OutputInterceptor.Listener() {
                public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                    if (isMustGenerateBeginEndDelimiters) {
                        // Output begin delimiter
                        outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-case-begin-end",
                                "xforms-case-begin-" + XFormsUtils.namespaceId(containingDocument, currentCaseEffectiveId));
                    }
                }
            });

            currentOutputInterceptor.setAddedClasses(isVisible ? "xforms-case-selected" : "xforms-case-deselected");

            // TODO: is the use of XFormsElementFilterContentHandler necessary now?
            handlerContext.getController().setOutput(new DeferredXMLReceiverImpl(new XFormsElementFilterXMLReceiver(currentOutputInterceptor)));
        } else if (!isVisible) {
            // Case not visible, set output to a black hole
            handlerContext.getController().setOutput(new DeferredXMLReceiverAdapter());
        }
    }

    @Override
    protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        if (!handlerContext.isNoScript()) {
            currentOutputInterceptor.flushCharacters(true, true);

            // Restore output
            handlerContext.getController().setOutput(currentSavedOutput);

            final boolean isMustGenerateBeginEndDelimiters = !handlerContext.isFullUpdateTopLevelControl(effectiveId);
            if (isMustGenerateBeginEndDelimiters) {
                if (currentOutputInterceptor.getDelimiterNamespaceURI() != null) {
                    // Output end delimiter
                    currentOutputInterceptor.outputDelimiter(currentSavedOutput, currentOutputInterceptor.getDelimiterNamespaceURI(),
                        currentOutputInterceptor.getDelimiterPrefix(), currentOutputInterceptor.getDelimiterLocalName(), "xforms-case-begin-end",
                            "xforms-case-end-" + XFormsUtils.namespaceId(containingDocument, currentCaseEffectiveId));
                } else {
                    // Output start and end delimiter using xhtml:span
                    final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                    currentOutputInterceptor.outputDelimiter(currentSavedOutput, XMLConstants.XHTML_NAMESPACE_URI,
                        xhtmlPrefix, "span", "xforms-case-begin-end", "xforms-case-begin-" + XFormsUtils.namespaceId(containingDocument, currentCaseEffectiveId));
                    currentOutputInterceptor.outputDelimiter(currentSavedOutput, XMLConstants.XHTML_NAMESPACE_URI,
                        xhtmlPrefix, "span", "xforms-case-begin-end", "xforms-case-end-" + XFormsUtils.namespaceId(containingDocument, currentCaseEffectiveId));
                }
            }
        } else if (!isVisible) {
            // Case not visible, restore output
            handlerContext.getController().setOutput(currentSavedOutput);
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
