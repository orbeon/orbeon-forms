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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.processor.XFormsFeatures;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.*;

/**
 * Handle xhtml:head.
 */
public class XHTMLHeadHandler extends HandlerBase {

    public XHTMLHeadHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // Open head element
        contentHandler.startElement(uri, localname, qName, attributes);

        final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
        final String prefix = XMLUtils.prefixFromQName(qName); // current prefix for XHTML

        // Gather information about controls appearances
        final Map appearancesMap = new HashMap();
        {
            final XFormsControls xformsControls = containingDocument.getXFormsControls();
            final boolean isStaticReadonly = containingDocument.getReadonlyAppearance().equals(XFormsConstants.XXFORMS_READONLY_APPEARANCE_STATIC_VALUE);
            xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorListener() {
                public void startVisitControl(XFormsControl xformsControl) {
                    final String controlName = xformsControl.getName();

                    // Don't run JavaScript initialization if the control is static readonly (could change in the
                    // future if some static readonly controls require JS initialization)
                    final boolean hasJavaScriptInitialization = xformsControl.hasJavaScriptInitialization() && !(xformsControl.isReadonly() && isStaticReadonly);
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
        final String combinedResourcesPrefix = XFormsFeatures.getCombinedResourcesName(appearancesMap);

        // Stylesheets
        if (XFormsUtils.isCombineResources()) {
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "link", new String[] {
                        "rel", "stylesheet", "href", combinedResourcesPrefix + ".css", "type", "text/css"});
        } else {
            for (Iterator i = XFormsFeatures.getCSSResources(appearancesMap).iterator(); i.hasNext();) {
                final XFormsFeatures.ResourceConfig resourceConfig = (XFormsFeatures.ResourceConfig) i.next();
                // Only include stylesheet if needed
                helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "link", new String[] {
                        "rel", "stylesheet", "href", resourceConfig.getResourcePath(), "type", "text/css"});
            }
        }

        // Scripts
        if (!containingDocument.isReadonly()) {

            if (XFormsUtils.isCombineResources()) {
                helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                        "type", "text/javascript", "src", combinedResourcesPrefix + ".js"});
            } else {
                for (Iterator i = XFormsFeatures.getJavaScriptResources(appearancesMap).iterator(); i.hasNext();) {
                    final XFormsFeatures.ResourceConfig resourceConfig = (XFormsFeatures.ResourceConfig) i.next();
                    // Only include stylesheet if needed
                    helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                            "type", "text/javascript", "src", resourceConfig.getResourcePath()});
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
                    final StringBuffer sb = new StringBuffer("\nfunction xformsPageLoadedServer() { ");

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
        }

        // Store information about "special" controls that need JavaScript initialization
        {
            helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                    "type", "text/javascript"});
            {
                final String applicationBase = externalContext.getResponse().rewriteResourceURL("/", true);
                helper.text("var opsXFormsServerBase = \"" + applicationBase + "\";");
            }

            // Produce JSON output
            if (appearancesMap.size() > 0) {
                final StringBuffer sb = new StringBuffer("var opsXFormsControls = {\"controls\":{");

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

    public void end(String uri, String localname, String qName) throws SAXException {

        // Close head element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
