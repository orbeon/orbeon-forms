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

public class SimpleOutputCacheKey extends OutputCacheKey {

    private final String key;
    private final int hash;

    public SimpleOutputCacheKey( final Class c, final String onam, String k ) {
        super( c, onam );
        key = k;
        if ( key == null ) throw new IllegalArgumentException( "key must not be null" );
        int tmp = 1;
        tmp += 31 * tmp + super.hashCode();
        tmp += 31 * tmp + outputName.hashCode();
        tmp += 31 * tmp + key.hashCode();
        hash = tmp;
    }

    public boolean equals( final Object rhsObj ) {
        boolean ret = this == rhsObj;
        done : if ( !ret ) {
            ret = rhsObj instanceof SimpleOutputCacheKey && super.equals( rhsObj );
            if ( !ret ) break done;
            final SimpleOutputCacheKey rhs = ( SimpleOutputCacheKey )rhsObj;
            ret = key.equals( rhs.key );
        }
        return ret;
    }

    public int hashCode() {
        return hash;
    }

    public String toString() {
        return "SimpleOutputCacheKey [class: " + CacheUtils.getShortClassName(getClazz())
                + ", outputName: " + outputName + ", key: " + key + "]"; 
    }
}
