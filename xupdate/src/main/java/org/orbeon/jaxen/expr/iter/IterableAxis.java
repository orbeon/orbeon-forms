/*
 $Id: IterableAxis.java,v 1.9 2006/02/05 21:47:42 elharo Exp $

 Copyright 2003 The Werken Company. All Rights Reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the name of the Jaxen Project nor the names of its
    contributors may be used to endorse or promote products derived
    from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package org.orbeon.jaxen.expr.iter;

import org.orbeon.jaxen.ContextSupport;
import org.orbeon.jaxen.UnsupportedAxisException;

import java.io.Serializable;
import java.util.Iterator;

/**
 * Provide access to the XPath axes.
 *
 * @author Bob McWhirter
 * @author James Strachan
 * @author Stephen Colebourne
 */
public abstract class IterableAxis implements Serializable {

    /** The axis type */
    private int value;

    /**
     * Constructor.
     *
     * @param axisValue
     */
    public IterableAxis(int axisValue) {
        this.value = axisValue;
    }

    /**
     * Gets the axis value.
     *
     * @return the axis value
     */
    public int value() {
        return this.value;
    }

    /**
     * Gets the iterator for a specific XPath axis.
     *
     * @param contextNode  the current context node to work from
     * @param support  the additional context information
     * @return an iterator for the axis
     * @throws UnsupportedAxisException
     */
    public abstract Iterator iterator(Object contextNode, ContextSupport support) throws UnsupportedAxisException;

    /**
     * Gets the iterator for a specific XPath axis that supports named access.
     *
     * @param contextNode  the current context node to work from
     * @param support  the additional context information
     * @param localName  the local name of the nodes to return
     * @param namespacePrefix  the prefix of the namespace of the nodes to return
     * @param namespaceURI  the URI of the namespace of the nodes to return
     */
    public Iterator namedAccessIterator(
        Object contextNode,
        ContextSupport support,
        String localName,
        String namespacePrefix,
        String namespaceURI)
            throws UnsupportedAxisException {

        throw new UnsupportedOperationException("Named access unsupported");
    }

    /**
     * Does this axis support named access?
     *
     * @param support  the additional context information
     * @return true if named access supported. If not iterator() will be used
     */
    public boolean supportsNamedAccess(ContextSupport support) {
        return false;
    }

}
