/**
 *  Copyright (C) 2005 Orbeon, Inc.
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

import org.dom4j.util.NodeComparator;
import org.dom4j.util.NonLazyElement;

/**
 * 4/7/2005 d : Under JDK 1.5 the fact that dom4j isn't thread safe by default became apparent.
 * In particular DefaultElement ( and sub-classes thereof ) are not thread safe because of the
 * following :
 * o DefaultElement has a single field, private Object content, by which it refers to all of its
 *   child nodes.  If there is a single child node then content points to it.  If there are more
 *   then content points to a java.util.List which in turns points to all the children.
 * o However, if you do almost anything with an instance of DefaultElement, i.e. iterate over
 *   children, it will first create and fill a list before completing the operation.  This even
 *   if there was only a single child.
 * The consequence of the above is that DefaultElement and its sub-classes aren't thread safe,
 * even if all of the threads are just readers.
 * 
 * The 'usual' solution is to use dom4j's NonLazyElement and NonLazyElementDocumentFactory.
 * However in our case we were using a sub-class of DefaultElement, UserDataElement, whose 
 * functionality is unmatched by NonLazyElement.  Hence this class, a subclass of NonLazyElement  
 * with the safe functionality as UserDataElement.
 * 
 * Btw NonLazyUserDataElement also tries to be smart wrt to cloning and parent specifying.  That
 * is, if you clone the clone will have parent == null but will have all of the requisite 
 * namespace declarations and if you setParent( notNull ) then any redundant namespace declarations
 * are removed.
 * 
 */
public class NonLazyUserDataElement extends NonLazyElement {

    private Object data;
    
    public NonLazyUserDataElement( final String name ) { 
        super( name );
    }

    public NonLazyUserDataElement( final org.dom4j.QName qname ) { 
        super( qname );
    }
    
    public NonLazyUserDataElement( final String nm, final org.dom4j.Namespace ns ) {
        super( nm, ns );
    }
        
    /**
     * Doesn't try to grab name space decls from ancestors.
     * @return a clone.  May be missing some necessary namespace decls.
     * @see #clone
     */
    private NonLazyUserDataElement cloneInternal() {
        final NonLazyUserDataElement ret = ( NonLazyUserDataElement )super.clone();

        if ( ret != this ) {
            ret.content = null;
            ret.attributes = null;
            ret.appendAttributes( this );
            ret.appendContent( this );
            ret.data = getCopyOfUserData();
        }
        return ret;
    }

    protected java.util.List createContentList() {
        return createContentList( 2 );
    }

    protected Object getCopyOfUserData() {
        return data;            
    }

    protected org.dom4j.Element createElement( final String name ) {
        final org.dom4j.DocumentFactory fctry = getDocumentFactory();
        final org.dom4j.QName qnam = fctry.createQName( name );
        return createElement( qnam );
    }
    
    protected org.dom4j.Element createElement( final org.dom4j.QName qName ) {
        final NonLazyUserDataDocumentFactory fctry 
            = NonLazyUserDataDocumentFactory.getInstance14();
        final org.dom4j.Element ret = fctry.createElement( qName );
        final Object dta = getCopyOfUserData();
        ret.setData( dta );
        return ret;
    }
    protected org.dom4j.DocumentFactory getDocumentFactory() {
        return NonLazyUserDataDocumentFactory.getInstance14();
    }

    public Object getData() {
        return data;
    }
    
    public void setData( final Object d ) {
        data = d;
    }    
    
    public String toString() {
        return super.toString() + " userData: " + data;
    }
    
    public void appendAttributes( final org.dom4j.Element src ) {
        for ( int i = 0, size = src.attributeCount(); i < size; i++) {
            final org.dom4j.Attribute att = src.attribute( i );
            final org.dom4j.Attribute attCln = ( org.dom4j.Attribute )att.clone();
            add( attCln );
        }

    }
    
