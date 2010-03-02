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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.servlet.OrbeonXFormsFilter;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * This ContentHandler extracts XForms information from an XHTML document and creates a static state document.
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
 * <static-state xmlns:xxforms="..." xml:base="..." deployment="integrated" context-path="/orbeon" container-type="servlet" container-namespace="">
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
public class XFormsExtractorContentHandler extends ForwardingContentHandler {

    public static final QName LAST_ID_QNAME = new QName("last-id");

    private Locator locator;
    private LocationData locationData;

    private Map<String, String> properties = new HashMap<String, String>();

    private int level;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private boolean mustOutputFirstElement = true;

    private final boolean isTopLevel;
    private final ExternalContext externalContext;
    private final XFormsAnnotatorContentHandler.Metadata metadata;
    private final boolean ignoreRootElement;

    private Stack<URI> xmlBaseStack = new Stack<URI>();
    private XFormsConstants.DeploymentType deploymentType;
    private String requestContextPath;

    private boolean inXFormsOrExtension;       // whether we are in a model
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in a label, etc., schema or instance
    private int preserveLevel;

    /**
     * Constructor for top-level document.
     *
     * @param externalContext   external context to obtain request path, properties, etc.
     * @param contentHandler    resulting static state document
     * @param metadata          metadata
     */
    public XFormsExtractorContentHandler(ExternalContext externalContext, ContentHandler contentHandler,
                                         XFormsAnnotatorContentHandler.Metadata metadata) {
        super(contentHandler);

        this.isTopLevel = true;
        this.externalContext = externalContext;
        this.metadata = metadata;
        this.ignoreRootElement = false;

        final ExternalContext.Request request = externalContext.getRequest();

        // Remember if filter provided separate deployment information
        final String rendererDeploymentType = (String) request.getAttributesMap().get(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_ATTRIBUTE_NAME);
        deploymentType = "separate".equals(rendererDeploymentType) ? XFormsConstants.DeploymentType.separate
                    : "integrated".equals(rendererDeploymentType) ? XFormsConstants.DeploymentType.integrated
                    : XFormsConstants.DeploymentType.plain;

        // Try to get request context path
        requestContextPath = request.getClientContextPath("/");

        // Create xml:base stack
        try {
            final String rootXMLBase;
            {
                // It is possible to override the base URI by setting a request attribute. This is used by OPSXFormsFilter.
                final String rendererBaseURI = (String) request.getAttributesMap().get(OrbeonXFormsFilter.RENDERER_BASE_URI_ATTRIBUTE_NAME);
                if (rendererBaseURI != null)
                    rootXMLBase = rendererBaseURI;
                else
                    rootXMLBase = request.getRequestPath();
            }
            xmlBaseStack.push(new URI(null, null, rootXMLBase, null));
        } catch (URISyntaxException e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    /**
     * Constructor for nested document (XBL templates).
     *
     * @param contentHandler    resulting static state document
     * @param metadata          metadata
     * @param ignoreRootElement whether root element must just be skipped
     * @param baseURI           base URI
     */
    public XFormsExtractorContentHandler(ContentHandler contentHandler, XFormsAnnotatorContentHandler.Metadata metadata,
                                         boolean ignoreRootElement, String baseURI) {
        super(contentHandler, contentHandler != null);

        this.isTopLevel = false;
        this.externalContext = null;
        this.metadata = metadata;
        this.ignoreRootElement = ignoreRootElement;

        try {
            assert baseURI != null;
            xmlBaseStack.push(new URI(null, null, baseURI, null));
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

            if (externalContext != null) {// null in case of nested document (XBL templates)
                // Add xml:base attribute
                attributesImpl.addAttribute(XMLConstants.XML_URI, "base", "xml:base", ContentHandlerHelper.CDATA, externalContext.getResponse().rewriteRenderURL((xmlBaseStack.get(0)).toString()));
                // Add deployment attribute
                attributesImpl.addAttribute(XMLConstants.XML_URI, "deployment", "deployment", ContentHandlerHelper.CDATA, deploymentType.name());
                // Add context path attribute
                attributesImpl.addAttribute(XMLConstants.XML_URI, "context-path", "context-path", ContentHandlerHelper.CDATA, requestContextPath);
                // Add container-type attribute
                attributesImpl.addAttribute("", "container-type", "container-type", ContentHandlerHelper.CDATA, externalContext.getRequest().getContainerType());
                // Add container-namespace attribute
                attributesImpl.addAttribute("", "container-namespace", "container-namespace", ContentHandlerHelper.CDATA, externalContext.getRequest().getContainerNamespace());
            }

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
            for (final Map.Entry<String, String> currentEntry: properties.entrySet()) {
                final String propertyName = currentEntry.getKey();
                newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, propertyName, "xxforms:" + propertyName, ContentHandlerHelper.CDATA, currentEntry.getValue());
            }

            super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
            super.startElement("", "properties", "properties", newAttributes);
            super.endElement("", "properties", "properties");
            super.endPrefixMapping("xxforms");
        }

        if (isTopLevel) {
            // Remember the last id used for id generation. During state restoration, XBL components must start with this id.
            final AttributesImpl newAttributes = new AttributesImpl();
            newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, Integer.toString(metadata.idGenerator.getCurrentId()));
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


        // Handle xml:base
        if (!inXFormsOrExtension) {
            final String xmlBaseAttribute = attributes.getValue(XMLConstants.XML_URI, "base");
            if (xmlBaseAttribute == null) {
                xmlBaseStack.push(xmlBaseStack.peek());
            } else {
                try {
                    final URI currentXMLBaseURI = xmlBaseStack.peek();
                    xmlBaseStack.push(currentXMLBaseURI.resolve(new URI(xmlBaseAttribute)).normalize());// normalize to remove "..", etc.
                } catch (URISyntaxException e) {
                    throw new ValidationException("Error creating URI from: '" + xmlBaseStack.peek() + "' and '" + xmlBaseAttribute + "'.", e, new LocationData(locator));
                }
            }
        }

        // Handle properties of the form @xxforms:* when outside of models or controls
        if (!inXFormsOrExtension && !isXFormsOrExtension) {
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

        if (level > 0 || !ignoreRootElement) {

            // Start extracting model or controls
            if (!inXFormsOrExtension && isXFormsOrExtension) {

                inXFormsOrExtension = true;
                xformsLevel = level;

                outputFirstElementIfNeeded();

                // Add xml:base on element
                attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

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
                if ((XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.contains(localname) // labels, etc. may contain XHTML
                        || "instance".equals(localname)) && isXForms // XForms instances
                        || "schema".equals(localname) && XMLConstants.XSD_URI.equals(uri) // XML schemas
                        || "xbl".equals(localname) && isXBL // preserve everything under xbl:xbl so that templates may be processed by static state
                        || isExtension) {
                    inPreserve = true;
                    preserveLevel = level;
                }

                // Callback for XForms elements
                if (isXFormsOrExtension) {
                    startXFormsOrExtension(uri, localname, qName, attributes);
                }
            }

            // We are within preserved content or we output regular XForms content
            if (inXFormsOrExtension && (inPreserve || isXFormsOrExtension)) {
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
        final URI currentXMLBaseURI = xmlBaseStack.peek();
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
            if (inXFormsOrExtension && (inPreserve || isXFormsOrExtension)) {
                super.endElement(uri, localname, qName);
            }

            if (inPreserve && level == preserveLevel) {
                // Leaving preserved content
                inPreserve = false;
            }

            // Callback for XForms elements
            if (isXFormsOrExtension && !inPreserve) {
                endXFormsOrExtension(uri, localname, qName);
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
            xmlBaseStack.pop();
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
}
