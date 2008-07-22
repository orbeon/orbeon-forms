/**
 *  Copyright (C) 2008 Orbeon, Inc.
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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.servlet.OPSXFormsFilter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This ContentHandler extracts XForms models and controls from an XHTML document and creates a static state document.
 *
 * The static state document contains only models and controls, without interleaved XHTML elements in order to save
 * memory and to facilitate visiting controls. The exceptions are:
 *
 * o The content of inline XForms instances (xforms:instance)
 * o The content of inline XML Schemas (xs:schema)
 * o The content of xforms:label, xforms:hint, xforms:help, xforms:alert (as they can contain XHTML)
 *
 * Notes:
 *
 * o xml:base attributes are added on the models and root control elements.
 * o XForms controls and AVTs are also extracted outside the HTML body.
 *
 * We try to keep this ContentHandler simple. Nested models and script elements are extracted by XFormsStaticState.
 *
 * Structure:
 *
 * <static-state xmlns:xxforms="..." xml:base="..." container-type="servlet" container-namespace="">
 *   <!-- E.g. AVT on xhtml:html -->
 *   <xxforms:attribute .../>
 *   <!-- E.g. xforms:output within xhtml:title -->
 *   <xforms:output .../>
 *   <!-- Models -->
 *   <xforms:model ...>
 *   <xforms:model ...>
 *   <!-- Controls -->
 *   <xforms:group ...>
 *   <xforms:input ...>
 *   <!-- Global properties -->
 *   <properties xxforms:noscript="true" .../>
 * </static-state>
 */
public class XFormsExtractorContentHandler extends ForwardingContentHandler {

    private static final Map ALLOWED_XXFORMS_ELEMENTS = new HashMap();
    static {
        ALLOWED_XXFORMS_ELEMENTS.put(XFormsActions.XXFORMS_SCRIPT_ACTION, "");
        ALLOWED_XXFORMS_ELEMENTS.put(XFormsActions.XXFORMS_SHOW_ACTION, "");
        ALLOWED_XXFORMS_ELEMENTS.put(XFormsActions.XXFORMS_HIDE_ACTION, "");
        ALLOWED_XXFORMS_ELEMENTS.put(XFormsActions.XXFORMS_ONLINE_ACTION, "");
        ALLOWED_XXFORMS_ELEMENTS.put(XFormsActions.XXFORMS_OFFLINE_ACTION, "");
        ALLOWED_XXFORMS_ELEMENTS.put(XFormsActions.XXFORMS_OFFLINE_SAVE_ACTION, "");
        ALLOWED_XXFORMS_ELEMENTS.put("dialog", "");
        ALLOWED_XXFORMS_ELEMENTS.put("variable", "");
        ALLOWED_XXFORMS_ELEMENTS.put("attribute", "");
        ALLOWED_XXFORMS_ELEMENTS.put("context", "");
        ALLOWED_XXFORMS_ELEMENTS.put("size", "");//xforms:upload/xxforms:size
    }

    private static final Map ALLOWED_EXFORMS_ELEMENTS = new HashMap();
    static {
        ALLOWED_EXFORMS_ELEMENTS.put("variable", "");
    }

    private static final Map LABEL_HINT_HELP_ALERT_ELEMENT = new HashMap();
    static {
        LABEL_HINT_HELP_ALERT_ELEMENT.put("label", "");
        LABEL_HINT_HELP_ALERT_ELEMENT.put("hint", "");
        LABEL_HINT_HELP_ALERT_ELEMENT.put("help", "");
        LABEL_HINT_HELP_ALERT_ELEMENT.put("alert", "");
    }

    private Locator locator;
    private LocationData locationData;

    private Map properties = new HashMap();

    private int level;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private boolean mustOutputFirstElement = true;

    private final ExternalContext externalContext;
    private Stack xmlBaseStack = new Stack();

    private boolean inXForms;       // whether we are in a model
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in a label, etc., schema or instance
    private int preserveLevel;

