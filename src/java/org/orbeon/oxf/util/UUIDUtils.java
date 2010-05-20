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

import org.orbeon.oxf.common.OXFException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.rmi.server.UID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UUIDUtils {

    // Compute host name bytes once and for all
    private static byte[] hostNameBytes;
    static {
        try {
            hostNameBytes = InetAddress.getLocalHost().toString().getBytes("utf-8");
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static final int UUID_LENGTH = createPseudoUUID().length();

    /**
     * Return a String that looks like a 128-bit UUID. It really does not follow the spec as to the
     * meaning of the different elements.
     *
     * http://www.opengroup.org/onlinepubs/9629399/apdxa.htm
     */
    public static String createPseudoUUID() {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");

            // Create a locally unique ID
            messageDigest.update(new UID().toString().getBytes("utf-8"));

            // Add local host to make the ID more globally unique
            messageDigest.update(hostNameBytes);

            // Digest and create a String that looks nice
            final byte[] digestBytes = messageDigest.digest();

            final StringBuilder sb = new StringBuilder();
            sb.append(toHexString(NumberUtils.readIntBigEndian(digestBytes, 0)));
            sb.append('-');
            sb.append(toHexString(NumberUtils.readShortBigEndian(digestBytes, 4)));
            sb.append('-');
            sb.append(toHexString(NumberUtils.readShortBigEndian(digestBytes, 6)));
            sb.append('-');
            sb.append(toHexString(NumberUtils.readShortBigEndian(digestBytes, 8)));
            sb.append('-');
            sb.append(toHexString(NumberUtils.readShortBigEndian(digestBytes, 10)));
            sb.append(toHexString(NumberUtils.readIntBigEndian(digestBytes, 12)));

            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new OXFException(e);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    private static String toHexString(int i) {
        final String zeroes = "0000000";
        final String s = Integer.toHexString(i).toUpperCase();
        if (s.length() < 8)
            return zeroes.substring(s.length() - 1) + s;
        else
            return s;
    }

    private static String toHexString(short i) {
        return toHexString((int) i).substring(4);
    }
}
