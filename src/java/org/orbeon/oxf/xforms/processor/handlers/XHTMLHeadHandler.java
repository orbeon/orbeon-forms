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
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
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

    private static final ResourceConfig[] stylesheets = {
            // Calendar stylesheets
            // TODO: move to YUI if possible
            new ResourceConfig("/ops/javascript/jscalendar/calendar-blue.css", null),
            // Yahoo! UI Library
            new ResourceConfig("/ops/css/yui/tree.css", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/css/yui/tree-check.css", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/css/yui/menu.css", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isMenuInUse(appearancesMap);
                }
            },
            // Other standard stylesheets
            new ResourceConfig("/config/theme/xforms.css", null)
    };

    private static final ResourceConfig[] scripts = {
            // Calendar scripts
            // TODO: move to YUI in the future if possible
            new ResourceConfig("/ops/javascript/jscalendar/calendar.js", null),
            new ResourceConfig("/ops/javascript/jscalendar/lang/calendar-en.js", null),
            new ResourceConfig("/ops/javascript/jscalendar/calendar-setup.js", null),
            // Yahoo UI Library
            new ResourceConfig("/ops/javascript/yui/yahoo.js", "/ops/javascript/yui/yahoo-min.js"),
            new ResourceConfig("/ops/javascript/yui/event.js", "/ops/javascript/yui/event-min.js"),
            new ResourceConfig("/ops/javascript/yui/dom.js", "/ops/javascript/yui/dom-min.js"),
            new ResourceConfig("/ops/javascript/yui/connection.js", "/ops/javascript/yui/connection-min.js"),
            new ResourceConfig("/ops/javascript/yui/animation.js", "/ops/javascript/yui/animation-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isRangeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/yui/dragdrop.js", "/ops/javascript/yui/dragdrop-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isRangeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/yui/slider.js", "/ops/javascript/yui/slider-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isRangeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/yui/treeview.js", "/ops/javascript/yui/treeview-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/yui/treeview-tasknode.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/yui/treeview-checkonclicknode.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/yui/container_core.js", "/ops/javascript/yui/container_core-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isMenuInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/yui/menu.js", "/ops/javascript/yui/menu-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isMenuInUse(appearancesMap);
                }
            },
            // HTML area
            new ResourceConfig("/ops/fckeditor/fckeditor.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isHtmlAreaInUse(appearancesMap);
                }
            },
            // Autocomplete
            new ResourceConfig("/ops/javascript/suggest-common.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isAutocompleteInUse(appearancesMap);
                }
            },
            new ResourceConfig("/ops/javascript/suggest-actb.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isAutocompleteInUse(appearancesMap);
                }
            },
            // Other standard scripts
            new ResourceConfig("/ops/javascript/wz_tooltip.js", null),// TODO: move to YUI
            new ResourceConfig("/ops/javascript/overlib_mini.js", null),// TODO: move to YUI
            new ResourceConfig("/ops/javascript/time-utils.js", null),// TODO: check who uses this
            // XForms client
            new ResourceConfig("/ops/javascript/xforms.js", null)
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
                    final boolean hasJavaScriptInitialization = xformsControl.hasJavaScriptInitialization() && (!xformsControl.isReadonly() && isStaticReadonly);
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

        // Stylesheets
        for (int i = 0; i < stylesheets.length; i++) {
            final ResourceConfig resourceConfig = stylesheets[i];
            if (resourceConfig.isInUse(appearancesMap)) {
                // Only include stylesheet if needed
                helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "link", new String[] {
                        "rel", "stylesheet", "href", resourceConfig.getResource(), "type", "text/css"});
            }
        }

        // Scripts
        if (!containingDocument.isReadonly()) {

            for (int i = 0; i < scripts.length; i++) {
                final ResourceConfig resourceConfig = scripts[i];
                if (resourceConfig.isInUse(appearancesMap)) {
                    // Only include script if needed
                    helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                        "type", "text/javascript", "src", resourceConfig.getResource()});
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
            final String serverBase = externalContext.getResponse().rewriteResourceURL("/", true);
            // TODO: store server base

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

    private static class ResourceConfig {
        private String fullResource;
        private String minResource;

        public ResourceConfig(String fullResource, String minResource) {
            this.fullResource = fullResource;
            this.minResource = minResource;
        }

        public String getResource() {
            // Load minimal resource if requested and there exists a minimal resource
            final boolean isMinimal = XFormsUtils.isMinimalResources();
            return (isMinimal && minResource != null) ? minResource : fullResource;
        }

        public boolean isInUse(Map appearancesMap) {
            return true;
        }

        protected boolean isInUse(Map appearancesMap, String controlName) {
            final Map controlMap = (Map) appearancesMap.get(controlName);
            return controlMap != null;
        }

        protected boolean isInUse(Map appearancesMap, String controlName, String appearanceOrMediatypeName) {
            final Map controlMap = (Map) appearancesMap.get(controlName);
            if (controlMap == null) return false;
            final Object controlAppearanceOrMediatypeList = controlMap.get(appearanceOrMediatypeName);
            return controlAppearanceOrMediatypeList != null;
        }

        protected boolean isRangeInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "range");
        }

        protected boolean isTreeInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "select1", XFormsSelect1Control.TREE_APPEARANCE) || isInUse(appearancesMap, "select", XFormsSelect1Control.TREE_APPEARANCE);
        }

        protected boolean isMenuInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "select1", XFormsSelect1Control.MENU_APPEARANCE) || isInUse(appearancesMap, "select", XFormsSelect1Control.MENU_APPEARANCE);
        }

        protected boolean isAutocompleteInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "select1", XFormsSelect1Control.AUTOCOMPLETE_APPEARANCE);
        }

        protected boolean isHtmlAreaInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "textarea", "text/html");
        }
    }
}
