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

import org.apache.commons.lang3.StringUtils;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.control.*;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;


/**
 * Base class for all XHTML and XForms element handlers.
 */
public abstract class XFormsBaseHandlerXHTML extends XFormsBaseHandler {

    protected XFormsBaseHandlerXHTML(boolean repeating, boolean forwarding) {
       super(repeating, forwarding);
    }
    
    protected HandlerContext getHandlerContext() {
    	return this.handlerContext;
    }

    public static void outputDisabledAttribute(AttributesImpl newAttributes) {
        // @disabled="disabled"
        // HTML 4: @disabled supported on: input, button, select, optgroup, option, and textarea.
        newAttributes.addAttribute("", "disabled", "disabled", ContentHandlerHelper.CDATA, "disabled");
    }

    public void handleMIPClasses(StringBuilder sb, String controlPrefixedId, XFormsControl control) {

        // Output MIP classes only having a binding
        final boolean hasBinding = containingDocument.getStaticOps().hasBinding(controlPrefixedId);
        if (hasBinding) {
            if (control != null) {
                // The case of a concrete control

                // TODO: Move this to to control itself, like writeMIPsAsAttributes

                // Output standard MIP classes
                if (!control.isRelevant()) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append("xforms-disabled");
                }
                if (control.visited()) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append("xforms-visited");
                }
                if (control instanceof XFormsSingleNodeControl) {
                    // TODO: inherit from this method instead rather than using instanceof
                    final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                    if (!singleNodeControl.isValid()) {
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append("xforms-invalid");
                        // Combined class for IE6
                        if (control.visited()) {
                            sb.append(" xforms-invalid-visited");
                        }
                    }
                    if (singleNodeControl.isReadonly()) {
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append("xforms-readonly");
                    }
                    if (singleNodeControl.isRequired()) {
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append("xforms-required");
                        if (control instanceof XFormsValueControl) {
                            // NOTE: Test above excludes xforms:group
                            // Combined class for IE6
                            if (((XFormsValueControl) control).isEmpty())
                                sb.append(" xforms-required-empty");
                            else
                                sb.append(" xforms-required-filled");
                        }
                    }

                    // Output custom MIPs classes
                    final String customMIPs = singleNodeControl.jCustomMIPsClassesAsString();
                    if (! customMIPs.equals("")) {
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append(customMIPs);
                    }

                    // Output type class
                    final String typeName = singleNodeControl.getBuiltinTypeName();
                    if (typeName != null) {
                        // Control is bound to built-in schema type
                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append("xforms-type-");
                        sb.append(typeName);
                    } else {
                        // Output custom type class
                       final String customTypeName = singleNodeControl.getTypeLocalName();
                       if (customTypeName != null) {
                           // Control is bound to a custom schema type
                           if (sb.length() > 0)
                               sb.append(' ');

                           sb.append("xforms-type-custom-");
                           sb.append(customTypeName);
                       }
                    }
                }
            } else if (!handlerContext.isTemplate()) {
                // Case of a non-concrete control - simply mark the control as disabled
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-disabled");
            }
        }
    }

    protected StringBuilder getInitialClasses(String controlURI, String controlName, Attributes controlAttributes, XFormsControl control, boolean incrementalDefault) {
        
        final StringBuilder sb = new StringBuilder(50);
        // User-defined classes go first
        appendControlUserClasses(controlAttributes, control, sb);

        // Control classes based on the name for built-in controls
        if (XFormsControlFactory.isBuiltinControl(controlURI, controlName)) {
            if (sb.length() > 0)
                sb.append(' ');

            // We only call xforms-control the actual controls as per the spec
            // TODO: XForms 1.1 has core and container controls, but now we depend on xforms-control class in client
            if (! XFormsControlFactory.isContainerControl(controlURI, controlName))
                sb.append("xforms-control xforms-");
            else
                sb.append("xforms-");
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
        // Classes for appearances
        appendAppearances(elementAnalysis, sb);
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
        if (isStaticReadonly(control))
            sb.append(" xforms-static");

        return sb;
    }

    protected static void appendAppearances(ElementAnalysis elementAnalysis, StringBuilder sb) {
        for (final QName appearance : XFormsControl.jAppearances(elementAnalysis)) {
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("xforms-");
            sb.append(elementAnalysis.element().getName());
            sb.append("-appearance-");
            // Allow xxforms:* and *
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearance.getNamespace().getURI()))
                sb.append("xxforms-");
            else if (!"".equals(appearance.getNamespace().getURI()))
                throw new ValidationException("Invalid appearance namespace URI: " + appearance.getNamespace().getURI(), elementAnalysis.locationData());
            sb.append(appearance.getName());
        }
    }

    protected StringBuilder appendControlUserClasses(Attributes controlAttributes, XFormsControl control, StringBuilder sb) {
        // @class
        final String attributeValue = controlAttributes.getValue("class");
        final String value;
        if (attributeValue != null) {

            if (!XFormsUtils.maybeAVT(attributeValue)) {
                // Definitely not an AVT
                value = attributeValue;
            } else {
                // Possible AVT
                if (control != null) {
                    // Ask the control if possible
                    value = control.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME);
                } else {
                    // Otherwise we can't compute it
                    value = null;
                }
            }

            if (value != null) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append(value);
            }
        }

        return sb;
    }

    protected ElementAnalysis getLHHAAnalysis(String controlPrefixedId, XFormsBaseHandler.LHHAC lhhaType) {
        // Statically obtain attributes information
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        if (lhhaType == LHHAC.LABEL) {
            return globalOps.getLabel(controlPrefixedId);
        } else if (lhhaType == LHHAC.HELP) {
            return globalOps.getHelp(controlPrefixedId);
        } else if (lhhaType == LHHAC.HINT) {
            return globalOps.getHint(controlPrefixedId);
        } else if (lhhaType == LHHAC.ALERT) {
            return globalOps.getAlert(controlPrefixedId);
        } else {
            throw new IllegalStateException("Illegal type requested");
        }
    }

    protected void handleLabelHintHelpAlert(ElementAnalysis lhhaAnalysis, String targetControlEffectiveId, String forEffectiveId, XFormsBaseHandler.LHHAC lhhaType, XFormsControl control, boolean isTemplate, boolean addIds) throws SAXException {

        // NOTE: We used to not handle alerts and help in read-only mode. We now prefer to controls this with CSS.
        final boolean isLabel = lhhaType == LHHAC.LABEL;
        final boolean isHelp = lhhaType == LHHAC.HELP;
        final boolean isHint = lhhaType == LHHAC.HINT;
        final boolean isAlert = lhhaType == LHHAC.ALERT;

        final String labelHintHelpAlertValue;
        final boolean mustOutputHTMLFragment;
        if (control != null) {
            // Get actual value from control
            if (isLabel) {
                labelHintHelpAlertValue = control.getLabel();
                mustOutputHTMLFragment = control.isHTMLLabel();
            } else if (isHelp) {
                // NOTE: Special case here where we get the escaped help to facilitate work below. Help is a special
                // case because it is stored as escaped HTML within a <label> element.
                labelHintHelpAlertValue = control.getEscapedHelp();
                mustOutputHTMLFragment = false;
            } else if (isHint) {
                labelHintHelpAlertValue = control.getHint();
                mustOutputHTMLFragment = control.isHTMLHint();
            } else if (isAlert) {
                labelHintHelpAlertValue = control.getAlert();
                mustOutputHTMLFragment = control.isHTMLAlert();
            } else {
                throw new IllegalStateException("Illegal type requested");
            }
        } else {
            // Placeholder
            labelHintHelpAlertValue = null;
            mustOutputHTMLFragment = false;
        }

        final String elementName;
        {
            // Statically obtain attributes information
            if (isLabel) {
                elementName = handlerContext.getLabelElementName();
            } else if (isHelp) {
                elementName = handlerContext.getHelpElementName();
            } else if (isHint) {
                elementName = handlerContext.getHintElementName();
            } else if (isAlert) {
                elementName = handlerContext.getAlertElementName();
            } else {
                throw new IllegalStateException("Illegal type requested");
            }
        }

        final Attributes labelHintHelpAlertAttributes = XMLUtils.getSAXAttributes(lhhaAnalysis.element());

        if (labelHintHelpAlertAttributes != null || isAlert) {
            // If no attributes were found, there is no such label / help / hint / alert

            final StringBuilder classes = new StringBuilder(30);

            // Put user classes first if any
            if (labelHintHelpAlertAttributes != null) {
                final String userClass = labelHintHelpAlertAttributes.getValue("class");
                if (userClass != null)
                    classes.append(userClass);
            }

            // Handle alert state
            // TODO: Once we have the new HTML layout, this won't be needed anymore
            if (isAlert) {
                if (classes.length() > 0)
                    classes.append(' ');
                if (control instanceof XFormsSingleNodeControl && !((XFormsSingleNodeControl) control).isValid()) {
                    classes.append("xforms-alert-active");
                    // Combined class for IE6
                    if (control.visited()) {
                        classes.append(" xforms-alert-active-visited");
                    }
                } else {
                    classes.append("xforms-alert-inactive");
                }
            }

            // Handle visibility
            // TODO: It would be great to actually know about the relevance of help, hint, and label. Right now, we just look at whether the value is empty
            if (control != null) {
                if (isAlert || isLabel) {
                    // Allow empty labels and alerts
                    if (!control.isRelevant()) {
                        if (classes.length() > 0)
                            classes.append(' ');
                        classes.append("xforms-disabled");
                    }
                } else {
                    // For help and hint, consider "non-relevant" if empty
                    final boolean isHintHelpRelevant = control.isRelevant() && StringUtils.isNotEmpty(labelHintHelpAlertValue);
                    if (!isHintHelpRelevant) {
                        if (classes.length() > 0)
                            classes.append(' ');
                        classes.append("xforms-disabled");
                    }
                }
            } else if (!isTemplate || isHelp) {
                // Null control outside of template OR help within template
                if (classes.length() > 0)
                    classes.append(' ');
                classes.append("xforms-disabled");
            }

            if (classes.length() > 0)
                classes.append(' ');
            classes.append("xforms-");
            classes.append(lhhaType.name().toLowerCase());

            final String labelClasses = classes.toString();

            final boolean isNoscript = handlerContext.isNoScript();
            if (isHelp) {
                // HACK: For help, output XHTML image natively in order to help with the IE bug whereby IE reloads
                // background images way too often.

                final ContentHandler contentHandler = handlerContext.getController().getOutput();
                final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

                if (isNoscript && control != null) {
                    // Start <a href="#my-control-id-help">

                    final AttributesImpl aAttributes = new AttributesImpl();
                    aAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "#" + getLHHACId(containingDocument, targetControlEffectiveId, LHHAC_CODES.get(LHHAC.HELP)));
                    aAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-help-anchor");

                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, aAttributes);
                }

                classes.append("-image"); // xforms-help-image class
                final String helpClasses = classes.toString();

                final AttributesImpl helpAttributes = new AttributesImpl();
                if (addIds)
                    helpAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, getLHHACId(containingDocument, targetControlEffectiveId, "i"));
                helpAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, helpClasses);
                // Q: Do we need a title and/or alt for screen readers?
                helpAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, "");
                helpAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, "");

                final String helpQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", helpQName, helpAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", helpQName);

                if (isNoscript && control != null) {
                    // End </a>
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);
                }
            }

            // Output label, except help in noscript mode since in that case help is displayed separately
            if (!(isNoscript && isHelp)) {

                // We handle null attributes as well because we want a placeholder for "alert" even if there is no xforms:alert
                final Attributes newAttributes = (labelHintHelpAlertAttributes != null) ? labelHintHelpAlertAttributes : new AttributesImpl();
                outputLabelFor(handlerContext, getAttributes(newAttributes, labelClasses, null), targetControlEffectiveId,
                        forEffectiveId, lhhaType, elementName, labelHintHelpAlertValue, mustOutputHTMLFragment, addIds);
            }
        }
    }

    protected static void outputLabelFor(HandlerContext handlerContext, Attributes attributes, String targetControlEffectiveId,
                                         String forEffectiveId, LHHAC lhha, String elementName, String labelValue,
                                         boolean mustOutputHTMLFragment, boolean addIds) throws SAXException {
        outputLabelForStart(handlerContext, attributes, targetControlEffectiveId, forEffectiveId, lhha, elementName, addIds);
        outputLabelForEnd(handlerContext, elementName, labelValue, mustOutputHTMLFragment);
    }

    protected static void outputLabelForStart(HandlerContext handlerContext, Attributes attributes, String targetControlEffectiveId,
                                              String forEffectiveId, LHHAC lhha, String elementName, boolean addIds) throws SAXException {

        assert lhha != null;
        assert ! addIds || targetControlEffectiveId != null;

        // Replace id attribute to be foo-label, foo-hint, foo-help, or foo-alert
        final AttributesImpl newAttribute;
        if (addIds && targetControlEffectiveId != null) {
            // Add or replace existing id attribute
            // NOTE: addIds == true for external LHHA
            newAttribute = XMLUtils.addOrReplaceAttribute(attributes, "", "", "id", getLHHACId(handlerContext.getContainingDocument(), targetControlEffectiveId, LHHAC_CODES.get(lhha)));
        } else {
            // Remove existing id attribute if any
            newAttribute = XMLUtils.removeAttribute(attributes, "", "id");
        }

        // Add @for attribute if specified and element is a label
        if (forEffectiveId != null && elementName.equals("label"))
            newAttribute.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, forEffectiveId);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, elementName);
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, labelQName, newAttribute);
    }

    protected static void outputLabelForEnd(HandlerContext handlerContext, String elementName, String labelValue, boolean mustOutputHTMLFragment) throws SAXException {

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, elementName);
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        // Only output content when there value is non-empty
        if (StringUtils.isNotEmpty(labelValue)) {
            if (mustOutputHTMLFragment) {
                XFormsUtils.streamHTMLFragment(xmlReceiver, labelValue, null, xhtmlPrefix);
            } else {
                xmlReceiver.characters(labelValue.toCharArray(), 0, labelValue.length());
            }
        }
        xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, labelQName);
    }

    public static void outputLabelText(XMLReceiver xmlReceiver, XFormsControl xformsControl, String value, String xhtmlPrefix, boolean mustOutputHTMLFragment) throws SAXException {
        // Only output content when there value is non-empty
        if (value != null && !value.equals("")) {
            if (mustOutputHTMLFragment)
                XFormsUtils.streamHTMLFragment(xmlReceiver, value, xformsControl != null ? xformsControl.getLocationData() : null, xhtmlPrefix);
            else
                xmlReceiver.characters(value.toCharArray(), 0, value.length());
        }
    }
}
