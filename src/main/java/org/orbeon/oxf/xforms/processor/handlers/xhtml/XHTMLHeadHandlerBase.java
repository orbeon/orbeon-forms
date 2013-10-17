/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.EventHandler;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handle xhtml:head.
 */
public abstract class XHTMLHeadHandlerBase extends XFormsBaseHandlerXHTML {

    private String formattingPrefix;

    public XHTMLHeadHandlerBase() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        // Register control handlers on controller
        {
            final ElementHandlerController controller = handlerContext.getController();
            controller.registerHandler(XXFormsTextHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "text", XHTMLBodyHandler.ANY_MATCHER);
        }

        // Declare xmlns:f
        formattingPrefix = handlerContext.findFormattingPrefixDeclare();

        // Open head element
        xmlReceiver.startElement(uri, localname, qName, attributes);

        final ContentHandlerHelper helper = new ContentHandlerHelper(xmlReceiver);
        final String xhtmlPrefix = XMLUtils.prefixFromQName(qName); // current prefix for XHTML

        // Create prefix for combined resources if needed
        final boolean isMinimal = XFormsProperties.isMinimalResources();
        final boolean isVersionedResources = URLRewriterUtils.isResourcesVersioned();

        // Include static XForms CSS. This makes sure that
        final String requestPath = handlerContext.getExternalContext().getRequest().getRequestPath();
        helper.element("", XMLConstants.XINCLUDE_URI, "include", new String[] {
                "href", XHTMLBodyHandler.getIncludedResourceURL(requestPath, "static-xforms-css.xml"),
                "fixup-xml-base", "false"
        });

        // Stylesheets
        final AttributesImpl attributesImpl = new AttributesImpl();
        outputCSSResources(helper, xhtmlPrefix, isMinimal, attributesImpl);

