/**
 *  Copyright (C) 2005-2008 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.NamespaceSupport3;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.common.ValidationException;
import org.xml.sax.Locator;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;

/**
 * ContentHandler that adds ids on all the XForms elements which don't have any, and gathers namespace information on
 * XForms elements (xforms:* and xxforms:*).
 */
public class XFormsDocumentAnnotatorContentHandler extends ForwardingContentHandler {

    private int currentId = 1;
    private int level = 0;
    private int xformsInstanceLevel = -1;

    private Locator documentLocator;

    private final String containerNamespace;
    private final boolean portlet;

    private final Map namespaceMappings;
    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private final Map ids = new HashMap();
    private final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs();
    private final AttributesImpl reusableAttributes = new AttributesImpl();

    public XFormsDocumentAnnotatorContentHandler(ContentHandler contentHandler, ExternalContext externalContext, Map namespaceMappings) {
        super(contentHandler);

        this.containerNamespace = externalContext.getRequest().getContainerNamespace();
        this.portlet = "portlet".equals(externalContext.getRequest().getContainerType());

        this.namespaceMappings = namespaceMappings;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

        level++;

        if (xformsInstanceLevel >= 0) {
            // Don't generate ids within an XForms instance,
            super.startElement(uri, localname, qName, attributes);
        } else if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) || XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            // This is an XForms element

            final int idIndex = attributes.getIndex("id");
            final String newIdAttributeUnprefixed;
            final String newIdAttribute;
            if (idIndex == -1) {
                // Create a new "id" attribute, prefixing if needed
                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                newIdAttributeUnprefixed = "xforms-element-" + currentId;
                newIdAttribute = containerNamespace + newIdAttributeUnprefixed;
                newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, newIdAttribute);
                attributes = newAttributes;
            } else if (portlet) {
                // Then we must prefix the existing id
                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                newIdAttributeUnprefixed = newAttributes.getValue(idIndex);
                newIdAttribute = containerNamespace + newIdAttributeUnprefixed;
                newAttributes.setValue(idIndex, newIdAttribute);
                attributes = newAttributes;
            } else {
                // Keep existing id
                newIdAttributeUnprefixed = newIdAttribute = attributes.getValue(idIndex);
            }

            // Check for duplicate ids
            if (ids.get(newIdAttribute) != null) // TODO: create Element to provide more location info?
                throw new ValidationException("Duplicate id for XForms element: " + newIdAttributeUnprefixed,
                        new ExtendedLocationData(new LocationData(getDocumentLocator()), "analyzing control element", new String[] { "id", newIdAttributeUnprefixed }, false));

            // Remember that this id was used
            ids.put(newIdAttribute, "");

            // Gather namespace information
            if (namespaceMappings != null) {
                final Map namespaces = new HashMap();
                for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                    final String namespacePrefix = (String) e.nextElement();
                    if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                        namespaces.put(namespacePrefix, namespaceSupport.getURI(namespacePrefix));
                }

                namespaceMappings.put(newIdAttribute, namespaces);
            }

            currentId++;

            super.startElement(uri, localname, qName, attributes);

            if ("instance".equals(localname)) { // NOTE: this catches xforms:instance AND xxforms:instance (shouldn't be a problem...)
                // Remember we are inside an instance
                xformsInstanceLevel = level;
            }

        } else if (XMLConstants.XHTML_NAMESPACE_URI.equals(uri) && hostLanguageAVTs) {
            // This is an XHTML element

            final int attributesCount = attributes.getLength();
            if (attributesCount > 0) {
                for (int i = 0; i <attributesCount; i++) {
                    final String attributeValue = attributes.getValue(i);
                    if (attributeValue.indexOf('{') != -1) {
                        // This is an AVT
                        final String attributeName = attributes.getLocalName(i);

                        final String id = attributes.getValue("id");// TODO: create / update id if needed

                        reusableAttributes.clear();
                        reusableAttributes.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, id);
                        reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, attributeName);

                        super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
                        super.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", "xxforms:attribute", reusableAttributes);
                        super.endElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", "xxforms:attribute");
                        super.endPrefixMapping("xxforms");
                    }
                }
                // TODO: create / update id if needed
                super.startElement(uri, localname, qName, attributes);
            } else {
                super.startElement(uri, localname, qName, attributes);
            }
        } else {
            super.startElement(uri, localname, qName, attributes);
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        if (level == xformsInstanceLevel) {
            // Exiting xforms:instance
            xformsInstanceLevel = -1;
        }

        super.endElement(uri, localname, qName);
        level--;

        namespaceSupport.endElement();
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceSupport.startPrefixMapping(prefix, uri);
        super.startPrefixMapping(prefix, uri);
    }

    public void setDocumentLocator(Locator locator) {
        this.documentLocator = locator;
        super.setDocumentLocator(locator);
    }

    public Locator getDocumentLocator() {
        return documentLocator;
    }
}
