package org.orbeon.jaxen.dom4j;

/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/dom4j/DocumentNavigator.java,v 1.32 2006/05/03 16:07:03 elharo Exp $
 * $Revision: 1.32 $
 * $Date: 2006/05/03 16:07:03 $
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
 * $Id: DocumentNavigator.java,v 1.32 2006/05/03 16:07:03 elharo Exp $
*/

import org.orbeon.dom4j.*;
import org.orbeon.dom4j.io.SAXReader;
import org.orbeon.jaxen.*;
import org.orbeon.jaxen.XPath;
import org.orbeon.jaxen.saxpath.SAXPathException;
import org.orbeon.jaxen.util.SingleObjectIterator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * Interface for navigating around the DOM4J object model.
 *
 * <p>
 * This class is not intended for direct usage, but is
 * used by the Jaxen engine during evaluation.
 * </p>
 *
 * @see XPath
 *
 * @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 * @author Stephen Colebourne
 */
public class DocumentNavigator extends DefaultNavigator implements NamedAccessNavigator
{

    /**
     *
     */
    private static final long serialVersionUID = 5582300797286535936L;
    private transient SAXReader reader;

    /** Singleton implementation.
     */
    private static class Singleton
    {
        /** Singleton instance.
         */
        private static DocumentNavigator instance = new DocumentNavigator();
    }

    /** Retrieve the singleton instance of this <code>DocumentNavigator</code>.
     */
    public static Navigator getInstance()
    {
        return Singleton.instance;
    }

    public boolean isElement(Object obj)
    {
        return obj instanceof Element;
    }

    public boolean isComment(Object obj)
    {
        return obj instanceof Comment;
    }

    public boolean isText(Object obj)
    {
        return ( obj instanceof Text
                 ||
                 obj instanceof CDATA );
    }

    public boolean isAttribute(Object obj)
    {
        return obj instanceof Attribute;
    }

    public boolean isProcessingInstruction(Object obj)
    {
        return obj instanceof ProcessingInstruction;
    }

    public boolean isDocument(Object obj)
    {
        return obj instanceof Document;
    }

    public boolean isNamespace(Object obj)
    {
        return obj instanceof Namespace;
    }

    public String getElementName(Object obj)
    {
        Element elem = (Element) obj;

        return elem.getName();
    }

    public String getElementNamespaceUri(Object obj)
    {
        Element elem = (Element) obj;

        String uri = elem.getNamespaceURI();
        if ( uri == null)
            return "";
        else
            return uri;
    }

    public String getElementQName(Object obj)
    {
        Element elem = (Element) obj;

        return elem.getQualifiedName();
    }

    public String getAttributeName(Object obj)
    {
        Attribute attr = (Attribute) obj;

        return attr.getName();
    }

    public String getAttributeNamespaceUri(Object obj)
    {
        Attribute attr = (Attribute) obj;

        String uri = attr.getNamespaceURI();
        if ( uri == null)
            return "";
        else
            return uri;
    }

    public String getAttributeQName(Object obj)
    {
        Attribute attr = (Attribute) obj;

        return attr.getQualifiedName();
    }

    public Iterator getChildAxisIterator(Object contextNode)
    {
        Iterator result = null;
        if ( contextNode instanceof Branch )
        {
            Branch node = (Branch) contextNode;
            result = node.nodeIterator();
        }
        if (result != null) {
            return result;
        }
        return JaxenConstants.EMPTY_ITERATOR;
    }

