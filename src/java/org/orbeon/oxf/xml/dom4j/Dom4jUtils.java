/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml.dom4j;

import org.dom4j.DocumentFactory;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.dom4j.util.UserDataDocumentFactory;
import org.dom4j.util.UserDataElement;
import org.dom4j.util.UserDataAttribute;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.xml.NamespaceCleanupContentHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;

public class Dom4jUtils {
    
    public static final Namespace XSI_NAMESPACE = new Namespace
        ( XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI );
    
    public static final org.dom4j.Document NULL_DOCUMENT;

    static {
        NULL_DOCUMENT = DocumentHelper.createDocument();
        Element nullElement = DocumentHelper.createElement("null");
        final org.dom4j.QName attNm = new QName
            ( XMLConstants.XSI_NIL_ATTRIBUTE, XSI_NAMESPACE ); 
        nullElement.addAttribute( attNm, "true" );
        NULL_DOCUMENT.setRootElement( nullElement );
    }
    
    private static String domToString
    ( final org.dom4j.Node n, final OutputFormat frmt ) {
        try {
            final StringWriter wrtr = new StringWriter();
            // Ugh, XMLWriter doesn't accept null formatter _and_ default 
            // formatter is protected.
            final XMLWriter xmlWrtr 
                = frmt == null 
                 ? new XMLWriter( wrtr ) : new XMLWriter( wrtr, frmt );
            xmlWrtr.write( n );
            xmlWrtr.close();
            return wrtr.toString();
        } catch ( final IOException e ) {
            throw new OXFException(e);
        }
    }
    
    private static String domToString
    ( final org.dom4j.Branch brnch, final boolean trm, final boolean cmpct  ) {
        final OutputFormat frmt = new OutputFormat();
        if ( cmpct ) {
            frmt.setIndent( false );
            frmt.setNewlines( false );
            frmt.setTrimText( true );
        } else {
            frmt.setIndentSize( 4 );
            frmt.setNewlines( trm );
            frmt.setTrimText( trm );
        }
        final String ret = domToString( brnch, frmt );
        return ret;
    }
    
    private static org.dom4j.Node cloneNodeWorker(org.dom4j.Node node) {
        if (node instanceof UserDataElement) {
            UserDataElement current = (UserDataElement) node;
            UserDataElement clone = new UserDataElement(current.getQName());
            clone.setData(current.getData());

            // Copy attributes
            for (Iterator i = current.attributes().iterator(); i.hasNext();) {
                org.dom4j.Attribute attribute = (org.dom4j.Attribute) i.next();
                clone.add(cloneNodeWorker(attribute));
            }

            // Copy content
            for (Iterator i = current.content().iterator(); i.hasNext();) {
                org.dom4j.Node child = (org.dom4j.Node) i.next();
                clone.add(cloneNodeWorker(child));
            }
            return clone;
        } else if (node instanceof UserDataAttribute) {
            UserDataAttribute current = (UserDataAttribute) node;
            UserDataAttribute clone = new UserDataAttribute
                (current.getQName(), current.getText());
            clone.setData(current.getData());
            return clone;
        } else {
            return (org.dom4j.Node) node.clone();
        }
    }

    /**
     * Clone a node, making sure that we copy all the declared namespace of
     * the source.
     */
    public static org.dom4j.Node cloneNode( final org.dom4j.Node n ) {
        final org.dom4j.Node ret;
        if ( n.getNodeType() == org.dom4j.Node.ELEMENT_NODE ) {
            ret = cloneElement( ( org.dom4j.Element )n );
        } else {
            ret = cloneNodeWorker( n );
        }
        return ret;
    }
    /**
     * Clone a node, making sure that we copy all the declared namespace of
     * the source.
     */
    public static org.dom4j.Element cloneElement( final org.dom4j.Element e ) {
        final Map namespaceContext = Dom4jUtils.getNamespaceContext( e );
        final org.dom4j.Element ret = (org.dom4j.Element) cloneNodeWorker( e );
        for ( final Iterator i = namespaceContext.keySet().iterator(); 
              i.hasNext(); ) {
            final String pfx = ( String )i.next();
            if ( ret.getNamespaceForPrefix( pfx ) == null ) {
                ret.addNamespace(pfx, ( String )namespaceContext.get( pfx ) );
            }
        }
        return ret;
    }
    /**
     * Replacment for DocumentHelper.parseText.  DocumentHelper.parseText is not
     * used since it creates work for GC ( because it relies on JAXP ). 
     */
    public static org.dom4j.Document parseText( final String s ) 
    throws SAXException, org.dom4j.DocumentException {
        final SAXParser sxPrs = XMLUtils.newSAXParser();
        final XMLReader xr = sxPrs.getXMLReader();
        final SAXReader sxRdr = new SAXReader( xr );
        final java.io.StringReader strRdr = new java.io.StringReader( s );
        final org.dom4j.Document ret = sxRdr.read( strRdr );
        return ret;
    }

