/*--
 *
 * $Id$
 *
 * Copyright 2000-2004 Jason Hunter & Brett McLaughlin.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   * Neither the name of the Jaxen Project nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Jaxen Project and was originally
 * created by bob mcwhirter <bob@werken.com> and
 * James Strachan <jstrachan@apache.org>.  For more information on the
 * Jaxen Project, please see <http://www.jaxen.org/>.
 *
 */

package org.orbeon.jaxen.saxpath.base;

/**
 * A utility class to handle well-formedness checks on names.
 *
 * @author  Brett McLaughlin
 * @author  Elliotte Rusty Harold
 * @author  Jason Hunter
 * @author  Bradley S. Huffman
 */
final class Verifier {

    /**
     * This is a utility function for determining whether a specified
     * character is a name character according to production 4 of the
     * XML 1.0 specification.
     *
     * @param c <code>char</code> to check for XML name compliance.
     * @return <code>boolean</code> true if it's a name character,
     *                                false otherwise
     */
     static boolean isXMLNCNameCharacter(char c) {

      return (isXMLLetter(c) || isXMLDigit(c) || c == '.' || c == '-'
                             || c == '_' || isXMLCombiningChar(c)
                             || isXMLExtender(c));
    }

    /**
     * This is a utility function for determining whether a specified
     * character is a legal name start character according to production 5
     * of the XML 1.0 specification. This production does allow names
     * to begin with colons which the Namespaces in XML Recommendation
     * disallows.
     *
     * @param c <code>char</code> to check for XML name start compliance
     * @return true if it's a name start character, false otherwise
     */
    static boolean isXMLNCNameStartCharacter(char c) {

      return (isXMLLetter(c) || c == '_');

    }

