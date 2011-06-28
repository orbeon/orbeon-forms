/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This ContentHandler extracts XForms information from an XHTML document and creates a static state document.
 *
 * NOTE: This must be independent from the actual request (including request path, etc.) so the state can be reused
 * between different requests. Request information, if needed, must go into the dynamic state.
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
 * <static-state xmlns:xxforms="..." system-id="..." ...>
 *   <!-- E.g. AVT on xhtml:html -->
 *   <xxforms:attribute .../>
 *   <!-- E.g. xforms:output within xhtml:title -->
 *   <xforms:output .../>
 *   <!-- E.g. XBL component definitions -->
 *   <xbl:xbl .../>
 *   <xbl:xbl .../>
 *   <!-- Top-level models -->
 *   <xforms:model ...>
 *   <xforms:model ...>
 *   <!-- Controls including XBL-bound controls -->
 *   <xforms:group ...>
 *   <xforms:input ...>
 *   <foo:bar ...>
 *   <!-- Global properties -->
 *   <properties xxforms:noscript="true" .../>
 *   <!-- Last id used (for id generation in XBL after deserialization) -->
 *   <last-id id="123"/>
 * </static-state>
 */
public class XFormsExtractorContentHandler extends ForwardingXMLReceiver {

    public static final QName LAST_ID_QNAME = new QName("last-id");

    private Locator locator;
    private LocationData locationData;

    private Map<String, Object> properties = new HashMap<String, Object>();

    private int level;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private boolean mustOutputFirstElement = true;

    private final boolean isTopLevel;
    private final Metadata metadata;
    private final boolean ignoreRootElement;

    private static class XMLBaseLang {
        public final String id;
        public final URI xmlBase;
        public final String xmlLang;

        private XMLBaseLang(String id, URI xmlBase, String xmlLang) {
            this.id = id;
            this.xmlBase = xmlBase;
            this.xmlLang = xmlLang;
        }
    }

    private Stack<XMLBaseLang> xmlBaseLangStack = new Stack<XMLBaseLang>();

    private boolean inXFormsOrExtension;       // whether we are in a model
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in a schema, instance, or xbl:xbl
    private boolean inLHHA;         // whether we are in an LHHA element
    private int preserveOrLHHALevel;

