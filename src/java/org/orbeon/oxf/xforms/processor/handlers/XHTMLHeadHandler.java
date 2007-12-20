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

import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.processor.XFormsFeatures;
import org.orbeon.oxf.xforms.processor.XFormsResourceServer;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * Handle xhtml:head.
 */
public class XHTMLHeadHandler extends HandlerBase {

    private String formattingPrefix;

    public XHTMLHeadHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final ExternalContext.Response response = externalContext.getResponse();

        // Declare xmlns:f
        formattingPrefix = handlerContext.findFormattingPrefixDeclare();

        // Open head element
        contentHandler.startElement(uri, localname, qName, attributes);

        final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
        final String prefix = XMLUtils.prefixFromQName(qName); // current prefix for XHTML

        // Gather information about controls appearances
        final Map appearancesMap = new HashMap();
        {
            final XFormsControls xformsControls = containingDocument.getXFormsControls();
            xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorListener() {
                public void startVisitControl(XFormsControl xformsControl) {
                    final String controlName = xformsControl.getName();

                    // Don't run JavaScript initialization if the control is static readonly (could change in the
                    // future if some static readonly controls require JS initialization)
                    final boolean hasJavaScriptInitialization = xformsControl.hasJavaScriptInitialization() && !xformsControl.isStaticReadonly();
                    if (hasJavaScriptInitialization) {
                        Map listForControlNameMap = (Map) appearancesMap.get(controlName);
                        if (listForControlNameMap == null) {
                            listForControlNameMap = new HashMap();
                            appearancesMap.put(xformsControl.getName(), listForControlNameMap);
                        }
                        final String controlAppearanceOrMediatype;
                        {
                            final String controlAppearance = xformsControl.getAppearance();
                            controlAppearanceOrMediatype = (controlAppearance != null) ? controlAppearance : xformsControl.getMediatype();
                        }

                        List idsForAppearanceOrMediatypeList = (List) listForControlNameMap.get(controlAppearanceOrMediatype);
                        if (idsForAppearanceOrMediatypeList == null) {
                            idsForAppearanceOrMediatypeList = new ArrayList();
                            listForControlNameMap.put(controlAppearanceOrMediatype, idsForAppearanceOrMediatypeList);
                        }
                        idsForAppearanceOrMediatypeList.add(xformsControl.getEffectiveId());
                    }
                }

                public void endVisitControl(XFormsControl xformsControl) {}
            });
        }

        // Create prefix for combined resources if needed
        final boolean isMinimal = XFormsProperties.isMinimalResources(containingDocument);
        final String combinedResourcesPrefix = XFormsFeatures.getCombinedResourcesPrefix(containingDocument, appearancesMap, isMinimal);

        final boolean isCombineResources = XFormsProperties.isCombinedResources(containingDocument);
        final boolean isCacheCombinedResources = isCombineResources && XFormsProperties.isCacheCombinedResources();
        if (isCombineResources) {
            XFormsServer.logger.debug("XForms resources - creating xhtml:head with combined resources.");
            if (isCacheCombinedResources) {
                XFormsServer.logger.debug("XForms resources - attempting to cache combined resources.");
            }
        }

        // Stylesheets
        final AttributesImpl attributesImpl = new AttributesImpl();
        if (isCombineResources) {
            final String combinedResourceName = combinedResourcesPrefix + ".css";

            attributesImpl.clear();
            final String[] attributesList = new String[] { "rel", "stylesheet", "href", response.rewriteResourceURL(combinedResourceName, false), "type", "text/css" };
            ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
            attributesImpl.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "link", attributesImpl);

            if (isCacheCombinedResources) {
                // Attempt to cache combined resources
                // Do it at this point so that deployments using an HTTP server front-end can access the resource on disk directly
                final List resources = XFormsFeatures.getCSSResources(appearancesMap);
                final long combinedLastModified = XFormsResourceServer.computeCombinedLastModified(resources, isMinimal);
                XFormsResourceServer.cacheResources(resources, response, combinedResourceName, combinedLastModified, true, isMinimal);
            }
        } else {
            for (Iterator i = XFormsFeatures.getCSSResources(appearancesMap).iterator(); i.hasNext();) {
                final XFormsFeatures.ResourceConfig resourceConfig = (XFormsFeatures.ResourceConfig) i.next();
                // Only include stylesheet if needed
                attributesImpl.clear();
                final String[] attributesList = new String[] { "rel", "stylesheet", "href", response.rewriteResourceURL(resourceConfig.getResourcePath(isMinimal), false), "type", "text/css" };
                ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                attributesImpl.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");
                helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "link", attributesImpl);
            }
        }

        // Scripts
        if (!XFormsProperties.isReadonly(containingDocument)) {

            if (isCombineResources) {
                final String combinedResourceName = combinedResourcesPrefix + ".js";

                attributesImpl.clear();
                final String[] attributesList = new String[] { "type", "text/javascript", "src", response.rewriteResourceURL(combinedResourceName, false) };
                ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                attributesImpl.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");
                helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", attributesImpl);

                if (isCacheCombinedResources) {
                    // Attempt to cache combined resources
                    // Do it at this point so that deployments using an HTTP server front-end can access the resource on disk directly
                    final List resources = XFormsFeatures.getJavaScriptResources(appearancesMap);
                    final long combinedLastModified = XFormsResourceServer.computeCombinedLastModified(resources, isMinimal);
                    XFormsResourceServer.cacheResources(resources, response, combinedResourceName, combinedLastModified, false, isMinimal);
                }

            } else {
                for (Iterator i = XFormsFeatures.getJavaScriptResources(appearancesMap).iterator(); i.hasNext();) {
                    final XFormsFeatures.ResourceConfig resourceConfig = (XFormsFeatures.ResourceConfig) i.next();
                    // Only include stylesheet if needed

                    attributesImpl.clear();
                    final String[] attributesList =  new String[] { "type", "text/javascript", "src", response.rewriteResourceURL(resourceConfig.getResourcePath(isMinimal), false) };
                    ContentHandlerHelper.populateAttributes(attributesImpl, attributesList);
                    attributesImpl.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");
                    helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", attributesImpl);
                }
            }


            // Configuration properties
            {
                final Map nonDefaultProperties = containingDocument.getStaticState().getNonDefaultProperties();

                // Handle heartbeat delay specially
                final long heartbeatDelay = XFormsStateManager.getHeartbeatDelay(containingDocument, externalContext);

                FastStringBuffer sb = null;
                if (heartbeatDelay != -1) {
                    // First property found
                    helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                        "type", "text/javascript"});
                    sb = new FastStringBuffer("var opsXFormsProperties = {");

                    sb.append('\"');
                    sb.append(XFormsProperties.SESSION_HEARTBEAT_DELAY_PROPERTY);
                    sb.append("\",\"");
                    sb.append(Long.toString(heartbeatDelay));
                    sb.append('\"');
                }

                if (heartbeatDelay != -1 || nonDefaultProperties.size() > 0) {

                    for (Iterator i = nonDefaultProperties.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry = (Map.Entry) i.next();
                        final String propertyName = (String) currentEntry.getKey();
                        final String propertyValue = (String) currentEntry.getValue();

                        final XFormsProperties.PropertyDefinition propertyDefinition = (XFormsProperties.PropertyDefinition) XFormsProperties.SUPPORTED_DOCUMENT_PROPERTIES.get(propertyName);
                        if (propertyDefinition.isPropagateToClient()) {

                            if (sb == null) {
                                // First property found
                                helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                                    "type", "text/javascript"});
                                sb = new FastStringBuffer("var opsXFormsProperties = {");
                            } else {
                                // Subsequent property found
                                sb.append(',');
                            }

                            sb.append('\"');
                            sb.append(propertyName);
                            sb.append("\",\"");
                            sb.append(propertyValue);
                            sb.append('\"');
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
            final String focusElementId = containingDocument.getClientFocusEffectiveControlId(pipelineContext);
            if (scriptsToDeclare != null || focusElementId != null) {
                helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
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

                if (focusElementId != null || (scriptsToRun != null)) {
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
                            sb.append(script.getEventHandlerContainerId());
                            sb.append("\");");
                        }
                    }

                    sb.append(" }");

                    helper.text(sb.toString());
                }

                helper.endElement();
            }

            // Store information about "special" controls that need JavaScript initialization
            {
                helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                        "type", "text/javascript"});

                // Produce JSON output
                if (appearancesMap.size() > 0) {
                    final FastStringBuffer sb = new FastStringBuffer("var opsXFormsControls = {\"controls\":{");

                    for (Iterator i = appearancesMap.entrySet().iterator(); i.hasNext();) {
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
