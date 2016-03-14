/*
 $Id: IterableChildAxis.java,v 1.11 2006/05/03 16:07:03 elharo Exp $

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
import org.orbeon.jaxen.NamedAccessNavigator;
import org.orbeon.jaxen.UnsupportedAxisException;

import java.util.Iterator;

/**
 * Provide access to the child xpath axis.
 *
 * @author Bob McWhirter
 * @author James Strachan
 * @author Stephen Colebourne
 */
public class IterableChildAxis extends IterableAxis {

    /**
     *
     */
    private static final long serialVersionUID = 7354613713335746920L;

    /**
     * Constructor.
     *
     * @param value the axis value
     */
    public IterableChildAxis(int value) {
        super(value);
    }

    /**
     * Gets the iterator for the child axis.
     *
     * @param contextNode  the current context node to work from
     * @param support  the additional context information
     * @return an iterator over the children of the context node
     * @throws UnsupportedAxisException if the child axis is not supported
     */
    public Iterator iterator(Object contextNode, ContextSupport support)
      throws UnsupportedAxisException {
        return support.getNavigator().getChildAxisIterator(contextNode);
    }

    /**
     * Gets an iterator for the child XPath axis that supports named access.
     *
     * @param contextNode  the current context node to work from
     * @param support  the additional context information
     * @param localName  the local name of the children to return
     * @param namespacePrefix  the prefix of the namespace of the children to return
     * @param namespaceURI  the URI of the namespace of the children to return
     * @return an iterator over the children of the context node
     * @throws UnsupportedAxisException if the child axis is not supported by the model
     */
    public Iterator namedAccessIterator(
        Object contextNode,
        ContextSupport support,
        String localName,
        String namespacePrefix,
        String namespaceURI)
            throws UnsupportedAxisException {

        NamedAccessNavigator nav = (NamedAccessNavigator) support.getNavigator();
        return nav.getChildAxisIterator(contextNode, localName, namespacePrefix, namespaceURI);
    }

    /**
     * Does this axis support named access?
     *
     * @param support the additional context information
     * @return true if named access supported. If not iterator() will be used
     */
    public boolean supportsNamedAccess(ContextSupport support) {
        return (support.getNavigator() instanceof NamedAccessNavigator);
    }

}
