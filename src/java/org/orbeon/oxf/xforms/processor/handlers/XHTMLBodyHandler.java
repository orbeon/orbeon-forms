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

import org.apache.commons.lang.StringUtils;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Map;

/**
 * Handle xhtml:body.
 */
public class XHTMLBodyHandler extends XFormsBaseHandler {

    private ContentHandlerHelper helper;

//    private String formattingPrefix;

    public XHTMLBodyHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final XFormsStaticState staticState = containingDocument.getStaticState();

        // Register control handlers on controller
        registerHandlers(handlerContext.getController(), staticState);

        // Add class for YUI skin
        // TODO: should be configurable somehow
        attributes = XMLUtils.appendToClassAttribute(attributes, "yui-skin-sam");

        // Start xhtml:body
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();
        xmlReceiver.startElement(uri, localname, qName, attributes);
        helper = new ContentHandlerHelper(xmlReceiver);

        final XFormsControls xformsControls = containingDocument.getControls();
        final String htmlPrefix = XMLUtils.prefixFromQName(qName);

        // Get formatting prefix and declare it if needed
        // TODO: would be nice to do this here, but then we need to make sure this prefix is available to other handlers
//        formattingPrefix = handlerContext.findFormattingPrefixDeclare();

        final String requestPath;
        final String xformsSubmissionPath;
        {
            final ExternalContext.Request request = handlerContext.getExternalContext().getRequest();
            requestPath = request.getRequestPath();
            if (containingDocument.getDeploymentType() != XFormsConstants.DeploymentType.standalone || request.getContainerType().equals("portlet")) {
                // Integrated or separate deployment mode or portlet
                xformsSubmissionPath =  "/xforms-server-submit";
            } else {
                // Plain deployment mode: submission posts to URL of the current page and xforms-xml-submission.xpl intercepts that
                xformsSubmissionPath = requestPath;
            }
        }

        // Noscript panel is included before the xhtml:form element, in case the form is hidden through CSS
        if (!handlerContext.isNoScript()) {
            helper.element("", XMLConstants.XINCLUDE_URI, "include", new String[] {
                    "href", getIncludedResourcePath(requestPath, "noscript-panel.xml"),
                    "fixup-xml-base", "false"
            });
        }

        // Create xhtml:form element
        final boolean hasUpload = staticState.hasControlByName("upload");
        helper.startElement(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "form", new String[] {
                // Add id so that things work in portals    
                "id", XFormsUtils.getFormId(containingDocument),
                // Regular classes
                "class", "xforms-form" + (handlerContext.isNoScript() ? " xforms-noscript" : " xforms-initially-hidden")
                        + (handlerContext.isSpanHTMLLayout() ? " xforms-layout-span" : " xforms-layout-nospan"),
                // Submission parameters
                "action", xformsSubmissionPath, "method", "POST",
                // In noscript mode, don't add event handler
                "onsubmit", handlerContext.isNoScript() ? null : "return false",
                hasUpload ? "enctype" : null, hasUpload ? "multipart/form-data" : null});

        {
            // Output encoded static and dynamic state
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[] {
                    "type", "hidden", "name", "$uuid", "value", containingDocument.getUUID()
            });
            // NOTE: we don't need $sequence here as HTML form posts are either:
            //
            // o 2nd phase of replace="all" submission: we don't (and can't) retry
            // o background upload: we don't want a sequence number as this run in parallel
            // o noscript mode: we don't (and can't) retry
            //
            // NOTE: Keep empty static state and dynamic state until client is able to deal without them
            final String clientEncodedStaticState = XFormsStateManager.instance().getClientEncodedStaticState(containingDocument);
//            if (clientEncodedStaticState != null) {
                helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[] {
                        "type", "hidden", "name", "$static-state",
                        "value", clientEncodedStaticState
                });
//            }
            final String clientEncodedDynamicState = XFormsStateManager.instance().getClientEncodedDynamicState(containingDocument);
//            if (clientEncodedDynamicState != null) {
                helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[] {
                        "type", "hidden", "name", "$dynamic-state",
                        "value", clientEncodedDynamicState
                });
