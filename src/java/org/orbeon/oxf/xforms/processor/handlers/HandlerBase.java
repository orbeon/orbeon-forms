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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerNew;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 */
public abstract class HandlerBase extends ElementHandlerNew {

    private int level = 0;

    private Attributes labelAttributes;
    private Attributes helpAttributes;
    private Attributes hintAttributes;
    private Attributes alertAttributes;

    // NOTE: the XForms schema seems to indicates that "style", "onchange", and others
    // cannot be used; those should probably be in the XHTML namespace
    private static final String[] XHTML_ATTRIBUTES_TO_COPY = {"style", "onchange"};

    private boolean repeating;
    private boolean forwarding;

    protected HandlerContext handlerContext;
    protected PipelineContext pipelineContext;
    protected XFormsContainingDocument containingDocument;
    protected ExternalContext externalContext;

    protected AttributesImpl reusableAttributes = new AttributesImpl();

    protected HandlerBase(boolean repeating, boolean forwarding) {
        this.repeating = repeating;
        this.forwarding = forwarding;
    }

    public void setContext(Object context) {
        this.handlerContext = (HandlerContext) context;

        this.pipelineContext = handlerContext.getPipelineContext();
        this.containingDocument = handlerContext.getContainingDocument();
        this.externalContext = handlerContext.getExternalContext();

        super.setContext(context);
    }

    public boolean isRepeating() {
        return repeating;
    }

    public boolean isForwarding() {
        return forwarding;
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        // Reset state, as this handler may be reused
        level = 0;
        labelAttributes = null;
        helpAttributes = null;
        hintAttributes = null;
        alertAttributes = null;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        level++;
        if (level == 1 && XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            // Handle direct children only
            if ("label".equals(localname)) {
                labelAttributes = new AttributesImpl(attributes);
            } else if ("hint".equals(localname)) {
                hintAttributes = new AttributesImpl(attributes);
            } else if ("help".equals(localname)) {
                helpAttributes = new AttributesImpl(attributes);
            } else if ("alert".equals(localname)) {
                alertAttributes = new AttributesImpl(attributes);
            }
        }
        super.startElement(uri, localname, qName, attributes);
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        super.endElement(uri, localname, qName);
        level--;
    }

    public static void handleReadOnlyAttribute(AttributesImpl newAttributes, XFormsControl XFormsControl) {
        if (XFormsControl != null && XFormsControl.isReadonly()) {
            // @disabled="disabled"
            newAttributes.addAttribute("", "disabled", "disabled", ContentHandlerHelper.CDATA, "disabled");
        }
    }

    public static void handleMIPClasses(StringBuffer sb, XFormsControl XFormsControl) {
        if (XFormsControl != null) {// TEMP, controlInfo should not be null
            if (!XFormsControl.isRelevant()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-disabled");
            }
            if (!XFormsControl.isValid()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-invalid");
            }
            if (XFormsControl != null && XFormsControl.isReadonly()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-readonly");
            }
            if (XFormsControl != null && XFormsControl.isRequired()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-required");
                if ("".equals(XFormsControl.getValue()))
                    sb.append(" xforms-required-empty");
                else
                    sb.append(" xforms-required-filled");

            }
        }
    }

    public static boolean isDateOrTime(String type) {
        return isDate(type)
                || "{http://www.w3.org/2001/XMLSchema}dateTime".equals(type)
                || "{http://www.w3.org/2001/XMLSchema}time".equals(type);
    }

    public static boolean isDate(String type) {
        return "{http://www.w3.org/2001/XMLSchema}date".equals(type);
    }

    protected void handleAccessibilityAttributes(Attributes srcAttributes, AttributesImpl destAttributes) {
        // Handle "tabindex"
        {
            String value = srcAttributes.getValue("navindex");// This is the standard XForms attribute
            if (value == null)
                value = srcAttributes.getValue("tabindex");// This is the XHTML attribute
            if (value != null)
                destAttributes.addAttribute("", "tabindex", "tabindex", ContentHandlerHelper.CDATA, value);
        }
        // Handle "accesskey"
        {
            final String value = srcAttributes.getValue("accesskey");
            if (value != null)
                destAttributes.addAttribute("", "accesskey", "accesskey", ContentHandlerHelper.CDATA, value);
        }
    }

