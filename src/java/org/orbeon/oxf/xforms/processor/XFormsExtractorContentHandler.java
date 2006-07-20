package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.xml.sax.Locator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * This ContentHandler extracts XForms models and controls from an XHTML document and creates a static state document
 * for the request encoder. xml:base attributes are added on the models and root control elements.
 */
public class XFormsExtractorContentHandler extends ForwardingContentHandler {

    private final String HTML_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "html");
    private final String HEAD_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "head");
    private final String BODY_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "body");

    private Locator locator;

    private String stateHandling;
    private String readonly;
    private String readonlyAppearance;

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
            final String rootXMLBase = externalContext.getRequest().getRequestPath();
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
            // Add state-handling attribute
            if (stateHandling != null)
                attributesImpl.addAttribute("", "state-handling", "state-handling", ContentHandlerHelper.CDATA, stateHandling);
            // Add read-only attribute
            if (readonly != null)
                attributesImpl.addAttribute("", "readonly", "readonly", ContentHandlerHelper.CDATA, readonly);
            // Add read-only appearance
            if (readonlyAppearance != null)
                attributesImpl.addAttribute("", "readonly-appearance", "readonly-appearance", ContentHandlerHelper.CDATA, readonlyAppearance);

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
//            super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
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
//            super.endPrefixMapping("xxforms");
        }

        super.endElement("", "static-state", "static-state");
        super.endDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

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
            // Handle preferences
            if (stateHandling == null) {
                final String xxformsStateHandling = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, XFormsConstants.XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME);
                if (xxformsStateHandling != null) {
                    if (!(xxformsStateHandling.equals(XFormsConstants.XXFORMS_STATE_HANDLING_CLIENT_VALUE) || xxformsStateHandling.equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE)))
                        throw new ValidationException("Invalid xxforms:" + XFormsConstants.XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME + " attribute value: " + xxformsStateHandling, new LocationData(locator));

                    stateHandling = xxformsStateHandling;
                }
            }
            if (readonly == null) {
                final String xxformsReadonly = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME);
                if (xxformsReadonly != null) {
                    if (!(xxformsReadonly.equals("true") || xxformsReadonly.equals("false")))
                        throw new ValidationException("Invalid xxforms:" + XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME + " attribute value: " + xxformsReadonly, new LocationData(locator));

                    readonly = xxformsReadonly;
                }
            }
            if (readonlyAppearance == null) {
                final String xxformsReadonlyAppearance = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, XFormsConstants.XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME);
                if (xxformsReadonlyAppearance != null) {
                    if (!(xxformsReadonlyAppearance.equals(XFormsConstants.XXFORMS_READONLY_APPEARANCE_DYNAMIC_VALUE)
                            || xxformsReadonlyAppearance.equals(XFormsConstants.XXFORMS_READONLY_APPEARANCE_STATIC_VALUE)))
                        throw new ValidationException("Invalid xxforms:" + XFormsConstants.XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME + " attribute value: " + xxformsReadonlyAppearance, new LocationData(locator));

                    readonlyAppearance = xxformsReadonlyAppearance;
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
            if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                // This is an XForms element

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

                if (inControl) {
                    super.startElement(uri, localname, qName, attributes);
                }
            } else if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {

                if (BODY_QNAME.equals(element1)) {// NOTE: This test is a little harsh, as the user may use xxforms:* elements for examples, etc.
                    if (!("img".equals(localname) || "size".equals(localname) || "script".equals(localname)))
                        throw new ValidationException("Invalid element in XForms document: xxforms:" + localname, new LocationData(locator));
                }

                if (inControl || inModel) {

                    if ("script".equals(localname)) {
                        if (xxformsScriptMap == null) {
                            xxformsScriptMap = new HashMap();
                            xxformsScriptStringBuffer = new StringBuffer();
                        } else {
                            xxformsScriptStringBuffer.setLength(0);
                        }
                        xxformsScriptId = attributes.getValue("id");
                        inScript = true;
                    }

                    if (inControl) {
                        super.startElement(uri, localname, qName, attributes);
                    }
                }
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

        if (inModel) {
            super.endElement(uri, localname, qName);
        } else if (inControl && (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) || XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri))) {
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
