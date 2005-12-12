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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;

/**
 * Handle xforms:switch and xforms:case.
 */
public class XFormsSwitchHandler extends XFormsGroupHandler {

    private DeferredContentHandler currentSavedOutput;
    private OutputInterceptor currentOutputInterceptor;
    private String currentCaseEffectiveId;

    public XFormsSwitchHandler() {
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) && localname.equals("case")) {
            // xforms:case

            currentCaseEffectiveId = handlerContext.getEffectiveId(attributes);

            // Find classes to add
            final StringBuffer classes = new StringBuffer("xforms-" + localname);

            final AttributesImpl newAttributes = getAttributes(attributes, classes.toString(), currentCaseEffectiveId);

            final Map switchIdToSelectedCaseIdMap = containingDocument.getXFormsControls().getCurrentControlsState().getSwitchIdToSelectedCaseIdMap();

            final String selectedCaseId = (String) switchIdToSelectedCaseIdMap.get(effectiveGroupId);
            final boolean isVisible = currentCaseEffectiveId.equals(selectedCaseId);
            newAttributes.addAttribute("", "style", "style", ContentHandlerHelper.CDATA, "display: " + (isVisible ? "block" : "none"));

            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

            // Place interceptor
            currentSavedOutput = handlerContext.getController().getOutput();
            currentOutputInterceptor = new OutputInterceptor(currentSavedOutput, spanQName, new OutputInterceptor.Listener() {
                public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                    // Output begin delimiter
                    outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-case-begin-end", "xforms-case-begin-" + currentCaseEffectiveId);
                }
            });

            currentOutputInterceptor.setAddedClasses(new StringBuffer(isVisible ? "xforms-case-selected" : "xforms-case-deselected"));

            handlerContext.getController().setOutput(new DeferredContentHandlerImpl(currentOutputInterceptor));
            setContentHandler(handlerContext.getController().getOutput());

        } else {
            super.startElement(uri, localname, qName, attributes);
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) && localname.equals("case")) {
            // xforms:case

            // Restore output
            handlerContext.getController().setOutput(currentSavedOutput);
            setContentHandler(currentSavedOutput);

            // Output end delimiter
            currentOutputInterceptor.outputDelimiter(currentSavedOutput, currentOutputInterceptor.getDelimiterNamespaceURI(),
                    currentOutputInterceptor.getDelimiterPrefix(), currentOutputInterceptor.getDelimiterLocalName(), "xforms-case-begin-end", "xforms-case-end-" + currentCaseEffectiveId);

        } else {
            super.endElement(uri, localname, qName);
        }
    }
}
