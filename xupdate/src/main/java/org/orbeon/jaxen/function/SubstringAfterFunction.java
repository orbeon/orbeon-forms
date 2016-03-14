/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/SubstringAfterFunction.java,v 1.11 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.11 $
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
 * $Id: SubstringAfterFunction.java,v 1.11 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.List;

/**
 * <p><b>4.2</b> <code><i>string</i> substring-after(<i>string</i>,<i>string</i>)</code></p>
 *
 *
 * <blockquote src="http://www.w3.org/TR/xpath">
 * The <b>substring-after</b> function returns the substring of the first argument string
 * that follows the first occurrence of the second argument string in the first
 * argument string, or the empty string if the first argument string does not contain the second argument string.
 * For example, substring-after("1999/04/01","/") returns 04/01,
 * and substring-after("1999/04/01","19") returns 99/04/01.
 * </blockquote>
 *
 * @author bob mcwhirter (bob @ werken.com)
 * @see <a href="http://www.w3.org/TR/xpath#function-substring-after" target="_top">Section 4.2 of the XPath Specification</a>
 */
public class SubstringAfterFunction implements Function
{

    /**
     * Create a new <code>SubstringAfterFunction</code> object.
     */
    public SubstringAfterFunction() {}


    /**
     * Returns the part of the string-value of the first item in <code>args</code>
     * that follows the string-value of the second item in <code>args</code>;
     * or the empty string if the second string is not a substring of the first string.
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list that contains two items
     *
     * @return a <code>String</code> containing that
     *     part of the string-value of the first item in <code>args</code>
     *     that comes before the string-value of the second item in <code>args</code>
     *
     * @throws FunctionCallException if <code>args</code> does not have length two
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if (args.size() == 2)
        {
            return evaluate( args.get(0),
                             args.get(1),
                             context.getNavigator() );
        }

        throw new FunctionCallException( "substring-after() requires two arguments." );
    }


    /**
     * Returns the part of <code>strArg</code> that follows the first occurence
     * of <code>matchArg</code>; or the empty string if the
     * <code>strArg</code> does not contain <code>matchArg</code>
     *
     * @param strArg the string from which the substring is extracted
     * @param matchArg the string that marks the boundary of the substring
     * @param nav the <code>Navigator</code> used to calculate the string-values of
     *     the first two arguments
     *
     * @return a <code>String</code> containing
     *     the part of <code>strArg</code> that precedes the first occurence
     *     of <code>matchArg</code>
     *
     */
    public static String evaluate(Object strArg,
                                  Object matchArg,
                                  Navigator nav)
    {
        String str   = StringFunction.evaluate( strArg,
                                                nav );

        String match = StringFunction.evaluate( matchArg,
                                                nav );

        int loc = str.indexOf(match);

        if ( loc < 0 )
        {
            return "";
        }

        return str.substring(loc+match.length());
    }
}
