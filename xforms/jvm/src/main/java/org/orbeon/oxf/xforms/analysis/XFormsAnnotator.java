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
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.xbl.IndexableBinding;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.orbeon.xml.NamespaceMapping;

/**
 * XMLReceiver that:
 *
 * - adds ids on all the XForms elements which don't have one
 * - gathers namespace information on XForms elements (xf:*, xxf:*, exf:*, xbl:*).
 * - finds AVTs on non-XForms elements
 *   - adds ids to those elements
 *   - produces xxf:attribute elements
 * - finds title information and produces xxf:text elements
 * - register xbl:binding mappings
 * - add ids to elements with XBL bindings
 * - insert fr:xforms-inspector if requested and needed
 *
 * NOTE: There was a thought of merging this with XFormsExtractor but we need a separate annotated document in
 * XFormsToXHTML to produce the output. So if we modify this, we should modify it so that two separate XMLReceiver (at
 * least two separate outputs) are produced, one for the annotated output, another for the extracted output.
 */
public class XFormsAnnotator extends XFormsAnnotatorBase implements XMLReceiver {

    private int level = 0;
    private boolean inHead;         // whether we are in the HTML head
    private boolean inBody;         // whether we are in the HTML body
    private boolean inTitle;        // whether we are in the HTML title

    private boolean inXForms;       // whether we are in a model or other XForms content
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in LHHA, schema or instance
    private int preserveLevel;
    private boolean inLHHA;         // whether we are in LHHA (meaningful only if inPreserve == true)
    private boolean inXBL;          // whether we are in xbl:xbl (meaningful only if inPreserve == true)
    private boolean inXBLBinding;   // whether we are in the body of an XBL binding (meaningful only if inPreserve == true)

    private final boolean isTopLevel;
    private final boolean isRestore;
    private final Metadata metadata;
    private final boolean isGenerateIds;

    private NamespaceContext namespaceContext = new NamespaceContext();

    private final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
    private final AttributesImpl reusableAttributes = new AttributesImpl();
    private final String[] reusableStringArray = new String[1];

    private String htmlTitleElementId;

    @Override
    public boolean isInXBLBinding() {
        return inXBLBinding;
    }

    @Override
    public boolean isInPreserve() {
        return inPreserve;
    }

    /**
     * Constructor for XBL shadow trees and top-level documents.
     *
     * @param templateReceiver      template output (special treatment for marks if this is a SAXStore)
     * @param extractorReceiver     extractor output (can be null for XBL for now)
     * @param metadata              metadata to gather
     */
    public XFormsAnnotator(XMLReceiver templateReceiver, XMLReceiver extractorReceiver, Metadata metadata, boolean isTopLevel) {
        super(templateReceiver, extractorReceiver, metadata, isTopLevel);

        this.metadata      = metadata;
        this.isGenerateIds = true;
        this.isTopLevel    = isTopLevel;
        this.isRestore     = false;
    }

    /**
     * This constructor just computes the namespace mappings and AVT elements and gathers id information.
     */
    public XFormsAnnotator(Metadata metadata) {
        super(null, null, metadata, true);

        // In this mode, all elements that need to have ids already have them, so set safe defaults
        this.metadata      = metadata;
        this.isGenerateIds = false;
        this.isTopLevel    = true;
        this.isRestore     = true;

        metadata.initializeBindingLibraryIfNeeded();
    }

    @Override
    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceContext.startElement();
        final StackElement stackElement = startElement(uri, localname, attributes);

        final int idIndex = attributes.getIndex("id");

        // Entering model or controls
        if (!inXForms && stackElement.isXFormsOrBuiltinExtension()) {
            inXForms = true;
            xformsLevel = level;
        }

