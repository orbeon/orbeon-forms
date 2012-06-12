/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import java.util.*;

import org.apache.commons.lang.ArrayUtils;

public class StringConversions {

    /**
     * Convert an Enumeration of String into an array.
     */
    public static String[] stringEnumerationToArray(Enumeration enumeration) {
        final List<String> values = new ArrayList<String>();
        while (enumeration.hasMoreElements())
            values.add((String) enumeration.nextElement());
        final String[] stringValues = new String[values.size()];
        values.toArray(stringValues);
        return stringValues;
    }

    /**
     * Convert an Object array into a String array, removing non-string values.
     */
    public static String[] objectArrayToStringArray(Object[] values) {

        if (values == null)
            return null;

        final String[] result = new String[values.length];
        int size = 0;
        for (final Object currentValue: values) {
            if (currentValue instanceof String) {
                result[size++] = (String) currentValue;
            }
        }
        if (size == values.length) {
            // Optimistic approach worked
            return result;
        } else {
            // Optimistic approach failed
            final String[] newResult = new String[size];
            System.arraycopy(result, 0, newResult, 0, size);
            return newResult;
        }
    }

    /**
     * Convert a String array into an Object array.
     */
    public static Object[] stringArrayToObjectArray(String[] values) {

        if (values == null)
            return null;

        final Object[] result = new Object[values.length];
        int size = 0;
        for (final Object currentValue: values) {
            result[size++] = currentValue;
        }
        return result;
    }

    public static Map<String, Object[]> stringArrayMapToObjectArrayMap(Map<String, String[]> in) {
        if (in == null)
            return null;

        final Map<String, Object[]> result = new HashMap<String, Object[]>();
        for (Map.Entry<String, String[]> entry : in.entrySet()) {
            final Object[] values = stringArrayToObjectArray(entry.getValue());
            if (values != null && values.length > 0)
                result.put(entry.getKey(), values);
        }
        return result;
    }

    /**
     * Return the value of the first object in the array as a String.
     */
    public static String getStringFromObjectArray(Object[] values) {
        if (values == null || values.length == 0 || !(values[0] instanceof String))
            return null;
        else
            return (String) values[0];
    }

    public static String getFirstValueFromStringArray(String[] values) {
        return (values != null && values.length > 0) ? values[0] : null;
    }

    public static void addValueToObjectArrayMap(Map<String, Object[]> map, String name, Object value) {
        map.put(name, ArrayUtils.add(map.get(name), value));
    }

    public static void addValuesToObjectArrayMap(Map<String, Object[]> map, String name, Object[] values) {
        map.put(name, ArrayUtils.addAll(map.get(name), values));
    }

    public static void addValueToStringArrayMap(Map<String, String[]> map, String name, String value) {
        map.put(name, (String[])ArrayUtils.add(map.get(name), value));
    }

    public static void addValuesToStringArrayMap(Map<String, String[]> map, String name, String[] values) {
        map.put(name, (String[])ArrayUtils.addAll(map.get(name), values));
    }
}
