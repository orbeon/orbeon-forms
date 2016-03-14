/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/RoundFunction.java,v 1.12 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.12 $
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
 * $Id: RoundFunction.java,v 1.12 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.List;

/**
 * <p><b>4.4</b> <code><i>number</i> round(<i>number</i>)</code> </p>
 *
 *
 * <blockquote src="http://www.w3.org/TR/xpath">
 * The <b>round</b> function returns the number that is closest to the argument and that is an integer.
 * If there are two such numbers, then the one that is closest to positive infinity is returned.
 * If the argument is NaN, then NaN is returned. If the argument is positive infinity, then positive infinity is returned.
 * If the argument is negative infinity, then negative infinity is returned.
 * If the argument is positive zero, then positive zero is returned.
 * If the argument is negative zero, then negative zero is returned.
 * If the argument is less than zero, but greater than or equal to -0.5, then negative zero is returned.
 * </blockquote>
 *
 * @author bob mcwhirter (bob @ werken.com)
 *
 * @see <a href="http://www.w3.org/TR/xpath#function-round" target="_top">Section 4.4 of the XPath Specification</a>
 */
public class RoundFunction implements Function
{

    /**
     * Create a new <code>RoundFunction</code> object.
     */
    public RoundFunction() {}


    /** Returns the nearest integer to the number.
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list with exactly one item which will be converted to a
     *     <code>Double</code> as if by the XPath <code>number()</code> function
     *
     * @return a <code>Double</code> containing the integer nearest to
     *     <code>args.get(0)</code>
     *
     * @throws FunctionCallException if <code>args</code> has more or less than one item
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if (args.size() == 1)
        {
            return evaluate( args.get(0),
                             context.getNavigator() );
        }

        throw new FunctionCallException( "round() requires one argument." );
    }

    /**
     * Returns the integer nearest to the argument.
     * If necessary, the argument is first converted to a <code>Double</code>
     * as if by the XPath <code>number()</code> function.
     *
     * @param obj the object to be rounded
     * @param nav ignored
     *
     * @return the integer nearest to <code>obj</code>
     */
    public static Double evaluate(Object obj,
                                  Navigator nav)
    {
        Double d  = NumberFunction.evaluate( obj,
                                            nav );

        if (d.isNaN() || d.isInfinite())
        {
            return d;
        }

        double value = d.doubleValue();
        return new Double( Math.round( value ) );
    }
}