    protected AttributesImpl getAttributes(Attributes elementAttributes, String classes, String id) {
        reusableAttributes.clear();

        // Copy "id"
        if (id != null) {
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, id);
        }
        // Copy common attributes
        for (int i = 0; i < XHTML_ATTRIBUTES_TO_COPY.length; i++) {
            final String name = XHTML_ATTRIBUTES_TO_COPY[i];
            final String value = elementAttributes.getValue(name);
            if (value != null)
                reusableAttributes.addAttribute("", name, name, ContentHandlerHelper.CDATA, value);
        }
        // Create "class" attribute if necessary
        {
            final StringBuffer sb = new StringBuffer();
            // User-defined classes go first
            {
                final String value = elementAttributes.getValue("class");
                if (value != null) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(value);
                }
            }
            {
                final String value = elementAttributes.getValue(XMLConstants.XHTML_NAMESPACE_URI, "class");
                if (value != null) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(value);
                }
            }
            // XForms engine classes go next
            {
                if (classes != null) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(classes);
                }
            }
            {
                final String value = elementAttributes.getValue("incremental");
                if ("true".equals(value)) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append("xforms-incremental");
                }
            }
            if (sb.length() > 0) {
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, sb.toString());
            }
        }
        // Copy attributes in the xhtml namespace to no namespace
        for (int i = 0; i < elementAttributes.getLength(); i++) {
            if (XMLConstants.XHTML_NAMESPACE_URI.equals(elementAttributes.getURI(i))) {
                final String name = elementAttributes.getLocalName(i);
                if (!"class".equals(name)) {
                    reusableAttributes.addAttribute("", name, name, ContentHandlerHelper.CDATA, elementAttributes.getValue(i));
                }
            }
        }

        return reusableAttributes;
    }

    protected StringBuffer getInitialClasses(String controlName, XFormsControl XFormsControl) {
        final StringBuffer sb;
        if (!XFormsControls.isGroupingControl(controlName))
            sb = new StringBuffer("xforms-control xforms-" + controlName);
        else
            sb = new StringBuffer("xforms-" + controlName);// not sure why those wouldn't have xforms-control as well

        if (isStaticReadonly(XFormsControl))
            sb.append(" xforms-static");

        return sb;
    }

    protected String uriFromQName(String qName) {
        return XMLUtils.uriFromQName(qName, handlerContext.getController().getNamespaceSupport());
    }

    protected boolean isStaticReadonly(XFormsControl xformsControl) {
        return (xformsControl != null && xformsControl.isReadonly())
                && XFormsConstants.XXFORMS_READONLY_APPEARANCE_STATIC_VALUE.equals(containingDocument.getReadonlyAppearance());
    }

    protected void handleLabelHintHelpAlert(String parentId, String type, XFormsControl XFormsControl) throws SAXException {

        // Don't handle alerts and help in read-only mode
        // TODO: Removing hints and help could be optional depending on appearance
        if (isStaticReadonly(XFormsControl) && (type.equals("alert") || type.equals("hint")))
            return;

        final String value;
        if (XFormsControl != null) {
            // Get actual value from control
            if (type.equals("label")) {
                value = XFormsControl.getLabel();
            } else if (type.equals("help")) {
                value = XFormsControl.getHelp();
            } else if (type.equals("hint")) {
                value = XFormsControl.getHint();
            } else if (type.equals("alert")) {
                value = XFormsControl.getAlert();
            } else {
                throw new IllegalStateException("Illegal type requested");
            }
        } else {
            // Placeholder
            value = null;
        }

        // Find id
        final Attributes labelHintHelpAlertAttributes;
        if (type.equals("label")) {
            labelHintHelpAlertAttributes = labelAttributes;
        } else if (type.equals("help")) {
            labelHintHelpAlertAttributes = helpAttributes;
        } else if (type.equals("hint")) {
            labelHintHelpAlertAttributes = hintAttributes;
        } else if (type.equals("alert")) {
            labelHintHelpAlertAttributes = alertAttributes;
        } else {
            throw new IllegalStateException("Illegal type requested");
        }

        // If no attributes were found, there is no such label / help / hint
        if (labelHintHelpAlertAttributes != null) {
            //final String id = labelHintHelpId +  handlerContext.getIdPostfix();

            final StringBuffer classes = new StringBuffer("xforms-");
            classes.append(type);
            if (type.equals("alert")) {
                if (!handlerContext.isGenerateTemplate() && !XFormsControl.isValid())
                    classes.append(" xforms-alert-active");
                else
                    classes.append(" xforms-alert-inactive");
            }

            // If the value of a help, hint or label is empty, consider it as "non-relevant"
            // TODO: It would be great to actually know about the relevance of help, hint, and label. Right now, we just look at whether the value is empty
            if (!type.equals("alert")) {
                if (value == null || value.equals("")) {
                    classes.append(" xforms-disabled");
                }
            }

            outputLabelHintHelpAlert(handlerContext, getAttributes(labelHintHelpAlertAttributes, classes.toString(), null), parentId, value);
        }
    }

    public static void outputLabelHintHelpAlert(HandlerContext handlerContext, AttributesImpl labelHintHelpAlertAttributes, String parentId, String value) throws SAXException {
        labelHintHelpAlertAttributes.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, parentId);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, "label");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "label", labelQName, labelHintHelpAlertAttributes);
        if (!handlerContext.isGenerateTemplate() && value != null) {
            contentHandler.characters(value.toCharArray(), 0, value.length());
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "label", labelQName);
    }

    protected static void copyAttributes(Attributes sourceAttributes, String sourceNamespaceURI, String[] sourceAttributeLocalNames, AttributesImpl destAttributes) {
        for (int i = 0; i < sourceAttributeLocalNames.length; i++) {
            final String attributeName = sourceAttributeLocalNames[i];
            final String attributeValue = sourceAttributes.getValue(sourceNamespaceURI, attributeName);
            if (attributeValue != null)
                destAttributes.addAttribute("", attributeName, attributeName, ContentHandlerHelper.CDATA, attributeValue);
        }
    }
}
