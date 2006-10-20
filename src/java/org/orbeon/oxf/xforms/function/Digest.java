/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.Base64;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.security.MessageDigest;

/**
 * XForms digest() function (XForms 1.1).
 */
public class Digest extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final Expression dataExpression = argument[0];
        final Expression algorithmExpression = argument[1];
        final Expression encodingExpression = argument[2];

        final String data = dataExpression.evaluateAsString(xpathContext);
        final String algorithm = algorithmExpression.evaluateAsString(xpathContext);
        final String encoding = encodingExpression.evaluateAsString(xpathContext);

        // Create digest
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithm);
            messageDigest.update(data.getBytes("utf-8"));
        } catch (Exception e) {
            throw new OXFException("Exception computing digest with algorithm: " + algorithm, e);
        }
        byte[] digestBytes = messageDigest.digest();

        // Format result
        final String result;
        if ("base64".equals(encoding)) {
            result = Base64.encode(digestBytes);
        } else if ("hex".equals(encoding)) {
            result = byteArrayToHex(digestBytes);
        } else {
            throw new OXFException("Invalid digest encoding (must be one of 'base64' or 'hex'): " + encoding);
        }

        return new StringValue(result);
    }

    final static char[] HEXADECIMAL_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    public String byteArrayToHex(byte[] bytes) {
//        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        final FastStringBuffer sb = new FastStringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(HEXADECIMAL_DIGITS[(bytes[i] >> 4) & 0xf]);
            sb.append(HEXADECIMAL_DIGITS[bytes[i] & 0xf]);
        }
        return sb.toString();
    }
}
