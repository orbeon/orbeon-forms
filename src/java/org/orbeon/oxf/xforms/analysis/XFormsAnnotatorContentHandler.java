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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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
public class XFormsAnnotatorContentHandler extends ForwardingContentHandler {

    private SAXStore saxStore;

    private int level = 0;
    private boolean inHead;
    private boolean inTitle;

    private Locator documentLocator;

    private final String containerNamespace;
    private final boolean portlet;


    private final Metadata metadata;
    private final boolean isGenerateIds;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
    private final AttributesImpl reusableAttributes = new AttributesImpl();
    private final String[] reusableStringArray = new String[1];

    private String htmlTitleElementId;

    private boolean inXForms;       // whether we are in a model
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in LHHA, schema or instance
    private int preserveLevel;
    private boolean inLHHA;         // whether we are in LHHA (meaningful only if inPreserve == true)
    private boolean inXBL;          // whether we are in xbl:xbl (meaningful only if inPreserve == true)


    private Map<String, Map<String, String>> xblBindings;   // Map<String uri, Map<String localname, "">>

    public static class Metadata {
        public final IdGenerator idGenerator;
        public final Map<String, Map<String, String>> namespaceMappings;
        public final Map<String, SAXStore.Mark> marks = new HashMap<String, SAXStore.Mark>();

        public Metadata(IdGenerator idGenerator, Map<String, Map<String, String>> namespaceMappings) {
            this.idGenerator = idGenerator;
            this.namespaceMappings = namespaceMappings;
        }

        public Metadata() {
            this.idGenerator = new IdGenerator();
            this.namespaceMappings = new HashMap<String, Map<String, String>>();
        }
    }

    /**
     * Constructor for top-level document.
     *
     * @param contentHandler        output of transformation
     * @param externalContext       external context
     * @param metadata              metadata to gather
     */
    public XFormsAnnotatorContentHandler(SAXStore contentHandler, ExternalContext externalContext, Metadata metadata) {
        this(contentHandler, externalContext.getRequest().getContainerNamespace(), "portlet".equals(externalContext.getRequest().getContainerType()), metadata);
        this.saxStore = contentHandler;
    }

    /**
     * Constructor for XBL shadow trees and top-level documents.
     *
     * @param contentHandler        output of transformation
     * @param containerNamespace    container namespace for portlets
     * @param portlet               whether we are in portlet mode
     * @param metadata              metadata to gather
     */
    public XFormsAnnotatorContentHandler(ContentHandler contentHandler, String containerNamespace, boolean portlet, Metadata metadata) {
        super(contentHandler, contentHandler != null);

        this.containerNamespace = containerNamespace;
        this.portlet = portlet;

        this.metadata = metadata;
        this.isGenerateIds = true;
    }

