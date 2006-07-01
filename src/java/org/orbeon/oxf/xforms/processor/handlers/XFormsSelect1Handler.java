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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.oxf.xforms.controls.Select1ControlInfo;
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

                    final Select1ControlInfo controlInfo
                            = (Select1ControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);

                    final List itemsetInfos = (List) controlInfo.getItemset();
                    if (itemsetInfos != null && itemsetInfos.size() > 0) { // may be null when there is no item in the itemset
                        final Stack nodeStack = new Stack();
                        int level = 0;
                        for (Iterator j = itemsetInfos.iterator(); j.hasNext();) {
                            final XFormsControls.ItemsetInfo currentItemsetInfo = (XFormsControls.ItemsetInfo) j.next();
                            final NodeInfo currentNodeInfo = currentItemsetInfo.getNodeInfo();

                            final int newLevel = Select1ControlInfo.getNodeLevel(currentNodeInfo, nodeStack);
                            if (level - newLevel >= 0) {
                                //  We are going down one or more levels
                                for (int i = newLevel; i <= level; i++) {
                                    nodeStack.pop();
                                }
                            }

                            items.add(new Select1ControlInfo.Item(true, itemsetAttributes, currentItemsetInfo.getLabel(), currentItemsetInfo.getValue(), newLevel));
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
                items.add(new Select1ControlInfo.Item(false, itemChoicesAttributes, labelStringBuffer.toString(), valueStringBuffer.toString(), hierarchyLevel));
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

                    items.add(new Select1ControlInfo.Item(false, itemChoicesAttributes, choicesLabel, null, hierarchyLevel));
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
        final Select1ControlInfo controlInfo = (Select1ControlInfo) (handlerContext.isGenerateTemplate()
                ? null : (ControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId));

        final boolean isMany = localname.equals("select");

        String appearanceValue;
        {
            final String appearanceAttribute = elementAttributes.getValue("appearance");
            if (appearanceAttribute != null)
                appearanceValue = appearanceAttribute;
            else if (isMany)
                appearanceValue = "compact";// default for xforms:select
            else
                appearanceValue = "minimal";// default for xforms:select1
        }
        String appearanceLocalname = XMLUtils.localNameFromQName(appearanceValue);
        String appearanceURI = uriFromQName(appearanceValue);

        boolean isOpenSelection = "open".equals(elementAttributes.getValue("selection"));
        boolean isAutocomplete = isOpenSelection
                && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI)
                && "autocomplete".equals(appearanceLocalname);

        // NOTE: We don't support autocompletion with xforms:select for now, only with xforms:select1
        if (isAutocomplete && isMany) {
            appearanceValue = "compact";
            appearanceLocalname = appearanceValue;
            appearanceURI = "";
            isOpenSelection = false;
            isAutocomplete = false;
        }

        final boolean isFull = "full".equals(appearanceLocalname);
        final boolean isTree = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI) && ("tree".equals(appearanceLocalname));
        final boolean isMenu = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI) && ("menu".equals(appearanceLocalname));

        final boolean isAutocompleteNoFilter = isAutocomplete && "false".equals(elementAttributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "filter"));

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", controlInfo);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = new StringBuffer("xforms-control");
            if (isMany) {
                classes.append(" xforms-select-");
            } else {
                classes.append(" xforms-select1-");
            }
            classes.append(appearanceLocalname);
            if (isOpenSelection)
                classes.append(" xforms-select1-open");
            if (isAutocompleteNoFilter)
                classes.append(" xforms-select1-open-autocomplete-nofilter");

            if (isTree)
                classes.append(" xforms-initially-hidden");

            if (!handlerContext.isGenerateTemplate()) {
                handleMIPClasses(classes, controlInfo);
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        if (isFull) {
            final String fullItemType = isMany ? "checkbox" : "radio";
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            {
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);

                boolean isFirst = true;
                for (Iterator i = items.iterator(); i.hasNext();) {
                    final Select1ControlInfo.Item item = (Select1ControlInfo.Item) i.next();
                    handleItemFull(contentHandler, xhtmlPrefix, spanQName, controlInfo, effectiveId, fullItemType, item, isFirst);
                    isFirst = false;
                }

                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }

            if (hasItemset) {
                // Produce template
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "xforms-select-template-" + effectiveId);
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select-template");

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                handleItemFull(contentHandler, xhtmlPrefix, spanQName, null, effectiveId, fullItemType, new Select1ControlInfo.Item(true, itemsetAttributes, "$xforms-template-label$", "$xforms-template-value$", 1), false);
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

                            final String value = (controlInfo == null) ? null : controlInfo.getValue();
                            reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, (value == null) ? "" : value);
                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);

                            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                        }
                        {
                            // Create xhtml:select
                            final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select1-open-select");

                            if ("compact".equals(appearanceLocalname))
                                reusableAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                            // Handle accessibility attributes
                            handleAccessibilityAttributes(elementAttributes, reusableAttributes);

                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, reusableAttributes);

                            final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                            handleItemCompact(contentHandler, optionQName, controlInfo, isMany, new Select1ControlInfo.Item(false, new AttributesImpl(), "", "", 1));
                            for (Iterator i = items.iterator(); i.hasNext();) {
                                final Select1ControlInfo.Item item = (Select1ControlInfo.Item) i.next();
                                if (item.getValue() != null)
                                    handleItemCompact(contentHandler, optionQName, controlInfo, isMany, item);
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

                handleReadOnlyAttribute(newAttributes, controlInfo);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);

                outputJSONTreeInfo(controlInfo, isMany, contentHandler);

                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

            } else if (isMenu) {
                // xxforms:menu appearance

                // Create enclosing xhtml:div
                final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
                final String ulQName = XMLUtils.buildQName(xhtmlPrefix, "ul");
                final String liQName = XMLUtils.buildQName(xhtmlPrefix, "li");
                final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");

                handleReadOnlyAttribute(newAttributes, controlInfo);
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
                                final Select1ControlInfo.Item currentItem = (Select1ControlInfo.Item) j.next();

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
                outputJSONTreeInfo(controlInfo, isMany, contentHandler);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

            } else {
                // Create xhtml:select
                final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");

                if ("compact".equals(appearanceLocalname))
                    newAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                // Handle accessibility attributes
                handleAccessibilityAttributes(elementAttributes, newAttributes);

                handleReadOnlyAttribute(newAttributes, controlInfo);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, newAttributes);

                final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                final String optGroupQName = XMLUtils.buildQName(xhtmlPrefix, "optgroup");

                int level = 0;
                for (Iterator j = items.iterator(); j.hasNext();) {
                    final Select1ControlInfo.Item currentItem = (Select1ControlInfo.Item) j.next();
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
                        handleItemCompact(contentHandler, optionQName, controlInfo, isMany, currentItem);
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

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", controlInfo);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", controlInfo);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", controlInfo);
    }

    private void outputJSONTreeInfo(ControlInfo controlInfo, boolean many, ContentHandler contentHandler) throws SAXException {
        if (!handlerContext.isGenerateTemplate()) {
            // Produce a JSON fragment with hierachical information
            if (items.size() > 0) { // may be null when there is no item in the itemset
                final String controlValue = controlInfo.getValue();
                final StringBuffer sb = new StringBuffer();

                sb.append("[");

                int level = 0;
                for (Iterator j = items.iterator(); j.hasNext();) {
                    final Select1ControlInfo.Item currentItem = (Select1ControlInfo.Item) j.next();
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
                                ControlInfo controlInfo, String effectiveId, String type, Select1ControlInfo.Item item, boolean isFirst) throws SAXException {

        // xhtml:span

        final Attributes spanAttributes = getAttributes(item.getAttributes(), null, null);
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, spanAttributes);

        // xhtml:input
        {
            final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, type);
            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);// TODO: may have duplicate ids for itemsets
            reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getValue());

            if (!handlerContext.isGenerateTemplate() && controlInfo != null) {
                final String itemValue = ((item.getValue() == null) ? "" : item.getValue()).trim();
                final String controlValue = ((controlInfo.getValue() == null) ? "" : controlInfo.getValue()).trim();

                if ("".equals(controlValue)) {
                    // Special case of empty string: check the item that has empty string if any
                    if ("".equals(itemValue)) {
                        reusableAttributes.addAttribute("", "checked", "checked", ContentHandlerHelper.CDATA, "checked");
                    }
                } else {
                    // Case of multiple tokens
                    for (final StringTokenizer st = new StringTokenizer(controlValue); st.hasMoreTokens();) {
                        final String token = st.nextToken();
                        if (token.equals(itemValue)) {
                            reusableAttributes.addAttribute("", "checked", "checked", ContentHandlerHelper.CDATA, "checked");
                            break;
                        }
                    }
                }

                if (isFirst) {
                    // Handle accessibility attributes
                    handleAccessibilityAttributes(elementAttributes, reusableAttributes);
                }
            }

            handleReadOnlyAttribute(reusableAttributes, controlInfo);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
            final String label = item.getLabel();
            contentHandler.characters(label.toCharArray(), 0, label.length());
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private void handleItemCompact(ContentHandler contentHandler, String optionQName, ControlInfo controlInfo,
                                   boolean isMany, Select1ControlInfo.Item item) throws SAXException {

        final String optionValue = item.getValue();
        final AttributesImpl optionAttributes = getAttributes(item.getAttributes(), null, null);

        optionAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, optionValue);

        // Figure out whether what items are selected
        if (!handlerContext.isGenerateTemplate() && controlInfo != null) {
            final String controlValue = controlInfo.getValue();
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
            for (final StringTokenizer st = new StringTokenizer(controlValue); st.hasMoreTokens();) {
                final String token = st.nextToken();
                if (token.equals(itemValue)) {
                    selected = true;
                    break;
                }
            }
        } else {
            selected = controlValue.equals(itemValue);
        }
        return selected;
    }
}
