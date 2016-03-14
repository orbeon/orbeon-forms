package org.orbeon.jaxen.util;

/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/util/FollowingAxisIterator.java,v 1.7 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.7 $
 * $Date: 2006/02/05 21:47:40 $
 *
 * ====================================================================
 *
 * Copyright 2000-2005 bob mcwhirter & James Strachan.
 * All rights reserved.
 *
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
 * $Id: FollowingAxisIterator.java,v 1.7 2006/02/05 21:47:40 elharo Exp $
*/

import org.orbeon.jaxen.JaxenConstants;
import org.orbeon.jaxen.JaxenRuntimeException;
import org.orbeon.jaxen.Navigator;
import org.orbeon.jaxen.UnsupportedAxisException;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class FollowingAxisIterator implements Iterator
{
    private Object contextNode;

    private Navigator navigator;

    private Iterator siblings;

    private Iterator currentSibling;

    public FollowingAxisIterator(Object contextNode,
                                 Navigator navigator) throws UnsupportedAxisException
    {
        this.contextNode = contextNode;
        this.navigator = navigator;
        this.siblings = navigator.getFollowingSiblingAxisIterator(contextNode);
        this.currentSibling = JaxenConstants.EMPTY_ITERATOR;
    }

    private boolean goForward()
    {
        while ( ! siblings.hasNext() )
        {
            if ( !goUp() )
            {
                return false;
            }
        }

        Object nextSibling = siblings.next();

        this.currentSibling = new DescendantOrSelfAxisIterator(nextSibling, navigator);

        return true;
    }

    private boolean goUp()
    {
        if ( contextNode == null
             ||
             navigator.isDocument(contextNode) )
        {
            return false;
        }

        try
        {
            contextNode = navigator.getParentNode( contextNode );

            if ( contextNode != null
                 &&
                 !navigator.isDocument(contextNode) )
            {
                siblings = navigator.getFollowingSiblingAxisIterator(contextNode);
                return true;
            }
            else
            {
                return false;
            }
        }
        catch (UnsupportedAxisException e)
        {
            throw new JaxenRuntimeException(e);
        }
    }

    public boolean hasNext()
    {
        while ( ! currentSibling.hasNext() )
        {
            if ( ! goForward() )
            {
                return false;
            }
        }

        return true;
    }

    public Object next() throws NoSuchElementException
    {
        if ( ! hasNext() )
        {
            throw new NoSuchElementException();
        }

        return currentSibling.next();
    }

    public void remove() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }
}
