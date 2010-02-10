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

import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class XFormsGroupDefaultHandler extends XFormsGroupHandler {

    private static final String XHTML_PREFIX = "{" + XMLConstants.XHTML_NAMESPACE_URI + "}";
    private static final int XHTML_PREFIX_LENGTH = XHTML_PREFIX.length();

    private static final Set<String> TABLE_CONTAINERS  = new HashSet<String>();
    static {
        TABLE_CONTAINERS.add("table");
        TABLE_CONTAINERS.add("tbody");
        TABLE_CONTAINERS.add("thead");
        TABLE_CONTAINERS.add("tfoot");
        TABLE_CONTAINERS.add("tr");
    }

    private boolean isGroupInTable;

    private DeferredContentHandler currentSavedOutput;
    private OutputInterceptor outputInterceptor;

    @Override
    public void init(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        super.init(uri, localname, qName, attributes);

        // Determine whether the closest xhtml:* parent is xhtml:table|xhtml:tbody|xhtml:thead|xhtml:tfoot|xhtml:tr
        final ElementHandlerController controller = handlerContext.getController();
        {
            final Stack<String> elementNames = controller.getElementNames();
            for (int i = elementNames.size() - 1; i >= 0; i--) {
                final String currentElementName = elementNames.get(i);
                if (currentElementName.startsWith(XHTML_PREFIX)) {
                    final String currentLocalName = currentElementName.substring(XHTML_PREFIX_LENGTH);
                    isGroupInTable = TABLE_CONTAINERS.contains(currentLocalName);
                    break;
                }
            }
        }
    }

    @Override
    protected boolean isMustOutputContainerElement() {
        // Don't output a container element if we are in a table
        return !isGroupInTable;
    }

    public void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, final String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        // Start xhtml:span or xhtml:fieldset
        final String groupElementName = getContainingElementName();
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);

        final ElementHandlerController controller = handlerContext.getController();

        if (!isGroupInTable) {
            // Group outside table

            // Start xhtml:span element
            if (!handlerContext.isSpanHTMLLayout())
                controller.getOutput().startElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName,
                        getContainerAttributes(uri, localname, attributes, effectiveId, xformsControl, false));

        } else {
            // Group within table

            // Place interceptor on output

            // NOTE: Strictly, we should be able to do without the interceptor. We use it here because it
            // automatically handles ids and element names
            currentSavedOutput = controller.getOutput();
            if (!handlerContext.isNoScript()) {

                final String elementClasses;
                {
                    // Get classes
                    // As of August 2009, actually only need the marker class as well as xforms-disabled if the group is non-relevant
                    final StringBuilder classes = new StringBuilder();
                    handleMIPClasses(classes, getPrefixedId(), xformsControl);
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
                        outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), firstDelimiterClasses, "group-begin-" + effectiveId);
                    }
                });
                // TODO: is the use of XFormsElementFilterContentHandler necessary now?
                controller.setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(outputInterceptor)));

                // Set control classes
                outputInterceptor.setAddedClasses(elementClasses);
            } else if (isDisabled(xformsControl)) {
                // In noscript, if the group not visible, set output to a black hole
                handlerContext.getController().setOutput(new DeferredContentHandlerAdapter());
            }

            // Don't support label, help, alert, or hint and other appearances, only the content!
        }
    }

    @Override
    public void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final ElementHandlerController controller = handlerContext.getController();
        if (!isGroupInTable) {
            // Group outside table

            // Close xhtml:span
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String groupElementName = getContainingElementName();
            final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);

            if (!handlerContext.isSpanHTMLLayout())
                controller.getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName);
        } else {
            // Group within table

            if (!handlerContext.isNoScript()) {
                // Restore output
                controller.setOutput(currentSavedOutput);

                // Delimiter: end repeat
                outputInterceptor.flushCharacters(true, true);
                outputInterceptor.outputDelimiter(currentSavedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-group-begin-end", "group-end-" + effectiveId);
            } else if (isDisabled(xformsControl)) {
                // In noscript, group was not visible, restore output
                handlerContext.getController().setOutput(currentSavedOutput);
            }

            // Don't support help, alert, or hint!
        }
    }

    @Override
    protected void handleLabel() throws SAXException {
        if (!isGroupInTable) {
            // TODO: check why we output our own label here

            final XFormsSingleNodeControl xformsControl = getXFormsControl();
            final String effectiveId = getEffectiveId();

            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, getLabelClasses(xformsControl));
            outputLabelFor(handlerContext, reusableAttributes, effectiveId, effectiveId, LHHAC.LABEL, handlerContext.getLabelElementName(),
                    getLabelValue(xformsControl), xformsControl != null && xformsControl.isHTMLLabel(pipelineContext), !handlerContext.isSpanHTMLLayout());
        }
    }

    @Override
    protected void handleHint() throws SAXException {
        if (!isGroupInTable)
            super.handleHint();
    }

    @Override
    protected void handleAlert() throws SAXException {
        if (!isGroupInTable)
            super.handleAlert();
    }

    @Override
    protected void handleHelp() throws SAXException {
        if (!isGroupInTable)
            super.handleHelp();
    }
}