    /**
     * Constructor for top-level document.
     *
     * @param xmlReceiver       resulting static state document
     * @param metadata          metadata
     */
    public XFormsExtractorContentHandler(XMLReceiver xmlReceiver, Metadata metadata) {
        super(xmlReceiver);

        this.isTopLevel = true;
        this.metadata = metadata;
        this.ignoreRootElement = false;

        // Create xml:base stack
        try {
            xmlBaseLangStack.push(new XMLBaseLang(null, new URI(null, null, ".", null), null));
        } catch (URISyntaxException e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    /**
     * Constructor for nested document (XBL templates).
     *
     * @param xmlReceiver       resulting static state document
     * @param metadata          metadata
     * @param ignoreRootElement whether root element must just be skipped
     * @param baseURI           base URI
     */
    public XFormsExtractorContentHandler(XMLReceiver xmlReceiver, Metadata metadata,
                                         boolean ignoreRootElement, String baseURI) {
        super(xmlReceiver);

        this.isTopLevel = false;
        this.metadata = metadata;
        this.ignoreRootElement = ignoreRootElement;

        try {
            assert baseURI != null;
            xmlBaseLangStack.push(new XMLBaseLang(null, new URI(null, null, baseURI, null), null));
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
        // Output non-default properties
        {
            final PropertySet propertySet = Properties.instance().getPropertySet();
            for (Iterator i = XFormsProperties.getPropertyDefinitionEntryIterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String propertyName = (String) currentEntry.getKey();
                final XFormsProperties.PropertyDefinition propertyDefinition = (XFormsProperties.PropertyDefinition) currentEntry.getValue();

                final Object defaultPropertyValue = propertyDefinition.getDefaultValue(); // value can be String, Boolean, Integer
                final Object actualPropertyValue = properties.get(propertyName); // value can be String, Boolean, Integer
                if (actualPropertyValue == null) {
                    // Property not defined in the document, try to obtain from global properties
                    final Object globalPropertyValue = propertySet.getObject(XFormsProperties.XFORMS_PROPERTY_PREFIX + propertyName, defaultPropertyValue);

                    // If the global property is different from the default, add it
                    if (!globalPropertyValue.equals(defaultPropertyValue)) {
                        propertyDefinition.validate(globalPropertyValue, locationData);
                        properties.put(propertyName, globalPropertyValue);
                    }

                } else {
                    // Property defined in the document

                    // If the property is identical to the default, remove it
                    if (actualPropertyValue.equals(defaultPropertyValue))
                        properties.remove(propertyName);
                    else
                        propertyDefinition.validate(actualPropertyValue, locationData);
                }
            }

            // Create attributes
            final AttributesImpl newAttributes = new AttributesImpl();
            for (final Map.Entry<String, Object> currentEntry : properties.entrySet()) {
                final String propertyName = currentEntry.getKey();
                newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, propertyName, "xxforms:" + propertyName, ContentHandlerHelper.CDATA, currentEntry.getValue().toString());
            }

            super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
            super.startElement("", "properties", "properties", newAttributes);
            super.endElement("", "properties", "properties");
            super.endPrefixMapping("xxforms");
        }

        if (isTopLevel) {
            // Remember the last id used for id generation. During state restoration, XBL components must start with this id.
            final AttributesImpl newAttributes = new AttributesImpl();
            newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, Integer.toString(metadata.idGenerator().getCurrentId()));
            final String lastIdName = LAST_ID_QNAME.getName();
            super.startElement("", lastIdName, lastIdName, newAttributes);
            super.endElement("", lastIdName, lastIdName);
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
        final boolean isXBL = XFormsConstants.XBL_NAMESPACE_URI.equals(uri);

        final boolean isExtension = metadata.isXBLBinding(uri, localname);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms || isXBL || isExtension;

        // Handle outer xml:base and xml:lang
        if (!inXFormsOrExtension) {
            final String xmlBaseAttribute = attributes.getValue(XMLConstants.XML_URI, "base");
            final String xmlLangAttribute = attributes.getValue(XMLConstants.XML_URI, "lang");
            if (xmlBaseAttribute == null && xmlLangAttribute == null) {
                xmlBaseLangStack.push(xmlBaseLangStack.peek());
            } else {
                final XMLBaseLang currentXMLBaseLang = xmlBaseLangStack.peek();

                final URI newBase;
                if (xmlBaseAttribute != null) {
                    try {
                        // Resolve
                        newBase = currentXMLBaseLang.xmlBase.resolve(new URI(xmlBaseAttribute)).normalize();// normalize to remove "..", etc.
                    } catch (URISyntaxException e) {
                        throw new ValidationException("Error creating URI from: '" + xmlBaseLangStack.peek() + "' and '" + xmlBaseAttribute + "'.", e, new LocationData(locator));
                    }
                } else {
                    newBase = currentXMLBaseLang.xmlBase;
                }

                final String newLang;
                if (xmlLangAttribute != null) {
                    // Override
                    newLang = xmlLangAttribute;
                } else {
                    newLang = currentXMLBaseLang.xmlLang;
                }

                xmlBaseLangStack.push(new XMLBaseLang(attributes.getValue("", "id"), newBase, newLang));
            }
        }

        // Handle properties of the form @xxforms:* when outside of models or controls
        if (!inXFormsOrExtension && !isXFormsOrExtension) {
            handleProperties(attributes);
        }

        if (level > 0 || !ignoreRootElement) {

            // Start extracting model or controls
            if (!inXFormsOrExtension && isXFormsOrExtension) {

                inXFormsOrExtension = true;
                xformsLevel = level;

                // Handle properties on top-level model elements
                if (isXForms && localname.equals("model")) {
                    handleProperties(attributes);
                }

                outputFirstElementIfNeeded();

                // Add xml:base on element
                attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

                // Add xml:lang on element if found
                final String xmlLang = xmlBaseLangStack.peek().xmlLang;
                if (xmlLang != null) {
                    final String newXMLLang;
                    if (XFormsUtils.maybeAVT(xmlLang)) {
                        // In this case there is a control representing the AVT
                        // Put a special value for xml:lang so we know where to find the dynamic value
                        newXMLLang = "#" + xmlBaseLangStack.peek().id;
                    } else {
                        // No AVT
                        newXMLLang = xmlLang;
                    }

                    attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "lang", newXMLLang);
                }

                sendStartPrefixMappings();
            }

            // Check for preserved content
            if (inXFormsOrExtension && !inPreserve) {
                // TODO: Just warn?
                if (isXXForms) {
                    // Check that we are getting a valid xxforms:* element
                    if (!XFormsConstants.ALLOWED_XXFORMS_ELEMENTS.contains(localname) && !XFormsActions.isActionName(XFormsConstants.XXFORMS_NAMESPACE_URI, localname))
                        throw new ValidationException("Invalid extension element in XForms document: " + qName, new LocationData(locator));
                } else if (isEXForms) {
                    // Check that we are getting a valid exforms:* element
                    if (!XFormsConstants.ALLOWED_EXFORMS_ELEMENTS.contains(localname))
                        throw new ValidationException("Invalid eXForms element in XForms document: " + qName, new LocationData(locator));
                } else if (isXBL) {
                    // Check that we are getting a valid xbl:* element
                    if (!XFormsConstants.ALLOWED_XBL_ELEMENTS.contains(localname))
                        throw new ValidationException("Invalid XBL element in XForms document: " + qName, new LocationData(locator));
                }

                // Preserve as is the content of labels, etc., instances, and schemas
                if (!inLHHA) {
                    if (XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.contains(localname) && isXForms) {// labels, etc. may contain XHTML)
                        inLHHA = true;
                        preserveOrLHHALevel = level;
                    } else if ("instance".equals(localname) && isXForms                         // XForms instance
                            || "schema".equals(localname) && XMLConstants.XSD_URI.equals(uri)   // XML schema
                            || "xbl".equals(localname) && isXBL // preserve everything under xbl:xbl so that templates may be processed by static state
                            || isExtension) {
                        inPreserve = true;
                        preserveOrLHHALevel = level;
                    }
                }

                // Callback for elements of interest
                if (isXFormsOrExtension || inLHHA) {
                    // NOTE: We call this also for HTML elements within LHHA so we can gather scope information for AVTs
                    startXFormsOrExtension(uri, localname, qName, attributes);
                }
            }

            // We are within preserved content or we output regular XForms content
            if (inXFormsOrExtension && (inPreserve || inLHHA || isXFormsOrExtension)) {
                super.startElement(uri, localname, qName, attributes);
            }
        } else {
            // Just open the root element
            outputFirstElementIfNeeded();
            sendStartPrefixMappings();
            super.startElement(uri, localname, qName, attributes);
        }

        level++;
    }

    private String getCurrentBaseURI() {
        final URI currentXMLBaseURI = xmlBaseLangStack.peek().xmlBase;
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

        // Check for XForms or extension namespaces
        // TODO: use stack and avoid redoing all the tests on endElement()
        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXBL = XFormsConstants.XBL_NAMESPACE_URI.equals(uri);

        final boolean isExtension = metadata.isXBLBinding(uri, localname);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms || isXBL || isExtension;

        if (level > 0 || !ignoreRootElement) {
            // We are within preserved content or we output regular XForms content
            if (inXFormsOrExtension && (inPreserve || inLHHA || isXFormsOrExtension)) {
                super.endElement(uri, localname, qName);
            }

            if ((inPreserve || inLHHA) && level == preserveOrLHHALevel) {
                // Leaving preserved content
                inPreserve = false;
                inLHHA = false;
            }

            if (inXFormsOrExtension && !inPreserve) {
                // Callback for elements of interest
                if (isXFormsOrExtension || inLHHA) {
                    endXFormsOrExtension(uri, localname, qName);
                }
            }

            if (inXFormsOrExtension && level == xformsLevel) {
                // Leaving model or controls
                inXFormsOrExtension = false;
                sendEndPrefixMappings();
            }
        } else {
            // Just close the root element
            super.endElement(uri, localname, qName);
            sendEndPrefixMappings();
        }

        if (!inXFormsOrExtension) {
            xmlBaseLangStack.pop();
        }

        namespaceSupport.endElement();
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (inPreserve) {
            super.characters(chars, start, length);
        } else {

            // TODO: we must not output characters here if we are not directly within an XForms element
            // See: http://forge.objectweb.org/tracker/index.php?func=detail&aid=310835&group_id=168&atid=350207
            if (inXFormsOrExtension) // TODO: check this: only keep spaces within XForms elements that require it in order to reduce the size of the static state
                super.characters(chars, start, length);
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceSupport.startPrefixMapping(prefix, uri);
        if (inXFormsOrExtension)
            super.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (inXFormsOrExtension)
            super.endPrefixMapping(s);
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    protected void startXFormsOrExtension(String uri, String localname, String qName, Attributes attributes) {
        // NOP
    }

    protected void endXFormsOrExtension(String uri, String localname, String qName) {
        // NOP
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        // NOP
    }

    @Override
    public void endDTD() throws SAXException {
        // NOP
    }

    @Override
    public void startEntity(String name) throws SAXException {
        // NOP
    }

    @Override
    public void endEntity(String name) throws SAXException {
        // NOP
    }

    @Override
    public void startCDATA() throws SAXException {
        // NOP
    }

    @Override
    public void endCDATA() throws SAXException {
        // NOP
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (inPreserve) {
            super.comment(ch, start, length);
        }
    }

    private void handleProperties(Attributes attributes) {
        final int attributesCount = attributes.getLength();
        for (int i = 0; i < attributesCount; i++) {
            final String attributeURI = attributes.getURI(i);
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attributeURI)) {
                // Found xxforms:* attribute
                addProperty(attributes.getLocalName(i), attributes.getValue(i));
            }
        }
    }

    private void addProperty(String name, String stringValue) {

        final Object propertyValue = XFormsProperties.parseProperty(name, stringValue);
        if (propertyValue == null) {
            // Invalid property or other problem
            return;
        }

        if (properties.get(name) != null) {
            // Property by this name already specified, ignore it as we take the first occurrence into account
            return;
        }

        properties.put(name, propertyValue);
    }
}
