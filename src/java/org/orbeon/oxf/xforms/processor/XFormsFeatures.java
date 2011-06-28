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
package org.orbeon.oxf.xforms.processor;

import org.dom4j.QName;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.XFormsConstants;

import java.util.ArrayList;
import java.util.List;

public class XFormsFeatures {

    private static final ResourceConfig[] stylesheets = {
            // Standard CSS
            new ResourceConfig("/ops/yui/container/assets/skins/sam/container.css", null),
            new ResourceConfig("/ops/yui/progressbar/assets/skins/sam/progressbar.css", null),
            // Calendar CSS
            new ResourceConfig("/ops/yui/calendar/assets/skins/sam/calendar.css", null),
            // Yahoo! UI Library
            new ResourceConfig("/ops/yui/treeview/assets/skins/sam/treeview.css", null) {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isTreeInUse(staticStateGlobalOps);
                }
            },
            new ResourceConfig("/ops/yui/examples/treeview/assets/css/check/tree.css", null) {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isTreeInUse(staticStateGlobalOps);
                }
            },
            new ResourceConfig("/ops/yui/menu/assets/skins/sam/menu.css", null) {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isMenuInUse(staticStateGlobalOps) || isYUIRTEInUse(staticStateGlobalOps);
                }
            },
            // HTML area
            new ResourceConfig("/ops/yui/editor/assets/skins/sam/editor.css", null) {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isYUIRTEInUse(staticStateGlobalOps);
                }
            },
            new ResourceConfig("/ops/yui/button/assets/skins/sam/button.css", null) {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isYUIRTEInUse(staticStateGlobalOps);
                }
            },
            // Other standard stylesheets
            new ResourceConfig("/config/theme/xforms.css", null),
            new ResourceConfig("/config/theme/error.css", null)
    };

    private static final ResourceConfig[] scripts = {
            // Yahoo UI Library
            new ResourceConfig("/ops/yui/yahoo/yahoo.js", "/ops/yui/yahoo/yahoo-min.js"),
            new ResourceConfig("/ops/yui/event/event.js", "/ops/yui/event/event-min.js"),
            new ResourceConfig("/ops/yui/dom/dom.js", "/ops/yui/dom/dom-min.js"),
            new ResourceConfig("/ops/yui/connection/connection.js", "/ops/yui/connection/connection-min.js"),
            new ResourceConfig("/ops/yui/element/element.js", "/ops/yui/element/element-min.js"),
            new ResourceConfig("/ops/yui/animation/animation.js", "/ops/yui/animation/animation-min.js"),
            new ResourceConfig("/ops/yui/progressbar/progressbar.js", "/ops/yui/progressbar/progressbar-min.js"),
            new ResourceConfig("/ops/yui/dragdrop/dragdrop.js", "/ops/yui/dragdrop/dragdrop-min.js"),
            new ResourceConfig("/ops/yui/container/container.js", "/ops/yui/container/container-min.js"),
            new ResourceConfig("/ops/yui/examples/container/assets/containerariaplugin.js", "/ops/yui/examples/container/assets/containerariaplugin-min.js"),
            new ResourceConfig("/ops/yui/calendar/calendar.js", "/ops/yui/calendar/calendar-min.js"),
            new ResourceConfig("/ops/yui/slider/slider.js", "/ops/yui/slider/slider-min.js") {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isRangeInUse(staticStateGlobalOps);
                }
            },
            new ResourceConfig("/ops/yui/treeview/treeview.js", "/ops/yui/treeview/treeview-min.js") {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isTreeInUse(staticStateGlobalOps);
                }
            },
            new ResourceConfig("/ops/yui/examples/treeview/assets/js/TaskNode.js", "/ops/yui/examples/treeview/assets/js/TaskNode-min.js") {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isTreeInUse(staticStateGlobalOps);
                }
            },
            new ResourceConfig("/ops/yui/menu/menu.js", "/ops/yui/menu/menu-min.js") {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isMenuInUse(staticStateGlobalOps) || isYUIRTEInUse(staticStateGlobalOps);
                }
            },
            // HTML area
            new ResourceConfig("/ops/yui/button/button.js", "/ops/yui/button/button-min.js") {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isYUIRTEInUse(staticStateGlobalOps);
                }
            },
            new ResourceConfig("/ops/yui/editor/editor.js", "/ops/yui/editor/editor-min.js") {
                public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
                    return isYUIRTEInUse(staticStateGlobalOps);
                }
            },
            // Underscore library
            new ResourceConfig("/ops/javascript/underscore/underscore.js", "/ops/javascript/underscore/underscore-min.js"),
            // XForms client
            new ResourceConfig("/ops/javascript/xforms.js",                                     "/ops/javascript/xforms-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/util/ExecutionQueue.js",                 "/ops/javascript/orbeon/util/ExecutionQueue-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/server/Server.js",                "/ops/javascript/orbeon/xforms/server/Server-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/server/AjaxServer.js",            "/ops/javascript/orbeon/xforms/server/AjaxServer-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/server/UploadServer.js",          "/ops/javascript/orbeon/xforms/server/UploadServer-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/LoadingIndicator.js",             "/ops/javascript/orbeon/xforms/LoadingIndicator-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/Form.js",                         "/ops/javascript/orbeon/xforms/Form-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/Page.js",                         "/ops/javascript/orbeon/xforms/Page-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Control.js",              "/ops/javascript/orbeon/xforms/control/Control-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/CalendarResources.js",    "/ops/javascript/orbeon/xforms/control/CalendarResources-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Calendar.js",             "/ops/javascript/orbeon/xforms/control/Calendar-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Upload.js",               "/ops/javascript/orbeon/xforms/control/Upload-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/RTEConfig.js",            "/ops/javascript/orbeon/xforms/control/RTEConfig-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/RTE.js",                  "/ops/javascript/orbeon/xforms/control/RTE-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Tree.js",                 "/ops/javascript/orbeon/xforms/control/Tree-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/action/Message.js",               "/ops/javascript/orbeon/xforms/action/Message-min.js")
    };

    public static class ResourceConfig {
        private String fullResource;
        private String minResource;

        public ResourceConfig(String fullResource, String minResource) {
            this.fullResource = fullResource;
            this.minResource = minResource;
        }

        public String getResourcePath(boolean tryMinimal) {
            // Load minimal resource if requested and there exists a minimal resource
            return (tryMinimal && minResource != null) ? minResource : fullResource;
        }

        public boolean isInUse(StaticStateGlobalOps staticStateGlobalOps) {
            // Default to true but can be overridden
            return true;
        }

        public static boolean isInUse(StaticStateGlobalOps staticStateGlobalOps, String controlName) {
            return staticStateGlobalOps != null && staticStateGlobalOps.hasControlByName(controlName);
        }

        public static boolean isInUse(StaticStateGlobalOps staticStateGlobalOps, String controlName, QName appearanceOrMediatypeName) {
            return staticStateGlobalOps != null && staticStateGlobalOps.hasControlAppearance(controlName, appearanceOrMediatypeName);
        }

        protected boolean isRangeInUse(StaticStateGlobalOps staticStateGlobalOps) {
            return isInUse(staticStateGlobalOps, "range");
        }

        protected boolean isTreeInUse(StaticStateGlobalOps staticStateGlobalOps) {
            return isInUse(staticStateGlobalOps, "select1", XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME) || isInUse(staticStateGlobalOps, "select", XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME);
        }

        protected boolean isMenuInUse(StaticStateGlobalOps staticStateGlobalOps) {
            return isInUse(staticStateGlobalOps, "select1", XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME) || isInUse(staticStateGlobalOps, "select", XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME);
        }

        private boolean isHtmlAreaInUse(StaticStateGlobalOps staticStateGlobalOps) {
            return isInUse(staticStateGlobalOps, "textarea", XFormsConstants.XXFORMS_RICH_TEXT_APPEARANCE_QNAME);
        }

        protected boolean isYUIRTEInUse(StaticStateGlobalOps staticStateGlobalOps) {
            return isHtmlAreaInUse(staticStateGlobalOps);
        }
    }

    public static List<ResourceConfig> getCSSResources(StaticStateGlobalOps staticStateGlobalOps) {
        final List<ResourceConfig> result = new ArrayList<ResourceConfig>();
        for (final ResourceConfig resourceConfig: stylesheets) {
            if (resourceConfig.isInUse(staticStateGlobalOps)) {
                // Only include stylesheet if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }

    public static List<ResourceConfig> getJavaScriptResources(StaticStateGlobalOps staticStateGlobalOps) {
        final List<ResourceConfig> result = new ArrayList<ResourceConfig>();
        for (final ResourceConfig resourceConfig: scripts) {
            if (resourceConfig.isInUse(staticStateGlobalOps)) {
                // Only include script if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }
}
