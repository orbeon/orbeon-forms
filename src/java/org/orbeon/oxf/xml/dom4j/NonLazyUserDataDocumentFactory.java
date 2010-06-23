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

import org.dom4j.Attribute;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.util.UserDataAttribute;

/**
 *
 */
public class NonLazyUserDataDocumentFactory extends DocumentFactory {

    private static final NonLazyUserDataDocumentFactory SINGLETON = new NonLazyUserDataDocumentFactory();

    public static DocumentFactory getInstance() {
        return SINGLETON;
    }

    public Element createElement(final QName qName) {
        return new NonLazyUserDataElement(qName);
    }

    public Attribute createAttribute(final Element ownerElement, final QName qName, final String value) {
        return new UserDataAttribute(qName, value);
    }
}
