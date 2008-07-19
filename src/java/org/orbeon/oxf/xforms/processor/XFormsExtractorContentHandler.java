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
 * This ContentHandler extracts XForms models and controls from an XHTML document and creates a static state document
 * for the request encoder. xml:base attributes are added on the models and root control elements.
 *
 * This now allows for XForms controls and AVTs not only within the body, but anywhere else outside models.
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
 *   <!-- Scripts -->
 *   <scripts>
 *      <script id="...">...</script
 *      <script id="...">...</script
 *   </scripts>
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

    private boolean inControl;              // whether we are in an XForms control or extension element
    private int controlLevel;
    private boolean inModel;                // whether we are in a model
    private int modelLevel;
    private boolean inLabelHintHelpAlert;   // whether we are in a label, etc.

    private boolean inScript;               // whether we are in a script
    private String xxformsScriptId;
    private Map xxformsScriptMap;
    private StringBuffer xxformsScriptStringBuffer;

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

        // Output scripts
        if (xxformsScriptMap != null) {
            super.startElement("", "scripts", "scripts", XMLUtils.EMPTY_ATTRIBUTES);

            final AttributesImpl newAttributes = new AttributesImpl();
            for (Iterator i = xxformsScriptMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();

                newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, (String) currentEntry.getKey());

                super.startElement("", "script", "script", newAttributes);
                final String content = (String) currentEntry.getValue();
                super.characters(content.toCharArray(), 0, content.length());// TODO: this copies characters around, maybe we can avoid that
                super.endElement("", "script", "script");

                newAttributes.clear();
            }

            super.endElement("", "scripts", "scripts");
        }

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

        // Handle location data
        if (locationData == null && locator != null && mustOutputFirstElement) {
            final String systemId = locator.getSystemId();
            if (systemId != null) {
                locationData = new LocationData(systemId, locator.getLineNumber(), locator.getColumnNumber());
            }
        }

        namespaceSupport.startElement();

        // Check for XForms or extension namespaces
        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms;

        if (!inControl && !inModel) {

            // Handle xml:base
            {
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
            if (!isXFormsOrExtension) {
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
        }

        {
            if (isXFormsOrExtension) {
                // This is an XForms or extension element

                if (!inModel) {
                    if (isXXForms) {
                        // Check that we are getting a valid xxforms:* element if used in body
                        if (ALLOWED_XXFORMS_ELEMENTS.get(localname) == null)
                            throw new ValidationException("Invalid element in XForms document: xxforms:" + localname, new LocationData(locator));
                    } else if (isEXForms) {
                        // Check that we are getting a valid exforms:* element if used in body
                        if (ALLOWED_EXFORMS_ELEMENTS.get(localname) == null)
                            throw new ValidationException("Invalid element in XForms document: exforms:" + localname, new LocationData(locator));
                    }
                }

                if (!inControl && !inModel && localname.equals("model")) {
                    // Start extracting model
                    inModel = true;
                    modelLevel = level;

                    outputFirstElementIfNeeded();

                    // Add xml:base on element
                    attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

                    sendStartPrefixMappings();

                } else if (!inControl && !inModel) {
                    // Start extracting controls
                    inControl = true;
                    controlLevel = level;

                    outputFirstElementIfNeeded();

                    // Add xml:base on element
                    attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

                    sendStartPrefixMappings();
                }

                if (inControl || inModel) {

                    if ("script".equals(localname) && isXXForms) {
                        if (xxformsScriptMap == null) {
                            xxformsScriptMap = new HashMap();
                            xxformsScriptStringBuffer = new StringBuffer();
                        } else {
                            xxformsScriptStringBuffer.setLength(0);
                        }
                        xxformsScriptId = attributes.getValue("id");
                        inScript = true;
                    }
                }

                if (inControl) {
                    if (LABEL_HINT_HELP_ALERT_ELEMENT.get(localname) != null) {
                        inLabelHintHelpAlert = true;
                    }

                    super.startElement(uri, localname, qName, attributes);
                }

            } else if (inLabelHintHelpAlert && (XMLConstants.XHTML_NAMESPACE_URI.equals(uri) || "".equals(uri))) {
                // Preserve content within labels, etc.
                super.startElement(uri, localname, qName, attributes);
            }

            if (inModel) {
                super.startElement(uri, localname, qName, attributes);
            }
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

        if (inModel) {
            super.endElement(uri, localname, qName);
        } else if (inControl && isXForms) {
            if (LABEL_HINT_HELP_ALERT_ELEMENT.get(localname) != null) {
                inLabelHintHelpAlert = false;
            }
            super.endElement(uri, localname, qName);
        } else if (inControl && (isXXForms || isEXForms)) {
            super.endElement(uri, localname, qName);
        } else if (inLabelHintHelpAlert && (XMLConstants.XHTML_NAMESPACE_URI.equals(uri) || "".equals(uri))) {
            // Preserve content
            super.endElement(uri, localname, qName);
        }

        if (inModel && level == modelLevel) {
            // Leaving model
            inModel = false;
            sendEndPrefixMappings();
        } else if (inControl && level == controlLevel) {
            // Leaving control
            inControl = false;
            sendEndPrefixMappings();
        }

        if (inScript && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri) && "script".equals(localname)) {
            // Leaving script: remember script content by id
            xxformsScriptMap.put(xxformsScriptId, xxformsScriptStringBuffer.toString());
            inScript = false;
        }

        if (!inModel && !inControl) {
            xmlBaseStack.pop();
        }

        namespaceSupport.endElement();
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (inScript) {
            // Script content doesn't go through
            xxformsScriptStringBuffer.append(chars, start, length);
        } else  if (inModel || inControl) {
            super.characters(chars, start, length);
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceSupport.startPrefixMapping(prefix, uri);
        if (inModel || inControl)
            super.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (inModel || inControl)
            super.endPrefixMapping(s);
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }
}
