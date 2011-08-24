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
import org.dom4j.Text;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.Metadata;
import org.orbeon.oxf.xforms.analysis.PartAnalysisImpl;
import org.orbeon.oxf.xforms.analysis.XFormsAnnotatorContentHandler;
import org.orbeon.oxf.xforms.analysis.XFormsExtractorContentHandler;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

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

    public XBLBindingsBase(PartAnalysisImpl partAnalysis, Metadata metadata) {

        this.partAnalysis = partAnalysis;
        this.metadata = metadata;

        this.logShadowTrees = XFormsProperties.getDebugLogging().contains("analysis-xbl-tree");
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

    public void freeTransientState() {
        // Not needed after analysis
        metadata = null;
    }
}
