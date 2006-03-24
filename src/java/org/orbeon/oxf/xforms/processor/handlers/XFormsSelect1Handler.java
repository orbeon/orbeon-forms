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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.controls.Select1ControlInfo;
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.common.OXFException;
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
    private Attributes itemAttributes;
    private StringBuffer labelStringBuffer;
    private StringBuffer valueStringBuffer;
    private boolean hasItemset;
    private Attributes itemsetAttributes;

    private boolean isInItem;
    private boolean isInLabel;
    private boolean isInValue;


    public XFormsSelect1Handler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        effectiveId = handlerContext.getEffectiveId(elementAttributes);

        // Reset state, as this handler is reused
        if (items != null)
            items.clear();
        else
            items = new ArrayList();
        hasItemset = false;

        super.start(uri, localname, qName, attributes);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            if ("item".equals(localname)) {
                // xforms:item
                itemAttributes = new AttributesImpl(attributes);

                if (labelStringBuffer != null)
                    labelStringBuffer.setLength(0);
                else
                    labelStringBuffer = new StringBuffer();

                if (valueStringBuffer != null)
                    valueStringBuffer.setLength(0);
                else
                    valueStringBuffer = new StringBuffer();

                isInItem = true;

            } else if ("itemset".equals(localname)) {
                // xforms:itemset
                itemsetAttributes = new AttributesImpl(attributes);
                hasItemset = true; // TODO: in the future we should be able to handle multiple itemsets

                if (!handlerContext.isGenerateTemplate()) {

                    final Select1ControlInfo controlInfo
                            = (Select1ControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);

                    final List itemsetInfos = (List) controlInfo.getItemset();
                    if (itemsetInfos != null) { // may be null when there is no item in the itemset
                        for (Iterator j = itemsetInfos.iterator(); j.hasNext();) {
                            final XFormsControls.ItemsetInfo itemsetInfo = (XFormsControls.ItemsetInfo) j.next();
                            items.add(new Item(true, itemsetAttributes, itemsetInfo.getLabel(), itemsetInfo.getValue()));
                        }
                    }
                }

            } else if (isInItem) {
                if ("label".equals(localname)) {
                    // xforms:label
                    isInLabel = true;
                } else if ("value".equals(localname)) {
                    // xforms:value
                    isInValue = true;
                }
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
                items.add(new Item(false, itemAttributes, labelStringBuffer.toString(), valueStringBuffer.toString()));
                isInItem = false;
            } else if ("label".equals(localname)) {
                // xforms:label
                isInLabel = false;
            } else if ("value".equals(localname)) {
                // xforms:value
                isInValue = false;
            }
        }
        super.endElement(uri, localname, qName);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final ControlInfo controlInfo = handlerContext.isGenerateTemplate()
                ? null : (ControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);

        final boolean isMany = localname.equals("select");
        final String type = isMany ? "checkbox" : "radio";

        final String appearanceValue;
        {
            final String appearanceAttribute = elementAttributes.getValue("appearance");
            if (appearanceAttribute != null)
                appearanceValue = appearanceAttribute;
            else if (isMany)
                appearanceValue = "compact";// default for xforms:select
            else
                appearanceValue = "minimal";// default for xforms:select1
        }
        final String appearanceLocalname = XMLUtils.localNameFromQName(appearanceValue);
        final String appearanceURI = uriFromQName(appearanceValue);

        final boolean isFull = "full".equals(appearanceLocalname);
        final boolean isOpenSelection = "open".equals(elementAttributes.getValue("selection"));
        final boolean isAutocomplete = isOpenSelection
                && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI)
                && "autocomplete".equals(appearanceLocalname);

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

            if (!handlerContext.isGenerateTemplate()) {
                handleMIPClasses(classes, controlInfo);
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        if (isFull) {

            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            {
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);

                for (Iterator i = items.iterator(); i.hasNext();) {
                    final Item item = (Item) i.next();
                    handleItemFull(contentHandler, xhtmlPrefix, spanQName, controlInfo, effectiveId, type, item);
                }

                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }

            if (hasItemset) {
                // Produce template
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "xforms-select-template-" + effectiveId);
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select-template");

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                handleItemFull(contentHandler, xhtmlPrefix, spanQName, null, effectiveId, type, new Item(true, itemsetAttributes, "$xforms-template-label$", "$xforms-template-value$"));
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

                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, reusableAttributes);

                            final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                            handleItemCompact(contentHandler, optionQName, controlInfo, isMany, new Item(false, new AttributesImpl(), "", ""));
                            for (Iterator i = items.iterator(); i.hasNext();) {
                                final Item item = (Item) i.next();
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
                
            } else {
                // Create xhtml:select
                final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");

                if ("compact".equals(appearanceLocalname))
                    newAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                handleReadOnlyAttribute(newAttributes, controlInfo);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, newAttributes);

                final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                for (Iterator i = items.iterator(); i.hasNext();) {
                    final Item item = (Item) i.next();
                    handleItemCompact(contentHandler, optionQName, controlInfo, isMany, item);
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

    private void handleItemFull(ContentHandler contentHandler, String xhtmlPrefix, String spanQName,
                                ControlInfo controlInfo, String effectiveId, String type, Item item) throws SAXException {

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

            final String value = item.getValue();
            if (!handlerContext.isGenerateTemplate() && controlInfo != null && controlInfo.getValue() != null) {
                for (final StringTokenizer st = new StringTokenizer(controlInfo.getValue()); st.hasMoreTokens();) {
                    final String token = st.nextToken();
                    if (token.equals(value)) {
                        reusableAttributes.addAttribute("", "checked", "checked", ContentHandlerHelper.CDATA, "checked");
                        break;
                    }
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
                                   boolean isMany, Item item) throws SAXException {

        final AttributesImpl optionAttributes = getAttributes(item.getAttributes(), null, null);
        optionAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getValue());

        // Figure out whether what items are selected
        final String value = item.getValue();
        if (!handlerContext.isGenerateTemplate() && controlInfo != null) {

            boolean selected = false;
            {
                final String controlValue = controlInfo.getValue();
                if (controlValue != null) {
                    if (isMany) {
                        for (final StringTokenizer st = new StringTokenizer(controlValue); st.hasMoreTokens();) {
                            final String token = st.nextToken();
                            if (token.equals(value)) {
                                selected = true;
                                break;
                            }
                        }
                    } else {
                        selected = controlValue.equals(value);
                    }
                }
            }
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

    private static class Item {

        private boolean isItemSet;
        private Attributes attributes;
        private String label;
        private String value;

        public Item(boolean isItemSet, Attributes attributes, String label, String value) {
            this.isItemSet = isItemSet;
            this.attributes = attributes;
            this.label = label;
            this.value = value;
        }

        public boolean isItemSet() {
            return isItemSet;
        }

        public Attributes getAttributes() {
            return attributes;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }
    }
}
