/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/NumberFunction.java,v 1.18 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.18 $
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
 * $Id: NumberFunction.java,v 1.18 2006/02/05 21:47:41 elharo Exp $
 */

package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.Iterator;
import java.util.List;

/**
 * <p>
 * <b>4.4</b> <code><i>number</i> number(<i>object</i>)</code>
 *
 *
 * <blockquote src="http://www.w3.org/TR/xpath#function-number">
 * <p>
 * The <b>number</b> function converts
 * its argument to a number as follows:
 * </p>
 *
 * <ul>
 *
 * <li>
 * <p>
 * a string that consists of optional whitespace followed by an optional
 * minus sign followed by a <a href="#NT-Number">Number</a> followed by
 * whitespace is converted to the IEEE 754 number that is nearest
 * (according to the IEEE 754 round-to-nearest rule) to the mathematical
 * value represented by the string; any other string is converted to NaN
 * </p>
 * </li>
 *
 * <li>
 * <p>
 * boolean true is converted to 1; boolean false is converted to 0
 * </p>
 * </li>
 *
 * <li>
 *
 * <p>
 * a node-set is first converted to a string as if by a call to the <b><a
 * href="http://www.w3.org/TR/xpath#function-string" target="_top">string</a></b> function and then converted
 * in the same way as a string argument
 * </p>
 *
 * </li>
 *
 * <li>
 * <p>
 * an object of a type other than the four basic types is converted to a
 * number in a way that is dependent on that type
 * </p>
 * </li>
 *
 * </ul>
 *
 * <p>
 * If the argument is omitted, it defaults to a node-set with the
 * context node as its only member.
 * </p>
 *
 * <blockquote> <b>NOTE: </b>The <b>number</b>
 * function should not be used for conversion of numeric data occurring
 * in an element in an XML document unless the element is of a type that
 * represents numeric data in a language-neutral format (which would
 * typically be transformed into a language-specific format for
 * presentation to a user). In addition, the <b>number</b> function cannot be used
 * unless the language-neutral format used by the element is consistent
 * with the XPath syntax for a <a href="http://www.w3.org/TR/xpath#NT-Number">Number</a>.</blockquote>
 *
 * </blockquote>
 *
 * @author bob mcwhirter (bob @ werken.com)
 *
 * @see <a href="http://www.w3.org/TR/xpath#function-number"
 *      target="_top">Section 4.4 of the XPath Specification</a>
 */
public class NumberFunction implements Function
{

    private final static Double NaN = new Double( Double.NaN );


    /**
     * Create a new <code>NumberFunction</code> object.
     */
    public NumberFunction() {}

    /**
     * Returns the number value of <code>args.get(0)</code>,
     * or the number value of the context node if <code>args</code>
     * is empty.
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list containing the single item to be converted to a
     *     <code>Double</code>
     *
     * @return a <code>Double</code>
     *
     * @throws FunctionCallException if <code>args</code> has more than one item
     */
    public Object call(Context context, List args) throws FunctionCallException
    {
        if (args.size() == 1)
        {
            return evaluate( args.get(0), context.getNavigator() );
        }
        else if (args.size() == 0)
        {
            return evaluate( context.getNodeSet(), context.getNavigator() );
        }

        throw new FunctionCallException( "number() takes at most one argument." );
    }

    /**
     * Returns the number value of <code>obj</code>.
     *
     * @param obj the object to be converted to a number
     * @param nav the <code>Navigator</code> used to calculate the string-value
     *     of node-sets
     *
     * @return a <code>Double</code>
     */
    public static Double evaluate(Object obj, Navigator nav)
    {
        if( obj instanceof Double )
        {
            return (Double) obj;
        }
        else if ( obj instanceof String )
        {
            String str = (String) obj;
            try
            {
                Double doubleValue = new Double( str );
                return doubleValue;
            }
            catch (NumberFormatException e)
            {
                return NaN;
            }
        }
        else if ( obj instanceof List || obj instanceof Iterator )
        {
          return evaluate( StringFunction.evaluate( obj, nav ), nav );
        }
        else if ( nav.isElement( obj ) || nav.isAttribute( obj ) )
        {
            return evaluate( StringFunction.evaluate( obj, nav ), nav );
        }
        else if ( obj instanceof Boolean )
          {
          if ( obj == Boolean.TRUE )
          {
              return new Double( 1 );
          }
          else
          {
              return new Double( 0 );
          }
        }
        return NaN;
    }

  /**
   * Determines whether the argument is not a number (NaN) as defined
   * by IEEE 754.
   *
   * @param val the double to test
   * @return true if the value is NaN, false otherwise
   */
    public static boolean isNaN( double val )
    {
        return Double.isNaN(val);
    }

  /**
   * Determines whether the argument is not a number (NaN) as defined
   * by IEEE 754.
   *
   * @param val the <code>Double</code> to test
   * @return true if the value is NaN, false otherwise
   */
    public static boolean isNaN( Double val )
    {
        return val.equals( NaN );
    }

}
