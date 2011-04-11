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

import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.itemset.Item;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.itemset.ItemsetListener;
import org.orbeon.oxf.xforms.itemset.XFormsItemUtils;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Iterator;
import java.util.Map;

/**
 * Handle xforms:select and xforms:select1.
 *
 * TODO: Subclasses per appearance.
 */
public class XFormsSelect1Handler extends XFormsControlLifecyleHandler {

    private boolean isMultiple;
    private boolean isOpenSelection;

    private QName effectiveAppearance;
    private boolean isFull;
    private boolean isCompact;
    private boolean isTree;
    private boolean isMenu;

    public XFormsSelect1Handler() {
        super(false);
    }

    @Override
    public void init(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        super.init(uri, localname, qName, attributes);

        isMultiple = localname.equals("select");
        isOpenSelection = "open".equals(attributes.getValue("selection"));

        // Compute effective appearance
        {
            final QName plainAppearance = super.getAppearance(attributes);
            final QName effectiveAppearance;
            if (plainAppearance != null) {
                if (isMultiple && XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(plainAppearance)) {
                    // For now, a select with minimal appearance is handled as a compact appearance
                    effectiveAppearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;
                } else if (isMultiple && XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME.equals(plainAppearance)) {
                    // We don't support autocompletion with xforms:select for now, only with xforms:select1
                    effectiveAppearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;// default for xforms:select
                    isOpenSelection = false;
                } else if (isOpenSelection && XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME.equals(plainAppearance)) {
                    effectiveAppearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;// default for xforms:select
                    isOpenSelection = false;
                } else {
                    effectiveAppearance = plainAppearance;
                }
            } else if (isMultiple) {
                effectiveAppearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;// default for xforms:select
            } else {
                effectiveAppearance = XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME;// default for xforms:select1
            }
            this.effectiveAppearance = effectiveAppearance;
        }

        isFull = XFormsConstants.XFORMS_FULL_APPEARANCE_QNAME.equals(effectiveAppearance);
        isCompact = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME.equals(effectiveAppearance);
        isTree = XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME.equals(effectiveAppearance);
        isMenu = XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME.equals(effectiveAppearance);
    }

    @Override
    protected void addCustomClasses(StringBuilder classes, XFormsControl control) {
        if (isOpenSelection)
            classes.append(" xforms-select1-open");
        if (isTree)
            classes.append(" xforms-initially-hidden");
    }

    @Override
    protected boolean isDefaultIncremental() {
        // Incremental mode is the default
        return true;
    }

    @Override
    protected QName getAppearance(Attributes attributes) {
        return effectiveAppearance;
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        // Get items, dynamic or static, if possible
        final XFormsSelect1Control xformsSelect1Control = (XFormsSelect1Control) control;

        // Get items if:
        // 1. The itemset is static
        // 2. The control exists and is relevant
        final Itemset itemset = XFormsSelect1Control.getInitialItemset(pipelineContext, containingDocument, xformsSelect1Control, getPrefixedId());

        outputContent(uri, localname, attributes, effectiveId, xformsSelect1Control, itemset, isMultiple, isFull, false);
    }

