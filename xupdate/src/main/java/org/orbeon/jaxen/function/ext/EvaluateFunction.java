/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/ext/EvaluateFunction.java,v 1.10 2006/02/05 21:47:42 elharo Exp $
 * $Revision: 1.10 $
 * $Date: 2006/02/05 21:47:42 $
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
 * $Id: EvaluateFunction.java,v 1.10 2006/02/05 21:47:42 elharo Exp $
 */

package org.orbeon.jaxen.function.ext;

import org.orbeon.jaxen.*;
import org.orbeon.jaxen.function.StringFunction;

import java.util.Collections;
import java.util.List;

/**
 * <code><i>node-set</i> evaluate(<i>string</i>)</code>
 *
 * @author Erwin Bolwidt (ejb @ klomp.org)
 */
public class EvaluateFunction implements Function
{
    public Object call( Context context, List args )
        throws FunctionCallException
    {
        if ( args.size() == 1 ) {
            return evaluate( context, args.get(0));
        }

        throw new FunctionCallException( "evaluate() requires one argument" );
    }

    public static List evaluate (Context context, Object arg)
        throws FunctionCallException
    {
        List contextNodes = context.getNodeSet();

        if (contextNodes.size() == 0)
            return Collections.EMPTY_LIST;

        Navigator nav = context.getNavigator();

        String xpathString;
        if ( arg instanceof String )
            xpathString = (String)arg;
        else
            xpathString = StringFunction.evaluate(arg, nav);

        try {
            XPath xpath = nav.parseXPath(xpathString);
            ContextSupport support = context.getContextSupport();
            xpath.setVariableContext( support.getVariableContext() );
            xpath.setFunctionContext( support.getFunctionContext() );
            xpath.setNamespaceContext( support.getNamespaceContext() );
            return xpath.selectNodes( context.duplicate() );
        }
        catch ( org.orbeon.jaxen.saxpath.SAXPathException e ) {
            throw new FunctionCallException(e.toString());
        }
    }
}

