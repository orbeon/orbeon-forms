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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
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
    private boolean isAutocomplete;
    private boolean isAutocompleteNoFilter;
    private boolean isFull;
    private boolean isCompact;
    private boolean isTree;
    private boolean isMenu;

    private static final Item EMPTY_TOP_LEVEL_ITEM = new Item(false, false, null, "", "");

    public XFormsSelect1Handler() {
        super(false);
    }

    @Override
    public void init(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        super.init(uri, localname, qName, attributes);

        this.isMultiple = localname.equals("select");
        this.isOpenSelection = "open".equals(attributes.getValue("selection"));
        
        QName appearance = getAppearance(attributes); // this uses isMultiple

        this.isAutocomplete = isOpenSelection
                && XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME.equals(appearance);

        // NOTE: We don't support autocompletion with xforms:select for now, only with xforms:select1
        if (isAutocomplete && isMultiple) {
            appearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;
            isOpenSelection = false;
            isAutocomplete = false;
        }

        this.isAutocompleteNoFilter = isAutocomplete && "false".equals(attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "filter"));

        this.isFull = XFormsConstants.XFORMS_FULL_APPEARANCE_QNAME.equals(appearance);
        this.isCompact = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME.equals(appearance);
        this.isTree = XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME.equals(appearance);
        this.isMenu = XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME.equals(appearance);
    }

    @Override
    protected void addCustomClasses(StringBuilder classes, XFormsSingleNodeControl xformsControl) {
        if (isOpenSelection)
            classes.append(" xforms-select1-open");
        if (isAutocompleteNoFilter)
            classes.append(" xforms-select1-open-autocomplete-nofilter");
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
        final QName tempAppearance = super.getAppearance(attributes);

        final QName appearance;
        if (tempAppearance != null)
            appearance = tempAppearance;
        else if (isMultiple)
            appearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;// default for xforms:select
        else
            appearance = XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME;// default for xforms:select1

        return appearance;
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {
        // Get items, dynamic or static, if possible
        final XFormsSelect1Control xformsSelect1Control = (XFormsSelect1Control) xformsControl;

        // Get items if:
        // 1. The itemset is static
        // 2. The control exists and is relevant
        final Itemset itemset = XFormsSelect1Control.getInitialItemset(pipelineContext, containingDocument, xformsSelect1Control, getPrefixedId());

        outputContent(uri, localname, attributes, effectiveId, xformsSelect1Control, itemset, isMultiple, isFull, false);
    }

    public void outputContent(String uri, String localname, Attributes attributes, String effectiveId,
                              final XFormsValueControl xformsSelect1Control, Itemset itemset,
                              final boolean isMultiple, final boolean isFull, boolean isBooleanInput) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, xformsSelect1Control, !isFull);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        if (!isStaticReadonly(xformsSelect1Control)) {
            if (isFull) {
                // Full appearance
                outputFull(uri, localname, attributes, effectiveId, xformsSelect1Control, itemset, isMultiple, isBooleanInput);
            } else {

                if (isOpenSelection) {

                    if (isAutocomplete) {

                        // Create xhtml:span
                        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, containerAttributes);

                        {
                            {
                                // Create xhtml:input
                                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

                                reusableAttributes.clear();
                                reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "text");
                                reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, "xforms-select1-open-input-" + effectiveId);
                                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select1-open-input");
                                reusableAttributes.addAttribute("", "autocomplete", "autocomplete", ContentHandlerHelper.CDATA, "off");

                                final String value = (xformsSelect1Control == null) ? null : xformsSelect1Control.getValue(pipelineContext);
                                // NOTE: With open selection, we send all values to the client but not encrypt them because the client matches on values
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, (value == null) ? "" : value);
                                handleReadOnlyAttribute(reusableAttributes, containingDocument, xformsSelect1Control);
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);

                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                            }
                            {
                                // Create xhtml:select
                                final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");

                                reusableAttributes.clear();
                                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select1-open-select");

                                if (isCompact)
                                    reusableAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                                // Handle accessibility attributes
                                handleAccessibilityAttributes(attributes, reusableAttributes);

                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, reusableAttributes);

                                final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                                handleItemCompact(contentHandler, optionQName, xformsSelect1Control, isMultiple, EMPTY_TOP_LEVEL_ITEM);
                                if (itemset != null) {
                                    for (final Item item: itemset.toList()) {
                                        if (item.getValue() != null)
                                            handleItemCompact(contentHandler, optionQName, xformsSelect1Control, isMultiple, item);
                                    }
                                }

                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName);
                            }
                        }

                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                    } else {
                        // We do not support other appearances or regular open selection for now
                        throw new ValidationException("Open selection currently only supports the xxforms:autocomplete appearance.",
                                new ExtendedLocationData(handlerContext.getLocationData(), "producing markup for xforms:" + localname + " control",
                                        (xformsSelect1Control != null) ? xformsSelect1Control.getControlElement() : null));
                    }

                } else if (isTree) {
                    // xxforms:tree appearance

                    // Create xhtml:div with tree info
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");

                    handleReadOnlyAttribute(containerAttributes, containingDocument, xformsSelect1Control);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, containerAttributes);
                    if (itemset != null) { // can be null if the control is non-relevant
                        outputJSONTreeInfo(xformsSelect1Control, itemset, isMultiple, contentHandler);
                    }
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else if (isMenu) {
                    // xxforms:menu appearance

                    // Create enclosing xhtml:div
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
                    final String ulQName = XMLUtils.buildQName(xhtmlPrefix, "ul");
                    final String liQName = XMLUtils.buildQName(xhtmlPrefix, "li");
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");

                    handleReadOnlyAttribute(containerAttributes, containingDocument, xformsSelect1Control);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, containerAttributes);
                    {
                        // Create xhtml:div with initial menu entries
                        {
                            itemset.visit(contentHandler, new ItemsetListener() {

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

                                    final String text = item.getLabel();
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

                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                        if (itemset != null) { // can be null if the control is non-relevant
                            outputJSONTreeInfo(xformsSelect1Control, itemset, isMultiple, contentHandler);
                        }
                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
                    }
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else {
                    // Create xhtml:select
                    final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");
                    containerAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);// necessary for noscript mode

                    if (isCompact)
                        containerAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                    // Handle accessibility attributes
                    handleAccessibilityAttributes(attributes, containerAttributes);

                    handleReadOnlyAttribute(containerAttributes, containingDocument, xformsSelect1Control);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, containerAttributes);
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

                            itemset.visit(contentHandler, new ItemsetListener() {

                                private int optgroupCount = 0;

                                public void startLevel(ContentHandler contentHandler, Item item) throws SAXException {}

                                public void endLevel(ContentHandler contentHandler) throws SAXException {
                                    if (optgroupCount-- > 0) {
                                        // End xhtml:optgroup
                                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName);
                                    }
                                }

                                public void startItem(ContentHandler contentHandler, Item item, boolean first) throws SAXException {

                                    final String label = item.getLabel();
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
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName);
                }
            }
        } else {
            // Read-only mode

            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, containerAttributes);
            if (!handlerContext.isTemplate()) {
                final String value = (xformsSelect1Control == null || xformsSelect1Control.getValue(pipelineContext) == null) ? "" : xformsSelect1Control.getValue(pipelineContext);
                final StringBuilder sb = new StringBuilder();
                if (itemset != null) {
                    int selectedFound = 0;
                    for (final Item currentItem: itemset.toList()) {
                        if (XFormsItemUtils.isSelected(isMultiple, value, currentItem.getValue())) {
                            if (selectedFound > 0)
                                sb.append(" - ");
                            sb.append(currentItem.getLabel());
                            selectedFound++;
                        }
                    }
                }

                if (sb.length() > 0) {
                    final String result = sb.toString();
                    contentHandler.characters(result.toCharArray(), 0, result.length());
                }
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }
    }

    private void outputFull(String uri, String localname, Attributes attributes, String effectiveId,
                            XFormsValueControl xformsControl, Itemset itemset, boolean isMultiple, boolean isBooleanInput) throws SAXException {
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
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
            final boolean outputContainerElement = !isBooleanInput || !handlerContext.isNewXHTMLLayout();
            if (outputContainerElement)
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, containingElementName, containingElementQName, containerAttributes);
            {
                if (handlerContext.isNoScript()) {
                    // Output <legend>
                    final String legendName = "legend";
                    final String legendQName = XMLUtils.buildQName(xhtmlPrefix, legendName);
                    reusableAttributes.clear();
                    // TODO: handle other attributes? xforms-disabled?
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-label");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, legendName, legendQName, reusableAttributes);
                    if (xformsControl != null) {
                        final boolean mustOutputHTMLFragment = xformsControl.isHTMLLabel(pipelineContext);
                        outputLabelText(contentHandler, xformsControl, xformsControl.getLabel(pipelineContext), xhtmlPrefix, mustOutputHTMLFragment);
                    }
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, legendName, legendQName);
                }

                if (itemset != null) {
                    int itemIndex = 0;
                    for (Iterator<Item> i = itemset.toList().iterator(); i.hasNext(); itemIndex++) {
                        final Item item = i.next();
                        final String itemEffectiveId = getItemId(effectiveId, Integer.toString(itemIndex));
                        handleItemFull(pipelineContext, handlerContext, contentHandler, reusableAttributes, attributes, xhtmlPrefix, spanQName,
                                containingDocument, xformsControl, effectiveId, itemEffectiveId, isMultiple, fullItemType, item, itemIndex == 0);
                    }
                }
            }
            if (outputContainerElement)
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, containingElementName, containingElementQName);
        }

        // NOTE: Templates for full items are output globally in XHTMLBodyHandler
    }

    public static void outputItemFullTemplate(PipelineContext pipelineContext, HandlerContext handlerContext,
                                              ContentHandler contentHandler, String xhtmlPrefix, String spanQName,
                                              XFormsContainingDocument containingDocument,
                                              AttributesImpl reusableAttributes, Attributes attributes, String templateId,
                                              String effectiveId, boolean isMultiple, String fullItemType) throws SAXException {
        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, templateId);
        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-template");

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
        {
            final String itemEffectiveId = "$xforms-item-effective-id$";
            handleItemFull(pipelineContext, handlerContext, contentHandler, reusableAttributes, attributes,
                    xhtmlPrefix, spanQName, containingDocument, null, effectiveId, itemEffectiveId, isMultiple, fullItemType,
                    new Item(isMultiple, false, null, // make sure the value "$xforms-template-value$" is not encrypted
                            "$xforms-template-label$", "$xforms-template-value$"), true);
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private void outputJSONTreeInfo(XFormsValueControl valueControl, Itemset itemset, boolean many, ContentHandler contentHandler) throws SAXException {
        if (valueControl != null && !handlerContext.isTemplate()) {
            // Produce a JSON fragment with hierarchical information
            final String result = itemset.getJSONTreeInfo(pipelineContext, valueControl.getValue(pipelineContext), many, handlerContext.getLocationData());
            contentHandler.characters(result.toCharArray(), 0, result.length());
        } else {
            // Don't produce any content when generating a template
        }
    }

    public static void handleItemFull(PipelineContext pipelineContext, HandlerContext handlerContext, ContentHandler contentHandler,
                                      AttributesImpl reusableAttributes, Attributes attributes, String xhtmlPrefix, String spanQName,
                                      XFormsContainingDocument containingDocument, XFormsValueControl xformsControl,
                                      String effectiveId, String itemEffectiveId, boolean isMultiple, String type,
                                      Item item, boolean isFirst) throws SAXException {

        // Whether this is selected
        boolean isSelected = isSelected(pipelineContext, handlerContext, xformsControl, isMultiple, item);

        // xhtml:span enclosing input and label
        final String itemClasses = getItemClasses(item, isSelected ? "xforms-selected" : "xforms-deselected");
        final AttributesImpl spanAttributes = getAttributes(reusableAttributes, XMLUtils.EMPTY_ATTRIBUTES, itemClasses, null);
        // Add item attributes to span
        addItemAttributes(item, spanAttributes);
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, spanAttributes);

        {
            {
                // xhtml:input
                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, itemEffectiveId);
                reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, type);

                // Get group name from selection control if possible, otherwise use effective id
                final String name = (!isMultiple && xformsControl instanceof XFormsSelect1Control) ? ((XFormsSelect1Control) xformsControl).getGroupName() : effectiveId;
                reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, name);

                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getExternalValue(pipelineContext));

                if (!handlerContext.isTemplate() && xformsControl != null) {

                    if (isSelected) {
                        reusableAttributes.addAttribute("", "checked", "checked", ContentHandlerHelper.CDATA, "checked");
                    }

                    if (isFirst) {
                        // Handle accessibility attributes
                        handleAccessibilityAttributes(attributes, reusableAttributes);
                    }
                }

                handleReadOnlyAttribute(reusableAttributes, containingDocument, xformsControl);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
            }

            // We don't output the label within <input></input>, because XHTML won't display it.
            final String label = item.getLabel();
            if (label != null) { // allow null label to tell not to output the <label> element at all
                reusableAttributes.clear();
                outputLabelFor(handlerContext, reusableAttributes, itemEffectiveId, itemEffectiveId, LHHAC.LABEL, "label", label, false, false);// TODO: may be HTML for full appearance
            }
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private static boolean isSelected(PipelineContext pipelineContext, HandlerContext handlerContext, XFormsValueControl xformsControl, boolean isMultiple, Item item) {
        boolean isSelected;
        if (!handlerContext.isTemplate() && xformsControl != null) {
            final String itemValue = ((item.getValue() == null) ? "" : item.getValue()).trim();
            final String controlValue = ((xformsControl.getValue(pipelineContext) == null) ? "" : xformsControl.getValue(pipelineContext)).trim();
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
        optionAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getExternalValue(pipelineContext));

        // Figure out whether what items are selected
        boolean isSelected = isSelected(pipelineContext, handlerContext, xformsControl, isMultiple, item);
        if (isSelected)
            optionAttributes.addAttribute("", "selected", "selected", ContentHandlerHelper.CDATA, "selected");

        // xhtml:option
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName, optionAttributes);
        final String label = item.getLabel();
        if (label != null)
            contentHandler.characters(label.toCharArray(), 0, label.length());
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName);
    }

    private static void addItemAttributes(Item item, AttributesImpl spanAttributes) {
        final Map<String, String> itemAttributes = item.getAttributes();
        if (itemAttributes != null && itemAttributes.size() > 0) {
            for (final Map.Entry<String, String> entry: itemAttributes.entrySet()) {
                final String attributeName = entry.getKey();
                if (!attributeName.equals("class")) { // class is handled separately
                    spanAttributes.addAttribute("", attributeName, attributeName, ContentHandlerHelper.CDATA, entry.getValue());
                }
            }
        }
    }

    private static String getItemClasses(Item item, String initialClasses) {
        final Map<String, String> itemAttributes = item.getAttributes();
        final StringBuilder sb = (initialClasses != null) ? new StringBuilder(initialClasses) : new StringBuilder();
        if (itemAttributes != null) {
            final String itemClassValue = itemAttributes.get("class");
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
        if (isStaticReadonly(getXFormsControl()) || !isFull || !handlerContext.isNoScript()) {
            // In noscript mode for full items, this is handled by fieldset/legend
            super.handleLabel();
        }
    }
}
