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
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsStaticStateImpl;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.state.AnnotatedTemplate;
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
 * o The content of inline XForms instances (xf:instance)
 * o The content of inline XML Schemas (xs:schema)
 * o The content of inline XBL definitions (xbl:xbl)
 * o The content of xf:label, xf:hint, xf:help, xf:alert (as they can contain XHTML)
 *
 * Notes:
 *
 * o xml:base attributes are added on the models and root control elements.
 * o XForms controls and AVTs outside the HTML body are also extracted.
 *
 * Structure:
 *
 * <static-state xmlns:xxf="..." system-id="..." is-html="..." ...>
 *   <root>
 *     <!-- E.g. AVT on xhtml:html -->
 *     <xxf:attribute .../>
 *     <!-- E.g. xf:output within xhtml:title -->
 *     <xf:output .../>
 *     <!-- E.g. XBL component definitions -->
 *     <xbl:xbl .../>
 *     <xbl:xbl .../>
 *     <!-- Top-level models -->
 *     <xf:model ...>
 *     <xf:model ...>
 *     <!-- Top-level controls including XBL-bound controls -->
 *     <xf:group ...>
 *     <xf:input ...>
 *     <foo:bar ...>
 *   </root>
 *   <!-- Global properties -->
 *   <properties xxf:noscript="true" .../>
 *   <!-- Last id used (for id generation in XBL after deserialization) -->
 *   <last-id id="123"/>
 *   <!-- Template (for full updates, possibly noscript) -->
 *   <template>base64</template>
 * </static-state>
 */
public class XFormsExtractor extends ForwardingXMLReceiver {

    public static final QName LAST_ID_QNAME = new QName("last-id");

    private Locator locator;
    private LocationData locationData;

    private Map<String, Object> properties = new HashMap<String, Object>();

    private int level;

    private NamespaceContext namespaceContext = new NamespaceContext();

    private boolean mustOutputFirstElement = true;

    private final boolean isTopLevel;
    private final AnnotatedTemplate templateUnderConstruction;
    private final Metadata metadata;
    private final boolean ignoreRootElement;
    private final boolean outputSingleTemplate;

    private static class XMLElementDetails {
        public final String id;
        public final URI xmlBase;
        public final String xmlLang;
        public final String xmlLangAvtId;
        public final XFormsConstants.XXBLScope scope;
        public final boolean isModel;

        private XMLElementDetails(String id, URI xmlBase, String xmlLang, String xmlLangAvtId, XFormsConstants.XXBLScope scope, boolean isModel) {
            this.id = id;
            this.xmlBase = xmlBase;
            this.xmlLang = xmlLang;
            this.xmlLangAvtId = xmlLangAvtId;
            this.scope = scope;
            this.isModel = isModel;
        }
    }

    private Stack<XMLElementDetails> elementStack = new Stack<XMLElementDetails>();

    private boolean inXFormsOrExtension;       // whether we are in a model
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in a schema, instance, or xbl:xbl
    private boolean inForeign;      // whether we are in a foreign element section in the model
    private boolean inLHHA;         // whether we are in an LHHA element
    private int preserveOrLHHAOrForeignLevel;
    private boolean isHTMLDocument; // Whether this is an (X)HTML document

    public XFormsExtractor(
            XMLReceiver xmlReceiver,
            Metadata metadata,
            AnnotatedTemplate templateUnderConstruction,
            String baseURI,
            XFormsConstants.XXBLScope startScope,
            boolean isTopLevel,
            boolean ignoreRootElement, // NOTE: unused as of 2013-10-11
            boolean outputSingleTemplate) {

        super(xmlReceiver);

        this.isTopLevel = isTopLevel;
        this.metadata = metadata;
        this.templateUnderConstruction = templateUnderConstruction;
        this.ignoreRootElement = ignoreRootElement;
        this.outputSingleTemplate = outputSingleTemplate;

        // Create xml:base stack
        try {
            assert baseURI != null;
            elementStack.push(new XMLElementDetails(null, new URI(null, null, baseURI, null), null, null, startScope, false));
        } catch (URISyntaxException e) {
            throw new ValidationException(e, LocationData.createIfPresent(locator));
        }
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
    }

