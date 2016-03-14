/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/util/LinkedIterator.java,v 1.7 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.7 $
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
 * $Id: LinkedIterator.java,v 1.7 2006/02/05 21:47:40 elharo Exp $
 */


package org.orbeon.jaxen.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * @deprecated elharo this class is undocumented and untested.
 *     It will be removed in a future release.
 */
public class LinkedIterator implements Iterator
{
    private List iterators;
    private int  cur;

    public LinkedIterator()
    {
        this.iterators = new ArrayList();
        this.cur       = 0;
    }

    public void addIterator(Iterator i)
    {
        this.iterators.add( i );
    }

    public boolean hasNext()
    {
        boolean has = false;

        if ( this.cur < this.iterators.size() )
        {
            has = ((Iterator)this.iterators.get( this.cur )).hasNext();

            if ( ! has
                 &&
                 this.cur < this.iterators.size() )
            {
                ++this.cur;
                has = hasNext();
            }
        }
        else
        {
            has = false;
        }

        return has;
    }

    public Object next()
    {
        if ( ! hasNext() )
        {
            throw new NoSuchElementException();
        }

        return ((Iterator)this.iterators.get( this.cur )).next();
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
