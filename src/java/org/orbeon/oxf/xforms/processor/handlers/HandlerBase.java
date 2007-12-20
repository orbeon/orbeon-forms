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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.QName;
import org.dom4j.Namespace;

/**
 *
 */
public abstract class HandlerBase extends ElementHandler {

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

    public static void handleReadOnlyAttribute(AttributesImpl newAttributes, XFormsContainingDocument containingDocument, XFormsSingleNodeControl xformsControl) {
        if (xformsControl != null && xformsControl.isReadonly() && !XFormsProperties.isStaticReadonlyAppearance(containingDocument)) {
            // @disabled="disabled"
            newAttributes.addAttribute("", "disabled", "disabled", ContentHandlerHelper.CDATA, "disabled");
        }
    }

    public static void handleMIPClasses(StringBuffer sb, XFormsSingleNodeControl xformsControl) {
        if (xformsControl != null) {
            // The case of a concrete control
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
            if (xformsControl.isReadonly()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-readonly");
            }
            if (xformsControl.isRequired()) {
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
            final String type = xformsControl.getType();
            if (type != null && (type.startsWith(XFormsConstants.XSD_EXPLODED_TYPE_PREFIX) || type.startsWith(XFormsConstants.XFORMS_EXPLODED_TYPE_PREFIX))) {
                // Control is bound to built-in schema type
                if (sb.length() > 0)
                    sb.append(' ');

                final String typeLocalname = type.substring(type.indexOf('}') + 1);
                sb.append("xforms-type-");
                sb.append(typeLocalname);
            }
        } else {
            // Case of a non-concrete control - simply mark the control as disabled
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("xforms-disabled");
        }
    }

    public static boolean isDateOrTime(String type) {
        if (type != null){
            // Support both xs:* and xforms:*
            final boolean isBuiltInSchemaType = type.startsWith(XFormsConstants.XSD_EXPLODED_TYPE_PREFIX);
            final boolean isBuiltInXFormsType = type.startsWith(XFormsConstants.XFORMS_EXPLODED_TYPE_PREFIX);

            if (isBuiltInSchemaType || isBuiltInXFormsType) {
                final String typeName = type.substring(type.indexOf('}') + 1);
                return "date".equals(typeName) || "dateTime".equals(typeName) || "time".equals(typeName);
            } else {
                return false;
            }
        } else {
            return false;
        }
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
            final FastStringBuffer sb = new FastStringBuffer(100);
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

    protected StringBuffer getInitialClasses(String controlName, Attributes controlAttributes, XFormsControl xformsControl) {
        return getInitialClasses(controlName, controlAttributes, xformsControl, null, false);
    }

    protected StringBuffer getInitialClasses(String controlName, Attributes controlAttributes, XFormsControl xformsControl, QName appearance, boolean incrementalDefault) {

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
            // Set the class if the default is non-incremental and the user explicitly set the value to true, or the
            // default is incremental and the user did not explicitly set it to false
            if ((!incrementalDefault && "true".equals(value)) || (incrementalDefault && !"false".equals(value))) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-incremental");
            }
        }
        {
            // Class for appearance
            if (appearance == null)
                appearance = getAppearance(controlAttributes);
            
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
                    throw new ValidationException("Invalid appearance namespace URI: " + appearance.getNamespace().getURI(), handlerContext.getLocationData());
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
                    throw new ValidationException("Invalid mediatype attribute value: " + mediatypeValue, handlerContext.getLocationData());

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
        if (isStaticReadonly(xformsControl))
            sb.append(" xforms-static");

        return sb;
    }

    protected String uriFromQName(String qName) {
        return XMLUtils.uriFromQName(qName, handlerContext.getController().getNamespaceSupport());
    }

    protected boolean isStaticReadonly(XFormsControl xformsControl) {
        return xformsControl != null && xformsControl.isStaticReadonly();
    }

    protected void handleLabelHintHelpAlert(String parentId, String type, XFormsSingleNodeControl xformsControl) throws SAXException {
        handleLabelHintHelpAlert(parentId, type, xformsControl, true);
    }

    protected void handleLabelHintHelpAlert(String parentId, String type, XFormsSingleNodeControl xformsControl, boolean placeholder) throws SAXException {

        final boolean isHint = type.equals("hint");
        final boolean isAlert = type.equals("alert");

        // Don't handle alerts and help in read-only mode
        // TODO: Removing hints and help could be optional depending on appearance
        if (isStaticReadonly(xformsControl) && (isAlert || isHint))
            return;

        final boolean isLabel = type.equals("label");
        final boolean isHelp = type.equals("help");

        final String labelHintHelpAlertValue;
        final boolean mustOutputHTMLFragment;
        if (xformsControl != null) {
            // Get actual value from control
            if (isLabel) {
                labelHintHelpAlertValue = xformsControl.getLabel(pipelineContext);
                mustOutputHTMLFragment = xformsControl.isHTMLLabel(pipelineContext);
            } else if (isHelp) {
                // NOTE: Special case here where we get the escaped help to facilitate work below. Help is a special
                // case because it is stored as escaped HTML within a <label> element.
                labelHintHelpAlertValue = xformsControl.getEscapedHelp(pipelineContext);
                mustOutputHTMLFragment = false;
            } else if (isHint) {
                labelHintHelpAlertValue = xformsControl.getHint(pipelineContext);
                mustOutputHTMLFragment = xformsControl.isHTMLHint(pipelineContext);
            } else if (isAlert) {
                labelHintHelpAlertValue = xformsControl.getAlert(pipelineContext);
                mustOutputHTMLFragment = xformsControl.isHTMLAlert(pipelineContext);
            } else {
                throw new IllegalStateException("Illegal type requested");
            }
        } else {
            // Placeholder
            labelHintHelpAlertValue = null;
            mustOutputHTMLFragment = false;
        }

        // Find attributes
        final Attributes labelHintHelpAlertAttributes;
        if (isLabel) {
            labelHintHelpAlertAttributes = labelAttributes;
        } else if (isHelp) {
            labelHintHelpAlertAttributes = helpAttributes;
        } else if (isHint) {
            labelHintHelpAlertAttributes = hintAttributes;
        } else if (isAlert) {
            labelHintHelpAlertAttributes = alertAttributes;
        } else {
            throw new IllegalStateException("Illegal type requested");
        }

        if (labelHintHelpAlertAttributes != null || isAlert) {
            // If no attributes were found, there is no such label / help / hint / alert

            final StringBuffer classes = new StringBuffer();

            // Handle alert state
            if (isAlert) {
                if (!handlerContext.isGenerateTemplate() && xformsControl != null && !xformsControl.isValid())
                    classes.append(" xforms-alert-active");
                else
                    classes.append(" xforms-alert-inactive");
            }

            // Handle visibility
            // TODO: It would be great to actually know about the relevance of help, hint, and label. Right now, we just look at whether the value is empty
            if (!handlerContext.isGenerateTemplate() && xformsControl != null) {
                if (isAlert || isLabel) {
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
            } else {
                // Repeat template or null control
                classes.append(" xforms-disabled");
            }

            classes.append(" xforms-");
            classes.append(type);

            final String labelClasses = classes.toString();

            if (isHelp) {
                // HACK: For help, output XHTML image natively in order to help with the IE bug whereby IE reloads
                // background images way too often.

                classes.append("-image"); // xforms-help-image class
                final String helpImageClasses = classes.toString();

                final AttributesImpl imgAttributes = new AttributesImpl();
                imgAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, helpImageClasses);
                imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA,
                        externalContext.getResponse().rewriteResourceURL(XFormsConstants.HELP_IMAGE_URI, false));
                imgAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, "");
                imgAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, "Help");

                // TODO: xmlns:f declaration should be placed on xhtml:body
                final String formattingPrefix = handlerContext.findFormattingPrefixDeclare();
                imgAttributes.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");

                final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                final ContentHandler contentHandler = handlerContext.getController().getOutput();
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, imgAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);

                handlerContext.findFormattingPrefixUndeclare(formattingPrefix);
            }

            // We handle null attributes as well because we want a placeholder for "alert" even if there is no xforms:alert
            final Attributes newAttributes = (labelHintHelpAlertAttributes != null) ? labelHintHelpAlertAttributes : (placeholder) ? new AttributesImpl() : null;
            if (newAttributes != null) {
                outputLabelFor(handlerContext, getAttributes(newAttributes, labelClasses, null), parentId, labelHintHelpAlertValue, mustOutputHTMLFragment);
            }
        }
    }

    public static void outputLabelFor(HandlerContext handlerContext, AttributesImpl attributes, String forId, String value, boolean mustOutputHTMLFragment) throws SAXException {
        attributes.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, forId);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, "label");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "label", labelQName, attributes);
        if (value != null) {
            if (mustOutputHTMLFragment) {
                XFormsUtils.streamHTMLFragment(contentHandler, value, null, xhtmlPrefix);
            } else {
                contentHandler.characters(value.toCharArray(), 0, value.length());
            }
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
