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
package org.orbeon.oxf.xml;

/**
 * Defines a mapping from JavaBean property names to XML element names.
 *
 * <p>If the capitalization changes from lowercase to uppercase, a dash is
 * inserted before the first uppercase character. If it changes from
 * uppercase to lower case, a dash is inserted before the last uppercase
 * character.
 *
 * <p><b>Examples:</b>
 * <pre>
 *     Java Name      XML Name         SQL Name
 *     ---------      --------         --------
 *     SOLineNumber   so-line-number   SO_LINE_NUMBER
 *     CHLPOAlertId   chlpo-alert-id   CHLPO_ALERT_ID
 * </pre>
 */
public class NamingConvention {
    public static String javaToXMLName(String javaName) {

        // Strip "package name".
        int lastDot = javaName.lastIndexOf('.');
        if (lastDot != -1 && lastDot + 1 < javaName.length())
            javaName = javaName.substring(lastDot + 1);

        // Strip "inner class prefix".
        int lastDollar = javaName.lastIndexOf('$');
        if (lastDollar != -1)
            javaName = javaName.substring(lastDollar + 1);

        StringBuffer result = new StringBuffer();
        for (int i = 0; i < javaName.length(); i++) {
            // ASTWhen switch from lower to upper, add a dash before upper.
            if (i > 0  && Character.isLowerCase(javaName.charAt(i - 1))
                    && Character.isUpperCase(javaName.charAt(i)))
                result.append('-');
            // ASTWhen switch from upper to lower, add dash before upper.
            else if (i > 0 && i < javaName.length() - 1
                    && Character.isUpperCase(javaName.charAt(i))
                    && Character.isLowerCase(javaName.charAt(i + 1)))
                result.append('-');
            result.append(Character.toLowerCase(javaName.charAt(i)));
        }
        return result.toString();
    }

    public static String javaToSQLName(String javaName) {
        return javaToXMLName(javaName).replace('-', '_').toUpperCase();
    }
}
