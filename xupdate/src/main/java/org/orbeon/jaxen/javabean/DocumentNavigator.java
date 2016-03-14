/*
 $Id: DocumentNavigator.java,v 1.8 2006/05/03 16:07:04 elharo Exp $

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
package org.orbeon.jaxen.javabean;

import org.orbeon.jaxen.*;
import org.orbeon.jaxen.util.SingleObjectIterator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;

/**
 * Interface for navigating around a JavaBean object model.
 *
 * <p>
 * This class is not intended for direct usage, but is
 * used by the Jaxen engine during evaluation.
 * </p>
 *
 * @see XPath
 *
 * @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 */
public class DocumentNavigator
    extends DefaultNavigator
    implements NamedAccessNavigator
{

    /**
     *
     */
    private static final long serialVersionUID = -1768605107626726499L;

    /** Empty Class array. */
    private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];

    /** Empty Object array. */
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    /** Singleton implementation.
     */
    private static final DocumentNavigator instance = new DocumentNavigator();

    /** Retrieve the singleton instance of this <code>DocumentNavigator</code>.
     */
    public static Navigator getInstance()
    {
        return instance;
    }

    public boolean isElement(Object obj)
    {
        return (obj instanceof Element);
    }

    public boolean isComment(Object obj)
    {
        return false;
    }

    public boolean isText(Object obj)
    {
        return ( obj instanceof String );
    }

    public boolean isAttribute(Object obj)
    {
        return false;
    }

    public boolean isProcessingInstruction(Object obj)
    {
        return false;
    }

    public boolean isDocument(Object obj)
    {
        return false;
    }

    public boolean isNamespace(Object obj)
    {
        return false;
    }

    public String getElementName(Object obj)
    {
        return ((Element)obj).getName();
    }

    public String getElementNamespaceUri(Object obj)
    {
        return "";
    }

    public String getElementQName(Object obj)
    {
        return "";
    }

    public String getAttributeName(Object obj)
    {
        return "";
    }

    public String getAttributeNamespaceUri(Object obj)
    {
        return "";
    }

    public String getAttributeQName(Object obj)
    {
        return "";
    }

    public Iterator getChildAxisIterator(Object contextNode)
    {
        return JaxenConstants.EMPTY_ITERATOR;
    }

    /**
     * Retrieves an <code>Iterator</code> over the child elements that
     * match the supplied name.
     *
     * @param contextNode  the origin context node
     * @param localName  the local name of the children to return, always present
     * @param namespacePrefix  the prefix of the namespace of the children to return
     * @param namespaceURI  the namespace URI of the children to return
     * @return an Iterator that traverses the named children, or null if none
     */
    public Iterator getChildAxisIterator(Object contextNode,
                                         String localName,
                                         String namespacePrefix,
                                         String namespaceURI)
    {
        Class cls = ((Element)contextNode).getObject().getClass();

        String methodName = javacase( localName );

        Method method = null;

        try
        {
            method = cls.getMethod( "get" + methodName, EMPTY_CLASS_ARRAY );
        }
        catch (NoSuchMethodException e)
        {
            try
            {
                method = cls.getMethod( "get" + methodName + "s", EMPTY_CLASS_ARRAY );
            }
            catch (NoSuchMethodException ee)
            {
                try
                {
                    method = cls.getMethod( localName, EMPTY_CLASS_ARRAY );
                }
                catch (NoSuchMethodException eee)
                {
                    method = null;
                }
            }
        }

        if ( method == null )
        {
            return JaxenConstants.EMPTY_ITERATOR;
        }

        try
        {
            Object result = method.invoke( ((Element)contextNode).getObject(), EMPTY_OBJECT_ARRAY );

            if ( result == null )
            {
                return JaxenConstants.EMPTY_ITERATOR;
            }

            if ( result instanceof Collection )
            {
                return new ElementIterator( (Element) contextNode, localName, ((Collection)result).iterator() );
            }

            if ( result.getClass().isArray() )
            {
                return JaxenConstants.EMPTY_ITERATOR;
            }

            return new SingleObjectIterator( new Element( (Element) contextNode, localName, result ) );
        }
        catch (IllegalAccessException e)
        {
            // swallow
        }
        catch (InvocationTargetException e)
        {
            // swallow
        }

        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getParentAxisIterator(Object contextNode)
    {
        if ( contextNode instanceof Element )
        {
            return new SingleObjectIterator( ((Element)contextNode).getParent() );
        }

        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getAttributeAxisIterator(Object contextNode)
    {
        return JaxenConstants.EMPTY_ITERATOR;
    }

    /**
     * Retrieves an <code>Iterator</code> over the attribute elements that
     * match the supplied name.
     *
     * @param contextNode  the origin context node
     * @param localName  the local name of the attributes to return, always present
     * @param namespacePrefix  the prefix of the namespace of the attributes to return
     * @param namespaceURI  the namespace URI of the attributes to return
     * @return an Iterator that traverses the named attributes, not null
     */
    public Iterator getAttributeAxisIterator(Object contextNode,
                                             String localName,
                                             String namespacePrefix,
                                             String namespaceURI) {
        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getNamespaceAxisIterator(Object contextNode)
    {
        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Object getDocumentNode(Object contextNode)
    {
        return null;
    }

    public Object getParentNode(Object contextNode)
    {
        if ( contextNode instanceof Element )
        {
            return ((Element)contextNode).getParent();
        }

        return JaxenConstants.EMPTY_ITERATOR;
    }

    public String getTextStringValue(Object obj)
    {
        if ( obj instanceof Element )
        {
            return ((Element)obj).getObject().toString();
        }
        return obj.toString();
    }

    public String getElementStringValue(Object obj)
    {
        if ( obj instanceof Element )
        {
            return ((Element)obj).getObject().toString();
        }
        return obj.toString();
    }

    public String getAttributeStringValue(Object obj)
    {
        return obj.toString();
    }

    public String getNamespaceStringValue(Object obj)
    {
        return obj.toString();
    }

    public String getNamespacePrefix(Object obj)
    {
        return null;
    }

    public String getCommentStringValue(Object obj)
    {
        return null;
    }

    public String translateNamespacePrefixToUri(String prefix, Object context)
    {
        return null;
    }

    public short getNodeType(Object node)
    {
        return 0;
    }

    public Object getDocument(String uri) throws FunctionCallException
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

    public XPath parseXPath(String xpath)
        throws org.orbeon.jaxen.saxpath.SAXPathException
    {
        return new JavaBeanXPath( xpath );
    }

    protected String javacase(String name)
    {
        if ( name.length() == 0 )
        {
            return name;
        }
        else if ( name.length() == 1 )
        {
            return name.toUpperCase();
        }

        return name.substring( 0, 1 ).toUpperCase() + name.substring( 1 );
    }
}
