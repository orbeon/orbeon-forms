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
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerNew;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.QName;
import org.dom4j.Namespace;

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

    public static void handleMIPClasses(StringBuffer sb, XFormsControl xformsControl) {
        if (xformsControl != null) {// TEMP, xformsControl should not be null
            if (!xformsControl.isRelevant()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-disabled");
            }
            if (!xformsControl.isValid()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-invalid");
            }
            if (xformsControl != null && xformsControl.isReadonly()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-readonly");
            }
            if (xformsControl != null && xformsControl.isRequired()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-required");
                if (xformsControl instanceof XFormsValueControl) {
                    if ("".equals(((XFormsValueControl) xformsControl).getValue()))
                        sb.append(" xforms-required-empty");
                    else
                        sb.append(" xforms-required-filled");
                }
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

    protected StringBuffer getInitialClasses(String controlName, Attributes controlAttributes, XFormsControl XFormsControl) {

        // Control name
        final StringBuffer sb;
        {
            // We only call xforms-control the actual controls as per the spec
            if (!XFormsControls.isGroupingControl(controlName))
                sb = new StringBuffer("xforms-control xforms-");
            else
                sb = new StringBuffer("xforms-");
            sb.append(controlName);
        }
        {
            // Class for incremental mode
            final String value = controlAttributes.getValue("incremental");
            if ("true".equals(value)) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-incremental");
            }
        }
        {
            // Class for appearance
            final QName appearance = getAppearance(controlAttributes);
            if (appearance != null) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-");
                sb.append(controlName);
                sb.append("-appearance-");
                // Allow xxforms:* and *
                if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearance.getNamespace().getURI()))
                    sb.append("xxforms-");
                else if (!"".equals(appearance.getNamespace().getURI()))
                    throw new OXFException("Invalid appearance namespace URI: " + appearance.getNamespace().getURI());
                sb.append(appearance.getName());
            }
        }
        {
            // Class for mediatype
            final String mediatypeValue = controlAttributes.getValue("mediatype");
            if (mediatypeValue != null) {

                // NOTE: We could certainly do a better check than this to make sure we have a valid mediatype
                final int slashIndex = mediatypeValue.indexOf('/');
                if (slashIndex == -1)
                    throw new OXFException("Invalid mediatype attribute value: " + mediatypeValue);

                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-mediatype-");
                if (mediatypeValue.endsWith("/*")) {
                    // Add class with just type: "image/*" -> "xforms-mediatype-image"
                    sb.append(mediatypeValue.substring(0, mediatypeValue.length() - 2));
                } else {
                    // Add class with type and subtype: "text/html" -> "xforms-mediatype-text-html"
                    sb.append(mediatypeValue.replace('/', '-'));
                    // Also add class with just type: "image/jpeg" -> "xforms-mediatype-image"
                    sb.append(" xforms-mediatype-");
                    sb.append(mediatypeValue.substring(0, slashIndex));
                }
            }
        }

        // Static read-only
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

    protected void handleLabelHintHelpAlert(String parentId, String type, XFormsControl xformsControl) throws SAXException {
        handleLabelHintHelpAlert(parentId, type, xformsControl, true);
    }

    protected void handleLabelHintHelpAlert(String parentId, String type, XFormsControl xformsControl, boolean placeholder) throws SAXException {

        // Don't handle alerts and help in read-only mode
        // TODO: Removing hints and help could be optional depending on appearance
        if (isStaticReadonly(xformsControl) && (type.equals("alert") || type.equals("hint")))
            return;

        final String labelHintHelpAlertValue;
        if (xformsControl != null) {
            // Get actual value from control
            if (type.equals("label")) {
                labelHintHelpAlertValue = xformsControl.getLabel();
            } else if (type.equals("help")) {
                labelHintHelpAlertValue = xformsControl.getHelp();
            } else if (type.equals("hint")) {
                labelHintHelpAlertValue = xformsControl.getHint();
            } else if (type.equals("alert")) {
                labelHintHelpAlertValue = xformsControl.getAlert();
            } else {
                throw new IllegalStateException("Illegal type requested");
            }
        } else {
            // Placeholder
            labelHintHelpAlertValue = null;
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

        if (labelHintHelpAlertAttributes != null || type.equals("alert")) {
            // If no attributes were found, there is no such label / help / hint / alert

            final StringBuffer classes = new StringBuffer("xforms-");
            classes.append(type);

            // Handle alert state
            if (type.equals("alert")) {
                if (!handlerContext.isGenerateTemplate() && !xformsControl.isValid())
                    classes.append(" xforms-alert-active");
                else
                    classes.append(" xforms-alert-inactive");
            }

            // Handle visibility
            // TODO: It would be great to actually know about the relevance of help, hint, and label. Right now, we just look at whether the value is empty
            if (!handlerContext.isGenerateTemplate()) {
                if (type.equals("alert") || type.equals("label")) {
                    // Allow empty labels and alerts
                    if (!xformsControl.isRelevant())
                        classes.append(" xforms-disabled");
                } else {
                    // For help and hint, consider "non-relevant" if empty
                    final boolean isHintHelpRelevant = xformsControl.isRelevant() && !(labelHintHelpAlertValue == null || labelHintHelpAlertValue.equals(""));
                    if (!isHintHelpRelevant) {
                        classes.append(" xforms-disabled");
                    }
                }
            }

            // We handle null attributes as well because we want a placeholder for "alert" even if there is no xforms:alert
            final Attributes newAttributes = (labelHintHelpAlertAttributes != null) ? labelHintHelpAlertAttributes : (placeholder) ? new AttributesImpl() : null;
            if (newAttributes != null) {
                outputLabelHintHelpAlert(handlerContext, getAttributes(newAttributes, classes.toString(), null), parentId, labelHintHelpAlertValue);
            }
        }
    }

    public static void outputLabelHintHelpAlert(HandlerContext handlerContext, AttributesImpl labelHintHelpAlertAttributes, String parentId, String value) throws SAXException {
        labelHintHelpAlertAttributes.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, parentId);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, "label");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "label", labelQName, labelHintHelpAlertAttributes);
        if (value != null) {
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

    protected QName getAppearance(Attributes controlAttributes) {
        final String appearanceValue = controlAttributes.getValue("appearance");
        if (appearanceValue == null)
            return null;

        final String appearanceLocalname = XMLUtils.localNameFromQName(appearanceValue);
        final String appearancePrefix = XMLUtils.prefixFromQName(appearanceValue);
        final String appearanceURI = uriFromQName(appearanceValue);

        return new QName(appearanceLocalname, new Namespace(appearancePrefix, appearanceURI));
    }
}