    public void appendContent( final org.dom4j.Branch branch ) {
        final int size = branch.nodeCount();
        for ( int i = 0; i < size; i++ ) {
            final org.dom4j.Node node = branch.node( i );
            final org.dom4j.Node cln;
            if ( node.getNodeType() == org.dom4j.Node.ELEMENT_NODE ) {
                cln = ( ( NonLazyUserDataElement )node ).cloneInternal();
            } else {
                cln = ( org.dom4j.Node )node.clone();
            }
            add( cln );
        }
    }

    /**
     * @return A clone.  The clone will have parent == null but will have any necessary namespace
     *         declarations this element's ancestors.
     */
    public Object clone() {
        final NonLazyUserDataElement ret = cloneInternal();
        org.dom4j.Element anstr = getParent();
        done : if ( anstr != null ) {
            final NodeComparator nc = new NodeComparator();
            final java.util.TreeSet nsSet = new java.util.TreeSet( nc );

            do {
                final java.util.List sibs = anstr.content();
                for ( final java.util.Iterator itr = sibs.iterator(); itr.hasNext(); ) {
                    final org.dom4j.Node sib = ( org.dom4j.Node )itr.next();
                    if ( sib.getNodeType() != org.dom4j.Node.NAMESPACE_NODE ) continue;
                    nsSet.add( sib );
                }
                anstr = anstr.getParent();
            }
            while ( anstr != null ); 
            if ( nsSet.isEmpty() ) break done;
            for ( final java.util.Iterator itr = nsSet.iterator(); itr.hasNext(); ) {
                final org.dom4j.Namespace ns = ( org.dom4j.Namespace )itr.next();
                final String pfx = ns.getPrefix();
                if ( ret.getNamespaceForPrefix( pfx ) != null ) continue;
                ret.add( ns );
            }
        }
        return ret;
    }
    /**
     * If parent != null checks with ancestors and removes any redundant namespace declarations.
     */
    public void setParent( final org.dom4j.Element prnt ) {
        super.setParent( prnt );
        done : if ( prnt != null ) {
            final org.dom4j.Namespace myNs = getNamespace();
            if ( myNs != org.dom4j.Namespace.NO_NAMESPACE ) {
                final String myPfx = myNs.getPrefix();
                final org.dom4j.Namespace prntNs = prnt.getNamespaceForPrefix( myPfx );
                if ( myPfx.equals( prntNs ) ) {
                    final String myNm = myNs.getName();
                    final org.dom4j.QName newNm = new org.dom4j.QName( myNm );
                    setQName( newNm );
                }
            }
            if ( content == null ) break done;
            for ( final java.util.Iterator itr = content.iterator(); itr.hasNext(); ) {
                final org.dom4j.Node chld = ( org.dom4j.Node )itr.next();
                if ( chld.getNodeType() != org.dom4j.Node.NAMESPACE_NODE ) continue;

                final org.dom4j.Namespace ns = ( org.dom4j.Namespace )chld;
                final String pfx = ns.getPrefix();

                final org.dom4j.Namespace prntNs = prnt.getNamespaceForPrefix( pfx );
                if ( ns.equals( prntNs ) ) itr.remove();
            }
        }
    }
    

    public org.dom4j.Element createCopy() {
        final NonLazyUserDataElement ret = ( NonLazyUserDataElement )super.createCopy();
        ret.data = getCopyOfUserData();
        return ret;
    }

    public org.dom4j.Element createCopy( final org.dom4j.QName qName ) {
        final NonLazyUserDataElement ret = ( NonLazyUserDataElement )super.createCopy( qName );
        ret.data = getCopyOfUserData();
        return ret;
    }

    public org.dom4j.Element createCopy( final String name ) {
        final NonLazyUserDataElement ret = ( NonLazyUserDataElement )super.createCopy( name );
        ret.data = getCopyOfUserData();
        return ret;
    }
}

