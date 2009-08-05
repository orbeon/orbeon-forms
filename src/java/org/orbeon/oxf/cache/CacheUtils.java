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
package org.orbeon.oxf.cache;

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.common.OXFException;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class CacheUtils {

    final private static int INDENTATION = 4;

    public static String validityToString(Object validity) {
        return validityToString(validity, 0);
    }

    private static String validityToString(Object validity, int level) {
        if (validity instanceof Long) {
            return StringUtils.repeat(" ", INDENTATION * level) +
                    new Date((Long) validity).toString();
        } else if (validity instanceof List) {
            StringBuffer result = new StringBuffer();
            result.append(StringUtils.repeat(" ", INDENTATION * level));
            result.append("[\n");
            for (Iterator i = ((List) validity).iterator(); i.hasNext();) {
                Object childValidity = i.next();
                result.append(validityToString(childValidity, level + 1));
                result.append("\n");
            }
            result.append(StringUtils.repeat(" ", INDENTATION * level));
            result.append("]");
            return result.toString();
        } else {
            throw new OXFException("Unsupported validity type: '" + validity.getClass().getName() + "'");
        }
    }

    public static String getShortClassName(Class clazz) {
        int i = clazz.getName().lastIndexOf('.');
        if(i == -1)
            return clazz.getName().substring(i);
        else
            return clazz.getName();
    }
}
