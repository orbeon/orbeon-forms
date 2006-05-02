/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.resources;

import java.util.Map;
import java.util.HashMap;

/**
 * A map where the value expire after the interval given at construction time.
 */
public class ExpirationMap {

    private long expirationInterval;
    private Map store = new HashMap();

    public ExpirationMap(long expirationInterval) {
        this.expirationInterval = expirationInterval;
    }

    public Object  get(long currentTimeMillis, String key) {
        MapEntry entry = (MapEntry) store.get(key);
        if (entry == null || (entry.lastAccess + expirationInterval) < currentTimeMillis) {
            return null;
        } else {
            entry.lastAccess = currentTimeMillis;
            return entry.value;
        }
    }

    public void put(long currentTimeMillis, String key, Object value) {
        MapEntry entry = (MapEntry) store.get(key);
        if (entry == null) {
            entry = new MapEntry();
            store.put(key, entry);
        }
        entry.lastAccess = currentTimeMillis;
        entry.value = value;
    }

    private static class MapEntry {
        public long lastAccess;
        public Object value;
    }
}
