/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/pattern/NamespaceTest.java,v 1.6 2006/02/05 21:47:42 elharo Exp $
 * $Revision: 1.6 $
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
 * $Id: NamespaceTest.java,v 1.6 2006/02/05 21:47:42 elharo Exp $
 */

package org.orbeon.jaxen.pattern;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Navigator;

/** <p><code>NamespaceTest</code> tests for a given namespace URI.</p>
  *
  * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
  * @version $Revision: 1.6 $
  */
public class NamespaceTest extends NodeTest {

    /** The prefix to match against */
    private String prefix;

    /** The type of node to match - either attribute or element */
    private short nodeType;

    public NamespaceTest(String prefix, short nodeType)
    {
        if ( prefix == null )
        {
            prefix = "";
        }
        this.prefix = prefix;
        this.nodeType = nodeType;
    }

    /** @return true if the pattern matches the given node
      */
    public boolean matches( Object node, Context context )
    {
        Navigator navigator = context.getNavigator();
        String uri = getURI( node, context );

        if ( nodeType == Pattern.ELEMENT_NODE )
        {
            return navigator.isElement( node )
                && uri.equals( navigator.getElementNamespaceUri( node ) );
        }
        else if ( nodeType == Pattern.ATTRIBUTE_NODE )
        {
            return navigator.isAttribute( node )
                && uri.equals( navigator.getAttributeNamespaceUri( node ) );
        }
        return false;
    }

    public double getPriority()
    {
        return -0.25;
    }


    public short getMatchType()
    {
        return nodeType;
    }

    public String getText()
    {
        return prefix + ":";
    }

    public String toString()
    {
        return super.toString() + "[ prefix: " + prefix + " type: " + nodeType + " ]";
    }

    /** Returns the URI of the current prefix or "" if no URI can be found
     */
    protected String getURI(Object node, Context context)
    {
        String uri = context.getNavigator().translateNamespacePrefixToUri( prefix, node );
        if ( uri == null )
        {
            uri = context.getContextSupport().translateNamespacePrefixToUri( prefix );
        }
        if ( uri == null )
        {
            uri = "";
        }
        return uri;
    }
}
