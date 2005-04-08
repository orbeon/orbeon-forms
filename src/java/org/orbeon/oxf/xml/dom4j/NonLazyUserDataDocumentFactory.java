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

import org.dom4j.DocumentFactory;
import org.dom4j.util.UserDataAttribute;

/**
 * @see NonLazyUserDataElement
 */
public class NonLazyUserDataDocumentFactory extends org.dom4j.DocumentFactory {
    
    private static final NonLazyUserDataDocumentFactory singleton 
        = new NonLazyUserDataDocumentFactory();

    /**
     * This would return NonLazyUserDataDocumentFactory however silly 1.4 compiler complains 
     * claiming that it is an incompatible override of DocumentFactory.getInstance. ( Static's
     * cannot over-ride, sigh. )  So this method just here to be safe and 
     * getInstance( NonLazyUserDataDocumentFactory ) is what should really be used.
     */
    public static DocumentFactory getInstance() {
        return singleton;
    }
    /**
     * @param   notUsed.  This parameter is ignored.  It is just present so that the compiler won't
     *          consider this an ( incompatible ) override of getInstance().
     */
    public static NonLazyUserDataDocumentFactory getInstance
    ( final NonLazyUserDataDocumentFactory notUsed ) {
        return singleton;
    }

    public org.dom4j.Element createElement( final org.dom4j.QName qname ) {
        return new NonLazyUserDataElement( qname );
    }

    public org.dom4j.Attribute createAttribute
    ( final org.dom4j.Element own, final org.dom4j.QName qnam, final String val ) {
        return new UserDataAttribute( qnam, val );
    }
    
}

