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

import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class XFormsGroupSeparatorHandler extends XFormsGroupHandler {

    private DeferredContentHandler currentSavedOutput;
    private OutputInterceptor outputInterceptor;

    @Override
    protected boolean isMustOutputContainerElement() {
        // Don't output a container element unless in full update
        return handlerContext.isFullUpdate();
    }

    public void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, final String effectiveId, XFormsControl control) throws SAXException {

        final String groupElementName = getContainingElementName();
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);

        final ElementHandlerController controller = handlerContext.getController();

        // Place interceptor on output

        // NOTE: Strictly, we should be able to do without the interceptor. We use it here because it
        // automatically handles ids and element names
        currentSavedOutput = controller.getOutput();
        if (!handlerContext.isNoScript()) {

            final boolean isMustGenerateBeginEndDelimiters = !handlerContext.isFullUpdateTopLevelControl(effectiveId);

            final String elementClasses;
            {
                // Get classes
                // As of August 2009, actually only need the marker class as well as xforms-disabled if the group is non-relevant
                final StringBuilder classes = new StringBuilder();
                handleMIPClasses(classes, getPrefixedId(), control);
                elementClasses = classes.toString();
            }

            final String firstDelimiterClasses;
            {
                final StringBuilder classes = new StringBuilder("xforms-group-begin-end");
                if (elementClasses.length() > 0) {
                    classes.append(' ');
                    classes.append(elementClasses);
                }
                firstDelimiterClasses = classes.toString();
            }

            outputInterceptor = new OutputInterceptor(currentSavedOutput, groupElementQName, new OutputInterceptor.Listener() {
                public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                    // Delimiter: begin group
                    if (isMustGenerateBeginEndDelimiters) {
                        outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), firstDelimiterClasses, "group-begin-" + effectiveId);
                    }
                }
            });
            // TODO: is the use of XFormsElementFilterContentHandler necessary now?
            controller.setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(outputInterceptor)));

            // Set control classes
            outputInterceptor.setAddedClasses(elementClasses);
        } else if (isDisabled(control)) {
            // In noscript, if the group not visible, set output to a black hole
            handlerContext.getController().setOutput(new DeferredContentHandlerAdapter());
        }

        // Don't support label, help, alert, or hint and other appearances, only the content!
    }

    @Override
    public void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        final ElementHandlerController controller = handlerContext.getController();
        if (!handlerContext.isNoScript()) {
            // Restore output
            controller.setOutput(currentSavedOutput);

            // Delimiter: end repeat
            outputInterceptor.flushCharacters(true, true);

            final boolean isMustGenerateBeginEndDelimiters = !handlerContext.isFullUpdateTopLevelControl(effectiveId);
            if (isMustGenerateBeginEndDelimiters) {
                outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-group-begin-end", "group-end-" + effectiveId);
            }
        } else if (isDisabled(control)) {
            // In noscript, group was not visible, restore output
            handlerContext.getController().setOutput(currentSavedOutput);
        }

        // Don't support help, alert, or hint!
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
