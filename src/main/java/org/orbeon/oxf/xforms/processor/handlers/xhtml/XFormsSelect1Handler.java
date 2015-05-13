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

import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.SelectAppearanceTrait;
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlTrait;
import org.orbeon.oxf.xforms.control.LHHAValue;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control$;
import org.orbeon.oxf.xforms.itemset.Item;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.itemset.ItemsetListener;
import org.orbeon.oxf.xforms.itemset.XFormsItemUtils;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import scala.Option;

import java.util.Iterator;
import java.util.Map;

/**
 * Handle xf:select and xf:select1.
 *
 * TODO: Subclasses per appearance.
 */
public class XFormsSelect1Handler extends XFormsControlLifecyleHandler {

    public XFormsSelect1Handler() {
        super(false);
    }

    @Override
    protected boolean isDefaultIncremental() {
        // Incremental mode is the default
        return true;
    }

    protected SelectAppearanceTrait getAppearanceTrait() {
        if (elementAnalysis instanceof SelectAppearanceTrait) {
            return (SelectAppearanceTrait) elementAnalysis;
        } else {
            return null;
        }
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String effectiveId, XFormsControl control) throws SAXException {
        // Get items, dynamic or static, if possible
        final XFormsSelect1Control xformsSelect1Control = (XFormsSelect1Control) control;

        // Get items if:
        // 1. The itemset is static
        // 2. The control exists and is relevant
        final Itemset itemset = XFormsSelect1Control.getInitialItemset(containingDocument, xformsSelect1Control, getPrefixedId());

        final SelectAppearanceTrait appearanceTrait = getAppearanceTrait();
        outputContent(uri, localname, attributes, effectiveId, xformsSelect1Control, itemset, appearanceTrait != null && appearanceTrait.isMultiple(), appearanceTrait != null && appearanceTrait.isFull(), false);
    }

