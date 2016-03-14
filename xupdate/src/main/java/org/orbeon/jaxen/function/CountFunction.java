/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/CountFunction.java,v 1.14 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.14 $
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
 * $Id: CountFunction.java,v 1.14 2006/02/05 21:47:41 elharo Exp $
 */

package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;

import java.util.List;

/**
 *  <p><b>4.1</b> <code><i>number</i> count(<i>node-set</i>)</code></p>
 *
 * <blockquote src="http://www.w3.org/TR/xpath#function-count">
 * The <b>count</b> function returns the number of nodes in the argument node-set.
 * </blockquote>
 * @author bob mcwhirter (bob @ werken.com)
 * @see <a href="http://www.w3.org/TR/xpath#function-count">Section 4.1 of the XPath Specification</a>
 */
public class CountFunction implements Function
{

    /**
     * Create a new <code>CountFunction</code> object.
     */
    public CountFunction() {}

    /**
     * <p>
     * Returns the number of nodes in the specified node-set.
     * </p>
     * @param context ignored
     * @param args the function arguments
     *
     * @return a <code>Double</code> giving the integral number of items in the first argument
     *
     * @throws FunctionCallException if args does not have exactly one
     *     item; or that item is not a <code>List</code>
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if (args.size() == 1)
        {
            return evaluate( args.get(0) );
        }

        throw new FunctionCallException( "count() requires one argument." );
    }

    /**
     * <p>
     * Returns the number of nodes in the specified node-set.
     * </p>
     *
     * @param obj a <code>List</code> of nodes
     * @return the integral number of items in the list
     * @throws FunctionCallException if obj is not a <code>List</code>
     */
    public static Double evaluate(Object obj) throws FunctionCallException
    {

        if (obj instanceof List)
        {
            return new Double( ((List) obj).size() );
        }

        throw new FunctionCallException("count() function can only be used for node-sets");

    }

}