    public XFormsExtractorContentHandler(PipelineContext pipelineContext, ContentHandler contentHandler) {
        super(contentHandler);

        this.externalContext = ((ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT));

        // Create xml:base stack
        try {
            final String rootXMLBase;
            {
                // It is possible to override the base URI by setting a request attribute. This is used by OPSXFormsFilter.
                final String rendererBaseURI = (String) externalContext.getRequest().getAttributesMap().get(OPSXFormsFilter.OPS_XFORMS_RENDERER_BASE_URI_ATTRIBUTE_NAME);
                if (rendererBaseURI != null)
                    rootXMLBase = rendererBaseURI;
                else
                    rootXMLBase = externalContext.getRequest().getRequestPath();
            }
            xmlBaseStack.push(new URI(null, null, rootXMLBase, null));
        } catch (URISyntaxException e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void startDocument() throws SAXException {
        super.startDocument();
    }

    private void outputFirstElementIfNeeded() throws SAXException {
        if (mustOutputFirstElement) {
            final AttributesImpl attributesImpl = new AttributesImpl();

            // Add xml:base attribute
            attributesImpl.addAttribute(XMLConstants.XML_URI, "base", "xml:base", ContentHandlerHelper.CDATA, externalContext.getResponse().rewriteRenderURL(((URI) xmlBaseStack.get(0)).toString()));
            // Add container-type attribute
            attributesImpl.addAttribute("", "container-type", "container-type", ContentHandlerHelper.CDATA, externalContext.getRequest().getContainerType());
            // Add container-namespace attribute
            attributesImpl.addAttribute("", "container-namespace", "container-namespace", ContentHandlerHelper.CDATA, externalContext.getRequest().getContainerNamespace());

            // Add location information
            if (locationData != null) {
                attributesImpl.addAttribute("", "system-id", "system-id", ContentHandlerHelper.CDATA, locationData.getSystemID());
                attributesImpl.addAttribute("", "line", "line", ContentHandlerHelper.CDATA, Integer.toString(locationData.getLine()));
                attributesImpl.addAttribute("", "column", "column", ContentHandlerHelper.CDATA, Integer.toString(locationData.getCol()));
            }

            super.startElement("", "static-state", "static-state", attributesImpl);
            mustOutputFirstElement = false;
        }
    }

    public void endDocument() throws SAXException {

        outputFirstElementIfNeeded();

        // Output global properties
        if (properties.size() > 0) {
            final AttributesImpl newAttributes = new AttributesImpl();
            for (Iterator i = properties.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String propertyName = (String) currentEntry.getKey();
                newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, propertyName, "xxforms:" + propertyName, ContentHandlerHelper.CDATA, (String) currentEntry.getValue());
            }

            super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
            super.startElement("", "properties", "properties", newAttributes);
            super.endElement("", "properties", "properties");
            super.endPrefixMapping("xxforms");
        }

        super.endElement("", "static-state", "static-state");
        super.endDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

        // Handle location data
        if (locationData == null && locator != null && mustOutputFirstElement) {
            final String systemId = locator.getSystemId();
            if (systemId != null) {
                locationData = new LocationData(systemId, locator.getLineNumber(), locator.getColumnNumber());
            }
        }

        // Check for XForms or extension namespaces
        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms;

        // Handle xml:base
        if (!inXForms) {
            final String xmlBaseAttribute = attributes.getValue(XMLConstants.XML_URI, "base");
            if (xmlBaseAttribute == null) {
                xmlBaseStack.push(xmlBaseStack.peek());
            } else {
                try {
                    final URI currentXMLBaseURI = (URI) xmlBaseStack.peek();
                    xmlBaseStack.push(currentXMLBaseURI.resolve(new URI(xmlBaseAttribute)));
                } catch (URISyntaxException e) {
                    throw new ValidationException("Error creating URI from: '" + xmlBaseStack.peek() + "' and '" + xmlBaseAttribute + "'.", e, new LocationData(locator));
                }
            }
        }

        // Handle properties of the form @xxforms:* when outside of models or controls
        if (!inXForms && !isXFormsOrExtension) {
            final int attributesCount = attributes.getLength();
            for (int i = 0; i < attributesCount; i++) {
                final String attributeURI = attributes.getURI(i);
                if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attributeURI)) {
                    // Found xxforms:* attribute
                    final String attributeLocalName = attributes.getLocalName(i);
                    // Only take the first occurrence into account, and make sure the property is supported
                    if (properties.get(attributeLocalName) == null && XFormsProperties.getPropertyDefinition(attributeLocalName) != null) {
                        properties.put(attributeLocalName, attributes.getValue(i));
                    }
                }
            }
        }

