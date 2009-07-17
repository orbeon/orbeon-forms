/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

public interface PropertyContext {

    /**
     * Set an attribute.
     *
     * @param key the attribute key
     * @param o   the attribute value to associate with the key
     */
    void setAttribute(Object key, Object o);

    /**
     * Get an attribute.
     *
     * @param key the attribute key
     * @return the attribute value, null if there is no attribute with the given key
     */
    Object getAttribute(Object key);
}
