/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/pattern/LocationPathPattern.java,v 1.15 2006/02/05 21:47:42 elharo Exp $
 * $Revision: 1.15 $
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
 * $Id: LocationPathPattern.java,v 1.15 2006/02/05 21:47:42 elharo Exp $
 */

package org.orbeon.jaxen.pattern;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.JaxenException;
import org.orbeon.jaxen.Navigator;
import org.orbeon.jaxen.expr.FilterExpr;
import org.orbeon.jaxen.util.SingletonList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** <p><code>LocationPathPattern</code> matches any node using a
  * location path such as A/B/C.
  * The parentPattern and ancestorPattern properties are used to
  * chain location path patterns together</p>
  *
  * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
  * @version $Revision: 1.15 $
  */
public class LocationPathPattern extends Pattern {

    /** The node test to perform on this step of the path */
    private NodeTest nodeTest = AnyNodeTest.getInstance();

    /** Patterns matching my parent node */
    private Pattern parentPattern;

    /** Patterns matching one of my ancestors */
    private Pattern ancestorPattern;

    /** The filters to match against */
    private List filters;

    /** Whether this lcoation path is absolute or not */
    private boolean absolute;


    public LocationPathPattern()
    {
    }

    public LocationPathPattern(NodeTest nodeTest)
    {
        this.nodeTest = nodeTest;
    }

    public Pattern simplify()
    {
        if ( parentPattern != null )
        {
            parentPattern = parentPattern.simplify();
        }
        if ( ancestorPattern != null )
        {
            ancestorPattern = ancestorPattern.simplify();
        }
        if ( filters == null )
        {
            if ( parentPattern == null && ancestorPattern == null )
            {
                return nodeTest;
            }
            if ( parentPattern != null && ancestorPattern == null )
            {
                if ( nodeTest instanceof AnyNodeTest )
                {
                    return parentPattern;
                }
            }
        }
        return this;
    }

    /** Adds a filter to this pattern
     */
    public void addFilter(FilterExpr filter)
    {
        if ( filters == null )
        {
            filters = new ArrayList();
        }
        filters.add( filter );
    }

    /** Adds a pattern for the parent of the current
     * context node used in this pattern.
     */
    public void setParentPattern(Pattern parentPattern)
    {
        this.parentPattern = parentPattern;
    }

    /** Adds a pattern for an ancestor of the current
     * context node used in this pattern.
     */
    public void setAncestorPattern(Pattern ancestorPattern)
    {
        this.ancestorPattern = ancestorPattern;
    }

    /** Allows the NodeTest to be set
     */
    public void setNodeTest(NodeTest nodeTest) throws JaxenException
    {
        if ( this.nodeTest instanceof AnyNodeTest )
        {
            this.nodeTest = nodeTest;
        }
        else
        {
            throw new JaxenException( "Attempt to overwrite nodeTest: " + this.nodeTest + " with: " + nodeTest );
        }
    }

    /** @return true if the pattern matches the given node
      */
    public boolean matches( Object node, Context context ) throws JaxenException
    {
        Navigator navigator = context.getNavigator();

/*
        if ( isAbsolute() )
        {
            node = navigator.getDocumentNode( node );
        }
*/
        if (! nodeTest.matches(node, context) )
        {
            return false;
        }

        if (parentPattern != null)
        {
            Object parent = navigator.getParentNode( node );
            if ( parent == null )
            {
                return false;
            }
            if ( ! parentPattern.matches( parent, context ) )
            {
                return false;
            }
        }

        if (ancestorPattern != null) {
            Object ancestor = navigator.getParentNode( node );
            while (true)
            {
                if ( ancestorPattern.matches( ancestor, context ) )
                {
                    break;
                }
                if ( ancestor == null )
                {
                    return false;
                }
                if ( navigator.isDocument( ancestor ) )
                {
                    return false;
                }
                ancestor = navigator.getParentNode( ancestor );
            }
        }

        if (filters != null)
        {
            List list = new SingletonList(node);

            context.setNodeSet( list );

            // XXXX: filters aren't positional, so should we clone context?

            boolean answer = true;

            for (Iterator iter = filters.iterator(); iter.hasNext(); )
            {
                FilterExpr filter = (FilterExpr) iter.next();

                if ( ! filter.asBoolean( context ) )
                {
                    answer = false;
                    break;
                }
            }
            // restore context

            context.setNodeSet( list );

            return answer;
        }
        return true;
    }

    public double getPriority()
    {
        if ( filters != null )
        {
            return 0.5;
        }
        return nodeTest.getPriority();
    }


    public short getMatchType()
    {
        return nodeTest.getMatchType();
    }

    public String getText()
    {
        StringBuffer buffer = new StringBuffer();
        if ( absolute )
        {
            buffer.append( "/" );
        }
        if (ancestorPattern != null)
        {
            String text = ancestorPattern.getText();
            if ( text.length() > 0 )
            {
                buffer.append( text );
                buffer.append( "//" );
            }
        }
        if (parentPattern != null)
        {
            String text = parentPattern.getText();
            if ( text.length() > 0 )
            {
                buffer.append( text );
                buffer.append( "/" );
            }
        }
        buffer.append( nodeTest.getText() );

        if ( filters != null )
        {
            buffer.append( "[" );
            for (Iterator iter = filters.iterator(); iter.hasNext(); )
            {
                FilterExpr filter = (FilterExpr) iter.next();
                buffer.append( filter.getText() );
            }
            buffer.append( "]" );
        }
        return buffer.toString();
    }

    public String toString()
    {
        return super.toString() + "[ absolute: " + absolute + " parent: " + parentPattern + " ancestor: "
            + ancestorPattern + " filters: " + filters + " nodeTest: "
            + nodeTest + " ]";
    }

    public boolean isAbsolute()
    {
        return absolute;
    }

    public void setAbsolute(boolean absolute)
    {
        this.absolute = absolute;
    }

    public boolean hasAnyNodeTest()
    {
        return nodeTest instanceof AnyNodeTest;
    }

}
