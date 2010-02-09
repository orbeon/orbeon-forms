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
import org.orbeon.oxf.xforms.analysis.IdGenerator;
import org.orbeon.oxf.xforms.analysis.XFormsAnnotatorContentHandler;
import org.orbeon.oxf.xforms.analysis.XFormsExtractorContentHandler;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;

import javax.xml.transform.sax.TransformerHandler;
import java.util.*;

/**
 * All the information statically gathered about XBL bindings.
 *
 * TODO:
 *
 * o xbl:handler and models under xbl:implementation are copied for each binding. We should be able to do this better:
 *   o do the "id" part of annotation only once
 *   o therefore keep a single DOM for all uses of those
 *   o however, if needed, still register namespace mappings by prefix once per mapping
 * o P2: even for templates that produce the same result per each instantiation:
 *   o detect that situation (when is this possible?)
 *   o keep a single DOM
 */
public class XBLBindings {

    private final XFormsStaticState staticState;            // associated static state

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

    private final Map<String, Scope> prefixedIdToXBLScopeMap = new HashMap<String, Scope>();                // maps element prefixed id => XBL scope
    private final Map<Scope, Map<String, String>> scopeToIdMap = new HashMap<Scope, Map<String, String>>(); // maps XBL scope => (map static id => prefixed id)
    private final Map<String, Scope> scopeIds = new HashMap<String, Scope>();                               // all distinct scopes by scope id

    private final Scope TOP_LEVEL_SCOPE = new Scope("");

    public class Scope {
        public final String scopeId;

        private Scope(String scopeId) {
            assert !scopeIds.containsKey(scopeId);
            this.scopeId = scopeId;
            scopeIds.put(scopeId, this);
        }

        public String getFullPrefix() {
            return scopeId.length() == 0 ? "" : scopeId + '$';
        }

        /**
         * Return the prefixed id of the given control static id within this scope.
         *
         * @param staticId  static id to resolve
         * @return          prefixed id corresponding to the static id passed
         */
        public String getPrefixedIdForStaticId(String staticId) {
            if (scopeToIdMap.size() == 0) {
                // If there are no XBL controls the map is empty
                return staticId;
            } else {
                // Otherwise use map
                return scopeToIdMap.get(this).get(staticId);
            }
        }

        @Override
        public int hashCode() {
            return scopeId.hashCode();
        }

        @Override
        public String toString() {
            return scopeId;
        }
    }

    public Scope getResolutionScopeById(String scopeId) {
        return scopeIds.get(scopeId);
    }

    public Scope getResolutionScopeByPrefix(String prefix) {
        assert prefix.length() == 0 || prefix.charAt(prefix.length() - 1) == XFormsConstants.COMPONENT_SEPARATOR;
        final String scopeId = (prefix.length() == 0) ? "" : prefix.substring(0, prefix.length() - 1);
        return getResolutionScopeById(scopeId);
    }

    /**
     * Return the resolution scope id for the given prefixed id.
     *
     * @param prefixedId    prefixed id of XForms element
     * @return              resolution scope
     */
    public Scope getResolutionScopeByPrefixedId(String prefixedId) {
        if (scopeToIdMap.size() == 0) {
            // If there are no XBL controls the map is empty
            return TOP_LEVEL_SCOPE;
        } else {
            // Otherwise use map
            final Scope result = prefixedIdToXBLScopeMap.get(prefixedId);
            assert result != null : "cannot find scope in map for prefixed id: " + prefixedId;
            return result;
        }
    }

    /*
     * Notes about id generation
     *
     * Two approaches:
     *
     * o use shared IdGenerator
     *   o simpler
     *   o drawback: automatic ids grow larger
     *   o works for id allocation, but not for checking duplicate ids, but we do duplicate id check separately for XBL
     *     anyway in ScopeExtractorContentHandler
     * o use separate outer/inner scope IdGenerator
     *   o more complex
     *   o requires to know inner/outer scope at annotation time
     *   o requires XFormsAnnotatorContentHandler to provide start/end of XForms element
     *
     * As of 2009-09-14, we use an IdGenerator shared among top-level and all XBL bindings.
     */
    private XFormsAnnotatorContentHandler.Metadata metadata;

