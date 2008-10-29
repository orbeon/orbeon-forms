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
package org.orbeon.oxf.xforms.xbl;

import org.apache.commons.lang.StringUtils;
import org.dom4j.*;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.NamespaceSupport3;
import org.orbeon.oxf.xml.SimpleForwardingContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.*;

/**
 * XBL utilities.
 */
public class XBLUtils {
    /**
     * Generate shadow content for the given control id and XBL binding.
     *
     * @param boundElement      element to which the binding applies
     * @param binding           corresponding <xbl:binding>
     * @return                  shadow tree document
     */
    public static Document generateXBLShadowContent(final PipelineContext pipelineContext, final DocumentWrapper documentWrapper, final Element boundElement, Element binding) {
        final Element templateElement = binding.element(XFormsConstants.XBL_TEMPLATE_QNAME);
        if (templateElement != null) {
            // TODO: in script mode, XHTML elements in template should only be kept during page generation

            // Here we create a completely separate document
            // Copy as the template element may be used many times
            final Document shadowTreeDocument = Dom4jUtils.createDocumentCopyParentNamespaces(templateElement);

            Dom4jUtils.visitSubtree(shadowTreeDocument.getRootElement(), new Dom4jUtils.VisitorListener() {
                public void startElement(Element element) {

                    // Handle xbl:content

                    final boolean isXBLContent = element.getQName().equals(XFormsConstants.XBL_CONTENT_QNAME);
                    final List resultingNodes;
                    if (isXBLContent) {
                        final String includesAttribute = element.attributeValue("includes");
                        final List contentToInsert;
                        if (includesAttribute == null) {
                            // All bound node content must be copied over
                            final List elementContent = boundElement.content();
                            final List clonedContent = new ArrayList();
                            for (Iterator i = elementContent.iterator(); i.hasNext();) {
                                final Node node = (Node) i.next();
                                if (!(node instanceof Namespace)) {
                                     clonedContent.add(Dom4jUtils.createCopy(node));
                                }
                            }

                            contentToInsert = clonedContent;
                        } else {
                            // Apply CSS selector

                            // Convert CSS to XPath
                            final String xpathExpression = cssToXPath(includesAttribute);

                            final NodeInfo boundElementInfo = documentWrapper.wrap(boundElement);

                            // TODO: don't use getNamespaceContext() as this is already computed for the bound element
                            final List elements = XPathCache.evaluate(pipelineContext, boundElementInfo, xpathExpression, Dom4jUtils.getNamespaceContext(element),
                                    null, null, null, null, null);// TODO: locationData

                            if (elements.size() > 0) {
                                // Clone all the resulting elements
                                contentToInsert = new ArrayList(elements.size());
                                for (Iterator i = elements.iterator(); i.hasNext();) {
                                    final NodeInfo currentNodeInfo = (NodeInfo) i.next();
                                    final Element currentElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                                    contentToInsert.add(Dom4jUtils.createCopy(currentElement));
                                }
                            } else {
                                contentToInsert = null;
                            }
                        }

                        // Insert content if any
                        if (contentToInsert != null && contentToInsert.size() > 0) {
                            final List parentContent = element.getParent().content();
                            final int elementIndex = parentContent.indexOf(element);
                            parentContent.addAll(elementIndex, contentToInsert);
                        }

                        // Remove <xbl:content> from shadow tree
                        element.detach();

                        resultingNodes = contentToInsert;
                    } else {
                        // Element is simply kept
                        resultingNodes = Collections.singletonList(element);
                    }

                    // Handle attribute forwarding
                    final Attribute xblAttr = element.attribute(XFormsConstants.XBL_ATTR_QNAME);
                    if (xblAttr != null) {
                        // Detach attribute (not strictly necessary?)
                        xblAttr.detach();
                        // Get attribute value
                        final String xblAttrString = xblAttr.getValue();
                        final StringTokenizer st = new StringTokenizer(xblAttrString);
                        while (st.hasMoreTokens()) {
                            final String currentValue = st.nextToken();

                            final int equalIndex = currentValue.indexOf('=');
                            if (equalIndex == -1) {
                                // No a=b pair, just a single QName
                                final QName valueQName = Dom4jUtils.extractTextValueQName(element, currentValue);
                                if (!valueQName.getNamespaceURI().equals(XFormsConstants.XBL_NAMESPACE_URI)) {
                                     // This is not xbl:text, copy the attribute
                                    setAttribute(resultingNodes, valueQName, boundElement.attributeValue(valueQName));
                                } else {
                                    // This is xbl:text
                                    // "The xbl:text value cannot occur by itself in the list"
                                }

                            } else {
                                // a=b pair
                                final QName leftSideQName; {
                                final String leftSide = currentValue.substring(0, equalIndex);
                                    leftSideQName = Dom4jUtils.extractTextValueQName(element, leftSide);
                                }
                                final QName rightSideQName; {
                                    final String rightSide = currentValue.substring(equalIndex + 1);
                                    rightSideQName = Dom4jUtils.extractTextValueQName(element, rightSide);
                                }

                                final boolean isLeftSideXBLText = leftSideQName.getNamespaceURI().equals(XFormsConstants.XBL_NAMESPACE_URI);
                                final boolean isRightSideXBLText = rightSideQName.getNamespaceURI().equals(XFormsConstants.XBL_NAMESPACE_URI);

                                final String rightSideValue;
                                if (!isRightSideXBLText) {
                                     // Get attribute value
                                    rightSideValue = boundElement.attributeValue(rightSideQName);
                                } else {
                                    // Get text value

                                    // "any text nodes (including CDATA nodes and whitespace text nodes) that are
                                    // explicit children of the bound element must have their data concatenated"
                                    rightSideValue = boundElement.getText();// must use getText() and not stringValue()
                                }

                                if (rightSideValue != null) {// not sure if XBL says what should happen if the source attribute is not found
                                    if (!isLeftSideXBLText) {
                                         // Set attribute value
                                        setAttribute(resultingNodes, leftSideQName, rightSideValue);
                                    } else {
                                        // Set text value

                                        // "value of the attribute on the right-hand side are to be represented as text
                                        // nodes underneath the shadow element"

                                        // TODO: "If the element has any child nodes in the DOM (any nodes, including
                                        // comment nodes, whitespace text nodes, or even empty CDATA nodes) then the pair
                                        // is in error and UAs must ignore it, meaning the attribute value is not forwarded"

                                        setText(resultingNodes, rightSideValue);
                                    }
                                }
                            }
                            // TODO: handle xbl:lang?
                            // TODO: handle type specifiers?
                        }
                    }
                }

                private final void setAttribute(List nodes, QName attributeQName, String attributeValue) {
                    if (nodes != null && nodes.size() > 0) {
                        for (Iterator i = nodes.iterator(); i.hasNext();) {
                            final Node node = (Node) i.next();
                            if (node instanceof Element) {
                                ((Element) node).addAttribute(attributeQName, attributeValue);
                            }
                        }
                    }
                }

                private final void setText(List nodes, String value) {
                    if (nodes != null && nodes.size() > 0) {
                        for (Iterator i = nodes.iterator(); i.hasNext();) {
                            final Node node = (Node) i.next();
                            if (node instanceof Element) {
                                ((Element) node).setText(value);
                            }
                        }
                    }
                }

                public void endElement(Element element) {}

                public void text(Text text) {}
            });

            if (XFormsServer.logger.isDebugEnabled()) {
                XFormsContainingDocument.logDebugStatic("static state", "shadow tree",
                        new String[] { "bound element", Dom4jUtils.elementToString(boundElement), "document", Dom4jUtils.domToString(shadowTreeDocument) });
            }

            return shadowTreeDocument;
        } else {
            return null;
        }
    }