    /**
     * Removes the elements and text inside the given element, but not the 
     * attributes or namespace declarations on the element.
     */
    public static void clearElementContent( final org.dom4j.Element elt ) {
        final java.util.List cntnt = elt.content();
        for ( final java.util.ListIterator j = cntnt.listIterator(); 
              j.hasNext(); ) {
            final org.dom4j.Node chld = ( org.dom4j.Node )j.next();
            if ( chld.getNodeType() == org.dom4j.Node.TEXT_NODE 
                 || chld.getNodeType() == org.dom4j.Node.ELEMENT_NODE ) {
                j.remove();
            }
        }
    }
    public static String makeSystemId( final org.dom4j.Document d ) {
        final org.dom4j.Element e = d.getRootElement();
        final String ret = makeSystemId( e );
        return ret;
    }
    public static String makeSystemId( final org.dom4j.Element e ) {
        final LocationData ld = ( LocationData )e.getData();
        final String ldSid = ld == null ? null : ld.getSystemID();
        final String ret = ldSid == null ? DOMGenerator.DefaultContext : ldSid;
        return ret;
    }

    public static org.dom4j.Document createDOM4JDocument() {
        final DocumentFactory fctry = UserDataDocumentFactory.getInstance();        
        return fctry.createDocument();
    }

    /*
        *  Convert the result of XPathUtils.selectObjectValue() to a string
        */
    public static String objectToString(Object o) {
        StringBuffer buff = new StringBuffer();
        if (o instanceof List) {
            for (Iterator i = ((List) o).iterator(); i.hasNext();) {
                // this will be a node
                buff.append(objectToString(i.next())); 
            }
        } else if(o instanceof org.dom4j.Element){
            buff.append(((org.dom4j.Element) o).asXML());
        } else if(o instanceof org.dom4j.Node){
            buff.append(((org.dom4j.Node)o).asXML());
        } else if (o instanceof String)
            buff.append((String) o);
        else if (o instanceof Number)
            buff.append((Number) o);
        else
            throw new OXFException("Should never happen");
        return buff.toString();
    }

    public static String domToString
    ( final org.dom4j.Element e, final boolean trm, final boolean cmpct ) {
        final org.dom4j.Branch cpy = e.createCopy();
        final String ret = Dom4jUtils.domToString( cpy, trm, cmpct );
        return ret;
    }

    public static String domToString( final org.dom4j.Element e ) {
        final String ret = domToString( e, true, false );
        return ret;
    }

    public static String domToString
    ( final org.dom4j.Document d, final boolean trm, final boolean cmpct ) {
        final org.dom4j.Element relt = d.getRootElement();
        final String ret = domToString( relt, trm, cmpct );
        return ret;
    }

    public static String domToString( final org.dom4j.Document d ) {
        final String ret = domToString( d, true, false );
        return ret;
    }

    public static String domToString
    ( final org.dom4j.Text txt, final boolean t, final boolean c ) {
        final String ret = txt.getText();
        return ret;
    }

    public static String domToString( final org.dom4j.Text t ) {
        return domToString( t, true, false );
    }

    /**
     * Checks type of n and, if apropriate, downcasts and returns 
     * domToString( ( Type )n, t, c ).  Otherwise returns domToString( n, null )
     */
    public static String domToString
    ( final org.dom4j.Node n, final boolean t, final boolean c ) {
        final String ret;
        switch ( n.getNodeType() ) {
            case org.dom4j.Node.DOCUMENT_NODE : 
            {
                ret = domToString( ( org.dom4j.Document )n, t, c ); break;
            }
            case org.dom4j.Node.ELEMENT_NODE : 
            {
                ret = domToString( ( org.dom4j.Element )n, t, c ); break;
            }
            case org.dom4j.Node.TEXT_NODE : 
            {
                ret = domToString( ( org.dom4j.Text )n, t, c ); break;
            }
            default : ret = domToString( n, null ); break; 
        }
        return ret;
    }

    public static String domToString( final org.dom4j.Node nd ) {
        return domToString( nd, true, false );
    }

