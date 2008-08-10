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

import org.orbeon.oxf.xml.*;
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
 * o adds ids on all the XForms elements which don't have one
 * o gathers namespace information on XForms elements (xforms:*, xxforms:*, exforms:*, xbl:*).
 * o finds AVTs on non-XForms elements
 *   o adds ids to those elements
 *   o produces xxforms:attribute elements
 * o finds title information and produces xxforms:text elements
 *
 * NOTE: There was a thought of merging this with XFormsExtractorContentHandler but we need a separate annotated
 * document in XFormsToXHTML to produce the output. Since the handlers use the XForms and XHTML element ids, it doesn't
 * seem that we can separate the steps.
 */
public class XFormsDocumentAnnotatorContentHandler extends ForwardingContentHandler {

    private int currentId = 1;
    private int level = 0;
    private boolean inHead;
    private boolean inTitle;

    private Locator documentLocator;

    private final String containerNamespace;
    private final boolean portlet;

    private final Map namespaceMappings;
    private final boolean isGenerateIds;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private final Map ids = new HashMap();
    private final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
    private final AttributesImpl reusableAttributes = new AttributesImpl();
    private final String[] reusableStringArray = new String[1];

    private String htmlTitleElementId;

    private boolean inXForms;       // whether we are in a model
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in a label, etc., schema or instance
    private int preserveLevel;

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
        this.isGenerateIds = false;
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
        this.isGenerateIds = true;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

        // Check for XForms or extension namespaces
        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXBL = XFormsConstants.XBL_NAMESPACE_URI.equals(uri);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms || isXBL;

        // Entering model or controls
        if (!inXForms && isXFormsOrExtension) {
            inXForms = true;
            xformsLevel = level;
        }

        if (inPreserve) {
            // Don't do anything fancy
            super.startElement(uri, localname, qName, attributes);
        } else if (isXFormsOrExtension) {
            // This is an XForms element

            // TODO: do we actually need to add ids / gather namespaces to xbl:*?
            // TODO: can we restrain gathering ids / namespaces to only certain elements (all controls + elements with XPath expressions + models + instances)?

            // Create a new id and update the attributes if needed
            attributes = getAttributesGatherNamespaces(attributes, reusableStringArray);

            if (inTitle && "output".equals(localname)) {
                // Special case of xforms:output within title, which produces an xxforms:text control
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "for", htmlTitleElementId);
                super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
                super.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "text", "xxforms:text", attributes);
            } else {
                // Leave element untouched (except for the id attribute)
                super.startElement(uri, localname, qName, attributes);
            }

        } else {
            // Non-XForms element

            String htmlElementId = null;

            if (level == 1) {
                if ("head".equals(localname)) {
                    // Entering head
                    inHead = true;
                }
            } else if (level == 2) {
                if (inHead && "title".equals(localname)) {
                    // Entering title
                    inTitle = true;
                    // Make sure there will be an id on the title element (ideally, we would do this only if there is a nested xforms:output)
                    attributes = getAttributesGatherNamespaces(attributes, reusableStringArray);
                    htmlElementId = reusableStringArray[0];
                    htmlTitleElementId = htmlElementId;
                }
            }

            if (hostLanguageAVTs) {
                // This is a non-XForms element and we allow AVTs
                final int attributesCount = attributes.getLength();
                if (attributesCount > 0) {
                    boolean elementOutput = false;
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
                                }

                                if (!elementOutput) {
                                    // Output the element with the new or updated id attribute
                                    super.startElement(uri, localname, qName, attributes);
                                    elementOutput = true;
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
                    if (!elementOutput)
                        super.startElement(uri, localname, qName, attributes);
                } else {
                    super.startElement(uri, localname, qName, attributes);
                }

            } else {
                // No attributes, just output the element
                super.startElement(uri, localname, qName, attributes);
            }
        }

        // Check for preserved content
        if (inXForms && !inPreserve) {
            // Preserve as is the content of labels, etc., instances, and schemas
            if ((XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.get(localname) != null       // labels, etc. may contain XHTML
                    || "instance".equals(localname)) && isXForms                            // XForms instances
                    || "schema".equals(localname) && XMLConstants.XSD_URI.equals(uri)) {    // XML schemas
                inPreserve = true;
                preserveLevel = level;
            }
        }

        level++;
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        level--;

        if (inPreserve && level == preserveLevel) {
            // Leaving preserved content
            inPreserve = false;
        } if (inXForms && level == xformsLevel) {
            // Leaving model or controls
            inXForms = false;
        }

        if (level == 1) {
            if ("head".equals(localname)) {
                // Exiting head
                inHead = false;
            }
        } else if (level == 2) {
            if ("title".equals(localname)) {
                // Exiting title
                inTitle = false;
            }
        }

        if (inTitle && XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) && "output".equals(localname)) {
            // Closing xforms:output within xhtml:title
            super.endElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "text", "xxforms:text");
            super.endPrefixMapping("xxforms");// for resolving appearance
        } else {
            // Leave element untouched
            super.endElement(uri, localname, qName);
        }

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
        if (isGenerateIds) {
            // Process ids
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

            currentId++;
        } else {
            // Don't process ids
            newIdAttribute[0] = attributes.getValue(idIndex);
        }

        // Gather namespace information if there is an id
        if (namespaceMappings != null && (isGenerateIds || idIndex != -1)) {
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

        return attributes;
    }
}