    /**
     * Poor man's CSS selector parser:
     *
     * o input: foo|a foo|b, bar|a bar|b
     * o output: .//foo:a//foo:b|.//bar:a//bar:b
     *
     * Also support the ">" combinator.
     *
     * TODO: handle [att], [att=val], [att~=val], [att|=val]
     * TODO: does Flying Saucer have a reusable CSS parser? Could possibly be used here.
     *
     * @param cssSelector   CSS selector
     * @return              XPath expression
     */
    private static String cssToXPath(String cssSelector) {

        final FastStringBuffer sb = new FastStringBuffer(cssSelector.length());
        final String[] selectors = StringUtils.split(cssSelector, ',');
        for (int i = 0; i < selectors.length; i++) {
            // For each comma-separated string
            final String selector = selectors[i];
            if (i > 0)
                sb.append("|");
            final String[] pathElements = StringUtils.split(selector.trim(), ' ');
            boolean previousIsChild = false;
            for (int j = 0; j < pathElements.length; j++) {
                // For each path element
                final String pathElement = pathElements[j];
                if (j == 0) {
                    // First path element
                    if (">".equals(pathElement)) {
                        sb.append("./");
                        previousIsChild = true;
                        continue;
                    } else if (!previousIsChild) {
                        sb.append(".//");
                    }
                } else {
                    // Subsequent path element
                    if (">".equals(pathElement)) {
                        sb.append("/");
                        previousIsChild = true;
                        continue;
                    } else if (!previousIsChild) {
                        sb.append("//");
                    }
                }

                sb.append(pathElement.replace('|', ':').trim());
                previousIsChild = false;
            }
        }
        return sb.toString();
    }

