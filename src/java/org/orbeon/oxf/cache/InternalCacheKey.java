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
package org.orbeon.oxf.cache;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.Processor;

import java.util.List;

public class InternalCacheKey extends CacheKey {

    private String type;
    private String key;
    private List keys;
    private int hash;

    public InternalCacheKey(String type, String key) {
        // Create a global cache key
        setClazz(this.getClass());
        setType(type);
        setKey(key);
    }

    public InternalCacheKey(Processor processor, String type, String key) {
        setClazz(processor.getClass());
        setType(type);
        setKey(key);
    }

    public InternalCacheKey(Processor processor, List keys) {
        setClazz(processor.getClass());
        this.keys = keys;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public boolean equals(Object obj) {
        boolean result = obj instanceof InternalCacheKey
                && super.equals(obj);
        return result && (key != null ?
                ((InternalCacheKey) obj).type.equals(type) && ((InternalCacheKey) obj).key.equals(key)
                : ((InternalCacheKey) obj).keys.equals(keys));
    }

    public int hashCode() {
        if (hash == 0) {
            int hash = 1;
            hash = 31*hash + super.hashCode();
            if (keys != null) hash = 31*hash + keys.hashCode();
            if (key != null) hash = 31*hash + key.hashCode();
            if (type != null) hash = 31*hash + type.hashCode();
            this.hash = hash;
        }
        return hash;
    }

    public String toString() {
        try {
            return "InternalCacheKey[class: " + CacheUtils.getShortClassName(getClazz()) +
                    (key != null ? ", key: " + getKey() : "first key: " + keys.get(0).toString())
                    + "]";
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