        // Start extracting model or controls
        if (!inXForms && isXFormsOrExtension) {

            inXForms = true;
            xformsLevel = level;

            outputFirstElementIfNeeded();

            // Add xml:base on element
            attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

            sendStartPrefixMappings();
        }

        // Check for preserved content
        if (inXForms && !inPreserve) {

            if (isXXForms) {
                // Check that we are getting a valid xxforms:* element if used in body
                if (ALLOWED_XXFORMS_ELEMENTS.get(localname) == null)
                    throw new ValidationException("Invalid element in XForms document: xxforms:" + localname, new LocationData(locator));
            } else if (isEXForms) {
                // Check that we are getting a valid exforms:* element if used in body
                if (ALLOWED_EXFORMS_ELEMENTS.get(localname) == null)
                    throw new ValidationException("Invalid element in XForms document: exforms:" + localname, new LocationData(locator));
            }

            // Preserve as is the content of labels, etc., instances, and schemas
            if (LABEL_HINT_HELP_ALERT_ELEMENT.get(localname) != null || "instance".equals(localname)
                    || "schema".equals(localname) && XMLConstants.XSD_URI.equals(uri)) {
                inPreserve = true;
                preserveLevel = level;
            }
        }

        // We are within preserved content or we output regular XForms content
        if (inXForms && (inPreserve || isXFormsOrExtension)) {
            super.startElement(uri, localname, qName, attributes);
        }

        level++;
    }

    private String getCurrentBaseURI() {
        final URI currentXMLBaseURI = (URI) xmlBaseStack.peek();
        return currentXMLBaseURI.toString();
    }

    private void sendStartPrefixMappings() throws SAXException {
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            final String namespacePrefix = (String) e.nextElement();
            final String namespaceURI = namespaceSupport.getURI(namespacePrefix);
            if (!namespacePrefix.startsWith("xml"))
                super.startPrefixMapping(namespacePrefix, namespaceURI);
        }
    }

    private void sendEndPrefixMappings() throws SAXException {
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            final String namespacePrefix = (String) e.nextElement();
            if (!namespacePrefix.startsWith("xml"))
                super.endPrefixMapping(namespacePrefix);
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        level--;

        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms;

        // We are within preserved content or we output regular XForms content
        if (inXForms && (inPreserve || isXFormsOrExtension)) {
            super.endElement(uri, localname, qName);
        }

        if (inPreserve && level == preserveLevel) {
            // Leaving preserved content
            inPreserve = false;
        } if (inXForms && level == xformsLevel) {
            // Leaving model
            inXForms = false;
            sendEndPrefixMappings();
        }

        if (!inXForms) {
            xmlBaseStack.pop();
        }

        namespaceSupport.endElement();
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (inPreserve)
            super.characters(chars, start, length);
        else if (inXForms) // TODO: check this: only keep spaces within XForms elements that require it in order to reduce the size of the static state
            super.characters(chars, start, length);
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceSupport.startPrefixMapping(prefix, uri);
        if (inXForms)
            super.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (inXForms)
            super.endPrefixMapping(s);
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }
}
