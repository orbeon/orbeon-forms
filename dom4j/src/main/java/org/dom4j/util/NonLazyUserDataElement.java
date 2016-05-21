/**
 * Copyright (C) 2016 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.dom4j.util;

import org.dom4j.*;
import org.dom4j.tree.BaseElement;

import java.util.Comparator;

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
public class NonLazyUserDataElement extends BaseElement {

    private Object data;

    public NonLazyUserDataElement( final String name ) {
        super( name );
        this.attributes = createAttributeList();
        this.content = createContentList();
    }

    public NonLazyUserDataElement(final QName qname) {
        super(qname);
        this.attributes = createAttributeList();
        this.content = createContentList();
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

    protected java.util.List<Node> createContentList() {
        return createContentList( 2 );
    }

    protected Object getCopyOfUserData() {
        return data;
    }

    protected Element createElement(final String name ) {
        final DocumentFactory factory = getDocumentFactory();
        final QName qnam = factory.createQName( name );
        return createElement( qnam );
    }

    protected Element createElement( final QName qName ) {
        final DocumentFactory factory  = getDocumentFactory();
        final Element ret = factory.createElement( qName );
        final Object dta = getCopyOfUserData();
        ret.setData( dta );
        return ret;
    }
    protected DocumentFactory getDocumentFactory() {
        return DocumentFactory.getInstance();
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

    public void appendAttributes( final Element src ) {
        for ( int i = 0, size = src.attributeCount(); i < size; i++) {
            final Attribute att = src.attribute( i );
            final Attribute attCln = ( Attribute )att.clone();
            add( attCln );
        }

    }

    public void appendContent( final Branch branch ) {
        final int size = branch.nodeCount();
        for ( int i = 0; i < size; i++ ) {
            final Node node = branch.node( i );
            final Node cln;
            if ( node.getNodeType() == Node.ELEMENT_NODE ) {
                cln = ( ( NonLazyUserDataElement )node ).cloneInternal();
            } else {
                cln = ( Node )node.clone();
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
        Element anstr = getParent();
        done : if ( anstr != null ) {
            final NamespaceNodeComparator nc = new NamespaceNodeComparator();
            final java.util.TreeSet<Namespace> nsSet = new java.util.TreeSet<Namespace>( nc );

            do {
                final java.util.List<Node> sibs = anstr.content();
                for ( final java.util.Iterator<Node> itr = sibs.iterator(); itr.hasNext(); ) {
                    final Node sib = itr.next();
                    if (sib instanceof Namespace)
                        nsSet.add((Namespace) sib);
                }
                anstr = anstr.getParent();
            } while ( anstr != null );

            if ( nsSet.isEmpty() )
                break done;

            for ( final java.util.Iterator<Namespace> itr = nsSet.iterator(); itr.hasNext(); ) {
                final Namespace ns = itr.next();
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
    public void setParent( final Element prnt ) {
        super.setParent( prnt );
        done : if ( prnt != null ) {
            final Namespace myNs = getNamespace();
            if ( myNs != Namespace.NO_NAMESPACE ) {
                final String myPfx = myNs.getPrefix();
                final Namespace prntNs = prnt.getNamespaceForPrefix( myPfx );
                if ( myPfx.equals( prntNs ) ) {
                    final String myNm = myNs.getName();
                    final QName newNm = new QName( myNm );
                    setQName( newNm );
                }
            }
            if ( content == null ) break done;
            for ( final java.util.Iterator itr = content.iterator(); itr.hasNext(); ) {
                final Node chld = ( Node )itr.next();
                if ( chld.getNodeType() != Node.NAMESPACE_NODE ) continue;

                final Namespace ns = ( Namespace )chld;
                final String pfx = ns.getPrefix();

                final Namespace prntNs = prnt.getNamespaceForPrefix( pfx );
                if ( ns.equals( prntNs ) ) itr.remove();
            }
        }
    }


    public Element createCopy() {
        final NonLazyUserDataElement ret = ( NonLazyUserDataElement )super.createCopy();
        ret.data = getCopyOfUserData();
        return ret;
    }

    public Element createCopy( final QName qName ) {
        final NonLazyUserDataElement ret = ( NonLazyUserDataElement )super.createCopy( qName );
        ret.data = getCopyOfUserData();
        return ret;
    }

    public Element createCopy( final String name ) {
        final NonLazyUserDataElement ret = ( NonLazyUserDataElement )super.createCopy( name );
        ret.data = getCopyOfUserData();
        return ret;
    }
}

class NamespaceNodeComparator implements Comparator<Namespace> {

    public int compare(Namespace n1, Namespace n2) {
        int answer = compare(n1.getURI(), n2.getURI());

        if (answer == 0) {
            answer = compare(n1.getPrefix(), n2.getPrefix());
        }

        return answer;
    }

    private int compare(String o1, String o2) {
        if (o1 == o2) {
            return 0;
        } else if (o1 == null) {
            // null is less
            return -1;
        } else if (o2 == null) {
            return 1;
        }

        return o1.compareTo(o2);
    }
}
