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
import org.dom4j.QName;
import org.orbeon.oxf.processor.PageFlowControllerProcessor;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.state.*;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

/**
 * Handle xhtml:body.
 */
public class XHTMLBodyHandler extends HandlerBase {

    private ContentHandlerHelper helper;

//    private String formattingPrefix;

    public XHTMLBodyHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        // Register control handlers on controller
        {
            final ElementHandlerController controller = handlerContext.getController();
            controller.registerHandler(XFormsInputHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "input");
            controller.registerHandler(XFormsOutputHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output");
            controller.registerHandler(XFormsTriggerHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger");
            controller.registerHandler(XFormsSubmitHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "submit");
            controller.registerHandler(XFormsSecretHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "secret");
            controller.registerHandler(XFormsTextareaHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "textarea");
            controller.registerHandler(XFormsUploadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "upload");
            controller.registerHandler(XFormsRangeHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "range");
            controller.registerHandler(XFormsSelectHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select");
            controller.registerHandler(XFormsSelect1Handler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select1");

            controller.registerHandler(XFormsGroupHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group");
            controller.registerHandler(XFormsSwitchHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "switch");
            controller.registerHandler(XFormsCaseHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "case");
            controller.registerHandler(XFormsRepeatHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "repeat");

            controller.registerHandler(XXFormsDialogHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "dialog");
        }

        final XFormsState xformsState = handlerContext.getXFormsState();
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.startElement(uri, localname, qName, attributes);
        helper = new ContentHandlerHelper(contentHandler);

        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        final String htmlPrefix = XMLUtils.prefixFromQName(qName);

        final boolean hasUpload = xformsControls.getCurrentControlsState().isHasUpload();

        // Get formatting prefix and declare it if needed
        // TODO: would be nice to do this here, but then we need to make sure this prefix is available to other handlers
//        formattingPrefix = handlerContext.findFormattingPrefixDeclare();

        // Create xhtml:form
        final String xformsSubmissionPath = OXFProperties.instance().getPropertySet(
                new QName("page-flow", XMLConstants.OXF_PROCESSORS_NAMESPACE)).getString(
                PageFlowControllerProcessor.XFORMS_SUBMISSION_PATH_PROPERTY_NAME,
                PageFlowControllerProcessor.XFORMS_SUBMISSION_PATH_DEFAULT_VALUE);

        helper.startElement(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "form", new String[] {
                "id", XFormsUtils.namespaceId(containingDocument, "xforms-form"), // add id so that things work in portals
                "class", "xforms-form", "action", xformsSubmissionPath, "method", "POST", "onsubmit", "return false",
                hasUpload ? "enctype" : null, hasUpload ? "multipart/form-data" : null});

        // Store private information used by the client-side JavaScript
        {
            // Get encoded state for the client
            final XFormsState encodedClientState = XFormsStateManager.getInitialEncodedClientState(containingDocument, externalContext, xformsState, handlerContext.getStaticStateUUID(), handlerContext.getDynamicStateUUID());

            // Output encoded state
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[] {
                    "type", "hidden", "name", "$static-state", "value", encodedClientState.getStaticState()
            });
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$dynamic-state", "value", encodedClientState.getDynamicState()
            });

            // Other fields used by JavaScript
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$server-events", "value", ""
            });
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$client-state", "value", ""
            });
        }

        // Store information about nested repeats hierarchy
        {
            final StringBuffer repeatHierarchyStringBuffer = new StringBuffer();
            xformsControls.visitAllControlStatic(new XFormsControls.ControlElementVisitorListener() {

                private Stack ancestorRepeatIds;

                public boolean startVisitControl(Element controlElement, String controlId) {
                    if (controlElement.getName().equals("repeat")) {

                        if (repeatHierarchyStringBuffer.length() > 0)
                            repeatHierarchyStringBuffer.append(',');

                        repeatHierarchyStringBuffer.append(controlId);

                        if (ancestorRepeatIds != null && ancestorRepeatIds.size() > 0) {
                            repeatHierarchyStringBuffer.append(' ');
                            repeatHierarchyStringBuffer.append(ancestorRepeatIds.peek());
                        }

                        if (ancestorRepeatIds == null)
                            ancestorRepeatIds = new Stack();

                        ancestorRepeatIds.push(controlId);
                    }
                    return true;
                }

                public boolean endVisitControl(Element controlElement, String controlId) {
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

            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$repeat-tree", "value", repeatHierarchyStringBuffer.toString()
            });
        }

        // Store information about the initial index of each repeat
        {
            final StringBuffer repeatIndexesStringBuffer = new StringBuffer();
            final Map repeatIdToIndex = xformsControls.getCurrentControlsState().getRepeatIdToIndex();
            if (repeatIdToIndex.size() != 0) {
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

            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$repeat-indexes", "value", repeatIndexesStringBuffer.toString()
            });
        }

        // Ajax loading indicator
        if (XFormsProperties.isAjaxShowLoadingIcon(containingDocument)) {

            helper.startElement(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "span", new String[]{ "class", "xforms-loading-loading" });
            helper.text("Loading..."); // text is hardcoded, but you can rewrite it in the theme if needed 
            helper.endElement();

            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "span", new String[]{ "class", "xforms-loading-none" });
        }

        // Ajax error panel
        if (XFormsProperties.isAjaxShowErrors(containingDocument)) {
            // XInclude dialog so users can configure it
            // TODO: must send startPrefixMapping()/endPrefixMapping()?
            helper.element("", XMLConstants.XINCLUDE_URI, "include", new String[] { "href", "oxf:/config/error-dialog.xml" });
        }

        // Help panel
        // TODO: must send startPrefixMapping()/endPrefixMapping()?
        helper.element("", XMLConstants.XINCLUDE_URI, "include", new String[] { "href", "oxf:/config/help-panel.xml" });
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        // Close xhtml:form
        helper.endElement();

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
