/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/SubstringFunction.java,v 1.16 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.16 $
 * $Date: 2006/02/05 21:47:41 $
 *
 * ====================================================================
 *
 * Copyright 2000-2002 bob mcwhirter & James Strachan.
 * All rights reserved.
 *
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
package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.List;
/**
 * <p>
 * <b>4.2</b>
 * <code><i>string</i> substring(<i>string</i>,<i>number</i>,<i>number?</i>)</code>
 * </p>
 *
 * <blockquote src="http://www.w3.org/TR/xpath">
 * <p>The <b>substring</b> function returns the
 * substring of the first argument starting at the position specified in
 * the second argument with length specified in the third argument. For
 * example,
 *
 * <code>substring("12345",2,3)</code> returns <code>"234"</code>.
 * If the third argument is not specified, it returns the substring
 * starting at the position specified in the second argument and
 * continuing to the end of the string. For example,
 * <code>substring("12345",2)</code> returns <code>"2345"</code>.
 * </p>
 *
 * <p>
 * More precisely, each character in the string (see <a
 * href="http://www.w3.org/TR/xpath#strings">[<b>3.6 Strings</b>]</a>) is considered to have a
 * numeric position: the position of the first character is 1, the
 * position of the second character is 2 and so on.
 * </p>
 *
 * <blockquote> <b>NOTE: </b>This differs from Java and ECMAScript, in
 * which the <code>String.substring</code> method treats the position
 * of the first character as 0.</blockquote>
 *
 * <p>
 * The returned substring contains those characters for which the
 * position of the character is greater than or equal to the rounded
 * value of the second argument and, if the third argument is specified,
 * less than the sum of the rounded value of the second argument and the
 * rounded value of the third argument; the comparisons and addition
 * used for the above follow the standard IEEE 754 rules; rounding is
 * done as if by a call to the <b><a href="#function-round">round</a></b>
 * function. The following examples illustrate various unusual cases:
 * </p>
 *
 * <ul>
 *
 * <li>
 * <p>
 * <code>substring("12345", 1.5, 2.6)</code> returns
 * <code>"234"</code>
 * </p>
 * </li>
 *
 * <li>
 * <p>
 * <code>substring("12345", 0, 3)</code> returns <code>"12"</code>
 *
 * </p>
 * </li>
 *
 * <li>
 * <p>
 * <code>substring("12345", 0 div 0, 3)</code> returns <code>""</code>
 * </p>
 * </li>
 *
 * <li>
 * <p>.
 * <code>substring("12345", 1, 0 div 0)</code> returns
 *
 * <code>""</code>
 * </p>
 * </li>
 *
 * <li>
 * <p>
 * <code>substring("12345", -42, 1 div 0)</code> returns
 * <code>"12345"</code>
 * </p>
 * </li>
 *
 * <li>
 * <p>
 *
 * <code>substring("12345", -1 div 0, 1 div 0)</code> returns
 * <code>""</code> </blockquote>
 *
 * @author bob mcwhirter (bob @ werken.com)
 *
 * @see <a href="http://www.w3.org/TR/xpath#function-substring"
 *      target="_top">Section 4.2 of the XPath Specification</a>
 */
public class SubstringFunction implements Function
{

    /**
     * Create a new <code>SubstringFunction</code> object.
     */
    public SubstringFunction() {}


    /** Returns a substring of an XPath string-value by character index.
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list that contains two or three items
     *
     * @return a <code>String</code> containing the specifed character subsequence of
     *     the original string or the string-value of the context node
     *
     * @throws FunctionCallException if <code>args</code> has more than three
     *     or less than two items
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        final int argc = args.size();
        if (argc < 2 || argc > 3){
            throw new FunctionCallException( "substring() requires two or three arguments." );
        }

        final Navigator nav = context.getNavigator();

        final String str = StringFunction.evaluate(args.get(0), nav );
        // The spec doesn't really address this case
        if (str == null) {
            return "";
        }

        final int stringLength = (StringLengthFunction.evaluate(args.get(0), nav )).intValue();

        if (stringLength == 0) {
            return "";
        }

        Double d1 = NumberFunction.evaluate(args.get(1), nav);

        if (d1.isNaN()){
            return "";
        }
        // Round the value and subtract 1 as Java strings are zero based
        int start = RoundFunction.evaluate(d1, nav).intValue() - 1;

        int substringLength = stringLength;
        if (argc == 3){
            Double d2 = NumberFunction.evaluate(args.get(2), nav);

            if (!d2.isNaN()){
                substringLength = RoundFunction.evaluate(d2, nav ).intValue();
            }
            else {
                substringLength = 0;
            }
        }

        if (substringLength < 0) return "";

        int end = start + substringLength;
        if (argc == 2) end = stringLength;

        // negative start is treated as 0
        if ( start < 0){
            start = 0;
        }
        else if (start > stringLength){
            return "";
        }

        if (end > stringLength){
            end = stringLength;
        }
        else if (end < start) return "";

        if (stringLength == str.length()) {
            // easy case; no surrogate pairs
            return str.substring(start, end);
        }
        else {
            return unicodeSubstring(str, start, end);
        }

    }

    private static String unicodeSubstring(String s, int start, int end) {

        StringBuffer result = new StringBuffer(s.length());
        for (int jChar = 0, uChar=0; uChar < end; jChar++, uChar++) {
            char c = s.charAt(jChar);
            if (uChar >= start) result.append(c);
            if (c >= 0xD800) { // get the low surrogate
                // ???? we could check here that this is indeed a low surroagte
                // we could also catch StringIndexOutOfBoundsException
                jChar++;
                if (uChar >= start) result.append(s.charAt(jChar));
            }
        }
        return result.toString();
    }
}
