/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.common.Version;
import org.orbeon.saxon.om.FastStringBuffer;

import java.util.*;

public class XFormsFeatures {

    private static final FeatureConfig[] features = {
            new FeatureConfig("range", "range"),
            new FeatureConfig("tree", new String[] { "select", "select1" }, XFormsSelect1Control.TREE_APPEARANCE),
            new FeatureConfig("menu", "select1", XFormsSelect1Control.MENU_APPEARANCE),
            new FeatureConfig("autocomplete", "select1", XFormsSelect1Control.AUTOCOMPLETE_APPEARANCE),
            new FeatureConfig("htmlarea", "textarea", "text/html"),
            new FeatureConfig("dialog", "dialog")
    };
    
    public static class FeatureConfig {
        private String name;
//        private String id;
        private String[] controlNames;
        private String[] controlAppearanceOrMediatypes;

        public FeatureConfig(String name, String controlName) {
            this(name, new String[] { controlName }, (String[]) null);
        }

        public FeatureConfig(String name, String controlName, String controlAppearanceOrMediatype) {
            this(name, new String[] { controlName }, new String[] { controlAppearanceOrMediatype });
        }

        public FeatureConfig(String name, String[] controlNames, String controlAppearanceOrMediatype) {
            this(name, controlNames, new String[] { controlAppearanceOrMediatype });
        }

        public FeatureConfig(String name, String[] controlNames, String[] controlAppearanceOrMediatypes) {
            this.name = name;
//            this.id = SecureUtils.digestString(name, "md5", "base64");
            this.controlNames = controlNames;
            this.controlAppearanceOrMediatypes = controlAppearanceOrMediatypes;
        }

        public String getName() {
            return name;
        }

        public String getId() {
//            return id;
            return name;
        }

        public boolean isInUse(Map appearancesMap) {
            if (controlAppearanceOrMediatypes != null && controlAppearanceOrMediatypes.length > 0) {
                // Test by control name and appearance/mediatype
                for (int i = 0; i < controlNames.length; i++) {
                    for (int j = 0; j < controlAppearanceOrMediatypes.length; j++) {
                        if (ResourceConfig.isInUse(appearancesMap, controlNames[i], controlAppearanceOrMediatypes[j])) {
                            return true;
                        }
                    }
                }
            } else {
                // Test by control name only
                for (int i = 0; i < controlNames.length; i++) {
                    if (ResourceConfig.isInUse(appearancesMap, controlNames[i]))
                        return true;
                }
            }
            return false;
        }
    }

    private static Map featuresByIdMap = new HashMap();
    static {
        for (int i = 0; i < features.length; i++) {
            final FeatureConfig currentFeatureConfig = features[i];
            featuresByIdMap.put(currentFeatureConfig.getId(), currentFeatureConfig);
        }
    }

