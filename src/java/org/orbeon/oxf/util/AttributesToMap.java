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
package org.orbeon.oxf.util;

import java.util.*;

/**
 * Generic class to present a Map view of an attribute-based API.
 */
public abstract class AttributesToMap implements Map {

    private Attributeable attributeable;

    public static interface Attributeable {
        Object getAttribute(String s);
        Enumeration getAttributeNames();
        void removeAttribute(String s);
        void setAttribute(String s, Object o);
    }

    public AttributesToMap(Attributeable attributeable) {
        this.attributeable = attributeable;
    }

    public Object put(Object key, Object value) {
        Object existing = attributeable.getAttribute((String) key);
        attributeable.setAttribute((String) key, value);
        return existing;
    }

    public void clear() {
        for (Enumeration e = attributeable.getAttributeNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            attributeable.removeAttribute(name);
        }
    }

    public boolean containsKey(Object key) {
        return attributeable.getAttribute((String) key) != null;
    }

    public boolean containsValue(Object value) {
        for (Enumeration e = attributeable.getAttributeNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            Object o = get(name);
            if (o == value)
                return true;
        }
        return false;
    }

    public Set entrySet() {
        // TODO: It's harder than it looks, as changes to the Entry elements must be reflected in the Map.
        throw new UnsupportedOperationException();
    }

    public Object get(Object key) {
        return attributeable.getAttribute((String) key);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Set keySet() {
        // FIXME: Changes to the Set must be reflected in the Map. For now return an immutable Map.
        return Collections.unmodifiableSet(new HashSet(Collections.list(attributeable.getAttributeNames())));
    }

    public void putAll(Map t) {
        for (Iterator i = t.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            put(key, t.get(key));
        }
    }

    public Object remove(Object key) {
        Object existing = attributeable.getAttribute((String) key);
        attributeable.removeAttribute((String) key);
        return existing;
    }

    public int size() {
        return keySet().size();
    }

    public Collection values() {
        // FIXME: Changes to the Set must be reflected in the Map. For now return an immutable Map.
        List results = new ArrayList();
        for (Enumeration e = attributeable.getAttributeNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            results.add(get(name));
        }
        return Collections.unmodifiableCollection(results);
    }
}
