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

import javax.portlet.PortletPreferences;
import javax.portlet.ReadOnlyException;
import javax.portlet.ValidatorException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class PortletPreferencesImpl implements PortletPreferences {

    private PortletConfigImpl portletConfig;
    private Map preferencesMap;

    public PortletPreferencesImpl(PortletConfigImpl portletConfig) {
        this.portletConfig = portletConfig;
    }


    public Enumeration getNames() {
        return portletConfig.getPreferenceNames();
    }

    public String getValue(String key, String def) {
        String[] values = portletConfig.getPreferences(key, null);
        return (values != null && values.length >= 1) ? values[0] : def;
    }

    public String[] getValues(String key, String[] def) {
        return portletConfig.getPreferences(key, def);
    }

    public void reset(String key) throws ReadOnlyException {
        // NIY
    }

    public void setValue(String key, String value) throws ReadOnlyException {
        // NIY
    }

    public void setValues(String key, String[] values) throws ReadOnlyException {
        // NIY
    }

    public void store() throws IOException, ValidatorException {
        // NIY
    }

    public Map getMap() {
        if (preferencesMap == null) {
            preferencesMap = new HashMap();
            for (Enumeration e = getNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                preferencesMap.put(name, getValues(name, null));
            }
        }
        return preferencesMap;
    }

    public boolean isReadOnly(String key) {
        // NIY
        return false;
    }
}
