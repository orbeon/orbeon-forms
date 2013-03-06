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

public class SimpleOutputCacheKey extends OutputCacheKey {

    private final String key;
    private final int hash;

    public SimpleOutputCacheKey(final Class clazz, final String outputName, String key) {
        super(clazz, outputName);
        this.key = key;

        if (this.key == null)
            throw new IllegalArgumentException("key must not be null");

        int tmp = 1;
        tmp += 31 * tmp + super.hashCode();
        tmp += 31 * tmp + outputName.hashCode();
        tmp += 31 * tmp + this.key.hashCode();

        hash = tmp;
    }

    @Override
    public boolean equals(final Object other) {
        boolean ret = this == other;
        done:
        if (!ret) {
            ret = other instanceof SimpleOutputCacheKey && super.equals(other);
            if (!ret) break done;
            final SimpleOutputCacheKey rhs = (SimpleOutputCacheKey) other;
            ret = key.equals(rhs.key);
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "SimpleOutputCacheKey [class: " + CacheUtils.getShortClassName(getClazz())
                + ", outputName: " + outputName + ", key: " + key + "]";
    }

    @Override
    public void toXML(ContentHandlerHelper helper, Object validities) {
        helper.element("output", new String[] { "class", getClazz().getName(), "validity", (validities != null) ? validities.toString() : null, "name", outputName, "key", key });
    }
}
