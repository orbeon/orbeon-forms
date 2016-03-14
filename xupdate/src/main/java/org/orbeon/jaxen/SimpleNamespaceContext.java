/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/SimpleNamespaceContext.java,v 1.18 2006/05/03 16:07:03 elharo Exp $
 * $Revision: 1.18 $
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
 * $Id: SimpleNamespaceContext.java,v 1.18 2006/05/03 16:07:03 elharo Exp $
 */


package org.orbeon.jaxen;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *  Provides mappings from namespace prefix to namespace URI to the XPath
 *  engine.
 */
public class SimpleNamespaceContext implements NamespaceContext, Serializable
{

    /**
     *
     */
    private static final long serialVersionUID = -808928409643497762L;
    // XXX should this prebind the xml prefix?
    private Map namespaces;

    /**
     * Creates a new empty namespace context.
     */
    public SimpleNamespaceContext()
    {
        this.namespaces = new HashMap();
    }

    /**
     * Creates a new namespace context pre-populated with the specified bindings.
     *
     * @param namespaces the initial namespace bindings in scope. The keys in this
     *     must be strings containing the prefixes and the values are strings
     *     containing the namespace URIs.
     *
     * @throws NullPointerException if the argument is null
     * @throws ClassCastException if any keys or values in the map are not strings
     */
    public SimpleNamespaceContext(Map namespaces)
    {
        Iterator entries = namespaces.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            if (! (entry.getKey() instanceof String)
              || ! (entry.getValue() instanceof String)) {
                throw new ClassCastException("Non-string namespace binding");
            }
        }
        this.namespaces = new HashMap(namespaces);
    }

    /**
     *  Adds all the namespace declarations that are in scope on the given
     *  element. In the case of an XSLT stylesheet, this would be the element
     *  that has the XPath expression in one of its attributes; e.g.
     *  <code>&lt;xsl:if test="condition/xpath/expression"&gt;</code>.
     *
     *  @param nav  the navigator for use in conjunction with
     *              <code>element</code>
     *  @param element the element to copy the namespaces from
     *  @throws UnsupportedAxisException if the navigator does not support the
     *     namespace axis
     */
    public void addElementNamespaces( Navigator nav, Object element )
        throws UnsupportedAxisException
    {
        Iterator namespaceAxis = nav.getNamespaceAxisIterator( element );

        while ( namespaceAxis.hasNext() ) {
            Object namespace = namespaceAxis.next();
            String prefix = nav.getNamespacePrefix( namespace );
            String uri = nav.getNamespaceStringValue( namespace );
            if ( translateNamespacePrefixToUri(prefix) == null ) {
                addNamespace( prefix, uri );
            }
        }
    }

    // ???? What if prefix or URI is null, or both?
    /**
     * Binds a prefix to a URI in this context.
     *
     * @param prefix the namespace prefix
     * @param URI    the namespace URI
     */
    public void addNamespace(String prefix, String URI)
    {
        this.namespaces.put( prefix, URI );
    }

    public String translateNamespacePrefixToUri(String prefix)
    {
        if ( this.namespaces.containsKey( prefix ) )
        {
            return (String) this.namespaces.get( prefix );
        }

        return null;
    }
}
