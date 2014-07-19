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
package org.orbeon.oxf.xml;

import org.apache.commons.lang3.StringUtils;
import org.orbeon.saxon.om.Name10Checker;

public class SaxonUtils {
    /**
     * Make an NCName out of a non-blank string. Any characters that do not belong in an NCName are converted to '_'.
     *
     * @param name  source
     * @return      NCName
     */
    public static String makeNCName(String name) {

        if (StringUtils.isBlank(name))
            throw new IllegalArgumentException("Name must not be blank or empty");

        final Name10Checker name10Checker = Name10Checker.getInstance();
        if (name10Checker.isValidNCName(name)) {
            return name;
        } else {
            final StringBuilder sb = new StringBuilder();
            final char start = name.charAt(0);
            sb.append(name10Checker.isNCNameStartChar(start) ? start : '_');

            for (int i = 1; i < name.length(); i++) {
                final char ch = name.charAt(i);
                sb.append(name10Checker.isNCNameChar(ch) ? ch : '_');
            }

            return sb.toString();
        }
    }
}
