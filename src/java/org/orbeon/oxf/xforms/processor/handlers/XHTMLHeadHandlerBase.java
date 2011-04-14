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
package org.orbeon.oxf.xforms.processor.handlers;

import org.apache.commons.collections.map.CompositeMap;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.XFormsEventHandler;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.Serializable;
import java.util.*;

/**
 * Handle xhtml:head.
 */
public abstract class XHTMLHeadHandlerBase extends XFormsBaseHandler {

    private String formattingPrefix;

    public XHTMLHeadHandlerBase() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        // Register control handlers on controller
        {
            final ElementHandlerController controller = handlerContext.getController();
            controller.registerHandler(XXFormsTextHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "text");
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
                "href", XHTMLBodyHandler.getIncludedResourcePath(requestPath, "static-xforms-css.xml"),
                "fixup-xml-base", "false"
        });

        // Stylesheets
        final AttributesImpl attributesImpl = new AttributesImpl();
        outputCSSResources(helper, xhtmlPrefix, isMinimal, attributesImpl);

        // Scripts
        // TODO: Have option to put this at the bottom of the page. See theme-plain.xsl and http://developer.yahoo.com/performance/rules.html#js_bottom -->
        if (!handlerContext.isNoScript() && !XFormsProperties.isReadonly(containingDocument)) {

            // Main JavaScript resources
            outputJavaScriptResources(helper, xhtmlPrefix, isMinimal, attributesImpl);

            // Configuration properties
            outputConfigurationProperties(helper, xhtmlPrefix, isVersionedResources);

            // User-defined (with xxforms:script) and XForms scripts
            final Map<String, Script> scriptsToDeclare = containingDocument.getScripts();
            final String focusElementId = containingDocument.getClientFocusControlEffectiveId();
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
            outputScriptDeclarations(helper, xhtmlPrefix, scriptsToDeclare, focusElementId, messagesToRun, dialogsToOpen);

            // Store information about "special" controls that need JavaScript initialization
            // Gather information about appearances of controls which use Script
            // Map<String controlName, Map<String appearanceOrMediatype, List<String effectiveId>>>
            final Map<String, Map<String, List<String>>> javaScriptControlsAppearancesMap = gatherJavascriptControls();
            outputJavaScriptInitialData(helper, xhtmlPrefix, javaScriptControlsAppearancesMap);
        }
    }

    private Map<String, Map<String, List<String>>> gatherJavascriptControls() {
        final Map<String, Map<String, List<String>>> javaScriptControlsAppearancesMap = new HashMap<String, Map<String, List<String>>>();
        final XFormsControls xformsControls = containingDocument.getControls();
        xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                final String controlName = control.getName();

                // Don't run JavaScript initialization if the control is static readonly (could change in the
                // future if some static readonly controls require JS initialization)
                final boolean hasJavaScriptInitialization = control.hasJavaScriptInitialization() && !control.isStaticReadonly();
                if (hasJavaScriptInitialization) {
                    Map<String, List<String>> listForControlNameMap = javaScriptControlsAppearancesMap.get(controlName);
                    if (listForControlNameMap == null) {
                        listForControlNameMap = new HashMap<String, List<String>>();
                        javaScriptControlsAppearancesMap.put(control.getName(), listForControlNameMap);
                    }
                    final String controlAppearanceOrMediatype;
                    {
                        final String controlAppearance = control.getAppearance();
                        controlAppearanceOrMediatype = (controlAppearance != null) ? controlAppearance : control.getMediatype();
                    }

                    List<String> idsForAppearanceOrMediatypeList = listForControlNameMap.get(controlAppearanceOrMediatype);
                    if (idsForAppearanceOrMediatypeList == null) {
                        idsForAppearanceOrMediatypeList = new ArrayList<String>();
                        listForControlNameMap.put(controlAppearanceOrMediatype, idsForAppearanceOrMediatypeList);
                    }
                    idsForAppearanceOrMediatypeList.add(XFormsUtils.namespaceId(containingDocument, control.getEffectiveId()));
                }
                return true;
            }
        });
        return javaScriptControlsAppearancesMap;
    }

    protected abstract void outputCSSResources(ContentHandlerHelper helper, String xhtmlPrefix,
        boolean minimal, AttributesImpl attributesImpl);

    protected abstract void outputJavaScriptResources(ContentHandlerHelper helper, String xhtmlPrefix,
        boolean minimal, AttributesImpl attributesImpl);

    private void outputConfigurationProperties(ContentHandlerHelper helper, String xhtmlPrefix, boolean versionedResources) {
        final Map clientPropertiesMap;
        {
            // Dynamic properties
            final Map<String, Serializable> dynamicProperties = new HashMap<String, Serializable>();
            {
                // Heartbeat delay
                {
                    final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(XFormsProperties.SESSION_HEARTBEAT_DELAY_PROPERTY);
                    final long heartbeatDelay = XFormsStateManager.getHeartbeatDelay(containingDocument, handlerContext.getExternalContext());
                    if (heartbeatDelay != ((Number) propertyDefinition.getDefaultValue()).longValue())
                        dynamicProperties.put(XFormsProperties.SESSION_HEARTBEAT_DELAY_PROPERTY, heartbeatDelay);
                }

                // Help events
                {
                    // TODO: Need better way to enable/disable xforms-help event support, probably better static analysis of event handlers/
                    final boolean hasHandlerForXFormsHelp = containingDocument.getStaticState().hasHandlerForEvent(XFormsEvents.XFORMS_HELP, false);
                    if (hasHandlerForXFormsHelp) {
                        dynamicProperties.put(XFormsProperties.HELP_HANDLER_PROPERTY, Boolean.TRUE);
                    }
                }

                // Application version
                {
                    // This is not an XForms property but we want to expose it on the client

                    // Put property only if different from default
                    if (versionedResources != URLRewriterUtils.RESOURCES_VERSIONED_DEFAULT)
                        dynamicProperties.put(URLRewriterUtils.RESOURCES_VERSIONED_PROPERTY, Boolean.toString(versionedResources));

                    if (versionedResources) {
                        final String applicationVersion = URLRewriterUtils.getApplicationResourceVersion();
                        if (applicationVersion != null) {
                            dynamicProperties.put(URLRewriterUtils.RESOURCES_VERSION_NUMBER_PROPERTY, applicationVersion);
                        }
                    }
                }
            }

            final Map<String, Object> nonDefaultProperties = containingDocument.getStaticState().getNonDefaultProperties();
            clientPropertiesMap = new CompositeMap(new Map[] { nonDefaultProperties, dynamicProperties });
        }

        StringBuilder sb = null;
        if (clientPropertiesMap.size() > 0) {

            for (Object o: clientPropertiesMap.entrySet()) {
                final Map.Entry currentEntry = (Map.Entry) o;
                final String propertyName = (String) currentEntry.getKey();
                final Object propertyValue = currentEntry.getValue();

                final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
                if (propertyDefinition != null && propertyDefinition.isPropagateToClient()
                        || URLRewriterUtils.RESOURCES_VERSION_NUMBER_PROPERTY.equals(propertyName)
                        || URLRewriterUtils.RESOURCES_VERSIONED_PROPERTY.equals(propertyName)) {

                    if (sb == null) {
                        // First property found
                        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[]{
                                "type", "text/javascript"});
                        sb = new StringBuilder("var opsXFormsProperties = {");
                    } else {
                        // Subsequent property found
                        sb.append(',');
                    }

                    sb.append('\"');
                    sb.append(propertyName);
                    sb.append("\":");
                    if (propertyValue instanceof String) {
                        // This is a string, add quotes
                        sb.append('\"');
                        sb.append(propertyValue.toString());
                        sb.append('\"');
                    } else {
                        // Don't need quotes
                        sb.append(propertyValue.toString());
                    }
                }
            }

            if (sb != null) {
                // Close everything
                sb.append("};");
                helper.text(sb.toString());
                helper.endElement();
            }
        }
    }

    private void outputScriptDeclarations(ContentHandlerHelper helper, String xhtmlPrefix, Map<String, Script> scriptsToDeclare, String focusElementId, List<XFormsContainingDocument.Message> messagesToRun, List<XXFormsDialogControl> dialogsToOpen) {
        if (scriptsToDeclare != null || focusElementId != null || messagesToRun != null || dialogsToOpen.size() > 0) {
            helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                "type", "text/javascript"});

            if (scriptsToDeclare != null) {
                for (final Script script: scriptsToDeclare.values()) {
                    // Only output client scripts
                    if (script.isClient) {
                        helper.text("\nfunction " + XFormsUtils.scriptIdToScriptName(script.prefixedId) + "(event) {\n");
                        helper.text(script.body);
                        helper.text("}\n");
                    }
                }
            }

            final List<XFormsContainingDocument.Script> scriptsToRun = containingDocument.getScriptsToRun();

            if (scriptsToRun != null || focusElementId != null || messagesToRun != null || dialogsToOpen.size() > 0) {
                final StringBuilder sb = new StringBuilder("\nfunction xformsPageLoadedServer() { ");

                // Initial xxforms:script executions if present
                if (scriptsToRun != null) {
                    for (final XFormsContainingDocument.Script script: scriptsToRun) {
                        sb.append("ORBEON.xforms.server.Server.callUserScript(\"");
                        sb.append(script.getFunctionName());
                        sb.append("\",\"");
                        sb.append(XFormsUtils.namespaceId(containingDocument, script.getEvent().getTargetObject().getEffectiveId()));
                        sb.append("\",\"");
                        sb.append(XFormsUtils.namespaceId(containingDocument, script.getEventObserver().getEffectiveId()));
                        sb.append("\");");
                    }
                }

                // Initial xforms:message to run if present
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
        final boolean hasKeyListeners = containingDocument.getStaticState().getKeyHandlers().size() > 0;
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
                sb.append(handlerContext.getExternalContext().getResponse().rewriteResourceURL("/xforms-server", false));

                sb.append("\",\"resources-base\": \"");
                sb.append(handlerContext.getExternalContext().getResponse().rewriteResourceURL("/", false));
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
                for (XFormsEventHandler handler: containingDocument.getStaticState().getKeyHandlers()) {
                    if (!first)
                        sb.append(',');

                    for (final String observer: handler.getObserversStaticIds()) {
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