    public XBLBindings(IndentedLogger indentedLogger, XFormsStaticState staticState, final IdGenerator idGenerator,
                       Map<String, Map<String, String>> namespacesMap, Element staticStateElement) {

        this.staticState = staticState;

        this.logShadowTrees = XFormsProperties.getDebugLogging().contains("analysis-xbl-tree");

        // Check whether there are any <xbl:xbl> elements
        final List<Element> xblElements = (staticStateElement != null) ? Dom4jUtils.elements(staticStateElement, XFormsConstants.XBL_XBL_QNAME) : Collections.<Element>emptyList();
        if (xblElements.size() > 0) {
            // Process <xbl:xbl>

            indentedLogger.startHandleOperation("", "extracting top-level XBL documents");

            // Add existing ids to scope map so we can check duplicates
            final Map<String, String> topLevelScopeMap = new HashMap<String, String>();
            scopeToIdMap.put(TOP_LEVEL_SCOPE, topLevelScopeMap);
            for (Iterator<String> i = idGenerator.iterator(); i.hasNext();) {
                final String id = i.next();
                topLevelScopeMap.put(id, id);
                prefixedIdToXBLScopeMap.put(id, TOP_LEVEL_SCOPE);
            }

            // Create delegating top-level static id generator which just doesn't check for duplicate ids
            this.metadata = new XFormsAnnotatorContentHandler.Metadata(
                new IdGenerator() {
                    @Override
                    public boolean isDuplicate(String id) {
                        // Duplicate ids are checked separately by scope
                        return false;
                    }

                    @Override
                    public void add(String id) {
                        idGenerator.add(id);
                    }

                    @Override
                    public String getNextId() {
                        return idGenerator.getNextId();
                    }

                    @Override
                    public Iterator<String> iterator() {
                        return idGenerator.iterator();
                    }
                }, namespacesMap);

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

                                xblHandlers.put(currentQNameMatch, handlerElements);
                            }
                        }

