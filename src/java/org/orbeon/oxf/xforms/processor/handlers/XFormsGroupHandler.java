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

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.processor.XFormsElementFilterContentHandler;
import org.orbeon.oxf.xml.*;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Handle xforms:group.
 */
public class XFormsGroupHandler extends XFormsControlLifecyleHandler {

    // Appearances
    private boolean isFieldsetAppearance;
    private boolean isInternalAppearance;

    private boolean isGroupInTable;

    // Label information gathered during prepareHandler()
    private String labelValue;
    private FastStringBuffer labelClasses;

    private DeferredContentHandler savedOutput;
    private OutputInterceptor outputInterceptor;

    private static final String XHTML_PREFIX = "{" + XMLConstants.XHTML_NAMESPACE_URI + "}";
    private static final int XHTML_PREFIX_LENGTH = XHTML_PREFIX.length();
    private static final Map TABLE_CONTAINERS  = new HashMap();

    static {
        TABLE_CONTAINERS.put("table", "");
        TABLE_CONTAINERS.put("tbody", "");
        TABLE_CONTAINERS.put("thead", "");
        TABLE_CONTAINERS.put("tfoot", "");
        TABLE_CONTAINERS.put("tr", "");
    }

    public XFormsGroupHandler() {
        super(false, true);
    }

    protected void prepareHandler(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) {
        // Special appearance that does not output any HTML. This is temporary until xforms:group is correctly supported within xforms:repeat.
        isInternalAppearance = XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME.equals(getAppearance(attributes));
        if (isInternalAppearance)
            return;

        isFieldsetAppearance = XFormsConstants.XXFORMS_FIELDSET_APPEARANCE_QNAME.equals(getAppearance(attributes));

        // Determine whether the closest xhtml:* parent is xhtml:table|xhtml:tbody|xhtml:thead|xhtml:tfoot|xhtml:tr
        final ElementHandlerController controller = handlerContext.getController();
        {
            final Stack elementNames = controller.getElementNames();
            for (int i = elementNames.size() - 1; i >= 0; i--) {
                final String currentElementName = (String) elementNames.get(i);
                if (currentElementName.startsWith(XHTML_PREFIX)) {
                    final String currentLocalName = currentElementName.substring(XHTML_PREFIX_LENGTH);
                    isGroupInTable = (TABLE_CONTAINERS.get(currentLocalName) != null);
                    break;
                }
            }
        }

        if (!isGroupInTable) {
            // Gather information about the label and alert

            final boolean hasLabel = XFormsControl.hasLabel(containingDocument, xformsControl, staticId);
            final String labelClassAttribute;
            if (handlerContext.isTemplate() || xformsControl == null) {
                // Determine information statically

                final Element groupElement = ((XFormsStaticState.ControlInfo) containingDocument.getStaticState().getControlInfoMap().get(handlerContext.getId(attributes))).getElement();
                final Element labelElement = groupElement.element(XFormsConstants.XFORMS_LABEL_QNAME);

                labelValue = null;
                labelClassAttribute = hasLabel ? labelElement.attributeValue("class") : null;

            } else {
                // Determine information dynamically

                labelValue = xformsControl.getLabel(pipelineContext);

                if (hasLabel) {
                    final Element groupElement = xformsControl.getControlElement();
                    final Element labelElement = groupElement.element(XFormsConstants.XFORMS_LABEL_QNAME);

                    labelClassAttribute = labelElement.attributeValue("class");
                } else {
                    labelClassAttribute = null;
                }
            }

            if (hasLabel) {
                labelClasses = new FastStringBuffer("xforms-label");

                // Handle relevance on label
                if ((xformsControl == null && !handlerContext.isTemplate()) || (xformsControl != null && !xformsControl.isRelevant())) {
                    labelClasses.append(" xforms-disabled");
                }

                // Copy over existing label classes if any
                if (labelClassAttribute != null) {
                    labelClasses.append(' ');
                    labelClasses.append(labelClassAttribute);
                }
            }
        }
    }

    public void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, final String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        // No additional markup for internal appearance
        if (isInternalAppearance)
            return;

        // Find classes to add
        final FastStringBuffer classes = getInitialClasses(localname, attributes, null);
        handleMIPClasses(classes, staticId, xformsControl);

        // Start xhtml:span or xhtml:fieldset
        final String groupElementName = isFieldsetAppearance ? "fieldset" : "span";
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);

        final ElementHandlerController controller = handlerContext.getController();

        if (!isGroupInTable) {

            final ContentHandler contentHandler = controller.getOutput();

            if (isFieldsetAppearance) {
                // Fieldset appearance

                // Start xhtml:fieldset element
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName, getAttributes(attributes, classes.toString(), effectiveId));

                // Output an xhtml:legend element if and only if there is an xforms:label element. This help with
                // styling in particular.
                final boolean hasLabel = XFormsControl.hasLabel(containingDocument, xformsControl, staticId);
                if (hasLabel) {

                    // Handle label classes
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, labelClasses.toString());

                    // Output xhtml:legend with label content
                    final String legendQName = XMLUtils.buildQName(xhtmlPrefix, "legend");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName, reusableAttributes);
                    if (labelValue != null && !labelValue.equals(""))
                        contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName);
                }
            } else {
                // Default appearance

                // Label is handled by handleLabel()

                // Start xhtml:span element
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName, getAttributes(attributes, classes.toString(), effectiveId));
            }
        } else {

            // Place interceptor on output

            // NOTE: Strictly, we should be able to do without the interceptor. We use it here because it
            // automatically handles ids and element names

            savedOutput = controller.getOutput();
            outputInterceptor = new OutputInterceptor(savedOutput, groupElementQName, new OutputInterceptor.Listener() {
                public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                    // Delimiter: begin group
                    outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-group-begin-end", "group-begin-" + effectiveId);
                }
            });
            controller.setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(outputInterceptor)));
            setContentHandler(controller.getOutput());

            // Set control classes
            outputInterceptor.setAddedClasses(classes);

            // Don't support label, help, alert, or hint and other appearances, only the content!
        }
    }

    public void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        // No additional markup for internal appearance
        if (isInternalAppearance)
            return;

        final ElementHandlerController controller = handlerContext.getController();
        if (!isGroupInTable) {
            // Close xhtml:span
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String groupElementName = isFieldsetAppearance ? "fieldset" : "span";
            final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);
            controller.getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName);
        } else {

            // Restore output
            controller.setOutput(savedOutput);
            setContentHandler(savedOutput);

            // Delimiter: end repeat
            outputInterceptor.flushCharacters(true, true);
            outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                    outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-group-begin-end", "group-end-" + effectiveId);

            // Don't support help, alert, or hint!
        }
    }

    protected void handleLabel(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        if (!isInternalAppearance && !isGroupInTable && !isFieldsetAppearance) {// regular group
            // Output an xhtml:label element if and only if there is an xforms:label element. This help with
            // styling in particular.
            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, labelClasses.toString());
            outputLabelFor(handlerContext, reusableAttributes, effectiveId, labelValue, xformsControl != null && xformsControl.isHTMLLabel(pipelineContext));
        }
    }

    protected void handleHint(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        if (!isInternalAppearance && !isGroupInTable)
            super.handleHint(staticId, effectiveId, xformsControl, isTemplate);
    }

    protected void handleAlert(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        if (!isInternalAppearance && !isGroupInTable)
            super.handleAlert(staticId, effectiveId, attributes, xformsControl, isTemplate);
    }

    protected void handleHelp(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        if (!isInternalAppearance && !isGroupInTable)
            super.handleHelp(staticId, effectiveId, xformsControl, isTemplate);
    }
}