    /**
     * Determine whether a specified character
     * is a letter according to production 84 of the XML 1.0 specification.
     *
     * @param c <code>char</code> to check for XML name compliance
     * @return <code>String</code> true if it's a letter, false otherwise
     */
    static boolean isXMLLetter(char c) {
        // Note that order is very important here.  The search proceeds
        // from lowest to highest values, so that no searching occurs
        // above the character's value.  BTW, the first line is equivalent to:
        // if (c >= 0x0041 && c <= 0x005A) return true;

        if (c < 0x0041) return false;  if (c <= 0x005a) return true;
        if (c < 0x0061) return false;  if (c <= 0x007A) return true;
        if (c < 0x00C0) return false;  if (c <= 0x00D6) return true;
        if (c < 0x00D8) return false;  if (c <= 0x00F6) return true;
        if (c < 0x00F8) return false;  if (c <= 0x00FF) return true;
        if (c < 0x0100) return false;  if (c <= 0x0131) return true;
        if (c < 0x0134) return false;  if (c <= 0x013E) return true;
        if (c < 0x0141) return false;  if (c <= 0x0148) return true;
        if (c < 0x014A) return false;  if (c <= 0x017E) return true;
        if (c < 0x0180) return false;  if (c <= 0x01C3) return true;
        if (c < 0x01CD) return false;  if (c <= 0x01F0) return true;
        if (c < 0x01F4) return false;  if (c <= 0x01F5) return true;
        if (c < 0x01FA) return false;  if (c <= 0x0217) return true;
        if (c < 0x0250) return false;  if (c <= 0x02A8) return true;
        if (c < 0x02BB) return false;  if (c <= 0x02C1) return true;
        if (c == 0x0386) return true;
        if (c < 0x0388) return false;  if (c <= 0x038A) return true;
        if (c == 0x038C) return true;
        if (c < 0x038E) return false;  if (c <= 0x03A1) return true;
        if (c < 0x03A3) return false;  if (c <= 0x03CE) return true;
        if (c < 0x03D0) return false;  if (c <= 0x03D6) return true;
        if (c == 0x03DA) return true;
        if (c == 0x03DC) return true;
        if (c == 0x03DE) return true;
        if (c == 0x03E0) return true;
        if (c < 0x03E2) return false;  if (c <= 0x03F3) return true;
        if (c < 0x0401) return false;  if (c <= 0x040C) return true;
        if (c < 0x040E) return false;  if (c <= 0x044F) return true;
        if (c < 0x0451) return false;  if (c <= 0x045C) return true;
        if (c < 0x045E) return false;  if (c <= 0x0481) return true;
        if (c < 0x0490) return false;  if (c <= 0x04C4) return true;
        if (c < 0x04C7) return false;  if (c <= 0x04C8) return true;
        if (c < 0x04CB) return false;  if (c <= 0x04CC) return true;
        if (c < 0x04D0) return false;  if (c <= 0x04EB) return true;
        if (c < 0x04EE) return false;  if (c <= 0x04F5) return true;
        if (c < 0x04F8) return false;  if (c <= 0x04F9) return true;
        if (c < 0x0531) return false;  if (c <= 0x0556) return true;
        if (c == 0x0559) return true;
        if (c < 0x0561) return false;  if (c <= 0x0586) return true;
        if (c < 0x05D0) return false;  if (c <= 0x05EA) return true;
        if (c < 0x05F0) return false;  if (c <= 0x05F2) return true;
        if (c < 0x0621) return false;  if (c <= 0x063A) return true;
        if (c < 0x0641) return false;  if (c <= 0x064A) return true;
        if (c < 0x0671) return false;  if (c <= 0x06B7) return true;
        if (c < 0x06BA) return false;  if (c <= 0x06BE) return true;
        if (c < 0x06C0) return false;  if (c <= 0x06CE) return true;
        if (c < 0x06D0) return false;  if (c <= 0x06D3) return true;
        if (c == 0x06D5) return true;
        if (c < 0x06E5) return false;  if (c <= 0x06E6) return true;
        if (c < 0x0905) return false;  if (c <= 0x0939) return true;
        if (c == 0x093D) return true;
        if (c < 0x0958) return false;  if (c <= 0x0961) return true;
        if (c < 0x0985) return false;  if (c <= 0x098C) return true;
        if (c < 0x098F) return false;  if (c <= 0x0990) return true;
        if (c < 0x0993) return false;  if (c <= 0x09A8) return true;
        if (c < 0x09AA) return false;  if (c <= 0x09B0) return true;
        if (c == 0x09B2) return true;
        if (c < 0x09B6) return false;  if (c <= 0x09B9) return true;
        if (c < 0x09DC) return false;  if (c <= 0x09DD) return true;
        if (c < 0x09DF) return false;  if (c <= 0x09E1) return true;
        if (c < 0x09F0) return false;  if (c <= 0x09F1) return true;
        if (c < 0x0A05) return false;  if (c <= 0x0A0A) return true;
        if (c < 0x0A0F) return false;  if (c <= 0x0A10) return true;
        if (c < 0x0A13) return false;  if (c <= 0x0A28) return true;
        if (c < 0x0A2A) return false;  if (c <= 0x0A30) return true;
        if (c < 0x0A32) return false;  if (c <= 0x0A33) return true;
        if (c < 0x0A35) return false;  if (c <= 0x0A36) return true;
        if (c < 0x0A38) return false;  if (c <= 0x0A39) return true;
        if (c < 0x0A59) return false;  if (c <= 0x0A5C) return true;
        if (c == 0x0A5E) return true;
        if (c < 0x0A72) return false;  if (c <= 0x0A74) return true;
        if (c < 0x0A85) return false;  if (c <= 0x0A8B) return true;
        if (c == 0x0A8D) return true;
        if (c < 0x0A8F) return false;  if (c <= 0x0A91) return true;
        if (c < 0x0A93) return false;  if (c <= 0x0AA8) return true;
        if (c < 0x0AAA) return false;  if (c <= 0x0AB0) return true;
        if (c < 0x0AB2) return false;  if (c <= 0x0AB3) return true;
        if (c < 0x0AB5) return false;  if (c <= 0x0AB9) return true;
        if (c == 0x0ABD) return true;
        if (c == 0x0AE0) return true;
        if (c < 0x0B05) return false;  if (c <= 0x0B0C) return true;
        if (c < 0x0B0F) return false;  if (c <= 0x0B10) return true;
        if (c < 0x0B13) return false;  if (c <= 0x0B28) return true;
        if (c < 0x0B2A) return false;  if (c <= 0x0B30) return true;
        if (c < 0x0B32) return false;  if (c <= 0x0B33) return true;
        if (c < 0x0B36) return false;  if (c <= 0x0B39) return true;
        if (c == 0x0B3D) return true;
        if (c < 0x0B5C) return false;  if (c <= 0x0B5D) return true;
        if (c < 0x0B5F) return false;  if (c <= 0x0B61) return true;
        if (c < 0x0B85) return false;  if (c <= 0x0B8A) return true;
        if (c < 0x0B8E) return false;  if (c <= 0x0B90) return true;
        if (c < 0x0B92) return false;  if (c <= 0x0B95) return true;
        if (c < 0x0B99) return false;  if (c <= 0x0B9A) return true;
        if (c == 0x0B9C) return true;
        if (c < 0x0B9E) return false;  if (c <= 0x0B9F) return true;
        if (c < 0x0BA3) return false;  if (c <= 0x0BA4) return true;
        if (c < 0x0BA8) return false;  if (c <= 0x0BAA) return true;
        if (c < 0x0BAE) return false;  if (c <= 0x0BB5) return true;
        if (c < 0x0BB7) return false;  if (c <= 0x0BB9) return true;
        if (c < 0x0C05) return false;  if (c <= 0x0C0C) return true;
        if (c < 0x0C0E) return false;  if (c <= 0x0C10) return true;
        if (c < 0x0C12) return false;  if (c <= 0x0C28) return true;
        if (c < 0x0C2A) return false;  if (c <= 0x0C33) return true;
        if (c < 0x0C35) return false;  if (c <= 0x0C39) return true;
        if (c < 0x0C60) return false;  if (c <= 0x0C61) return true;
        if (c < 0x0C85) return false;  if (c <= 0x0C8C) return true;
        if (c < 0x0C8E) return false;  if (c <= 0x0C90) return true;
        if (c < 0x0C92) return false;  if (c <= 0x0CA8) return true;
        if (c < 0x0CAA) return false;  if (c <= 0x0CB3) return true;
        if (c < 0x0CB5) return false;  if (c <= 0x0CB9) return true;
        if (c == 0x0CDE) return true;
        if (c < 0x0CE0) return false;  if (c <= 0x0CE1) return true;
        if (c < 0x0D05) return false;  if (c <= 0x0D0C) return true;
        if (c < 0x0D0E) return false;  if (c <= 0x0D10) return true;
        if (c < 0x0D12) return false;  if (c <= 0x0D28) return true;
        if (c < 0x0D2A) return false;  if (c <= 0x0D39) return true;
        if (c < 0x0D60) return false;  if (c <= 0x0D61) return true;
        if (c < 0x0E01) return false;  if (c <= 0x0E2E) return true;
        if (c == 0x0E30) return true;
        if (c < 0x0E32) return false;  if (c <= 0x0E33) return true;
        if (c < 0x0E40) return false;  if (c <= 0x0E45) return true;
        if (c < 0x0E81) return false;  if (c <= 0x0E82) return true;
        if (c == 0x0E84) return true;
        if (c < 0x0E87) return false;  if (c <= 0x0E88) return true;
        if (c == 0x0E8A) return true;
        if (c == 0x0E8D) return true;
        if (c < 0x0E94) return false;  if (c <= 0x0E97) return true;
        if (c < 0x0E99) return false;  if (c <= 0x0E9F) return true;
        if (c < 0x0EA1) return false;  if (c <= 0x0EA3) return true;
        if (c == 0x0EA5) return true;
        if (c == 0x0EA7) return true;
        if (c < 0x0EAA) return false;  if (c <= 0x0EAB) return true;
        if (c < 0x0EAD) return false;  if (c <= 0x0EAE) return true;
        if (c == 0x0EB0) return true;
        if (c < 0x0EB2) return false;  if (c <= 0x0EB3) return true;
        if (c == 0x0EBD) return true;
        if (c < 0x0EC0) return false;  if (c <= 0x0EC4) return true;
        if (c < 0x0F40) return false;  if (c <= 0x0F47) return true;
        if (c < 0x0F49) return false;  if (c <= 0x0F69) return true;
        if (c < 0x10A0) return false;  if (c <= 0x10C5) return true;
        if (c < 0x10D0) return false;  if (c <= 0x10F6) return true;
        if (c == 0x1100) return true;
        if (c < 0x1102) return false;  if (c <= 0x1103) return true;
        if (c < 0x1105) return false;  if (c <= 0x1107) return true;
        if (c == 0x1109) return true;
        if (c < 0x110B) return false;  if (c <= 0x110C) return true;
        if (c < 0x110E) return false;  if (c <= 0x1112) return true;
        if (c == 0x113C) return true;
        if (c == 0x113E) return true;
        if (c == 0x1140) return true;
        if (c == 0x114C) return true;
        if (c == 0x114E) return true;
        if (c == 0x1150) return true;
        if (c < 0x1154) return false;  if (c <= 0x1155) return true;
        if (c == 0x1159) return true;
        if (c < 0x115F) return false;  if (c <= 0x1161) return true;
        if (c == 0x1163) return true;
        if (c == 0x1165) return true;
        if (c == 0x1167) return true;
        if (c == 0x1169) return true;
        if (c < 0x116D) return false;  if (c <= 0x116E) return true;
        if (c < 0x1172) return false;  if (c <= 0x1173) return true;
        if (c == 0x1175) return true;
        if (c == 0x119E) return true;
        if (c == 0x11A8) return true;
        if (c == 0x11AB) return true;
        if (c < 0x11AE) return false;  if (c <= 0x11AF) return true;
        if (c < 0x11B7) return false;  if (c <= 0x11B8) return true;
        if (c == 0x11BA) return true;
        if (c < 0x11BC) return false;  if (c <= 0x11C2) return true;
        if (c == 0x11EB) return true;
        if (c == 0x11F0) return true;
        if (c == 0x11F9) return true;
        if (c < 0x1E00) return false;  if (c <= 0x1E9B) return true;
        if (c < 0x1EA0) return false;  if (c <= 0x1EF9) return true;
        if (c < 0x1F00) return false;  if (c <= 0x1F15) return true;
        if (c < 0x1F18) return false;  if (c <= 0x1F1D) return true;
        if (c < 0x1F20) return false;  if (c <= 0x1F45) return true;
        if (c < 0x1F48) return false;  if (c <= 0x1F4D) return true;
        if (c < 0x1F50) return false;  if (c <= 0x1F57) return true;
        if (c == 0x1F59) return true;
        if (c == 0x1F5B) return true;
        if (c == 0x1F5D) return true;
        if (c < 0x1F5F) return false;  if (c <= 0x1F7D) return true;
        if (c < 0x1F80) return false;  if (c <= 0x1FB4) return true;
        if (c < 0x1FB6) return false;  if (c <= 0x1FBC) return true;
        if (c == 0x1FBE) return true;
        if (c < 0x1FC2) return false;  if (c <= 0x1FC4) return true;
        if (c < 0x1FC6) return false;  if (c <= 0x1FCC) return true;
        if (c < 0x1FD0) return false;  if (c <= 0x1FD3) return true;
        if (c < 0x1FD6) return false;  if (c <= 0x1FDB) return true;
        if (c < 0x1FE0) return false;  if (c <= 0x1FEC) return true;
        if (c < 0x1FF2) return false;  if (c <= 0x1FF4) return true;
        if (c < 0x1FF6) return false;  if (c <= 0x1FFC) return true;
        if (c == 0x2126) return true;
        if (c < 0x212A) return false;  if (c <= 0x212B) return true;
        if (c == 0x212E) return true;
        if (c < 0x2180) return false;  if (c <= 0x2182) return true;
        if (c == 0x3007) return true;
        if (c < 0x3021) return false;  if (c <= 0x3029) return true;
        if (c < 0x3041) return false;  if (c <= 0x3094) return true;
        if (c < 0x30A1) return false;  if (c <= 0x30FA) return true;
        if (c < 0x3105) return false;  if (c <= 0x312C) return true;
        if (c < 0x4E00) return false;  if (c <= 0x9FA5) return true;
        if (c < 0xAC00) return false;  if (c <= 0xD7A3) return true;

        return false;

    }

