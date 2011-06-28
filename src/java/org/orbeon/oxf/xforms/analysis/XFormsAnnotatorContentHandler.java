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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

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
 * document in XFormsToXHTML to produce the output. So if we modify this, we should modify it so that two separate
 * ContentHandler (at least two separate outputs) are produced, one for the annotated output, another for the extracted
 * output.
 */
public class XFormsAnnotatorContentHandler extends XMLReceiverAdapter {

    private XMLReceiver templateReceiver;
    private XMLReceiver extractorReceiver;
    private SAXStore templateSAXStore;

    private int level = 0;
    private boolean inHead;         // whether we are in the HTML head
    private boolean inTitle;        // whether we are in the HTML title

    private boolean inXForms;       // whether we are in a model or other XForms content
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in LHHA, schema or instance
    private int preserveLevel;
    private boolean inLHHA;         // whether we are in LHHA (meaningful only if inPreserve == true)
    private boolean inXBL;          // whether we are in xbl:xbl (meaningful only if inPreserve == true)

    private Locator documentLocator;


    private final Metadata metadata;
    private final boolean isGenerateIds;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();
    private final List<String> xhtmlElementLocalnames = new ArrayList<String>();

    // Name of container elements that require the use of separators for handling visibility
    private static final Set<String> SEPARATOR_CONTAINERS = new HashSet<String>();
    static {
        SEPARATOR_CONTAINERS.add("table");
        SEPARATOR_CONTAINERS.add("tbody");
        SEPARATOR_CONTAINERS.add("thead");
        SEPARATOR_CONTAINERS.add("tfoot");
        SEPARATOR_CONTAINERS.add("tr");

        SEPARATOR_CONTAINERS.add("ol");
        SEPARATOR_CONTAINERS.add("ul");
        SEPARATOR_CONTAINERS.add("dl");
    }

    private final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
    private final AttributesImpl reusableAttributes = new AttributesImpl();
    private final String[] reusableStringArray = new String[1];

    private String htmlTitleElementId;

    /**
     * Constructor for XBL shadow trees and top-level documents.
     *
     * @param templateReceiver      template output (special treatment for marks if this is a SAXStore)
     * @param extractorReceiver     extractor output (can be null for XBL for now)
     * @param metadata              metadata to gather
     */
    public XFormsAnnotatorContentHandler(XMLReceiver templateReceiver, XMLReceiver extractorReceiver, Metadata metadata) {
        this.templateReceiver = templateReceiver;
        this.extractorReceiver = extractorReceiver;

        this.metadata = metadata;
        this.isGenerateIds = true;

        if (templateReceiver instanceof SAXStore)
            this.templateSAXStore = (SAXStore) templateReceiver;
    }

    /**
     * This constructor just computes the namespace mappings and AVT elements and gathers id information.
     *
     * @param metadata              metadata to gather
     */
    public XFormsAnnotatorContentHandler(Metadata metadata) {

        // In this mode, all elements that need to have ids already have them, so set safe defaults
        this.metadata = metadata;
        this.isGenerateIds = false;
    }

    @Override
    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

        if (XMLConstants.XHTML_NAMESPACE_URI.equals(uri))
            xhtmlElementLocalnames.add(localname);

        // Check for XForms or extension namespaces
        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXXForms = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isEXForms = XFormsConstants.EXFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXFormsOrExtension = isXForms || isXXForms || isEXForms;

        final boolean isXBL = XFormsConstants.XBL_NAMESPACE_URI.equals(uri);

        final int idIndex = attributes.getIndex("id");

        // Entering model or controls
        if (!inXForms && isXFormsOrExtension) {
            inXForms = true;
            xformsLevel = level;
        }

