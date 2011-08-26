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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl;
import org.orbeon.oxf.xforms.itemset.Item;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xml.*;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:input.
 *
 * TODO: Subclasses per appearance.
 */
public class XFormsInputHandler extends XFormsControlLifecyleHandler {

    private String appearance;
    private boolean isDateTime;
    private boolean isDateMinimal;
    private boolean isBoolean;

    public XFormsInputHandler() {
        super(false);
    }

    @Override
    public void init(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        super.init(uri, localname, qName, attributes);

        // Handle appearance
        if (!handlerContext.isTemplate()) {
            final XFormsInputControl control = (XFormsInputControl) getControl();
            if (control != null) {

                appearance = control.getAppearance();

                final String controlTypeName = control.getBuiltinTypeName();
                isDateTime = "dateTime".equals(controlTypeName);
                isDateMinimal = "date".equals(controlTypeName) && "minimal".equals(appearance) ;
                isBoolean = "boolean".equals(controlTypeName);
            } else {
                appearance = null;
                isDateTime = false;
                isDateMinimal = false;
                isBoolean = false;
            }
        } else {
            appearance = null;
            isDateTime = false;
            isDateMinimal = false;
            isBoolean = false;
        }
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        final XFormsInputControl inputControl = (XFormsInputControl) control;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = inputControl != null;

        if (isBoolean) {
            // Produce a boolean output

            if (!isStaticReadonly(inputControl)) {
                // Output control
                final boolean isMultiple = true;
                final Itemset itemset = new Itemset();
                // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
                // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
                // the encrypted value of "true". So we do not encrypt values.
                // NOTE: Put null label so that it is not output at all
                itemset.addChildItem(new Item(isMultiple, false, null, null, "true"));

                // NOTE: In the future, we may want to use other appearances provided by xforms:select
    //            items.add(new XFormsSelect1Control.Item(false, Collections.EMPTY_LIST, "False", "false", 1));

                // TODO: This delegation to xforms:select1 handler is error-prone, is there a better way?
                final XFormsSelect1Handler select1Handler = new XFormsSelect1Handler() {
                    @Override
                    protected String getPrefixedId() {
                        return XFormsInputHandler.this.getPrefixedId();
                    }
                    @Override
                    public String getEffectiveId() {
                        return XFormsInputHandler.this.getEffectiveId();
                    }
                    @Override
                    public XFormsControl getControl() {
                        return XFormsInputHandler.this.getControl();
                    }
                };
                select1Handler.setContext(getContext());
                select1Handler.init(uri, localname, qName, attributes);
                select1Handler.outputContent(uri, localname, attributes, effectiveId, inputControl, itemset, isMultiple, true, true);
            } else {
                // Output static read-only value

                if (isConcreteControl) {
                    final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                    final String enclosingElementLocalname = "span";
                    final String enclosingElementQName = XMLUtils.buildQName(xhtmlPrefix, enclosingElementLocalname);
                    final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, inputControl, false);

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName, containerAttributes);

                    final String outputValue = inputControl.getExternalValue();
                    if (outputValue != null)
                        contentHandler.characters(outputValue.toCharArray(), 0, outputValue.length());

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);
                }
            }

        } else {

            // Create xhtml:span
//            final boolean isReadOnly = isConcreteControl && inputControl.isReadonly();
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String enclosingElementLocalname = "span";
            final String enclosingElementQName = XMLUtils.buildQName(xhtmlPrefix, enclosingElementLocalname);
            final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

            final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, inputControl, false);

            if (!handlerContext.isSpanHTMLLayout())
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName, containerAttributes);

            {
                // Create xhtml:input
                {
                    if (!isStaticReadonly(inputControl)) {
                        // Regular read-write mode

                        // Main input field
                        {
                            final String inputIdName = getFirstInputEffectiveId(effectiveId);

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, inputIdName);
                            if (!isDateMinimal)
                                reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "text");
                            // Use effective id for name of first field
                            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, inputIdName);
                            final StringBuilder inputClasses = new StringBuilder("xforms-input-input");
                            if (isConcreteControl) {
                                // Output value only for concrete control
                                final String formattedValue = inputControl.getFirstValueUseFormat();
                                if (!isDateMinimal) {
                                    // Regular case, value goes to input control
                                    reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, formattedValue != null ? formattedValue : "");
                                } else {
                                    // "Minimal date", value goes to @alt attribute on image
                                    reusableAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, formattedValue != null ? formattedValue : "");
                                }

                                final String firstType = inputControl.getFirstValueType();
                                if (firstType != null) {
                                    inputClasses.append(" xforms-type-");
                                    inputClasses.append(firstType);
                                }
                                if (appearance != null) {
                                    inputClasses.append(" xforms-input-appearance-");
                                    inputClasses.append(appearance);
                                }

                                // Output xxforms:* extension attributes
                                inputControl.addExtensionAttributes(reusableAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);

                            } else {
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "");
                            }

                            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, inputClasses.toString());

                            // Handle accessibility attributes
                            handleAccessibilityAttributes(attributes, reusableAttributes);
                            if (isDateMinimal) {
                                final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                                reusableAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, XFormsConstants.CALENDAR_IMAGE_URI);
                                reusableAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, "");

                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, reusableAttributes);
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                            } else {
                                if (isHTMLDisabled(inputControl))
                                    outputDisabledAttribute(reusableAttributes);
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                            }
                        }

                        // Add second field for dateTime's time part
                        // NOTE: In the future, we probably want to do this as an XBL component
                        if (isDateTime) {

                            final String inputIdName = getSecondInputEffectiveId(effectiveId);

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, inputIdName);
                            reusableAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, XFormsConstants.CALENDAR_IMAGE_URI);
                            reusableAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, "");
                            reusableAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, "");
                            // TODO: Is this an appropriate name? Noscript must be able to find this
                            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, inputIdName);

                            final StringBuilder inputClasses = new StringBuilder("xforms-input-input");
                            if (isConcreteControl) {
                                // Output value only for concrete control
                                final String inputValue = inputControl.getSecondValueUseFormat();
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, inputValue);

                                final String secondType = inputControl.getSecondValueType();
                                if (secondType != null) {
                                    inputClasses.append(" xforms-type-");
                                    inputClasses.append(secondType);
                                }
                            } else {
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "");
                            }

                            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, inputClasses.toString());

                            if (isHTMLDisabled(inputControl))
                                outputDisabledAttribute(reusableAttributes);

                            // TODO: set @size and @maxlength

                            // Handle accessibility attributes
                            handleAccessibilityAttributes(attributes, reusableAttributes);

                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                        }

                    } else {
                        // Read-only mode
                        if (isConcreteControl) {
                            final String formattedValue = inputControl.getReadonlyValueUseFormat();
                            final String outputValue = (formattedValue != null) ? formattedValue : inputControl.getExternalValue();

                            if (handlerContext.isSpanHTMLLayout())
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName, containerAttributes);

                            if (outputValue != null)
                                contentHandler.characters(outputValue.toCharArray(), 0, outputValue.length());

                            if (handlerContext.isSpanHTMLLayout())
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);
                        }
                    }
                }
            }
            if (!handlerContext.isSpanHTMLLayout())
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);
        }
    }

    private String getFirstInputEffectiveId(String effectiveId) {
        if (!isBoolean) {
            // Do as if this was in a component, noscript has to handle that
            return XFormsUtils.namespaceId(containingDocument, XFormsUtils.appendToEffectiveId(effectiveId, "$xforms-input-1"));
        } else {
            return null;
        }
    }

    private String getSecondInputEffectiveId(String effectiveId) {
        if (isDateTime) {
            // Do as if this was in a component, noscript has to handle that
            return XFormsUtils.namespaceId(containingDocument, XFormsUtils.appendToEffectiveId(effectiveId, "$xforms-input-2"));
        } else {
            return null;
        }
    }

    @Override
    public String getForEffectiveId() {
        if (isBoolean) {
            return XFormsSelect1Handler.getItemId(getEffectiveId(), "0");
        } else {
            return getFirstInputEffectiveId(getEffectiveId());
        }
    }
}