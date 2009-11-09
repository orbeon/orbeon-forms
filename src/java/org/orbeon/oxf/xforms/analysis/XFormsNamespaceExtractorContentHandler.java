/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.NamespaceSupport3;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * ContentHandler that gathers namespace information on XForms elements (xforms:* and xxforms:*).
 */
public class XFormsNamespaceExtractorContentHandler extends ForwardingContentHandler {

    private int level = 0;
    private int xformsInstanceLevel = -1;

    private final Map<String, Map<String, Object>> namespaceMappings;
    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

//    private final Map ids = new HashMap();

    public XFormsNamespaceExtractorContentHandler(Map<String, Map<String, Object>> namespaceMappings) {
        this.namespaceMappings = namespaceMappings;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

        level++;

        if (xformsInstanceLevel >= 0) {
            // Don't check ids within an XForms instance,
        } else if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) || XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            // This is an XForms element

            final int idIndex = attributes.getIndex("id");
            if (idIndex != -1) {
                // There is an id attribute
                final String idAttribute = attributes.getValue(idIndex);

                // Remember that this id was used
//                ids.put(idAttribute, "");

                // Gather namespace information
                final Map<String, Object> namespaces = new HashMap<String, Object>();
                for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                    final String namespacePrefix = (String) e.nextElement();
                    if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                        namespaces.put(namespacePrefix, namespaceSupport.getURI(namespacePrefix));
                }

                namespaceMappings.put(idAttribute, namespaces);
            }

            if ("instance".equals(localname)) { // NOTE: this catches xforms:instance AND xxforms:instance (shouldn't be a problem...)
                // Remember we are inside an instance
                xformsInstanceLevel = level;
            }
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        if (level == xformsInstanceLevel) {
            // Exiting xforms:instance
            xformsInstanceLevel = -1;
        }

        level--;

        namespaceSupport.endElement();
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceSupport.startPrefixMapping(prefix, uri);
    }
}

