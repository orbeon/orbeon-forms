/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/expr/DefaultStep.java,v 1.21 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.21 $
 * $Date: 2006/02/05 21:47:40 $
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
 * $Id: DefaultStep.java,v 1.21 2006/02/05 21:47:40 elharo Exp $
 */
package org.orbeon.jaxen.expr;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.ContextSupport;
import org.orbeon.jaxen.JaxenException;
import org.orbeon.jaxen.UnsupportedAxisException;
import org.orbeon.jaxen.expr.iter.IterableAxis;
import org.orbeon.jaxen.saxpath.Axis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @deprecated this class will become non-public in the future;
 *     use the interface instead
 */
public abstract class DefaultStep implements Step
{
    private IterableAxis axis;
    private PredicateSet predicates;

    public DefaultStep(IterableAxis axis, PredicateSet predicates)
    {
        this.axis = axis;
        this.predicates = predicates;
    }

    public void addPredicate(Predicate predicate)
    {
        this.predicates.addPredicate(predicate);
    }

    public List getPredicates()
    {
        return this.predicates.getPredicates();
    }

    public PredicateSet getPredicateSet()
    {
        return this.predicates;
    }

    public int getAxis()
    {
        return this.axis.value();
    }

    public IterableAxis getIterableAxis()
    {
        return this.axis;
    }

    public String getAxisName()
    {
        return Axis.lookup(getAxis());
    }

    public String getText()
    {
        return this.predicates.getText();
    }

    public String toString()
    {
        return getIterableAxis() + " " + super.toString();
    }

    public void simplify()
    {
        this.predicates.simplify();
    }

    public Iterator axisIterator(Object contextNode, ContextSupport support)
        throws UnsupportedAxisException
    {
        return getIterableAxis().iterator(contextNode, support);
    }

    public List evaluate(final Context context) throws JaxenException
    {
        final List contextNodeSet  = context.getNodeSet();
        final IdentitySet unique = new IdentitySet();
        final int contextSize = contextNodeSet.size();

        // ???? try linked lists instead?
        // ???? initial size for these?
        final ArrayList interimSet = new ArrayList();
        final ArrayList newNodeSet = new ArrayList();
        final ContextSupport support = context.getContextSupport();

        // ???? use iterator instead
        for ( int i = 0 ; i < contextSize ; ++i )
        {
            Object eachContextNode = contextNodeSet.get( i );


                /* See jaxen-106. Might be able to optimize this by doing
                 * specific matching for individual axes. For instance on namespace axis
                 * we should only get namespace nodes and on attribute axes we only get
                 * attribute nodes. Self and parent axes have single members.
                 * Children, descendant, ancestor, and sibling axes never
                 * see any attributes or namespaces
                 */
            Iterator axisNodeIter = axis.iterator(eachContextNode, support);
            while ( axisNodeIter.hasNext() )
            {
                Object eachAxisNode = axisNodeIter.next();
                if ( ! unique.contains( eachAxisNode ) )
                {
                    if ( matches( eachAxisNode, support ) )
                    {
                        unique.add( eachAxisNode );
                        interimSet.add( eachAxisNode );
                    }
                }
            }
            newNodeSet.addAll(getPredicateSet().evaluatePredicates(
                              interimSet, support ));
            interimSet.clear();
        }
        return newNodeSet;
    }

}
