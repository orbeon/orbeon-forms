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

import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.XFormsControls;
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

    private static final String[] stylesheets = {
            // Calendar stylesheets
            "/config/theme/jscalendar/calendar-blue.css",
            // Yahoo! UI Library
            "/ops/css/tree.css",
            "/ops/css/tree-check.css",
            "/ops/css/menu.css",
            // Other standard stylesheets
            "/config/theme/xforms.css"
    };

    private static final String[] scripts = {
            // Calendar scripts
            "/config/theme/jscalendar/calendar.js",
            "/config/theme/jscalendar/lang/calendar-en.js",
            "/config/theme/jscalendar/calendar-setup.js",
            // Yahoo UI Library
            "/ops/javascript/yahoo.js",
            "/ops/javascript/event.js",
            "/ops/javascript/dom.js",
            "/ops/javascript/connection.js",
            "/ops/javascript/animation.js",
            "/ops/javascript/dragdrop.js",
            "/ops/javascript/slider.js",
            "/ops/javascript/treeview.js",
            "/ops/javascript/treeview-tasknode.js",
            "/ops/javascript/treeview-checkonclicknode.js",
            "/ops/javascript/container_core.js",
            "/ops/javascript/menu.js",
            // HTML area
            "/ops/fckeditor/fckeditor.js",
            // Other standard scripts
            "/config/theme/javascript/xforms-style.js",
            "/ops/javascript/wz_tooltip.js",
            "/ops/javascript/overlib_mini.js",
            "/ops/javascript/time-utils.js",
            "/ops/javascript/xforms.js",
            "/ops/javascript/suggest-common.js",
            "/ops/javascript/suggest-actb.js"
    };

    public XHTMLHeadHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // Open head element
        contentHandler.startElement(uri, localname, qName, attributes);

        final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
        final String prefix = XMLUtils.prefixFromQName(qName); // current prefix for XHTML

        // Stylesheets
        for (int i = 0; i < stylesheets.length; i++) {
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "link", new String[] {
                    "rel", "stylesheet", "href", stylesheets[i], "type", "text/css"});
        }

        // Scripts
        if (!containingDocument.isReadonly()) {
            for (int i = 0; i < scripts.length; i++) {
                helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                    "type", "text/javascript", "src", scripts[i]});
            }

            // User-defined scripts (with xxforms:script)
            final Map scripts = containingDocument.getScripts();
            final String focusElementId = containingDocument.getClientFocusEffectiveControlId();
            if (scripts != null || focusElementId != null) {
                helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                    "type", "text/javascript"});

                if (scripts != null) {
                    for (Iterator i = scripts.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry = (Map.Entry) i.next();
                        helper.text("\nfunction " + XFormsUtils.scriptIdToScriptName(currentEntry.getKey().toString()) + "(event) {\n");
                        helper.text(currentEntry.getValue().toString());
                        helper.text("}\n");
                    }
                }

                if (focusElementId != null) {
                    helper.text("\nfunction xformsPageLoadedServer() { ORBEON.xforms.Controls.setFocus(\"" + focusElementId + "\") }\n");
                }

                helper.endElement();
            }
        }

        // Store information about "special" controls that need JavaScript initialization
        {
            final XFormsControls xformsControls = containingDocument.getXFormsControls();

            final String serverBase = externalContext.getResponse().rewriteResourceURL("/", true);
            // TODO: store server base
            // TODO: produce JSON

            // Gather information about controls appearances
            final Map appearancesMap = new HashMap();
            xformsControls.getCurrentControlsState().visitXFormsControlFollowRepeats(pipelineContext, xformsControls, new XFormsControls.XFormsControlVisitorListener() {
                public void startVisitControl(XFormsControl xformsControl) {
                    final String controlName = xformsControl.getName();

                    final boolean hasJavaScriptInitialization = xformsControl.hasJavaScriptInitialization();
                    if (hasJavaScriptInitialization) {
                        Map listForControlNameMap = (Map) appearancesMap.get(controlName);
                        if (listForControlNameMap == null) {
                            listForControlNameMap = new HashMap();
                            appearancesMap.put(xformsControl.getName(), listForControlNameMap);
                        }
                        final String controlAppearance = xformsControl.getAppearance();
                        List idsForAppearanceList = (List) listForControlNameMap.get(controlAppearance);
                        if (idsForAppearanceList == null) {
                            idsForAppearanceList = new ArrayList();
                            listForControlNameMap.put(controlAppearance != null ? controlAppearance : "", idsForAppearanceList);
                        }
                        idsForAppearanceList.add(xformsControl.getEffectiveId());
                    }
                }

                public void endVisitControl(XFormsControl xformsControl) {}
            });

            // Produce JSON output
            if (appearancesMap.size() > 0) {
                final StringBuffer sb = new StringBuffer("var opsXFormsControls = {\"controls\":");

                for (Iterator i = appearancesMap.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry1 = (Map.Entry) i.next();
                    final String controlName = (String) currentEntry1.getKey();
                    final Map controlMap = (Map) currentEntry1.getValue();

                    sb.append("{\"");
                    sb.append(controlName);
                    sb.append("\":");

                    for (Iterator j = controlMap.entrySet().iterator(); j.hasNext();) {
                        final Map.Entry currentEntry2 = (Map.Entry) j.next();
                        final String controlAppearance = (String) currentEntry2.getKey();
                        final List idsForAppearanceList = (List) currentEntry2.getValue();

                        sb.append("{\"");
                        sb.append(controlAppearance);
                        sb.append("\":[");

                        for (Iterator k = idsForAppearanceList.iterator(); k.hasNext();) {
                            final String controlId = (String) k.next();
                            sb.append('"');
                            sb.append(controlId);
                            sb.append('"');
                            if (k.hasNext())
                                sb.append(',');
                        }

                        sb.append("]}");
                        if (j.hasNext())
                            sb.append(',');
                    }

                    sb.append("}");
                    if (i.hasNext())
                        sb.append(',');
                }

                sb.append("};");

                helper.startElement(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                    "type", "text/javascript"});

                helper.text(sb.toString());

                helper.endElement();

            }
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Close head element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