    /**
     * Determine whether a specified character
     * is a combining character according to production 87
     * of the XML 1.0 specification.
     *
     * @param c <code>char</code> to check
     * @return <code>boolean</code> true if it's a combining character,
     *         false otherwise
     */
    static boolean isXMLCombiningChar(char c) {
        // CombiningChar
        if (c < 0x0300) return false;  if (c <= 0x0345) return true;
        if (c < 0x0360) return false;  if (c <= 0x0361) return true;
        if (c < 0x0483) return false;  if (c <= 0x0486) return true;
        if (c < 0x0591) return false;  if (c <= 0x05A1) return true;

        if (c < 0x05A3) return false;  if (c <= 0x05B9) return true;
        if (c < 0x05BB) return false;  if (c <= 0x05BD) return true;
        if (c == 0x05BF) return true;
        if (c < 0x05C1) return false;  if (c <= 0x05C2) return true;

        if (c == 0x05C4) return true;
        if (c < 0x064B) return false;  if (c <= 0x0652) return true;
        if (c == 0x0670) return true;
        if (c < 0x06D6) return false;  if (c <= 0x06DC) return true;

        if (c < 0x06DD) return false;  if (c <= 0x06DF) return true;
        if (c < 0x06E0) return false;  if (c <= 0x06E4) return true;
        if (c < 0x06E7) return false;  if (c <= 0x06E8) return true;

        if (c < 0x06EA) return false;  if (c <= 0x06ED) return true;
        if (c < 0x0901) return false;  if (c <= 0x0903) return true;
        if (c == 0x093C) return true;
        if (c < 0x093E) return false;  if (c <= 0x094C) return true;

        if (c == 0x094D) return true;
        if (c < 0x0951) return false;  if (c <= 0x0954) return true;
        if (c < 0x0962) return false;  if (c <= 0x0963) return true;
        if (c < 0x0981) return false;  if (c <= 0x0983) return true;

        if (c == 0x09BC) return true;
        if (c == 0x09BE) return true;
        if (c == 0x09BF) return true;
        if (c < 0x09C0) return false;  if (c <= 0x09C4) return true;
        if (c < 0x09C7) return false;  if (c <= 0x09C8) return true;

        if (c < 0x09CB) return false;  if (c <= 0x09CD) return true;
        if (c == 0x09D7) return true;
        if (c < 0x09E2) return false;  if (c <= 0x09E3) return true;
        if (c == 0x0A02) return true;
        if (c == 0x0A3C) return true;

        if (c == 0x0A3E) return true;
        if (c == 0x0A3F) return true;
        if (c < 0x0A40) return false;  if (c <= 0x0A42) return true;
        if (c < 0x0A47) return false;  if (c <= 0x0A48) return true;

        if (c < 0x0A4B) return false;  if (c <= 0x0A4D) return true;
        if (c < 0x0A70) return false;  if (c <= 0x0A71) return true;
        if (c < 0x0A81) return false;  if (c <= 0x0A83) return true;
        if (c == 0x0ABC) return true;

        if (c < 0x0ABE) return false;  if (c <= 0x0AC5) return true;
        if (c < 0x0AC7) return false;  if (c <= 0x0AC9) return true;
        if (c < 0x0ACB) return false;  if (c <= 0x0ACD) return true;

        if (c < 0x0B01) return false;  if (c <= 0x0B03) return true;
        if (c == 0x0B3C) return true;
        if (c < 0x0B3E) return false;  if (c <= 0x0B43) return true;
        if (c < 0x0B47) return false;  if (c <= 0x0B48) return true;

        if (c < 0x0B4B) return false;  if (c <= 0x0B4D) return true;
        if (c < 0x0B56) return false;  if (c <= 0x0B57) return true;
        if (c < 0x0B82) return false;  if (c <= 0x0B83) return true;

        if (c < 0x0BBE) return false;  if (c <= 0x0BC2) return true;
        if (c < 0x0BC6) return false;  if (c <= 0x0BC8) return true;
        if (c < 0x0BCA) return false;  if (c <= 0x0BCD) return true;
        if (c == 0x0BD7) return true;

        if (c < 0x0C01) return false;  if (c <= 0x0C03) return true;
        if (c < 0x0C3E) return false;  if (c <= 0x0C44) return true;
        if (c < 0x0C46) return false;  if (c <= 0x0C48) return true;

        if (c < 0x0C4A) return false;  if (c <= 0x0C4D) return true;
        if (c < 0x0C55) return false;  if (c <= 0x0C56) return true;
        if (c < 0x0C82) return false;  if (c <= 0x0C83) return true;

        if (c < 0x0CBE) return false;  if (c <= 0x0CC4) return true;
        if (c < 0x0CC6) return false;  if (c <= 0x0CC8) return true;
        if (c < 0x0CCA) return false;  if (c <= 0x0CCD) return true;

        if (c < 0x0CD5) return false;  if (c <= 0x0CD6) return true;
        if (c < 0x0D02) return false;  if (c <= 0x0D03) return true;
        if (c < 0x0D3E) return false;  if (c <= 0x0D43) return true;

        if (c < 0x0D46) return false;  if (c <= 0x0D48) return true;
        if (c < 0x0D4A) return false;  if (c <= 0x0D4D) return true;
        if (c == 0x0D57) return true;
        if (c == 0x0E31) return true;

        if (c < 0x0E34) return false;  if (c <= 0x0E3A) return true;
        if (c < 0x0E47) return false;  if (c <= 0x0E4E) return true;
        if (c == 0x0EB1) return true;
        if (c < 0x0EB4) return false;  if (c <= 0x0EB9) return true;

        if (c < 0x0EBB) return false;  if (c <= 0x0EBC) return true;
        if (c < 0x0EC8) return false;  if (c <= 0x0ECD) return true;
        if (c < 0x0F18) return false;  if (c <= 0x0F19) return true;
        if (c == 0x0F35) return true;

        if (c == 0x0F37) return true;
        if (c == 0x0F39) return true;
        if (c == 0x0F3E) return true;
        if (c == 0x0F3F) return true;
        if (c < 0x0F71) return false;  if (c <= 0x0F84) return true;

        if (c < 0x0F86) return false;  if (c <= 0x0F8B) return true;
        if (c < 0x0F90) return false;  if (c <= 0x0F95) return true;
        if (c == 0x0F97) return true;
        if (c < 0x0F99) return false;  if (c <= 0x0FAD) return true;

        if (c < 0x0FB1) return false;  if (c <= 0x0FB7) return true;
        if (c == 0x0FB9) return true;
        if (c < 0x20D0) return false;  if (c <= 0x20DC) return true;
        if (c == 0x20E1) return true;

        if (c < 0x302A) return false;  if (c <= 0x302F) return true;
        if (c == 0x3099) return true;
        if (c == 0x309A) return true;

        return false;

    }

