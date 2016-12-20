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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.AppearanceTrait;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels;
import org.orbeon.oxf.xforms.control.*;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
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

    private void addConstraintClasses(StringBuilder sb, scala.Option<ValidationLevels.ValidationLevel> constraintLevel) {
        if (constraintLevel.isDefined()) {
            final String levelName = constraintLevel.get().name();
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("xforms-");
            sb.append(levelName.equals("error") ? "invalid" : levelName);// level is called "error" but we use "invalid" on the client
        }
    }

    final public void handleMIPClasses(StringBuilder sb, String controlPrefixedId, XFormsControl control) {

        // Output MIP classes

        // TODO: Move this to to control itself, like writeMIPsAsAttributes

        // xforms-disabled class
        // NOTE: We used to not output this class if the control existed and didn't have a binding. That looked either
        // like an inconsistent optimization (not done for controls with bindings), or like an oversight (likely).
        final boolean isRelevant = control != null && control.isRelevant();
        if (! isRelevant && ! handlerContext.isTemplate()) { // don't output class within a template
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("xforms-disabled");
        }

        // MIP classes for a concrete control
        if (isRelevant && containingDocument.getStaticOps().hasBinding(controlPrefixedId)) {

            // Output standard MIP classes
            if (control.visited()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-visited");
            }
            if (control instanceof XFormsSingleNodeControl) {
                // TODO: inherit from this method instead rather than using instanceof
                final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                addConstraintClasses(sb, singleNodeControl.alertLevel());

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
                        // NOTE: Test above excludes xf:group
                        if (((XFormsValueControl) control).isEmptyValue())
                            sb.append(" xforms-empty");
                        else
                            sb.append(" xforms-filled");
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
                final String typeCSSClass = singleNodeControl.getBuiltinOrCustomTypeCSSClassOrNull();
                if (typeCSSClass != null) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(typeCSSClass);
                }
            }
        }
    }

    final protected StringBuilder getInitialClasses(String controlURI, String controlName, Attributes controlAttributes, XFormsControl control, boolean incrementalDefault) {

        final StringBuilder sb = new StringBuilder(50);

        // User-defined classes go first
        appendControlUserClasses(controlAttributes, control, sb);

        // Component control doesn't get xforms-control, xforms-[control name], incremental, mediatype, xforms-static
        if (! (elementAnalysis instanceof ComponentControl)) {

            {
                if (sb.length() > 0)
                    sb.append(' ');

                // We only call xforms-control the actual controls as per the spec
                // NOTE: XForms 1.1 has core and container controls but our client depends on having `xforms-control`.
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
        }

        // Classes for appearances
        if (elementAnalysis instanceof AppearanceTrait)
            ((AppearanceTrait) elementAnalysis).encodeAndAppendAppearances(sb);

        return sb;
    }

    final protected StringBuilder appendControlUserClasses(Attributes controlAttributes, XFormsControl control, StringBuilder sb) {
        // @class
        final String attributeValue = controlAttributes.getValue("class");
        final String value;
        if (attributeValue != null) {

            if (! XFormsUtils.maybeAVT(attributeValue)) {
                // Definitely not an AVT
                value = attributeValue;
            } else {
                // Possible AVT
                if (control != null) {
                    // Ask the control if possible
                    value = control.jExtensionAttributeValue(XFormsConstants.CLASS_QNAME);
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

    final protected void handleLabelHintHelpAlert(
        LHHAAnalysis lhhaAnalysis,
        String targetControlEffectiveId,
        String forEffectiveId,
        XFormsBaseHandler.LHHAC lhhaType,
        String requestedElementName,
        XFormsControl control,
        boolean isTemplate,
        boolean isExternal
    ) throws SAXException {

        final AttributesImpl staticLHHAAttributes = Dom4jUtils.getSAXAttributes(lhhaAnalysis.element());

        final boolean isLabel = lhhaType == LHHAC.LABEL;
        final boolean isHelp  = lhhaType == LHHAC.HELP;
        final boolean isHint  = lhhaType == LHHAC.HINT;
        final boolean isAlert = lhhaType == LHHAC.ALERT;

        if (staticLHHAAttributes != null || isAlert) {
            // If no attributes were found, there is no such label / help / hint / alert

            if (handlerContext.isNoScript() && isHelp) {
                if (control != null) {

                    final ContentHandler contentHandler = handlerContext.getController().getOutput();
                    final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

                    // <a href="#my-control-id-help">

                    final AttributesImpl aAttributes = new AttributesImpl();
                    aAttributes.addAttribute("", "href", "href", XMLReceiverHelper.CDATA, "#" + getLHHACId(containingDocument, targetControlEffectiveId, LHHAC_CODES.get(LHHAC.HELP)));
                    aAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-help-anchor");

                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, aAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);
                }
            } else {

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
                    if (requestedElementName != null) {
                        elementName = requestedElementName;
                    } else if (isLabel) {
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

                final StringBuilder classes = new StringBuilder(30);

                // Put user classes first if any
                if (staticLHHAAttributes != null) {
                    final String userClass = staticLHHAAttributes.getValue("class");
                    if (userClass != null)
                        classes.append(userClass);
                }

                // Mark alert as active if needed
                if (isAlert) {
                    if (control instanceof XFormsSingleNodeControl) {
                        final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                        final scala.Option<ValidationLevels.ValidationLevel> constraintLevel = singleNodeControl.alertLevel();

                        if (constraintLevel.isDefined()) {
                            if (classes.length() > 0)
                                classes.append(' ');
                            classes.append("xforms-active");
                        }

                        // Constraint classes are placed on the control if the alert is not external
                        if (isExternal)
                            addConstraintClasses(classes, constraintLevel);
                    }
                }

                // Handle visibility
                // TODO: It would be great to actually know about the relevance of help, hint, and label. Right now, we just look at whether the value is empty
                if (control != null) {
                    if (!control.isRelevant()) {
                        if (classes.length() > 0)
                            classes.append(' ');
                        classes.append("xforms-disabled");
                    }
                } else if (!isTemplate || isHelp) {
                    // Null control outside of template OR help within template
                    if (classes.length() > 0)
                        classes.append(' ');
                    classes.append("xforms-disabled");
                }

                // LHHA name
                if (classes.length() > 0)
                    classes.append(' ');
                classes.append("xforms-");
                classes.append(lhhaType.name().toLowerCase());

                // We handle null attributes as well because we want a placeholder for "alert" even if there is no xf:alert
                final Attributes newAttributes = (staticLHHAAttributes != null) ? staticLHHAAttributes : new AttributesImpl();

                lhhaAnalysis.encodeAndAppendAppearances(classes);

                outputLabelFor(
                    handlerContext,
                    getIdClassXHTMLAttributes(newAttributes, classes.toString(), null),
                    targetControlEffectiveId,
                    forEffectiveId,
                    lhhaType,
                    elementName,
                    labelHintHelpAlertValue,
                    mustOutputHTMLFragment,
                    isExternal
                );
            }
        }
    }

    final protected static void outputLabelFor(
        HandlerContext handlerContext,
        Attributes attributes,
        String targetControlEffectiveId,
        String forEffectiveId,
        XFormsBaseHandler.LHHAC lhha,
        String elementName,
        String labelValue,
        boolean mustOutputHTMLFragment,
        boolean addIds
    ) throws SAXException {
        outputLabelForStart(handlerContext, attributes, targetControlEffectiveId, forEffectiveId, lhha, elementName, addIds);
        outputLabelText(handlerContext.getController().getOutput(), null, labelValue, handlerContext.findXHTMLPrefix(), mustOutputHTMLFragment);
        outputLabelForEnd(handlerContext, elementName);
    }

    final protected static void outputLabelForStart(
        HandlerContext handlerContext,
        Attributes attributes,
        String targetControlEffectiveId,
        String forEffectiveId,
        XFormsBaseHandler.LHHAC lhha,
        String elementName,
        boolean addIds
    ) throws SAXException {

        assert lhha != null;
        assert ! addIds || targetControlEffectiveId != null;

        // Replace id attribute to be foo-label, foo-hint, foo-help, or foo-alert
        final AttributesImpl newAttribute;
        if (addIds && targetControlEffectiveId != null) {
            // Add or replace existing id attribute
            // NOTE: addIds == true for external LHHA
            newAttribute = SAXUtils.addOrReplaceAttribute(attributes, "", "", "id", getLHHACId(handlerContext.getContainingDocument(), targetControlEffectiveId, LHHAC_CODES.get(lhha)));
        } else {
            // Remove existing id attribute if any
            newAttribute = SAXUtils.removeAttribute(attributes, "", "id");
        }

        // Add @for attribute if specified and element is a label
        if (forEffectiveId != null && elementName.equals("label"))
            newAttribute.addAttribute("", "for", "for", XMLReceiverHelper.CDATA, forEffectiveId);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, elementName);
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, labelQName, newAttribute);
    }

    final protected static void outputLabelForEnd(HandlerContext handlerContext, String elementName) throws SAXException {

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, elementName);
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, labelQName);
    }

    public static void outputLabelText(XMLReceiver xmlReceiver, XFormsControl xformsControl, String value, String xhtmlPrefix, boolean html) throws SAXException {
        if (StringUtils.isNotEmpty(value)) {
            if (html)
                XFormsUtils.streamHTMLFragment(xmlReceiver, value, xformsControl != null ? xformsControl.getLocationData() : null, xhtmlPrefix);
            else
                xmlReceiver.characters(value.toCharArray(), 0, value.length());
        }
    }

    public static void outputDisabledAttribute(AttributesImpl newAttributes) {
        // @disabled="disabled"
        // HTML 4: @disabled supported on: input, button, select, optgroup, option, and textarea.
        newAttributes.addAttribute("", "disabled", "disabled", XMLReceiverHelper.CDATA, "disabled");
    }
}
