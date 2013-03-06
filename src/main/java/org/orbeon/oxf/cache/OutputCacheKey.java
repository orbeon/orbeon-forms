/**
 *  Copyright (C) 2004-2005 Orbeon, Inc.
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

public abstract class OutputCacheKey extends CacheKey {

    protected final String outputName;

    public OutputCacheKey(final Class c, final String outputName) {
        // Don't do this test at runtime, as it appears to be costly
//        if (!Processor.class.isAssignableFrom(c)) {
//            throw new IllegalArgumentException("c must be a sub-class of PipelineProcessor");
//        }
        this.outputName = outputName;
        setClazz(c);
    }
}
