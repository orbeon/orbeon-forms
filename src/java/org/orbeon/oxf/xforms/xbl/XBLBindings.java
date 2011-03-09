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
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.Text;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactory;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.util.PropertyContext;
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
import org.orbeon.oxf.xforms.processor.handlers.XHTMLHeadHandler;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.xml.sax.Attributes;
import scala.Tuple2;

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

    public static final String XBL_MAPPING_PROPERTY_PREFIX = "oxf.xforms.xbl.mapping.";

    private final XFormsStaticState staticState;            // associated static state

    private final boolean logShadowTrees;                   // whether to log shadow trees as they are built

    // Static xbl:xbl
    private Map<QName, XFormsControlFactory.Factory> xblComponentsFactories;    // Map<QName bindingQName, Factory> of QNames to component factory
    private Map<QName, AbstractBinding> xblComponentBindings;       // Map<QName bindingQName, Element bindingElement> of QNames to bindings
    private List<Element> allScripts;                       // List<Element scriptElement>
    private List<Element> allStyles;                        // List<Element styleElement>
    private Map<QName, List<Element>> xblHandlers;          // Map<QName bindingQName, List<Element handlerElement>>
    private Map<QName, List<Document>> xblImplementations;  // Map<QName bindingQName, List<Document>>

    private Tuple2<scala.collection.Set<String>, scala.collection.Set<String>> baselineResources;

    // Abstract XBL bindings
    public static class AbstractBinding {
        public final QName qNameMatch;
        public final Element bindingElement;
        public final String bindingId;
        public final List<Element> scripts;
        public final List<Element> styles;

        public AbstractBinding(Element bindingElement, NamespaceMapping namespaceMapping, List<Element> scripts, IdGenerator idGenerator) {
            this.bindingElement = bindingElement;
            this.scripts = scripts;

            // For now, only handle "prefix|name" selectors
            // NOTE: Pass blank prefix as XBL bindings are all within the top-level document
            final String elementAttribute = bindingElement.attributeValue(XFormsConstants.ELEMENT_QNAME);
            qNameMatch = Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, elementAttribute.replace('|', ':'), true);

            // Binding id
            final String existingBindingId = XFormsUtils.getElementStaticId(bindingElement);
            this.bindingId = (existingBindingId != null) ? existingBindingId : (idGenerator != null) ? idGenerator.getNextId() : null; // idGenerator can be null when this is used to find styles

            // Extract xbl:binding/xbl:resources/xbl:style
            List<Element> styles = null;
            final List<Element> resourcesElements = Dom4jUtils.elements(bindingElement, XFormsConstants.XBL_RESOURCES_QNAME);
            for (final Element resourcesElement : resourcesElements) {
                final List<Element> styleElements = Dom4jUtils.elements(resourcesElement, XFormsConstants.XBL_STYLE_QNAME);

                if (styleElements.size() > 0) {
                    if (styles == null)
                        styles = new ArrayList<Element>(styleElements.size());

                    styles.addAll(styleElements);
                }
            }
            this.styles = styles != null ? styles : Collections.<Element>emptyList();
        }
    }

    // Concrete XBL bindings
    public static class ConcreteBinding {
        public final Scope innerScope;
        public final Document fullShadowTree;       // with full content, e.g. XHTML
        public final Document compactShadowTree;    // without full content, only the XForms controls
        public final String bindingId;
        public String containerElementName;

        public ConcreteBinding(AbstractBinding abstractBinding, Scope innerScope, Document fullShadowTree, Document compactShadowTree) {
            this.innerScope = innerScope;
            this.fullShadowTree = fullShadowTree;
            this.compactShadowTree = compactShadowTree;

            this.bindingId = abstractBinding.bindingId;
            assert this.bindingId != null : "missing id on XBL binding for " + Dom4jUtils.elementToDebugString(abstractBinding.bindingElement);

            this.containerElementName = abstractBinding.bindingElement.attributeValue(XFormsConstants.XXBL_CONTAINER_QNAME);
            if (this.containerElementName == null)
                this.containerElementName = "div";
        }
    }

    private Map<String, ConcreteBinding> concreteBindings;  // indexed by treePrefixedId

    private final Map<String, Scope> prefixedIdToXBLScopeMap = new HashMap<String, Scope>();                // maps element prefixed id => XBL scope
    private final Map<Scope, Map<String, String>> scopeToIdMap = new HashMap<Scope, Map<String, String>>(); // maps XBL scope => (map static id => prefixed id)
    private final Map<String, Scope> scopeIds = new HashMap<String, Scope>();                               // all distinct scopes by scope id

    private final Scope TOP_LEVEL_SCOPE = new Scope(null, "");

    public class Scope {
        public final Scope parent;
        public final String scopeId;

        private Scope(Scope parent, String scopeId) {
            assert parent != null || scopeId.equals("");
            assert !scopeIds.containsKey(scopeId);
            
            this.parent = parent;
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

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Scope && (this == obj || scopeId.equals(((Scope) obj).scopeId));
        }
    }

    public Scope getTopLevelScope() {
        return TOP_LEVEL_SCOPE;
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
    private XFormsStaticState.Metadata metadata;

    public XBLBindings(IndentedLogger indentedLogger, XFormsStaticState staticState,
                       XFormsStaticState.Metadata metadata, Element staticStateElement) {

        this.staticState = staticState;
        this.metadata = metadata;

        this.logShadowTrees = XFormsProperties.getDebugLogging().contains("analysis-xbl-tree");

        // Obtain list of XBL documents
        final List<Document> xblDocuments = new ArrayList<Document>();
        {
            // Get inline <xbl:xbl> elements
            final List<Element> xblElements = Dom4jUtils.elements(staticStateElement, XFormsConstants.XBL_XBL_QNAME);
            for (Element xblElement: xblElements) {
                // Copy the element because we may need it in staticStateDocument for encoding
                xblDocuments.add(Dom4jUtils.createDocumentCopyParentNamespaces(xblElement));
            }

            // Get automatically-included XBL documents
            final Set<String> includes = metadata.getBindingsIncludes();
            if (includes != null) {
                for (final String include: includes) {
                    xblDocuments.add(readXBLResource(include));
//                    System.out.println(Dom4jUtils.domToPrettyString(xblDocuments.get(xblDocuments.size() - 1)));
                }
            }
        }

        if (xblDocuments.size() > 0) {
            // Process <xbl:xbl>

            indentedLogger.startHandleOperation("", "extracting top-level XBL documents");

            int xblBindingCount = 0;
            for (final Document xblDocument: xblDocuments) {
                xblBindingCount += extractXBLBindings(xblDocument, staticState);
            }

            indentedLogger.endHandleOperation("xbl:xbl count", Integer.toString(xblDocuments.size()),
                    "xbl:binding count", Integer.toString(xblBindingCount));
        }
    }

    public Document readXBLResource(String include) {
        // Update last modified so that dependencies on external XBL files can be handled
        final long lastModified = ResourceManagerWrapper.instance().lastModified(include, false);
        metadata.updateBindingsLastModified(lastModified);

        // Read
        return ResourceManagerWrapper.instance().getContentAsDOM4J(include, XMLUtils.ParserConfiguration.XINCLUDE_ONLY, false);
    }

    public int extractXBLBindings(Document xblDocument, XFormsStaticState staticState) {

        // Perform initialization for first binding
        if (concreteBindings == null) {
              concreteBindings = new HashMap<String, ConcreteBinding>();

           // Add existing ids to scope map so we can check duplicates
            final Map<String, String> topLevelScopeMap = new HashMap<String, String>();
            scopeToIdMap.put(TOP_LEVEL_SCOPE, topLevelScopeMap);
            for (Iterator<String> i = metadata.idGenerator.iterator(); i.hasNext();) {
                final String id = i.next();
                topLevelScopeMap.put(id, id);
                prefixedIdToXBLScopeMap.put(id, TOP_LEVEL_SCOPE);
            }

            // Tell top-level static id generator to stop t check for duplicate ids
            metadata.idGenerator.setCheckDuplicates(false);

            xblComponentsFactories = new HashMap<QName, XFormsControlFactory.Factory>();
            xblComponentBindings = new HashMap<QName, AbstractBinding>();
        }

        // Extract xbl:xbl/xbl:script
        // TODO: should do this differently, in order to include only the scripts and resources actually used
        final List<Element> scriptElements = Dom4jUtils.elements(xblDocument.getRootElement(), XFormsConstants.XBL_SCRIPT_QNAME);
        if (scriptElements.size() > 0) {
            if (allScripts == null)
                allScripts = new ArrayList<Element>();
            allScripts.addAll(scriptElements);
        }

        // Find bindings
        int xblBindingCount = 0;
        for (final Element currentBindingElement : Dom4jUtils.elements(xblDocument.getRootElement(), XFormsConstants.XBL_BINDING_QNAME)) {
            final String currentElementAttribute = currentBindingElement.attributeValue(XFormsConstants.ELEMENT_QNAME);

            if (currentElementAttribute != null) {

                final AbstractBinding abstractBinding = new AbstractBinding(currentBindingElement, staticState.getNamespaceMapping("", currentBindingElement), scriptElements, metadata.idGenerator);

                // Create and remember factory for this QName
                xblComponentsFactories.put(abstractBinding.qNameMatch,
                    new XFormsControlFactory.Factory() {
                        public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId, Map<String, Element> state) {
                            return new XFormsComponentControl(container, parent, element, name, effectiveId);
                        }
                    });

                if (allStyles == null)
                    allStyles = new ArrayList<Element>(abstractBinding.styles.size());
                allStyles.addAll(abstractBinding.styles);
                xblComponentBindings.put(abstractBinding.qNameMatch, abstractBinding);

                // Extract xbl:handlers/xbl:handler
                {
                    final Element handlersElement = currentBindingElement.element(XFormsConstants.XBL_HANDLERS_QNAME);
                    if (handlersElement != null) {
                        final List<Element> handlerElements = Dom4jUtils.elements(handlersElement, XFormsConstants.XBL_HANDLER_QNAME);

                        if (xblHandlers == null) {
                            xblHandlers = new LinkedHashMap<QName, List<Element>>();
                        }

                        xblHandlers.put(abstractBinding.qNameMatch, handlerElements);
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

                        xblImplementations.put(abstractBinding.qNameMatch, modelDocuments);
                    }
                }
                
                xblBindingCount++;
            }
        }
        return xblBindingCount;
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

    public ConcreteBinding processElementIfNeeded(PropertyContext propertyContext, IndentedLogger indentedLogger, Element controlElement,
                                       String controlPrefixedId, LocationData locationData,
                                       DocumentWrapper controlsDocumentInfo, Configuration xpathConfiguration, Scope scope) {

        if (xblComponentBindings != null) {
            final AbstractBinding abstractBinding = xblComponentBindings.get(controlElement.getQName());
            if (abstractBinding != null) {
                // A custom component is bound to this element

                // Find new prefix
                final String newPrefix = controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR;

                // Check how many automatic XBL includes we have so far
                final int initialIncludesCount; {
                    final Set<String> includes = metadata.getBindingsIncludes();
                    initialIncludesCount = includes != null ? includes.size() : 0;
                }

                // Generate the shadow content for this particular binding
                final Document fullShadowTreeDocument = generateShadowTree(propertyContext, indentedLogger, controlsDocumentInfo, controlElement, abstractBinding.bindingElement, newPrefix);
                if (fullShadowTreeDocument != null) {

                    // Process newly added automatic XBL includes if any
                    final Set<String> includes = metadata.getBindingsIncludes();
                    final int finalIncludesCount = includes != null ? includes.size() : 0;
                    if (finalIncludesCount > initialIncludesCount) {
                        indentedLogger.startHandleOperation("", "adding XBL bindings");
                        int xblBindingCount = 0;
                        final List<String> includesAsList = new ArrayList<String>(includes);
                        for (final String include: includesAsList.subList(initialIncludesCount, finalIncludesCount)) {
                            xblBindingCount += extractXBLBindings(readXBLResource(include), staticState);
                        }
                        indentedLogger.endHandleOperation("xbl:xbl count", Integer.toString(finalIncludesCount - initialIncludesCount),
                                "xbl:binding count", Integer.toString(xblBindingCount),
                                "total xbl:binding count", Integer.toString(xblComponentBindings.size()));
                    }

                    // Generate compact shadow tree for this static id
                    final Scope newInnerScope = new Scope(scope, controlPrefixedId);
                    final Scope outerScope = prefixedIdToXBLScopeMap.get(controlPrefixedId);

                    final Document compactShadowTreeDocument = filterShadowTree(indentedLogger, fullShadowTreeDocument, controlElement, newPrefix, newInnerScope, controlPrefixedId);

                    // Register models placed under xbl:implementation
                    if (xblImplementations != null) {
                        final List<Document> implementationModelDocuments = xblImplementations.get(controlElement.getQName());
                        if (implementationModelDocuments != null && implementationModelDocuments.size() > 0) {
                            // Say we DO annotate because these models are outside the template
                            addModelDocuments(implementationModelDocuments, newPrefix, true,
                                    newInnerScope, outerScope, XFormsConstants.XXBLScope.inner);
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
                            addModelDocuments(templateModelDocuments, newPrefix, false, newInnerScope, null, null);
                            if (indentedLogger.isDebugEnabled())
                                indentedLogger.logDebug("", "created and registered XBL template model documents", "count", Integer.toString(templateModelDocuments.size()));
                        }
                    }

                    // Analyze the models first
                    staticState.analyzeModelsXPathForScope(newInnerScope);

                    // Remember concrete binding information
                    final ConcreteBinding newConcreteBinding = new ConcreteBinding(abstractBinding, newInnerScope, fullShadowTreeDocument, compactShadowTreeDocument);
                    concreteBindings.put(controlPrefixedId, newConcreteBinding);

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
                                        = annotateHandler(currentHandlerElement, newPrefix, newInnerScope, outerScope, XFormsConstants.XXBLScope.inner).getRootElement();

                                // NOTE: <xbl:handler> has similar attributes as XForms actions, in particular @event, @phase, etc.
                                final String controlStaticId = XFormsUtils.getStaticIdFromId(controlPrefixedId);
                                final String prefix = scope.getFullPrefix();
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

                    return newConcreteBinding;
                }
            }
        }
        return null;
    }

    public Document annotateHandler(Element currentHandlerElement, String newPrefix, Scope innerScope, Scope outerScope, XFormsConstants.XXBLScope startScope) {
        final Document handlerDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentHandlerElement, false);// for now, don't detach because element can be processed by multiple bindings
        final Document annotatedDocument = annotateShadowTree(handlerDocument, newPrefix, false);
        gatherScopeMappingsAndTransform(annotatedDocument, newPrefix, innerScope, outerScope, startScope, null, false, "/");
        return annotatedDocument;
    }

    private void addModelDocuments(List<Document> modelDocuments, String prefix, boolean annotate,
                                   Scope newInnerScope, Scope outerScope, XFormsConstants.XXBLScope startScope) {
        for (Document currentModelDocument: modelDocuments) {

            // Annotate if needed, otherwise leave as is
            if (annotate) {
                currentModelDocument = annotateShadowTree(currentModelDocument, prefix, false);
                gatherScopeMappingsAndTransform(currentModelDocument, prefix, newInnerScope, outerScope, startScope, null, false, "/");
            }

            // Store models by "prefixed id"
            staticState.addModelDocument(newInnerScope, currentModelDocument);
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
                indentedLogger.startHandleOperation("", "generating XBL shadow content",
                        "bound element", Dom4jUtils.elementToDebugString(boundElement),
                        "binding id", XFormsUtils.getElementStaticId(binding));
            }

            // TODO: in script mode, XHTML elements in template should only be kept during page generation

            // Here we create a completely separate document

            // 1. Apply optional preprocessing step (usually XSLT)
            // Copy as the template element may be used many times
            final Document shadowTreeDocument = applyPipelineTransform(templateElement, boundElement);

            // 2. Apply xbl:attr, xbl:content, xxbl:attr and index xxbl:scope
            XBLTransformer.transform(propertyContext, documentWrapper, shadowTreeDocument, boundElement);

            // 3: Annotate tree
            final boolean hasUpdateFull = hasFullUpdate(shadowTreeDocument);
            final Document annotatedShadowTreeDocument = annotateShadowTree(shadowTreeDocument, prefix, hasUpdateFull);

            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(annotatedShadowTreeDocument) : null);
            }

            return annotatedShadowTreeDocument;
        } else {
            return null;
        }
    }

    private boolean hasFullUpdate(Document shadowTreeDocument) {
        if (Version.isPE()) {
            final boolean[] hasUpdateFull = new boolean[1];
            Dom4jUtils.visitSubtree(shadowTreeDocument.getRootElement(), new Dom4jUtils.VisitorListener() {
                public void startElement(Element element) {
                    // Check if there is any xxforms:update="full"
                    final String xxformsUpdate = element.attributeValue(XFormsConstants.XXFORMS_UPDATE_QNAME);
                    if (XFormsConstants.XFORMS_FULL_UPDATE.equals(xxformsUpdate)) {
                        hasUpdateFull[0] = true;
                    }
                }
                public void endElement(Element element) {}
                public void text(Text text) {}
            }, true);
            return hasUpdateFull[0];
        } else {
            return false;
        }
    }

    // TODO: could this be done as a processor instead? could then have XSLT -> XBL -> XFACH and stream

    // Keep public for unit tests
    public Document annotateShadowTree(Document shadowTreeDocument, final String prefix, boolean hasFullUpdate) {
        // Create transformer
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();

        // Set result
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);

        // Put SAXStore in the middle if we have full updates
        final XMLReceiver output = hasFullUpdate ? new SAXStore(identity) : identity;

        // Write the document through the annotator
        // TODO: this adds xml:base on root element, must fix
        TransformerUtils.writeDom4j(shadowTreeDocument, new XFormsAnnotatorContentHandler(output, null, metadata) {
            @Override
            protected void addNamespaces(String id) {
                // Store prefixed id in order to avoid clashes between top-level controls and shadow trees
                super.addNamespaces(prefix + id);
            }

            @Override
            protected void addMark(String id, SAXStore.Mark mark) {
                super.addMark(prefix + id, mark);
            }
        });

        // Return annotated document
        return documentResult.getDocument();
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
        final String baseURI = XFormsUtils.resolveXMLBase(boundElement, null, ".").toString();
        final LocationDocumentResult result = filterShadowTree(fullShadowTree, prefix, innerScope, controlPrefixedId, baseURI);

        // Extractor produces /static-state/xbl:template, so extract the nested element
        final Document compactShadowTree = Dom4jUtils.createDocumentCopyParentNamespaces(result.getDocument().getRootElement().element(XFormsConstants.XBL_TEMPLATE_QNAME), true);

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(compactShadowTree) : null);
        }

        return compactShadowTree;
    }

    private LocationDocumentResult filterShadowTree(Document fullShadowTree, String prefix, Scope innerScope, String controlPrefixedId, String baseURI) {
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result = new LocationDocumentResult();
        identity.setResult(result);

        // Run transformation and gather scope mappings
        // Get ids of the two scopes
        final Scope outerScope = prefixedIdToXBLScopeMap.get(controlPrefixedId);
        gatherScopeMappingsAndTransform(fullShadowTree, prefix, innerScope, outerScope, XFormsConstants.XXBLScope.inner, identity, true, baseURI);

        return result;
    }

    private void gatherScopeMappingsAndTransform(Document document, String prefix, Scope innerScope, Scope outerScope,
                                                 XFormsConstants.XXBLScope startScope, XMLReceiver result, boolean ignoreRootElement, String baseURI) {
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
         * @param xmlReceiver           output of transformation
         * @param prefix                prefix of the ids within the new shadow tree, e.g. "my-stuff$my-foo-bar$"
         * @param innerScope            inner scope
         * @param outerScope            outer scope, i.e. scope of the bound element
         * @param ignoreRootElement     whether root element must just be skipped
         * @param baseURI               base URI of new tree
         * @param startScope            scope of root element
         */
        public ScopeExtractorContentHandler(XMLReceiver xmlReceiver, String prefix, Scope innerScope, Scope outerScope,
                                            boolean ignoreRootElement, XFormsConstants.XXBLScope startScope, String baseURI) {
            super(xmlReceiver, metadata, ignoreRootElement, baseURI);
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
            // NOTE: We can be called on HTML elements within LHHA, which may or may not have an id (they must have one if they have AVTs)
            if (staticId != null) {
                final String prefixedId = prefix + staticId;
                if (metadata.getNamespaceMapping(prefixedId) != null) {
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
            }
        }
        @Override
        protected void endXFormsOrExtension(String uri, String localname, String qName) {
            // Handle xxbl:scope
            scopeStack.pop();
        }
    }

    private static Document applyPipelineTransform(Element templateElement, Element boundElement) {
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
    public Map<QName, AbstractBinding> getComponentBindings() {
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
        final ConcreteBinding concreteBinding = (concreteBindings == null) ? null : concreteBindings.get(controlPrefixedId);
        return (concreteBinding == null) ? null : concreteBinding.fullShadowTree.getRootElement();
    }

    /**
     * Return the expanded shadow tree for the given prefixed control id, with only XForms controls and no markup.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      compact expanded shadow tree, or null
     */
    public Element getCompactShadowTree(String controlPrefixedId) {
        final ConcreteBinding concreteBinding = (concreteBindings == null) ? null : concreteBindings.get(controlPrefixedId);
        return (concreteBinding == null) ? null : concreteBinding.compactShadowTree.getRootElement();
    }

    /**
     * Return the id of the <xbl:binding> element associated with the given prefixed control id.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      binding id or null if not found
     */
    public String getBindingId(String controlPrefixedId) {
        final ConcreteBinding concreteBinding = (concreteBindings == null) ? null : concreteBindings.get(controlPrefixedId);
        return (concreteBinding == null) ? null : concreteBinding.bindingId;
    }

    /**
     * Whether the given prefixed control id has a binding.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      true iif id has an associated binding
     */
    public boolean hasBinding(String controlPrefixedId) {
        final ConcreteBinding concreteBinding = (concreteBindings == null) ? null : concreteBindings.get(controlPrefixedId);
        return concreteBinding != null && concreteBinding.bindingId != null;
    }

    public String getContainerElementName(String controlPrefixedId) {
        final ConcreteBinding concreteBinding = (concreteBindings == null) ? null : concreteBindings.get(controlPrefixedId);
        return (concreteBinding == null) ? null : concreteBinding.containerElementName;
    }

    /**
     * Return a List of xbl:style elements.
     *
     * @return list of <xbl:style> elements
     */
    public List<Element> getXBLStyles() {
        return allStyles != null ? allStyles : Collections.<Element>emptyList();
    }

    /**
     * Return a List of xbl:script elements.
     *
     * @return list of <xbl:script> elements
     */
    public List<Element> getXBLScripts() {
        return allScripts != null ? allScripts : Collections.<Element>emptyList();
    }

    public Tuple2<scala.collection.Set<String>, scala.collection.Set<String>> getBaselineResources() {
        if (baselineResources == null)
            baselineResources = XHTMLHeadHandler.getBaselineResources(staticState);
        return baselineResources;
    }

    public void freeTransientState() {
        // Not needed after analysis
        metadata = null;
    }
}