    public static DocumentSource getDocumentSource
    ( final org.dom4j.Document d ) {
        /*
         * Saxon's error handler is expensive for the service it provides so we just use our 
         * singeton intead. 
         * 
         * Wrt expensive, delta in heap dump info below is amount of bytes allocated during the 
         * handling of a single request to '/' in the examples app. i.e. The trace below was 
         * responsible for creating 200k of garbage during the handing of a single request to '/'.
         * 
         * delta: 213408 live: 853632 alloc: 4497984 trace: 380739 class: byte[]
         * 
         * TRACE 380739:
         * java.nio.HeapByteBuffer.<init>(HeapByteBuffer.java:39)
         * java.nio.ByteBuffer.allocate(ByteBuffer.java:312)
         * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:310)
         * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:290)
         * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:274)
         * sun.nio.cs.StreamEncoder.forOutputStreamWriter(StreamEncoder.java:69)
         * java.io.OutputStreamWriter.<init>(OutputStreamWriter.java:93)
         * java.io.PrintWriter.<init>(PrintWriter.java:109)
         * java.io.PrintWriter.<init>(PrintWriter.java:92)
         * org.orbeon.saxon.StandardErrorHandler.<init>(StandardErrorHandler.java:22)
         * org.orbeon.saxon.event.Sender.sendSAXSource(Sender.java:165)
         * org.orbeon.saxon.event.Sender.send(Sender.java:94)
         * org.orbeon.saxon.IdentityTransformer.transform(IdentityTransformer.java:31)
         * org.orbeon.oxf.xml.XMLUtils.getDigest(XMLUtils.java:453)
         * org.orbeon.oxf.xml.XMLUtils.getDigest(XMLUtils.java:423)
         * org.orbeon.oxf.processor.generator.DOMGenerator.<init>(DOMGenerator.java:93)         
         *
         * Before mod
         *
         * 1.4.2_06-b03 	P4 2.6 Ghz	/ 	50 th	tc 4.1.30	10510 ms ( 150 mb ), 7124 ( 512 mb ) 	2.131312472239924 ( 150 mb ), 1.7474380872589803 ( 512 mb )
         *
         * after mod
         *
         * 1.4.2_06-b03 	P4 2.6 Ghz	/ 	50 th	tc 4.1.30	9154 ms ( 150 mb ), 6949 ( 512 mb ) 	1.7316203642295738 ( 150 mb ), 1.479365288194895 ( 512 mb )
         *
         */
        final LocationDocumentSource lds = new LocationDocumentSource( d );
        final XMLReader rdr = lds.getXMLReader();
        rdr.setErrorHandler( XMLUtils.errorHandler );
        return lds;
    }

    public static byte[] getDigest(org.dom4j.Document document) {
        final DocumentSource ds = getDocumentSource( document );
        return XMLUtils.getDigest( ds );
    }

    /**
     * Clean-up namespaces. Some tools generate namespace "un-declarations" or 
     * the form xmlns:abc="". While this is needed to keep the XML infoset 
     * correct, it is illegal to generate such declarations in XML 1.0 (but it 
     * is legal in XML 1.1). Technically, this cleanup is incorrect at the DOM 
     * and SAX level, so this should be used only in rare occasions, when
     * serializing certain documents to XML 1.0.
     */
    public static org.dom4j.Document adjustNamespaces
    (org.dom4j.Document document, boolean xml11) {
        if (xml11)
            return document;
        LocationSAXWriter writer = new LocationSAXWriter();
        LocationSAXContentHandler ch = new LocationSAXContentHandler();
        writer.setContentHandler(new NamespaceCleanupContentHandler(ch, xml11));
        try {
            writer.write(document);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
        return ch.getDocument();
    }
    
    public static Map getNamespaceContext(Element element) {
        Map namespaces = new HashMap();
        for ( Element currentNode = element; 
              currentNode != null; 
              currentNode = currentNode.getParent()) {
            List currentNamespaces = currentNode.declaredNamespaces();
            for (Iterator j = currentNamespaces.iterator(); j.hasNext();) {
                Namespace namespace = (Namespace) j.next();
                if (!namespaces.containsKey(namespace.getPrefix()))
                    namespaces.put(namespace.getPrefix(), namespace.getURI());
            }
        }
        return namespaces;
    }

    /**
     * Extract a QName from an Element and an attribute name. The prefix of the QName must be in
     * scope. Return null if the attribute is not found.
     */
    public static QName extractAttributeValueQName(Element element, String attributeName) {
        return extractTextValueQName(element, element.attributeValue(attributeName));
    }

    /**
     * Extract a QName from an Element's string value. The prefix of the QName must be in scope.
     * Return null if the text is empty.
     */
    public static QName extractTextValueQName(Element element) {
        return extractTextValueQName(element, element.getStringValue());
    }

    private static QName extractTextValueQName(Element element, String qNameString) {
        if (qNameString == null)
            return null;
        qNameString = qNameString.trim();
        if (qNameString.length() == 0)
            return null;
        Map namespaces = getNamespaceContext(element);
        int colonIndex = qNameString.indexOf(':');
        String prefix = qNameString.substring(0, colonIndex);
        String localName = qNameString.substring(colonIndex + 1);
        String namespaceURI = (String) namespaces.get(prefix);
        if (namespaceURI == null)
            throw new OXFException("No namespace declaration found for prefix: " + prefix);
    
        return new QName(localName, new Namespace(prefix, namespaceURI));
    }

    /**
     * Decode a String containing an exploded QName into a QName.
     */
    public static QName explodedQNameToQName(String qName) {
        int openIndex = qName.indexOf("{");
    
        if (openIndex == -1)
            return new QName(qName);
    
        String namespaceURI = qName.substring(openIndex + 1, qName.indexOf("}"));
        String localName = qName.substring(qName.indexOf("}") + 1);
        return new QName(localName, new Namespace("p1", namespaceURI));
    }

    /**
     * Encode a QName to an exploded QName String.
     */
    public static String qNameToexplodedQName(QName qName) {
        if ("".equals(qName.getNamespaceURI()))
            return qName.getName();
        else
            return "{" + qName.getNamespaceURI() + "}" + qName.getName();
    }
}