                        // Extract xbl:implementation/xforms:model
                        {
                            final Element implementationElement = currentBindingElement.element(XFormsConstants.XBL_IMPLEMENTATION_QNAME);
                            if (implementationElement != null) {
                                final List<Document> modelDocuments = extractChildrenModels(implementationElement, true); // just detach because they are copied later anyway

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
                                       StringBuilder repeatHierarchyStringBuffer, Stack<String> repeatAncestorsStack) {

        if (xblComponentBindings != null) {
            final Element bindingElement = xblComponentBindings.get(controlElement.getQName());
            if (bindingElement != null) {
                // A custom component is bound to this element

                // Find new prefix
                final String newPrefix = controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR;

                // Generate the shadow content for this particular binding
                final Document fullShadowTreeDocument = generateShadowTree(propertyContext, indentedLogger, controlsDocumentInfo, controlElement, bindingElement, newPrefix);
                if (fullShadowTreeDocument != null) {

                    // Generate compact shadow tree for this static id
                    final Scope newScope = new Scope(newPrefix.substring(0, newPrefix.length() - 1));
                    final Document compactShadowTreeDocument = filterShadowTree(indentedLogger, fullShadowTreeDocument, controlElement, newPrefix, newScope, controlPrefixedId);

                    final Scope innerScope = getResolutionScopeById(controlPrefixedId);
                    final Scope outerScope = prefixedIdToXBLScopeMap.get(controlPrefixedId);

                    // Register models placed under xbl:implementation
                    if (xblImplementations != null) {
                        final List<Document> implementationModelDocuments = xblImplementations.get(controlElement.getQName());
                        if (implementationModelDocuments != null && implementationModelDocuments.size() > 0) {
                            // Say we DO annotate because these models are outside the template
                            addModelDocuments(controlPrefixedId, implementationModelDocuments, newPrefix, true,
                                    innerScope, outerScope, XFormsConstants.XXBLScope.inner);
                            if (indentedLogger.isDebugEnabled())
                                indentedLogger.logDebug("", "registered XBL implementation model documents", "count", Integer.toString(implementationModelDocuments.size()));
                        }

                    }   

                    // Extract and register models from within the template
                    {
                        final DocumentWrapper compactShadowTreeWrapper = new DocumentWrapper(compactShadowTreeDocument, null, xpathConfiguration);
                        final List<Document> templateModelDocuments = XFormsStaticState.extractNestedModels(propertyContext, compactShadowTreeWrapper, true, locationData);
                        if (templateModelDocuments.size() > 0) {
                            // Say we don't annotate documents because already annotated as part as template processing
                            addModelDocuments(controlPrefixedId, templateModelDocuments, newPrefix, false, null, null, null);
                            if (indentedLogger.isDebugEnabled())
                                indentedLogger.logDebug("", "created and registered XBL template model documents", "count", Integer.toString(templateModelDocuments.size()));
                        }
                    }

                    // Remember full and compact shadow trees for this prefixed id
                    xblFullShadowTrees.put(controlPrefixedId, fullShadowTreeDocument);
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

                                // Annotate handler and gather scope information
                                final Element currentHandlerAnnotatedElement
                                        = annotateHandler(currentHandlerElement, newPrefix, innerScope, outerScope, XFormsConstants.XXBLScope.inner).getRootElement();

                                // NOTE: <xbl:handler> has similar attributes as XForms actions, in particular @event, @phase, etc.
                                final String controlStaticId = XFormsUtils.getStaticIdFromId(controlPrefixedId);
                                final XFormsEventHandlerImpl eventHandler = new XFormsEventHandlerImpl(prefix, currentHandlerAnnotatedElement,
                                        null, controlStaticId, true, controlStaticId,
                                        currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_EVENT_ATTRIBUTE_QNAME),
                                        null, // no target attribute allowed in XBL
                                        currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_PHASE_ATTRIBUTE_QNAME),
                                        currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_PROPAGATE_ATTRIBUTE_QNAME),
                                        currentHandlerAnnotatedElement.attributeValue(XFormsConstants.XBL_HANDLER_DEFAULT_ACTION_ATTRIBUTE_QNAME),
                                        null, null);

                                staticState.registerActionHandler(eventHandler, prefix);

                                // Extract scripts in the handler
                                final DocumentWrapper handlerWrapper = new DocumentWrapper(currentHandlerAnnotatedElement.getDocument(), null, xpathConfiguration);
                                staticState.extractXFormsScripts(propertyContext, handlerWrapper, newPrefix);
                            }
                        }
                    }

                    staticState.analyzeComponentTree(propertyContext, xpathConfiguration, newPrefix, compactShadowTreeDocument.getRootElement(),
                            repeatHierarchyStringBuffer, repeatAncestorsStack);
                }
            }
        }
    }

    public Document annotateHandler(Element currentHandlerElement, String newPrefix, Scope innerScope, Scope outerScope, XFormsConstants.XXBLScope startScope) {
        final Document handlerDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentHandlerElement, false);// for now, don't detach because element can be processed by multiple bindings
        final Document annotatedDocument = annotateShadowTree(handlerDocument, newPrefix, metadata);
        gatherScopeMappingsAndTransform(annotatedDocument, newPrefix, innerScope, outerScope, startScope, null, false, "/");
        return annotatedDocument;
    }

    private void addModelDocuments(String controlPrefixedId, List<Document> modelDocuments, String prefix, boolean annotate,
                                   Scope innerScope, Scope outerScope, XFormsConstants.XXBLScope startScope) {
        for (Document currentModelDocument: modelDocuments) {

            // Annotate if needed, otherwise leave as is
            if (annotate) {
                currentModelDocument = annotateShadowTree(currentModelDocument, prefix, metadata);
                gatherScopeMappingsAndTransform(currentModelDocument, prefix, innerScope, outerScope, startScope, null, false, "/");
            }

            // Store models by "prefixed id"
            final String modelStaticId = currentModelDocument.getRootElement().attributeValue("id");
            staticState.addModelDocument(controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR + modelStaticId, currentModelDocument);
        }
    }

    /**
     * Generate shadow content for the given control id and XBL binding.
     *
     * @param propertyContext   context
     * @param indentedLogger    logger
     * @param documentWrapper   wrapper around controls document
     * @param boundElement      element to which the binding applies
     * @param binding           corresponding <xbl:binding>
     * @param prefix            prefix of the ids within the new shadow tree, e.g. component1$component2$
     * @return                  shadow tree document
     */
    private Document generateShadowTree(final PropertyContext propertyContext, final IndentedLogger indentedLogger, final DocumentWrapper documentWrapper,
                                        final Element boundElement, Element binding, final String prefix) {
        final Element templateElement = binding.element(XFormsConstants.XBL_TEMPLATE_QNAME);
        if (templateElement != null) {

            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.startHandleOperation("", "generating XBL shadow content", "bound element", Dom4jUtils.elementToDebugString(boundElement), "binding id", binding.attributeValue("id"));
            }

            // TODO: in script mode, XHTML elements in template should only be kept during page generation

            // Here we create a completely separate document

            // 1. Apply optional preprocessing step (usually XSLT)
            // Copy as the template element may be used many times
            final Document shadowTreeDocument = transformTemplate(templateElement, boundElement);

            // 2. Apply xbl:attr, xbl:content, xxbl:attr and index xxbl:scope
            applyXBLTransformation(propertyContext, documentWrapper, shadowTreeDocument, boundElement);

            // 3: Annotate tree
            final Document annotatedShadowTreeDocument = annotateShadowTree(shadowTreeDocument, prefix, metadata);

            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(annotatedShadowTreeDocument) : null);
            }

            return annotatedShadowTreeDocument;
        } else {
            return null;
        }
    }

    // TODO: could this be done as a processor instead? could then have XSLT -> XBL -> XFACH
    private static void applyXBLTransformation(final PropertyContext propertyContext, final DocumentWrapper documentWrapper, Document shadowTreeDocument, final Element boundElement) {
        Dom4jUtils.visitSubtree(shadowTreeDocument.getRootElement(), new Dom4jUtils.VisitorListener() {

            public void startElement(Element element) {

                // Handle xbl:content

                final boolean isXBLContent = element.getQName().equals(XFormsConstants.XBL_CONTENT_QNAME);
                final List<Node> resultingNodes;
                if (isXBLContent) {
                    final String includesAttribute = element.attributeValue("includes");
                    final String scopeAttribute = element.attributeValue(XFormsConstants.XXBL_SCOPE_QNAME);
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

                    if (!StringUtils.isBlank(scopeAttribute)) {
                        // If author specified scope attribute, use it
                        setAttribute(resultingNodes, XFormsConstants.XXBL_SCOPE_QNAME, scopeAttribute);
                    } else {
                        // By default, set xxbl:scope="outer" on resulting elements
                        setAttribute(resultingNodes, XFormsConstants.XXBL_SCOPE_QNAME, "outer");
                    }
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

                            if (rightSideValue != null) {
                                // NOTE: XBL doesn't seem to says what should happen if the source attribute is not
                                // found! We assume the rule is ignored in this case.
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
    }

    // Keep public for unit tests
    public Document annotateShadowTree(Document shadowTreeDocument, final String prefix, XFormsAnnotatorContentHandler.Metadata metadata) {
        // Create transformer
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();

        // Set result
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);

        // Write the document through the annotator
        // TODO: this adds xml:base on root element, must fix
        TransformerUtils.writeDom4j(shadowTreeDocument, new XFormsAnnotatorContentHandler(identity, "", false, metadata) {
            @Override
            protected void addNamespaces(String id) {
                // Store prefixed id in order to avoid clashes between top-level controls and shadow trees
                super.addNamespaces(prefix + id);
            }

            @Override
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

    /**
     * Filter a shadow tree document to keep only XForms controls. This does not modify the input document.
     *
     * @param indentedLogger        logger
     * @param fullShadowTree        full shadow tree document
     * @param boundElement          bound element
     * @param prefix                prefix of the ids within the new shadow tree, e.g. component1$component2$
     * @param innerScope            inner scope for the new tree
     * @param controlPrefixedId     prefixed id of the bound element
     * @return                      compact shadow tree document
     */
    private Document filterShadowTree(IndentedLogger indentedLogger, Document fullShadowTree, Element boundElement, String prefix,
                                      Scope innerScope, String controlPrefixedId) {
        assert StringUtils.isNotBlank(prefix);

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.startHandleOperation("", "filtering shadow tree", "bound element", Dom4jUtils.elementToDebugString(boundElement));
        }

        // Filter the tree
        final String baseURI = XFormsUtils.resolveXMLBase(boundElement, ".").toString();
        final LocationDocumentResult result = filterShadowTree(fullShadowTree, prefix, innerScope, controlPrefixedId, baseURI);

        // Extractor produces /static-state/xbl:template, so extract the nested element
        final Document compactShadowTree = Dom4jUtils.createDocumentCopyParentNamespaces(result.getDocument().getRootElement().element(XFormsConstants.XBL_TEMPLATE_QNAME), true);

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(compactShadowTree) : null);
        }

        return compactShadowTree;
    }

    private LocationDocumentResult filterShadowTree(Document fullShadowTree, String prefix, Scope innerScope, String controlPrefixedId, String baseURI) {
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result= new LocationDocumentResult();
        identity.setResult(result);

        // Run transformation and gather scope mappings
        // Get ids of the two scopes
        final Scope outerScope = prefixedIdToXBLScopeMap.get(controlPrefixedId);
        gatherScopeMappingsAndTransform(fullShadowTree, prefix, innerScope, outerScope, XFormsConstants.XXBLScope.inner, identity, true, baseURI);

        return result;
    }

    private void gatherScopeMappingsAndTransform(Document document, String prefix, Scope innerScope, Scope outerScope,
                                                 XFormsConstants.XXBLScope startScope, ContentHandler result, boolean ignoreRootElement, String baseURI) {
        // Run transformation which gathers scope information and extracts compact tree into the output ContentHandler
        TransformerUtils.writeDom4j(document, new ScopeExtractorContentHandler(result, prefix, innerScope, outerScope, ignoreRootElement, startScope, baseURI));
    }

    private static final String scopeURI = XFormsConstants.XXBL_SCOPE_QNAME.getNamespaceURI();
    private static final String scopeLocalname = XFormsConstants.XXBL_SCOPE_QNAME.getName();

    private class ScopeExtractorContentHandler extends XFormsExtractorContentHandler {

        private final String prefix;
        private final Scope innerScope;
        private final Scope outerScope;

        // TODO: Stack is synchronized, what other collection can we use?
        final Stack<XFormsConstants.XXBLScope> scopeStack = new Stack<XFormsConstants.XXBLScope>();

        /**
         *
         * @param contentHandler        output of transformation
         * @param prefix                prefix of the ids within the new shadow tree, e.g. "my-stuff$my-foo-bar$"
         * @param innerScope            inner scope
         * @param outerScope            outer scope, i.e. scope of the bound element
         * @param ignoreRootElement     whether root element must just be skipped
         * @param baseURI               base URI of new tree
         * @param startScope            scope of root element
         */
        public ScopeExtractorContentHandler(ContentHandler contentHandler, String prefix, Scope innerScope, Scope outerScope,
                                            boolean ignoreRootElement, XFormsConstants.XXBLScope startScope, String baseURI) {
            super(contentHandler, ignoreRootElement, baseURI);
            assert innerScope != null;
            assert outerScope != null;

            this.prefix = prefix;
            this.innerScope = innerScope;
            this.outerScope = outerScope;

            scopeStack.push(startScope);
        }

        @Override
        protected void startXFormsOrExtension(String uri, String localname, String qName, Attributes attributes) {
            // Handle xxbl:scope
            final XFormsConstants.XXBLScope currentScope;
            {
                final String scopeAttribute = attributes.getValue(scopeURI, scopeLocalname);
                if (scopeAttribute != null) {
                    scopeStack.push(XFormsConstants.XXBLScope.valueOf(scopeAttribute));
                } else {
                    scopeStack.push(scopeStack.peek());
                }
                currentScope = scopeStack.peek();
            }

            // Index prefixed id => scope
            final String staticId = attributes.getValue("id");
            assert staticId != null;
            final String prefixedId = prefix + staticId;
            final Scope scope;
            {
                if (prefixedIdToXBLScopeMap.containsKey(prefix)) // enforce constraint that mapping must be unique
                    throw new OXFException("Duplicate id found for effective id: " + prefixedId);

                if (currentScope == XFormsConstants.XXBLScope.inner) {
                    scope = innerScope;
                } else {
                    scope = outerScope;
                }
                prefixedIdToXBLScopeMap.put(prefixedId, scope);
            }

            // Index static id => prefixed id by scope
            {
                Map<String, String> staticIdToPrefixedIdMap = scopeToIdMap.get(scope);
                if (staticIdToPrefixedIdMap == null) {
                    staticIdToPrefixedIdMap = new HashMap<String, String>();
                    scopeToIdMap.put(scope, staticIdToPrefixedIdMap);
                }

                if (staticIdToPrefixedIdMap.containsKey(staticId)) // enforce constraint that mapping must be unique
                    throw new OXFException("Duplicate id found for static id: " + staticId);

                staticIdToPrefixedIdMap.put(staticId, prefixedId);
            }
        }
        @Override
        protected void endXFormsOrExtension(String uri, String localname, String qName) {
            // Handle xxbl:scope
            scopeStack.pop();
        }
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
     * Return whether this document has at least one component in use.
     */
//    public boolean hasComponentsInUse() {
//        return xblComponentBindings != null && xblComponentBindings.size() > 0;
//    }

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
     * Return the id of the <xbl:binding> element associated with the given prefixed control id.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      binding id or null if not found
     */
    public String getBindingId(String controlPrefixedId) {
        return (xblBindingIds == null) ? null : xblBindingIds.get(controlPrefixedId);
    }

    /**
     * Whether the given prefixed control id has a binding.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      true iif id has an associated binding
     */
    public boolean hasBinding(String controlPrefixedId) {
        return (xblBindingIds != null) && xblBindingIds.get(controlPrefixedId) != null;
    }

    /**
     * Return a List of xbl:style elements.
     *
     * @return list of <xbl:style> elements
     */
    public List<Element> getXBLStyles() {
        return xblStyles;
    }

    /**
     * Return a List of xbl:script elements.
     *
     * @return list of <xbl:script> elements
     */
    public List<Element> getXBLScripts() {
        return xblScripts;
    }

    public void freeTransientState() {
        // Not needed after analysis
        metadata = null;
    }
}
