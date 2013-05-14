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

import javax.portlet.PortalContext;
import javax.portlet.PortletMode;
import javax.portlet.WindowState;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

public class PortalContextImpl implements PortalContext {

    public static final String PORTAL_INFO = "Orbeon Forms Portal Server 0.1 alpha";

    private static final PortletMode[] portletModes = { PortletMode.VIEW, PortletMode.EDIT, PortletMode.HELP };
    private static final WindowState[] windowStates = { WindowState.NORMAL, WindowState.MINIMIZED, WindowState.MAXIMIZED };

    private static PortalContextImpl instance;

    public static synchronized PortalContext instance() {
        if (instance == null)
            instance = new PortalContextImpl();
        return instance;
    }

    private PortalContextImpl() {}

    public String getPortalInfo() {
        return PORTAL_INFO;
    }

    public String getProperty(String name) {
        // TEMP: No properties
        return null;
    }

    public Enumeration getPropertyNames() {
        // TEMP: No properties
        return Collections.enumeration(Collections.EMPTY_LIST);
    }

    public Enumeration getSupportedPortletModes() {
        return Collections.enumeration(Arrays.asList(portletModes));
    }

    public Enumeration getSupportedWindowStates() {
        return Collections.enumeration(Arrays.asList(windowStates));
    }
}
