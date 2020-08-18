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

import org.apache.commons.lang3.StringUtils;
import org.orbeon.dom.QName;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsError;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.Global;
import org.orbeon.oxf.xforms.analysis.controls.AppearanceTrait$;
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl;
import org.orbeon.oxf.xforms.control.LHHASupport;
import org.orbeon.oxf.xforms.control.XFormsControl$;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xml.*;
import org.orbeon.xforms.Constants;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import scala.Option;

/**
 * Handle xhtml:body.
 */
public class XHTMLBodyHandler extends XFormsBaseHandlerXHTML {

    private static Attributes prepareAttributes(Attributes attributes, HandlerContext xformsHandlerContext) {
        // Add class for YUI skin
        Attributes newAttributes = SAXUtils.appendToClassAttribute(attributes, Constants.YuiSkinSamClass());

        // Handle AVTs
        return handleAVTsAndIDs(newAttributes, XHTMLElementHandler.REF_ID_ATTRIBUTE_NAMES(), xformsHandlerContext);
    }

    private XMLReceiverHelper helper;

    public XHTMLBodyHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext) {
        super(uri, localname, qName, prepareAttributes(attributes, (HandlerContext) handlerContext), matched, handlerContext, false, true);
    }

    @Override
    public void start() throws SAXException {

        // Register control handlers on controller
        registerHandlers(xformsHandlerContext.getController(), containingDocument);

        // Start xhtml:body
        final XMLReceiver xmlReceiver = xformsHandlerContext.getController().getOutput();
        xmlReceiver.startElement(uri(), localname(), qName(), attributes());
        helper = new XMLReceiverHelper(xmlReceiver);

        final String htmlPrefix = XMLUtils.prefixFromQName(qName());

        // Get formatting prefix and declare it if needed
        // TODO: would be nice to do this here, but then we need to make sure this prefix is available to other handlers
//        formattingPrefix = handlerContext.findFormattingPrefixDeclare();

        final boolean isEmbeddedClient = containingDocument.isEmbedded();

        final String requestPath = containingDocument.getRequestPath();
        final String xformsSubmissionPath;
        {
            if (! containingDocument.getDeploymentType().equals(XFormsConstants.StandaloneDeploymentTypeJava())
                    || containingDocument.isPortletContainer() || isEmbeddedClient) {
                // Integrated or separate deployment mode or portlet
                xformsSubmissionPath = XFormsConstants.XFORMS_SERVER_SUBMIT();
            } else {
                // Plain deployment mode: submission posts to URL of the current page and xforms-xml-submission.xpl intercepts that
                xformsSubmissionPath = requestPath;
            }
        }

        helper.element("", XMLNames.XIncludeURI(), "include", new String[] {
            "href", getIncludedResourceURL(requestPath, "noscript-panel.xml"),
            "fixup-xml-base", "false"
        });

        final StringBuilder sb = new StringBuilder("xforms-form xforms-initially-hidden");

        // LHHA appearance classes
        //
        // NOTE: There can be multiple appearances at the same time:
        //
        // - `xforms-hint-appearance-full xforms-hint-appearance-minimal`
        // - `xforms-hint-appearance-tooltip xforms-hint-appearance-minimal`
        //
        // That's because the `minimal` appearance doesn't apply to all controls, but only (as of 2016.2) to input fields.
        //
        AppearanceTrait$.MODULE$.encodeAndAppendAppearances(sb, "label", containingDocument.getLabelAppearances());

        final scala.collection.immutable.Set<String> hintAppearances = containingDocument.getHintAppearances();
        AppearanceTrait$.MODULE$.encodeAndAppendAppearances(sb, "hint", hintAppearances);
        if (hintAppearances.contains("tooltip"))
            sb.append(" xforms-enable-hint-as-tooltip");
        else
            sb.append(" xforms-disable-hint-as-tooltip");

        AppearanceTrait$.MODULE$.encodeAndAppendAppearance(sb,  "help", QName.apply(containingDocument.getHelpAppearance()));

        // Create xhtml:form element
        // NOTE: Do multipart as well with portlet client to simplify the proxying so we don't have to re-encode parameters
        final boolean doMultipartPOST = containingDocument.staticOps().hasControlByName("upload") || isEmbeddedClient;
        helper.startElement(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "form", new String[] {
                // Add id so that things work in portals
                "id", XFormsUtils.getNamespacedFormId(containingDocument),
                // Regular classes
                "class", sb.toString(),
                // Submission parameters
                "action", xformsSubmissionPath, "method", "POST",
                "onsubmit", "return false",
                doMultipartPOST ? "enctype" : null, doMultipartPOST ? "multipart/form-data" : null});

        {
            // Only for 2-pass submission
            helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[] {
                    "type", "hidden", "name", Constants.UuidFieldName(), "value", containingDocument.uuid()
            });

            // NOTE: we don't need $sequence here as HTML form posts are either:
            //
            // - 2nd phase of replace="all" submission: we don't (and can't) retry
            // - background upload: we don't want a sequence number as this run in parallel
            //

            // Output encoded static and dynamic state, only for client state handling (no longer supported in JavaScript)
            final Option<String> clientEncodedStaticStateOpt =
                    XFormsStateManager.instance().getClientEncodedStaticState(containingDocument);
            if (clientEncodedStaticStateOpt.isDefined()) {
                helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[] {
                    "type", "hidden", "name", "$static-state", "value", clientEncodedStaticStateOpt.get()
                });
            }
            final Option<String> clientEncodedDynamicStateOpt =
                    XFormsStateManager.instance().getClientEncodedDynamicState(containingDocument);
            if (clientEncodedDynamicStateOpt.isDefined()) {
                helper.element(htmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "input", new String[] {
                    "type", "hidden", "name", "$dynamic-state", "value", clientEncodedDynamicStateOpt.get()
                });
            }
        }

        {
            // Ajax error panel
            XFormsError.outputAjaxErrorPanel(containingDocument, helper, htmlPrefix);

            // Help panel
            helper.element("", XMLNames.XIncludeURI(), "include", new String[] {
                    "href", getIncludedResourceURL(requestPath, "help-panel.xml"),
                    "fixup-xml-base", "false"
            });

            // Templates
            {
                // HACK: We would be ok with just one template, but IE 6 doesn't allow setting the input/@type attribute properly

                // xf:select[@appearance = 'full'], xf:input[@type = 'xs:boolean']
                XFormsSelect1Handler.outputItemFullTemplate(this, htmlPrefix,
                        containingDocument, reusableAttributes, attributes(),
                        "xforms-select-full-template", "$xforms-item-name$", true, "checkbox", xmlReceiver);

                // xf:select1[@appearance = 'full']
                XFormsSelect1Handler.outputItemFullTemplate(this, htmlPrefix,
                        containingDocument, reusableAttributes, attributes(),
                        "xforms-select1-full-template", "$xforms-item-name$", false, "radio", xmlReceiver);
            }

        }
    }

    private abstract static class Matcher extends ElementHandlerController.Function2Base<Attributes, Object, ElementAnalysis> {

        public String getPrefixedId(Attributes attributes, Object handlerContext) {
            final HandlerContext hc = (HandlerContext) handlerContext;
            return hc.getPrefixedId(attributes);
        }

        public ElementAnalysis getElementAnalysis(Attributes attributes, Object handlerContext) {
            final HandlerContext hc = (HandlerContext) handlerContext;
            final String prefixedId = hc.getPrefixedId(attributes);
            if (prefixedId != null) {
                return hc.getPartAnalysis().getControlAnalysis(prefixedId);
            } else {
                return null;
            }
        }

        public boolean hasAppearance(ElementAnalysis elementAnalysis, QName appearance) {
            return XFormsControl$.MODULE$.appearances(elementAnalysis).contains(appearance);
        }

        public ElementAnalysis apply(Attributes attributes, Object handlerContext) {
            return doesMatch(attributes, handlerContext) ? getElementAnalysis(attributes, handlerContext) : null;
        }

        abstract boolean doesMatch(Attributes attributes, Object handlerContext);
    }

    private static class AppearanceMatcher extends Matcher {
        private final QName appearance;
        public AppearanceMatcher(QName appearance) {
            this.appearance = appearance;
        }

        public boolean doesMatch(Attributes attributes, Object handlerContext) {
            return hasAppearance(getElementAnalysis(attributes, handlerContext), appearance);
        }
    }

    private static class AnyMatcher extends Matcher {
        boolean doesMatch(Attributes attributes, Object handlerContext) {
            return true;
        }
    }

    public static final Matcher ANY_MATCHER = new AnyMatcher();

    public static void registerHandlers(final ElementHandlerController controller, final XFormsContainingDocument containingDocument) {

        // Add handlers for XBL components
        controller.registerHandler(XXFormsComponentHandler.class.getName(), new Matcher() {
            public boolean doesMatch(Attributes attributes, Object handlerContext) {
                final HandlerContext hc = (HandlerContext) handlerContext;
                final Option<ElementAnalysis> control = hc.getPartAnalysis().findControlAnalysis(getPrefixedId(attributes, handlerContext));
                if (control.isDefined()) {
                    return control.get() instanceof ComponentControl;
                } else {
                    return false;
                }
            }
        });

        // xf:input
        controller.registerHandler(XFormsInputHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "input", ANY_MATCHER);

        // xf:output
        controller.registerHandler(XFormsOutputTextHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "output",
                new AppearanceMatcher(XFormsConstants.XXFORMS_TEXT_APPEARANCE_QNAME()));
        controller.registerHandler(XFormsOutputDownloadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "output",
                new AppearanceMatcher(XFormsConstants.XXFORMS_DOWNLOAD_APPEARANCE_QNAME()));
        controller.registerHandler(XFormsOutputImageHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "output", new Matcher() {
            public boolean doesMatch(Attributes attributes, Object handlerContext) {
                // TODO: aks ElementAnalysis for its mediatype
                final String mediatypeValue = attributes.getValue("mediatype");
                return mediatypeValue != null && mediatypeValue.startsWith("image/");
            }
        });
        controller.registerHandler(XFormsOutputHTMLHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "output", new Matcher() {
            public boolean doesMatch(Attributes attributes, Object handlerContext) {
                // TODO: aks ElementAnalysis for its mediatype
                final String mediatypeValue = attributes.getValue("mediatype");
                return mediatypeValue != null && mediatypeValue.equals("text/html");
            }
        });
        controller.registerHandler(XFormsOutputDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "output", ANY_MATCHER);

        // xf:trigger
        final Matcher triggerSubmitMinimalMatcher = new AppearanceMatcher(XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME());
        controller.registerHandler(XFormsTriggerMinimalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "trigger", triggerSubmitMinimalMatcher);
        controller.registerHandler(XFormsTriggerFullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "trigger", ANY_MATCHER);

        // xf:submit
        controller.registerHandler(XFormsTriggerMinimalHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "submit", triggerSubmitMinimalMatcher);
        controller.registerHandler(XFormsTriggerFullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "submit", ANY_MATCHER);

        // xf:group
        controller.registerHandler(TransparentHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "group",
                new AppearanceMatcher(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME()));

        controller.registerHandler(XFormsGroupSeparatorHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "group", new Matcher() {
            public boolean doesMatch(Attributes attributes, Object handlerContext) {
                // XFormsAnnotator adds this appearance if needed
                // See: https://github.com/orbeon/orbeon-forms/issues/418
                final String appearanceAttributeValue = attributes.getValue(XFormsConstants.APPEARANCE_QNAME().localName());
                return XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME().qualifiedName().equals(appearanceAttributeValue);
            }
        });

        controller.registerHandler(XFormsGroupFieldsetHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "group",
                new AppearanceMatcher(XFormsConstants.XXFORMS_FIELDSET_APPEARANCE_QNAME()) {
                    public boolean doesMatch(Attributes attributes, Object handlerContext) {
                        return super.doesMatch(attributes, handlerContext) || LHHASupport.hasLabel(containingDocument, getPrefixedId(attributes, handlerContext));
                    }
                });

        controller.registerHandler(XFormsGroupDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "group", ANY_MATCHER);

        // xf:switch
        // NOTE: We use the same handlers for switch as we do for group
        controller.registerHandler(XFormsGroupSeparatorHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "switch", new Matcher() {
            public boolean doesMatch(Attributes attributes, Object handlerContext) {
                // XFormsAnnotator adds this appearance if needed
                // See: https://github.com/orbeon/orbeon-forms/issues/418
                final String appearanceAttributeValue = attributes.getValue(XFormsConstants.APPEARANCE_QNAME().localName());
                return XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME().qualifiedName().equals(appearanceAttributeValue);
            }
        });
        controller.registerHandler(XFormsGroupDefaultHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "switch", ANY_MATCHER);
        controller.registerHandler(XFormsCaseHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "case", ANY_MATCHER);

        // xf:repeat
        controller.registerHandler(XFormsRepeatHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "repeat", ANY_MATCHER);
        controller.registerHandler(TransparentHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "repeat-iteration", ANY_MATCHER);

        // xf:secret
        controller.registerHandler(XFormsSecretHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "secret", ANY_MATCHER);

        // xf:upload
        controller.registerHandler(XFormsUploadHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "upload", ANY_MATCHER);

        // xf:range
        controller.registerHandler(XFormsRangeHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "range", ANY_MATCHER);

        // Other controls
        controller.registerHandler(XFormsTextareaHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "textarea", ANY_MATCHER);
        controller.registerHandler(XXFormsDialogHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI(), "dialog", ANY_MATCHER);

        // xf:select and xf:select1
        controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "select",
                new AppearanceMatcher(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME()));
        controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "select1",
                new AppearanceMatcher(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME()));
        controller.registerHandler(XFormsSelectHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "select", ANY_MATCHER);
        controller.registerHandler(XFormsSelect1Handler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "select1", ANY_MATCHER);

        // Add handlers for LHHA elements
        controller.registerHandler(XFormsLHHAHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "label", ANY_MATCHER);
        controller.registerHandler(XFormsLHHAHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "help", ANY_MATCHER);
        controller.registerHandler(XFormsLHHAHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "hint", ANY_MATCHER);
        controller.registerHandler(XFormsLHHAHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI(), "alert", ANY_MATCHER);

        // xxf:dynamic
        controller.registerHandler(XXFormsDynamicHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI(), "dynamic", ANY_MATCHER);
    }

    @Override
    public void end() throws SAXException {

        // Add global top-level XBL markup
        final scala.collection.Iterator<Global> i = containingDocument.staticOps().getGlobals().iterator();
        while (i.hasNext())
            XXFormsComponentHandler.processShadowTree(xformsHandlerContext.getController(), i.next().templateTree());

        // Close xhtml:form
        helper.endElement();

        // Close xhtml:body
        final ContentHandler contentHandler = xformsHandlerContext.getController().getOutput();
        contentHandler.endElement(uri(), localname(), qName());
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
