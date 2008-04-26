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
 */
public class XFormsExtractorContentHandler extends ForwardingContentHandler {

    private static final String HTML_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "html");
    private static final String HEAD_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "head");
    private static final String BODY_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "body");

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
    private String element0;
    private String element1;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private boolean gotModel;
    private boolean gotControl;

    private boolean inModel;
    private int modelLevel;
    private boolean inControl;
    private int controlLevel;

    private boolean inLabelHintHelpAlert;

    private boolean mustOutputFirstElement = true;

    private final ExternalContext externalContext;
    private Stack xmlBaseStack = new Stack();

    private boolean inScript;
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
            
            // Add properties
            for (Iterator i = properties.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String propertyName = (String) currentEntry.getKey();
                attributesImpl.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, propertyName, "xxforms:" + propertyName, ContentHandlerHelper.CDATA, (String) currentEntry.getValue());
            }

            super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
            super.startElement("", "static-state", "static-state", attributesImpl);
            mustOutputFirstElement = false;
        }
    }

    public void endDocument() throws SAXException {

        // Start and close elements
        if (!gotModel && !gotControl) {
            outputFirstElementIfNeeded();
            super.startElement("", "models", "models", XMLUtils.EMPTY_ATTRIBUTES);
        }

        if (gotModel && !gotControl) {
            super.endElement("", "models", "models");
            super.startElement("", "controls", "controls", XMLUtils.EMPTY_ATTRIBUTES);
            super.endElement("", "controls", "controls");
        }

        if (gotModel && gotControl) {
            super.endElement("", "controls", "controls");
        }

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

        super.endElement("", "static-state", "static-state");
        super.endPrefixMapping("xxforms");
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

        if (!inModel && !inControl) {

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

            // Handle properties of the form @xxforms:*
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

        // Remember first two levels of elements
        if (level == 0) {
            element0 = XMLUtils.buildExplodedQName(uri, localname);
        } else if (level == 1) {
            element1 = XMLUtils.buildExplodedQName(uri, localname);
        } else if (level >= 2 && HTML_QNAME.equals(element0)) {
            // We are under /xhtml:html
            final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
            final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
            final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
            if (isXForms || isXXForms || isEXForms) {
                // This is an XForms element or an extension element

                if (BODY_QNAME.equals(element1)) {
                    // TODO: Move this check to XFormsDocumentAnnotator so we can make sure we check head AND body correctly?
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

                if (!inModel && !inControl && localname.equals("model") && HEAD_QNAME.equals(element1)) {
                    // Start extracting model
                    inModel = true;
                    modelLevel = level;

                    if (gotControl)
                        throw new ValidationException("/xhtml:html/xhtml:head/xforms:model occurred after /xhtml:html/xhtml:body//xforms:*", new LocationData(locator));

                    if (!gotModel) {
                        outputFirstElementIfNeeded();
                        super.startElement("", "models", "models", XMLUtils.EMPTY_ATTRIBUTES);
                    }

                    gotModel = true;

                    // Add xml:base on element
                    attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

                    sendStartPrefixMappings();

                } else if (!inModel && !inControl && BODY_QNAME.equals(element1)) {
                    // Start extracting controls
                    inControl = true;
                    controlLevel = level;

                    if (!gotControl) {
                        if (gotModel) {
                            super.endElement("", "models", "models");
                        } else {
                            outputFirstElementIfNeeded();
                            super.startElement("", "models", "models", XMLUtils.EMPTY_ATTRIBUTES);
                            super.endElement("", "models", "models");
                        }
                        super.startElement("", "controls", "controls", XMLUtils.EMPTY_ATTRIBUTES);
                    }

                    gotControl = true;

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
                // Preserve content
                super.startElement(uri, localname, qName, attributes);
            }

            if (inModel) {

//                boolean found = false;
//                if (isXForms && "instance".equals(localname)) {
//                    // Processing an xforms:instance element
//                    final String srcAttribute = attributes.getValue("src");
//                    if (srcAttribute != null) {
//                        final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(srcAttribute);
//                        if (inputName != null) {
//                            // Found an input:* URI
//
//                            // Take care of namespaces and other fun stuff
//                            // TODO: Handle locator
//                            final ContentHandler includeContentHandler = new XIncludeProcessor.XIncludeContentHandler(null, super.getContentHandler(), null, null, null, namespaceSupport, false, new XIncludeProcessor.OutputLocator());
//
//                            // Output instance element without @src attribute
//                            final AttributesImpl newAttributes = new AttributesImpl(attributes);
//                            newAttributes.removeAttribute(newAttributes.getIndex("src"));
//                            super.startElement(uri, localname, qName, newAttributes);
//
//                            // Include document in the output stream
//                            uriResolver.readURLAsSAX(srcAttribute, null, null, includeContentHandler);
//
//                            found = true;
//                        }
//                    }
//                }
//                if (!found)
//                    super.startElement(uri, localname, qName, attributes);

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
