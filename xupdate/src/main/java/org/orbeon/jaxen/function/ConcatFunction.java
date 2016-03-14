/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/ConcatFunction.java,v 1.9 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.9 $
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
 * $Id: ConcatFunction.java,v 1.9 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.Iterator;
import java.util.List;

/**
 * <b>4.2</b> <code><i>string</i> concat(<i>string</i>,<i>string</i>,<i>string*</i>)</code>
 * <p>
 * Concatenates its arguments and returns the resulting string.
 * </p>
 *
 *  @author bob mcwhirter (bob@werken.com)
 *
 * @see <a href="http://www.w3.org/TR/xpath#function-concat">Section 4.2 of the XPath Specification</a>
 */
public class ConcatFunction implements Function
{

    /**
     * Create a new <code>ConcatFunction</code> object.
     */
    public ConcatFunction() {}

    /**
     * Concatenates the arguments and returns the resulting string.
     * Non-string items are first converted to strings as if by the
     * XPath <code>string()<code> function.
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args the list of strings to be concatenated
     *
     * @return a <code>String</code> containing the concatenation of the items
     *     of <code>args</code>
     *
     * @throws FunctionCallException if <code>args</code> has less than two items
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if ( args.size() >= 2 )
        {
            return evaluate( args,
                             context.getNavigator() );
        }

        throw new FunctionCallException("concat() requires at least two arguments");
    }

    /**
     * Converts each item in the list to a string and returns the
     * concatenation of these strings.
     * If necessary, each item is first converted to a <code>String</code>
     * as if by the XPath <code>string()</code> function.
     *
     * @param list the items to be concatenated
     * @param nav ignored
     *
     * @return the concatenation of the arguments
     */
   public static String evaluate(List list,
                                  Navigator nav)
    {
        StringBuffer result = new StringBuffer();
        Iterator argIter = list.iterator();
        while ( argIter.hasNext() )
        {
            result.append( StringFunction.evaluate( argIter.next(),
                                                    nav ) );
        }

        return result.toString();
    }
}
