/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/VariableContext.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
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
 * $Id: VariableContext.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen;

/** Resolves variable bindings within an XPath expression.
 *
 *  <p>
 *  Variables within an XPath expression are denoted using
 *  notation such as <code>$varName</code> or
 *  <code>$nsPrefix:varName</code>, and may
 *  refer to a <code>Boolean</code>, <code>Double</code>, <code>String</code>,
 *  node-set (<code>List</code>) or individual XML node.
 *  </p>
 *
 *  <p>
 *  When a variable is bound to a node-set, the
 *  actual Java object returned should be a <code>java.util.List</code>
 *  containing XML nodes from the object-model (e.g. dom4j, JDOM, DOM, etc.)
 *  being used with the XPath.
 *  </p>
 *
 *  <p>
 *  A variable may validly be assigned the <code>null</code> value,
 *  but an unbound variable (one that this context does not know about)
 *  should cause an {@link UnresolvableException} to be thrown.
 *  </p>
 *
 *  @see SimpleVariableContext
 *  @see NamespaceContext
 *
 *  @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 *  @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 */
public interface VariableContext
{
    /** An implementation should return the value of an XPath variable
     *  based on the namespace URI and local name of the variable-reference
     *  expression.
     *
     *  <p>
     *  It must not use the prefix parameter to select a variable,
     *  because a prefix could be bound to any namespace; the prefix parameter
     *  could be used in debugging output or other generated information.
     *  The prefix may otherwise be ignored.
     *  </p>
     *
     *  @param namespaceURI  the namespace URI to which the prefix parameter
     *                       is bound in the XPath expression. If the variable
     *                       reference expression had no prefix, the namespace
     *                       URI is <code>null</code>.
     *  @param prefix        the prefix that was used in the variable reference
     *                       expression; this value is ignored and has no effect
     *  @param localName     the local name of the variable-reference
     *                       expression. If there is no prefix, then this is
     *                       the whole name of the variable.
     *
     *  @return  the variable's value (which can be <code>null</code>)
     *  @throws UnresolvableException  when the variable cannot be resolved
     */
    public Object getVariableValue( String namespaceURI,
                                    String prefix,
                                    String localName )
        throws UnresolvableException;
}