    /**
     * Determine whether a specified
     * character is an extender according to production 88 of the XML 1.0
     * specification.
     *
     * @param c <code>char</code> to check
     * @return true if it's an extender, false otherwise
     */
    static boolean isXMLExtender(char c) {

        if (c < 0x00B6) return false;  // quick short circuit

        // Extenders
        if (c == 0x00B7) return true;
        if (c == 0x02D0) return true;
        if (c == 0x02D1) return true;
        if (c == 0x0387) return true;
        if (c == 0x0640) return true;
        if (c == 0x0E46) return true;
        if (c == 0x0EC6) return true;
        if (c == 0x3005) return true;

        if (c < 0x3031) return false;  if (c <= 0x3035) return true;
        if (c < 0x309D) return false;  if (c <= 0x309E) return true;
        if (c < 0x30FC) return false;  if (c <= 0x30FE) return true;

        return false;

    }

    /**
     * Determine whether a specified Unicode character
     * is a digit according to production 88 of the XML 1.0 specification.
     *
     * @param c <code>char</code> to check for XML digit compliance
     * @return <code>boolean</code> true if it's a digit, false otherwise
     */
    static boolean isXMLDigit(char c) {

        if (c < 0x0030) return false;  if (c <= 0x0039) return true;
        if (c < 0x0660) return false;  if (c <= 0x0669) return true;
        if (c < 0x06F0) return false;  if (c <= 0x06F9) return true;
        if (c < 0x0966) return false;  if (c <= 0x096F) return true;

        if (c < 0x09E6) return false;  if (c <= 0x09EF) return true;
        if (c < 0x0A66) return false;  if (c <= 0x0A6F) return true;
        if (c < 0x0AE6) return false;  if (c <= 0x0AEF) return true;

        if (c < 0x0B66) return false;  if (c <= 0x0B6F) return true;
        if (c < 0x0BE7) return false;  if (c <= 0x0BEF) return true;
        if (c < 0x0C66) return false;  if (c <= 0x0C6F) return true;

        if (c < 0x0CE6) return false;  if (c <= 0x0CEF) return true;
        if (c < 0x0D66) return false;  if (c <= 0x0D6F) return true;
        if (c < 0x0E50) return false;  if (c <= 0x0E59) return true;

        if (c < 0x0ED0) return false;  if (c <= 0x0ED9) return true;
        if (c < 0x0F20) return false;  if (c <= 0x0F29) return true;

        return false;
    }

}