    private void outputFirstElementIfNeeded() throws SAXException {
        if (! outputSingleTemplate && mustOutputFirstElement) {
            final AttributesImpl attributesImpl = new AttributesImpl();

            // Add location information
            if (locationData != null) {
                attributesImpl.addAttribute("", "system-id", "system-id", XMLReceiverHelper.CDATA, locationData.getSystemID());
                attributesImpl.addAttribute("", "line", "line", XMLReceiverHelper.CDATA, Integer.toString(locationData.getLine()));
                attributesImpl.addAttribute("", "column", "column", XMLReceiverHelper.CDATA, Integer.toString(locationData.getCol()));
            }
            
            // Add is HTML information
            attributesImpl.addAttribute("", "is-html", "is-html", XMLReceiverHelper.CDATA, isHTMLDocument?"true":"false");

            super.startElement("", "static-state", "static-state", attributesImpl);

            attributesImpl.clear();
            attributesImpl.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, "#document");
            super.startElement("", "root", "root", attributesImpl);
            mustOutputFirstElement = false;
        }
    }

    @Override
    public void endDocument() throws SAXException {

        if (! outputSingleTemplate) {

            outputFirstElementIfNeeded();
            super.endElement("", "root", "root");

            // Output non-default properties
            {
                final PropertySet propertySet = Properties.instance().getPropertySet();
                for (Iterator i = XFormsProperties.getPropertyDefinitionEntryIterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String propertyName = (String) currentEntry.getKey();
                    final XFormsProperties.PropertyDefinition propertyDefinition = (XFormsProperties.PropertyDefinition) currentEntry.getValue();

                    final Object defaultPropertyValue = propertyDefinition.defaultValue; // value can be String, Boolean, Integer
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
                    newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, propertyName, "xxf:" + propertyName, XMLReceiverHelper.CDATA, currentEntry.getValue().toString());
                }

                super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
                super.startElement("", "properties", "properties", newAttributes);
                super.endElement("", "properties", "properties");
                super.endPrefixMapping("xxforms");
            }

            if (isTopLevel) {
                // Remember the last id used for id generation. During state restoration, XBL components must start with this id.
                final AttributesImpl newAttributes = new AttributesImpl();
                newAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, Integer.toString(metadata.idGenerator().getCurrentId()));
                final String lastIdName = LAST_ID_QNAME.getName();
                super.startElement("", lastIdName, lastIdName, newAttributes);
                super.endElement("", lastIdName, lastIdName);

                // TODO: It's not good to serialize this right here, since we have a live SAXStore anyway used to create the
                // static state and since the serialization is only needed if the static state is serialized. In other
                // words, serialization of the template should be lazy.

                // Remember the template (and marks if any) if:
                // - we are in noscript mode and told to store the template statically
                // - OR if there are top-level marks
                final boolean isStoreNoscriptTemplate =
                    templateUnderConstruction != null &&
                    XFormsStaticStateImpl.isNoscriptJava(properties) &&
                    XFormsProperties.NOSCRIPT_TEMPLATE_STATIC_VALUE.equals(XFormsStaticStateImpl.<String>getPropertyJava(properties, XFormsProperties.NOSCRIPT_TEMPLATE));

                if (isStoreNoscriptTemplate || metadata.hasTopLevelMarks()) {
                    final String templateName = "template";
                    super.startElement("", templateName, templateName, new AttributesImpl());

                    // NOTE: At this point, the template has just received endDocument(), so is no longer under under
                    // construction and can be serialized safely.
                    final String templateString = templateUnderConstruction.asBase64();
                    super.characters(templateString.toCharArray(), 0, templateString.length());

                    super.endElement("", templateName, templateName);
                }
            }

            super.endElement("", "static-state", "static-state");
        }

        super.endDocument();
    }

    @Override
    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        namespaceContext.startElement();

        // Handle location data
        if (locationData == null && locator != null && mustOutputFirstElement) {
            final String systemId = locator.getSystemId();
            if (systemId != null)
                locationData = new LocationData(systemId, locator.getLineNumber(), locator.getColumnNumber());
        }

        // Check for XForms or extension namespaces
        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXBL = XFormsConstants.XBL_NAMESPACE_URI.equals(uri);
        final boolean isXXBL = XFormsConstants.XXBL_NAMESPACE_URI.equals(uri); // for xxbl:global

        final boolean isExtension = metadata.isXBLBinding(uri, localname);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms || isXBL || isXXBL || isExtension;

        final XMLElementDetails parentElementDetails = elementStack.peek();

        // Handle outer xml:base and xml:lang
        if (! inPreserve && ! inForeign) {
            final String xmlBaseAttribute = attributes.getValue(XMLConstants.XML_URI, "base");
            final String xmlLangAttribute = attributes.getValue(XMLConstants.XML_URI, "lang");
            final String xblScopeAttribute = attributes.getValue(XFormsConstants.XXBL_SCOPE_QNAME.getNamespaceURI(), XFormsConstants.XXBL_SCOPE_QNAME.getName());

            final String id = attributes.getValue("", "id");

            // Extract xbl:base
            final URI newBase;
            if (xmlBaseAttribute != null) {
                try {
                    // Resolve
                    newBase = parentElementDetails.xmlBase.resolve(new URI(xmlBaseAttribute)).normalize();// normalize to remove "..", etc.
                } catch (URISyntaxException e) {
                    throw new ValidationException("Error creating URI from: '" + parentElementDetails + "' and '" + xmlBaseAttribute + "'.", e, LocationData.createIfPresent(locator));
                }
            } else {
                newBase = parentElementDetails.xmlBase;
            }

            // Extract xml:lang
            final String newLang;
            final String xmlLangAvtId;
            if (xmlLangAttribute != null) {
                newLang = xmlLangAttribute;
                if (XFormsUtils.maybeAVT(newLang))
                    xmlLangAvtId = id;
                else
                    xmlLangAvtId = parentElementDetails.xmlLangAvtId;
            } else {
                newLang = parentElementDetails.xmlLang;
                xmlLangAvtId =  parentElementDetails.xmlLangAvtId;
            }

            final XFormsConstants.XXBLScope newScope;
            if (xblScopeAttribute != null) {
                newScope = XFormsConstants.XXBLScope.valueOf(xblScopeAttribute);
            } else {
                newScope = parentElementDetails.scope;
            }

            elementStack.push(new XMLElementDetails(id, newBase, newLang, xmlLangAvtId, newScope, isXForms && localname.equals("model")));
        }

        // Handle properties of the form @xxf:* when outside of models or controls
        if (! inXFormsOrExtension && ! isXFormsOrExtension) {
            handleProperties(attributes);
        }
        
        if (level == 0 && isTopLevel) {
        	isHTMLDocument = "html".equals(localname) && (uri == null || uri.length() == 0 || XMLConstants.XHTML_NAMESPACE_URI.equals(uri));
        }

        if (level > 0 || ! ignoreRootElement) {

            // Start extracting model or controls
            if (! inXFormsOrExtension && isXFormsOrExtension) {

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
                final String xmlLang = elementStack.peek().xmlLang;
                if (xmlLang != null) {
                    final String newXMLLang;
                    final String xmlLangAvtId = elementStack.peek().xmlLangAvtId;
                    if (XFormsUtils.maybeAVT(xmlLang) && xmlLangAvtId != null) {
                        // In this case the latest xml:lang on the stack might be an AVT and we set a special value for
                        // xml:lang containing the id of the control that evaluates the runtime value.
                        newXMLLang = "#" + xmlLangAvtId;
                    } else {
                        // No AVT
                        newXMLLang = xmlLang;
                    }

                    attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "lang", newXMLLang);
                }

                sendStartPrefixMappings();
            }

            // Check for preserved, foreign, or LHHA content
            if (inXFormsOrExtension && ! inPreserve && ! inForeign) {
                // TODO: Just warn?
                if (isXXForms) {
                    // Check that we are getting a valid xxf:* element
                    if (! XFormsConstants.ALLOWED_XXFORMS_ELEMENTS.contains(localname) && ! XFormsActions.isAction(QName.get(localname, XFormsConstants.XXFORMS_NAMESPACE)))
                        throw new ValidationException("Invalid extension element in XForms document: " + qName, LocationData.createIfPresent(locator));
                } else if (isEXForms) {
                    // Check that we are getting a valid exf:* element
                    if (! XFormsConstants.ALLOWED_EXFORMS_ELEMENTS.contains(localname))
                        throw new ValidationException("Invalid eXForms element in XForms document: " + qName, LocationData.createIfPresent(locator));
                } else if (isXBL) {
                    // Check that we are getting a valid xbl:* element
                    if (! XFormsConstants.ALLOWED_XBL_ELEMENTS.contains(localname))
                        throw new ValidationException("Invalid XBL element in XForms document: " + qName, LocationData.createIfPresent(locator));
                }

                // Preserve as is the content of labels, etc., instances, and schemas
                if (! inLHHA) {
                    if (XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.contains(localname) && isXForms) {// labels, etc. may contain XHTML)
                        inLHHA = true;
                        preserveOrLHHAOrForeignLevel = level;
                    } else if ("instance".equals(localname) && isXForms                         // XForms instance
                            || "schema".equals(localname) && XMLConstants.XSD_URI.equals(uri)   // XML schema
                            || "xbl".equals(localname) && isXBL // preserve everything under xbl:xbl so that templates may be processed by static state
                            || isExtension) {
                        inPreserve = true;
                        preserveOrLHHAOrForeignLevel = level;
                    }
                }

                // Callback for elements of interest
                if (isXFormsOrExtension || inLHHA) {
                    // NOTE: We call this also for HTML elements within LHHA so we can gather scope information for AVTs
                    startXFormsOrExtension(uri, localname, attributes, elementStack.peek().scope);
                }
            }

            if (inXFormsOrExtension && ! inForeign && (inPreserve || inLHHA || isXFormsOrExtension)) {
                // We are within preserved content or we output regular XForms content
                super.startElement(uri, localname, qName, attributes);
            } else if (inXFormsOrExtension && ! isXFormsOrExtension && parentElementDetails.isModel) {
                // Start foreign content in the model
                inForeign = true;
                preserveOrLHHAOrForeignLevel = level;
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
        final URI currentXMLBaseURI = elementStack.peek().xmlBase;
        return currentXMLBaseURI.toString();
    }

    private void sendStartPrefixMappings() throws SAXException {
        for (Enumeration e = namespaceContext.getPrefixes(); e.hasMoreElements();) {
            final String namespacePrefix = (String) e.nextElement();
            final String namespaceURI = namespaceContext.getURI(namespacePrefix);
            if (! namespacePrefix.startsWith("xml"))
                super.startPrefixMapping(namespacePrefix, namespaceURI);
        }
    }

    private void sendEndPrefixMappings() throws SAXException {
        for (Enumeration e = namespaceContext.getPrefixes(); e.hasMoreElements();) {
            final String namespacePrefix = (String) e.nextElement();
            if (! namespacePrefix.startsWith("xml"))
                super.endPrefixMapping(namespacePrefix);
        }
    }

    @Override
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

        if (level > 0 || ! ignoreRootElement) {
            // We are within preserved content or we output regular XForms content
            if (inXFormsOrExtension && ! inForeign && (inPreserve || inLHHA || isXFormsOrExtension)) {
                super.endElement(uri, localname, qName);
            }

            if ((inPreserve || inLHHA || inForeign) && level == preserveOrLHHAOrForeignLevel) {
                // Leaving preserved, foreign or LHHA content
                inPreserve = false;
                inForeign = false;
                inLHHA = false;
            }

            if (inXFormsOrExtension && ! inPreserve && ! inForeign) {
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

        if (! inPreserve && ! inForeign) {
            elementStack.pop();
        }

        namespaceContext.endElement();
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inPreserve) {
            super.characters(ch, start, length);
        } else if (! inForeign) {
            // TODO: we must not output characters here if we are not directly within an XForms element
            // See: https://github.com/orbeon/orbeon-forms/issues/493
            if (inXFormsOrExtension) // TODO: check this: only keep spaces within XForms elements that require it in order to reduce the size of the static state
                super.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        // ignore, should not happen
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceContext.startPrefixMapping(prefix, uri);
        if (inXFormsOrExtension)
            super.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping(String s) throws SAXException {
        if (inXFormsOrExtension)
            super.endPrefixMapping(s);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    protected void startXFormsOrExtension(String uri, String localname, Attributes attributes, XFormsConstants.XXBLScope scope) {
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

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        if (inPreserve) {
            super.processingInstruction(target, data);
        }
    }

    private void handleProperties(Attributes attributes) {
        final int attributesCount = attributes.getLength();
        for (int i = 0; i < attributesCount; i++) {
            final String attributeURI = attributes.getURI(i);
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attributeURI)) {
                // Found xxf:* attribute
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
