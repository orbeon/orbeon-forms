/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/SimpleVariableContext.java,v 1.19 2006/05/03 16:07:03 elharo Exp $
 * $Revision: 1.19 $
 * $Date: 2006/05/03 16:07:03 $
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
 * $Id: SimpleVariableContext.java,v 1.19 2006/05/03 16:07:03 elharo Exp $
 */


package org.orbeon.jaxen;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** Simple default implementation for <code>VariableContext</code>.
 *
 *  <p>
 *  This is a simple table-based key-lookup implementation
 *  for <code>VariableContext</code> which can be programmatically
 *  extended by setting additional variables.
 *  </p>
 *
 *  @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 */
public class SimpleVariableContext implements VariableContext, Serializable
{
    /**
     *
     */
    private static final long serialVersionUID = 961322093794516518L;
    /** Table of variable bindings. */
    private Map variables;

    /** Construct.
     *
     *  <p>
     *  Create a new empty variable context.
     *  </p>
     */
    public SimpleVariableContext()
    {
        variables = new HashMap();
    }

    /** Set the value associated with a variable.
     *
     *  <p>
     *  This method sets a variable that is
     *  associated with a particular namespace.
     *  These variables appear such as <code>$prefix:foo</code>
     *  in an XPath expression.  Prefix to URI resolution
     *  is the responsibility of a <code>NamespaceContext</code>.
     *  Variables within a <code>VariableContext</code> are
     *  referred to purely based upon their namespace URI,
     *  if any.
     *  </p>
     *
     *  @param namespaceURI the namespace URI of the variable
     *  @param localName the local name of the variable
     *  @param value The value to be bound to the variable
     */
    public void setVariableValue( String namespaceURI,
                                  String localName,
                                  Object value )
    {
        this.variables.put( new QualifiedName(namespaceURI, localName),
                            value );
    }

    /** Set the value associated with a variable.
     *
     *  <p>
     *  This method sets a variable that is <strong>not</strong>
     *  associated with any particular namespace.
     *  These variables look like <code>$foo</code>
     *  in an XPath expression.
     *  </p>
     *
     *  @param localName the local name of the variable
     *  @param value the value to be bound to the variable
     */
    public void setVariableValue( String localName,
                                  Object value )
    {
        this.variables.put( new QualifiedName(null, localName), value );
    }

    public Object getVariableValue( String namespaceURI,
                                    String prefix,
                                    String localName )
        throws UnresolvableException
    {
        QualifiedName key = new QualifiedName( namespaceURI, localName );

        if ( this.variables.containsKey(key) )
        {
            return this.variables.get( key );
        }
        else
        {
            throw new UnresolvableException( "Variable " + key.getClarkForm() );
        }
    }
}
