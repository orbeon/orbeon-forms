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

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.PortletContext;
import javax.portlet.PortletSession;
import java.util.Collections;
import java.util.Enumeration;

public class PortletSessionImpl implements PortletSession {

    private PortletContext portletContext;
    private int portletId;
    private ExternalContext.Session session;

    public PortletSessionImpl(PortletContext portletContext, int portletId, ExternalContext.Session session) {
        this.portletContext = portletContext;
        this.portletId = portletId;
        this.session = session;
    }

    public Object getAttribute(String name) {
        return getAttribute(name, PortletSession.PORTLET_SCOPE);
    }

    public Object getAttribute(String name, int scope) {
        if (scope == PortletSession.PORTLET_SCOPE)
            name = createApplicationScopeName(name);
        return session.getAttributesMap().get(name);
    }

    private String getApplicationScopePrefix() {
        return "javax.portlet.p." + portletId + "?";
    }

    private String createApplicationScopeName(String name) {
        return getApplicationScopePrefix() + name;
    }

    public Enumeration getAttributeNames() {
        return getAttributeNames(PortletSession.PORTLET_SCOPE);
    }

    public Enumeration getAttributeNames(int scope) {
        final Enumeration applicationAttributeNames = Collections.enumeration(session.getAttributesMap().keySet());
        if (scope == PortletSession.PORTLET_SCOPE)
            return new Enumeration() {
                // This enumeration return a subset of the available names
                private Object next;

                {
                    findNext();
                }

                private void findNext() {
                    while (applicationAttributeNames.hasMoreElements()) {
                        next = applicationAttributeNames.nextElement();
                        if (((String) next).startsWith(getApplicationScopePrefix()))
                            return;
                    }
                    next = null;
                }

                public boolean hasMoreElements() {
                    return next != null;
                }

                public Object nextElement() {
                    Object result = next;
                    findNext();
                    return result;
                }
            };
        else
            return applicationAttributeNames;
    }

    public long getCreationTime() {
        return session.getCreationTime();
    }

    public String getId() {
        return session.getId();
    }

    public long getLastAccessedTime() {
        return session.getLastAccessedTime();
    }

    public int getMaxInactiveInterval() {
        return session.getMaxInactiveInterval();
    }

    public PortletContext getPortletContext() {
        return portletContext;
    }

    public void invalidate() {
        session.invalidate();
    }

    public boolean isNew() {
        return session.isNew();
    }

    public void removeAttribute(String name) {
        removeAttribute(name, PortletSession.PORTLET_SCOPE);
    }

    public void removeAttribute(String name, int scope) {
        if (scope == PortletSession.PORTLET_SCOPE)
            name = createApplicationScopeName(name);
        session.getAttributesMap().remove(name);
    }

    public void setAttribute(String name, Object value) {
        setAttribute(name, value, PortletSession.PORTLET_SCOPE);
    }

    public void setAttribute(String name, Object value, int scope) {
        if (scope == PortletSession.PORTLET_SCOPE)
            name = createApplicationScopeName(name);
        session.getAttributesMap().put(name, value);
    }

    public void setMaxInactiveInterval(int interval) {
        session.setMaxInactiveInterval(interval);
    }
}