    /**
     * Retrieves an <code>Iterator</code> over the child elements that
     * match the supplied name.
     *
     * @param contextNode  the origin context node
     * @param localName  the local name of the children to return, always present
     * @param namespacePrefix  the prefix of the namespace of the children to return
     * @param namespaceURI  the uri of the namespace of the children to return
     * @return an Iterator that traverses the named children, or null if none
     */
    public Iterator getChildAxisIterator(
            Object contextNode, String localName, String namespacePrefix, String namespaceURI) {

        if ( contextNode instanceof Element ) {
            Element node = (Element) contextNode;
            return node.elementIterator(QName.get(localName, namespacePrefix, namespaceURI));
        }
        if ( contextNode instanceof Document ) {
            Document node = (Document) contextNode;
            Element el = node.getRootElement();
            if (el.getName().equals(localName) == false) {
                return JaxenConstants.EMPTY_ITERATOR;
            }
            if (namespaceURI != null) {
                if (namespaceURI.equals(el.getNamespaceURI()) == false) {
                    return JaxenConstants.EMPTY_ITERATOR;
                }
            }
            return new SingleObjectIterator(el);
        }

        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getParentAxisIterator(Object contextNode)
    {
        if ( contextNode instanceof Document )
        {
            return JaxenConstants.EMPTY_ITERATOR;
        }

        Node node = (Node) contextNode;

        Object parent = node.getParent();

        if ( parent == null )
        {
            parent = node.getDocument();
        }

        return new SingleObjectIterator( parent );
    }

    public Iterator getAttributeAxisIterator(Object contextNode)
    {
        if ( ! ( contextNode instanceof Element ) )
        {
            return JaxenConstants.EMPTY_ITERATOR;
        }

        Element elem = (Element) contextNode;

        return elem.attributeIterator();
    }

    /**
     * Retrieves an <code>Iterator</code> over the attribute elements that
     * match the supplied name.
     *
     * @param contextNode  the origin context node
     * @param localName  the local name of the attributes to return, always present
     * @param namespacePrefix  the prefix of the namespace of the attributes to return
     * @param namespaceURI  the URI of the namespace of the attributes to return
     * @return an Iterator that traverses the named attributes, not null
     */
    public Iterator getAttributeAxisIterator(
            Object contextNode, String localName, String namespacePrefix, String namespaceURI) {

        if ( contextNode instanceof Element ) {
            Element node = (Element) contextNode;
            Attribute attr = node.attribute(QName.get(localName, namespacePrefix, namespaceURI));
            if (attr == null) {
                return JaxenConstants.EMPTY_ITERATOR;
            }
            return new SingleObjectIterator(attr);
        }
        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getNamespaceAxisIterator(Object contextNode)
    {
        if ( ! ( contextNode instanceof Element ) )
        {
            return JaxenConstants.EMPTY_ITERATOR;
        }

        Element element = (Element) contextNode;
        List nsList = new ArrayList();
        HashSet prefixes = new HashSet();
        for ( Element context = element; context != null; context = context.getParent() ) {
            List declaredNS = new ArrayList(context.declaredNamespaces());
            declaredNS.add(context.getNamespace());

            for ( Iterator iter = context.attributes().iterator(); iter.hasNext(); )
            {
                Attribute attr = (Attribute) iter.next();
                declaredNS.add(attr.getNamespace());
            }

            for ( Iterator iter = declaredNS.iterator(); iter.hasNext(); )
            {
                Namespace namespace = (Namespace) iter.next();
                if (namespace != Namespace.NO_NAMESPACE)
                {
                    String prefix = namespace.getPrefix();
                    if ( ! prefixes.contains( prefix ) ) {
                        prefixes.add( prefix );
                        nsList.add( namespace.asXPathResult( element ) );
                    }
                }
            }
        }
        nsList.add( Namespace.XML_NAMESPACE.asXPathResult( element ) );
        return nsList.iterator();
    }

    public Object getDocumentNode(Object contextNode)
    {
        if ( contextNode instanceof Document )
        {
            return contextNode;
        }
        else if ( contextNode instanceof Node )
        {
            Node node = (Node) contextNode;
            return node.getDocument();
        }
        return null;
    }

    /** Returns a parsed form of the given XPath string, which will be suitable
     *  for queries on DOM4J documents.
     */
    public XPath parseXPath (String xpath) throws SAXPathException
    {
        return new Dom4jXPath(xpath);
    }

    public Object getParentNode(Object contextNode)
    {
        if ( contextNode instanceof Node )
        {
            Node node = (Node) contextNode;
            Object answer = node.getParent();
            if ( answer == null )
            {
                answer = node.getDocument();
                if (answer == contextNode) {
                    return null;
                }
            }
            return answer;
        }
        return null;
    }

    public String getTextStringValue(Object obj)
    {
        return getNodeStringValue( (Node) obj );
    }

    public String getElementStringValue(Object obj)
    {
        return getNodeStringValue( (Node) obj );
    }

    public String getAttributeStringValue(Object obj)
    {
        return getNodeStringValue( (Node) obj );
    }

    private String getNodeStringValue(Node node)
    {
        return node.getStringValue();
    }

    public String getNamespaceStringValue(Object obj)
    {
        Namespace ns = (Namespace) obj;

        return ns.getURI();
    }

    public String getNamespacePrefix(Object obj)
    {
        Namespace ns = (Namespace) obj;

        return ns.getPrefix();
    }

    public String getCommentStringValue(Object obj)
    {
        Comment cmt = (Comment) obj;

        return cmt.getText();
    }

    public String translateNamespacePrefixToUri(String prefix, Object context)
    {
        Element element = null;
        if ( context instanceof Element )
        {
            element = (Element) context;
        }
        else if ( context instanceof Node )
        {
            Node node = (Node) context;
            element = node.getParent();
        }
        if ( element != null )
        {
            Namespace namespace = element.getNamespaceForPrefix( prefix );

            if ( namespace != null )
            {
                return namespace.getURI();
            }
        }
        return null;
    }

    public short getNodeType(Object node)
    {
        if ( node instanceof Node )
        {
            return ((Node) node).getNodeType();
        }
        return 0;
    }

    public Object getDocument(String uri) throws FunctionCallException
    {
        try
        {
            return getSAXReader().read( uri );
        }
        catch (DocumentException e)
        {
            throw new FunctionCallException("Failed to parse document for URI: " + uri, e);
        }
    }

    public String getProcessingInstructionTarget(Object obj)
    {
        ProcessingInstruction pi = (ProcessingInstruction) obj;

        return pi.getTarget();
    }

    public String getProcessingInstructionData(Object obj)
    {
        ProcessingInstruction pi = (ProcessingInstruction) obj;

        return pi.getText();
    }

    // Properties
    //-------------------------------------------------------------------------
    public SAXReader getSAXReader()
    {
        if ( reader == null )
        {
            reader = new SAXReader();
            reader.setMergeAdjacentText( true );
        }
        return reader;
    }

    public void setSAXReader(SAXReader reader)
    {
        this.reader = reader;
    }

}
