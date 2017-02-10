package org.orbeon.saxon.event;

import org.orbeon.saxon.tinytree.CompressedWhitespace;
import org.orbeon.saxon.trans.XPathException;

public class HTML1252Emitter extends HTMLEmitter {

    public static char[] CP1252_BEST_FIT = new char[] {
        '\u007f', // Delete
        '\u20ac', // Euro Sign 
        '\u0081',
        '\u201a', // Single Low-9 Quotation Mark 
        '\u0192', // Latin Small Letter F With Hook 
        '\u201e', // Double Low-9 Quotation Mark 
        '\u2026', // Horizontal Ellipsis 
        '\u2020', // Dagger 
        '\u2021', // Double Dagger 
        '\u02c6', // Modifier Letter Circumflex Accent 
        '\u2030', // Per Mille Sign 
        '\u0160', // Latin Capital Letter S With Caron 
        '\u2039', // Single Left-Pointing Angle Quotation Mark 
        '\u0152', // Latin Capital Ligature Oe 
        '\u008d',
        '\u017d', // Latin Capital Letter Z With Caron 
        '\u008f',
        '\u0090',
        '\u2018', // Left Single Quotation Mark 
        '\u2019', // Right Single Quotation Mark 
        '\u201c', // Left Double Quotation Mark 
        '\u201d', // Right Double Quotation Mark 
        '\u2022', // Bullet 
        '\u2013', // En Dash 
        '\u2014', // Em Dash 
        '\u02dc', // Small Tilde 
        '\u2122', // Trade Mark Sign 
        '\u0161', // Latin Small Letter S With Caron 
        '\u203a', // Single Right-Pointing Angle Quotation Mark 
        '\u0153', // Latin Small Ligature Oe 
        '\u009d',
        '\u017e', // Latin Small Letter Z With Caron 
        '\u0178'  // Latin Capital Letter Y With Diaeresis
    };

    // This method overrides the Saxon method so we can do a custom fix-up of invalid CP-1252 characters.
    @Override protected void writeEscape(final CharSequence chars, final boolean inAttribute)
    throws java.io.IOException, XPathException {

        int segstart = 0;
        final boolean[] specialChars = (inAttribute ? specialInAtt : specialInText);

        if (chars instanceof CompressedWhitespace) {
            ((CompressedWhitespace)chars).writeEscape(specialChars, writer);
            return;
        }
        boolean disabled = false;

        while (segstart < chars.length()) {
            int i = segstart;

            // find a maximal sequence of "ordinary" characters

            if (nonASCIIRepresentation == REP_NATIVE) {
                char c;
                while (i < chars.length() &&
                        ((c = chars.charAt(i)) < 127 ? !specialChars[c] : (characterSet.inCharset(c) && c > 160)
     				 )
     			  ) {
                    i++;
                }
            } else {
                char c;
                while (i < chars.length() && (c = chars.charAt(i)) < 127 && !specialChars[c]) {
                    i++;
                }
            }



            // if this was the whole string, output the string and quit

            if (i == chars.length()) {
                if (segstart == 0) {
                    writeCharSequence(chars);
                } else {
                    writeCharSequence(chars.subSequence(segstart, i));
                }
                return;
            }

            // otherwise, output this sequence and continue
            if (i > segstart) {
                writeCharSequence(chars.subSequence(segstart, i));
            }

            final char c = chars.charAt(i);

            if (c==0) {
                // used to switch escaping on and off
                // See https://github.com/orbeon/orbeon-forms/issues/3115
//                disabled = !disabled;
            } else if (disabled) {
                writer.write(c);
            } else if (c<=127) {

                // handle a special ASCII character

                if (inAttribute) {
                    if (c=='<') {
                        writer.write('<');                       // not escaped
                    } else if (c=='>') {
                        writer.write("&gt;");           // recommended for older browsers
                    } else if (c=='&') {
                        if (i+1<chars.length() && chars.charAt(i+1)=='{') {
                            writer.write('&');                   // not escaped if followed by '{'
                        } else {
                            writer.write("&amp;");
                        }
                    } else if (c=='\"') {
                        writer.write("&#34;");
                    } else if (c=='\n') {
                        writer.write("&#xA;");
                    } else if (c=='\t') {
                        writer.write("&#x9;");
                    } else if (c=='\r') {
                        writer.write("&#xD;");
                    }
                } else {
                    if (c=='<') {
                        writer.write("&lt;");
                    } else if (c=='>') {
                        writer.write("&gt;");  // changed to allow for "]]>"
                    } else if (c=='&') {
                        writer.write("&amp;");
                    } else if (c=='\r') {
                        writer.write("&#xD;");
                    }
                }

        	} else if (c==160) {
        		// always output NBSP as an entity reference
            	writer.write("&nbsp;");

            } else if (c>=127 && c<160) {
                // these control characters are illegal in HTML

                // ORBEON: Handle faulty CP-1252 characters ending up as unicode code points.
                outputCharacterReference(CP1252_BEST_FIT[c - 127]);

            } else if (c>=55296 && c<=56319) {  //handle surrogate pair

                //A surrogate pair is two consecutive Unicode characters.  The first
                //is in the range D800 to DBFF, the second is in the range DC00 to DFFF.
                //To compute the numeric value of the character corresponding to a surrogate
                //pair, use this formula (all numbers are hex):
        	    //(FirstChar - D800) * 400 + (SecondChar - DC00) + 10000

                    // we'll trust the data to be sound
                int charval = (((int)c - 55296) * 1024) + ((int)chars.charAt(i+1) - 56320) + 65536;
                outputCharacterReference(charval);
                i++;


            } else if (characterSet.inCharset(c)) {
            	switch(nonASCIIRepresentation) {
            		case REP_NATIVE:
            			writer.write(c);
            			break;
            		case REP_ENTITY:
            			if (c>160 && c<=255) {

			                // if chararacter in iso-8859-1, use an entity reference

			                writer.write('&');
			                writer.write(latin1Entities[(int)c-160]);
			                writer.write(';');
			                break;
			            }
			            // else fall through
			        case REP_DECIMAL:
			        	preferHex = false;
			        	outputCharacterReference(c);
			        	break;
			        case REP_HEX:
			        	preferHex = true;
			        	// fall through
			        default:
			        	outputCharacterReference(c);
			        	break;
			    }

            } else {
                // Character not present in encoding
                switch(excludedRepresentation) {
            		case REP_ENTITY:
            			if (c>160 && c<=255) {

			                // if chararacter in iso-8859-1, use an entity reference

			                writer.write('&');
			                writer.write(latin1Entities[(int)c-160]);
			                writer.write(';');
			                break;
			            }
			            // else fall through
                    case REP_NATIVE:
                    case REP_DECIMAL:
			        	preferHex = false;
			        	outputCharacterReference(c);
			        	break;
			        case REP_HEX:
			        	preferHex = true;
			        	// fall through
			        default:
			        	outputCharacterReference(c);
			        	break;
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
