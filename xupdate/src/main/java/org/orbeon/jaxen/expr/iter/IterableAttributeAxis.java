/*
 $Id: IterableAttributeAxis.java,v 1.9 2006/05/03 16:07:03 elharo Exp $

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
 * Provide access to the XPath attribute axis.
 * This axis does not include namespace declarations such as
 * <code>xmlns</code> and <code>xmlns:<i>prefix</i></code>.
 * It does include attributes defaulted from the DTD.
 *
 * @author Bob McWhirter
 * @author James Strachan
 * @author Stephen Colebourne
 */
public class IterableAttributeAxis extends IterableAxis {

    /**
     *
     */
    private static final long serialVersionUID = 8997702757140379230L;

    /**
     * Constructor.
     *
     * @param value the axis value
     */
    public IterableAttributeAxis(int value) {
        super(value);
    }

    /**
     * Gets an iterator for the attribute axis.
     *
     * @param contextNode  the current context node to work from
     * @param support  the additional context information
     */
    public Iterator iterator(Object contextNode, ContextSupport support) throws UnsupportedAxisException {
        return support.getNavigator().getAttributeAxisIterator(contextNode);
    }

    /**
     * Gets the iterator for the attribute axis that supports named access.
     *
     * @param contextNode  the current context node to work from
     * @param support  the additional context information
     * @param localName  the local name of the attributes to return
     * @param namespacePrefix  the prefix of the namespace of the attributes to return
     * @param namespaceURI  the uri of the namespace of the attributes to return
     */
    public Iterator namedAccessIterator(
        Object contextNode,
        ContextSupport support,
        String localName,
        String namespacePrefix,
        String namespaceURI)
            throws UnsupportedAxisException {

        NamedAccessNavigator nav = (NamedAccessNavigator) support.getNavigator();
        return nav.getAttributeAxisIterator(contextNode, localName, namespacePrefix, namespaceURI);
    }

    /**
     * Does this axis support named access?
     *
     * @param support  the additional context information
     * @return true if named access is supported. If not iterator() will be used.
     */
    public boolean supportsNamedAccess(ContextSupport support) {
        return (support.getNavigator() instanceof NamedAccessNavigator);
    }

}