    /**
     * Filter a shadow tree document to keep only XForms controls. This does not modify the input document.
     *
     * @param shadowTreeDocument    full shadow tree document
     * @return                      filtered shadow tree document
     */
    public static Document filterShadowTree(Document shadowTreeDocument) {

        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();

        final LocationDocumentResult result= new LocationDocumentResult();
        identity.setResult(result);

        TransformerUtils.writeDom4j(shadowTreeDocument, new XFormsFilterContentHandler(identity));

        return result.getDocument();
    }
}
/**
 * This filters non-XForms elements. Simplification of XFormsDocumentAnnotatorContentHandler. Can this be simplified?
 */
class XFormsFilterContentHandler extends SimpleForwardingContentHandler {

    private int level;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private boolean inXForms;       // whether we are in a model
    private int xformsLevel;
    private boolean inPreserve;     // whether we are in a label, etc., schema or instance
    private int preserveLevel;

    public XFormsFilterContentHandler(ContentHandler contentHandler) {
        super(contentHandler);
    }

    public void startDocument() throws SAXException {
        super.startDocument();
    }

    public void endDocument() throws SAXException {
        super.endDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.startElement();

        // Check for XForms or extension namespaces
        final boolean isXForms = XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri);
        final boolean isXHTML = XMLConstants.XHTML_NAMESPACE_URI.equals(uri);
        final boolean isXFormsOrExtension = !isXHTML && !"".equals(uri);// TODO: how else can we handle components?

        // Start extracting model or controls
        if (!inXForms && isXFormsOrExtension) {

            inXForms = true;
            xformsLevel = level;

            sendStartPrefixMappings();
        }

        // Check for preserved content
        if (inXForms && !inPreserve) {

            // Preserve as is the content of labels, etc., instances, and schemas
            if ((XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.get(localname) != null // labels, etc. may contain XHTML
                    || "instance".equals(localname)) && isXForms // XForms instances
                    || "schema".equals(localname) && XMLConstants.XSD_URI.equals(uri)) { // XML schemas
                inPreserve = true;
                preserveLevel = level;
            }
        }

        // We are within preserved content or we output regular XForms content
        if (inXForms && (inPreserve || isXFormsOrExtension)) {
            super.startElement(uri, localname, qName, attributes);
        }

        level++;
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
        final boolean isXHTML = XMLConstants.XHTML_NAMESPACE_URI.equals(uri);
        final boolean isXFormsOrExtension = !isXHTML && !"".equals(uri);// TODO: how else can we handle components?

        // We are within preserved content or we output regular XForms content
        if (inXForms && (inPreserve || isXFormsOrExtension)) {
            super.endElement(uri, localname, qName);
        }

        if (inPreserve && level == preserveLevel) {
            // Leaving preserved content
            inPreserve = false;
        } if (inXForms && level == xformsLevel) {
            // Leaving model or controls
            inXForms = false;
            sendEndPrefixMappings();
        }

        namespaceSupport.endElement();
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (inPreserve) {
            super.characters(chars, start, length);
        } else {

            // TODO: we must not output characters here if we are not directly within an XForms element;

            if (inXForms) // TODO: check this: only keep spaces within XForms elements that require it in order to reduce the size of the static state
                super.characters(chars, start, length);
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceSupport.startPrefixMapping(prefix, uri);
        if (inXForms)
            super.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (inXForms)
            super.endPrefixMapping(s);
    }
}
