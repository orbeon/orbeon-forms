/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/util/StackedIterator.java,v 1.12 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.12 $
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
 * $Id: StackedIterator.java,v 1.12 2006/02/05 21:47:40 elharo Exp $
 */



package org.orbeon.jaxen.util;

import org.orbeon.jaxen.Navigator;

import java.util.*;

/**
 * @deprecated this iterator is no longer used to implement any of the Jaxen axes. If you have implemented
 * a navigator-specific axis based on this class, take a look at the DescendantAxisIterator for ideas
 * on how to remove that dependency.
 */
public abstract class StackedIterator implements Iterator
{

    private LinkedList iteratorStack;
    private Navigator  navigator;

    private Set        created;

    public StackedIterator(Object contextNode,
                           Navigator navigator)
    {
        this.iteratorStack = new LinkedList();
        this.created       = new HashSet();

        init( contextNode,
              navigator );
    }

    protected StackedIterator()
    {
        this.iteratorStack = new LinkedList();
        this.created       = new HashSet();
    }

    protected void init(Object contextNode,
                        Navigator navigator)
    {
        this.navigator     = navigator;

        //pushIterator( internalCreateIterator( contextNode ) );
    }

    protected Iterator internalCreateIterator(Object contextNode)
    {
        if ( this.created.contains( contextNode ) )
        {
            return null;
        }

        this.created.add( contextNode );

        return createIterator( contextNode );
    }

    public boolean hasNext()
    {
        Iterator curIter = currentIterator();

        if ( curIter == null )
        {
            return false;
        }

        return curIter.hasNext();
    }

    public Object next() throws NoSuchElementException
    {
        if ( ! hasNext() )
        {
            throw new NoSuchElementException();
        }

        Iterator curIter = currentIterator();
        Object   object  = curIter.next();

        pushIterator( internalCreateIterator( object ) );

        return object;
    }

    public void remove() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    abstract protected Iterator createIterator(Object contextNode);

    protected void pushIterator(Iterator iter)
    {
        if ( iter != null )
        {
            this.iteratorStack.addFirst(iter); //addLast( iter );
        }
    }

    private Iterator currentIterator()
    {
        while ( iteratorStack.size() > 0 )
        {
            Iterator curIter = (Iterator) iteratorStack.getFirst();

            if ( curIter.hasNext() )
            {
                return curIter;
            }

            iteratorStack.removeFirst();
        }

        return null;
    }

    protected Navigator getNavigator()
    {
        return this.navigator;
    }
}