        if (inPreserve) {
            // Within preserved content

            if (inLHHA) {
                // Gather id and namespace information about content of LHHA
                if (stackElement.isXForms()) {
                    // Must be xf:output
                    attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);
                } else if (hostLanguageAVTs && hasAVT(attributes)) {
                    // Must be an AVT on an host language element
                    attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);
                }
            } else if (inXBL && level - 1 == preserveLevel && stackElement.isXBL() && "binding".equals(localname)) {

                // Gather id and namespace information
                attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);

                // Gather binding information from xbl:xbl/xbl:binding/@element
                final String elementAtt = attributes.getValue("element");
                if (elementAtt != null)
                    metadata.registerInlineBinding(
                        NamespaceMapping.apply(namespaceContext.current().mappings()),
                        elementAtt,
                        rewriteId(reusableStringArray[0])
                    );
            }
            // Output element
            stackElement.startElement(uri, localname, qName, attributes);

        } else if (stackElement.isXBL()) {
            // This must be xbl:xbl (otherwise we will have isPreserve == true) or xbl:template
            assert localname.equals("xbl") || localname.equals("template") || localname.equals("handler");
            // NOTE: Still process attributes, because the annotator is used to process top-level <xbl:handler> as well.
            attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);
            stackElement.startElement(uri, localname, qName, attributes);
        } else {

            // Only search for XBL bindings when under xh:body or if we are not at the top-level (which means that we
            // are within an XBL component already). But when restoring, always search, because we don't have a distinction
            // between model and view under <static-state>.
            final scala.Option<IndexableBinding> bindingOpt =
                (inBody || isRestore || ! isTopLevel) ? metadata.findBindingForElement(uri, localname, attributes) : NONE_INDEXABLE_BINDING;

            if (bindingOpt.isDefined()) {
                // Element with a binding

                // Create a new id and update the attributes if needed
                attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);
                final String xformsElementId = reusableStringArray[0];

                // Index binding by prefixed id
                metadata.mapBindingToElement(rewriteId(xformsElementId), bindingOpt.get());

                attributes = handleFullUpdateIfNeeded(stackElement, attributes, xformsElementId);

                // Leave element untouched (except for the id attribute)
                stackElement.startElement(uri, localname, qName, attributes);

                // Don't handle the content
                inPreserve = true;
                inXBLBinding = true;
                preserveLevel = level;
            } else if (stackElement.isXFormsOrBuiltinExtension()) {
                // This is an XForms element

                // TODO: can we restrain gathering ids / namespaces to only certain elements (all controls + elements with XPath expressions + models + instances)?

                // Create a new id and update the attributes if needed
                attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);
                final String xformsElementId = reusableStringArray[0];

                attributes = handleFullUpdateIfNeeded(stackElement, attributes, xformsElementId);

                // Rewrite elements / add appearances
                if (inTitle && "output".equals(localname)) {
                    // Special case of xf:output within title, which produces an xxf:text control
                    attributes = SAXUtils.addOrReplaceAttribute(attributes, "", "", "for", htmlTitleElementId);
                    startPrefixMapping2("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI());
                    stackElement.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI(), "text", "xxf:text", attributes);
                } else if (("group".equals(localname) || "switch".equals(localname)) && doesClosestXHTMLRequireSeparatorAppearance()) {
                    // Closest xhtml:* ancestor is xhtml:table|xhtml:tbody|xhtml:thead|xhtml:tfoot|xhtml:tr

                    // Append the new xxf:separator appearance
                    final String existingAppearance = attributes.getValue("appearance");
                    // See: https://github.com/orbeon/orbeon-forms/issues/418
                    attributes = SAXUtils.addOrReplaceAttribute(attributes, "", "", XFormsConstants.APPEARANCE_QNAME().localName(),
                            (existingAppearance != null ? existingAppearance + " " : "") + XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME().qualifiedName());
                    stackElement.startElement(uri, localname, qName, attributes);
                } else if (stackElement.isXForms() && "repeat".equals(localname)) {
                    // Add separator appearance
                    if (doesClosestXHTMLRequireSeparatorAppearance()) {
                        final String existingAppearance = attributes.getValue("appearance");
                        attributes = SAXUtils.addOrReplaceAttribute(attributes, "", "", XFormsConstants.APPEARANCE_QNAME().localName(),
                                (existingAppearance != null ? existingAppearance + " " : "") + XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME().qualifiedName());
                    }

                    // Start xf:repeat
                    stackElement.startElement(uri, localname, qName, attributes);

                    // Start xf:repeat-iteration
                    // NOTE: Use xf:repeat-iteration instead of xxf:iteration so we don't have to deal with a new namespace
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, xformsElementId + "~iteration");
                    final Attributes repeatIterationAttributes = getAttributesGatherNamespaces(uri, qName, reusableAttributes, reusableStringArray, 0);
                    stackElement.startElement(uri, localname + "-iteration", qName + "-iteration", repeatIterationAttributes);
                } else {
                    // Leave element untouched (except for the id attribute)
                    stackElement.startElement(uri, localname, qName, attributes);
                }
            } else {
                // Non-XForms element without an XBL binding

                String htmlElementId = null;

                if (! isRestore) {
                    if (level == 1) {
                        if ("head".equals(localname)) {
                            // Entering head
                            inHead = true;
                        } else if ("body".equals(localname)) {
                            // Entering body
                            inBody = true;
                            metadata.initializeBindingLibraryIfNeeded();
                        }
                    } else if (level == 2) {
                        if (inHead && "title".equals(localname)) {
                            // Entering title
                            inTitle = true;
                            // Make sure there will be an id on the title element (ideally, we would do this only if there is a nested xf:output)
                            attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);
                            htmlElementId = reusableStringArray[0];
                            htmlTitleElementId = htmlElementId;
                        }
                    }
                }

                // NOTE: @id attributes on XHTML elements are rewritten with their effective id during XHTML output by
                // XHTMLElementHandler.
                if ("true".equals(attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI(), "control"))) {
                    // Non-XForms element which we want to turn into a control (specifically, into a group)

                    // Create a new xf:group control which specifies the element name to use. Namespace mappings for the
                    // given QName must be in scope as that QName is the original element name.
                    final AttributesImpl newAttributes = new AttributesImpl(getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex));
                    newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI(), "element", "xxf:element", XMLReceiverHelper.CDATA, qName);

                    startPrefixMapping2("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI());
                    stackElement.startElement(XFormsConstants.XFORMS_NAMESPACE_URI(), "group", "xf:group", newAttributes);
                } else if (hostLanguageAVTs) {
                    // This is a non-XForms element and we allow AVTs
                    final int attributesCount = attributes.getLength();
                    if (attributesCount > 0) {
                        boolean elementWithAVTHasBeenOutput = false;
                        for (int i = 0; i < attributesCount; i++) {
                            final String currentAttributeURI = attributes.getURI(i);
                            if ("".equals(currentAttributeURI) || XMLConstants.XML_URI().equals(currentAttributeURI)) {
                                // For now we only support AVTs on attributes in no namespace or in the XML namespace (for xml:lang)
                                final String attributeValue = attributes.getValue(i);
                                if (XFormsUtils.maybeAVT(attributeValue)) {
                                    // This is an AVT
                                    final String attributeName = attributes.getQName(i);// use qualified name for xml:lang

                                    // Create a new id and update the attributes if needed
                                    if (htmlElementId == null) {
                                        attributes = getAttributesGatherNamespaces(uri, qName, attributes, reusableStringArray, idIndex);
                                        htmlElementId = reusableStringArray[0];

                                        // TODO: Clear all attributes having AVTs or XPath expressions will end up in repeat templates.
                                        // TODO: 2020-02-27: There are no more repeat templates. Check this.
                                    }

                                    if (!elementWithAVTHasBeenOutput) {
                                        // Output the element with the new or updated id attribute
                                        stackElement.startElement(uri, localname, qName, attributes);
                                        elementWithAVTHasBeenOutput = true;
                                    }

                                    // Create a new xxf:attribute control
                                    reusableAttributes.clear();

                                    final AttributesImpl newAttributes = (AttributesImpl) getAttributesGatherNamespaces(uri, qName, reusableAttributes, reusableStringArray, -1);

                                    newAttributes.addAttribute("", "for", "for", XMLReceiverHelper.CDATA, htmlElementId);
                                    newAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, attributeName);
                                    newAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, attributeValue);

                                    newAttributes.addAttribute("", "for-name", "for-name", XMLReceiverHelper.CDATA, localname);

                                    // These extra attributes can be used alongside src/href attributes
                                    if ("src".equals(attributeName) || "href".equals(attributeName)) {
                                        final String urlType = attributes.getValue(XMLConstants.OPS_FORMATTING_URI(), "url-type");
                                        final String portletMode = attributes.getValue(XMLConstants.OPS_FORMATTING_URI(), "portlet-mode");
                                        final String windowState = attributes.getValue(XMLConstants.OPS_FORMATTING_URI(), "window-state");

                                        if (urlType != null)
                                            newAttributes.addAttribute("", "url-type", "url-type", XMLReceiverHelper.CDATA, urlType);
                                        if (portletMode != null)
                                            newAttributes.addAttribute("", "portlet-mode", "portlet-mode", XMLReceiverHelper.CDATA, "portlet-mode");
                                        if (windowState != null)
                                            newAttributes.addAttribute("", "window-state", "window-state", XMLReceiverHelper.CDATA, "window-state");
                                    }

                                    startPrefixMapping2("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI());
                                    stackElement.element(XFormsConstants.XXFORMS_NAMESPACE_URI(), "attribute", "xxf:attribute", newAttributes);
                                    endPrefixMapping2("xxf");
                                }
                            }
                        }

                        // Output the element as is if no AVT was found
                        if (!elementWithAVTHasBeenOutput)
                            stackElement.startElement(uri, localname, qName, attributes);
                    } else {
                        stackElement.startElement(uri, localname, qName, attributes);
                    }

                } else {
                    // No AVT handling, just output the element
                    stackElement.startElement(uri, localname, qName, attributes);
                }
            }
        }

        // Check for preserved content
        if (! inPreserve) {
            if (inXForms) {
                // Preserve as is the content of labels, etc., instances, and schemas
                // Within other xf: check for labels, xf:instance, and xs:schema
                if (stackElement.isXForms()) {
                    inLHHA = XFormsConstants.LHHAElements().contains(localname); // labels, etc. may contain XHTML
                    if (inLHHA || "instance".equals(localname)) {                                  // xf:instance
                        inPreserve = true;
                        preserveLevel = level;
                    }
                } else if ("schema".equals(localname) && XMLConstants.XSD_URI().equals(uri)) {       // xs:schema
                    inPreserve = true;
                    preserveLevel = level;

                }
            } else {
                // At the top-level: check for labels and xbl:xbl
                final boolean isXBLXBL = stackElement.isXBL() && "xbl".equals(localname);
                if (stackElement.isXForms()) {
                    inLHHA = XFormsConstants.LHHAElements().contains(localname); // labels, etc. may contain XHTML
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
    public void endElement(String uri, String localname, String qName) {

        final StackElement stackElement = endElement();

        level--;

        if (inPreserve && level == preserveLevel) {
            // Leaving preserved content
            inPreserve = false;
            inLHHA = false;
            inXBL = false;
            inXBLBinding = false;
        }

        if (inXForms) {

            if (!inPreserve && stackElement.isXForms() && "repeat".equals(localname)) {
                // Close xf:repeat-iteration
                endElement2(uri, localname + "-iteration", qName + "-iteration");
            }

            if (level == xformsLevel) {
                // Leaving model or controls
                inXForms = false;
            }
        }

        if (! isRestore) {
            if (level == 1) {
                if ("head".equals(localname)) {
                    // Exiting head
                    inHead = false;
                } else if ("body".equals(localname)) {
                    // Exiting body

                    // Add fr:xforms-inspector if requested by property AND if not already present
                    final PropertySet propertySet = org.orbeon.oxf.properties.Properties.instance().getPropertySet();
                    final String frURI = "http://orbeon.org/oxf/xml/form-runner";
                    final String inspectorLocal = "xforms-inspector";
                    if (metadata.isTopLevelPart() && propertySet.getBoolean("oxf.epilogue.xforms.inspector", false) && ! metadata.isByNameBindingInUse(frURI, inspectorLocal)) {

                        // Register the fr:xforms-inspector binding
                        reusableAttributes.clear();
                        final scala.Option<IndexableBinding> inspectorBindingOpt =
                            metadata.findBindingForElement(frURI, inspectorLocal, reusableAttributes);

                        if (inspectorBindingOpt.isDefined()) {

                            final String inspectorPrefix = "fr";
                            final String inspectorQName = XMLUtils.buildQName(inspectorPrefix, inspectorLocal);

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, "orbeon-inspector");
                            final Attributes newAttributes = getAttributesGatherNamespaces(frURI, inspectorQName, reusableAttributes, reusableStringArray, 0);
                            final String xformsElementId = reusableStringArray[0];

                            metadata.mapBindingToElement(rewriteId(xformsElementId), inspectorBindingOpt.get());

                            startPrefixMapping2(inspectorPrefix, frURI);
                            stackElement.element(frURI, inspectorLocal, inspectorQName, newAttributes);
                            endPrefixMapping2(inspectorPrefix);
                        }
                    }

                    inBody = false;
                }
            } else if (level == 2) {
                if ("title".equals(localname)) {
                    // Exiting title
                    inTitle = false;
                }
            }
        }

        // Close element with name that was used to open it
        stackElement.endElement();

        if (inTitle && stackElement.isXForms() && "output".equals(localname)) {
            endPrefixMapping2("xxf");// for resolving appearance
        }

        namespaceContext.endElement();
    }

    private boolean hasAVT(Attributes attributes) {
        final int attributesCount = attributes.getLength();
        if (attributesCount > 0) {
            for (int i = 0; i < attributesCount; i++) {
                final String currentAttributeURI = attributes.getURI(i);
                if ("".equals(currentAttributeURI) || XMLConstants.XML_URI().equals(currentAttributeURI)) {
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

    @Override
    public void startPrefixMapping(String prefix, String uri) {
        namespaceContext.startPrefixMapping(prefix, uri);
        startPrefixMapping2(prefix, uri);
    }

    private Attributes getAttributesGatherNamespaces(String uriForDebug, String qNameForDebug, Attributes attributes, String[] newIdAttribute, final int idIndex) {
        final String rawId;
        if (isGenerateIds) {
            // Process ids
            if (idIndex == -1) {
                // Create a new "id" attribute, prefixing if needed
                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                rawId = metadata.idGenerator().nextId();
                newAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, rawId);
                attributes = newAttributes;
            } else {
                // Keep existing id
                rawId = attributes.getValue(idIndex);

                // Check for duplicate ids
                // See https://github.com/orbeon/orbeon-forms/issues/1892
                // TODO: create Element to provide more location info?
                if (isTopLevel && metadata.idGenerator().contains(rawId))
                    throw new ValidationException("Duplicate id for XForms element: " + rawId,
                        new ExtendedLocationData(LocationData.createIfPresent(documentLocator()), "analyzing control element",
                                new String[] { "element", SAXUtils.saxElementToDebugString(uriForDebug, qNameForDebug, attributes), "id", rawId }));
            }

        } else {
            // Don't create a new id but remember the existing one
            rawId = attributes.getValue(idIndex);
        }

        // Remember that this id was used
        if (rawId != null) {
            metadata.idGenerator().add(rawId);

            // Gather namespace information if there is an id
            if (isGenerateIds || idIndex != -1) {
                metadata.addNamespaceMapping(rewriteId(rawId), namespaceContext.currentMapping());
            }
        }

        newIdAttribute[0] = rawId;

        return attributes;
    }

    private static final scala.Option<IndexableBinding> NONE_INDEXABLE_BINDING = scala.Option.apply(null);
}
