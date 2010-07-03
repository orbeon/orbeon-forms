/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
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

import org.orbeon.oxf.xml.ContentHandlerHelper;

import java.util.*;

public class CompoundOutputCacheKey extends OutputCacheKey {

    private final List<CacheKey> keys;

    private final int hash;

    public CompoundOutputCacheKey(final Class clazz, final String outputName, final CacheKey[] keys) {
        super(clazz, outputName);
        if (keys == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        this.keys = new ArrayList<CacheKey>(keys.length);
        this.keys.addAll(Arrays.asList(keys));

        int tmp = 1;
        tmp += 31 * tmp + super.hashCode();
        tmp += 31 * tmp + outputName.hashCode();
        tmp += 31 * tmp + this.keys.hashCode();

        hash = tmp;
    }

    @Override
    public boolean equals(final Object other) {
        boolean ret = this == other;
        done:
        if (!ret) {
            ret = other instanceof CompoundOutputCacheKey && super.equals(other);
            if (!ret) break done;
            final CompoundOutputCacheKey rhs = (CompoundOutputCacheKey) other;
            ret = keys.equals(rhs.keys);
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "CompoundOutputCacheKey [class: " + CacheUtils.getShortClassName(getClazz())
                + ", outputName: " + outputName + ", key: " + keys + "]";
    }

    @Override
    public void toXML(ContentHandlerHelper helper, Object validities) {

        final List validitiesList = (List) validities;
        final Iterator validitiesIterator = (validitiesList != null) ? validitiesList.iterator() : null;

        helper.startElement("output", new String[] { "class", getClazz().getName(), "name", outputName });
        if (keys != null) {
            for (final CacheKey key : keys) {
                final Object childValidity = (validitiesIterator != null) ? validitiesIterator.next() : null;
                key.toXML(helper, childValidity);
            }
        }
        helper.endElement();
    }
}
