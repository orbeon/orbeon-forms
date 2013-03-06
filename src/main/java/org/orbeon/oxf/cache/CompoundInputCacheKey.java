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



public class CompoundInputCacheKey /* extends InputCacheKey */ {

//    private final java.util.List keys;
//    private final int hash;
//
//    public CompoundInputCacheKey(final Class c, final String onam, final ProcessorInput[] processorInputs, final OutputCacheKey[] cacheKeys) {
//        super(c, onam);
//        if (cacheKeys == null) {
//            throw new IllegalArgumentException("key must not be null");
//        }
//        keys = new java.util.ArrayList(cacheKeys.length);
//        for (int i = 0; i < cacheKeys.length; i++) {
//            keys.add(cacheKeys[i]);
//        }
//        int tmp = 1;
//        tmp += 31 * tmp + super.hashCode();
//        tmp += 31 * tmp + outputName.hashCode();
//        tmp += 31 * tmp + keys.hashCode();
//        hash = tmp;
//    }
//
//    public boolean equals(final Object rhsObj) {
//        boolean ret = this == rhsObj;
//        done : if (!ret) {
//            ret = rhsObj instanceof CompoundOutputCacheKey && super.equals(rhsObj);
//            if (!ret) break done;
//            final CompoundOutputCacheKey rhs = (CompoundOutputCacheKey) rhsObj;
//            ret = keys.equals(rhs.keys);
//        }
//        return ret;
//    }
//
//    public int hashCode() {
//        return hash;
//    }
//
//    public String toString() {
//        return "CompoundOutputCacheKey [class: " + CacheUtils.getShortClassName(getClazz())
//                + ", outputName: " + outputName + ", key: " + keys + "]";
//    }
}
