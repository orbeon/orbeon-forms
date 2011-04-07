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
public abstract class AttributesToMap<E> implements Map<String, E> {

    private Attributeable<E> attributeable;

    public interface Attributeable<E> {
        E getAttribute(String key);
        Enumeration<String> getAttributeNames();
        void removeAttribute(String key);
        void setAttribute(String key, E value);
    }

    public AttributesToMap(Attributeable<E> attributeable) {
        this.attributeable = attributeable;
    }

    public E put(String key, E value) {
        final E existing = attributeable.getAttribute(key);
        attributeable.setAttribute(key, value);
        return existing;
    }

    public void clear() {
        for (Enumeration<String> e = attributeable.getAttributeNames(); e.hasMoreElements();) {
            final String name = e.nextElement();
            attributeable.removeAttribute(name);
        }
    }

    public boolean containsKey(Object key) {
        return attributeable.getAttribute((String) key) != null;
    }

    public boolean containsValue(Object value) {
        for (Enumeration<String> e = attributeable.getAttributeNames(); e.hasMoreElements();) {
            final String name = e.nextElement();
            final Object o = get(name);
            if (o == value)
                return true;
        }
        return false;
    }

    public Set<Map.Entry<String, E>> entrySet() {
        // TODO: It's harder than it looks, as changes to the Entry elements must be reflected in the Map.
        throw new UnsupportedOperationException();
    }

    public E get(Object key) {
        return attributeable.getAttribute((String) key);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Set<String> keySet() {
        // FIXME: Changes to the Set must be reflected in the Map. For now return an immutable Map.
        return Collections.unmodifiableSet(new HashSet<String>(Collections.list(attributeable.getAttributeNames())));
    }

    public void putAll(Map<? extends String, ? extends E> t) {
        for (String key: t.keySet()) {
            put(key, t.get((String) key));
        }
    }

    public E remove(Object key) {
        final E existing = attributeable.getAttribute((String) key);
        attributeable.removeAttribute((String) key);
        return existing;
    }

    public int size() {
        return keySet().size();
    }

    public Collection<E> values() {
        // FIXME: Changes to the Set must be reflected in the Map. For now return an immutable Map.
        List<E> results = new ArrayList<E>();
        for (Enumeration<String> e = attributeable.getAttributeNames(); e.hasMoreElements();) {
            final String name = e.nextElement();
            results.add(get(name));
        }
        return Collections.unmodifiableCollection(results);
    }
}
