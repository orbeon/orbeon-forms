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
import org.orbeon.oxf.xforms.processor.XFormsElementFilterContentHandler;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.*;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Stack;
import java.util.Map;
import java.util.HashMap;

/**
 * Handle xforms:group.
 */
public class XFormsGroupHandler extends HandlerBase {

    protected String effectiveGroupId;
    private XFormsControl groupXFormsControl;
    private boolean isFieldsetAppearance;
    private boolean isInternalAppearance;

    private boolean isGroupInTable;
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

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        // Special appearance that does not output any HTML. This is temporary until xforms:group is correctly supported within xforms:repeat.
        isInternalAppearance = XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME.equals(getAppearance(attributes));
        if (isInternalAppearance)
            return;

        effectiveGroupId = handlerContext.getEffectiveId(attributes);
        isFieldsetAppearance = XFormsConstants.XXFORMS_FIELDSET_APPEARANCE_QNAME.equals(getAppearance(attributes));

        // Find classes to add
        final StringBuffer classes = getInitialClasses(localname, attributes, null);
        if (!handlerContext.isGenerateTemplate()) {
            groupXFormsControl = ((XFormsControl) containingDocument.getObjectById(handlerContext.getPipelineContext(), effectiveGroupId));

            HandlerBase.handleMIPClasses(classes, groupXFormsControl);
        }

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

        // Start xhtml:span or xhtml:fieldset
        final String groupElementName = isFieldsetAppearance ? "fieldset" : "span";
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);

        if (!isGroupInTable) {

            final ContentHandler contentHandler = controller.getOutput();
            final String labelValue = (handlerContext.isGenerateTemplate() || groupXFormsControl == null) ? null : groupXFormsControl.getLabel();
            if (isFieldsetAppearance) {

                // Start xhtml:fieldset element
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName, getAttributes(attributes, classes.toString(), effectiveGroupId));

                // Output an xhtml:legend element
                final AttributesImpl labelAttributes = getAttributes(attributes, null, null);
                final String legendQName = XMLUtils.buildQName(xhtmlPrefix, "legend");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName, labelAttributes);
                if (labelValue != null && !labelValue.equals(""))
                    contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName);
            } else {

                // TODO: Should find a way to find xforms:alert/@class

                // Output an xhtml:label element if and only if there is an xforms:label element

                // TODO: Label element detection should be based on static control info so we don't needlessly generate
                // a label in the template. Maybe XFormsControls should just support a method to obtain a control
                // element by id.
                if (handlerContext.isGenerateTemplate() || groupXFormsControl.hasLabel()) {
                    reusableAttributes.clear();

                    // Handle relevance
                    final FastStringBuffer labelClasses = new FastStringBuffer("xforms-label");
                    if (groupXFormsControl != null && !groupXFormsControl.isRelevant())
                        labelClasses.append(" xforms-disabled");

                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, labelClasses.toString());
                    outputLabelFor(handlerContext, reusableAttributes, effectiveGroupId, labelValue);
                }

                // Start xhtml:span element
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName, getAttributes(attributes, classes.toString(), effectiveGroupId));
            }

            // NOTE: This doesn't work because attributes for the label are only gathered after start()
//            handleLabelHintHelpAlert(effectiveGroupId, "label", groupXFormsControl);
        } else {

            // Place interceptor on output

            // NOTE: Strictly, we should be able to do without the interceptor. We use it here because it
            // automatically handles ids and element names

            savedOutput = controller.getOutput();
            outputInterceptor = new OutputInterceptor(savedOutput, groupElementQName, new OutputInterceptor.Listener() {
                public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                    // Delimiter: begin group
                    outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-group-begin-end", "group-begin-" + effectiveGroupId);
                }
            });
            controller.setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(outputInterceptor)));
            setContentHandler(controller.getOutput());

            // Set control classes
            outputInterceptor.setAddedClasses(classes);

            // Don't support label and other appearances, only the content!
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        if (isInternalAppearance)
            return;

        final ElementHandlerController controller = handlerContext.getController();
        if (!isGroupInTable) {
            // Close xhtml:span
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String groupElementName = isFieldsetAppearance ? "fieldset" : "span";
            final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);
            controller.getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName);

            // xforms:help
            handleLabelHintHelpAlert(effectiveGroupId, "help", groupXFormsControl, false);

            // xforms:alert
            handleLabelHintHelpAlert(effectiveGroupId, "alert", groupXFormsControl, false);

            // xforms:hint
            handleLabelHintHelpAlert(effectiveGroupId, "hint", groupXFormsControl, false);
        } else {

            // Restore output
            controller.setOutput(savedOutput);
            setContentHandler(savedOutput);

            // Delimiter: end repeat
            outputInterceptor.flushCharacters(true, true);
            outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                    outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-group-begin-end", "group-end-" + effectiveGroupId);

            // Don't support help, alert, or hint!
        }
    }
}