    public void outputContent(String uri, String localname, Attributes attributes, String effectiveId,
                              final XFormsValueControl xformsSelect1Control, Itemset itemset,
                              final boolean isMultiple, final boolean isFull, boolean isBooleanInput) throws SAXException {

        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, xformsSelect1Control, !isFull);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        if (!isStaticReadonly(xformsSelect1Control)) {
            if (isFull) {
                // Full appearance
                outputFull(uri, localname, attributes, effectiveId, xformsSelect1Control, itemset, isMultiple, isBooleanInput);
            } else {

                if (isOpenSelection) {
                    // We do not support other appearances or regular open selection for now
                    throw new ValidationException("Open selection currently not supported.",
                            new ExtendedLocationData(handlerContext.getLocationData(), "producing markup for xforms:" + localname + " control",
                                    (xformsSelect1Control != null) ? xformsSelect1Control.getControlElement() : null));
                } else if (isTree) {
                    // xxforms:tree appearance

                    // Create xhtml:div with tree info
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");

                    if (isHTMLDisabled(xformsSelect1Control))
                        outputDisabledAttribute(containerAttributes);
                    xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, containerAttributes);
                    if (itemset != null) { // can be null if the control is non-relevant
                        outputJSONTreeInfo(xformsSelect1Control, itemset, isMultiple, xmlReceiver);
                    }
                    xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else if (isMenu) {
                    // xxforms:menu appearance

                    // Create enclosing xhtml:div
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
                    final String ulQName = XMLUtils.buildQName(xhtmlPrefix, "ul");
                    final String liQName = XMLUtils.buildQName(xhtmlPrefix, "li");
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");

                    if (isHTMLDisabled(xformsSelect1Control))
                        outputDisabledAttribute(containerAttributes);
                    xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, containerAttributes);
                    if (itemset != null) { // can be null if the control is non-relevant
                        // Create xhtml:div with initial menu entries
                        {
                            itemset.visit(xmlReceiver, new ItemsetListener() {

                                private boolean groupJustStarted = false;

                                public void startLevel(ContentHandler contentHandler, Item item) throws SAXException {

                                    final boolean isTopLevel = item == null;

                                    reusableAttributes.clear();
                                    final String divClasses = isTopLevel ? "yuimenubar" : "yuimenu";
                                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, divClasses);
                                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);

                                    reusableAttributes.clear();
                                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "bd");
                                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);

                                    reusableAttributes.clear();
                                    // NOTE: We just decide to put item classes on <ul>
                                    final String classes = isTopLevel ? "first-of-type" : getItemClasses(item, "first-of-type");
                                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, classes);
                                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "ul", ulQName, reusableAttributes);

                                    groupJustStarted = true;
                                }

