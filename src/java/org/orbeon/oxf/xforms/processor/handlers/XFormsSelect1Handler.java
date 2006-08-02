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

import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * Handle xforms:select and xforms:select1.
 */
public class XFormsSelect1Handler extends XFormsValueControlHandler {

    private Attributes elementAttributes;
    private String id;
    private String effectiveId;

    private List items;
    private int hierarchyLevel;
    private Stack choicesStack;
    private boolean hasItemset;

    private Attributes itemChoicesAttributes;
    private Attributes itemsetAttributes;

    private StringBuffer labelStringBuffer;
    private StringBuffer valueStringBuffer;

    private boolean isInItem;
    private boolean isInChoices;
    private boolean isInLabel;
    private boolean isInValue;

    public XFormsSelect1Handler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        id = handlerContext.getId(elementAttributes);
        effectiveId = handlerContext.getEffectiveId(elementAttributes);

        // Reset state, as this handler is reused
        if (items != null) {
            items.clear();
            choicesStack.clear();
        } else {
            items = new ArrayList();
            choicesStack = new Stack();
        }
        hierarchyLevel = 0;
        hasItemset = false;
        isInItem = isInLabel = isInValue = isInChoices = false;

        super.start(uri, localname, qName, attributes);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            if ("item".equals(localname)) {
                // xforms:item
                itemChoicesAttributes = new AttributesImpl(attributes);

                if (labelStringBuffer != null)
                    labelStringBuffer.setLength(0);
                else
                    labelStringBuffer = new StringBuffer();

                if (valueStringBuffer != null)
                    valueStringBuffer.setLength(0);
                else
                    valueStringBuffer = new StringBuffer();

                isInItem = true;
                isInChoices = false;
                hierarchyLevel++;

            } else if ("itemset".equals(localname)) {
                // xforms:itemset
                itemsetAttributes = new AttributesImpl(attributes);
                hasItemset = true; // TODO: in the future we should be able to handle multiple itemsets

                if (!handlerContext.isGenerateTemplate()) {

                    final XFormsSelect1Control xformsSelect1Control
                            = (XFormsSelect1Control) containingDocument.getObjectById(pipelineContext, effectiveId);

                    final List itemsetInfos = (List) xformsSelect1Control.getItemset();
                    if (itemsetInfos != null && itemsetInfos.size() > 0) { // may be null when there is no item in the itemset
                        final Stack nodeStack = new Stack();
                        int level = 0;
                        for (Iterator j = itemsetInfos.iterator(); j.hasNext();) {
                            final XFormsSelect1Control.ItemsetInfo currentItemsetInfo = (XFormsSelect1Control.ItemsetInfo) j.next();
                            final NodeInfo currentNodeInfo = currentItemsetInfo.getNodeInfo();

                            final int newLevel = XFormsSelect1Control.getNodeLevel(currentNodeInfo, nodeStack);
                            if (level - newLevel >= 0) {
                                //  We are going down one or more levels
                                for (int i = newLevel; i <= level; i++) {
                                    nodeStack.pop();
                                }
                            }

                            items.add(new XFormsSelect1Control.Item(true, getAttributes(itemsetAttributes, null, null), currentItemsetInfo.getLabel(), currentItemsetInfo.getValue(), newLevel));
                            nodeStack.push(currentNodeInfo);
                            level = newLevel;
                        }
                    }
                }

                isInItem = false;
                isInChoices = false;

            } else if ("choices".equals(localname)) {
                // xforms:choices
                itemChoicesAttributes = new AttributesImpl(attributes);

                // Push a null label
                choicesStack.push(null);

                if (labelStringBuffer != null)
                    labelStringBuffer.setLength(0);
                else
                    labelStringBuffer = new StringBuffer();

                isInItem = false;
                isInChoices = true;
            } else if ("label".equals(localname)) {
                // xforms:label
                if (isInItem || isInChoices)
                    isInLabel = true;
            } else if ("value".equals(localname)) {
                // xforms:value
                if (isInItem)
                    isInValue = true;
            }
        }
        super.startElement(uri, localname, qName, attributes);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (isInLabel) {
            labelStringBuffer.append(chars, start, length);
        } else if (isInValue) {
            valueStringBuffer.append(chars, start, length);
        }
        super.characters(chars, start, length);
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            if ("item".equals(localname)) {
                // xforms:item
                items.add(new XFormsSelect1Control.Item(false, getAttributes(itemChoicesAttributes, null, null), labelStringBuffer.toString(), valueStringBuffer.toString(), hierarchyLevel));
                isInItem = false;
                hierarchyLevel--;
            } else if ("label".equals(localname)) {
                // xforms:label

                if (isInChoices) {
                    // There was a label for the current xforms:choices

                    // We only modify the hierarchy if there is a label
                    final String choicesLabel = labelStringBuffer.toString();
                    choicesStack.pop();
                    choicesStack.push(choicesLabel);
                    hierarchyLevel++;

                    items.add(new XFormsSelect1Control.Item(false, getAttributes(itemChoicesAttributes, null, null), choicesLabel, null, hierarchyLevel));
                }

                isInLabel = false;
            } else if ("value".equals(localname)) {
                // xforms:value
                isInValue = false;
            } else if ("choices".equals(localname)) {
                // xforms:choices

                // Only count the hierarchy if there was a label
                final Object choicesLabel = choicesStack.pop();
                if (choicesLabel != null)
                    hierarchyLevel--;
            }
        } else {
            super.endElement(uri, localname, qName);
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final XFormsSelect1Control xformsSelect1Control = (XFormsSelect1Control) (handlerContext.isGenerateTemplate()
                ? null : (XFormsControl) containingDocument.getObjectById(pipelineContext, effectiveId));

        final boolean isMany = localname.equals("select");

        QName appearance;
        {
            final QName tempAppearance = getAppearance(elementAttributes);
            if (tempAppearance != null)
                appearance = tempAppearance;
            else if (isMany)
                appearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;// default for xforms:select
            else
                appearance = XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME;// default for xforms:select1
        }

        boolean isOpenSelection = "open".equals(elementAttributes.getValue("selection"));
        boolean isAutocomplete = isOpenSelection
                && XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME.equals(appearance);

        // NOTE: We don't support autocompletion with xforms:select for now, only with xforms:select1
        if (isAutocomplete && isMany) {
            appearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;
            isOpenSelection = false;
            isAutocomplete = false;
        }

        final boolean isFull = XFormsConstants.XFORMS_FULL_APPEARANCE_QNAME.equals(appearance);
        final boolean isCompact = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME.equals(appearance);
        final boolean isTree = XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME.equals(appearance);
        final boolean isMenu = XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME.equals(appearance);

        final boolean isAutocompleteNoFilter = isAutocomplete && "false".equals(elementAttributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "filter"));

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", xformsSelect1Control);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsSelect1Control);

            if (isOpenSelection)
                classes.append(" xforms-select1-open");
            if (isAutocompleteNoFilter)
                classes.append(" xforms-select1-open-autocomplete-nofilter");

            if (isTree)
                classes.append(" xforms-initially-hidden");

            if (!handlerContext.isGenerateTemplate()) {
                handleMIPClasses(classes, xformsSelect1Control);
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        if (!isStaticReadonly(xformsSelect1Control)) {
            if (isFull) {
                final String fullItemType = isMany ? "checkbox" : "radio";
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                {
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);

                    int itemIndex = 0;
                    for (Iterator i = items.iterator(); i.hasNext(); itemIndex++) {
                        final XFormsSelect1Control.Item item = (XFormsSelect1Control.Item) i.next();
                        handleItemFull(contentHandler, xhtmlPrefix, spanQName, xformsSelect1Control, id, effectiveId, isMany, fullItemType, item, Integer.toString(itemIndex), itemIndex == 0);
                    }

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }

                if (hasItemset) {
                    // Produce template
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "xforms-select-template-" + effectiveId);
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select-template");

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    handleItemFull(contentHandler, xhtmlPrefix, spanQName, null, id, effectiveId, isMany, fullItemType, new XFormsSelect1Control.Item(true, getAttributes(itemsetAttributes, null, null), "$xforms-template-label$", "$xforms-template-value$", 1), "$xforms-item-index$", true);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);

                    // TODO: in the future we should be able to handle multiple itemsets
                }
            } else {

                if (isOpenSelection) {

                    if (isAutocomplete) {

                        // Create xhtml:span
                        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);

                        {
                            {
                                // Create xhtml:input
                                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

                                reusableAttributes.clear();
                                reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "text");
                                reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, "xforms-select1-open-input-" + effectiveId);
                                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select1-open-input");
                                reusableAttributes.addAttribute("", "autocomplete", "autocomplete", ContentHandlerHelper.CDATA, "off");

                                final String value = (xformsSelect1Control == null) ? null : xformsSelect1Control.getValue();
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, (value == null) ? "" : value);
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
                                handleAccessibilityAttributes(elementAttributes, reusableAttributes);

                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, reusableAttributes);

                                final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                                handleItemCompact(contentHandler, optionQName, xformsSelect1Control, isMany, new XFormsSelect1Control.Item(false, new AttributesImpl(), "", "", 1));
                                for (Iterator i = items.iterator(); i.hasNext();) {
                                    final XFormsSelect1Control.Item item = (XFormsSelect1Control.Item) i.next();
                                    if (item.getValue() != null)
                                        handleItemCompact(contentHandler, optionQName, xformsSelect1Control, isMany, item);
                                }

                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName);
                            }
                        }

                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                    } else {
                        // We do not support other appearances or regular open selection for now
                        throw new OXFException("Open selection currently only supports the xxforms:autocomplete appearance.");
                        // TODO: Use ValidationException.
                    }

                } else if (isTree) {
                    // xxforms:tree appearance

                    // Create xhtml:div with tree info
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");

                    handleReadOnlyAttribute(newAttributes, xformsSelect1Control);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);

                    outputJSONTreeInfo(xformsSelect1Control, isMany, contentHandler);

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else if (isMenu) {
                    // xxforms:menu appearance

                    // Create enclosing xhtml:div
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
                    final String ulQName = XMLUtils.buildQName(xhtmlPrefix, "ul");
                    final String liQName = XMLUtils.buildQName(xhtmlPrefix, "li");
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");

                    handleReadOnlyAttribute(newAttributes, xformsSelect1Control);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);

                    // Create xhtml:div with initial menu entries
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "yuimenubar");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                    {
                        reusableAttributes.clear();
                        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "bd");
                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                        {
                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "first");

                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "ul", ulQName, reusableAttributes);
                            {
                                reusableAttributes.clear();

                                int index = 0;
                                for (Iterator j = items.iterator(); j.hasNext(); index++) {
                                    final XFormsSelect1Control.Item currentItem = (XFormsSelect1Control.Item) j.next();

                                    // Only care about item at level 1
                                    if (currentItem.getLevel() != 1)
                                        continue;

                                    reusableAttributes.clear();
                                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA,
                                            (index == 0) ? "yuimenubaritem first" : "yuimenubaritem");

                                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "li", liQName, reusableAttributes);

                                    {
                                        reusableAttributes.clear();
                                        reusableAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "#");
                                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, reusableAttributes);
                                        final String text = currentItem.getLabel();
                                        contentHandler.characters(text.toCharArray(), 0, text.length());
                                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);
                                    }

                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "li", liQName);
                                }
                            }
                            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "ul", ulQName);
                        }
                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
                    }
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                    // Create xhtml:div with tree info
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-initially-hidden");

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                    outputJSONTreeInfo(xformsSelect1Control, isMany, contentHandler);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else {
                    // Create xhtml:select
                    final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");

                    if (isCompact)
                        newAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                    // Handle accessibility attributes
                    handleAccessibilityAttributes(elementAttributes, newAttributes);

                    handleReadOnlyAttribute(newAttributes, xformsSelect1Control);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, newAttributes);

                    final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                    final String optGroupQName = XMLUtils.buildQName(xhtmlPrefix, "optgroup");

                    int level = 0;
                    for (Iterator j = items.iterator(); j.hasNext();) {
                        final XFormsSelect1Control.Item currentItem = (XFormsSelect1Control.Item) j.next();
                        final String value = currentItem.getValue();

                        final int newLevel = currentItem.getLevel();

                        if (level - newLevel > 0) {
                            //  We are going down one or more levels
                            for (int i = newLevel; i < level; i++) {
                                // End xhtml:optgroup
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName);
                            }
                        }

                        if (value == null) {
                            // Starting a new group

                            final String label = currentItem.getLabel();
                            final AttributesImpl optGroupAttributes = getAttributes(currentItem.getAttributes(), null, null);
                            if (label != null)
                                optGroupAttributes.addAttribute("", "label", "label", ContentHandlerHelper.CDATA, label);

                            // Start xhtml:optgroup
                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName, optGroupAttributes);
                        } else {
                            // Adding a new item
                            handleItemCompact(contentHandler, optionQName, xformsSelect1Control, isMany, currentItem);
                        }

                        level = newLevel;
                    }

                    // Close brackets
                    for (int i = level; i > 1; i--) {
                        // End xhtml:optgroup
                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName);
                    }

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName);
                }
            }
        } else {
            // Read-only mode

            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
            if (!handlerContext.isGenerateTemplate()) {
                final String value = (xformsSelect1Control.getValue() == null) ? "" : xformsSelect1Control.getValue();
                final StringBuffer sb = new StringBuffer();
                int selectedFound = 0;
                for (Iterator i = items.iterator(); i.hasNext();) {
                    final XFormsSelect1Control.Item currentItem = (XFormsSelect1Control.Item) i.next();
                    if (isSelected(isMany, value, currentItem.getValue())) {
                        if (selectedFound > 0)
                            sb.append(" - ");
                        sb.append(currentItem.getLabel());
                        selectedFound++;
                    }
                }

                if (sb.length() > 0) {
                    final String result = sb.toString();
                    contentHandler.characters(result.toCharArray(), 0, result.length());
                }
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsSelect1Control);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", xformsSelect1Control);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsSelect1Control);
    }

    private void outputJSONTreeInfo(XFormsControl XFormsControl, boolean many, ContentHandler contentHandler) throws SAXException {
        if (!handlerContext.isGenerateTemplate()) {
            // Produce a JSON fragment with hierachical information
            if (items.size() > 0) { // may be null when there is no item in the itemset
                final String controlValue = XFormsControl.getValue();
                final StringBuffer sb = new StringBuffer();

                sb.append("[");

                int level = 0;
                for (Iterator j = items.iterator(); j.hasNext();) {
                    final XFormsSelect1Control.Item currentItem = (XFormsSelect1Control.Item) j.next();
                    final String label = currentItem.getLabel();
                    final String value = currentItem.getValue();

                    final int newLevel = currentItem.getLevel();

                    if (level - newLevel >= 0) {
                        //  We are going down one or more levels
                        for (int i = newLevel; i <= level; i++) {
                            sb.append("]");
                        }
                        sb.append(",[");
                    } else {
                        // We are going up one level
                        if (level > 0)
                            sb.append(",");

                        sb.append("[");
                    }

                    sb.append('"');
                    sb.append(label);
                    sb.append("\",\"");
                    if (value != null)
                        sb.append(value);
                    sb.append("\",");
                    sb.append((value != null) && isSelected(many, controlValue, value));

                    level = newLevel;
                }

                // Close brackets
                for (int i = level; i >= 0; i--) {
                    sb.append("]");
                }

                final String result = sb.toString();
                contentHandler.characters(result.toCharArray(), 0, result.length());
            }
        } else {
            // Don't produce any content when generating a template
        }
    }

    private void handleItemFull(ContentHandler contentHandler, String xhtmlPrefix, String spanQName,
                                XFormsControl XFormsControl, String id, String effectiveId, boolean isMany, String type, XFormsSelect1Control.Item item, String itemIndex, boolean isFirst) throws SAXException {

        // Create an id for the item (trying to make this unique)
        final String itemEffectiveId = id + "-opsitem" + itemIndex + handlerContext.getIdPostfix();

        // xhtml:span
        final Attributes spanAttributes = getAttributes(item.getAttributes(), null, null);
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, spanAttributes);

        // xhtml:input
        {
            final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, itemEffectiveId);
            reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, type);
            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);// TODO: may have duplicate ids for itemsets
            reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getValue());

            if (!handlerContext.isGenerateTemplate() && XFormsControl != null) {
                final String itemValue = ((item.getValue() == null) ? "" : item.getValue()).trim();
                final String controlValue = ((XFormsControl.getValue() == null) ? "" : XFormsControl.getValue()).trim();


                if (isSelected(isMany, controlValue, itemValue)) {
                    reusableAttributes.addAttribute("", "checked", "checked", ContentHandlerHelper.CDATA, "checked");
                }

                if (isFirst) {
                    // Handle accessibility attributes
                    handleAccessibilityAttributes(elementAttributes, reusableAttributes);
                }
            }

            handleReadOnlyAttribute(reusableAttributes, XFormsControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);

            // We don't output the label within <input></input>, because XHTML won't display it.
            final AttributesImpl itemAttributes;
            {
                if (item.getAttributes() != null) {
                    itemAttributes = new AttributesImpl(item.getAttributes());
                } else {
                    reusableAttributes.clear();
                    itemAttributes = reusableAttributes;
                }
            }

            final String label = item.getLabel();
            outputLabelHintHelpAlert(handlerContext, itemAttributes, itemEffectiveId, label);
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private void handleItemCompact(ContentHandler contentHandler, String optionQName, XFormsControl XFormsControl,
                                   boolean isMany, XFormsSelect1Control.Item item) throws SAXException {

        final String optionValue = item.getValue();
        final AttributesImpl optionAttributes = getAttributes(item.getAttributes(), null, null);

        optionAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, optionValue);

        // Figure out whether what items are selected
        if (!handlerContext.isGenerateTemplate() && XFormsControl != null) {
            final String controlValue = XFormsControl.getValue();
            final boolean selected = (controlValue != null) && isSelected(isMany, controlValue, optionValue);
            if (selected)
                optionAttributes.addAttribute("", "selected", "selected", ContentHandlerHelper.CDATA, "selected");
        }

        // xhtml:option
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName, optionAttributes);
        final String label = item.getLabel();
        if (label != null)
            contentHandler.characters(label.toCharArray(), 0, label.length());
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "option", optionQName);
    }

    private boolean isSelected(boolean isMany, String controlValue, String itemValue) {
        boolean selected = false;
        if (isMany) {
            if ("".equals(controlValue)) {
                // Special case of empty string: check the item that has empty string if any
                if ("".equals(itemValue)) {
                    selected = true;
                }
            } else {
                // Case of multiple tokens
                for (final StringTokenizer st = new StringTokenizer(controlValue); st.hasMoreTokens();) {
                    final String token = st.nextToken();
                    if (token.equals(itemValue)) {
                        selected = true;
                        break;
                    }
                }
            }
        } else {
            selected = controlValue.equals(itemValue);
        }
        return selected;
    }
}
