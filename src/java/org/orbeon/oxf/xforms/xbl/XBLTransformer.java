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
package org.orbeon.oxf.xforms.xbl;

import org.apache.commons.lang.StringUtils;
import org.dom4j.*;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

public class XBLTransformer {

    public static final QName XBL_ATTR_QNAME = new QName("attr", XFormsConstants.XBL_NAMESPACE);
    public static final QName XBL_CONTENT_QNAME = new QName("content", XFormsConstants.XBL_NAMESPACE);
    public static final QName XXBL_ATTR_QNAME = new QName("attr", XFormsConstants.XXBL_NAMESPACE);

    /**
     * Apply an XBL transformation, i.e. apply xbl:content, xbl:attr, etc.
     */
    public static void transform(final DocumentWrapper documentWrapper, final Document shadowTreeDocument, final Element boundElement) {

        Dom4jUtils.visitSubtree(shadowTreeDocument.getRootElement(), new Dom4jUtils.VisitorListener() {

            public void startElement(Element element) {

                // Handle xbl:content

                final boolean isXBLContent = element.getQName().equals(XBL_CONTENT_QNAME);
                final List<Node> resultingNodes;
                if (isXBLContent) {
                    final String includesAttribute = element.attributeValue(XFormsConstants.INCLUDES_QNAME);
                    final String scopeAttribute = element.attributeValue(XFormsConstants.XXBL_SCOPE_QNAME);
                    final List<Node> contentToInsert;
                    if (includesAttribute == null) {
                        // All bound node content must be copied over
                        final List<Node> elementContent = Dom4jUtils.content(boundElement);
                        final List<Node> clonedContent = new ArrayList<Node>();
                        for (final Node node: elementContent) {
                            if (node instanceof Element) {
                                clonedContent.add(Dom4jUtils.copyElementCopyParentNamespaces((Element) node));
                            } else if (!(node instanceof Namespace)) {
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
                        final List elements = XPathCache.evaluate(boundElementInfo, xpathExpression, new NamespaceMapping(Dom4jUtils.getNamespaceContext(element)),
                                null, null, null, null, null);// TODO: locationData

                        if (elements.size() > 0) {
                            // Clone all the resulting elements
                            contentToInsert = new ArrayList<Node>(elements.size());
                            for (Object o: elements) {
                                final NodeInfo currentNodeInfo = (NodeInfo) o;
                                final Element currentElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                                contentToInsert.add(Dom4jUtils.copyElementCopyParentNamespaces(currentElement));
                            }
                        } else {
                            // Clone all the element's children if any
                            // See: http://www.w3.org/TR/xbl/#the-content
                            contentToInsert = new ArrayList<Node>(element.nodeCount());
                            for (Object o: element.elements()) {
                                final Element currentElement = (Element) o;
                                contentToInsert.add(Dom4jUtils.copyElementCopyParentNamespaces(currentElement));
                            }
                        }
                    }

                    // Insert content if any
                    if (contentToInsert != null && contentToInsert.size() > 0) {
                        final List<Node> parentContent = Dom4jUtils.content(element.getParent());
                        final int elementIndex = parentContent.indexOf(element);
                        parentContent.addAll(elementIndex, contentToInsert);
                    }

                    // Remove <xbl:content> from shadow tree
                    element.detach();

                    resultingNodes = contentToInsert;

                    if (!StringUtils.isBlank(scopeAttribute)) {
                        // If author specified scope attribute, use it
                        setAttribute(resultingNodes, XFormsConstants.XXBL_SCOPE_QNAME, scopeAttribute, null);
                    } else {
                        // By default, set xxbl:scope="outer" on resulting elements
                        setAttribute(resultingNodes, XFormsConstants.XXBL_SCOPE_QNAME, "outer", null);
                    }
                } else {
                    // Element is simply kept
                    resultingNodes = Collections.singletonList((Node) element);
                }

                // Handle attribute forwarding
                final Attribute xblAttr = element.attribute(XBL_ATTR_QNAME);    // standard xbl:attr (custom syntax)
                final Attribute xxblAttr = element.attribute(XXBL_ATTR_QNAME);  // extension xxbl:attr (XPath expression)
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
                            final QName valueQName = Dom4jUtils.extractTextValueQName(element, currentValue, true);
                            if (!valueQName.getNamespaceURI().equals(XFormsConstants.XBL_NAMESPACE_URI)) {
                                 // This is not xbl:text, copy the attribute
                                setAttribute(resultingNodes, valueQName, boundElement.attributeValue(valueQName), boundElement);
                            } else {
                                // This is xbl:text
                                // "The xbl:text value cannot occur by itself in the list"
                            }

                        } else {
                            // a=b pair
                            final QName leftSideQName; {
                            final String leftSide = currentValue.substring(0, equalIndex);
                                leftSideQName = Dom4jUtils.extractTextValueQName(element, leftSide, true);
                            }
                            final QName rightSideQName; {
                                final String rightSide = currentValue.substring(equalIndex + 1);
                                rightSideQName = Dom4jUtils.extractTextValueQName(element, rightSide, true);
                            }

                            final boolean isLeftSideXBLText = leftSideQName.getNamespaceURI().equals(XFormsConstants.XBL_NAMESPACE_URI);
                            final boolean isRightSideXBLText = rightSideQName.getNamespaceURI().equals(XFormsConstants.XBL_NAMESPACE_URI);

                            final String rightSideValue;
                            final Element namespaceElement;
                            if (!isRightSideXBLText) {
                                 // Get attribute value
                                rightSideValue = boundElement.attributeValue(rightSideQName);
                                namespaceElement = boundElement;
                            } else {
                                // Get text value

                                // "any text nodes (including CDATA nodes and whitespace text nodes) that are
                                // explicit children of the bound element must have their data concatenated"
                                rightSideValue = boundElement.getText();// must use getText() and not stringValue()
                                namespaceElement = null;
                            }

                            if (rightSideValue != null) {
                                // NOTE: XBL doesn't seem to says what should happen if the source attribute is not
                                // found! We assume the rule is ignored in this case.
                                if (!isLeftSideXBLText) {
                                     // Set attribute value
                                    setAttribute(resultingNodes, leftSideQName, rightSideValue, namespaceElement);
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
                } else if (xxblAttr != null) {
                    // Detach attribute (not strictly necessary?)
                    xxblAttr.detach();
                    // Get attribute value
                    final String xxblAttrString = xxblAttr.getValue();

                    final NodeInfo boundElementInfo = documentWrapper.wrap(boundElement);

                    // TODO: don't use getNamespaceContext() as this is already computed for the bound element
                    final List nodeInfos = XPathCache.evaluate(boundElementInfo, xxblAttrString, new NamespaceMapping(Dom4jUtils.getNamespaceContext(element)),
                            null, null, null, null, null);// TODO: locationData

                    if (nodeInfos.size() > 0) {
                        for (Object nodeInfo: nodeInfos) {
                            final NodeInfo currentNodeInfo = (NodeInfo) nodeInfo;
                            if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ATTRIBUTE_NODE) {
                                // This is an attribute
                                final Attribute currentAttribute = (Attribute) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();
                                setAttribute(resultingNodes, currentAttribute.getQName(), currentAttribute.getValue(), currentAttribute.getParent());
                            }
                        }
                    }
                }

                // Prefix resulting xhtml:*/(@id |@for)

                // NOTE: We could also do the prefixing in the handlers, when the page is output.
                //
                // * Benefit of prefixing here: done statically
                // * Drawback of prefixing here: in the future if we try to reuse simple shadow trees this won't work

//                    {
//                        if (resultingNodes != null && resultingNodes.size() > 0) {
//                            for (Iterator i = resultingNodes.iterator(); i.hasNext();) {
//                                final Node node = (Node) i.next();
//                                if (node instanceof Element) {
//                                    Dom4jUtils.visitSubtree((Element) node, new Dom4jUtils.VisitorListener() {
//                                        public void startElement(Element element) {
//                                            if (XMLConstants.XHTML_NAMESPACE_URI.equals(element.getNamespaceURI())) {
//                                                // Found XHTML element
//
//                                                // Update @id and @for if any
//                                                final Attribute idAttribute = element.attribute("id");
//                                                if (idAttribute != null) {
//                                                    idAttribute.setValue(prefix + idAttribute.getValue());
//                                                }
//                                                final Attribute forAttribute = element.attribute("for");
//                                                if (forAttribute != null) {
//                                                    forAttribute.setValue(prefix + forAttribute.getValue());
//                                                }
//                                            }
//                                        }
//
//                                        public void endElement(Element element) {
//                                        }
//
//                                        public void text(Text text) {
//                                        }
//                                    });
//                                }
//                            }
//                        }
//                    }
            }

            private void setAttribute(List<Node> nodes, QName attributeQName, String attributeValue, Element namespaceElement) {
                if (nodes != null && nodes.size() > 0) {
                    for (final Node node: nodes) {
                        if (node instanceof Element) {
                            final Element element = ((Element) node);
                            // Copy missing namespaces, so that copying things like ref="foo:bar" work as expected
                            Dom4jUtils.copyMissingNamespaces(namespaceElement, element);
                            element.addAttribute(attributeQName, attributeValue);
                        }
                    }
                }
            }

            private void setText(List<Node> nodes, String value) {
                if (nodes != null && nodes.size() > 0) {
                    for (final Node node: nodes) {
                        if (node instanceof Element) {
                            node.setText(value);
                        }
                    }
                }
            }

            public void endElement(Element element) {}
            public void text(Text text) {}
        }, true);
    }

    /**
     * Poor man's CSS selector parser:
     *
     * o input: foo|a foo|b, bar|a bar|b
     * o output: descendant-or-self::foo:a//foo:b|descendant-or-self:://bar:a//bar:b
     *
     * Also support the ">" combinator.
     *
     * TODO: handle [att], [att=val], [att~=val], [att|=val]
     * TODO: does Flying Saucer have a reusable CSS parser? Could possibly be used here.
     *
     * @param cssSelector   CSS selector
     * @return              XPath expression
     */
    public static String cssToXPath(String cssSelector) {

        final StringBuilder sb = new StringBuilder(cssSelector.length());
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
                    } else {
                        sb.append("descendant-or-self::");
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
}