                                public void endLevel(ContentHandler contentHandler) throws SAXException {
                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "ul", ulQName);
                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                                    groupJustStarted = false;
                                }

                                public void startItem(ContentHandler contentHandler, Item item, boolean first) throws SAXException {

                                    final String liClasses;
                                    {
                                        final StringBuilder sb = new StringBuilder(item.isTopLevel() ? "yuimenubaritem" : "yuimenuitem");
                                        if (groupJustStarted)
                                            sb.append(" first-of-type");
                                        liClasses = getItemClasses(item, sb.toString());
                                    }
                                    reusableAttributes.clear();
                                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, liClasses);
                                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "li", liQName, reusableAttributes);

                                    reusableAttributes.clear();
                                    reusableAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "#");
                                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, reusableAttributes);

                                    assert !item.getLabel().isHTML();
                                    final String text = item.getLabel().getLabel();
                                    contentHandler.characters(text.toCharArray(), 0, text.length());

                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);

                                    groupJustStarted = false;
                                }


                                public void endItem(ContentHandler contentHandler) throws SAXException {
                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "li", liQName);

                                    groupJustStarted = false;
                                }
                            });

                        }

                        // Create xhtml:div with tree info
                        reusableAttributes.clear();
                        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-initially-hidden");

                        xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                        if (itemset != null) { // can be null if the control is non-relevant
                            outputJSONTreeInfo(xformsSelect1Control, itemset, isMultiple, xmlReceiver);
                        }
                        xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
                    }
                    xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else {
                    // Create xhtml:select
                    final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");
                    containerAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);// necessary for noscript mode

                    if (isCompact)
                        containerAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                    // Handle accessibility attributes
                    handleAccessibilityAttributes(attributes, containerAttributes);

                    if (isHTMLDisabled(xformsSelect1Control))
                        outputDisabledAttribute(containerAttributes);
                    xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, containerAttributes);
                    {
                        final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                        final String optGroupQName = XMLUtils.buildQName(xhtmlPrefix, "optgroup");

                        if (itemset != null) {

    // Work in progress for in-bounds/out-of-bounds
    //                        if (!((XFormsSelect1Control) xformsControl).isInBounds(items)) {
    //                            // Control is out of bounds so add first item with out of bound value to handle this
    //                            handleItemCompact(contentHandler, optionQName, xformsControl, isMultiple,
    //                                    new XFormsItemUtils.Item(XFormsProperties.isEncryptItemValues(containingDocument),
    //                                            Collections.EMPTY_LIST, "", xformsControl.getValue(pipelineContext), 1));
    //                        }

                            itemset.visit(xmlReceiver, new ItemsetListener() {

                                private int optgroupCount = 0;

                                public void startLevel(ContentHandler contentHandler, Item item) throws SAXException {}

                                public void endLevel(ContentHandler contentHandler) throws SAXException {
                                    if (optgroupCount-- > 0) {
                                        // End xhtml:optgroup
                                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName);
                                    }
                                }

                                public void startItem(ContentHandler contentHandler, Item item, boolean first) throws SAXException {

                                	assert !item.getLabel().isHTML();
                                    final String label = item.getLabel().getLabel();
                                    final String value = item.getValue();

                                    if (value == null) {
                                        final String itemClasses = getItemClasses(item, null);
                                        final AttributesImpl optGroupAttributes = getAttributes(XMLUtils.EMPTY_ATTRIBUTES, itemClasses, null);
                                        if (label != null)
                                            optGroupAttributes.addAttribute("", "label", "label", ContentHandlerHelper.CDATA, label);

                                        // Start xhtml:optgroup
                                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName, optGroupAttributes);
                                        optgroupCount++;
                                    } else {
                                        handleItemCompact(contentHandler, optionQName, xformsSelect1Control, isMultiple, item);
                                    }
                                }


                                public void endItem(ContentHandler contentHandler) throws SAXException {
                                }
                            });
                        }
                    }
                    xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName);
                }
            }
        } else {
            // Read-only mode

            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, containerAttributes);
            if (!handlerContext.isTemplate()) {
                final String value = (xformsSelect1Control == null || xformsSelect1Control.getValue() == null) ? "" : xformsSelect1Control.getValue();
                if (itemset != null) {
                    int selectedFound = 0;
                    final ContentHandlerHelper ch = new ContentHandlerHelper(xmlReceiver);
                    for (final Item currentItem : itemset.toList()) {
                        if (XFormsItemUtils.isSelected(isMultiple, value, currentItem.getValue())) {
                            if (selectedFound > 0)
                                ch.text(" - ");

                            currentItem.getLabel().streamAsHTML(ch, xformsSelect1Control.getLocationData());

                            selectedFound++;
                        }
                    }
                }
            }
            xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }
    }

    private void outputFull(String uri, String localname, Attributes attributes, String effectiveId,
                            XFormsValueControl xformsControl, Itemset itemset, boolean isMultiple, boolean isBooleanInput) throws SAXException {
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();
        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, xformsControl, !isFull);
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

        final String fullItemType = isMultiple ? "checkbox" : "radio";

        // In noscript mode, use <fieldset>

        // TODO: This really hasn't much to do with noscript; should we always use fieldset, or make this an
        // option? Benefit of limiting to noscript is that then no JS change is needed
        final String containingElementName = handlerContext.isNoScript() ? "fieldset" : "span";
        final String containingElementQName = XMLUtils.buildQName(xhtmlPrefix, containingElementName);

        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
        {

            // Old layout always output container <span>/<fieldset>, and in new layout we only put it for select/select1
            final boolean outputContainerElement = !isBooleanInput || !handlerContext.isSpanHTMLLayout();
            if (outputContainerElement)
                xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, containingElementName, containingElementQName, containerAttributes);
            {
                if (handlerContext.isNoScript()) {
                    // Output <legend>
                    final String legendName = "legend";
                    final String legendQName = XMLUtils.buildQName(xhtmlPrefix, legendName);
                    reusableAttributes.clear();
                    // TODO: handle other attributes? xforms-disabled?
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-label");
                    xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, legendName, legendQName, reusableAttributes);
                    if (xformsControl != null) {
                        final boolean mustOutputHTMLFragment = xformsControl.isHTMLLabel(pipelineContext);
                        outputLabelText(xmlReceiver, xformsControl, xformsControl.getLabel(), xhtmlPrefix, mustOutputHTMLFragment);
                    }
                    xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, legendName, legendQName);
                }

                if (itemset != null) {
                    int itemIndex = 0;
                    for (Iterator<Item> i = itemset.toList().iterator(); i.hasNext(); itemIndex++) {
                        final Item item = i.next();
                        final String itemEffectiveId = getItemId(effectiveId, Integer.toString(itemIndex));
                        handleItemFull(pipelineContext, this, xmlReceiver, reusableAttributes, attributes, xhtmlPrefix, spanQName,
                                containingDocument, xformsControl, effectiveId, itemEffectiveId, isMultiple, fullItemType, item, itemIndex == 0);
                    }
                }
            }
            if (outputContainerElement)
                xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, containingElementName, containingElementQName);
        }

        // NOTE: Templates for full items are output globally in XHTMLBodyHandler
    }

    public static void outputItemFullTemplate(PipelineContext pipelineContext, XFormsBaseHandler baseHandler,
                                              ContentHandler contentHandler, String xhtmlPrefix, String spanQName,
                                              XFormsContainingDocument containingDocument,
                                              AttributesImpl reusableAttributes, Attributes attributes, String templateId,
                                              String effectiveId, boolean isMultiple, String fullItemType) throws SAXException {
        reusableAttributes.clear();
//        reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(handlerContext.getContainingDocument(), templateId));
        // Client queries template by id without namespace, so output that. Not ideal as all ids should be namespaced.
        reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, templateId);
        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-template");

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
        {
            final String itemEffectiveId = "$xforms-item-effective-id$";
            handleItemFull(pipelineContext, baseHandler, contentHandler, reusableAttributes, attributes,
                    xhtmlPrefix, spanQName, containingDocument, null, effectiveId, itemEffectiveId, isMultiple, fullItemType,
                    new Item(isMultiple, false, null, // make sure the value "$xforms-template-value$" is not encrypted
                            new Item.Label("$xforms-template-label$", false), "$xforms-template-value$"), true);
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private void outputJSONTreeInfo(XFormsValueControl valueControl, Itemset itemset, boolean many, ContentHandler contentHandler) throws SAXException {
        if (valueControl != null && !handlerContext.isTemplate()) {
            // Produce a JSON fragment with hierarchical information
            final String result = itemset.getJSONTreeInfo(valueControl.getValue(), many, handlerContext.getLocationData());
            contentHandler.characters(result.toCharArray(), 0, result.length());
        } else {
            // Don't produce any content when generating a template
        }
    }

    public static void handleItemFull(PipelineContext pipelineContext, XFormsBaseHandler baseHandler, ContentHandler contentHandler,
                                      AttributesImpl reusableAttributes, Attributes attributes, String xhtmlPrefix, String spanQName,
                                      XFormsContainingDocument containingDocument, XFormsValueControl xformsControl,
                                      String effectiveId, String itemEffectiveId, boolean isMultiple, String type,
                                      Item item, boolean isFirst) throws SAXException {

        final HandlerContext handlerContext = baseHandler.handlerContext;

        // Whether this is selected
        boolean isSelected = isSelected(handlerContext, xformsControl, isMultiple, item);

        // xhtml:span enclosing input and label
        final String itemClasses = getItemClasses(item, isSelected ? "xforms-selected" : "xforms-deselected");
        final AttributesImpl spanAttributes = getAttributes(containingDocument, reusableAttributes, XMLUtils.EMPTY_ATTRIBUTES, itemClasses, null);
        // Add item attributes to span
        addItemAttributes(item, spanAttributes);
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, spanAttributes);

        {
            final Item.Label itemLabel = item.getLabel();
            final boolean labelNonEmpty = itemLabel != null && itemLabel.getLabel().length() != 0;// empty only for xforms|input:xxforms-type(xs:boolean)
            if (labelNonEmpty) {
                reusableAttributes.clear();
                outputLabelForStart(handlerContext, reusableAttributes, itemEffectiveId, itemEffectiveId, LHHAC.LABEL, "label", false);
            }

            {
                // xhtml:input
                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(handlerContext.getContainingDocument(), itemEffectiveId));
                reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, type);

                // Get group name from selection control if possible, otherwise use effective id
                final String name = (!isMultiple && xformsControl instanceof XFormsSelect1Control) ? ((XFormsSelect1Control) xformsControl).getGroupName() : effectiveId;
                reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, name);

                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getExternalValue());

                if (!handlerContext.isTemplate() && xformsControl != null) {

                    if (isSelected) {
                        reusableAttributes.addAttribute("", "checked", "checked", ContentHandlerHelper.CDATA, "checked");
                    }

                    if (isFirst) {
                        // Handle accessibility attributes
                        handleAccessibilityAttributes(attributes, reusableAttributes);
                    }
                }

                if (baseHandler.isHTMLDisabled(xformsControl))
                    outputDisabledAttribute(reusableAttributes);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
            }

            if (labelNonEmpty) {
                outputLabelForEnd(handlerContext, "label", itemLabel.getLabel(), itemLabel.isHTML());
            }
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private static boolean isSelected(HandlerContext handlerContext, XFormsValueControl xformsControl, boolean isMultiple, Item item) {
        boolean isSelected;
        if (!handlerContext.isTemplate() && xformsControl != null) {
            final String itemValue = (item.getValue() == null) ? "" : item.getValue();
            final String controlValue = (xformsControl.getValue() == null) ? "" : xformsControl.getValue();
            isSelected = XFormsItemUtils.isSelected(isMultiple, controlValue, itemValue);
        } else {
            isSelected = false;
        }
        return isSelected;
    }

    private void handleItemCompact(ContentHandler contentHandler, String optionQName, XFormsValueControl xformsControl,
                                   boolean isMultiple, Item item) throws SAXException {

        final String itemClasses = getItemClasses(item, null);
        final AttributesImpl optionAttributes = getAttributes(XMLUtils.EMPTY_ATTRIBUTES, itemClasses, null);
        // Add item attributes to option
        addItemAttributes(item, optionAttributes);
        optionAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getExternalValue());

        // Figure out whether what items are selected
        boolean isSelected = isSelected(handlerContext, xformsControl, isMultiple, item);
        if (isSelected)
            optionAttributes.addAttribute("", "selected", "selected", ContentHandlerHelper.CDATA, "selected");

        // xhtml:option
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName, optionAttributes);
        assert !item.getLabel().isHTML();
        final String label = item.getLabel().getLabel();
        if (label != null)
            contentHandler.characters(label.toCharArray(), 0, label.length());
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName);
    }

    private static void addItemAttributes(Item item, AttributesImpl spanAttributes) {
        final Map<QName, String> itemAttributes = item.getAttributes();
        if (itemAttributes != null && itemAttributes.size() > 0) {
            for (final Map.Entry<QName, String> entry: itemAttributes.entrySet()) {
                final QName attributeQName = entry.getKey();
                if (!attributeQName.equals(XFormsConstants.CLASS_QNAME)) { // class is handled separately
                    final String attributeName = Itemset.getAttributeName(attributeQName);
                    spanAttributes.addAttribute("", attributeName, attributeName, ContentHandlerHelper.CDATA, entry.getValue());
                }
            }
        }
    }

    private static String getItemClasses(Item item, String initialClasses) {
        final Map<QName, String> itemAttributes = item.getAttributes();
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
        return XFormsUtils.appendToEffectiveId(effectiveId, "$$e" + itemIndex);
    }

    @Override
    public String getForEffectiveId() {
        // For full appearance we don't put a @for attribute so that selecting the main label doesn't select the item
        return isFull ? null : super.getForEffectiveId();
    }

    @Override
    protected void handleLabel() throws SAXException {
        if (isStaticReadonly(getControl()) || !isFull || !handlerContext.isNoScript()) {
            // In noscript mode for full items, this is handled by fieldset/legend
            super.handleLabel();
        }
    }
}