    private static final ResourceConfig[] stylesheets = {
            // Calendar stylesheets
            // TODO: move to YUI if possible
            new ResourceConfig("/ops/javascript/jscalendar/calendar-blue.css", null),
            new ResourceConfig("/ops/css/yui/container.css", null),
            // Yahoo! UI Library
            new ResourceConfig("/ops/css/yui/tree.css", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/css/yui/tree-check.css", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/css/yui/menu.css", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isMenuInUse(appearancesMap);
                }
                public String getFeatureName() { return "menu"; }
            },
            // NOTE: This doesn't work, probably because FCK editor files must be loaded in an iframe
//            new ResourceConfig("/ops/fckeditor/editor/skins/default/fck_editor.css", null) {
//                public boolean isInUse(Map appearancesMap) {
//                    return isHtmlAreaInUse(appearancesMap);
//                }
//                public String getFeatureName() { return "htmlarea"; }
//            },
            // Other standard stylesheets
            new ResourceConfig("/config/theme/xforms.css", null),
            new ResourceConfig("/config/theme/error.css", null)
    };

    private static final ResourceConfig[] scripts = {
            // Calendar scripts
            // TODO: move to YUI in the future if possible
             new ResourceConfig("/ops/javascript/jscalendar/calendar.js", "/ops/javascript/jscalendar/calendar-min.js"),// our min version
            new ResourceConfig("/ops/javascript/jscalendar/lang/calendar-en.js", "/ops/javascript/jscalendar/lang/calendar-en-min.js"),// our min version
            new ResourceConfig("/ops/javascript/jscalendar/calendar-setup.js", "/ops/javascript/jscalendar/calendar-setup-min.js"),// our min version
            // Yahoo UI Library
            new ResourceConfig("/ops/javascript/yui/yahoo.js", "/ops/javascript/yui/yahoo-min.js"),
            new ResourceConfig("/ops/javascript/yui/event.js", "/ops/javascript/yui/event-min.js"),
            new ResourceConfig("/ops/javascript/yui/dom.js", "/ops/javascript/yui/dom-min.js"),
            new ResourceConfig("/ops/javascript/yui/connection.js", "/ops/javascript/yui/connection-min.js"),
            new ResourceConfig("/ops/javascript/yui/animation.js", "/ops/javascript/yui/animation-min.js"),
            new ResourceConfig("/ops/javascript/yui/container.js", "/ops/javascript/yui/container-min.js"),
            new ResourceConfig("/ops/javascript/yui/dragdrop.js", "/ops/javascript/yui/dragdrop-min.js"),
            new ResourceConfig("/ops/javascript/yui/slider.js", "/ops/javascript/yui/slider-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isRangeInUse(appearancesMap);
                }
                public String getFeatureName() { return "range"; }
            },
            new ResourceConfig("/ops/javascript/yui/treeview.js", "/ops/javascript/yui/treeview-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/javascript/yui/treeview-tasknode.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/javascript/yui/treeview-checkonclicknode.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/javascript/yui/menu.js", "/ops/javascript/yui/menu-min.js") {
                public boolean isInUse(Map appearancesMap) {
                    return isMenuInUse(appearancesMap);
                }
                public String getFeatureName() { return "menu"; }
            },
            // HTML area
            new ResourceConfig("/ops/fckeditor/fckeditor.js", null) {
                public boolean isInUse(Map appearancesMap) {
                    return isHtmlAreaInUse(appearancesMap);
                }
                public String getFeatureName() { return "htmlarea"; }
            },
            // Autocomplete
            new ResourceConfig("/ops/javascript/suggest-common.js", "/ops/javascript/suggest-common-min.js") {// our min version
                public boolean isInUse(Map appearancesMap) {
                    return isAutocompleteInUse(appearancesMap);
                }
                public String getFeatureName() { return "autocomplete"; }
            },
            new ResourceConfig("/ops/javascript/suggest-actb.js", "/ops/javascript/suggest-actb-min.js") {// our min version
                public boolean isInUse(Map appearancesMap) {
                    return isAutocompleteInUse(appearancesMap);
                }
                public String getFeatureName() { return "autocomplete"; }
            },
            // Other standard scripts
            new ResourceConfig("/ops/javascript/time-utils.js", "/ops/javascript/time-utils-min.js"),// TODO: check who uses this // our min version
            // XForms client
            new ResourceConfig("/ops/javascript/xforms.js", null)// TODO: our min version
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

        public boolean isInUse(Map appearancesMap) {
            // Default to true but can be overridden
            return true;
        }

        public boolean isInUseByFeatureMap(Map featureMap) {
            // Default to true but can be overridden
            final String featureName = getFeatureName();

            if (featureName == null)
                return true;

            final FeatureConfig featureConfig = (FeatureConfig) featureMap.get(featureName);
            return featureConfig != null;
        }

        protected String getFeatureName() {
            return null;
        }

        public static boolean isInUse(Map appearancesMap, String controlName) {
            final Map controlMap = (Map) appearancesMap.get(controlName);
            return controlMap != null;
        }

        public static boolean isInUse(Map appearancesMap, String controlName, String appearanceOrMediatypeName) {
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

        protected boolean isDialogInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "dialog");
        }
    }

    public static String getCombinedResourcesPrefix(XFormsContainingDocument containingDocument, Map appearancesMap, boolean isMinimal, boolean isVersioned) {
        if (XFormsProperties.isCombinedResources(containingDocument)) {
            final FastStringBuffer sb = new FastStringBuffer("/xforms-server/");

            // Make the Orbeon Forms version part of the path if requested
            if (isVersioned) {
                sb.append(Version.getVersion());
                sb.append('/');
            }

            sb.append("xforms");

            for (int i = 0; i < features.length; i++) {
                final FeatureConfig currentFeature = features[i];
                if (currentFeature.isInUse(appearancesMap)) {
                    sb.append('-');
                    sb.append(currentFeature.getId());
                }
            }
            if (isMinimal)
                sb.append("-min");
            return sb.toString();
        } else {
            return null;
        }
    }

    public static List getCSSResources(Map appearancesMap) {
        final List result = new ArrayList();
        for (int i = 0; i < stylesheets.length; i++) {
            final ResourceConfig resourceConfig = stylesheets[i];
            if (resourceConfig.isInUse(appearancesMap)) {
                // Only include stylesheet if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }

    public static List getCSSResourcesByFeatureMap(Map featureMap) {
        final List result = new ArrayList();
        for (int i = 0; i < stylesheets.length; i++) {
            final ResourceConfig resourceConfig = stylesheets[i];
            if (resourceConfig.isInUseByFeatureMap(featureMap)) {
                // Only include stylesheet if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }

    public static List getJavaScriptResources(Map appearancesMap) {
        final List result = new ArrayList();
        for (int i = 0; i < scripts.length; i++) {
            final ResourceConfig resourceConfig = scripts[i];
            if (resourceConfig.isInUse(appearancesMap)) {
                // Only include script if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }

    public static List getJavaScriptResourcesByFeatureMap(Map featureMap) {
        final List result = new ArrayList();
        for (int i = 0; i < scripts.length; i++) {
            final ResourceConfig resourceConfig = scripts[i];
            if (resourceConfig.isInUseByFeatureMap(featureMap)) {
                // Only include script if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }

    public static FeatureConfig getFeatureById(String featureId) {
        return (FeatureConfig) featuresByIdMap.get(featureId);
    }
}
