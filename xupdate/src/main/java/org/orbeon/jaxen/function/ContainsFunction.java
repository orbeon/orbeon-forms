/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/ContainsFunction.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
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
 * $Id: ContainsFunction.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
 */

package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.List;

/**
 *  <p><b>4.2</b> <code><i>boolean</i> contains(<i>string</i>,<i>string</i>)</code></p>
 *
 * <blockquote src="http://www.w3.org/TR/xpath">
 * The <b>contains</b> function returns true if the first argument
 * string contains the second argument string, and otherwise returns false.
 * </blockquote>
 *
 * @author bob mcwhirter (bob @ werken.com)
 *
 * @see <a href="http://www.w3.org/TR/xpath#function-contains">Section 4.2 of the XPath Specification</a>
 */
public class ContainsFunction implements Function
{

    /**
     * Create a new <code>ContainsFunction</code> object.
     */
    public ContainsFunction() {}

    /**
     * <p>
     *  Returns true if the string-value of the
     *  first item in <code>args</code> contains string-value of the second
     *  item; false otherwise.
     *  If necessary one or both items are converted to a string as if by the XPath
     *  <code>string()</code> function.
     * </p>
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list containing exactly two items
     *
     * @return the result of evaluating the function;
     *     <code>Boolean.TRUE</code> or <code>Boolean.FALSE</code>
     *
     * @throws FunctionCallException if <code>args</code> does not have exactly two items
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if (args.size() == 2)
        {
            return evaluate(args.get(0),
                            args.get(1),
                            context.getNavigator() );
        }

        throw new FunctionCallException("contains() requires two arguments.");
    }

    /**
     * <p>Returns true if the first string contains the second string; false otherwise.
     * If necessary one or both arguments are converted to a string as if by the XPath
     * <code>string()</code> function.
     * </p>
     *
     * @param strArg the containing string
     * @param matchArg the contained string
     * @param nav used to calculate the string-values of the first two arguments
     *
     * @return <code>Boolean.TRUE</code> if true if the first string contains
     *     the second string; <code>Boolean.FALSE</code> otherwise.
     */
    public static Boolean evaluate(Object strArg,
                                   Object matchArg,
                                   Navigator nav)
    {
        String str   = StringFunction.evaluate( strArg,
                                                nav );

        String match = StringFunction.evaluate( matchArg,
                                                nav );

        return ( ( str.indexOf(match) >= 0)
                 ? Boolean.TRUE
                 : Boolean.FALSE
                 );
    }
}
