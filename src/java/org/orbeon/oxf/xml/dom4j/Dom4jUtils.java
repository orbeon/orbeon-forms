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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.dom4j.util.UserDataElement;
import org.dom4j.util.UserDataAttribute;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.util.Iterator;
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
        final Map namespaceContext = XMLUtils.getNamespaceContext( e );
        final org.dom4j.Element ret = (org.dom4j.Element) cloneNodeWorker( e );
        for ( final Iterator i = namespaceContext.keySet().iterator(); i.hasNext(); ) {
            final String pfx = ( String )i.next();
            if ( ret.getNamespaceForPrefix( pfx ) == null ) {
                ret.addNamespace(pfx, ( String )namespaceContext.get( pfx ) );
            }
        }
        return ret;
    }
    /**
     * Replacment for DocumentHelper.parseText.  DocumentHelper.parseText is creates work for GC
     * because it relies on JAXP. 
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
            UserDataAttribute clone = new UserDataAttribute(current.getQName(), current.getText());
            clone.setData(current.getData());
            return clone;
        } else {
            return (org.dom4j.Node) node.clone();
        }
    }

    /**
     * Removes the elements and text inside the given element, but not the attributes or
     * namespace declarations on the element.
     */
    public static void clearElementContent( final org.dom4j.Element elt ) {
        final java.util.List cntnt = elt.content();
        for ( final java.util.ListIterator j = cntnt.listIterator(); j.hasNext(); ) {
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
}
