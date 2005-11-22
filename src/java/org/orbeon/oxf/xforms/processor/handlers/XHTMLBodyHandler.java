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

import org.dom4j.Element;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsServerSessionCache;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.processor.NewXFormsServer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

/**
 * Handle xhtml:body.
 */
public class XHTMLBodyHandler extends HandlerBase {

    private static final String SESSION_STATE_PREFIX = "session:";

    private ContentHandlerHelper helper;
    private NewXFormsServer.XFormsState xformsState;

    private String currentSwitchId;

    private boolean justInGroupOrCase;
    private String groupOrCaseId;
    private boolean inGroupOrCaseLabel;

    public XHTMLBodyHandler(HandlerContext handlerContext) {
        super(handlerContext, false);
        xformsState = handlerContext.getXFormsState();

        addElementHandler(new XFormsInputHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "input");
        addElementHandler(new XFormsOutputHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "output");
        addElementHandler(new XFormsTriggerHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger");
        addElementHandler(new XFormsSubmitHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "submit");
        addElementHandler(new XFormsSecretHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "secret");
        addElementHandler(new XFormsTextareaHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "textarea");
        addElementHandler(new XFormsUploadHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "upload");
        addElementHandler(new XFormsRangeHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "range");
        addElementHandler(new XFormsSelectHandler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "select");
        addElementHandler(new XFormsSelect1Handler(handlerContext), XFormsConstants.XFORMS_NAMESPACE_URI, "select1");

        /*

                xforms:repeat
        */

        setDoForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getOutput();
        contentHandler.startElement(uri, localname, qName, attributes);
        helper = new ContentHandlerHelper(contentHandler);

        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        final String prefix = XMLUtils.prefixFromQName(qName);

        final boolean hasUpload = false;// TODO

        // Create xhtml:form
        helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "form", new String[]{
                "id", "xforms-form", "class", "xforms-form",
                "action", "/xforms-server-submit", "method", "POST", "onsubmit", "return false",
                hasUpload ? "enctype" : null, hasUpload ? "multipart/form-data" : null});

        // Store private information used by the client-side JavaScript
        final String currentPageGenerationId = UUIDUtils.createPseudoUUID();
        {
            final String staticStateString;
            if (containingDocument.getStateHandling().equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE)) {
                // Produce static state key, in fact a page generation id
                staticStateString = SESSION_STATE_PREFIX + currentPageGenerationId;
            } else {
                // Produce encoded static state
                staticStateString = xformsState.getStaticState();
            }

            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$static-state", "value", staticStateString
            });
        }

        {
            final String dynamicStateString;
            {
                if (containingDocument.getStateHandling().equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE))
                {
                    // Produce dynamic state key
                    final String newRequestId = UUIDUtils.createPseudoUUID();
                    final XFormsServerSessionCache sessionCache = XFormsServerSessionCache.instance(externalContext.getSession(true), true);
                    sessionCache.add(currentPageGenerationId, newRequestId, xformsState);
                    dynamicStateString = SESSION_STATE_PREFIX + newRequestId;
                } else {
                    // Send state to the client
                    dynamicStateString = xformsState.getDynamicState();
                }
            }
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$dynamic-state", "value", dynamicStateString
            });
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$temp-dynamic-state", "value", ""
            });
        }

        // Store information about nested repeats hierarchy
        {
            final StringBuffer repeatHierarchyStringBuffer = new StringBuffer();
            xformsControls.visitAllControlStatic(new XFormsControls.ControlElementVisitorListener() {

                private Stack ancestorRepeatIds;

                public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                    if (controlElement.getName().equals("repeat")) {
                        final String repeatId = controlElement.attributeValue("id");

                        if (repeatHierarchyStringBuffer.length() > 0)
                            repeatHierarchyStringBuffer.append(',');

                        repeatHierarchyStringBuffer.append(repeatId);

                        if (ancestorRepeatIds != null && ancestorRepeatIds.size() > 0) {
                            repeatHierarchyStringBuffer.append(' ');
                            repeatHierarchyStringBuffer.append(ancestorRepeatIds.peek());
                        }

                        if (ancestorRepeatIds == null)
                            ancestorRepeatIds = new Stack();

                        ancestorRepeatIds.push(repeatId);
                    }
                    return true;
                }

                public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                    if (controlElement.getName().equals("repeat")) {
                        ancestorRepeatIds.pop();
                    }
                    return true;
                }

                public void startRepeatIteration(int iteration) {
                }

                public void endRepeatIteration(int iteration) {
                }
            });

            helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "span", new String[]{
                    "id", "xforms-repeat-tree"
            });
            helper.text(repeatHierarchyStringBuffer.toString());
            helper.endElement();
        }

        // Store information about the initial index of each repeat
        {
            final StringBuffer repeatIndexesStringBuffer = new StringBuffer();
            final Map repeatIdToIndex = xformsControls.getCurrentControlsState().getRepeatIdToIndex();
            if (repeatIdToIndex != null && repeatIdToIndex.size() != 0) {
                for (Iterator i = repeatIdToIndex.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String repeatId = (String) currentEntry.getKey();
                    final Integer index = (Integer) currentEntry.getValue();

                    if (repeatIndexesStringBuffer.length() > 0)
                        repeatIndexesStringBuffer.append(',');

                    repeatIndexesStringBuffer.append(repeatId);
                    repeatIndexesStringBuffer.append(' ');
                    repeatIndexesStringBuffer.append(index);
                }
            }

            helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "span", new String[]{
                    "id", "xforms-repeat-indexes"
            });
            helper.text(repeatIndexesStringBuffer.toString());
            helper.endElement();
        }

        // Ajax loading icon
        if (XFormsUtils.isAjaxShowLoadingIcon()) {
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "span", new String[]{"class", "xforms-loading-loading"});
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "span", new String[]{"class", "xforms-loading-none"});
        }

        // Ajax errors
        if (XFormsUtils.isAjaxShowErrors()) {
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "span", new String[]{"class", "xforms-loading-error"});
        }
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            if (localname.equals("group") || localname.equals("switch")) {
                // xforms:group and xforms:switch

                final String effectiveId = handlerContext.getEffectiveId(attributes);
                currentSwitchId = effectiveId;

                final XFormsControls.ControlInfo controlInfo = ((XFormsControls.ControlInfo) containingDocument.getObjectById(handlerContext.getPipelineContext(), effectiveId));

                // Find classes to add
                final StringBuffer classes = new StringBuffer("xforms-" + localname);
                if (!handlerContext.isGenerateTemplate()) {
                    HandlerBase.handleReadOnlyClass(classes, controlInfo);
                    HandlerBase.handleRelevantClass(classes, controlInfo);
                }

                // Create xhtml:span
                final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                handlerContext.getOutput().startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, getAttributes(attributes, classes.toString(), effectiveId));

                justInGroupOrCase = localname.equals("group");
                groupOrCaseId = attributes.getValue("id");

            } else if (localname.equals("case")) {
                // xforms:case

                final String effectiveId = handlerContext.getEffectiveId(attributes);

                // Find classes to add
                final StringBuffer classes = new StringBuffer("xforms-" + localname);

                final AttributesImpl newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

                final Map switchIdToSelectedCaseIdMap = containingDocument.getXFormsControls().getCurrentControlsState().getSwitchIdToSelectedCaseIdMap();

                final String selectedCaseId = (String) switchIdToSelectedCaseIdMap.get(currentSwitchId);
                final boolean isVisible = effectiveId.equals(selectedCaseId);
                newAttributes.addAttribute("", "style", "style", ContentHandlerHelper.CDATA, "display: " + (isVisible ? "block" : "none"));

                final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                handlerContext.getOutput().startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);

                justInGroupOrCase = true;
                groupOrCaseId = attributes.getValue("id");

            } else if (justInGroupOrCase && localname.equals("label")) {
                // xforms:case or xforms:group label

                final XFormsControls.ControlInfo controlInfo = handlerContext.isGenerateTemplate()
                    ? null : (XFormsControls.ControlInfo) containingDocument.getObjectById(pipelineContext, groupOrCaseId);
                final String labelValue = handlerContext.isGenerateTemplate() ? null : controlInfo.getLabel();

                final AttributesImpl labelAttributes = getAttributes(attributes, "xforms-label", null);
                XFormsValueControlHandler.outputLabelHintHelpAlert(handlerContext, labelAttributes, groupOrCaseId, labelValue);

                inGroupOrCaseLabel = true;

            } else {
                super.startElement(uri, localname, qName, attributes);

                justInGroupOrCase = false;
            }
        } else {
            super.startElement(uri, localname, qName, attributes);

            justInGroupOrCase = false;
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            if (localname.equals("group") || localname.equals("switch")) {
                // xforms:group and xforms:switch

                // Close xhtml:span
                final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                handlerContext.getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);

            } else if (localname.equals("case")) {
                // xforms:case

                final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                handlerContext.getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);

            } else if (justInGroupOrCase && localname.equals("label")) {
                // xforms:case or xforms:group label

                inGroupOrCaseLabel = false;

            } else {
                super.endElement(uri, localname, qName);
            }
        } else {
            super.endElement(uri, localname, qName);
        }
        justInGroupOrCase = false;
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (!inGroupOrCaseLabel)
            super.characters(chars, start, length);
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        // Close xhtml:form
        helper.endElement();

        final ContentHandler contentHandler = handlerContext.getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
