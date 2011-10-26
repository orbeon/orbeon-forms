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
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor;
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor.Listener;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Group within xhtml:table|xhtml:tbody|xhtml:thead|xhtml:tfoot|xhtml:tr.
 */
public class XFormsGroupSeparatorHandler extends XFormsGroupHandler {

    private DeferredXMLReceiver currentSavedOutput;
    private OutputInterceptor outputInterceptor;

    @Override
    protected boolean isMustOutputContainerElement() {
        // If we are the top-level of a full update, output a delimiter anyway
        return handlerContext.isFullUpdateTopLevelControl(getEffectiveId());
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

            // Classes on top-level elements and characters and on the first delimiter
            final String elementClasses;
            {
                final StringBuilder classes = new StringBuilder();
                appendControlUserClasses(attributes, control, classes);
                // NOTE: Could also use getInitialClasses(uri, localname, attributes, control), but then we get the
                // xforms-group-appearance-xxforms-separator class. Is that desirable?
                handleMIPClasses(classes, getPrefixedId(), control); // as of August 2009, actually only need the marker class as well as xforms-disabled if the group is non-relevant
                elementClasses = classes.toString();
            }

            outputInterceptor = new OutputInterceptor(currentSavedOutput, groupElementQName, new OutputInterceptor.Listener() {

                // Classes on first delimiter
                private final String firstDelimiterClasses;
                {
                    final StringBuilder classes = new StringBuilder("xforms-group-begin-end");
                    if (elementClasses.length() > 0) {
                        classes.append(' ');
                        classes.append(elementClasses);
                    }
                    firstDelimiterClasses = classes.toString();
                }

                public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                    // Delimiter: begin group
                    if (isMustGenerateBeginEndDelimiters) {
                        outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), firstDelimiterClasses,
                                "group-begin-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
                    }
                }
            });
            
            controller.setOutput(new DeferredXMLReceiverImpl(outputInterceptor));

            // Set control classes
            outputInterceptor.setAddedClasses(elementClasses);
        } else if (isHTMLDisabled(control)) {
            // In noscript, if the group not visible, set output to a black hole
            controller.setOutput(new DeferredXMLReceiverAdapter());
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
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-group-begin-end",
                        "group-end-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
            }
        } else if (isHTMLDisabled(control)) {
            // In noscript, group was not visible, restore output
            controller.setOutput(currentSavedOutput);
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