    public void outputContent(String uri, String localname, Attributes attributes, String effectiveId,
                              final XFormsValueControl control, Itemset itemset,
                              final boolean isMultiple, final boolean isFull, boolean isBooleanInput) throws SAXException {

        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        final XFormsControl xformsControl = (XFormsControl) control; // cast because Java is not aware that XFormsValueControl extends XFormsControl
        final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control; // same as above

        final AttributesImpl containerAttributes = getEmptyNestedControlAttributesMaybeWithId(uri, localname, attributes, effectiveId, xformsControl, !isFull);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final SelectAppearanceTrait appearanceTrait = getAppearanceTrait();

        final boolean isStaticReadonly = isStaticReadonly(xformsControl);

        final boolean allowFullStaticReadonly =
              isMultiple && containingDocument.isReadonlyAppearanceStaticSelectFull() ||
            ! isMultiple && containingDocument.isReadonlyAppearanceStaticSelect1Full();

        final boolean mustOutputFull = isBooleanInput || (isFull && (allowFullStaticReadonly || ! isStaticReadonly));
        final boolean encode =
            (elementAnalysis instanceof SelectionControlTrait)
            ? XFormsSelect1Control$.MODULE$.mustEncodeValues(containingDocument, (SelectionControlTrait) elementAnalysis)
            : false; // case of boolean input

        if (mustOutputFull) {
            // Full appearance, also in static readonly mode
            outputFull(uri, localname, attributes, effectiveId, control, itemset, isMultiple, isBooleanInput, isStaticReadonly, encode);
        } else  if (! isStaticReadonly) {
            // Create xhtml:select
            final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");
            containerAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, effectiveId);// necessary for noscript mode

            if (appearanceTrait != null && appearanceTrait.isCompact())
                containerAttributes.addAttribute("", "multiple", "multiple", XMLReceiverHelper.CDATA, "multiple");

            // Handle accessibility attributes
            handleAccessibilityAttributes(attributes, containerAttributes, xformsControl);

            if (isHTMLDisabled(xformsControl))
                outputDisabledAttribute(containerAttributes);

            if (singleNodeControl != null)
                handleAriaAttributes(singleNodeControl.isRequired(), singleNodeControl.isValid(), containerAttributes);

            xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, containerAttributes);
            {
                final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                final String optGroupQName = XMLUtils.buildQName(xhtmlPrefix, "optgroup");

                if (itemset != null) {

                    itemset.visit(xmlReceiver, new ItemsetListener<ContentHandler>() {

                        private boolean inOptgroup = false; // nesting opgroups is not allowed, avoid it

                        public void startLevel(ContentHandler contentHandler, Item item) {}

                        public void endLevel(ContentHandler contentHandler) throws SAXException {
                            if (inOptgroup) {
                                // End xhtml:optgroup
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName);
                                inOptgroup = false;
                            }
                        }

                        public void startItem(ContentHandler contentHandler, Item item, boolean first) throws SAXException {

                            assert !item.label().isHTML();
                            final String label = item.label().label();
                            final String value = item.value();

                            if (value == null) {
                                assert item.hasChildren();
                                final String itemClasses = getItemClasses(item, null);
                                final AttributesImpl optGroupAttributes = getIdClassXHTMLAttributes(SAXUtils.EMPTY_ATTRIBUTES, itemClasses, null);
                                if (label != null)
                                    optGroupAttributes.addAttribute("", "label", "label", XMLReceiverHelper.CDATA, label);

                                // If another optgroup is open, close it - nested optgroups are not allowed. Of course this results in an
                                // incorrect structure for tree-like itemsets, there is no way around that. If the user however does
                                // the indentation himself, it will still look right.
                                if (inOptgroup)
                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName);

                                // Start xhtml:optgroup
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName, optGroupAttributes);
                                inOptgroup = true;
                            } else {
                                handleItemCompact(contentHandler, optionQName, control, isMultiple, item, encode);
                            }
                        }

                        public void endItem(ContentHandler contentHandler, Item item) {}
                    });
                }
            }
            xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName);
        } else {
            // Output static read-only value
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            containerAttributes.addAttribute("", "class", "class", "CDATA", "xforms-field");
            xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, containerAttributes);
            if (!handlerContext.isTemplate()) {
                final String value = (control == null || control.getValue() == null) ? "" : control.getValue();
                if (itemset != null) {
                    boolean selectedFound = false;
                    final XMLReceiverHelper ch = new XMLReceiverHelper(xmlReceiver);
                    for (final Item currentItem : itemset.jSelectedItems(value)) {
                        if (selectedFound)
                            ch.text(" - ");

                        currentItem.label().streamAsHTML(ch, xformsControl.getLocationData());

                        selectedFound = true;
                    }
                }
            }
            xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }
    }

    private void outputFull(String uri, String localname, Attributes attributes, String effectiveId,
                            XFormsValueControl control, Itemset itemset, boolean isMultiple, boolean isBooleanInput, boolean isStaticReadonly, boolean encode) throws SAXException {

        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();
        final SelectAppearanceTrait appearanceTrait = getAppearanceTrait();
        final XFormsControl xformsControl = (XFormsControl) control; // cast because Java is not aware that XFormsValueControl extends XFormsControl

        final AttributesImpl containerAttributes = getEmptyNestedControlAttributesMaybeWithId(uri, localname, attributes, effectiveId, xformsControl, !(appearanceTrait != null && appearanceTrait.isFull()));
        containerAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-items"); // to help with styling
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

        final String fullItemType = isMultiple ? "checkbox" : "radio";

        // In noscript mode, use <fieldset>

        // TODO: This really hasn't much to do with noscript; should we always use fieldset, or make this an
        // option? Benefit of limiting to noscript is that then no JS change is needed
        final String containingElementName = handlerContext.isNoScript() ? "fieldset" : "span";
        final String containingElementQName = XMLUtils.buildQName(xhtmlPrefix, containingElementName);

        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
        {

            // Output container <span>/<fieldset> for select/select1
            final boolean outputContainerElement = !isBooleanInput;
            if (outputContainerElement)
                xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, containingElementName, containingElementQName, containerAttributes);
            {
                if (handlerContext.isNoScript()) {
                    // Output <legend>
                    final String legendName = "legend";
                    final String legendQName = XMLUtils.buildQName(xhtmlPrefix, legendName);
                    reusableAttributes.clear();
                    // TODO: handle other attributes? xforms-disabled?
                    reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-label");
                    xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, legendName, legendQName, reusableAttributes);
                    if (control != null) {
                        final boolean mustOutputHTMLFragment = xformsControl.isHTMLLabel();
                        outputLabelText(xmlReceiver, xformsControl, xformsControl.getLabel(), xhtmlPrefix, mustOutputHTMLFragment);
                    }
                    xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, legendName, legendQName);
                }

                if (itemset != null) {
                    int itemIndex = 0;
                    for (Iterator<Item> i = itemset.jAllItemsIterator(); i.hasNext(); itemIndex++) {
                        handleItemFull(
                            this,
                            xmlReceiver,
                            reusableAttributes,
                            attributes,
                            xhtmlPrefix,
                            spanQName,
                            containingDocument,
                            control,
                            effectiveId,
                            getItemId(effectiveId, Integer.toString(itemIndex)),
                            isMultiple,
                            fullItemType,
                            i.next(),
                            itemIndex == 0,
                            isBooleanInput,
                            isStaticReadonly,
                            encode
                        );
                    }
                }
            }
            if (outputContainerElement)
                xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, containingElementName, containingElementQName);
        }

        // NOTE: Templates for full items are output globally in XHTMLBodyHandler
    }

    public static void outputItemFullTemplate(XFormsBaseHandlerXHTML baseHandler,
                                              ContentHandler contentHandler, String xhtmlPrefix, String spanQName,
                                              XFormsContainingDocument containingDocument,
                                              AttributesImpl reusableAttributes, Attributes attributes, String templateId,
                                              String itemName, boolean isMultiple, String fullItemType) throws SAXException {
        reusableAttributes.clear();
//        reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(handlerContext.containingDocument(), templateId));
        // Client queries template by id without namespace, so output that. Not ideal as all ids should be namespaced.
        reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, templateId);
        reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-template");

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
        handleItemFull(
            baseHandler,
            contentHandler,
            reusableAttributes,
            attributes,
            xhtmlPrefix,
            spanQName,
            containingDocument,
            null,
            itemName,
            "$xforms-item-id-select" + (isMultiple? "" : "1") + "$", // create separate id for select/select1
            isMultiple,
            fullItemType,
            Item.apply(
                0,
                isMultiple,
                null, // make sure the value "$xforms-template-value$" is not encrypted
                new LHHAValue("$xforms-template-label$", false),
                Option.apply(new LHHAValue("$xforms-template-help$", false)),
                Option.apply(new LHHAValue("$xforms-template-hint$", false)),
                "$xforms-template-value$"
            ),
            true,
            false,
            false,
            false
        );
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private void outputJSONTreeInfo(XFormsValueControl control, Itemset itemset, boolean encode, ContentHandler contentHandler) throws SAXException {
        if (control != null && ! handlerContext.isTemplate()) {
            // Produce a JSON fragment with hierarchical information
            final String result = itemset.getJSONTreeInfo(control.getValue(), encode, handlerContext.getLocationData());
            contentHandler.characters(result.toCharArray(), 0, result.length());
        } else {
            // Don't produce any content when generating a template
        }
    }

    public static void handleItemFull(XFormsBaseHandlerXHTML baseHandler, ContentHandler contentHandler,
                                      AttributesImpl reusableAttributes, Attributes attributes, String xhtmlPrefix, String spanQName,
                                      XFormsContainingDocument containingDocument, XFormsValueControl control,
                                      String itemName, String itemEffectiveId, boolean isMultiple, String type,
                                      Item item, boolean isFirst, boolean isBooleanInput, boolean isStaticReadonly, boolean encode) throws SAXException {

        final HandlerContext handlerContext = baseHandler.getHandlerContext();

        // Whether this is selected
        boolean isSelected = isSelected(handlerContext, control, isMultiple, item);

        // xhtml:span enclosing input and label
        final String itemClasses = getItemClasses(item, isSelected ? "xforms-selected" : "xforms-deselected");
        final AttributesImpl spanAttributes = getIdClassXHTMLAttributes(containingDocument, reusableAttributes, SAXUtils.EMPTY_ATTRIBUTES, itemClasses, null);
        // Add item attributes to span
        addItemAttributes(item, spanAttributes);
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, spanAttributes);

        {
            final LHHAValue itemLabel = item.label();
            final String itemNamespacedId = XFormsUtils.namespaceId(handlerContext.getContainingDocument(), itemEffectiveId);

            // TODO: In static readonly mode, shouldn't use <label>, unless maybe alongside an <output> element.
            final String labelName = "label";

            if (! isBooleanInput) {
                reusableAttributes.clear();
                // Add Bootstrap classes
                reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, isMultiple ? "checkbox" : "radio");

                // No need for @for as the input, if any, is nested
                outputLabelForStart(handlerContext, reusableAttributes, null, null, LHHAC.LABEL, labelName, false);
            }

            {
                // xhtml:input
                if (! isStaticReadonly) {

                    final String elementName = "input";
                    final String elementQName = XMLUtils.buildQName(xhtmlPrefix, elementName);

                    reusableAttributes.clear();

                    reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, itemNamespacedId);
                    reusableAttributes.addAttribute("", "type", "type", XMLReceiverHelper.CDATA, type);

                    // Get group name from selection control if possible, otherwise use effective id
                    final String name = (!isMultiple && control instanceof XFormsSelect1Control) ? ((XFormsSelect1Control) control).getGroupName() : itemName;
                    reusableAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, name);

                    reusableAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, item.externalValue(encode));

                    if (!handlerContext.isTemplate() && control != null) {

                        if (isSelected)
                            reusableAttributes.addAttribute("", "checked", "checked", XMLReceiverHelper.CDATA, "checked");

                        if (isFirst)
                            handleAccessibilityAttributes(attributes, reusableAttributes, (XFormsControl) control);
                    }

                    if (baseHandler.isHTMLDisabled((XFormsControl) control))// cast because Java is not aware that XFormsValueControl extends XFormsControl
                        outputDisabledAttribute(reusableAttributes);

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName, reusableAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName);
                }

                if (! isBooleanInput) {

                    // <span class="xforms-hint-region"> or plain <span>
                    reusableAttributes.clear();
                    if (item.hint().isDefined())
                        reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-hint-region");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    outputLabelText(handlerContext.getController().getOutput(), null, itemLabel.label(), xhtmlPrefix, itemLabel.isHTML());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);

                    // <span class="xforms-help">
                    {
                        final Option<LHHAValue> helpOpt = item.help();
                        if (helpOpt.isDefined()) {
                            final LHHAValue help = helpOpt.get();

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-help");
                            outputLabelFor(handlerContext, reusableAttributes, null, null, LHHAC.HELP, "span", help.label(), help.isHTML(), false);
                        }
                    }

                    // <span class="xforms-hint">
                    {
                        final Option<LHHAValue> hintOpt = item.hint();
                        if (hintOpt.isDefined()) {
                            final LHHAValue hint = hintOpt.get();

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-hint");
                            outputLabelFor(handlerContext, reusableAttributes, null, null, LHHAC.HINT, "span", hint.label(), hint.isHTML(), false);
                        }
                    }
                }
            }

            if (! isBooleanInput)
                outputLabelForEnd(handlerContext, labelName);
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private static boolean isSelected(HandlerContext handlerContext, XFormsValueControl xformsControl, boolean isMultiple, Item item) {
        boolean isSelected;
        if (!handlerContext.isTemplate() && xformsControl != null) {
            final String itemValue = (item.value() == null) ? "" : item.value();
            final String controlValue = (xformsControl.getValue() == null) ? "" : xformsControl.getValue();
            isSelected = XFormsItemUtils.isSelected(isMultiple, controlValue, itemValue);
        } else {
            isSelected = false;
        }
        return isSelected;
    }

    private void handleItemCompact(ContentHandler contentHandler, String optionQName, XFormsValueControl xformsControl,
                                   boolean isMultiple, Item item, boolean encode) throws SAXException {

        final String itemClasses = getItemClasses(item, null);
        final AttributesImpl optionAttributes = getIdClassXHTMLAttributes(SAXUtils.EMPTY_ATTRIBUTES, itemClasses, null);
        // Add item attributes to option
        addItemAttributes(item, optionAttributes);
        optionAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, item.externalValue(encode));

        // Figure out whether what items are selected
        boolean isSelected = isSelected(handlerContext, xformsControl, isMultiple, item);
        if (isSelected)
            optionAttributes.addAttribute("", "selected", "selected", XMLReceiverHelper.CDATA, "selected");

        // xhtml:option
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName, optionAttributes);
        assert !item.label().isHTML();
        final String label = item.label().label();
        if (label != null)
            contentHandler.characters(label.toCharArray(), 0, label.length());
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName);
    }

    private static void addItemAttributes(Item item, AttributesImpl spanAttributes) {
        final Map<QName, String> itemAttributes = item.jAttributes();
        if (itemAttributes != null && itemAttributes.size() > 0) {
            for (final Map.Entry<QName, String> entry: itemAttributes.entrySet()) {
                final QName attributeQName = entry.getKey();
                if (!attributeQName.equals(XFormsConstants.CLASS_QNAME)) { // class is handled separately
                    final String attributeName = Itemset.getAttributeName(attributeQName);
                    spanAttributes.addAttribute("", attributeName, attributeName, XMLReceiverHelper.CDATA, entry.getValue());
                }
            }
        }
    }

    private static String getItemClasses(Item item, String initialClasses) {
        final Map<QName, String> itemAttributes = item.jAttributes();
        final StringBuilder sb = (initialClasses != null) ? new StringBuilder(initialClasses) : new StringBuilder();
        if (itemAttributes != null) {
            final String itemClassValue = itemAttributes.get(XFormsConstants.CLASS_QNAME);
            if (itemClassValue != null) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append(itemClassValue);
            }
        }
        return sb.toString();
    }

    public static String getItemId(String effectiveId, String itemIndex) {
        return XFormsUtils.appendToEffectiveId(effectiveId, "" + XFormsConstants.COMPONENT_SEPARATOR + XFormsConstants.COMPONENT_SEPARATOR + "e" + itemIndex);
    }

    @Override
    public String getForEffectiveId(String effectiveId) {
        // For full appearance we don't put a @for attribute so that selecting the main label doesn't select the item
        final SelectAppearanceTrait appearanceTrait = getAppearanceTrait();
        return appearanceTrait != null && appearanceTrait.isFull() ? null : super.getForEffectiveId(effectiveId);
    }

    @Override
    protected void handleLabel() throws SAXException {
        final SelectAppearanceTrait appearanceTrait = getAppearanceTrait();
        if (isStaticReadonly(currentControlOrNull()) || !(appearanceTrait != null && appearanceTrait.isFull()) || !handlerContext.isNoScript()) {
            // In noscript mode for full items, this is handled by fieldset/legend
            super.handleLabel();
        }
    }
}
