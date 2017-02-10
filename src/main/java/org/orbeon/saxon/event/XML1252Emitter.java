
package org.orbeon.saxon.event;

import org.orbeon.saxon.charcode.UTF16;
import org.orbeon.saxon.tinytree.CompressedWhitespace;
import org.orbeon.saxon.trans.XPathException;

public class XML1252Emitter extends XMLEmitter {

    // This method overrides the Saxon method so we can do a custom fix-up of invalid CP-1252 characters.
    @Override protected void writeEscape(final CharSequence chars, final boolean inAttribute)
    throws java.io.IOException, XPathException {
        int segstart = 0;
        boolean disabled = false;
        final boolean[] specialChars = (inAttribute ? specialInAtt : specialInText);

        if (chars instanceof CompressedWhitespace) {
            ((CompressedWhitespace)chars).writeEscape(specialChars, writer);
            return;
        }

        final int clength = chars.length();
        while (segstart < clength) {
            int i = segstart;
            // find a maximal sequence of "ordinary" characters
            while (i < clength) {
                final char c = chars.charAt(i);
                if (c < 127) {
                    if (specialChars[c]) {
                        break;
                    } else {
                        i++;
                    }
                } else if (c < 160) {
                    break;
                } else if (c == 0x2028) {
                    break;
                } else if (UTF16.isHighSurrogate(c)) {
                    break;
                } else if (!characterSet.inCharset(c)) {
                    break;
                } else {
                    i++;
                }
            }

            // if this was the whole string write it out and exit
            if (i >= clength) {
                if (segstart == 0) {
                    writeCharSequence(chars);
                } else {
                    writeCharSequence(chars.subSequence(segstart, i));
                }
                return;
            }

            // otherwise write out this sequence
            if (i > segstart) {
                writeCharSequence(chars.subSequence(segstart, i));
            }

            // examine the special character that interrupted the scan
            final char c = chars.charAt(i);
            if (c==0) {
                // used to switch escaping on and off
                // See https://github.com/orbeon/orbeon-forms/issues/3115
//                disabled = !disabled;
            } else if (disabled) {
                if (c > 127) {
                    if (UTF16.isHighSurrogate(c)) {
                        int cc = UTF16.combinePair(c, chars.charAt(i+1));
                        if (!characterSet.inCharset(cc)) {
                            XPathException de = new XPathException("Character x" + Integer.toHexString(cc) +
                                    " is not available in the chosen encoding");
                            de.setErrorCode("SERE0008");
                            throw de;
                        }
                    } else if (!characterSet.inCharset(c)) {
                        XPathException de = new XPathException("Character " + c + " (x" + Integer.toHexString((int)c) +
                                ") is not available in the chosen encoding");
                        de.setErrorCode("SERE0008");
                        throw de;
                    }
                }
                writer.write(c);
            } else if (c>=127 && c<160) {
                // XML 1.1 requires these characters to be written as character references

                // ORBEON: Handle faulty CP-1252 characters ending up as unicode code points.
                outputCharacterReference(HTML1252Emitter.CP1252_BEST_FIT[c - 127]);

            } else if (c>=160) {
                if (c==0x2028) {
                    outputCharacterReference(c);
                } else if (UTF16.isHighSurrogate(c)) {
                    char d = chars.charAt(++i);
                    int charval = UTF16.combinePair(c, d);
                    if (characterSet.inCharset(charval)) {
                        writer.write(c);
                        writer.write(d);
                    } else {
                        outputCharacterReference(charval);
                    }
                } else {
                    // process characters not available in the current encoding
                    outputCharacterReference(c);
                }

            } else {

                // process special ASCII characters

                if (c=='<') {
                    writer.write("&lt;");
                } else if (c=='>') {
                    writer.write("&gt;");
                } else if (c=='&') {
                    writer.write("&amp;");
                } else if (c=='\"') {
                    writer.write("&#34;");
                } else if (c=='\n') {
                    writer.write("&#xA;");
                } else if (c=='\r') {
                    writer.write("&#xD;");
                } else if (c=='\t') {
                    writer.write("&#x9;");
                } else {
                    // C0 control characters
                     outputCharacterReference(c);
                }
            }
            segstart = ++i;
        }
    }
}
//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//
