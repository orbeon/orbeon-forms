/**
 * Copyright (C) 2011 Orbeon, Inc.
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
import org.orbeon.oxf.xforms.PartAnalysis;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.*;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.Attributes;
import scala.collection.Seq;

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
public class XBLBindingsBase {

    public static final String XBL_MAPPING_PROPERTY_PREFIX = "oxf.xforms.xbl.mapping.";
    protected final boolean logShadowTrees;                   // whether to log shadow trees as they are built

    protected final PartAnalysisImpl partAnalysis;

    // binding QName => control factory
    protected Map<QName, XFormsControlFactory.Factory> xblComponentsFactories = new HashMap<QName, XFormsControlFactory.Factory>();
    // QNames => abstract binding
    protected Map<QName, AbstractBinding> abstractBindings = new HashMap<QName, AbstractBinding>();
    // prefixed id => concrete binding
    protected Map<String, ConcreteBinding> concreteBindings = new HashMap<String, ConcreteBinding>();

    protected List<Element> allScripts;                       // List<Element scriptElement>
    protected List<Element> allStyles;                        // List<Element styleElement>

    // Abstract XBL bindings
    public static class AbstractBinding {
        public final QName qNameMatch;
        public final Element bindingElement;
        public final String bindingId;
        public final List<Element> scripts;
        public final List<Element> styles;
        public final List<Element> handlers;
        public final List<Document> implementations;
        public final Document global;

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

            // Extract xbl:handlers/xbl:handler
            final Element handlersElement = bindingElement.element(XFormsConstants.XBL_HANDLERS_QNAME);
            if (handlersElement != null) {
                this.handlers = Dom4jUtils.elements(handlersElement, XFormsConstants.XBL_HANDLER_QNAME);
            } else {
                this.handlers = Collections.emptyList();
            }

            // Extract xbl:implementation/xforms:model
            final Element implementationElement = bindingElement.element(XFormsConstants.XBL_IMPLEMENTATION_QNAME);
            if (implementationElement != null) {
                implementations = extractChildrenModels(implementationElement, true); // just detach because they are copied later anyway
            } else {
                implementations = Collections.emptyList();
            }

            // Extract global markup
            final Element globalElement = bindingElement.element(XFormsConstants.XXBL_GLOBAL_QNAME);
            if (globalElement == null) {
                global = null;
            } else {
                global = Dom4jUtils.createDocumentCopyParentNamespaces(globalElement, true);
            }
        }
    }

    // Concrete XBL bindings
    public static class ConcreteBinding {
        public final XBLBindingsBase.Scope innerScope;  // each binding defines a new scope
        public final Document fullShadowTree;       // with full content, e.g. XHTML
        public final Document compactShadowTree;    // without full content, only the XForms controls
        public final Seq<Model> models;             // all the models
        public final String bindingId;
        public String containerElementName;

        public ConcreteBinding(AbstractBinding abstractBinding, XBLBindingsBase.Scope innerScope, Document fullShadowTree, Document compactShadowTree, Seq<Model> models) {
            this.innerScope = innerScope;
            this.fullShadowTree = fullShadowTree;
            this.compactShadowTree = compactShadowTree;
            this.models = models;

            this.bindingId = abstractBinding.bindingId;
            assert this.bindingId != null : "missing id on XBL binding for " + Dom4jUtils.elementToDebugString(abstractBinding.bindingElement);

            this.containerElementName = (abstractBinding.bindingElement != null) ? abstractBinding.bindingElement.attributeValue(XFormsConstants.XXBL_CONTAINER_QNAME) : null;
            if (this.containerElementName == null)
                this.containerElementName = "div";
        }
    }

    public static class Global {
        public final Document fullShadowTree;       // with full content, e.g. XHTML
        public final Document compactShadowTree;    // without full content, only the XForms controls

        public Global(Document fullShadowTree, Document compactShadowTree) {
            this.fullShadowTree = fullShadowTree;
            this.compactShadowTree = compactShadowTree;
        }
    }

    public static class Scope {
        public final Scope parent;
        public final String scopeId;

        public final Map<String, String> idMap = new HashMap<String, String>(); // static id => prefixed id

        public Scope(Scope parent, String scopeId) {
            assert parent != null || scopeId.equals("");
            
            this.parent = parent;
            this.scopeId = scopeId;
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
            return idMap.get(staticId);
        }

        public boolean isTopLevelScope() {
            return scopeId.length() == 0;
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
    protected Metadata metadata;

    public XBLBindingsBase(IndentedLogger indentedLogger, PartAnalysisImpl partAnalysis,
                           Metadata metadata, List<Document> inlineXBLDocuments) {

        this.partAnalysis = partAnalysis;
        this.metadata = metadata;

        this.logShadowTrees = XFormsProperties.getDebugLogging().contains("analysis-xbl-tree");

        // Obtain list of XBL documents
        final List<Document> xblDocuments = new ArrayList<Document>();
        {
            // Get inline <xbl:xbl> elements
            xblDocuments.addAll(inlineXBLDocuments);

            // Get automatically-included XBL documents
            final Set<String> includes = metadata.getBingingIncludes();
            for (final String include : includes)
                xblDocuments.add(readXBLResource(include));
        }

        if (xblDocuments.size() > 0) {
            // Process <xbl:xbl>

            indentedLogger.startHandleOperation("", "extracting top-level XBL documents");

            int xblBindingCount = 0;
            for (final Document xblDocument: xblDocuments) {
                xblBindingCount += extractXBLBindings(xblDocument, partAnalysis);
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

    public int extractXBLBindings(Document xblDocument, PartAnalysis partAnalysis) {

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

                final AbstractBinding abstractBinding = new AbstractBinding(currentBindingElement, partAnalysis.getNamespaceMapping("", currentBindingElement), scriptElements, metadata.idGenerator());

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
                abstractBindings.put(abstractBinding.qNameMatch, abstractBinding);
                
                xblBindingCount++;
            }
        }
        return xblBindingCount;
    }

    protected static List<Document> extractChildrenModels(Element parentElement, boolean detach) {

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

    public Document annotateHandler(Element currentHandlerElement, String newPrefix, XBLBindingsBase.Scope innerScope, Scope outerScope, XFormsConstants.XXBLScope startScope) {
        final Document handlerDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentHandlerElement, false);// for now, don't detach because element can be processed by multiple bindings
        final Document annotatedDocument = annotateShadowTree(handlerDocument, newPrefix, false);
        gatherScopeMappingsAndTransform(annotatedDocument, newPrefix, innerScope, outerScope, startScope, null, false, "/");
        return annotatedDocument;
    }

    protected Document generateGlobalShadowTree(final IndentedLogger indentedLogger, Element binding, Document shadowTreeDocument) {

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.startHandleOperation("", "generating global XBL shadow content",
                    "binding id", XFormsUtils.getElementStaticId(binding));
        }

        // TODO: in script mode, XHTML elements in template should only be kept during page generation

        // Annotate tree
        final boolean hasUpdateFull = hasFullUpdate(shadowTreeDocument);
        final Document annotatedShadowTreeDocument = annotateShadowTree(shadowTreeDocument, partAnalysis.startScope().getFullPrefix(), hasUpdateFull);

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(annotatedShadowTreeDocument) : null);
        }

        return annotatedShadowTreeDocument;
    }

    protected boolean hasFullUpdate(Document shadowTreeDocument) {
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
     * @param outerScope            outer scope of the tree
     * @return                      compact shadow tree document
     */
    protected Document filterShadowTree(IndentedLogger indentedLogger, Document fullShadowTree, Element boundElement, String prefix,
                                      Scope innerScope, Scope outerScope) {

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.startHandleOperation("", "filtering shadow tree", "bound element", Dom4jUtils.elementToDebugString(boundElement));
        }

        // Filter the tree
        final String baseURI = XFormsUtils.resolveXMLBase(boundElement, null, ".").toString();
        final LocationDocumentResult result = filterShadowTree(fullShadowTree, prefix, innerScope, outerScope, baseURI);

        // Extractor produces /static-state/(xbl:template|xxbl:global), so extract the nested element
        final Document compactShadowTree = Dom4jUtils.createDocumentCopyParentNamespaces(result.getDocument().getRootElement().element(fullShadowTree.getRootElement().getQName()), true);

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.endHandleOperation("document", logShadowTrees ? Dom4jUtils.domToString(compactShadowTree) : null);
        }

        return compactShadowTree;
    }

    protected LocationDocumentResult filterShadowTree(Document fullShadowTree, String prefix, Scope innerScope, Scope outerScope, String baseURI) {
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result = new LocationDocumentResult();
        identity.setResult(result);

        // Run transformation and gather scope mappings
        gatherScopeMappingsAndTransform(fullShadowTree, prefix, innerScope, outerScope, XFormsConstants.XXBLScope.inner, identity, true, baseURI);

        return result;
    }

    protected void gatherScopeMappingsAndTransform(Document document, String prefix, Scope innerScope, Scope outerScope,
                                                 XFormsConstants.XXBLScope startScope, XMLReceiver result, boolean ignoreRootElement, String baseURI) {
        // Run transformation which gathers scope information and extracts compact tree into the output ContentHandler
        TransformerUtils.writeDom4j(document, new ScopeExtractorContentHandler(result, prefix, innerScope, outerScope, ignoreRootElement, startScope, baseURI));
    }

    protected static final String scopeURI = XFormsConstants.XXBL_SCOPE_QNAME.getNamespaceURI();
    protected static final String scopeLocalname = XFormsConstants.XXBL_SCOPE_QNAME.getName();

    protected class ScopeExtractorContentHandler extends XFormsExtractorContentHandler {

        protected final String prefix;
        protected final Scope innerScope;
        protected final Scope outerScope;

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

                    final Scope scope = (currentScope == XFormsConstants.XXBLScope.inner) ? innerScope : outerScope;

                    // Index scope by prefixed id
                    partAnalysis.indexScope(prefixedId, scope);

                    // Index static id => prefixed id by scope
                    if (scope.idMap.containsKey(staticId)) // enforce constraint that mapping must be unique
                        throw new OXFException("Duplicate id found for static id: " + staticId);

                    scope.idMap.put(staticId, prefixedId);
                }
            }
        }
        @Override
        protected void endXFormsOrExtension(String uri, String localname, String qName) {
            // Handle xxbl:scope
            scopeStack.pop();
        }
    }

    protected Document applyPipelineTransform(Element templateElement, Element boundElement) {
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
            boolean success = false;
            final Document generated;
            try {
                domSerializerData.start(newPipelineContext);

                // Get the result, move its root element into a xbl:template and return it
                generated = domSerializerData.getDocument(newPipelineContext);
                success = true;
            } finally {
                newPipelineContext.destroy(success);
            }
            final Element result = (Element) generated.getRootElement().detach();
            generated.addElement(new QName("template", XFormsConstants.XBL_NAMESPACE, "xbl:template"));
            final Element newRoot = generated.getRootElement();
            newRoot.add(XFormsConstants.XBL_NAMESPACE);
            newRoot.add(result);

            return generated;
        }
    }

    /**
     * All component bindings.
     *
     * @return Map<QName, Element> of QNames to bindings, or null
     */
    public Map<QName, AbstractBinding> getComponentBindings() {
        return abstractBindings;
    }

    /**
     * Return whether the given QName has an associated binding.
     *
     * @param qName QName to check
     * @return      true iif there is a binding
     */
    public boolean isComponent(QName qName) {
        return abstractBindings != null && abstractBindings.get(qName) != null;
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
     * Return the id of the <xbl:binding> element associated with the given prefixed control id.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      binding id or null if not found
     */
    public String getBindingId(String controlPrefixedId) {
        final ConcreteBinding concreteBinding = getBinding(controlPrefixedId);
        return (concreteBinding == null) ? null : concreteBinding.bindingId;
    }

    /**
     * Whether the given prefixed control id has a binding.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      true iif id has an associated binding
     */
    public boolean hasBinding(String controlPrefixedId) {
        final ConcreteBinding concreteBinding = getBinding(controlPrefixedId);
        return concreteBinding != null && concreteBinding.bindingId != null;
    }

    public ConcreteBinding getBinding(String controlPrefixedId) {
        return (concreteBindings == null) ? null : concreteBindings.get(controlPrefixedId);
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

    public void freeTransientState() {
        // Not needed after analysis
        metadata = null;
    }
}
