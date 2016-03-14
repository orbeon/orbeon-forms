/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/TranslateFunction.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.10 $
 * $Date: 2006/02/05 21:47:41 $
 *
 * ====================================================================
 *
 * Copyright 2000-2002 bob mcwhirter & James Strachan.
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
 * $Id: TranslateFunction.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * <b>4.2</b>
 * <code><i>string</i> translate(<i>string</i>,<i>string</i>,<i>string</i>)</code>
 * </p>
 *
 * <blockquote src="http://www.w3.org/TR/xpath#function-translate">
 * <p>
 * The <b><a href="http://www.w3.org/TR/xpath#function-translate">translate</a></b> function
 * returns the first argument string with occurrences of characters in
 * the second argument string replaced by the character at the
 * corresponding position in the third argument string. For example,
 * <code>translate("bar","abc","ABC")</code> returns the string
 * <code>BAr</code>. If there is a character in the second argument
 * string with no character at a corresponding position in the third
 * argument string (because the second argument string is longer than
 * the third argument string), then occurrences of that character in the
 * first argument string are removed. For example,
 * <code>translate("--aaa--","abc-","ABC")</code> returns
 * <code>"AAA"</code>. If a character occurs more than once in the
 * second argument string, then the first occurrence determines the
 * replacement character. If the third argument string is longer than
 * the second argument string, then excess characters are ignored.
 * </p>
 *
 * <blockquote> <b>NOTE: </b>The <b>translate</b> function is not a
 * sufficient solution for case conversion in all languages. A future
 * version of XPath may provide additional functions for case
 * conversion.</blockquote>
 *
 * </blockquote>
 *
 * @author Jan Dvorak ( jan.dvorak @ mathan.cz )
 *
 * @see <a href="http://www.w3.org/TR/xpath#function-translate"
 *      target="_top">Section 4.2 of the XPath Specification</a>
 */
public class TranslateFunction implements Function
{

     /* The translation is done thru a HashMap. Performance tip (for anyone
      * who needs to improve the performance of this particular function):
      * Cache the HashMaps, once they are constructed. */

    /**
     * Create a new <code>TranslateFunction</code> object.
     */
    public TranslateFunction() {}


    /** Returns a copy of the first argument in which
     * characters found in the second argument are replaced by
     * corresponding characters from the third argument.
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list that contains exactly three items
     *
     * @return a <code>String</code> built from <code>args.get(0)</code>
     *     in which occurrences of characters in <code>args.get(1)</code>
     *     are replaced by the corresponding characters in <code>args.get(2)</code>
     *
     * @throws FunctionCallException if <code>args</code> does not have exactly three items
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if (args.size() == 3) {
            return evaluate( args.get(0),
                             args.get(1),
                             args.get(2),
                             context.getNavigator() );
        }

        throw new FunctionCallException( "translate() requires three arguments." );
    }

    /**
     * Returns a copy of <code>strArg</code> in which
     * characters found in <code>fromArg</code> are replaced by
     * corresponding characters from <code>toArg</code>.
     * If necessary each argument is first converted to it string-value
     * as if by the XPath <code>string()</code> function.
     *
     * @param strArg the base string
     * @param fromArg the characters to be replaced
     * @param toArg the characters they will be replaced by
     * @param nav the <code>Navigator</code> used to calculate the string-values of the arguments.
     *
     * @return a copy of <code>strArg</code> in which
     *  characters found in <code>fromArg</code> are replaced by
     *  corresponding characters from <code>toArg</code>
     *
     * @throws FunctionCallException if one of the arguments is a malformed Unicode string;
     *     that is, if surrogate characters don't line up properly
     *
     */
    public static String evaluate(Object strArg,
                                  Object fromArg,
                                  Object toArg,
                                  Navigator nav) throws FunctionCallException
    {
        String inStr = StringFunction.evaluate( strArg, nav );
        String fromStr = StringFunction.evaluate( fromArg, nav );
        String toStr = StringFunction.evaluate( toArg, nav );

        // Initialize the mapping in a HashMap
        Map characterMap = new HashMap();
        String[] fromCharacters = toUnicodeCharacters(fromStr);
        String[] toCharacters = toUnicodeCharacters(toStr);
        int fromLen = fromCharacters.length;
        int toLen = toCharacters.length;
        for ( int i = 0; i < fromLen; i++ ) {
            String cFrom = fromCharacters[i];
            if ( characterMap.containsKey( cFrom ) ) {
                // We've seen the character before, ignore
                continue;
            }

            if ( i < toLen ) {
                // Will change
                characterMap.put( cFrom, toCharacters[i] );
            }
            else {
                // Will delete
                characterMap.put( cFrom, null );
            }
        }

        // Process the input string thru the map
        StringBuffer outStr = new StringBuffer( inStr.length() );
        String[] inCharacters = toUnicodeCharacters(inStr);
        int inLen = inCharacters.length;
        for ( int i = 0; i < inLen; i++ ) {
            String cIn = inCharacters[i];
            if ( characterMap.containsKey( cIn ) ) {
                String cTo = (String) characterMap.get( cIn );
                if ( cTo != null ) {
                    outStr.append( cTo );
                }
            }
            else {
                outStr.append( cIn );
            }
        }

        return outStr.toString();
    }

    private static String[] toUnicodeCharacters(String s) throws FunctionCallException {

        String[] result = new String[s.length()];
        int stringLength = 0;
        for (int i = 0; i < s.length(); i++) {
            char c1 = s.charAt(i);
            if (isHighSurrogate(c1)) {
                try {
                    char c2 = s.charAt(i+1);
                    if (isLowSurrogate(c2)) {
                        result[stringLength] = (c1 + "" + c2).intern();
                        i++;
                    }
                    else {
                        throw new FunctionCallException("Mismatched surrogate pair in translate function");
                    }
                }
                catch (StringIndexOutOfBoundsException ex) {
                    throw new FunctionCallException("High surrogate without low surrogate at end of string passed to translate function");
                }
            }
            else {
                result[stringLength]=String.valueOf(c1).intern();
            }
            stringLength++;
        }

        if (stringLength == result.length) return result;

        // trim array
        String[] trimmed = new String[stringLength];
        System.arraycopy(result, 0, trimmed, 0, stringLength);
        return trimmed;

    }

    private static boolean isHighSurrogate(char c) {
        return c >= 0xD800 && c <= 0xDBFF;
    }

    private static boolean isLowSurrogate(char c) {
        return c >= 0xDC00 && c <= 0xDFFF;
    }

}