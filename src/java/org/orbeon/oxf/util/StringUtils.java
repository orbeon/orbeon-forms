/**
 *  Copyright (C) 2008 Orbeon, Inc.
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

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

public class StringUtils {

    /**
     * Utility class which we have here so we can create a Set in Saxon.
     * Without this, we are unable to call new HashSet(...) as Saxon doesn't know which
     * version of the contructor to call.
     */
    public static Set createSet(String[] strings) {
        return new HashSet(Arrays.asList(strings));
    }

}