    /**
     * This constructor just computes the namespace mappings and AVT elements and gathers id information.
     *
     * @param metadata              metadata to gather
     */
    public XFormsAnnotatorContentHandler(Metadata metadata) {

        // In this mode, all elements that need to have ids already have them, so set safe defaults
        this.containerNamespace = "";
        this.portlet = false;

        this.metadata = metadata;
        this.isGenerateIds = false;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

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
                    attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);
                } else if (hostLanguageAVTs && hasAVT(attributes)) {
                    // Must be an AVT on an host language element
                    attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);
                }
            } else if (inXBL && level - 1 == preserveLevel && isXBL && "binding".equals(localname)) {
                // Gather binding information in xbl:xbl/xbl:binding
                final String elementAttribute = attributes.getValue("element");
                if (elementAttribute != null) {
                    storeXBLBinding(elementAttribute);
                }
                // Gather id and namespace information
                attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);
            }
            // Output element
            super.startElement(uri, localname, qName, attributes);
        } else if (isXFormsOrExtension) {
            // This is an XForms element

            // TODO: can we restrain gathering ids / namespaces to only certain elements (all controls + elements with XPath expressions + models + instances)?

            // Create a new id and update the attributes if needed
            attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);

            if (saxStore != null) {
                // Remember mark if xxforms:update="full"
                final String xxformsUpdate = attributes.getValue(XFormsConstants.XXFORMS_UPDATE_QNAME.getNamespaceURI(), XFormsConstants.XXFORMS_UPDATE_QNAME.getName());
                if (XFormsConstants.XFORMS_FULL_UPDATE.equals(xxformsUpdate))
                    metadata.marks.put(reusableStringArray[0], saxStore.getElementMark());
            }

            if (inTitle && "output".equals(localname)) {
                // Special case of xforms:output within title, which produces an xxforms:text control
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "for", htmlTitleElementId);
                super.startPrefixMapping("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI);
                super.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "text", "xxforms:text", attributes);
            } else {
                // Leave element untouched (except for the id attribute)
                super.startElement(uri, localname, qName, attributes);
            }
        } else if (!isXBL && isXBLBinding(uri, localname)) {
            // Element with a binding

            // Create a new id and update the attributes if needed
            attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);

            // Leave element untouched (except for the id attribute)
            super.startElement(uri, localname, qName, attributes);

            // Don't handle the content
            inPreserve = true;
            preserveLevel = level;

        } else if (isXBL) {
            // This must be xbl:xbl (otherwise we will have isPreserve == true)
            // NOTE: Still process attributes, because the annotator is used to process top-level <xbl:handler> as well.
            attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);
            super.startElement(uri, localname, qName, attributes);
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
                    attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);
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
                            if (attributeValue.indexOf('{') != -1) {
                                // This is an AVT
                                final String attributeName = attributes.getQName(i);// use qualified name for xml:lang

                                // Create a new id and update the attributes if needed
                                if (htmlElementId == null) {
                                    attributes = getAttributesGatherNamespaces(attributes, reusableStringArray, idIndex);
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

                                final AttributesImpl newAttributes = (AttributesImpl) getAttributesGatherNamespaces(reusableAttributes, reusableStringArray, -1);

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
                // No AVT handling, just output the element
                super.startElement(uri, localname, qName, attributes);
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

    private boolean hasAVT(Attributes attributes) {
        final int attributesCount = attributes.getLength();
        if (attributesCount > 0) {
            for (int i = 0; i < attributesCount; i++) {
                final String currentAttributeURI = attributes.getURI(i);
                if ("".equals(currentAttributeURI) || XMLConstants.XML_URI.equals(currentAttributeURI)) {
                    // For now we only support AVTs on attributes in no namespace or in the XML namespace (for xml:lang)
                    final String attributeValue = attributes.getValue(i);
                    if (attributeValue.indexOf('{') != -1) {
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
            if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                namespaces.put(namespacePrefix, namespaceSupport.getURI(namespacePrefix));
        }
        // Re-add standard "xml" prefix mapping
        // TODO: WHY?
        namespaces.put(XMLConstants.XML_PREFIX, XMLConstants.XML_URI);
        metadata.namespaceMappings.put(id, namespaces);
    }

    private void storeXBLBinding(String elementAttribute) {
        elementAttribute = elementAttribute.replace('|', ':');
        final String bindingPrefix = XMLUtils.prefixFromQName(elementAttribute);
        if (bindingPrefix != null) {
            final String bindingURI = namespaceSupport.getURI(bindingPrefix);
            if (bindingURI != null) {
                // Found URI

                if (xblBindings == null)
                    xblBindings = new HashMap<String, Map<String, String>>();

                Map<String, String> localnamesMap = xblBindings.get(bindingURI);
                if (localnamesMap == null) {
                    localnamesMap = new HashMap<String, String>();
                    xblBindings.put(bindingURI, localnamesMap);
                }

                localnamesMap.put(XMLUtils.localNameFromQName(elementAttribute), "");
            }
        }
    }

    protected boolean isXBLBinding(String uri, String localname) {
        if (xblBindings == null)
            return false;

        final Map localnamesMap = xblBindings.get(uri);
        return localnamesMap != null && localnamesMap.get(localname) != null;
    }

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

    private Attributes getAttributesGatherNamespaces(Attributes attributes, String[] newIdAttribute, final int idIndex) {
        if (isGenerateIds) {
            // Process ids
            final String newIdAttributeUnprefixed;
            if (idIndex == -1) {
                // Create a new "id" attribute, prefixing if needed
                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                newIdAttributeUnprefixed = metadata.idGenerator.getNextId();
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
            if (metadata.idGenerator.isDuplicate(newIdAttribute[0])) // TODO: create Element to provide more location info?
                throw new ValidationException("Duplicate id for XForms element: " + newIdAttributeUnprefixed,
                        new ExtendedLocationData(new LocationData(getDocumentLocator()), "analyzing control element", new String[] { "id", newIdAttributeUnprefixed }, false));

            // Prefix observer id
            if (portlet) {
                final int observerIndex = attributes.getIndex(XFormsConstants.XML_EVENTS_NAMESPACE_URI, "observer");
                if (observerIndex != -1) {
                    final AttributesImpl newAttributes = new AttributesImpl(attributes);
                    String newObserverAttributeUnprefixed = newAttributes.getValue(observerIndex);
                    newAttributes.setValue(observerIndex, containerNamespace + newObserverAttributeUnprefixed);
                    attributes = newAttributes;
                }
            }

        } else {
            // Don't create a new id but remember the existing one
            newIdAttribute[0] = attributes.getValue(idIndex);
        }

        // Remember that this id was used
        metadata.idGenerator.add(newIdAttribute[0]);

        // Gather namespace information if there is an id
        if (metadata.namespaceMappings != null && (isGenerateIds || idIndex != -1)) {
            addNamespaces(newIdAttribute[0]);
        }

        return attributes;
    }
}
