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

import org.apache.commons.collections.map.CompositeMap;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsFeatures;
import org.orbeon.oxf.xforms.processor.XFormsResourceServer;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.Element;

import java.util.*;

/**
 * Handle xhtml:head.
 */
public class XHTMLHeadHandler extends XFormsBaseHandler {

    private String formattingPrefix;

    public XHTMLHeadHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // Register control handlers on controller
        {
            final ElementHandlerController controller = handlerContext.getController();
            controller.registerHandler(XXFormsTextHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "text");
        }

        // Declare xmlns:f
        formattingPrefix = handlerContext.findFormattingPrefixDeclare();

        // Open head element
        contentHandler.startElement(uri, localname, qName, attributes);

        final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
        final String xhtmlPrefix = XMLUtils.prefixFromQName(qName); // current prefix for XHTML

        // Gather information about appearances of controls which use Script
        // Map<String controlName, Map<String appearanceOrMediatype, List<String effectiveId>>>
        // TODO: This would probably be done better, and more correctly, statically, in XFormsStaticState
        final Map javaScriptControlsAppearancesMap = new HashMap();
        {
            final XFormsControls xformsControls = containingDocument.getControls();
            xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorListener() {
                public void startVisitControl(XFormsControl control) {
                    final String controlName = control.getName();

                    // Don't run JavaScript initialization if the control is static readonly (could change in the
                    // future if some static readonly controls require JS initialization)
                    final boolean hasJavaScriptInitialization = control.hasJavaScriptInitialization() && !control.isStaticReadonly();
                    if (hasJavaScriptInitialization) {
                        Map listForControlNameMap = (Map) javaScriptControlsAppearancesMap.get(controlName);
                        if (listForControlNameMap == null) {
                            listForControlNameMap = new HashMap();
                            javaScriptControlsAppearancesMap.put(control.getName(), listForControlNameMap);
                        }
                        final String controlAppearanceOrMediatype;
                        {
                            final String controlAppearance = control.getAppearance();
                            controlAppearanceOrMediatype = (controlAppearance != null) ? controlAppearance : control.getMediatype();
                        }

                        List idsForAppearanceOrMediatypeList = (List) listForControlNameMap.get(controlAppearanceOrMediatype);
                        if (idsForAppearanceOrMediatypeList == null) {
                            idsForAppearanceOrMediatypeList = new ArrayList();
                            listForControlNameMap.put(controlAppearanceOrMediatype, idsForAppearanceOrMediatypeList);
                        }
                        idsForAppearanceOrMediatypeList.add(control.getEffectiveId());
                    }
                }

                public void endVisitControl(XFormsControl xformsControl) {}
            });
        }

        // Create prefix for combined resources if needed
        final boolean isMinimal = XFormsProperties.isMinimalResources(containingDocument);
        final boolean isVersionedResources = URLRewriterUtils.isResourcesVersioned();
        final String combinedResourcesPrefix = XFormsFeatures.getCombinedResourcesPrefix(containingDocument, javaScriptControlsAppearancesMap, isMinimal, isVersionedResources);

        final boolean isCombineResources = XFormsProperties.isCombinedResources(containingDocument);
        final boolean isCacheCombinedResources = isCombineResources && XFormsProperties.isCacheCombinedResources();
        if (isCombineResources) {
            containingDocument.logDebug("XForms resources", "creating xhtml:head with combined resources");
            if (isCacheCombinedResources) {
                containingDocument.logDebug("XForms resources", "attempting to cache combined resources");
            }
        }

        // Stylesheets
        final AttributesImpl attributesImpl = new AttributesImpl();
        {
            // Main CSS resources
            if (isCombineResources) {
                final String combinedResourceName = combinedResourcesPrefix + ".css";

                attributesImpl.clear();
                final String[] attributesList = new String[] { "rel", "stylesheet", "href", combinedResourceName, "type", "text/css", "media", "all" };
                ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "link", attributesImpl);

                if (isCacheCombinedResources) {
                    // Attempt to cache combined resources
                    // Do it at this point so that deployments using an HTTP server front-end can access the resource on disk directly
                    final List resources = XFormsFeatures.getCSSResources(containingDocument, javaScriptControlsAppearancesMap);
                    final long combinedLastModified = XFormsResourceServer.computeCombinedLastModified(resources, isMinimal);
                    XFormsResourceServer.cacheResources(resources, pipelineContext, combinedResourceName, combinedLastModified, true, isMinimal);
                }
            } else {
                for (Iterator i = XFormsFeatures.getCSSResources(containingDocument, javaScriptControlsAppearancesMap).iterator(); i.hasNext();) {
                    final XFormsFeatures.ResourceConfig resourceConfig = (XFormsFeatures.ResourceConfig) i.next();
                    // Only include stylesheet if needed
                    attributesImpl.clear();
                    final String[] attributesList = new String[] { "rel", "stylesheet", "href", resourceConfig.getResourcePath(isMinimal), "type", "text/css", "media", "all"  };
                    ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                    helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "link", attributesImpl);
                }
            }

            // XBL resources
            final List xblStyles = containingDocument.getStaticState().getXBLStyles();
            if (xblStyles != null) {
                for (Iterator i = xblStyles.iterator(); i.hasNext();) {
                    final Element styleElement = (Element) i.next();
                    attributesImpl.clear();
                    if (styleElement.attributeValue("src") != null) {
                        // xhtml:link
                        final String[] attributesList = new String[] { "rel", "stylesheet", "href", styleElement.attributeValue("src"), "type", "text/css", "media", styleElement.attributeValue("media") };
                        ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                        helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "link", attributesImpl);
                    } else {
                        // xhtml:style
                        final String[] attributesList = new String[] { "rel", "stylesheet", "type", "text/css", "media", styleElement.attributeValue("media") };
                        ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "style", attributesImpl);
                        helper.text(styleElement.getText());
                        helper.endElement();
                    }
                }
            }
        }

        // Scripts
        // TODO: Have option to put this at the bottom of the page. See theme-plain.xsl and http://developer.yahoo.com/performance/rules.html#js_bottom -->
        if (!handlerContext.isNoScript() && !XFormsProperties.isReadonly(containingDocument)) {

            // Main JavaScript resources
            if (isCombineResources) {
                final String combinedResourceName = combinedResourcesPrefix + ".js";

                attributesImpl.clear();
                final String[] attributesList = new String[] { "type", "text/javascript", "src", combinedResourceName };
                ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", attributesImpl);

                if (isCacheCombinedResources) {
                    // Attempt to cache combined resources
                    // Do it at this point so that deployments using an HTTP server front-end can access the resource on disk directly
                    final List resources = XFormsFeatures.getJavaScriptResources(containingDocument, javaScriptControlsAppearancesMap);
                    final long combinedLastModified = XFormsResourceServer.computeCombinedLastModified(resources, isMinimal);
                    XFormsResourceServer.cacheResources(resources, pipelineContext, combinedResourceName, combinedLastModified, false, isMinimal);
                }

            } else {
                for (Iterator i = XFormsFeatures.getJavaScriptResources(containingDocument, javaScriptControlsAppearancesMap).iterator(); i.hasNext();) {
                    final XFormsFeatures.ResourceConfig resourceConfig = (XFormsFeatures.ResourceConfig) i.next();
                    // Only include stylesheet if needed

                    attributesImpl.clear();
                    final String[] attributesList =  new String[] { "type", "text/javascript", "src", resourceConfig.getResourcePath(isMinimal) };
                    ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                    helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", attributesImpl);
                }
            }

            // XBL scripts
            final List xblScripts = containingDocument.getStaticState().getXBLScripts();
            if (xblScripts != null) {
                for (Iterator i = xblScripts.iterator(); i.hasNext();) {
                    final Element scriptElement = (Element) i.next();
                    attributesImpl.clear();
                    if (scriptElement.attributeValue("src") != null) {
                        // xhtml:script with @src
                        final String[] attributesList = new String[] { "type", "text/javascript", "src", scriptElement.attributeValue("src") };
                        ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                        helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", attributesImpl);
                    } else {
                        // xhtml:script without @src
                        final String[] attributesList = new String[] { "type", "text/javascript" };
                        ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", attributesImpl);
                        helper.text(scriptElement.getText());
                        helper.endElement();
                    }
                }
            }

            // Configuration properties
            {
                final Map clientPropertiesMap;
                {
                    // Dynamic properties
                    final Map dynamicProperties = new HashMap();
                    {
                        // Heartbeat delay
                        {
                            final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(XFormsProperties.SESSION_HEARTBEAT_DELAY_PROPERTY);
                            final long heartbeatDelay = XFormsStateManager.getHeartbeatDelay(containingDocument, handlerContext.getExternalContext());
                            if (heartbeatDelay != ((Number) propertyDefinition.getDefaultValue()).longValue())
                                dynamicProperties.put(XFormsProperties.SESSION_HEARTBEAT_DELAY_PROPERTY, new Long(heartbeatDelay));
                        }

                        // Produce JavaScript paths for use on the client
                        {
                            // FCKeditor path
                            {
                                final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(XFormsProperties.FCK_EDITOR_BASE_PATH_PROPERTY);
                                final String fckEditorPath = isVersionedResources ? "/" + Version.getVersion() + propertyDefinition.getDefaultValue() : (String) propertyDefinition.getDefaultValue();
                                if (!fckEditorPath.equals(propertyDefinition.getDefaultValue()))
                                    dynamicProperties.put(XFormsProperties.FCK_EDITOR_BASE_PATH_PROPERTY, fckEditorPath);
                            }
                        }

                        // Help events
                        {
                            final boolean hasHandlerForXFormsHelp = containingDocument.getControls().hasHandlerForEvent(XFormsEvents.XFORMS_HELP);
                            if (hasHandlerForXFormsHelp) {
                                dynamicProperties.put(XFormsProperties.HELP_HANDLER_PROPERTY, Boolean.TRUE);
                            }
                        }

                        // Application version
                        {
                            // This is not an XForms property but we want to expose it on the client
                            if (isVersionedResources != URLRewriterUtils.RESOURCES_VERSIONED_DEFAULT)
                                dynamicProperties.put(URLRewriterUtils.RESOURCES_VERSIONED_PROPERTY, Boolean.toString(isVersionedResources));

                            if (isVersionedResources) {
                                final String applicationVersion = URLRewriterUtils.getApplicationResourceVersion();
                                if (applicationVersion != null) {
                                    // This is not an XForms property but we want to expose it on the client 
                                    dynamicProperties.put(URLRewriterUtils.RESOURCES_VERSION_NUMBER_PROPERTY, applicationVersion);
                                }
                            }
                        }

                        // Offline mode
//                        if (containingDocument.getStaticState().isHasOfflineSupport()) {
//                            dynamicProperties.put(XFormsProperties.OFFLINE_SUPPORT_PROPERTY, Boolean.TRUE);
//                        }
                    }

                    final Map nonDefaultProperties = containingDocument.getStaticState().getNonDefaultProperties();
                    clientPropertiesMap = new CompositeMap(new Map[] { nonDefaultProperties, dynamicProperties });
                }
                
                FastStringBuffer sb = null;
                if (clientPropertiesMap.size() > 0) {

                    for (Iterator i = clientPropertiesMap.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry = (Map.Entry) i.next();
                        final String propertyName = (String) currentEntry.getKey();
                        final Object propertyValue = currentEntry.getValue();

                        final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
                        if (propertyDefinition != null && propertyDefinition.isPropagateToClient()
                                || URLRewriterUtils.RESOURCES_VERSION_NUMBER_PROPERTY.equals(propertyName)
                                || URLRewriterUtils.RESOURCES_VERSIONED_PROPERTY.equals(propertyName)) {

                            if (sb == null) {
                                // First property found
                                helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                                    "type", "text/javascript"});
                                sb = new FastStringBuffer("var opsXFormsProperties = {");
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

            // User-defined scripts (with xxforms:script)
            final Map scriptsToDeclare = containingDocument.getScripts();
            final String focusElementId = containingDocument.getClientFocusEffectiveControlId();
            final List messagesToRun = containingDocument.getMessagesToRun();
            if (scriptsToDeclare != null || focusElementId != null || messagesToRun != null) {
                helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                    "type", "text/javascript"});

                if (scriptsToDeclare != null) {
                    for (Iterator i = scriptsToDeclare.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry = (Map.Entry) i.next();
                        helper.text("\nfunction " + XFormsUtils.scriptIdToScriptName(currentEntry.getKey().toString()) + "(event) {\n");
                        helper.text(currentEntry.getValue().toString());
                        helper.text("}\n");
                    }
                }

                final List scriptsToRun = containingDocument.getScriptsToRun();

                if (focusElementId != null || scriptsToRun != null || messagesToRun != null) {
                    final FastStringBuffer sb = new FastStringBuffer("\nfunction xformsPageLoadedServer() { ");

                    // Initial setfocus if present
                    if (focusElementId != null) {
                        sb.append("ORBEON.xforms.Controls.setFocus(\"");
                        sb.append(focusElementId);
                        sb.append("\");");
                    }

                    // Initial xxforms:script executions if present
                    if (scriptsToRun != null) {
                        for (Iterator i = scriptsToRun.iterator(); i.hasNext();) {
                            final XFormsContainingDocument.Script script = (XFormsContainingDocument.Script) i.next();
                            sb.append("ORBEON.xforms.Server.callUserScript(\"");
                            sb.append(script.getFunctionName());
                            sb.append("\",\"");
                            sb.append(script.getEventTargetId());
                            sb.append("\",\"");
                            sb.append(script.getEventObserverId());
                            sb.append("\");");
                        }
                    }

                    // Initial xforms:message to run if present
                    if (messagesToRun != null) {
                        for (Iterator i = messagesToRun.iterator(); i.hasNext();) {
                            final XFormsContainingDocument.Message message = (XFormsContainingDocument.Message) i.next();
                            if ("modal".equals(message.getLevel())) {
                                // TODO: should not call directly alert() but a client-side method
                                sb.append("alert(\"");
                                sb.append(XFormsUtils.escapeJavaScript(message.getMessage()));
                                sb.append("\");");
                            }
                        }
                    }

                    sb.append(" }");

                    helper.text(sb.toString());
                }

                helper.endElement();
            }

            // Store information about "special" controls that need JavaScript initialization
            {
                helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                        "type", "text/javascript"});

                // Produce JSON output
                if (javaScriptControlsAppearancesMap.size() > 0) {
                    final FastStringBuffer sb = new FastStringBuffer("var opsXFormsControls = {\"controls\":{");

                    for (Iterator i = javaScriptControlsAppearancesMap.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry1 = (Map.Entry) i.next();
                        final String controlName = (String) currentEntry1.getKey();
                        final Map controlMap = (Map) currentEntry1.getValue();

                        sb.append("\"");
                        sb.append(controlName);
                        sb.append("\":{");

                        for (Iterator j = controlMap.entrySet().iterator(); j.hasNext();) {
                            final Map.Entry currentEntry2 = (Map.Entry) j.next();
                            final String controlAppearance = (String) currentEntry2.getKey();
                            final List idsForAppearanceList = (List) currentEntry2.getValue();

                            sb.append('"');
                            sb.append(controlAppearance != null ? controlAppearance : "");
                            sb.append("\":[");

                            for (Iterator k = idsForAppearanceList.iterator(); k.hasNext();) {
                                final String controlId = (String) k.next();
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

                    sb.append("}};");

                    helper.text(sb.toString());
                }

                helper.endElement();
            }
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Close head element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);

        // Undeclare xmlns:f
        handlerContext.findFormattingPrefixUndeclare(formattingPrefix);
    }
}
