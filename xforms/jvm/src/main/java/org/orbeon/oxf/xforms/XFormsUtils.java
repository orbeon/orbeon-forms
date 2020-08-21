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
package org.orbeon.oxf.xforms;

import org.apache.commons.lang3.StringUtils;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.orbeon.dom.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.DebugProcessor;
import org.orbeon.oxf.util.MarkupUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl;
import org.orbeon.oxf.xforms.model.DataModel;
import org.orbeon.oxf.xforms.model.InstanceData;
import org.orbeon.oxf.xforms.state.ControlState;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.VirtualNode;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.TransformerHandler;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class XFormsUtils {

    //@XPathFunction
    public static String encodeXMLAsDOM(org.w3c.dom.Node node) {
        try {
            return EncodeDecode.encodeXML(TransformerUtils.domToDom4jDocument(node), XFormsProperties.isGZIPState(), true, false);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static String encodeXML(Document documentToEncode, boolean encodeLocationData) {
        return EncodeDecode.encodeXML(documentToEncode, XFormsProperties.isGZIPState(), true, encodeLocationData);
    }

    private static final HTMLSchema TAGSOUP_HTML_SCHEMA = new HTMLSchema();

    private static void htmlStringToResult(String value, LocationData locationData, Result result) {
        try {
            final XMLReader xmlReader = new org.ccil.cowan.tagsoup.Parser();
            xmlReader.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, TAGSOUP_HTML_SCHEMA);
            xmlReader.setFeature(org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true);
            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            identity.setResult(result);
            xmlReader.setContentHandler(identity);
            final InputSource inputSource = new InputSource();
            inputSource.setCharacterStream(new StringReader(value));
            xmlReader.parse(inputSource);
        } catch (Exception e) {
            throw new ValidationException("Cannot parse value as text/html for value: '" + value + "'", locationData);
        }
//			r.setFeature(Parser.CDATAElementsFeature, false);
//			r.setFeature(Parser.namespacesFeature, false);
//			r.setFeature(Parser.ignoreBogonsFeature, true);
//			r.setFeature(Parser.bogonsEmptyFeature, false);
//			r.setFeature(Parser.defaultAttributesFeature, false);
//			r.setFeature(Parser.translateColonsFeature, true);
//			r.setFeature(Parser.restartElementsFeature, false);
//			r.setFeature(Parser.ignorableWhitespaceFeature, true);
//			r.setProperty(Parser.scannerProperty, new PYXScanner());
//          r.setProperty(Parser.lexicalHandlerProperty, h);
    }


    //@XPathFunction
    public static org.w3c.dom.Document htmlStringToDocumentTagSoup(String value, LocationData locationData) {
        final org.w3c.dom.Document document = XMLParsing.createDocument();
        final DOMResult domResult = new DOMResult(document);
        htmlStringToResult(value, locationData, domResult);
        return document;
    }

    public static Document htmlStringToDom4jTagSoup(String value, LocationData locationData) {
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        htmlStringToResult(value, locationData, documentResult);
        return documentResult.getDocument();
    }

    // TODO: implement server-side plain text output with <br> insertion
//    public static void streamPlainText(final ContentHandler contentHandler, String value, LocationData locationData, final String xhtmlPrefix) {
//        // 1: Split string along 0x0a and remove 0x0d (?)
//        // 2: Output string parts, and between them, output <xhtml:br> element
//        try {
//            contentHandler.characters(filteredValue.toCharArray(), 0, filteredValue.length());
//        } catch (SAXException e) {
//            throw new OXFException(e);
//        }
//    }

    public static void streamHTMLFragment(XMLReceiver xmlReceiver, String value, LocationData locationData, String xhtmlPrefix) {
        if (StringUtils.isNotBlank(value)) { // don't parse blank values
            final org.w3c.dom.Document htmlDocument = htmlStringToDocumentTagSoup(value, locationData);

            // Stream fragment to the output
            if (htmlDocument != null) {
                TransformerUtils.sourceToSAX(new DOMSource(htmlDocument), new HTMLBodyXMLReceiver(xmlReceiver, xhtmlPrefix));
            }
        }
    }

    /**
     * Get the value of a child element known to have only static content.
     *
     * @param childElement          element to evaluate (xf:label, etc.)
     * @param acceptHTML            whether the result may contain HTML
     * @param containsHTML          whether the result actually contains HTML (null allowed)
     * @return                      string containing the result of the evaluation
     */
    public static String getStaticChildElementValue(final String prefix, final Element childElement, final boolean acceptHTML, final boolean[] containsHTML) {

        assert childElement != null;

        // No HTML found by default
        if (containsHTML != null)
            containsHTML[0] = false;

        // Try to get inline value
        {
            final StringBuilder sb = new StringBuilder(20);

            // Visit the subtree and serialize

            // NOTE: It is a little funny to do our own serialization here, but the alternative is to build a DOM and
            // serialize it, which is not trivial because of the possible interleaved xf:output's. Furthermore, we
            // perform a very simple serialization of elements and text to simple (X)HTML, not full-fledged HTML or XML
            // serialization.

            Dom4jUtils.visitSubtree(childElement, new LHHAElementVisitorListener(prefix, acceptHTML, containsHTML, sb, childElement));
            if (acceptHTML && containsHTML != null && !containsHTML[0]) {
                // We went through the subtree and did not find any HTML
                // If the caller supports the information, return a non-escaped string so we can optimize output later
                return MarkupUtils.unescapeXmlMinimal(sb.toString());
            } else {
                // We found some HTML, just return it
                return sb.toString();
            }
        }
    }

    /**
     * Get the value of an element by trying single-node binding, value attribute, linking attribute, and inline value
     * (including nested XHTML and xf:output elements).
     *
     * This may return an HTML string if HTML is accepted and found, or a plain string otherwise.
     *
     * @param container             current XBLContainer
     * @param contextStack          context stack for XPath evaluation
     * @param sourceEffectiveId     source effective id for id resolution
     * @param childElement          element to evaluate (xf:label, etc.)
     * @param acceptHTML            whether the result may contain HTML
     * @param containsHTML          whether the result actually contains HTML (null allowed)
     * @return                      string containing the result of the evaluation, null if evaluation failed (see comments)
     */
    public static String getElementValue(final XBLContainer container,
                                         final XFormsContextStack contextStack, final String sourceEffectiveId,
                                         final Element childElement, final boolean acceptHTML, final boolean defaultHTML,
                                         final boolean[] containsHTML) {

        // No HTML found by default
        if (containsHTML != null)
            containsHTML[0] = defaultHTML;

        final BindingContext currentBindingContext = contextStack.getCurrentBindingContext();

        // "the order of precedence is: single node binding attributes, linking attributes, inline text."

        // Try to get single node binding
        {
            final boolean hasSingleNodeBinding = currentBindingContext.newBind();
            if (hasSingleNodeBinding) {
                final Item boundItem = currentBindingContext.getSingleItem();
                final String tempResult = DataModel.getValue(boundItem);
                if (tempResult != null) {
                    return (acceptHTML && containsHTML == null) ? MarkupUtils.escapeXmlMinimal(tempResult) : tempResult;
                } else {
                    // There is a single-node binding but it doesn't point to an acceptable item
                    return null;
                }
            }
        }

        // Try to get value attribute
        // NOTE: This is an extension attribute not standard in XForms 1.0 or 1.1
        {
            final String valueAttribute = childElement.attributeValue(XFormsConstants.VALUE_QNAME());
            final boolean hasValueAttribute = valueAttribute != null;
            if (hasValueAttribute) {
                final List<Item> currentNodeset = currentBindingContext.nodeset();
                if (! currentNodeset.isEmpty()) {
                    String tempResult;
                    try {
                        tempResult = XPathCache.evaluateAsString(
                            currentNodeset,
                            currentBindingContext.position(),
                            valueAttribute,
                            container.getNamespaceMappings(childElement),
                            contextStack.getCurrentBindingContext().getInScopeVariables(),
                            container.getContainingDocument().functionLibrary(),
                            contextStack.getFunctionContext(sourceEffectiveId),
                            null,
                            (LocationData) childElement.getData(),
                            container.getContainingDocument().getRequestStats().getReporter()
                        );
                    } catch (Exception e) {
                        XFormsError.handleNonFatalXPathError(container, e);
                        tempResult = "";
                    }

                    return (acceptHTML && containsHTML == null) ? MarkupUtils.escapeXmlMinimal(tempResult) : tempResult;
                } else {
                    // There is a value attribute but the evaluation context is empty
                    return null;
                }
            }
        }

        // NOTE: Linking attribute is deprecated in XForms 1.1 and we no longer support it.

        // Try to get inline value
        {
            final StringBuilder sb = new StringBuilder(20);

            // Visit the subtree and serialize

            // NOTE: It is a little funny to do our own serialization here, but the alternative is to build a DOM and
            // serialize it, which is not trivial because of the possible interleaved xf:output's. Furthermore, we
            // perform a very simple serialization of elements and text to simple (X)HTML, not full-fledged HTML or XML
            // serialization.
            Dom4jUtils.visitSubtree(
                childElement,
                new LHHAElementVisitorListener(
                    container,
                    contextStack,
                    sourceEffectiveId,
                    acceptHTML,
                    defaultHTML,
                    containsHTML,
                    sb,
                    childElement
                )
            );
            if (acceptHTML && containsHTML != null && ! containsHTML[0]) {
                // We went through the subtree and did not find any HTML
                // If the caller supports the information, return a non-escaped string so we can optimize output later
                return MarkupUtils.unescapeXmlMinimal(sb.toString());
            } else {
                // We found some HTML, just return it
                return sb.toString();
            }
        }
    }

    public static String resolveRenderURL(XFormsContainingDocument containingDocument, Element currentElement, String url, boolean skipRewrite) {
        final URI resolvedURI = resolveXMLBase(containingDocument, currentElement, url);

        final String resolvedURIStringNoPortletFragment = uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument, resolvedURI);

        return skipRewrite ? resolvedURIStringNoPortletFragment :
                NetUtils.getExternalContext().getResponse().rewriteRenderURL(resolvedURIStringNoPortletFragment, null, null);
    }

    public static String resolveActionURL(XFormsContainingDocument containingDocument, Element currentElement, String url) {
        final URI resolvedURI = resolveXMLBase(containingDocument, currentElement, url);

        final String resolvedURIStringNoPortletFragment = uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument, resolvedURI);

        return NetUtils.getExternalContext().getResponse().rewriteActionURL(resolvedURIStringNoPortletFragment, null, null);
    }

    private static String uriToStringRemoveFragmentForPortletAndEmbedded(XFormsContainingDocument containingDocument, URI resolvedURI) {
        if ((containingDocument.isPortletContainer() || containingDocument.isEmbedded()) && resolvedURI.getFragment() != null) {
            // Page was loaded from a portlet or embedding API and there is a fragment, remove it
            try {
                return new URI(resolvedURI.getScheme(), resolvedURI.getRawAuthority(), resolvedURI.getRawPath(), resolvedURI.getRawQuery(), null).toString();
            } catch (URISyntaxException e) {
                throw new OXFException(e);
            }
        } else {
            return resolvedURI.toString();
        }
    }

    /**
     * Resolve a resource URL including xml:base resolution.
     *
     *
     * @param containingDocument    current document
     * @param element               element used to start resolution (if null, no resolution takes place)
     * @param url                   URL to resolve
     * @param rewriteMode           rewrite mode (see ExternalContext.Response)
     * @return                      resolved URL
     */
    public static String resolveResourceURL(XFormsContainingDocument containingDocument, Element element, String url, int rewriteMode) {

        final URI resolvedURI = resolveXMLBase(containingDocument, element, url);

        return NetUtils.getExternalContext().getResponse().rewriteResourceURL(resolvedURI.toString(), rewriteMode);
    }

    /**
     * Resolve a resource URL including xml:base resolution.
     *
     * @param containingDocument    current document
     * @param element               element used to start resolution (if null, no resolution takes place)
     * @param url                   URL to resolve
     * @param rewriteMode           rewrite mode (see ExternalContext.Response)
     * @return                      resolved URL
     */
    public static String resolveServiceURL(XFormsContainingDocument containingDocument, Element element, String url, int rewriteMode) {

        final URI resolvedURI = resolveXMLBase(containingDocument, element, url);

        return URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext().getRequest(), resolvedURI.toString(), rewriteMode);
    }

    /**
     * Resolve attribute value templates (AVTs).
     *
     * @param xpathContext      current XPath context
     * @param contextNode       context node for evaluation
     * @param attributeValue    attribute value
     * @return                  resolved attribute value
     */
    public static String resolveAttributeValueTemplates(XFormsContainingDocument containingDocument, XPathCache.XPathContext xpathContext, NodeInfo contextNode, String attributeValue) {

        if (attributeValue == null)
            return null;

        return XPathCache.evaluateAsAvt(xpathContext, contextNode, attributeValue, containingDocument.getRequestStats().getReporter());
    }

    /**
     * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
     * the resolution, and using the document's request URL as a base.
     *
     * @param containingDocument    current document
     * @param element   element used to start resolution (if null, no resolution takes place)
     * @param uri       URI to resolve
     * @return          resolved URI
     */
    public static URI resolveXMLBase(XFormsContainingDocument containingDocument, Element element, String uri) {
        return resolveXMLBase(element, containingDocument.getRequestPath(), uri);
    }

    /**
     * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
     * the resolution.
     *
     * @param element   element used to start resolution (if null, no resolution takes place)
     * @param baseURI   optional base URI
     * @param uri       URI to resolve
     * @return          resolved URI
     */
    public static URI resolveXMLBase(Element element, String baseURI, String uri) {
        try {
            // Allow for null Element
            if (element == null)
                return new URI(uri);

            final List<String> xmlBaseValues = new ArrayList<String>();

            // Collect xml:base values
            Element currentElement = element;
            do {
                final String xmlBaseAttribute = currentElement.attributeValue(XMLConstants.XML_BASE_QNAME());
                if (xmlBaseAttribute != null)
                    xmlBaseValues.add(xmlBaseAttribute);
                currentElement = currentElement.getParent();
            } while(currentElement != null);

            // Append base if needed
            if (baseURI != null)
                xmlBaseValues.add(baseURI);

            // Go from root to leaf
            Collections.reverse(xmlBaseValues);
            xmlBaseValues.add(uri);

            // Resolve paths from root to leaf
            URI result = null;
            for (final String currentXMLBase: xmlBaseValues) {
                final URI currentXMLBaseURI = new URI(currentXMLBase);
                result = (result == null) ? currentXMLBaseURI : result.resolve(currentXMLBaseURI);
            }
            return result;
        } catch (URISyntaxException e) {
            throw new ValidationException("Error while resolving URI: " + uri, e, (element != null) ? (LocationData) element.getData() : null);
        }
    }

    /**
     * Resolve f:url-norewrite attributes on this element, taking into account ancestor f:url-norewrite attributes for
     * the resolution.
     *
     * @param element   element used to start resolution
     * @return          true if rewriting is turned off, false otherwise
     */
    public static boolean resolveUrlNorewrite(Element element) {
        Element currentElement = element;
        do {
            final String urlNorewriteAttribute = currentElement.attributeValue(XMLConstants.FORMATTING_URL_NOREWRITE_QNAME());
            // Return the first ancestor value found
            if (urlNorewriteAttribute != null)
                return "true".equals(urlNorewriteAttribute);
            currentElement = currentElement.getParent();
        } while(currentElement != null);

        // Default is to rewrite
        return false;
    }

    /**
     * Log a message and Document.
     *
     * @param debugMessage  the message to display
     * @param document      the Document to display
     */
    public static void logDebugDocument(String debugMessage, Document document) {
        DebugProcessor.logger.info(debugMessage + ":\n" + Dom4jUtils.domToPrettyStringJava(document));
    }

    /**
     * Prefix an id with the container namespace if needed. If the id is null, return null.
     *
     * @param containingDocument    current ContainingDocument
     * @param id                    id to prefix
     * @return                      prefixed id or null
     */
    public static String namespaceId(XFormsContainingDocument containingDocument, CharSequence id) {
        if (id == null)
            return null;
        else
            return containingDocument.getContainerNamespace() + id;
    }

    /**
     * Remove the container namespace prefix if possible. If the id is null, return null.
     *
     * @param containingDocument    current ContainingDocument
     * @param id                    id to de-prefix
     * @return                      de-prefixed id if possible or null
     */
    public static String deNamespaceId(XFormsContainingDocument containingDocument, String id) {
        if (id == null)
            return null;

        final String containerNamespace = containingDocument.getContainerNamespace();
        if (containerNamespace.length() > 0 && id.startsWith(containerNamespace))
            return id.substring(containerNamespace.length());
        else
            return id;
    }

    /**
     * Return LocationData for a given node, null if not found.
     *
     * @param node  node containing the LocationData
     * @return      LocationData or null
     */
    public static LocationData getNodeLocationData(Node node) {
        final Object data;
        {
            if (node instanceof Element)
                data = ((Element) node).getData();
            else if (node instanceof Attribute)
                data = ((Attribute) node).getData();
            else
                data = null;
            // TODO: other node types
        }
        if (data == null)
            return null;
        else if (data instanceof LocationData)
            return (LocationData) data;
        else if (data instanceof InstanceData)
            return ((InstanceData) data).getLocationData();

        return null;
    }

    /**
     * Return the underlying Node from the given NodeInfo, possibly converting it to a Dom4j Node. Changes to the returned Node may or may not
     * reflect on the original, depending on its type.
     *
     * @param nodeInfo      NodeInfo to process
     * @return              Node
     */
    public static Node getNodeFromNodeInfoConvert(NodeInfo nodeInfo) {
        if (nodeInfo instanceof VirtualNode)
            return (Node) ((VirtualNode) nodeInfo).getUnderlyingNode();
        else if (nodeInfo.getNodeKind() == org.w3c.dom.Node.ATTRIBUTE_NODE) {
            return DocumentFactory.createAttribute(
                QName.apply(nodeInfo.getLocalPart(), Namespace$.MODULE$.apply(nodeInfo.getPrefix(), nodeInfo.getURI())),
                nodeInfo.getStringValue()
            );
        } else
            return TransformerUtils.tinyTreeToDom4j((nodeInfo.getParent() instanceof DocumentInfo) ? nodeInfo.getParent() : nodeInfo);
    }

    /**
     * Return the underlying Node from the given NodeInfo if possible. If not, throw an exception with the given error
     * message.
     *
     * @param nodeInfo      NodeInfo to process
     * @param errorMessage  error message to throw
     * @return              Node if found
     */
    public static Node getNodeFromNodeInfo(NodeInfo nodeInfo, String errorMessage) {
        if (!(nodeInfo instanceof VirtualNode))
            throw new OXFException(errorMessage);

        return (Node) ((VirtualNode) nodeInfo).getUnderlyingNode();
    }

    private static String[] voidElementsNames = {
        // HTML 5: http://www.w3.org/TR/html5/syntax.html#void-elements
        "area", "base", "br", "col", "command", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr",
        // Legacy
        "basefont", "frame", "isindex"
    };
    private static final Set<String> voidElements = new HashSet<String>(Arrays.asList(voidElementsNames));

    public static boolean isVoidElement(String elementName) {
        return voidElements.contains(elementName);
    }

    private static class LHHAElementVisitorListener implements Dom4jUtils.VisitorListener {
        private final String prefix;
        private final XBLContainer container;
        private final XFormsContextStack contextStack;
        private final String sourceEffectiveId;
        private final boolean acceptHTML;
        private final boolean defaultHTML;
        private final boolean[] containsHTML;
        private final StringBuilder sb;
        private final Element childElement;
        private final boolean hostLanguageAVTs;

        // Constructor for "static" case, i.e. when we know the child element cannot have dynamic content
        public LHHAElementVisitorListener(String prefix, boolean acceptHTML, boolean[] containsHTML, StringBuilder sb, Element childElement) {
            this.prefix = prefix;
            this.container = null;
            this.contextStack = null;
            this.sourceEffectiveId = null;
            this.acceptHTML = acceptHTML;
            this.defaultHTML = false;
            this.containsHTML = containsHTML;
            this.sb = sb;
            this.childElement = childElement;
            this.hostLanguageAVTs = false;
        }

        // Constructor for "dynamic" case, i.e. when we know the child element can have dynamic content
        public LHHAElementVisitorListener(XBLContainer container, XFormsContextStack contextStack,
                                          String sourceEffectiveId, boolean acceptHTML, boolean defaultHTML, boolean[] containsHTML,
                                          StringBuilder sb, Element childElement) {
            this.prefix = container.getFullPrefix();
            this.container = container;
            this.contextStack = contextStack;
            this.sourceEffectiveId = sourceEffectiveId;
            this.acceptHTML = acceptHTML;
            this.defaultHTML = defaultHTML;
            this.containsHTML = containsHTML;
            this.sb = sb;
            this.childElement = childElement;
            this.hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs();
        }

        private boolean lastIsStart = false;

        public void startElement(Element element) {
            if (element.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME())) {
                // This is an xf:output nested among other markup

                final XFormsOutputControl outputControl = new XFormsOutputControl(container, null, element, null) {
                    // Override this as super.getContextStack() gets the containingDocument's stack, and here we need whatever is the current stack
                    // Probably need to modify super.getContextStack() at some point to NOT use the containingDocument's stack
                    @Override
                    public XFormsContextStack getContextStack() {
                        return LHHAElementVisitorListener.this.contextStack;
                    }

                    @Override
                    public String getEffectiveId() {
                        // Return given source effective id, so we have a source effective id for resolution of index(), etc.
                        return sourceEffectiveId;
                    }

                    @Override
                    public boolean isAllowedBoundItem(Item item) {
                        return DataModel.isAllowedBoundItem(item);
                    }
                };

                final boolean isHTMLMediatype =
                    ! defaultHTML && LHHAAnalysis.isHTML(element) || defaultHTML && ! LHHAAnalysis.isPlainText(element);

                contextStack.pushBinding(element, sourceEffectiveId, outputControl.getChildElementScope(element));
                outputControl.setBindingContext(contextStack.getCurrentBindingContext(), true, false, false, scala.Option.<ControlState>apply(null));
                contextStack.popBinding();

                if (outputControl.isRelevant()) {
                    if (acceptHTML) {
                        if (isHTMLMediatype) {
                            if (containsHTML != null)
                                containsHTML[0] = true; // this indicates for sure that there is some nested HTML
                            sb.append(outputControl.getExternalValue());
                        } else {
                            // Mediatype is not HTML so we don't escape
                            sb.append(MarkupUtils.escapeXmlMinimal(outputControl.getExternalValue()));
                        }
                    } else {
                        if (isHTMLMediatype) {
                            // HTML is not allowed here, better tell the user
                            throw new OXFException("HTML not allowed within element: " + childElement.getName());
                        } else {
                            // Mediatype is not HTML so we don't escape
                            sb.append(outputControl.getExternalValue());
                        }
                    }
                }
            } else {
                // This is a regular element, just serialize the start tag to no namespace

                // If HTML is not allowed here, better tell the user
                if (!acceptHTML)
                    throw new OXFException("Nested XHTML or XForms not allowed within element: " + childElement.getName());

                if (containsHTML != null)
                    containsHTML[0] = true;// this indicates for sure that there is some nested HTML

                sb.append('<');
                sb.append(element.getName());
                final List attributes = element.attributes();
                if (attributes.size() > 0) {
                    for (Object attribute: attributes) {
                        final Attribute currentAttribute = (Attribute) attribute;

                        final String currentAttributeName = currentAttribute.getName();
                        final String currentAttributeValue = currentAttribute.getValue();

                        final String resolvedValue;
                        if (hostLanguageAVTs && maybeAVT(currentAttributeValue)) {
                            // This is an AVT, use attribute control to produce the output
                            final XXFormsAttributeControl attributeControl
                                    = new XXFormsAttributeControl(container, element, currentAttributeName, currentAttributeValue, element.getName());

                            contextStack.pushBinding(element, sourceEffectiveId, attributeControl.getChildElementScope(element));
                            attributeControl.setBindingContext(contextStack.getCurrentBindingContext(), true, false, false, scala.Option.<ControlState >apply(null));
                            contextStack.popBinding();

                            resolvedValue = attributeControl.getExternalValue();
                        } else if (currentAttributeName.equals("id")) {
                            // This is an id, prefix if needed
                            resolvedValue = prefix + currentAttributeValue;
                        } else {
                            // Simply use control value
                            resolvedValue = currentAttributeValue;
                        }

                        // Only consider attributes in no namespace
                        if ("".equals(currentAttribute.getNamespaceURI())) {
                            sb.append(' ');
                            sb.append(currentAttributeName);
                            sb.append("=\"");
                            if (resolvedValue != null)
                                sb.append(MarkupUtils.escapeXmlMinimal(resolvedValue));
                            sb.append('"');
                        }
                    }
                }
                sb.append('>');
                lastIsStart = true;
            }
        }

        public void endElement(Element element) {
            final String elementName = element.getName();
            if ((!lastIsStart || !isVoidElement(elementName)) && !element.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME())) {
                // This is a regular element, just serialize the end tag to no namespace
                // UNLESS the element was just opened. This means we output <br>, not <br></br>, etc.
                sb.append("</");
                sb.append(elementName);
                sb.append('>');
            }
            lastIsStart = false;
        }

        public void text(Text text) {
            sb.append(acceptHTML ? MarkupUtils.escapeXmlMinimal(text.getStringValue()) : text.getStringValue());
            lastIsStart = false;
        }
    }

    public static String escapeJavaScript(String value) {
        value = StringUtils.replace(value, "\\", "\\\\");
        value = StringUtils.replace(value, "\"", "\\\"");
        value = StringUtils.replace(value, "\n", "\\n");
        value = StringUtils.replace(value, "\t", "\\t");
        return value;
    }

    public static boolean maybeAVT(String attributeValue) {
        return attributeValue.indexOf('{') != -1;
    }

    /**
     * Return the id of the enclosing HTML <form> element.
     *
     * @param containingDocument    containing document
     * @return                      id, possibly namespaced
     */
    public static String getNamespacedFormId(XFormsContainingDocument containingDocument) {
        return namespaceId(containingDocument, "xforms-form");
    }

    /**
     * Get an element's id.
     *
     * @param element   element to check
     * @return          id or null
     */
    public static String getElementId(Element element) {
        return element.attributeValue(XFormsConstants.ID_QNAME());
    }
}
