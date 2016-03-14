/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/DefaultNavigator.java,v 1.19 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.19 $
 * $Date: 2006/02/05 21:47:41 $
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
 * $Id: DefaultNavigator.java,v 1.19 2006/02/05 21:47:41 elharo Exp $
 */



package org.orbeon.jaxen;

import org.orbeon.jaxen.pattern.Pattern;
import org.orbeon.jaxen.util.*;

import java.util.Iterator;

/** Default implementation of {@link Navigator}.
 *
 *  <p>
 *  This implementation is an abstract class, since
 *  some required operations cannot be implemented without
 *  additional knowledge of the object model.
 *  </p>
 *
 *  <p>
 *  When possible, default method implementations build
 *  upon each other, to reduce the number of methods required
 *  to be implemented for each object model.  All methods,
 *  of course, may be overridden, to provide more-efficient
 *  implementations.
 *  </p>
 *
 *  @author bob mcwhirter (bob@werken.com)
 *  @author Erwin Bolwidt (ejb@klomp.org)
 */
public abstract class DefaultNavigator implements Navigator
{

    /** Throws <code>UnsupportedAxisException</code>
     *
     * @param contextNode
     * @return never returns
     * @throws UnsupportedAxisException always
     */
    public Iterator getChildAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        throw new UnsupportedAxisException("child");
    }

    /* (non-Javadoc)
     * @see org.orbeon.jaxen.Navigator#getDescendantAxisIterator(java.lang.Object)
     */
    public Iterator getDescendantAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new DescendantAxisIterator( contextNode,
                                           this );
    }

    /** Throws <code>UnsupportedAxisException</code>
     *
     * @param  contextNode
     * @return never returns
     * @throws UnsupportedAxisException
     */
    public Iterator getParentAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        throw new UnsupportedAxisException("parent");
    }

    public Iterator getAncestorAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new AncestorAxisIterator( contextNode,
                                         this );
    }


    public Iterator getFollowingSiblingAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new FollowingSiblingAxisIterator( contextNode,
                                                 this );
    }


    public Iterator getPrecedingSiblingAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new PrecedingSiblingAxisIterator( contextNode,
                                                 this );
    }

    public Iterator getFollowingAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new FollowingAxisIterator( contextNode,
                                          this );

        // throw new UnsupportedAxisException("following");
    }


    public Iterator getPrecedingAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new PrecedingAxisIterator( contextNode,
                                         this );

        // throw new UnsupportedAxisException("preceding");
    }

    /** Throws <code>UnsupportedAxisException</code>. Subclasses that
     * support the attribute axis must override this method.
     *
     * @param contextNode
     * @return never returns
     * @throws UnsupportedAxisException
     */
    public Iterator getAttributeAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        throw new UnsupportedAxisException("attribute");
    }

    /** Throws <code>UnsupportedAxisException</code>. Subclasses that
     * support the namespace axis must override this method.
     *
     * @param contextNode
     * @return never returns
     * @throws UnsupportedAxisException
     */
    public Iterator getNamespaceAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        throw new UnsupportedAxisException("namespace");
    }

    public Iterator getSelfAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new SelfAxisIterator( contextNode );
    }

    public Iterator getDescendantOrSelfAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new DescendantOrSelfAxisIterator( contextNode,
                                                 this );
    }

    public Iterator getAncestorOrSelfAxisIterator(Object contextNode) throws UnsupportedAxisException
    {
        return new AncestorOrSelfAxisIterator( contextNode,
                                               this );
    }

    public Object getDocumentNode(Object contextNode)
    {
        return null;
    }

    public String translateNamespacePrefixToUri(String prefix, Object element)
    {
        return null;
    }

    public String getProcessingInstructionTarget(Object obj)
    {
        return null;
    }

    public String getProcessingInstructionData(Object obj)
    {
        return null;
    }

    public short getNodeType(Object node)
    {
        if ( isElement(node) )
        {
            return Pattern.ELEMENT_NODE;
        }
        else if ( isAttribute(node) )
        {
            return Pattern.ATTRIBUTE_NODE;
        }
        else if ( isText(node) )
        {
            return Pattern.TEXT_NODE;
        }
        else if ( isComment(node) )
        {
            return Pattern.COMMENT_NODE;
        }
        else if ( isDocument(node) )
        {
            return Pattern.DOCUMENT_NODE;
        }
        else if ( isProcessingInstruction(node) )
        {
            return Pattern.PROCESSING_INSTRUCTION_NODE;
        }
        else if ( isNamespace(node) )
        {
            return Pattern.NAMESPACE_NODE;
        }
        else {
            return Pattern.UNKNOWN_NODE;
        }
    }

    /**
     * Default inefficient implementation. Subclasses
     * should override this method.
     *
     * @param contextNode   the node whose parent to return
     * @return the parent node
     * @throws UnsupportedAxisException if the parent axis is not supported
     */
    public Object getParentNode(Object contextNode) throws UnsupportedAxisException
    {
        Iterator iter = getParentAxisIterator( contextNode );
        if ( iter != null && iter.hasNext() )
        {
            return iter.next();
        }
        return null;
    }

    /**
     *  Default implementation that always returns null. Override in subclass
     *  if the subclass can load documents.
     *
     * @param url the URL of the document to load
     *
     * @return null
     * @throws FunctionCallException if an error occurs while loading the
     *    URL; e.g. an I/O error or the document is malformed
     */
    public Object getDocument(String url) throws FunctionCallException
    {
        return null;
    }

    /**
     *  Default implementation that cannot find elements. Override in subclass
     *  if subclass does know about attribute types.
     *
     *  @param contextNode   a node from the document in which to look for the
     *                       id
     *  @param elementId   id to look for
     *
     *  @return   null
     */
    public Object getElementById(Object contextNode, String elementId)
    {
        return null;
    }

}
