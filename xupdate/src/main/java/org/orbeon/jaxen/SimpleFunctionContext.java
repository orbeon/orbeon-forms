/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/SimpleFunctionContext.java,v 1.14 2006/02/05 21:47:41 elharo Exp $
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
 * $Id: SimpleFunctionContext.java,v 1.14 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen;

import java.util.HashMap;

/** Simple default implementation of <code>FunctionContext</code>.
 *
 *  <p>
 *  This is a simple table-based key-lookup implementation
 *  for <code>FunctionContext</code> which can be programmatically
 *  extended by registering additional functions.
 *  </p>
 *
 *  @see XPathFunctionContext
 *
 *  @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 */
public class SimpleFunctionContext implements FunctionContext
{
    /** Table of functions. */
    private HashMap functions;

    /**
     *  <p>
     *  Construct an empty function context.
     *  </p>
     */
    public SimpleFunctionContext()
    {
        this.functions = new HashMap();
    }

    /** Register a new function.
     *
     *  <p>
     *  By registering a new function, any XPath expression
     *  that utilizes this <code>FunctionContext</code> may
     *  refer to and use the new function.
     *  </p>
     *
     *  <p>
     *  Functions may exist either in a namespace or not.
     *  Namespace prefix-to-URI resolution is the responsibility
     *  of a {@link NamespaceContext}.  Within this <code>FunctionContext</code>
     *  functions are only referenced using the URI, <strong>not</strong>
     *  the prefix.
     *  </p>
     *
     *  <p>
     *  The namespace URI of a function may be <code>null</code>
     *  to indicate that it exists without a namespace.
     *  </p>
     *
     *  @param namespaceURI the namespace URI of the function to
     *         be registered with this context
     *  @param localName the non-prefixed local portion of the
     *         function to be registered with this context
     *  @param function a {@link Function} implementation object
     *         to be used when evaluating the function
     */
    public void registerFunction(String namespaceURI,
                                 String localName,
                                 Function function )
    {
        this.functions.put( new QualifiedName(namespaceURI, localName),
                            function );
    }

    public Function getFunction(String namespaceURI,
                                String prefix,
                                String localName )
        throws UnresolvableException
    {
        QualifiedName key = new QualifiedName( namespaceURI, localName );

        if ( this.functions.containsKey(key) ) {
            return (Function) this.functions.get( key );
        }
        else {
            throw new UnresolvableException( "No Such Function " + key.getClarkForm() );
        }
    }
}
