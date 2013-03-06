/**
 *  Copyright (C) 2004-2007 Orbeon, Inc.
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

public class NumberUtils {

    private static final char digits[] = {
        '0', '1', '2', '3',
        '4', '5', '6', '7',
        '8', '9', 'a', 'b',
        'c', 'd', 'e', 'f'
    };

    /**
     * Convert a byte array into a hexadecimal String representation.
     *
     * @param bytes  array of bytes to convert
     * @return       hexadecimal representation
     */
    public static String toHexString(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(digits[(bytes[i] >> 4) & 0x0f]);
            sb.append(digits[bytes[i] & 0x0f]);
        }
        return sb.toString();
    }

    /**
     * Convert a byte into a hexadecimal String representation.
     *
     * @param b      byte to convert
     * @return       hexadecimal representation
     */
    public static String toHexString(byte b) {
        final StringBuilder sb = new StringBuilder(2);
        sb.append(digits[(b >> 4) & 0x0f]);
        sb.append(digits[b & 0x0f]);
        return sb.toString();
    }

    public static double pctChange(Number oldValue, Number newValue) {
        if (oldValue == null || newValue == null || oldValue.intValue() == 0) {
            return 0;
        } else {
            return (oldValue.doubleValue() > 0 ? 1 : -1) *
                (newValue.doubleValue() - oldValue.doubleValue())/oldValue.doubleValue();
        }
    }

    public static int readIntBigEndian(byte[] bytes, int first) {
        return ((((int) bytes[first + 0]) & 0xff) << 24)
                + ((((int) bytes[first + 1]) & 0xff) << 16)
                + ((((int) bytes[first + 2]) & 0xff) << 8)
                + (((int) bytes[first + 3]) & 0xff);
    }

    public static short readShortBigEndian(byte[] bytes, int first) {
        return (short) (((((int) bytes[first + 0]) & 0xff) << 8)
                + (((int) bytes[first + 1]) & 0xff));
    }

    public static short readShortLittleEndian(byte[] bytes, int first) {
        return (short) (((((int) bytes[first + 1]) & 0xff) << 8)
                + (((int) bytes[first + 0]) & 0xff));
    }
}
