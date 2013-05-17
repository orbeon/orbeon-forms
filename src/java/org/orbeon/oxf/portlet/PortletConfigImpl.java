/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.portlet;

import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletMode;
import java.io.Serializable;
import java.util.*;

public class PortletConfigImpl implements PortletConfig, Serializable  {

    private static final String DEFAULT_TITLE = "[Untitled]";
    private static final String DEFAULT_SHORT_TITLE = DEFAULT_TITLE;
    private static final String DEFAULT_KEYWORDS = "";

    private String portletName;
    private String className;
    private Map mimeTypes = new HashMap();
    private Map portletModes = new HashMap();
    private String title;
    private String shortTitle;
    private String keywords;

    private PortletContext portletContext;
    private Map initParameters = new HashMap();
    private Map preferences = new HashMap();

    public PortletConfigImpl() {
        // The VIEW mode is always supported, per the spec
        portletModes.put(PortletMode.VIEW, PortletMode.VIEW);
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public void setPortletName(String portletName) {
        this.portletName = portletName;
    }

    public void setPortletContext(PortletContext portletContext) {
        this.portletContext = portletContext;
    }

    public void setInitParameter(String name, String value) {
        initParameters.put(name, value);
    }

    public void setPreferences(String name, String[] values) {
        preferences.put(name, values);
    }

    public void addMimeType(String mimeType) {
        mimeTypes.put(mimeType, mimeType);
    }

    public void addPortletMode(String portletModeString) {
        PortletMode portletMode = new PortletMode(portletModeString);
        portletModes.put(portletMode, portletMode);
    }

    public Set getPortletModes() {
        return portletModes.keySet();
    }

    public boolean supportsPortletMode(PortletMode portletMode) {
        return portletModes.get(portletMode) != null;
    }

    public String getShortTitle() {
        return shortTitle;
    }

    public void setShortTitle(String shortTitle) {
        this.shortTitle = shortTitle;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getInitParameter(String name) {
        return (String) initParameters.get(name);
    }

    public Enumeration getInitParameterNames() {
        return Collections.enumeration(initParameters.keySet());
    }

    public String[] getPreferences(String name, String[] def) {
        String[] result = (String[]) preferences.get(name);
        return (result == null) ? def : result;
    }

    public Enumeration getPreferenceNames() {
        return Collections.enumeration(preferences.keySet());
    }

    public PortletContext getPortletContext() {
        return portletContext;
    }

    public String getPortletName() {
        return portletName;
    }

    public ResourceBundle getResourceBundle(Locale locale) {
        // TODO: user-defined bundle
        if (defaultResourceBundle == null)
            defaultResourceBundle = new DefaultResourceBundle();
        return defaultResourceBundle;
    }

    private class DefaultResourceBundle extends ListResourceBundle {
        public Object[][] getContents() {
            if (contents == null) {
                contents = new Object[][]{
                    { "javax.portlet.title", getTitle() != null ? getTitle() : DEFAULT_TITLE },
                    { "javax.portlet.short-title", getShortTitle() != null ? getShortTitle() : DEFAULT_SHORT_TITLE },
                    { "javax.portlet.keywords", getKeywords() != null ? getKeywords() : DEFAULT_KEYWORDS }
                };
            }
            return contents;
        }

        private Object[][] contents;
    }

    private ResourceBundle defaultResourceBundle = new DefaultResourceBundle();
}