        // Scripts
        if (!handlerContext.isNoScript() && !XFormsProperties.isReadonly(containingDocument)) {

            // Main JavaScript resources
            outputJavaScriptResources(helper, xhtmlPrefix, isMinimal, attributesImpl);

            // Configuration properties
            outputConfigurationProperties(helper, xhtmlPrefix, isVersionedResources);

            // User-defined (with xxf:script) and XForms scripts
            final XFormsControl focusedControl = containingDocument.getControls().getFocusedControl();
            final String focusElementId = focusedControl != null ? focusedControl.getEffectiveId() : null;
            final List<XFormsContainingDocument.Message> messagesToRun = containingDocument.getMessagesToRun();

            final List<XXFormsDialogControl> dialogsToOpen = new ArrayList<XXFormsDialogControl>(); {
                final Map<String, XFormsControl> dialogsMap = containingDocument.getControls().getCurrentControlTree().getDialogControls();
                if (dialogsMap != null && dialogsMap.size() > 0) {
                    for (final XFormsControl control: dialogsMap.values()) {
                        final XXFormsDialogControl dialogControl = (XXFormsDialogControl) control;
                        if (dialogControl.isVisible()) {
                            dialogsToOpen.add(dialogControl);
                        }
                    }
                }
            }

            outputScriptDeclarations(helper, xhtmlPrefix, focusElementId, messagesToRun, dialogsToOpen);

            // Store information about "special" controls that need JavaScript initialization
            // Gather information about appearances of controls which use Script
            // Map<String controlName, Map<String appearanceOrMediatype, List<String effectiveId>>>
            outputJavaScriptInitialData(helper, xhtmlPrefix, XHTMLHeadHandler.gatherJavascriptControls(containingDocument));
        }
    }

    protected abstract void outputCSSResources(ContentHandlerHelper helper, String xhtmlPrefix,
        boolean minimal, AttributesImpl attributesImpl);

    protected abstract void outputJavaScriptResources(ContentHandlerHelper helper, String xhtmlPrefix,
        boolean minimal, AttributesImpl attributesImpl);

    protected abstract void outputConfigurationProperties(ContentHandlerHelper helper, String xhtmlPrefix, boolean versionedResources);

    private void outputScriptDeclarations(ContentHandlerHelper helper, String xhtmlPrefix, String focusElementId, List<XFormsContainingDocument.Message> messagesToRun, List<XXFormsDialogControl> dialogsToOpen) {

        if (containingDocument.getStaticOps().uniqueClientScripts().size() > 0 || focusElementId != null || messagesToRun != null || dialogsToOpen.size() > 0) {
            helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                "type", "text/javascript"});

            XHTMLHeadHandler.outputScripts(helper, containingDocument.getStaticOps().uniqueClientScripts());

            final List<XFormsContainingDocument.Script> scriptsToRun = containingDocument.getScriptsToRun();

            if (scriptsToRun != null || focusElementId != null || messagesToRun != null || dialogsToOpen.size() > 0) {
                final StringBuilder sb = new StringBuilder("\nfunction xformsPageLoadedServer() { ");

                // Initial xxf:script executions if present
                if (scriptsToRun != null) {
                    for (final XFormsContainingDocument.Script script: scriptsToRun) {
                        sb.append("ORBEON.xforms.server.Server.callUserScript(\"");
                        sb.append(script.functionName);
                        sb.append("\",\"");
                        sb.append(XFormsUtils.namespaceId(containingDocument, script.targetEffectiveId));
                        sb.append("\",\"");
                        sb.append(XFormsUtils.namespaceId(containingDocument, script.observerEffectiveId));
                        sb.append("\");");
                    }
                }

                // Initial xf:message to run if present
                if (messagesToRun != null) {
                    boolean foundModalMessage = false;
                    for (final XFormsContainingDocument.Message message: messagesToRun) {
                        if ("modal".equals(message.getLevel())) {
                            if (foundModalMessage) {
                                sb.append(", ");
                            } else {
                                foundModalMessage = true;
                                sb.append("ORBEON.xforms.action.Message.showMessages([");
                            }
                            sb.append("\"");
                            sb.append(XFormsUtils.escapeJavaScript(message.getMessage()));
                            sb.append("\"");
                        }
                    }
                    if (foundModalMessage) {
                        sb.append("]);");
                    }
                }

                // Initial dialogs to open
                if (dialogsToOpen.size() > 0) {
                    for (final XXFormsDialogControl dialogControl: dialogsToOpen) {
                        sb.append("ORBEON.xforms.Controls.showDialog(\"");
                        sb.append(XFormsUtils.namespaceId(containingDocument, dialogControl.getEffectiveId()));
                        sb.append("\", ");
                        if (dialogControl.getNeighborControlId() != null) {
                            sb.append('"');
                            sb.append(XFormsUtils.namespaceId(containingDocument, dialogControl.getNeighborControlId()));
                            sb.append('"');
                        } else {
                            sb.append("null");
                        }
                        sb.append(");");
                    }
                }

                // Initial setfocus if present
                // Seems reasonable to do this after dialogs as focus might be within a dialog
                if (focusElementId != null) {
                    sb.append("ORBEON.xforms.Controls.setFocus(\"");
                    sb.append(XFormsUtils.namespaceId(containingDocument, focusElementId));
                    sb.append("\");");
                }

                sb.append(" }");

                helper.text(sb.toString());
            }

            helper.endElement();
        }
    }

    private void outputJavaScriptInitialData(ContentHandlerHelper helper, String xhtmlPrefix, Map<String, Map<String, List<String>>> javaScriptControlsAppearancesMap) {
        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                "type", "text/javascript"});

        // Produce JSON output
        final boolean hasPaths = true;
        final boolean hasInitControls = javaScriptControlsAppearancesMap.size() > 0;
        final boolean hasKeyListeners = containingDocument.getStaticOps().getKeyHandlers().size() > 0;
        final boolean hasServerEvents; {
            final List<XFormsContainingDocument.DelayedEvent> delayedEvents = containingDocument.getDelayedEvents();
            hasServerEvents = delayedEvents != null && delayedEvents.size() > 0;
        }
        final boolean outputInitData = hasPaths || hasInitControls || hasKeyListeners || hasServerEvents;
        if (outputInitData) {
            final StringBuilder sb = new StringBuilder("var orbeonInitData = orbeonInitData || {}; orbeonInitData[\"");
            sb.append(XFormsUtils.getFormId(containingDocument));
            sb.append("\"] = {");

            // Output path information
            if (hasPaths) {
                sb.append("\"paths\":{");

                sb.append("\"xforms-server\": \"");
                sb.append(handlerContext.getExternalContext().getResponse().rewriteResourceURL("/xforms-server", ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE));

                // NOTE: This is to handle the minimal date picker. Because the client must re-generate markup when
                // the control becomes relevant or changes type, it needs the image URL, and that URL must be rewritten
                // for use in portlets. This should ideally be handled by a template and/or the server should provide
                // the markup directly when needed.
                final String calendarImage = "/ops/images/xforms/calendar.png";
                final String rewrittenCalendarImage = handlerContext.getExternalContext().getResponse().rewriteResourceURL(calendarImage, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);

                sb.append("\",\"calendar-image\": \"");
                sb.append(rewrittenCalendarImage);
                sb.append('"');

                sb.append('}');
            }

            // Output controls initialization
            if (hasInitControls) {
                if (hasPaths)
                    sb.append(',');

                sb.append("\"controls\":{");

                for (Iterator<Map.Entry<String,Map<String,List<String>>>> i = javaScriptControlsAppearancesMap.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry<String,Map<String,List<String>>> currentEntry1 = i.next();
                    final String controlName = currentEntry1.getKey();
                    final Map<String, List<String>> controlMap = currentEntry1.getValue();

                    sb.append("\"");
                    sb.append(controlName);
                    sb.append("\":{");

                    for (Iterator<Map.Entry<String,List<String>>> j = controlMap.entrySet().iterator(); j.hasNext();) {
                        final Map.Entry<String,List<String>> currentEntry2 = j.next();
                        final String controlAppearance = currentEntry2.getKey();
                        final List<String> idsForAppearanceList = currentEntry2.getValue();

                        sb.append('"');
                        sb.append(controlAppearance != null ? controlAppearance : "");
                        sb.append("\":[");

                        for (Iterator<String> k = idsForAppearanceList.iterator(); k.hasNext();) {
                            final String controlId = k.next();
                            sb.append('"');
                            sb.append(controlId);
                            sb.append('"');
                            if (k.hasNext())
                                sb.append(',');
                        }

                        sb.append(']');
                        if (j.hasNext())
                            sb.append(',');
                    }

                    sb.append("}");
                    if (i.hasNext())
                        sb.append(',');
                }
                sb.append('}');
            }

            // Output key listener information
            if (hasKeyListeners) {
                if (hasPaths || hasInitControls)
                    sb.append(',');

                sb.append("\"keylisteners\":[");

                boolean first = true;
                for (EventHandler handler: containingDocument.getStaticOps().getKeyHandlers()) {
                    if (!first)
                        sb.append(',');

                    for (final String observer: handler.jObserversPrefixedIds()) {
                        sb.append('{');
                        sb.append("\"observer\":\"");
                        sb.append(observer);
                        sb.append('"');
                        if (handler.getKeyModifiers() != null) {
                            sb.append(",\"modifier\":\"");
                            sb.append(handler.getKeyModifiers());
                            sb.append('"');
                        }
                        if (handler.getKeyText() != null) {
                            sb.append(",\"text\":\"");
                            sb.append(handler.getKeyText());
                            sb.append('"');
                        }
                        sb.append('}');

                        first = false;
                    }
                }

                sb.append(']');
            }

            // Output server events
            if (hasServerEvents) {
                if (hasPaths || hasInitControls || hasKeyListeners)
                    sb.append(',');

                sb.append("\"server-events\":[");

                final long currentTime = System.currentTimeMillis();
                boolean first = true;
                for (XFormsContainingDocument.DelayedEvent delayedEvent: containingDocument.getDelayedEvents()) {

                    if (!first)
                        sb.append(',');

                    delayedEvent.toJSON(sb, currentTime);

                    first = false;
                }

                sb.append(']');
            }

            sb.append("};");

            helper.text(sb.toString());
        }

        helper.endElement();
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Close head element
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();
        xmlReceiver.endElement(uri, localname, qName);

        // Undeclare xmlns:f
        handlerContext.findFormattingPrefixUndeclare(formattingPrefix);
    }
}
