/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/NamespaceContext.java,v 1.9 2006/02/05 21:47:41 elharo Exp $
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
 * $Id: NamespaceContext.java,v 1.9 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen;

/** Resolves namespace prefixes to namespace URIs.
 *
 *  <p>
 *  The prefixes used within an XPath expression are
 *  independent of those used within any target document.
 *  When evaluating an XPath against a document, only
 *  the resolved namespace URIs are compared, not their
 *  prefixes.
 *  </p>
 *
 *  <p>
 *  A <code>NamespaceContext</code> is responsible for
 *  translating prefixes as they appear in XPath expressions
 *  into URIs for comparison.  A document's prefixes are
 *  resolved internal to the document based upon its own
 *  namespace nodes.
 *  </p>
 *
 *  @see BaseXPath
 *  @see Navigator#getElementNamespaceUri
 *  @see Navigator#getAttributeNamespaceUri
 *
 *  @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 */
public interface NamespaceContext
{
    /** Translate the provided namespace prefix into
     *  the matching bound namespace URI.
     *
     *  <p>
     *  In XPath, there is no such thing as a 'default namespace'.
     *  The empty prefix <strong>always</strong> resolves to the empty
     *  namespace URI. This method should return null for the
     *  empty prefix.
     *  Similarly, the prefix "xml" always resolves to
     *  the URI "http://www.w3.org/XML/1998/namespace".
     *  </p>
     *
     *  @param prefix the namespace prefix to resolve
     *
     *  @return the namespace URI bound to the prefix; or null if there
     *     is no such namespace
     */
    String translateNamespacePrefixToUri(String prefix);
}
