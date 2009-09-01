/**
 * Copyright (C) 2009 Orbeon, Inc.
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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactory;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xforms.processor.XFormsDocumentAnnotatorContentHandler;
import org.orbeon.oxf.xforms.processor.XFormsExtractorContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;

import javax.xml.transform.sax.TransformerHandler;
import java.util.*;

public class XBLBindings {

    private final XFormsStaticState staticState;            // associated static state
    private Map<String, Map<String, String>> namespacesMap; // Map<String prefixedId, Map<String prefix, String uri>> of namespace mappings

    private final boolean logShadowTrees;                   // whether to log shadow trees as they are built

    private Map<QName, XFormsControlFactory.Factory> xblComponentsFactories;    // Map<QName bindingQName, Factory> of QNames to component factory
    private Map<QName, Element> xblComponentBindings;       // Map<QName bindingQName, Element bindingElement> of QNames to bindings
    private Map<String, Document> xblFullShadowTrees;       // Map<String treePrefixedId, Document> (with full content, e.g. XHTML)
    private Map<String, Document> xblCompactShadowTrees;    // Map<String treePrefixedId, Document> (without full content, only the XForms controls)
    private Map<String, String> xblBindingIds;              // Map<String treePrefixedId, String bindingId>
    private List<Element> xblScripts;                       // List<Element xblScriptElements>
    private List<Element> xblStyles;                        // List<Element xblStyleElements>
    private Map<QName, List<Element>> xblHandlers;          // Map<QName bindingQName, List<Element handlerElement>>
    private Map<QName, List<Document>> xblImplementations;  // Map<QName bindingQName, List<Document>>

    public XBLBindings(IndentedLogger indentedLogger, XFormsStaticState staticState, Map<String, Map<String, String>> namespacesMap, Element staticStateElement) {

        this.staticState = staticState;
        this.namespacesMap = namespacesMap;

        this.logShadowTrees = XFormsProperties.getDebugLogging().contains("analysis-xbl-tree");

        final List<Element> xblElements = (staticStateElement != null) ? Dom4jUtils.elements(staticStateElement, XFormsConstants.XBL_XBL_QNAME) : Collections.<Element>emptyList();
        if (xblElements.size() > 0) {

            indentedLogger.startHandleOperation("", "extracting top-level XBL documents");

            xblComponentsFactories = new HashMap<QName, XFormsControlFactory.Factory>();
            xblComponentBindings = new HashMap<QName, Element>();
            xblFullShadowTrees = new HashMap<String, Document>();
            xblCompactShadowTrees = new HashMap<String, Document>();
            xblBindingIds = new HashMap<String, String>();

            int xblCount = 0;
            int xblBindingCount = 0;
            for (Element currentXBLElement: xblElements) {
                // Copy the element because we may need it in staticStateDocument for encoding
                final Document currentXBLDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentXBLElement);

                // Extract xbl:xbl/xbl:script
                // TODO: should do this differently, in order to include only the scripts and resources actually used
                final List<Element> scriptElements = Dom4jUtils.elements(currentXBLDocument.getRootElement(), XFormsConstants.XBL_SCRIPT_QNAME);
                if (scriptElements != null && scriptElements.size() > 0) {
                    if (xblScripts == null)
                        xblScripts = new ArrayList<Element>();
                    xblScripts.addAll(scriptElements);
                }

                // Find bindings
                for (Iterator j = currentXBLDocument.getRootElement().elements(XFormsConstants.XBL_BINDING_QNAME).iterator(); j.hasNext(); xblBindingCount++) {
                    final Element currentBindingElement = (Element) j.next();
                    final String currentElementAttribute = currentBindingElement.attributeValue("element");

                    if (currentElementAttribute != null) {

                        // For now, only handle "prefix|name" selectors
                        // NOTE: Pass blank prefix as XBL bindings are all within the top-level document
                        final QName currentQNameMatch
                                = Dom4jUtils.extractTextValueQName(staticState.getNamespaceMappings("", currentBindingElement), currentElementAttribute.replace('|', ':'), true);

                        // Create and remember factory for this QName
                        xblComponentsFactories.put(currentQNameMatch,
                            new XFormsControlFactory.Factory() {
                                public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                                    return new XFormsComponentControl(container, parent, element, name, effectiveId);
                                }
                            });

                        xblComponentBindings.put(currentQNameMatch, currentBindingElement);

                        // Extract xbl:handlers/xbl:handler
                        {
                            final Element handlersElement = currentBindingElement.element(XFormsConstants.XBL_HANDLERS_QNAME);
                            if (handlersElement != null) {
                                final List<Element> handlerElements = Dom4jUtils.elements(handlersElement, XFormsConstants.XBL_HANDLER_QNAME);

                                if (xblHandlers == null) {
                                    xblHandlers = new LinkedHashMap<QName, List<Element>>();
                                }

//                                xxx TODO: WIP: get unique ids for handlers
                                xblHandlers.put(currentQNameMatch, handlerElements);
                            }
                        }

                        // Extract xbl:implementation/xforms:model
                        {
                            final Element implementationElement = currentBindingElement.element(XFormsConstants.XBL_IMPLEMENTATION_QNAME);
                            if (implementationElement != null) {
                                // TODO: check if really need to pass detach == true
                                final List<Document> modelDocuments = extractChildrenModels(implementationElement, true);

                                if (xblImplementations == null) {
                                    xblImplementations = new LinkedHashMap<QName, List<Document>>();
                                }

                                xblImplementations.put(currentQNameMatch, modelDocuments);
                            }
                        }

                        // Extract xbl:binding/xbl:resources/xbl:style
                        // TODO: should do this differently, in order to include only the scripts and resources actually used
                        {
                            final List resourcesElements = currentBindingElement.elements(XFormsConstants.XBL_RESOURCES_QNAME);
                            if (resourcesElements != null) {
                                for (Object resourcesElement: resourcesElements) {
                                    final Element currentResourcesElement = (Element) resourcesElement;
                                    final List<Element> styleElements = Dom4jUtils.elements(currentResourcesElement, XFormsConstants.XBL_STYLE_QNAME);
                                    if (styleElements != null && styleElements.size() > 0) {
                                        if (xblStyles == null) {
                                            xblStyles = new ArrayList<Element>(styleElements);
                                        } else {
                                            xblStyles.addAll(styleElements);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            indentedLogger.endHandleOperation("xbl:xbl count", Integer.toString(xblCount),
                    "xbl:binding count", Integer.toString(xblBindingCount));
        }
    }

    private static List<Document> extractChildrenModels(Element parentElement, boolean detach) {

        final List<Document> result = new ArrayList<Document>();
        final List<Element> modelElements = Dom4jUtils.elements(parentElement, XFormsConstants.XFORMS_MODEL_QNAME);

        if (modelElements.size() > 0) {
            for (Element currentModelElement: modelElements) {
                final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentModelElement, detach);
                result.add(modelDocument);
            }
        }

        return result;
    }

    public void processElementIfNeeded(PropertyContext propertyContext, IndentedLogger indentedLogger, Element controlElement,
                                       String controlPrefixedId, LocationData locationData,
                                       DocumentWrapper controlsDocumentInfo, Configuration xpathConfiguration, String prefix,
                                       FastStringBuffer repeatHierarchyStringBuffer, Stack<String> repeatAncestorsStack) {

        if (xblComponentBindings != null) {
            final Element bindingElement = xblComponentBindings.get(controlElement.getQName());
            if (bindingElement != null) {
                // A custom component is bound to this element

                // Find new prefix
                final String newPrefix = controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR;

                // Generate the shadow content for this particular binding
                final Document fullShadowTreeDocument = generateXBLShadowContent(propertyContext, indentedLogger, controlsDocumentInfo, controlElement, bindingElement, namespacesMap, newPrefix);
                if (fullShadowTreeDocument != null) {

                    final DocumentWrapper fullShadowTreeWrapper = new DocumentWrapper(fullShadowTreeDocument, null, xpathConfiguration);

                    // Register models placed under xbl:implementation
                    if (xblImplementations != null) {
                        final List<Document> implementationModelDocuments = xblImplementations.get(controlElement.getQName());
                        if (implementationModelDocuments.size() > 0) {
                            for (Document currentModelDocument: implementationModelDocuments) {
                                // Store models by "prefixed id"
                                staticState.addModelDocument(controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR + currentModelDocument.getRootElement().attributeValue("id"), currentModelDocument);
                            }
                            indentedLogger.logDebug("", "registered XBL implementation model documents", "count", Integer.toString(implementationModelDocuments.size()));
                        }
                    }

                    // Extract and register models from within the template
                    {
                        final List<Document> extractedModels = XFormsStaticState.extractNestedModels(propertyContext, fullShadowTreeWrapper, true, locationData);
                        if (extractedModels.size() > 0) {
                            for (Document currentModelDocument: extractedModels) {
                                // Store models by "prefixed id"
                                staticState.addModelDocument(controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR + currentModelDocument.getRootElement().attributeValue("id"), currentModelDocument);
                            }
                            indentedLogger.logDebug("", "created and registered XBL template model documents", "count", Integer.toString(extractedModels.size()));
                        }
                    }

                    // Remember full shadow tree for this prefixed id
                    xblFullShadowTrees.put(controlPrefixedId, fullShadowTreeDocument);

                    // Generate compact shadow tree for this static id
                    final Document compactShadowTreeDocument = filterShadowTree(indentedLogger, fullShadowTreeDocument, controlElement);
                    xblCompactShadowTrees.put(controlPrefixedId, compactShadowTreeDocument);

                    // Remember id of binding
                    xblBindingIds.put(controlPrefixedId, bindingElement.attributeValue("id"));

                    // Extract xbl:xbl/xbl:script and xbl:binding/xbl:resources/xbl:style
                    // TODO: should do this here, in order to include only the scripts and resources actually used

                    // Gather xbl:handlers/xbl:handler attached to bound node
                    if (xblHandlers != null) {
                        final List<Element> handlerElements = xblHandlers.get(controlElement.getQName());
                        if (handlerElements != null) {
                            for (Element currentHandlerElement: handlerElements) {
                                // Register xbl:handler as an action handler
                                // NOTE: xbl:handler has similar attributes as XForms actions, in particular @event, @phase, etc.
                                final String controlStaticId = XFormsUtils.getStaticIdFromId(controlPrefixedId);
                                final XFormsEventHandlerImpl eventHandler = new XFormsEventHandlerImpl(currentHandlerElement, controlStaticId, true,
                                        controlStaticId,
                                        currentHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME),
                                        null, // no target attribute allowed in XBL
                                        currentHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_PHASE_ATTRIBUTE_QNAME),
                                        currentHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME),
                                        currentHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME));

                                staticState.registerActionHandler(eventHandler, prefix);
                            }
                        }
                    }

                    // NOTE: Say we don't want to exclude gathering event handlers within nested models
                    staticState.analyzeComponentTree(propertyContext, xpathConfiguration, newPrefix, compactShadowTreeDocument.getRootElement(),
                            repeatHierarchyStringBuffer, repeatAncestorsStack, false);
                }
            }
        }
    }

    /**
     * Generate shadow content for the given control id and XBL binding.
     *
     * @param propertyContext   context
     * @param indentedLogger    logger
     * @param documentWrapper
     * @param boundElement      element to which the binding applies
     * @param binding           corresponding <xbl:binding>
     * @param namespaceMappings
     * @param prefix
     * @return                  shadow tree document
     */
    private Document generateXBLShadowContent(final PropertyContext propertyContext, final IndentedLogger indentedLogger, final DocumentWrapper documentWrapper,
                                              final Element boundElement, Element binding, Map<String, Map<String, String>> namespaceMappings, final String prefix) {
        final Element templateElement = binding.element(XFormsConstants.XBL_TEMPLATE_QNAME);
        if (templateElement != null) {

            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.startHandleOperation("", "generating XBL shadow content", "bound element", Dom4jUtils.elementToString(boundElement), "binding id", binding.attributeValue("id"));
            }

            // TODO: in script mode, XHTML elements in template should only be kept during page generation

            // Here we create a completely separate document

            // 1. Apply optional preprocessing step (usually XSLT)
            // Copy as the template element may be used many times
            final Document shadowTreeDocument = transformTemplate(templateElement, boundElement);

            // 2. Apply xbl:attr, xbl:content, xxbl:attr
            Dom4jUtils.visitSubtree(shadowTreeDocument.getRootElement(), new Dom4jUtils.VisitorListener() {
                public void startElement(Element element) {

                    // Handle xbl:content

                    final boolean isXBLContent = element.getQName().equals(XFormsConstants.XBL_CONTENT_QNAME);
                    final List<Node> resultingNodes;
                    if (isXBLContent) {
                        final String includesAttribute = element.attributeValue("includes");
                        final List<Node> contentToInsert;
                        if (includesAttribute == null) {
                            // All bound node content must be copied over
                            final List<Node> elementContent = Dom4jUtils.content(boundElement);
                            final List<Node> clonedContent = new ArrayList<Node>();
                            for (Node node: elementContent) {
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
                            final List elements = XPathCache.evaluate(propertyContext, boundElementInfo, xpathExpression, Dom4jUtils.getNamespaceContext(element),
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
                                contentToInsert = null;
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
                    } else {
                        // Element is simply kept
                        resultingNodes = Collections.singletonList((Node) element);
                    }

                    // Handle attribute forwarding
                    final Attribute xblAttr = element.attribute(XFormsConstants.XBL_ATTR_QNAME);    // standard xbl:attr (custom syntax)
                    final Attribute xxblAttr = element.attribute(XFormsConstants.XXBL_ATTR_QNAME);  // extension xxbl:attr (XPath expression)
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
                                    setAttribute(resultingNodes, valueQName, boundElement.attributeValue(valueQName));
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
                    } else if (xxblAttr != null) {
                        // Detach attribute (not strictly necessary?)
                        xxblAttr.detach();
                        // Get attribute value
                        final String xxblAttrString = xxblAttr.getValue();

                        final NodeInfo boundElementInfo = documentWrapper.wrap(boundElement);

                        // TODO: don't use getNamespaceContext() as this is already computed for the bound element
                        final List nodeInfos = XPathCache.evaluate(propertyContext, boundElementInfo, xxblAttrString, Dom4jUtils.getNamespaceContext(element),
                                null, null, null, null, null);// TODO: locationData

                        if (nodeInfos.size() > 0) {
                            for (Object nodeInfo: nodeInfos) {
                                final NodeInfo currentNodeInfo = (NodeInfo) nodeInfo;
                                if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ATTRIBUTE_NODE) {
                                    // This is an attribute
                                    final Attribute currentAttribute = (Attribute) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();
                                    setAttribute(resultingNodes, currentAttribute.getQName(), currentAttribute.getValue());
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

                private void setAttribute(List<Node> nodes, QName attributeQName, String attributeValue) {
                    if (nodes != null && nodes.size() > 0) {
                        for (final Node node: nodes) {
                            if (node instanceof Element) {
                                ((Element) node).addAttribute(attributeQName, attributeValue);
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

            // Annotate tree
            final Document annotatedShadowTreeDocument = annotateShadowTree(shadowTreeDocument, namespaceMappings, prefix);

            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(annotatedShadowTreeDocument) : null);
            }

            return annotatedShadowTreeDocument;
        } else {
            return null;
        }
    }

    // Keep public for unit tests
    public Document annotateShadowTree(Document shadowTreeDocument, Map<String, Map<String, String>> namespaceMappings, final String prefix) {
        // Create transformer
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();

        // Set result
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);

        // Write the document through the annotator and gather namespace mappings
        TransformerUtils.writeDom4j(shadowTreeDocument, new XFormsDocumentAnnotatorContentHandler(identity, "", false, namespaceMappings) {
            protected void addNamespaces(String id) {
                // Store prefixed id in order to avoid clashes between top-level controls and shadow trees
                super.addNamespaces(prefix + id);
            }

            protected boolean isXBLBinding(String uri, String localname) {
                return xblComponentBindings != null && xblComponentBindings.get(QName.get(localname, Namespace.get(uri))) != null;
            }
        });

        // Return annotated document
        return documentResult.getDocument();
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

    /**
     * Filter a shadow tree document to keep only XForms controls. This does not modify the input document.
     *
     * @param indentedLogger    logger
     * @param fullShadowTree    full shadow tree document
     * @param boundElement
     * @return                  compact shadow tree document
     */
    private Document filterShadowTree(IndentedLogger indentedLogger, Document fullShadowTree, Element boundElement) {

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.startHandleOperation("", "filtering shadow tree", "bound element", Dom4jUtils.elementToString(boundElement));
        }

        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result= new LocationDocumentResult();
        identity.setResult(result);

        // Run transformation
        TransformerUtils.writeDom4j(fullShadowTree, new XFormsExtractorContentHandler(identity));
//        TransformerUtils.writeDom4j(fullShadowTree, new XFormsExtractorContentHandler(new SAXLoggerProcessor.DebugContentHandler(identity)));

        // Extractor produces /static-state/xbl:template, so extract the nested element
        final Document compactShadowTree = Dom4jUtils.createDocumentCopyParentNamespaces(result.getDocument().getRootElement().element(XFormsConstants.XBL_TEMPLATE_QNAME), true);

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(compactShadowTree) : null);
        }

        return compactShadowTree;
    }

    private static Document transformTemplate(Element templateElement, Element boundElement) {
        final QName processorName = Dom4jUtils.extractAttributeValueQName(templateElement, XFormsConstants.XXBL_TRANSFORM_QNAME);
        if (processorName == null) {
            // @xxbl:transform is missing or empty: keep the template element alone
            return Dom4jUtils.createDocumentCopyParentNamespaces(templateElement);
        } else {
            // Find a processor and create one
            final ProcessorFactory processorFactory = ProcessorFactoryRegistry.lookup(processorName);
            if (processorFactory == null) {
                throw new OXFException("Cannot find a processor for xxbl:transform='" +
                        templateElement.attributeValue(XFormsConstants.XXBL_TRANSFORM_QNAME) + "'.");
            }
            final Processor processor = processorFactory.createInstance();

            // Check if we have a single root for our transformation
            final int nbChildElements = templateElement.elements().size();
            if (nbChildElements != 1) {
                throw new OXFException("xxbl:transform requires a single child element.");
            }

            // Connect this root to the processor config input
            final Element templateChild = (Element) templateElement.elements().get(0);
            final DOMGenerator domGeneratorConfig = PipelineUtils.createDOMGenerator(
                    Dom4jUtils.createDocumentCopyParentNamespaces(templateChild),
                    "xbl-xslt-config", processor, Dom4jUtils.makeSystemId(templateChild));
            PipelineUtils.connect(domGeneratorConfig, "data", processor, "config");

            // Connect the bound element to the processor data input
            final DOMGenerator domGeneratorData = PipelineUtils.createDOMGenerator(
                    Dom4jUtils.createDocumentCopyParentNamespaces(boundElement),
                    "xbl-xslt-data", processor, Dom4jUtils.makeSystemId(boundElement));
            PipelineUtils.connect(domGeneratorData, "data", processor, "data");

            // Connect a DOM serializer to the processor data output
            final DOMSerializer domSerializerData = new DOMSerializer();
            PipelineUtils.connect(processor, "data", domSerializerData, "data");

            // Run the transformation
            final PipelineContext newPipelineContext = new PipelineContext();
            domSerializerData.start(newPipelineContext);

            // Get the result, move its root element into a xbl:template and return it
            final Document generated = domSerializerData.getDocument(newPipelineContext);
            final Element result = (Element) generated.getRootElement().detach();
            generated.addElement(new QName("template", XFormsConstants.XBL_NAMESPACE, "xbl:template"));
            final Element newRoot = generated.getRootElement();
            newRoot.add(XFormsConstants.XBL_NAMESPACE);
            newRoot.add(result);

            return generated;
        }
    }

    /*
     * Return whether this document has at leat one component in use.
     */
    public boolean hasComponentsInUse() {
        return xblComponentBindings != null && xblComponentBindings.size() > 0;
    }

    /**
     * All component bindings.
     *
     * @return Map<QName, Element> of QNames to bindings, or null
     */
    public Map<QName, Element> getComponentBindings() {
        return xblComponentBindings;
    }

    /**
     * Return whether the given QName has an associated binding.
     *
     * @param qName QName to check
     * @return      true iif there is a binding
     */
    public boolean isComponent(QName qName) {
        return xblComponentBindings != null && xblComponentBindings.get(qName) != null;
    }

    /**
     * Return a control factory for the given QName.
     *
     * @param qName QName to check
     * @return      control factory, or null
     */
    public XFormsControlFactory.Factory getComponentFactory(QName qName) {
        return (xblComponentsFactories == null) ? null : xblComponentsFactories.get(qName);
    }

    /**
     * Return the expanded shadow tree for the given prefixed control id.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      full expanded shadow tree, or null
     */
    public Element getFullShadowTree(String controlPrefixedId) {
        return (xblFullShadowTrees != null) ? xblFullShadowTrees.get(controlPrefixedId).getRootElement() : null;
    }

    /**
     * Return the expanded shadow tree for the given prefixed control id, with only XForms controls and no markup.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      compact expanded shadow tree, or null
     */
    public Element getCompactShadowTree(String controlPrefixedId) {
        return (xblCompactShadowTrees == null) ? null : xblCompactShadowTrees.get(controlPrefixedId).getRootElement();
    }

    /**
     * Return the id of the <xbl:binding> element associated with the given  prefixed control id.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      binding id or null if not found
     */
    public String getBindingId(String controlPrefixedId) {
        return (xblBindingIds == null) ? null : xblBindingIds.get(controlPrefixedId);
    }

    /**
     * Return a List of xbl:style elements.
     */
    public List<Element> getXBLStyles() {
        return xblStyles;
    }

    /**
     * Return a List of xbl:script elements.
     */
    public List<Element> getXBLScripts() {
        return xblScripts;
    }
}
