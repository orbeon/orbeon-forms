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
 * ContentHandler that:
 *
 * o adds ids on all the XForms elements which don't have any
 * o gathers namespace information on XForms elements (xforms:* and xxforms:*).
 * o gathers ids of XHTML elements with AVTs
 *
 * TODO: Should combine this with XFormsExtractorContentHandler?
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
    private final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
    private final AttributesImpl reusableAttributes = new AttributesImpl();
    private final String[] reusableStringArray = new String[1];

    /**
     * This constructor just computes the namespace mappings and AVT elements
     *
     * @param namespaceMappings     Map<String, Map<String, String>> of control id to Map of namespace mappings
     */
    public XFormsDocumentAnnotatorContentHandler(Map namespaceMappings) {

        // In this mode, all elements that need to have ids already have them, so set safe defaults
        this.containerNamespace = "";
        this.portlet = false;

        this.namespaceMappings = namespaceMappings;
    }

    /**
     * This constructor just computes the namespace mappings and AVT elements
     *
     * @param contentHandler        ContentHandler
     * @param externalContext       ExternalContext
     * @param namespaceMappings     Map<String, Map<String, String>> of control id to Map of namespace mappings
     */
    public XFormsDocumentAnnotatorContentHandler(ContentHandler contentHandler, ExternalContext externalContext, Map namespaceMappings) {
        super(contentHandler, contentHandler != null);

        // In this mode, elements may not yet have ids, and existing ids may have to get prefixed
        this.containerNamespace = externalContext.getRequest().getContainerNamespace();
        this.portlet = "portlet".equals(externalContext.getRequest().getContainerType());

        this.namespaceMappings = namespaceMappings;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

        level++;

        if (xformsInstanceLevel >= 0) {
            // Don't generate ids within an XForms instance
            super.startElement(uri, localname, qName, attributes);
        } else if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) || XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            // This is an XForms element

            // Create a new id and update the attributes if needed
            attributes = getAttributesGatherNamespaces(attributes, reusableStringArray);

            super.startElement(uri, localname, qName, attributes);

            if ("instance".equals(localname)) { // NOTE: this catches xforms:instance AND xxforms:instance (shouldn't be a problem...)
                // Remember we are inside an instance
                xformsInstanceLevel = level;
            }

        } else if (XMLConstants.XHTML_NAMESPACE_URI.equals(uri) && hostLanguageAVTs) {
            // This is an XHTML element and we allow AVTs

            final int attributesCount = attributes.getLength();
            if (attributesCount > 0) {
                String htmlElementId = null;
                for (int i = 0; i < attributesCount; i++) {
                    final String currentAttributeURI = attributes.getURI(i);
                    if ("".equals(currentAttributeURI) || XMLConstants.XML_URI.equals(currentAttributeURI)) {
                        // For now we only support AVTs on attributes in no namespace or in the XML namespace (for xml:lang)
                        final String attributeValue = attributes.getValue(i);
                        if (attributeValue.indexOf('{') != -1) {
                            // This is an AVT
                            final String attributeName = attributes.getQName(i);// use qualified name for xml:lang

                            // Create a new id and update the attributes if needed
                            if (htmlElementId == null) {
                                attributes = getAttributesGatherNamespaces(attributes, reusableStringArray);
                                htmlElementId = reusableStringArray[0];

                                // TODO: Clear all attributes having AVTs or XPath expressions will end up in repeat templates.

                                // Output the element with the new or updated id attribute
                                super.startElement(uri, localname, qName, attributes);
                            }

                            // Create a new xxforms:attribute control
                            reusableAttributes.clear();

                            final AttributesImpl newAttributes = (AttributesImpl) getAttributesGatherNamespaces(reusableAttributes, reusableStringArray);

                            newAttributes.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, htmlElementId);
                            newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, attributeName);
                            newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, attributeValue);

                            super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
                            super.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", "xxforms:attribute", newAttributes);
                            super.endElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", "xxforms:attribute");
                            super.endPrefixMapping("xxforms");
                        }
                    }
                }

                // Output the element as is if no AVT was found
                if (htmlElementId == null)
                    super.startElement(uri, localname, qName, attributes);

            } else {
                // No attributes, just output the element
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

    private Attributes getAttributesGatherNamespaces(Attributes attributes, String[] newIdAttribute) {
        final int idIndex = attributes.getIndex("id");
        final String newIdAttributeUnprefixed;
        if (idIndex == -1) {
            // Create a new "id" attribute, prefixing if needed
            final AttributesImpl newAttributes = new AttributesImpl(attributes);
            newIdAttributeUnprefixed = "xforms-element-" + currentId;
            newIdAttribute[0] = containerNamespace + newIdAttributeUnprefixed;
            newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, newIdAttribute[0]);
            attributes = newAttributes;
        } else if (portlet) {
            // Then we must prefix the existing id
            final AttributesImpl newAttributes = new AttributesImpl(attributes);
            newIdAttributeUnprefixed = newAttributes.getValue(idIndex);
            newIdAttribute[0] = containerNamespace + newIdAttributeUnprefixed;
            newAttributes.setValue(idIndex, newIdAttribute[0]);
            attributes = newAttributes;
        } else {
            // Keep existing id
            newIdAttributeUnprefixed = newIdAttribute[0] = attributes.getValue(idIndex);
        }

        // Check for duplicate ids
        if (ids.get(newIdAttribute[0]) != null) // TODO: create Element to provide more location info?
            throw new ValidationException("Duplicate id for XForms element: " + newIdAttributeUnprefixed,
                    new ExtendedLocationData(new LocationData(getDocumentLocator()), "analyzing control element", new String[] { "id", newIdAttributeUnprefixed }, false));

        // Remember that this id was used
        ids.put(newIdAttribute[0], "");

        // Gather namespace information
        if (namespaceMappings != null) {
            final Map namespaces = new HashMap();
            for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                    namespaces.put(namespacePrefix, namespaceSupport.getURI(namespacePrefix));
            }
            // Re-add standard "xml" prefix mapping
            namespaces.put(XMLConstants.XML_PREFIX, XMLConstants.XML_URI);
            namespaceMappings.put(newIdAttribute[0], namespaces);
        }

        currentId++;

        return attributes;
    }
}
