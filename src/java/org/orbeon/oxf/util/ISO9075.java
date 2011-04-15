/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orbeon.oxf.util;

import org.orbeon.saxon.charcode.XMLCharacterData;
import org.orbeon.saxon.om.Name10Checker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Orbeon note: this class is under an Apache 2 license. It is copied from org.apache.jackrabbit.util.ISO9075. Just
 * kept the encode/decode methods to reduce dependencies on other classes.
 */

/**
 * Implements the encode and decode routines as specified for XML name to SQL
 * identifier conversion in ISO 9075-14:2003.<br/>
 * If a character <code>c</code> is not valid at a certain position in an XML 1.0
 * NCName it is encoded in the form: '_x' + hexValueOf(c) + '_'
 */
public class ISO9075 {

    /** Hidden constructor. */
    private ISO9075() { }

    /** Pattern on an encoded character */
    private static final Pattern ENCODE_PATTERN = Pattern.compile("_x\\p{XDigit}{4}_");

    /** Padding characters */
    private static final char[] PADDING = new char[] {'0', '0', '0'};

    /** All the possible hex digits */
    private static final String HEX_DIGITS = "0123456789abcdefABCDEF";

    /**
     * Encodes <code>name</code> as specified in ISO 9075.
     * @param name the <code>String</code> to encode.
     * @return the encoded <code>String</code> or <code>name</code> if it does
     *   not need encoding.
     */
    public static String encode(String name) {
        // quick check for root node name
        if (name.length() == 0) {
            return name;
        }
        if (Name10Checker.getInstance().isValidNCName(name) && name.indexOf("_x") < 0) {
            // already valid
            return name;
        } else {
            // encode
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                if (i == 0) {
                    // first character of name
                    if (XMLCharacterData.isNCNameStart10(name.charAt(i))) {
                        if (needsEscaping(name, i)) {
                            // '_x' must be encoded
                            encode('_', encoded);
                        } else {
                            encoded.append(name.charAt(i));
                        }
                    } else {
                        // not valid as first character -> encode
                        encode(name.charAt(i), encoded);
                    }
                } else if (!XMLCharacterData.isNCName10(name.charAt(i))) {
                    encode(name.charAt(i), encoded);
                } else {
                    if (needsEscaping(name, i)) {
                        // '_x' must be encoded
                        encode('_', encoded);
                    } else {
                        encoded.append(name.charAt(i));
                    }
                }
            }
            return encoded.toString();
        }
    }

    /**
     * Decodes the <code>name</code>.
     * @param name the <code>String</code> to decode.
     * @return the decoded <code>String</code>.
     */
    public static String decode(String name) {
        // quick check
        if (name.indexOf("_x") < 0) {
            // not encoded
            return name;
        }
        StringBuffer decoded = new StringBuffer();
        Matcher m = ENCODE_PATTERN.matcher(name);
        while (m.find()) {
            char ch = (char) Integer.parseInt(m.group().substring(2, 6), 16);
            if (ch == '$' || ch == '\\') {
                m.appendReplacement(decoded, "\\" + ch);
            } else {
                m.appendReplacement(decoded, Character.toString(ch));
            }
        }
        m.appendTail(decoded);
        return decoded.toString();
    }

    //-------------------------< internal >-------------------------------------

    /**
     * Encodes the character <code>c</code> as a String in the following form:
     * <code>"_x" + hex value of c + "_"</code>. Where the hex value has
     * four digits if the character with possibly leading zeros.
     * <p/>
     * Example: ' ' (the space character) is encoded to: _x0020_
     * @param c the character to encode
     * @param b the encoded character is appended to <code>StringBuilder</code>
     *  <code>b</code>.
     */
    private static void encode(char c, StringBuilder b) {
        b.append("_x");
        String hex = Integer.toHexString(c);
        b.append(PADDING, 0, 4 - hex.length());
        b.append(hex);
        b.append("_");
    }

    /**
     * Returns true if <code>name.charAt(location)</code> is the underscore
     * character and the following character sequence is 'xHHHH_' where H
     * is a hex digit.
     * @param name the name to check.
     * @param location the location to look at.
     * @throws ArrayIndexOutOfBoundsException if location > name.length()
     */
    private static boolean needsEscaping(String name, int location)
            throws ArrayIndexOutOfBoundsException {
        if (name.charAt(location) == '_' && name.length() >= location + 6) {
            return name.charAt(location + 1) == 'x'
                && HEX_DIGITS.indexOf(name.charAt(location + 2)) != -1
                && HEX_DIGITS.indexOf(name.charAt(location + 3)) != -1
                && HEX_DIGITS.indexOf(name.charAt(location + 4)) != -1
                && HEX_DIGITS.indexOf(name.charAt(location + 5)) != -1;
        } else {
            return false;
        }
    }
}
