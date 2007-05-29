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
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
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

    private boolean hasItem;
    private boolean hasNonStaticItem;
    private boolean hasItemset;

    public XFormsSelect1Handler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        id = handlerContext.getId(elementAttributes);
        effectiveId = handlerContext.getEffectiveId(elementAttributes);

        // Reset state, as this handler is reused
        hasItem = false;
        hasNonStaticItem = false;
        hasItemset = false;

        super.start(uri, localname, qName, attributes);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            if ("itemset".equals(localname)) {
                hasItemset = true;
            } else if ("item".equals(localname)) {
                hasItem = true;
                hasNonStaticItem = true;// TODO: we don't actually test because we need to know the children
            }
        }
        super.startElement(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final XFormsSelect1Control xformsSelect1Control = (XFormsSelect1Control) (handlerContext.isGenerateTemplate()
                ? null : (XFormsControl) containingDocument.getObjectById(pipelineContext, effectiveId));

        final List items = (xformsSelect1Control != null) ? xformsSelect1Control.getItemset(pipelineContext) : null;
        outputContent(localname, xformsSelect1Control, items);
    }

    public void outputContent(String localname, final XFormsValueControl xformsValueControl, List items) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

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
        handleLabelHintHelpAlert(effectiveId, "label", xformsValueControl);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsValueControl, appearance, true); // incremental mode is the default

            if (isOpenSelection)
                classes.append(" xforms-select1-open");
            if (isAutocompleteNoFilter)
                classes.append(" xforms-select1-open-autocomplete-nofilter");

            if (isTree)
                classes.append(" xforms-initially-hidden");

            if (!handlerContext.isGenerateTemplate()) {
                handleMIPClasses(classes, xformsValueControl);
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        if (!isStaticReadonly(xformsValueControl)) {
            if (isFull) {
                final String fullItemType = isMany ? "checkbox" : "radio";
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                {
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);

                    if (items != null) {
                        int itemIndex = 0;
                        for (Iterator i = items.iterator(); i.hasNext(); itemIndex++) {
                            final XFormsSelect1Control.Item item = (XFormsSelect1Control.Item) i.next();
                            handleItemFull(contentHandler, xhtmlPrefix, spanQName, xformsValueControl, id, effectiveId, isMany, fullItemType, item, Integer.toString(itemIndex), itemIndex == 0);
                        }
                    }

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }

                if (hasItemset || hasNonStaticItem || hasItem && xformsValueControl == null) {
                    // Try to produce the template only when needed
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "xforms-select-template-" + effectiveId);
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select-template");

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    handleItemFull(contentHandler, xhtmlPrefix, spanQName, null, id, effectiveId, isMany, fullItemType,
                            new XFormsSelect1Control.Item(true, Collections.EMPTY_LIST,
                                    "$xforms-template-label$", "$xforms-template-value$", 1),
                            "$xforms-item-index$", true);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
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

                                final String value = (xformsValueControl == null) ? null : xformsValueControl.getValue();
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, (value == null) ? "" : value);
                                handleReadOnlyAttribute(reusableAttributes, containingDocument, xformsValueControl);
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
                                handleItemCompact(contentHandler, optionQName, xformsValueControl, isMany, new XFormsSelect1Control.Item(false, Collections.EMPTY_LIST, "", "", 1));
                                if (items != null) {
                                    for (Iterator i = items.iterator(); i.hasNext();) {
                                        final XFormsSelect1Control.Item item = (XFormsSelect1Control.Item) i.next();
                                        if (item.getValue() != null)
                                            handleItemCompact(contentHandler, optionQName, xformsValueControl, isMany, item);
                                    }
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

                    handleReadOnlyAttribute(newAttributes, containingDocument, xformsValueControl);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);
                    outputJSONTreeInfo(xformsValueControl, items, isMany, contentHandler);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else if (isMenu) {
                    // xxforms:menu appearance

                    // Create enclosing xhtml:div
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
                    final String ulQName = XMLUtils.buildQName(xhtmlPrefix, "ul");
                    final String liQName = XMLUtils.buildQName(xhtmlPrefix, "li");
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");

                    handleReadOnlyAttribute(newAttributes, containingDocument, xformsValueControl);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);

                    // Create xhtml:div with initial menu entries
                    {
                        outputTree(contentHandler, items, new TreeListener() {

                            private boolean groupJustStarted = false;

                            public void startLevel(ContentHandler contentHandler, int level) throws SAXException {

                                reusableAttributes.clear();
                                final String className;
                                {
                                    if (level == 1)
                                        className = "yuimenubar";
                                    else
                                        className = "yuimenu";
                                }
                                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, className);
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);

                                reusableAttributes.clear();
                                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "bd");
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);

                                reusableAttributes.clear();
                                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "first-of-type");
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "ul", ulQName, reusableAttributes);

                                groupJustStarted = true;
                            }

                            public void endLevel(ContentHandler contentHandler, int level) throws SAXException {
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "ul", ulQName);
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                                groupJustStarted = false;
                            }

                            public void startItem(ContentHandler contentHandler, XFormsSelect1Control.Item item) throws SAXException {

                                final String className;
                                {
                                    if (item.getLevel() == 1)
                                        className = "yuimenubaritem";
                                    else
                                        className = "yuimenuitem";
                                }
                                reusableAttributes.clear();
                                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, className + (groupJustStarted ? " first-of-type" : ""));
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
                    outputJSONTreeInfo(xformsValueControl, items, isMany, contentHandler);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else {
                    // Create xhtml:select
                    final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");

                    if (isCompact)
                        newAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                    // Handle accessibility attributes
                    handleAccessibilityAttributes(elementAttributes, newAttributes);

                    handleReadOnlyAttribute(newAttributes, containingDocument, xformsValueControl);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, newAttributes);

                    final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                    final String optGroupQName = XMLUtils.buildQName(xhtmlPrefix, "optgroup");

                    if (items != null) {
                        outputTree(contentHandler, items, new TreeListener() {

                            private int optgroupCount = 0;

                            public void startLevel(ContentHandler contentHandler, int level) throws SAXException {
                                // NOP
                            }

                            public void endLevel(ContentHandler contentHandler, int level) throws SAXException {
                                if (optgroupCount-- > 0) {
                                    // End xhtml:optgroup
                                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName);
                                }
                            }

                            public void startItem(ContentHandler contentHandler, XFormsSelect1Control.Item item) throws SAXException {

                                final String label = item.getLabel();
                                final String value = item.getValue();

                                if (value == null) {
                                    final AttributesImpl optGroupAttributes = getAttributes(new AttributesImpl(), null, null);
                                    if (label != null)
                                        optGroupAttributes.addAttribute("", "label", "label", ContentHandlerHelper.CDATA, label);

                                    // Start xhtml:optgroup
                                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "optgroup", optGroupQName, optGroupAttributes);
                                    optgroupCount++;
                                } else {
                                    handleItemCompact(contentHandler, optionQName, xformsValueControl, isMany, item);
                                }
                            }


                            public void endItem(ContentHandler contentHandler) throws SAXException {
                            }
                        });
                    }
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName);
                }
            }
        } else {
            // Read-only mode

            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
            if (!handlerContext.isGenerateTemplate()) {
                final String value = (xformsValueControl == null || xformsValueControl.getValue() == null) ? "" : xformsValueControl.getValue();
                final StringBuffer sb = new StringBuffer();
                if (items != null) {
                    int selectedFound = 0;
                    for (Iterator i = items.iterator(); i.hasNext();) {
                        final XFormsSelect1Control.Item currentItem = (XFormsSelect1Control.Item) i.next();
                        if (XFormsSelect1Control.isSelected(isMany, value, currentItem.getValue())) {
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

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsValueControl);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", xformsValueControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsValueControl);
    }

    private void outputTree(ContentHandler contentHandler, List items, TreeListener listener) throws SAXException {
        if (items.size() > 0) { // may be null when there is no item in the itemset

            int currentLevel = 0;
            int startItemCount = 0;
            for (Iterator j = items.iterator(); j.hasNext();) {
                final XFormsSelect1Control.Item currentItem = (XFormsSelect1Control.Item) j.next();

                final int newLevel = currentItem.getLevel();

                if (newLevel < currentLevel) {
                    //  We are going down one or more levels
                    for (int i = currentLevel; i > newLevel; i--) {
                        listener.endItem(contentHandler);
                        startItemCount--;
                        if (startItemCount < 0) throw new OXFException("Too many endItem() generated.");
                        listener.endLevel(contentHandler, i);
                    }
                    listener.endItem(contentHandler);
                    startItemCount--;
                    if (startItemCount < 0) throw new OXFException("Too many endItem() generated.");
                } else if (newLevel > currentLevel) {
                    // We are going up one or more levels
                    for (int i = currentLevel + 1; i <= newLevel; i++) {
                        listener.startLevel(contentHandler, i);
                    }
                } else {
                    // Same level as previous item
                    listener.endItem(contentHandler);
                    startItemCount--;
                    if (startItemCount < 0) throw new OXFException("Too many endItem() generated.");
                }

                startItemCount++;
                listener.startItem(contentHandler, currentItem);
                currentLevel = newLevel;
            }

            // Make sure we go back down all levels
            for (int i = currentLevel; i > 0; i--) {
                listener.endItem(contentHandler);
                startItemCount--;
                if (startItemCount < 0) throw new OXFException("Too many endItem() generated.");
                listener.endLevel(contentHandler, i);
            }
        }
    }

    private interface TreeListener {
        public void startLevel(ContentHandler contentHandler, int level) throws SAXException;
        public void endLevel(ContentHandler contentHandler, int level) throws SAXException;
        public void startItem(ContentHandler contentHandler, XFormsSelect1Control.Item item) throws SAXException;
        public void endItem(ContentHandler contentHandler) throws SAXException;
    }

    private void outputJSONTreeInfo(XFormsValueControl xformsControl, List items, boolean many, ContentHandler contentHandler) throws SAXException {
        if (xformsControl != null && !handlerContext.isGenerateTemplate()) {
            // Produce a JSON fragment with hierachical information
            final String result = XFormsSelect1Control.getJSONTreeInfo(items, xformsControl.getValue(), many);
            contentHandler.characters(result.toCharArray(), 0, result.length());
        } else {
            // Don't produce any content when generating a template
        }
    }

    private void handleItemFull(ContentHandler contentHandler, String xhtmlPrefix, String spanQName,
                                XFormsValueControl xformsControl, String id, String effectiveId, boolean isMany, String type, XFormsSelect1Control.Item item, String itemIndex, boolean isFirst) throws SAXException {

        // Create an id for the item (trying to make this unique)
        final String itemEffectiveId = id + "-opsitem" + itemIndex + handlerContext.getIdPostfix();

        // xhtml:span
        final Attributes spanAttributes = getAttributes(new AttributesImpl(), null, null);
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, spanAttributes);

        // xhtml:input
        {
            final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, itemEffectiveId);
            reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, type);
            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);// TODO: may have duplicate ids for itemsets
            reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getValue() == null ? "" : item.getValue());

            if (!handlerContext.isGenerateTemplate() && xformsControl != null) {
                final String itemValue = ((item.getValue() == null) ? "" : item.getValue()).trim();
                final String controlValue = ((xformsControl.getValue() == null) ? "" : xformsControl.getValue()).trim();


                if (XFormsSelect1Control.isSelected(isMany, controlValue, itemValue)) {
                    reusableAttributes.addAttribute("", "checked", "checked", ContentHandlerHelper.CDATA, "checked");
                }

                if (isFirst) {
                    // Handle accessibility attributes
                    handleAccessibilityAttributes(elementAttributes, reusableAttributes);
                }
            }

            handleReadOnlyAttribute(reusableAttributes, containingDocument, xformsControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);

            // We don't output the label within <input></input>, because XHTML won't display it.

            final String label = item.getLabel();
            reusableAttributes.clear();
            outputLabelFor(handlerContext, reusableAttributes, itemEffectiveId, label);
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private void handleItemCompact(ContentHandler contentHandler, String optionQName, XFormsValueControl xformsControl,
                                   boolean isMany, XFormsSelect1Control.Item item) throws SAXException {

        final String optionValue = item.getValue();
        final AttributesImpl optionAttributes = getAttributes(new AttributesImpl(), null, null);

        optionAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, optionValue == null ? "" : optionValue);

        // Figure out whether what items are selected
        if (!handlerContext.isGenerateTemplate() && xformsControl != null) {
            final String controlValue = xformsControl.getValue();
            final boolean selected = (controlValue != null) && XFormsSelect1Control.isSelected(isMany, controlValue, optionValue);
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
}