//            }
        }

        if (!handlerContext.isNoScript()) {
            // Other fields used by JavaScript
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$server-events", "value", ""
            });
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$client-state", "value", ""
            });

            // Store information about nested repeats hierarchy
            {
                helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                        "type", "hidden", "name", "$repeat-tree", "value", staticState.getRepeatHierarchyString()
                });
            }

            // Store information about the initial index of each repeat
            {
                final StringBuilder repeatIndexesBuilder = new StringBuilder();
                final Map<String, Integer> repeatIdToIndex = xformsControls.getCurrentControlTree().getMinimalRepeatIdToIndex(staticState);
                if (repeatIdToIndex.size() != 0) {
                    for (final Map.Entry<String, Integer> currentEntry: repeatIdToIndex.entrySet()) {
                        final String repeatId = currentEntry.getKey();
                        final Integer index = currentEntry.getValue();

                        if (repeatIndexesBuilder.length() > 0)
                            repeatIndexesBuilder.append(',');

                        repeatIndexesBuilder.append(repeatId);
                        repeatIndexesBuilder.append(' ');
                        repeatIndexesBuilder.append(index);
                    }
                }

                helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                        "type", "hidden", "name", "$repeat-indexes", "value", repeatIndexesBuilder.toString()
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
                helper.element("", XMLConstants.XINCLUDE_URI, "include", new String[] {
                        "href", getIncludedResourcePath(requestPath, "error-dialog.xml"),
                        "fixup-xml-base", "false"
                });
            }

            // Help panel
            helper.element("", XMLConstants.XINCLUDE_URI, "include", new String[] {
                    "href", getIncludedResourcePath(requestPath, "help-panel.xml"),
                    "fixup-xml-base", "false"
            });

            // Templates
            {
                final String spanQName = XMLUtils.buildQName(htmlPrefix, "span");
                final String TEMPLATE_ID = "$xforms-effective-id$";

                // HACK: We would be ok with just one template, but IE 6 doesn't allow setting the input/@type attribute properly

                // xforms:select[@appearance = 'full'], xforms:input[@type = 'xs:boolean']
                XFormsSelect1Handler.outputItemFullTemplate(pipelineContext, this, xmlReceiver, htmlPrefix, spanQName,
                        containingDocument, reusableAttributes, attributes,
                        "xforms-select-full-template", TEMPLATE_ID, true, "checkbox");

                // xforms:select1[@appearance = 'full']
                XFormsSelect1Handler.outputItemFullTemplate(pipelineContext, this, xmlReceiver, htmlPrefix, spanQName,
                        containingDocument, reusableAttributes, attributes,
                        "xforms-select1-full-template", TEMPLATE_ID, true, "radio");
            }

        } else {
            // Noscript mode
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$noscript", "value", "true"
            });
        }
    }

    public static void registerHandlers(final ElementHandlerController controller, final XFormsStaticState staticState) {

        // xforms:input
        controller.registerHandler(XFormsInputHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "input");

        // xforms:output
        controller.registerHandler(XFormsOutputTextHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                return XFormsConstants.XXFORMS_TEXT_APPEARANCE_QNAME.equals(controller.getAttributeQNameValue(attributes.getValue(XFormsConstants.APPEARANCE_QNAME.getName())));
            }
        });
        controller.registerHandler(XFormsOutputDownloadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                return XFormsConstants.XXFORMS_DOWNLOAD_APPEARANCE_QNAME.equals(getAppearance(attributes));
            }
        });
        controller.registerHandler(XFormsOutputImageHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                final String mediatypeValue = attributes.getValue("mediatype");
                return mediatypeValue != null && mediatypeValue.startsWith("image/");
            }
        });
        controller.registerHandler(XFormsOutputHTMLHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                final String mediatypeValue = attributes.getValue("mediatype");
                return mediatypeValue != null && mediatypeValue.equals("text/html");
            }
        });
        controller.registerHandler(XFormsOutputDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output");

        // xforms:trigger
        final ElementHandlerController.Matcher triggerSubmitMinimalMatcher = controller.new Matcher() {
            public boolean match(Attributes attributes) {
                final QName appearance = getAppearance(attributes);
                return appearance != null && !staticState.isNoscript() // is noscript mode, use the full appearance
                        && (XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(appearance)    // minimal appearance
                            || XFormsConstants.XXFORMS_LINK_APPEARANCE_QNAME.equals(appearance)); // legacy appearance
            }
        };
        controller.registerHandler(XFormsTriggerMinimalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger", triggerSubmitMinimalMatcher);
        controller.registerHandler(XFormsTriggerFullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger");

        // xforms:submit
        controller.registerHandler(XFormsTriggerMinimalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "submit", triggerSubmitMinimalMatcher);
        controller.registerHandler(XFormsTriggerFullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "submit");

        // xforms:group
        controller.registerHandler(XFormsGroupInternalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                return XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME.equals(getAppearance(attributes));
            }
        });
        controller.registerHandler(XFormsGroupFieldsetHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                return XFormsConstants.XXFORMS_FIELDSET_APPEARANCE_QNAME.equals(getAppearance(attributes));
            }
        });
        controller.registerHandler(XFormsGroupSeparatorHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                // XFormsAnnotatorContentHandler adds this appearance if needed

                // NOTE: we just check on the attribute value instead of resolving the QName, so that XFormsAnnotatorContentHandler
                // doesn't have to declare the xxforms:* prefix.
                final String appearanceAttributeValue = attributes.getValue(XFormsConstants.APPEARANCE_QNAME.getName());
                return XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME.getQualifiedName().equals(appearanceAttributeValue);
            }
        });
        controller.registerHandler(XFormsGroupDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group");

        // xforms:switch
        controller.registerHandler(XFormsSwitchHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "switch");
        controller.registerHandler(XFormsCaseHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "case");

        // xforms:repeat
        controller.registerHandler(XFormsRepeatHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "repeat");

        // xforms:secret
        controller.registerHandler(XFormsSecretHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "secret");

        // xforms:upload
        controller.registerHandler(XFormsUploadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "upload");

        // xforms:range
        controller.registerHandler(XFormsRangeHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "range");

        // Other controls
        controller.registerHandler(XFormsTextareaHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "textarea");
        if (!staticState.isNoscript())
            controller.registerHandler(XXFormsDialogHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "dialog");
        else
            controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "dialog");

        // xforms:select and xforms:select1
        controller.registerHandler(XFormsSelect1InternalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                return XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME.equals(getAppearance(attributes));
            }
        });
        controller.registerHandler(XFormsSelect1InternalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select1", controller.new Matcher() {
            public boolean match(Attributes attributes) {
                return XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME.equals(getAppearance(attributes));
            }
        });
        controller.registerHandler(XFormsSelectHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select");
        controller.registerHandler(XFormsSelect1Handler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select1");

        // Add handlers for LHHA elements
        // TODO: check w/ XFStaticState if there are any standalone LHHA elements
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "label");
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "help");
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "hint");
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "alert");

        // Add handlers for custom components
        final Map<QName, XBLBindings.AbstractBinding> componentBindings = staticState.getXBLBindings().getComponentBindings();
        if (componentBindings != null) {
            for (final QName currentQName : componentBindings.keySet()) {
                controller.registerHandler(XXFormsComponentHandler.class.getName(), currentQName.getNamespaceURI(), currentQName.getName());
            }
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Add global top-level XBL markup
        final XBLBindings xblBindings = containingDocument.getStaticState().getXBLBindings();
        if (xblBindings != null)
            for (final XBLBindings.Global global : xblBindings.getGlobals().values())
                XXFormsComponentHandler.processShadowTree(handlerContext.getController(), global.fullShadowTree.getRootElement());

        // Close xhtml:form
        helper.endElement();

        // Close xhtml:body
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }

    public static String getIncludedResourcePath(String requestPath, String fileName) {
        // Path will look like "/app-name/whatever"
        final String[] pathElements = StringUtils.split(requestPath, '/');
        if (pathElements.length >= 2) {
            final String appName = pathElements[0];// it seems that split() does not return first blank match
            final String path = "/apps/" + appName + "/" + fileName;
            if (ResourceManagerWrapper.instance().exists(path)) {
                return "oxf:" + path;
            }
        }
        // Default
        return "oxf:/config/" + fileName;
    }
}
