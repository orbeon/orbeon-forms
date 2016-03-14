/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/FunctionContext.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
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
 * $Id: FunctionContext.java,v 1.10 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen;

/** Implemented by classes that know how to resolve XPath function names and
 *  namespaces to implementations of these functions.
 *
 *  <p>
 *  By using a custom <code>FunctionContext</code>, new or different
 *  functions may be installed and available to XPath expression writers.
 *  </p>
 *
 *  @see XPathFunctionContext
 *
 *  @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 */
public interface FunctionContext
{
    /** An implementation should return a <code>Function</code> implementation object
     *  based on the namespace URI and local name of the function-call
     *  expression.
     *
     *  <p>
     *  It must not use the prefix parameter to select an implementation,
     *  because a prefix could be bound to any namespace; the prefix parameter
     *  could be used in debugging output or other generated information.
     *  The prefix may otherwise be completely ignored.
     *  </p>
     *
     *  @param namespaceURI  the namespace URI to which the prefix parameter
     *                       is bound in the XPath expression. If the function
     *                       call expression had no prefix, the namespace URI
     *                       is <code>null</code>.
     *  @param prefix        the prefix that was used in the function call
     *                       expression
     *  @param localName     the local name of the function-call expression.
     *                       If there is no prefix, then this is the whole
     *                       name of the function.
     *
     *  @return  a Function implementation object.
     *  @throws UnresolvableException  when the function cannot be resolved
     */
    Function getFunction( String namespaceURI,
                          String prefix,
                          String localName ) throws UnresolvableException;
}
