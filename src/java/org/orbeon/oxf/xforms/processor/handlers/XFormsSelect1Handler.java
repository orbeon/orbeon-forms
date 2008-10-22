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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsItemUtils;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Handle xforms:select and xforms:select1.
 */
public class XFormsSelect1Handler extends XFormsControlLifecyleHandler {

    private boolean isMany;
    private QName appearance;
    private boolean isOpenSelection;
    private boolean isAutocomplete;
    private boolean isAutocompleteNoFilter;
    private boolean isFull;
    private boolean isCompact;
    private boolean isTree;
    private boolean isMenu;

    public XFormsSelect1Handler() {
        super(false);
    }

    protected void prepareHandler(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) {
        this.isMany = localname.equals("select");
        this.appearance = getAppearance(attributes);
        this.isOpenSelection = "open".equals(attributes.getValue("selection"));
        this.isAutocomplete = isOpenSelection
                && XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME.equals(appearance);

        // NOTE: We don't support autocompletion with xforms:select for now, only with xforms:select1
        if (isAutocomplete && isMany) {
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

    protected void addCustomClasses(FastStringBuffer classes, XFormsSingleNodeControl xformsControl) {
        if (isOpenSelection)
            classes.append(" xforms-select1-open");
        if (isAutocompleteNoFilter)
            classes.append(" xforms-select1-open-autocomplete-nofilter");
        if (isTree)
            classes.append(" xforms-initially-hidden");
    }

    protected boolean isDefaultIncremental() {
        // Incremental mode is the default
        return true;
    }

    protected QName getAppearance(Attributes attributes) {
        final QName tempAppearance = super.getAppearance(attributes);

        final QName appearance;
        if (tempAppearance != null)
            appearance = tempAppearance;
        else if (isMany)
            appearance = XFormsConstants.XFORMS_COMPACT_APPEARANCE_QNAME;// default for xforms:select
        else
            appearance = XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME;// default for xforms:select1

        return appearance;
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {
        // Get items, dynamic or static, if possible
        final XFormsSelect1Control xformsSelect1Control = (XFormsSelect1Control) xformsControl;
        final List items = XFormsSelect1Control.getItemset(pipelineContext, containingDocument, xformsSelect1Control, getPrefixedId());

        outputContent(attributes, id, effectiveId, uri, localname, xformsSelect1Control, items, isMany, isFull);
    }

    protected void handleLabel(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        final boolean isFull = XFormsConstants.XFORMS_FULL_APPEARANCE_QNAME.equals(appearance);
        if (isStaticReadonly(xformsControl) || !isFull || !handlerContext.isNoScript())
            super.handleLabel(staticId, effectiveId, xformsControl, isTemplate);
    }

    public void outputContent(Attributes attributes, String staticId, String effectiveId, String uri, String localname, final XFormsValueControl xformsControl, List items, final boolean isMany, final boolean isFull) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final AttributesImpl newAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            reusableAttributes.clear();
            newAttributes = reusableAttributes;
        } else {
            final FastStringBuffer classes = getInitialClasses(uri, localname, attributes, xformsControl);
            addCustomClasses(classes, xformsControl);
            handleMIPClasses(classes, getPrefixedId(), xformsControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);
        }

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        if (!isStaticReadonly(xformsControl)) {
            if (isFull) {
                final String fullItemType = isMany ? "checkbox" : "radio";

                // In noscript mode, use <fieldset>

                // TODO: This really hasn't much to do with noscript; should we always use fieldset, or make this an
                // option? Benefit of limiting to noscript is that then no JS change is needed
                final String elementName = handlerContext.isNoScript() ? "fieldset" : "span";

                final String elementQName = XMLUtils.buildQName(xhtmlPrefix, elementName);
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                {
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName, newAttributes);

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

                    if (items != null) {
                        int itemIndex = 0;
                        for (Iterator i = items.iterator(); i.hasNext(); itemIndex++) {
                            final XFormsItemUtils.Item item = (XFormsItemUtils.Item) i.next();
                            handleItemFull(contentHandler, attributes, xhtmlPrefix, spanQName, xformsControl, staticId, effectiveId, isMany, fullItemType, item, Integer.toString(itemIndex), itemIndex == 0);
                        }
                    }

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName);
                }

                // Try to produce the template only when needed
                if (!handlerContext.isNoScript()) {// don't generate templates in noscript mode as they won't be used
                    final XFormsStaticState.ItemsInfo itemsInfo = containingDocument.getStaticState().getItemsInfo(getPrefixedId());
                    if (itemsInfo == null || itemsInfo.hasNonStaticItem()) {// only generate if there are non-static items
                        reusableAttributes.clear();
                        reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "xforms-select-template-" + effectiveId);
                        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-select-template");

                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                        handleItemFull(contentHandler, attributes, xhtmlPrefix, spanQName, null, staticId, effectiveId, isMany, fullItemType,
                                new XFormsItemUtils.Item(false, Collections.EMPTY_LIST, // make sure the value "$xforms-template-value$" is not encrypted
                                        "$xforms-template-label$", "$xforms-template-value$", 1),
                                        "$xforms-item-index$", true);
                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                    }
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

                                final String value = (xformsControl == null) ? null : xformsControl.getValue(pipelineContext);
                                // NOTE: With open selection, we send all values to the client but not encrypt them because the client matches on values
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, (value == null) ? "" : value);
                                handleReadOnlyAttribute(reusableAttributes, containingDocument, xformsControl);
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
                                handleItemCompact(contentHandler, optionQName, xformsControl, isMany, new XFormsItemUtils.Item(false, Collections.EMPTY_LIST, "", "", 1));
                                if (items != null) {
                                    for (Iterator i = items.iterator(); i.hasNext();) {
                                        final XFormsItemUtils.Item item = (XFormsItemUtils.Item) i.next();
                                        if (item.getValue() != null)
                                            handleItemCompact(contentHandler, optionQName, xformsControl, isMany, item);
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
                                        (xformsControl != null) ? xformsControl.getControlElement() : null));
                    }

                } else if (isTree) {
                    // xxforms:tree appearance

                    // Create xhtml:div with tree info
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");

                    handleReadOnlyAttribute(newAttributes, containingDocument, xformsControl);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);
                    outputJSONTreeInfo(xformsControl, items, isMany, contentHandler);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else if (isMenu) {
                    // xxforms:menu appearance

                    // Create enclosing xhtml:div
                    final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
                    final String ulQName = XMLUtils.buildQName(xhtmlPrefix, "ul");
                    final String liQName = XMLUtils.buildQName(xhtmlPrefix, "li");
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");

                    handleReadOnlyAttribute(newAttributes, containingDocument, xformsControl);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);

                    // Create xhtml:div with initial menu entries
                    {
                        XFormsItemUtils.visitItemsTree(contentHandler, items, handlerContext.getLocationData(), new XFormsItemUtils.TreeListener() {

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

                            public void startItem(ContentHandler contentHandler, XFormsItemUtils.Item item, boolean first) throws SAXException {

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
                    outputJSONTreeInfo(xformsControl, items, isMany, contentHandler);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

                } else {
                    // Create xhtml:select
                    final String selectQName = XMLUtils.buildQName(xhtmlPrefix, "select");
                    newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);// necessary for noscript mode

                    if (isCompact)
                        newAttributes.addAttribute("", "multiple", "multiple", ContentHandlerHelper.CDATA, "multiple");

                    // Handle accessibility attributes
                    handleAccessibilityAttributes(attributes, newAttributes);

                    handleReadOnlyAttribute(newAttributes, containingDocument, xformsControl);
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "select", selectQName, newAttributes);

                    final String optionQName = XMLUtils.buildQName(xhtmlPrefix, "option");
                    final String optGroupQName = XMLUtils.buildQName(xhtmlPrefix, "optgroup");

                    if (items != null) {
                        XFormsItemUtils.visitItemsTree(contentHandler, items, handlerContext.getLocationData(), new XFormsItemUtils.TreeListener() {

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

                            public void startItem(ContentHandler contentHandler, XFormsItemUtils.Item item, boolean first) throws SAXException {

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
                                    handleItemCompact(contentHandler, optionQName, xformsControl, isMany, item);
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
            if (!handlerContext.isTemplate()) {
                final String value = (xformsControl == null || xformsControl.getValue(pipelineContext) == null) ? "" : xformsControl.getValue(pipelineContext);
                final StringBuffer sb = new StringBuffer();
                if (items != null) {
                    int selectedFound = 0;
                    for (Iterator i = items.iterator(); i.hasNext();) {
                        final XFormsItemUtils.Item currentItem = (XFormsItemUtils.Item) i.next();
                        if (XFormsItemUtils.isSelected(isMany, value, currentItem.getValue())) {
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

    private void outputJSONTreeInfo(XFormsValueControl xformsControl, List items, boolean many, ContentHandler contentHandler) throws SAXException {
        if (xformsControl != null && !handlerContext.isTemplate()) {
            // Produce a JSON fragment with hierachical information
            final String result = XFormsItemUtils.getJSONTreeInfo(pipelineContext, items, xformsControl.getValue(pipelineContext), many, handlerContext.getLocationData());
            contentHandler.characters(result.toCharArray(), 0, result.length());
        } else {
            // Don't produce any content when generating a template
        }
    }

    private void handleItemFull(ContentHandler contentHandler, Attributes attributes, String xhtmlPrefix, String spanQName,
                                XFormsValueControl xformsControl, String id, String effectiveId, boolean isMany, String type, XFormsItemUtils.Item item, String itemIndex, boolean isFirst) throws SAXException {

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
            reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getExternalValue(pipelineContext));

            if (!handlerContext.isTemplate() && xformsControl != null) {
                final String itemValue = ((item.getValue() == null) ? "" : item.getValue()).trim();
                final String controlValue = ((xformsControl.getValue(pipelineContext) == null) ? "" : xformsControl.getValue(pipelineContext)).trim();


                if (XFormsItemUtils.isSelected(isMany, controlValue, itemValue)) {
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

            // We don't output the label within <input></input>, because XHTML won't display it.

            final String label = item.getLabel();
            reusableAttributes.clear();
            outputLabelFor(handlerContext, reusableAttributes, itemEffectiveId, null, label, false);// TODO: may be HTML for full appearance
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    private void handleItemCompact(ContentHandler contentHandler, String optionQName, XFormsValueControl xformsControl,
                                   boolean isMany, XFormsItemUtils.Item item) throws SAXException {

        final String optionValue = item.getValue();
        final AttributesImpl optionAttributes = getAttributes(new AttributesImpl(), null, null);

        optionAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, item.getExternalValue(pipelineContext));

        // Figure out whether what items are selected
        if (!handlerContext.isTemplate() && xformsControl != null) {
            final String controlValue = xformsControl.getValue(pipelineContext);
            final boolean selected = (controlValue != null) && XFormsItemUtils.isSelected(isMany, controlValue, optionValue);
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