        if (inPreserve) {
            // Within preserved content


            if (inLHHA) {
                // Gather id and namespace information about content of LHHA
                if (isXForms) {
                    // Must be xforms:output
                    attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);
                } else if (hostLanguageAVTs && hasAVT(attributes)) {
                    // Must be an AVT on an host language element
                    attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);
                }
            } else if (inXBL && level - 1 == preserveLevel && isXBL && "binding".equals(localname)) {
                // Gather binding information in xbl:xbl/xbl:binding
                final String elementAttribute = attributes.getValue("element");
                if (elementAttribute != null) {
                    storeXBLBinding(elementAttribute);
                }
                // Gather id and namespace information
                attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);
            }
            // Output element
            startElement(true, uri, localname, qName, attributes);
        } else if (isXFormsOrExtension) {
            // This is an XForms element

            // TODO: can we restrain gathering ids / namespaces to only certain elements (all controls + elements with XPath expressions + models + instances)?

            // Create a new id and update the attributes if needed
            attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);

            // Handle full update annotation
            if (isXXForms && localname.equals("dynamic")) {
                // Remember this subtree has a full update
                addMark(reusableStringArray[0], templateSAXStore.getElementMark());
                // Add a class to help the client
                attributes = XMLUtils.appendToClassAttribute(attributes, "xforms-update-full");
            } else if (templateSAXStore != null && Version.isPE()) {
                // Remember mark if xxforms:update="full"
                final String xxformsUpdate = attributes.getValue(XFormsConstants.XXFORMS_UPDATE_QNAME.getNamespaceURI(), XFormsConstants.XXFORMS_UPDATE_QNAME.getName());
                if (XFormsConstants.XFORMS_FULL_UPDATE.equals(xxformsUpdate)) {
                    // Remember this subtree has a full update
                    addMark(reusableStringArray[0], templateSAXStore.getElementMark());
                    // Add a class to help the client
                    attributes = XMLUtils.appendToClassAttribute(attributes, "xforms-update-full");
                }
            }

            if (inTitle && "output".equals(localname)) {
                // Special case of xforms:output within title, which produces an xxforms:text control
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "for", htmlTitleElementId);
                startPrefixMapping(true, "xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
                startElement(true, XFormsConstants.XXFORMS_NAMESPACE_URI, "text", "xxforms:text", attributes);
            } else if ("group".equals(localname) && isClosestXHTMLAncestorTableContainer()) {
                // Closest xhtml:* ancestor is xhtml:table|xhtml:tbody|xhtml:thead|xhtml:tfoot|xhtml:tr
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", XFormsConstants.APPEARANCE_QNAME.getName(), XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME.getQualifiedName());
                startElement(true, uri, localname, qName, attributes);
            } else {
                // Leave element untouched (except for the id attribute)
                startElement(true, uri, localname, qName, attributes);
            }
        } else if (!isXBL && metadata.isXBLBindingCheckAutomaticBindings(uri, localname)) {
            // Element with a binding

            // Create a new id and update the attributes if needed
            attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);

            // Leave element untouched (except for the id attribute)
            startElement(true, uri, localname, qName, attributes);

            // Don't handle the content
            inPreserve = true;
            preserveLevel = level;

        } else if (isXBL) {
            // This must be xbl:xbl (otherwise we will have isPreserve == true) or xbl:template
            assert localname.equals("xbl") || localname.equals("template") || localname.equals("handler");
            // NOTE: Still process attributes, because the annotator is used to process top-level <xbl:handler> as well.
            attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);
            startElement(true, uri, localname, qName, attributes);
        } else {
            // Non-XForms element without an XBL binding

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
                    attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);
                    htmlElementId = reusableStringArray[0];
                    htmlTitleElementId = htmlElementId;
                }
            }

            // NOTE: @id attributes on XHTML elements are rewritten with their effective id during XHTML output by
            // XHTMLElementHandler.

            // Process AVTs if required
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
                            if (XFormsUtils.maybeAVT(attributeValue)) {
                                // This is an AVT
                                final String attributeName = attributes.getQName(i);// use qualified name for xml:lang

                                // Create a new id and update the attributes if needed
                                if (htmlElementId == null) {
                                    attributes = getAttributesGatherNamespaces(qName, attributes, reusableStringArray, idIndex);
                                    htmlElementId = reusableStringArray[0];

                                    // TODO: Clear all attributes having AVTs or XPath expressions will end up in repeat templates.
                                }

                                if (!elementOutput) {
                                    // Output the element with the new or updated id attribute
                                    startElement(true, uri, localname, qName, attributes);
                                    elementOutput = true;
                                }

                                // Create a new xxforms:attribute control
                                reusableAttributes.clear();

                                final AttributesImpl newAttributes = (AttributesImpl) getAttributesGatherNamespaces(qName, reusableAttributes, reusableStringArray, -1);

                                newAttributes.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, htmlElementId);
                                newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, attributeName);
                                newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, attributeValue);

                                newAttributes.addAttribute("", "for-name", "for-name", ContentHandlerHelper.CDATA, localname);

                                // These extra attributes can be used alongside src/href attributes
                                if ("src".equals(attributeName) || "href".equals(attributeName)) {
                                    final String urlType = attributes.getValue(XMLConstants.OPS_FORMATTING_URI, "url-type");
                                    final String portletMode = attributes.getValue(XMLConstants.OPS_FORMATTING_URI, "portlet-mode");
                                    final String windowState = attributes.getValue(XMLConstants.OPS_FORMATTING_URI, "window-state");

                                    if (urlType != null)
                                        newAttributes.addAttribute("", "url-type", "url-type", ContentHandlerHelper.CDATA, urlType);
                                    if (portletMode != null)
                                        newAttributes.addAttribute("", "portlet-mode", "portlet-mode", ContentHandlerHelper.CDATA, "portlet-mode");
                                    if (windowState != null)
                                        newAttributes.addAttribute("", "window-state", "window-state", ContentHandlerHelper.CDATA, "window-state");
                                }

                                startPrefixMapping(true, "xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
                                startElement(true, XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", "xxforms:attribute", newAttributes);
                                endElement(true, XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", "xxforms:attribute");
                                endPrefixMapping(true, "xxforms");
                            }
                        }
                    }

                    // Output the element as is if no AVT was found
                    if (!elementOutput)
                        startElement(true, uri, localname, qName, attributes);
                } else {
                    startElement(true, uri, localname, qName, attributes);
                }

            } else {
                // No AVT handling, just output the element
                startElement(true, uri, localname, qName, attributes);
            }
        }

        // Check for preserved content
        if (!inPreserve) {
            if (inXForms) {
                // Preserve as is the content of labels, etc., instances, and schemas
                // Within other XForms: check for labels, xforms:instance, and xs:schema
                if (isXForms) {
                    inLHHA = XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.contains(localname); // labels, etc. may contain XHTML
                    if (inLHHA || "instance".equals(localname)) {                                  // xforms:instance
                        inPreserve = true;
                        preserveLevel = level;
                    }
                } else if ("schema".equals(localname) && XMLConstants.XSD_URI.equals(uri)) {       // xs:schema
                    inPreserve = true;
                    preserveLevel = level;

                }
            } else {
                // At the top-level: check for labels and xbl:xbl
                final boolean isXBLXBL = isXBL && "xbl".equals(localname);
                if (isXForms) {
                    inLHHA = XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.contains(localname); // labels, etc. may contain XHTML
                    if (inLHHA) {
                        inPreserve = true;
                        preserveLevel = level;
                    }
                } else if (isXBLXBL) {// xbl:xbl
                    inPreserve = true;
                    preserveLevel = level;
                    inXBL = true;
                }
            }
        }

        level++;
    }

    @Override
    public void endElement(String uri, String localname, String qName) throws SAXException {

        level--;

        if (inPreserve && level == preserveLevel) {
            // Leaving preserved content
            inPreserve = false;
            inLHHA = false;
            inXBL = false;
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
            endElement(true, XFormsConstants.XXFORMS_NAMESPACE_URI, "text", "xxforms:text");
            endPrefixMapping(true, "xxforms");// for resolving appearance
        } else {
            // Leave element untouched
            endElement(true, uri, localname, qName);
        }

        if (XMLConstants.XHTML_NAMESPACE_URI.equals(uri))
            xhtmlElementLocalnames.remove(xhtmlElementLocalnames.size() - 1);

        namespaceSupport.endElement();
    }

    private boolean isClosestXHTMLAncestorTableContainer() {
        if (xhtmlElementLocalnames.size() > 0) {
            final String closestXHTMLElementLocalname = xhtmlElementLocalnames.get(xhtmlElementLocalnames.size() - 1);
            return SEPARATOR_CONTAINERS.contains(closestXHTMLElementLocalname);
        }
        return false;
    }

    private boolean hasAVT(Attributes attributes) {
        final int attributesCount = attributes.getLength();
        if (attributesCount > 0) {
            for (int i = 0; i < attributesCount; i++) {
                final String currentAttributeURI = attributes.getURI(i);
                if ("".equals(currentAttributeURI) || XMLConstants.XML_URI.equals(currentAttributeURI)) {
                    // For now we only support AVTs on attributes in no namespace or in the XML namespace (for xml:lang)
                    final String attributeValue = attributes.getValue(i);
                    if (XFormsUtils.maybeAVT(attributeValue)) {
                        // This is an AVT
                        return true;
                    }
                }
            }
        }

        return false;
    }

    protected void addNamespaces(String id) {
        final Map<String, String> namespaces = new HashMap<String, String>();
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            final String namespacePrefix = (String) e.nextElement();
            if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals("")) {
                namespaces.put(namespacePrefix, namespaceSupport.getURI(namespacePrefix));
                 // Intern namespace strings to save memory; should use NamePool later
//                namespaces.put(namespacePrefix.intern(), namespaceSupport.getURI(namespacePrefix).intern());
            }
        }
        // Re-add standard "xml" prefix mapping
        // TODO: WHY?
        namespaces.put(XMLConstants.XML_PREFIX, XMLConstants.XML_URI);
        metadata.addNamespaceMapping(id, namespaces);
    }

    protected void addMark(String id, SAXStore.Mark mark) {
        metadata.marks().put(id, mark);
    }

    private void storeXBLBinding(String elementAttribute) {
        elementAttribute = elementAttribute.replace('|', ':');
        final String bindingPrefix = XMLUtils.prefixFromQName(elementAttribute);
        if (bindingPrefix != null) {
            final String bindingURI = namespaceSupport.getURI(bindingPrefix);
            if (bindingURI != null) {
                // Found URI
                metadata.storeXBLBinding(bindingURI, XMLUtils.localNameFromQName(elementAttribute));
            }
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceSupport.startPrefixMapping(prefix, uri);
        startPrefixMapping(true, prefix, uri);
    }

    public Locator getDocumentLocator() {
        return documentLocator;
    }

    private Attributes getAttributesGatherNamespaces(String qName, Attributes attributes, String[] newIdAttribute, final int idIndex) {
        if (isGenerateIds) {
            // Process ids
            if (idIndex == -1) {
                // Create a new "id" attribute, prefixing if needed
                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                newIdAttribute[0] = metadata.idGenerator().getNextId();
                newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, newIdAttribute[0]);
                attributes = newAttributes;
            } else {
                // Keep existing id
                newIdAttribute[0] = attributes.getValue(idIndex);
            }

            // Check for duplicate ids
            // TODO: create Element to provide more location info?
            if (metadata.idGenerator().isDuplicate(newIdAttribute[0]))
                throw new ValidationException("Duplicate id for XForms element: " + newIdAttribute[0],
                        new ExtendedLocationData(new LocationData(getDocumentLocator()), "analyzing control element", Dom4jUtils.saxToDebugElement(qName, attributes), "id", newIdAttribute[0]));

            // TODO: Make sure we can test on the presence of "$" without breaking ids produced by XBLBindings
//            else if (newIdAttribute[0].contains("$"))
//                throw new ValidationException("Id for XForms element cannot contain the \"$\" character: " + newIdAttribute[0],
//                        new ExtendedLocationData(new LocationData(getDocumentLocator()), "analyzing control element", Dom4jUtils.saxToDebugElement(qName, attributes), "id", newIdAttribute[0]));

        } else {
            // Don't create a new id but remember the existing one
            newIdAttribute[0] = attributes.getValue(idIndex);
        }

        // Remember that this id was used
        metadata.idGenerator().add(newIdAttribute[0]);

        // Gather namespace information if there is an id
        if (isGenerateIds || idIndex != -1) {
            addNamespaces(newIdAttribute[0]);
        }

        return attributes;
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.documentLocator = locator;

        if (templateReceiver != null)
            templateReceiver.setDocumentLocator(locator);
        if (extractorReceiver != null)
            extractorReceiver.setDocumentLocator(locator);
    }

    @Override
    public void startDocument() throws SAXException {
        if (templateReceiver != null)
            templateReceiver.startDocument();
        if (extractorReceiver != null)
            extractorReceiver.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        if (templateReceiver != null)
            templateReceiver.endDocument();
        if (extractorReceiver != null)
            extractorReceiver.endDocument();
    }

    private final boolean isKeepHead() {
        return true;
        // TODO: Fix endElement() then enable below
//        return !(inHead && inXForms && !inTitle);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (templateReceiver != null && isKeepHead())
            templateReceiver.characters(ch, start, length);
        if (extractorReceiver != null)
            extractorReceiver.characters(ch, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        // NOP (could handle in future)
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (inPreserve) {
            // Preserve comments within e.g. instances
            if (templateReceiver != null && isKeepHead())
                templateReceiver.comment(ch, start, length);
            if (extractorReceiver != null)
                extractorReceiver.comment(ch, start, length);
        }
    }

    private void startElement(boolean outputToTemplate, String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        if (outputToTemplate) {
            if (templateReceiver != null && isKeepHead())
                templateReceiver.startElement(namespaceURI, localName, qName, atts);

            if (extractorReceiver != null)
                extractorReceiver.startElement(namespaceURI, localName, qName, atts);
        }
    }

    private void endElement(boolean outputToTemplate, String namespaceURI, String localName, String qName) throws SAXException {
        if (outputToTemplate) {
            if (templateReceiver != null && isKeepHead())
                templateReceiver.endElement(namespaceURI, localName, qName);

            if (extractorReceiver != null)
                extractorReceiver.endElement(namespaceURI, localName, qName);
        }
    }

    private void startPrefixMapping(boolean outputToTemplate, String prefix, String uri) throws SAXException {
        if (outputToTemplate) {
            if (templateReceiver != null && isKeepHead())
                templateReceiver.startPrefixMapping(prefix, uri);

            if (extractorReceiver != null)
                extractorReceiver.startPrefixMapping(prefix, uri);
        }
    }

    public void endPrefixMapping(boolean outputToTemplate, String prefix) throws SAXException {
        if (outputToTemplate) {
            if (templateReceiver != null && isKeepHead())
                templateReceiver.endPrefixMapping(prefix);

            if (extractorReceiver != null)
                extractorReceiver.endPrefixMapping(prefix);
        }
    }
}
