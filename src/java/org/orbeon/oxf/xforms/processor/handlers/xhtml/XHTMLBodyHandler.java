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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.apache.commons.lang.StringUtils;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.AppearanceTrait;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.NullHandler;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import scala.collection.Seq;

import java.util.Map;

/**
 * Handle xhtml:body.
 */
public class XHTMLBodyHandler extends XFormsBaseHandlerXHTML {

    private ContentHandlerHelper helper;

//    private String formattingPrefix;

    public XHTMLBodyHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        // Register control handlers on controller
        registerHandlers(handlerContext.getController(), containingDocument);

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

        final String requestPath = containingDocument.getRequestPath();
        final String xformsSubmissionPath;
        {
            final ExternalContext.Request request = handlerContext.getExternalContext().getRequest();
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
                    "href", getIncludedResourceURL(requestPath, "noscript-panel.xml"),
                    "fixup-xml-base", "false"
            });
        }

        // Create xhtml:form element
        final boolean hasUpload = containingDocument.getStaticOps().hasControlByName("upload");
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
                    "type", "text", "name", "$client-state", "value", "", "class", "xforms-initially-hidden"
            });

            // Store information about nested repeats hierarchy
            {
                helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                        "type", "hidden", "name", "$repeat-tree", "value", containingDocument.getStaticOps().getRepeatHierarchyString()
                });
            }

            // Store information about the initial index of each repeat
            {
                final StringBuilder repeatIndexesBuilder = new StringBuilder();
                final Map<String, Integer> repeatIdToIndex = xformsControls.getCurrentControlTree().getMinimalRepeatIdToIndex(containingDocument.getStaticOps());
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
            XFormsError.outputAjaxErrorPanel(containingDocument, helper, htmlPrefix);

            // Help panel
            helper.element("", XMLConstants.XINCLUDE_URI, "include", new String[] {
                    "href", getIncludedResourceURL(requestPath, "help-panel.xml"),
                    "fixup-xml-base", "false"
            });

            // Templates
            {
                final String spanQName = XMLUtils.buildQName(htmlPrefix, "span");
                final String TEMPLATE_ID = "$xforms-effective-id$";

                // HACK: We would be ok with just one template, but IE 6 doesn't allow setting the input/@type attribute properly

                // xforms:select[@appearance = 'full'], xforms:input[@type = 'xs:boolean']
                XFormsSelect1Handler.outputItemFullTemplate(this, xmlReceiver, htmlPrefix, spanQName,
                        containingDocument, reusableAttributes, attributes,
                        "xforms-select-full-template", TEMPLATE_ID, true, "checkbox");

                // xforms:select1[@appearance = 'full']
                XFormsSelect1Handler.outputItemFullTemplate(this, xmlReceiver, htmlPrefix, spanQName,
                        containingDocument, reusableAttributes, attributes,
                        "xforms-select1-full-template", TEMPLATE_ID, true, "radio");
            }

        } else {
            // Noscript mode
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[]{
                    "type", "hidden", "name", "$noscript", "value", "true"
            });

            // Noscript error panel
            XFormsError.outputNoscriptErrorPanel(containingDocument, helper, htmlPrefix);
        }
    }
    
    private abstract static class Matcher extends ElementHandlerController.Matcher {
        
        public ElementAnalysis getElementAnalysis(Attributes attributes, Object handlerContext) {
            final HandlerContext hc = (HandlerContext) handlerContext;
            final String staticId = hc.getId(attributes);
            if (staticId != null) {
                final String prefixedId = hc.getIdPrefix() + staticId;
                return hc.getPartAnalysis().getControlAnalysis(prefixedId);
            } else {
                return null;
            }
        }
        
        public boolean hasAppearance(ElementAnalysis elementAnalysis, QName appearance) {
            if (elementAnalysis instanceof AppearanceTrait) {
                return ((AppearanceTrait) elementAnalysis).jAppearances().contains(appearance);
            } else {
                return false;
            }
        }
    }
    
    private static class AppearanceMatcher extends Matcher {
        private final QName appearance;
        public AppearanceMatcher(QName appearance) {
            this.appearance = appearance;
        }

        public Object match(Attributes attributes, Object handlerContext) {
            final ElementAnalysis elementAnalysis = getElementAnalysis(attributes, handlerContext);
            return hasAppearance(elementAnalysis, appearance) ? elementAnalysis : null;
        }
    }

    private static class AnyMatcher extends Matcher {
        public Object match(Attributes attributes, Object handlerContext) {
            return getElementAnalysis(attributes, handlerContext);
        }
    }

    public static final Matcher ANY_MATCHER = new AnyMatcher();

    public static void registerHandlers(final ElementHandlerController controller, final XFormsContainingDocument containingDocument) {

        // xforms:input
        controller.registerHandler(XFormsInputHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "input", ANY_MATCHER);

        // xforms:output
        controller.registerHandler(XFormsOutputTextHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output",
                new AppearanceMatcher(XFormsConstants.XXFORMS_TEXT_APPEARANCE_QNAME));
        controller.registerHandler(XFormsOutputDownloadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output",
                new AppearanceMatcher(XFormsConstants.XXFORMS_DOWNLOAD_APPEARANCE_QNAME));
        controller.registerHandler(XFormsOutputImageHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", new Matcher() {
            public Object match(Attributes attributes, Object handlerContext) {
                final ElementAnalysis elementAnalysis = getElementAnalysis(attributes, handlerContext);
                // TODO: aks ElementAnalysis for its mediatype
                final String mediatypeValue = attributes.getValue("mediatype");
                return mediatypeValue != null && mediatypeValue.startsWith("image/") ? elementAnalysis : null;
            }
        });
        controller.registerHandler(XFormsOutputHTMLHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", new Matcher() {
            public Object match(Attributes attributes, Object handlerContext) {
                final ElementAnalysis elementAnalysis = getElementAnalysis(attributes, handlerContext);
                // TODO: aks ElementAnalysis for its mediatype
                final String mediatypeValue = attributes.getValue("mediatype");
                return mediatypeValue != null && mediatypeValue.equals("text/html") ? elementAnalysis : null;
            }
        });
        controller.registerHandler(XFormsOutputDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "output", ANY_MATCHER);

        // xforms:trigger
        final Matcher triggerSubmitMinimalMatcher = new Matcher() {
            public ElementAnalysis match(Attributes attributes, Object handlerContext) {
                final ElementAnalysis elementAnalysis = getElementAnalysis(attributes, handlerContext);
                return elementAnalysis != null && !containingDocument.getStaticState().isNoscript()             // in noscript mode, use the full appearance
                        && (hasAppearance(elementAnalysis, XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME)     // minimal appearance
                            || hasAppearance(elementAnalysis, XFormsConstants.XXFORMS_LINK_APPEARANCE_QNAME))   // legacy appearance
                       ? elementAnalysis : null;
            }
        };
        controller.registerHandler(XFormsTriggerMinimalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger", triggerSubmitMinimalMatcher);
        controller.registerHandler(XFormsTriggerFullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "trigger", ANY_MATCHER);

        // xforms:submit
        controller.registerHandler(XFormsTriggerMinimalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "submit", triggerSubmitMinimalMatcher);
        controller.registerHandler(XFormsTriggerFullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "submit", ANY_MATCHER);

        // xforms:group
        controller.registerHandler(XFormsGroupInternalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group",
                new AppearanceMatcher(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME));
        controller.registerHandler(XFormsGroupFieldsetHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group",
                new AppearanceMatcher(XFormsConstants.XXFORMS_FIELDSET_APPEARANCE_QNAME));
        controller.registerHandler(XFormsGroupSeparatorHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group", new Matcher() {
            public Object match(Attributes attributes, Object handlerContext) {
                final ElementAnalysis elementAnalysis = getElementAnalysis(attributes, handlerContext);

                // XFormsAnnotatorContentHandler adds this appearance if needed
                // NOTE: we just check on the attribute value instead of resolving the QName, so that XFormsAnnotatorContentHandler
                // doesn't have to declare the xxforms:* prefix.
                final String appearanceAttributeValue = attributes.getValue(XFormsConstants.APPEARANCE_QNAME.getName());
                return XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME.getQualifiedName().equals(appearanceAttributeValue) ? elementAnalysis : null;
            }
        });
        controller.registerHandler(XFormsGroupDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "group", ANY_MATCHER);

        // xforms:switch
        // NOTE: We use the same handlers for switch as we do for group
        controller.registerHandler(XFormsGroupSeparatorHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "switch", new Matcher() {
            public Object match(Attributes attributes, Object handlerContext) {
                final ElementAnalysis elementAnalysis = getElementAnalysis(attributes, handlerContext);

                // XFormsAnnotatorContentHandler adds this appearance if needed
                // NOTE: we just check on the attribute value instead of resolving the QName, so that XFormsAnnotatorContentHandler
                // doesn't have to declare the xxforms:* prefix.
                final String appearanceAttributeValue = attributes.getValue(XFormsConstants.APPEARANCE_QNAME.getName());
                return XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME.getQualifiedName().equals(appearanceAttributeValue) ? elementAnalysis : null;
            }
        });
        controller.registerHandler(XFormsGroupDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "switch", ANY_MATCHER);
        controller.registerHandler(XFormsCaseHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "case", ANY_MATCHER);

        // xforms:repeat
        controller.registerHandler(XFormsRepeatHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "repeat", ANY_MATCHER);

        // xforms:secret
        controller.registerHandler(XFormsSecretHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "secret", ANY_MATCHER);

        // xforms:upload
        controller.registerHandler(XFormsUploadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "upload", ANY_MATCHER);

        // xforms:range
        controller.registerHandler(XFormsRangeHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "range", ANY_MATCHER);

        // Other controls
        controller.registerHandler(XFormsTextareaHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "textarea", ANY_MATCHER);
        if (!containingDocument.getStaticState().isNoscript())
            controller.registerHandler(XXFormsDialogHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "dialog", ANY_MATCHER);
        else
            controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "dialog", ANY_MATCHER);

        // xforms:select and xforms:select1
        controller.registerHandler(XFormsSelect1InternalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select",
                new AppearanceMatcher(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME));
        controller.registerHandler(XFormsSelect1InternalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select1",
                new AppearanceMatcher(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME));
        controller.registerHandler(XFormsSelectHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select", ANY_MATCHER);
        controller.registerHandler(XFormsSelect1Handler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "select1", ANY_MATCHER);

        // Add handlers for LHHA elements
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "label", ANY_MATCHER);
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "help", ANY_MATCHER);
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "hint", ANY_MATCHER);
        controller.registerHandler(XFormsLabelHintHelpAlertHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI, "alert", ANY_MATCHER);

        // Add handlers for custom components
        final Seq<QName> componentBindings = containingDocument.getStaticOps().getBindingQNames();
        for (final scala.collection.Iterator<QName> i = componentBindings.iterator(); i.hasNext();) {
            final QName currentQName = i.next();
            controller.registerHandler(XXFormsComponentHandler.class.getName(), currentQName.getNamespaceURI(), currentQName.getName(), ANY_MATCHER);
        }

        // xxf:dynamic
        controller.registerHandler(XXFormsDynamicHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "dynamic", ANY_MATCHER);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Add global top-level XBL markup
        final scala.collection.Iterator<XBLBindingsBase.Global> i = containingDocument.getStaticOps().getGlobals().values().iterator();
        while (i.hasNext())
            XXFormsComponentHandler.processShadowTree(handlerContext.getController(), i.next().fullShadowTree);

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
                return path;
            }
        }
        // Default
        return "/config/" + fileName;
    }
    
    public static String getIncludedResourceURL(String requestPath, String fileName) {
        return "oxf:" + getIncludedResourcePath(requestPath, fileName);
    }
}
