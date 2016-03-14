/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/BooleanFunction.java,v 1.17 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.17 $
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
 * $Id: BooleanFunction.java,v 1.17 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.List;

/**
 * <p>
 * <b>4.3</b> <code><i>boolean</i> boolean(<i>object</i>)</code>
 * </p>
 *
 * <blockquote
 * src="http://www.w3.org/TR/xpath#section-Boolean-Functions">
 * <p>
 * The <b><a href="http://www.w3.org/TR/xpath#function-boolean" target="_top">boolean</a></b>
 * function converts its argument to a boolean as follows:
 * </p>
 *
 * <ul>
 *
 * <li>
 * <p>
 * a number is true if and only if it is neither positive or negative
 * zero nor NaN
 * </p>
 * </li>
 *
 * <li>
 * <p>
 * a node-set is true if and only if it is non-empty
 * </p>
 * </li>
 *
 * <li>
 * <p>
 * a string is true if and only if its length is non-zero
 * </p>
 * </li>
 *
 * <li>
 *
 * <p>
 * an object of a type other than the four basic types is converted to a
 * boolean in a way that is dependent on that type
 * </p></li></ul>
 * </blockquote>
 *
 * @author bob mcwhirter (bob @ werken.com)
 * @see <a href="http://www.w3.org/TR/xpath#function-boolean">Section 4.3 of the XPath Specification</a>
 */
public class BooleanFunction implements Function
{


    /**
     * Create a new <code>BooleanFunction</code> object.
     */
    public BooleanFunction() {}

    /** Convert the argument to a <code>Boolean</code>
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list with exactly one item which will be converted to a
     *     <code>Boolean</code>
     *
     * @return the result of evaluating the function;
     *     <code>Boolean.TRUE</code> or <code>Boolean.FALSE</code>
     *
     * @throws FunctionCallException if <code>args</code> has more or less than one item
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if ( args.size() == 1 )
        {
            return evaluate( args.get(0), context.getNavigator() );
        }

        throw new FunctionCallException("boolean() requires one argument");
    }

    /**
     * <p>Convert the argument <code>obj</code> to a <code>Boolean</code>
     * according to the following rules:</p>
     *
     * <ul>
     * <li>Lists are false if they're empty; true if they're not.</li>
     * <li>Booleans are false if they're false; true if they're true.</li>
     * <li>Strings are false if they're empty; true if they're not.</li>
     * <li>Numbers are false if they're 0 or NaN; true if they're not.</li>
     * <li>All other objects are true.</li>
     * </ul>
     *
     * @param obj the object to convert to a boolean
     * @param nav ignored
     *
     * @return <code>Boolean.TRUE</code> or <code>Boolean.FALSE</code>
     */
    public static Boolean evaluate(Object obj, Navigator nav)
    {
        if ( obj instanceof List )
        {
            List list = (List) obj;

            // if it's an empty list, then we have a null node-set -> false
            if (list.size() == 0)
            {
                return Boolean.FALSE;
            }

            // otherwise, unwrap the list and check the primitive
            obj = list.get(0);
        }

        // now check for primitive types
        // otherwise a non-empty node-set is true

        // if it's a Boolean, let it decide
        if ( obj instanceof Boolean )
        {
            return (Boolean) obj;
        }
        // if it's a Number, != 0 -> true
        else if ( obj instanceof Number )
        {
            double d = ((Number) obj).doubleValue();
            if ( d == 0 || Double.isNaN(d) )
            {
                return Boolean.FALSE;
            }
            return Boolean.TRUE;
        }
        // if it's a String, "" -> false
        else if ( obj instanceof String )
        {
            return ( ((String)obj).length() > 0
                     ? Boolean.TRUE
                     : Boolean.FALSE );
        }
        else
        {
            // assume it's a node so that this node-set is non-empty
            // and so it's true
            return ( obj != null ) ? Boolean.TRUE : Boolean.FALSE;
        }

    }
}
