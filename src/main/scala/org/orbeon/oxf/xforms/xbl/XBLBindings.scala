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
package org.orbeon.oxf.xforms.xbl

import org.dom4j._
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.util.{IndentedLogger, Logging, Whitespace}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData, LocationDocumentResult}
import org.xml.sax.Attributes

import scala.collection.mutable


/**
 * All the information statically gathered about XBL bindings.
 *
 * TODO:
 *
 * - xbl:handler and models under xbl:implementation are copied for each binding. We should be able to do this better:
 *   - do the "id" part of annotation only once
 *   - therefore keep a single DOM for all uses of those
 *   - however, if needed, still register namespace mappings by prefix once per mapping
 * - P2: even for templates that produce the same result per each instantiation:
 *   - detect that situation (when is this possible?)
 *   - keep a single DOM
 */
class XBLBindings(
    indentedLogger : IndentedLogger,
    partAnalysis   : PartAnalysisImpl,
    metadata       : Metadata,
    inlineXBL      : Seq[Element]
) extends Logging {

    private implicit val Logger = indentedLogger

    // For unit test written in Java
    def this(indentedLogger: IndentedLogger, partAnalysis: PartAnalysisImpl, metadata: Metadata) =
        this(indentedLogger, partAnalysis, metadata: Metadata, Seq.empty)

    // We now know all inline XBL bindings, which we didn't in XFormsAnnotator. So
    // NOTE: Inline bindings are only extracted at the top level of a part. We could imagine extracting them within
    // all XBL components. They would then have to be properly scoped.
    if (partAnalysis ne null) // for unit test which passes null in!
        metadata.extractInlineXBL(inlineXBL, partAnalysis.startScope)

    private val logShadowTrees = false // whether to log shadow trees as they are built

    /*
     * Notes about id generation
     *
     * Two approaches:
     *
     * - use shared IdGenerator
     *   - simpler
     *   - drawback: automatic ids grow larger
     *   - works for id allocation, but not for checking duplicate ids, but we do duplicate id check separately for XBL
     *     anyway in ScopeExtractorContentHandler
     * - use separate outer/inner scope IdGenerator
     *   - more complex
     *   - requires to know inner/outer scope at annotation time
     *   - requires XFormsAnnotator to provide start/end of XForms element
     *
     * As of 2009-09-14, we use an IdGenerator shared among top-level and all XBL bindings.
     */

    case class Global(templateTree: SAXStore, compactShadowTree: Document)

    val concreteBindings            = mutable.HashMap[String, ConcreteBinding]()
    val abstractBindingsWithGlobals = mutable.ArrayBuffer[AbstractBinding]()
    val allGlobals                  = mutable.ArrayBuffer[Global]()

    // Create concrete binding if there is an applicable abstract binding
    def processElementIfNeeded(
        controlElement    : Element,
        controlPrefixedId : String,
        locationData      : LocationData,
        containerScope    : Scope
    ): Option[ConcreteBinding] =
        metadata.findBindingByPrefixedId(controlPrefixedId) flatMap { abstractBinding ⇒
            generateRawShadowTree(controlElement, abstractBinding) map {
                rawShadowTree ⇒
                    val newBinding =
                        createConcreteBinding(
                            controlElement,
                            controlPrefixedId,
                            locationData,
                            containerScope,
                            abstractBinding,
                            rawShadowTree
                        )
                    concreteBindings += controlPrefixedId → newBinding
                    newBinding
            }
        }

    private def createConcreteBinding(
        boundElement           : Element,
        boundControlPrefixedId : String,
        locationData           : LocationData,
        containerScope         : Scope,
        abstractBinding        : AbstractBinding,
        rawShadowTree          : Document
    ): ConcreteBinding = {

        // New prefix corresponds to bound element prefixed id
        //val newPrefix = boundControlPrefixedId + COMPONENT_SEPARATOR

        val newInnerScope = partAnalysis.newScope(containerScope, boundControlPrefixedId)
        // NOTE: Outer scope is not necessarily the container scope!
        val outerScope = partAnalysis.scopeForPrefixedId(boundControlPrefixedId)

        // Annotate control tree
        val (templateTree, compactShadowTree) =
            annotateAndExtractSubtree(
                Some(boundElement),
                rawShadowTree,
                newInnerScope,
                outerScope,
                XXBLScope.inner,
                newInnerScope,
                hasFullUpdate(rawShadowTree),
                ignoreRoot = true
            )

        // Annotate event handlers and implementation models
        def annotateByElement(element: Element) =
            annotateSubtreeByElement(boundElement, element, newInnerScope, outerScope, XXBLScope.inner, newInnerScope)

        val annotatedHandlers = abstractBinding.handlers      map annotateByElement
        val annotatedModels   = abstractBinding.modelElements map annotateByElement

        // Remember concrete binding information
        val newConcreteBinding =
            ConcreteBinding(
                abstractBinding,
                newInnerScope,
                outerScope,
                annotatedHandlers,
                annotatedModels,
                templateTree,
                compactShadowTree
            )

        // Process globals here as the component is in use
        processGlobalsIfNeeded(abstractBinding, locationData)

        // Extract xbl:xbl/xbl:script and xbl:binding/xbl:resources/xbl:style
        // TODO: should do this here, in order to include only the scripts and resources actually used

        newConcreteBinding
    }

    /**
     * From a raw non-control tree (handlers, models) rooted at an element, produce a full annotated tree.
     */
    def annotateSubtreeByElement(
        boundElement   : Element,
        element        : Element,
        innerScope     : Scope,
        outerScope     : Scope,
        startScope     : XXBLScope,
        containerScope : Scope
    ): Element = annotateSubtree1(
            Some(boundElement),
            Dom4jUtils.createDocumentCopyParentNamespaces(element, false),
            innerScope,
            outerScope,
            startScope,
            containerScope,
            hasFullUpdate = false,
            ignoreRoot = false
        ).getRootElement

    // Annotate a tree
    def annotateSubtree1(
       boundElement   : Option[Element], // for xml:base resolution
       rawTree        : Node,
       innerScope     : Scope,
       outerScope     : Scope,
       startScope     : XXBLScope,
       containerScope : Scope,
       hasFullUpdate  : Boolean,
       ignoreRoot     : Boolean
    ): Document = withDebug("annotating tree") {

        val baseURI = XFormsUtils.resolveXMLBase(boundElement.orNull, null, ".").toString
        val fullAnnotatedTree = annotateShadowTree(rawTree, containerScope.fullPrefix)

        TransformerUtils.writeDom4j(
            fullAnnotatedTree,
            new ScopeExtractor(null, innerScope, outerScope, startScope, containerScope.fullPrefix, baseURI)
        )

        fullAnnotatedTree
    }

    // Annotate a subtree and return a template and compact tree
    def annotateAndExtractSubtree(
       boundElement   : Option[Element], // for xml:base resolution
       rawTree        : Node,
       innerScope     : Scope,
       outerScope     : Scope,
       startScope     : XXBLScope,
       containerScope : Scope,
       hasFullUpdate  : Boolean,
       ignoreRoot     : Boolean
    ): (SAXStore, Document) = withDebug("annotating and extracting tree") {

        val baseURI = XFormsUtils.resolveXMLBase(boundElement.orNull, null, ".").toString

        val (templateTree, compactTree) = {

            val templateOutput = new SAXStore

            val extractorOutput = TransformerUtils.getIdentityTransformerHandler
            val extractorDocument = new LocationDocumentResult
            extractorOutput.setResult(extractorDocument)

            TransformerUtils.writeDom4j(rawTree,
                new WhitespaceXMLReceiver(
                    new XFormsAnnotator(
                        templateOutput,
                        new ScopeExtractor(
                            new WhitespaceXMLReceiver(
                                extractorOutput,
                                Whitespace.defaultBasePolicy,
                                Whitespace.basePolicyMatcher
                            ),
                            innerScope,
                            outerScope,
                            startScope,
                            containerScope.fullPrefix,
                            baseURI
                        ),
                        metadata,
                        false
                    ) {
                        // Use prefixed id for marks and namespaces in order to avoid clashes between top-level
                        // controls and shadow trees
                        protected override def rewriteId(id: String) = containerScope.fullPrefix + id
                    },
                    Whitespace.defaultHTMLPolicy,
                    Whitespace.htmlPolicyMatcher
                )
            )

            (templateOutput, extractorDocument.getDocument)
        }

        if (logShadowTrees)
            debugResults(Seq(
                "full tree"    → Dom4jUtils.domToString(TransformerUtils.saxStoreToDom4jDocument(templateTree)),
                "compact tree" → Dom4jUtils.domToString(compactTree)
            ))

        // Result is full annotated tree and, if needed, the compact tree
        (templateTree, compactTree)
    }

    private def processGlobalsIfNeeded(abstractBinding: AbstractBinding, locationData: LocationData): Unit =
        if (partAnalysis.isTopLevel) // see also "Issues with xxbl:global" in PartAnalysisImpl
            abstractBinding.global match {
                case Some(globalDocument) if ! abstractBindingsWithGlobals.exists(abstractBinding eq) ⇒

                    val (globalTemplateTree, globalCompactShadowTree) =
                        withDebug("generating global XBL shadow content", Seq("binding id" → abstractBinding.bindingId.orNull)) {

                            val topLevelScopeForGlobals = partAnalysis.startScope

                            // TODO: in script mode, XHTML elements in template should only be kept during page generation
                            annotateAndExtractSubtree(
                                boundElement   = None,
                                rawTree        = globalDocument,
                                innerScope     = topLevelScopeForGlobals,
                                outerScope     = topLevelScopeForGlobals,
                                startScope     = XXBLScope.inner,
                                containerScope = topLevelScopeForGlobals,
                                hasFullUpdate  = hasFullUpdate(globalDocument),
                                ignoreRoot     = true
                            )
                        }

                    abstractBindingsWithGlobals += abstractBinding
                    allGlobals                  += Global(globalTemplateTree, globalCompactShadowTree)

                case _ ⇒ // no global to process
            }

    // Generate raw (non-annotated) shadow content for the given control id and XBL binding.
    private def generateRawShadowTree(boundElement: Element, abstractBinding: AbstractBinding): Option[Document] =
        abstractBinding.templateElement map {
            templateElement ⇒
                withDebug("generating raw XBL shadow content", Seq("binding id" → abstractBinding.bindingId.orNull)) {

                    // TODO: in script mode, XHTML elements in template should only be kept during page generation

                    // Here we create a completely separate document

                    // 1. Apply optional preprocessing step (usually XSLT)
                    // If @xxbl:transform is not present, just use a copy of the template element itself
                    val shadowTreeDocument =
                        abstractBinding.newTransform(boundElement) getOrElse
                            Dom4jUtils.createDocumentCopyParentNamespaces(templateElement)

                    // 2. Apply xbl:attr, xbl:content, xxbl:attr and index xxbl:scope
                    XBLTransformer.transform(
                        shadowTreeDocument,
                        boundElement,
                        abstractBinding.modeHandlers,
                        abstractBinding.modeLHHA,
                        abstractBinding.supportAVTs
                    )
                }
        }

    private def hasFullUpdate(shadowTreeDocument: Document) =
        if (Version.isPE) {
            var hasUpdateFull = false

            Dom4jUtils.visitSubtree(shadowTreeDocument.getRootElement, new Dom4jUtils.VisitorListener {
                def startElement(element: Element): Unit = {
                    val xxformsUpdate = element.attributeValue(XXFORMS_UPDATE_QNAME)
                    if (XFORMS_FULL_UPDATE == xxformsUpdate)
                        hasUpdateFull = true
                }

                def endElement(element: Element) = ()
                def text(text: Text) = ()
            }, true)

            hasUpdateFull
        } else
            false

    // Keep public for unit tests
    def annotateShadowTree(shadowTree: Node, prefix: String): Document = {

        // Create transformer
        val identity = TransformerUtils.getIdentityTransformerHandler

        // Set result
        val documentResult = new LocationDocumentResult
        identity.setResult(documentResult)

        // Put SAXStore in the middle if we have full updates
        val output = identity

        // Write the document through the annotator
        // TODO: this adds xml:base on root element, must fix
        TransformerUtils.writeDom4j(shadowTree, new XFormsAnnotator(output, null, metadata, false) {
            // Use prefixed id for marks and namespaces to avoid clashes between top-level controls and shadow trees
            protected override def rewriteId(id: String) = prefix + id
        })

        // Return annotated document
        documentResult.getDocument
    }

    private class ScopeExtractor(
        xmlReceiver : XMLReceiver,  // output of transformation or null
        innerScope  : Scope,        // inner scope
        outerScope  : Scope,        // outer scope, i.e. scope of the bound
        startScope  : XXBLScope,    // scope of root element
        prefix      : String,       // prefix of the ids within the new shadow tree
        baseURI     : String)       // base URI of new tree
    extends XFormsExtractor(xmlReceiver, metadata, null, baseURI, startScope, false, false, true) {

        assert(innerScope ne null)
        assert(outerScope ne null)

        override def getPrefixedId(staticId: String) = prefix + staticId

        override def indexElementWithScope(
            uri          : String,
            localname    : String,
            attributes   : Attributes,
            currentScope : XXBLScope
        ): Unit = {

            // Index prefixed id ⇒ scope
            val staticId = attributes.getValue("id")

            // NOTE: We can be called on HTML elements within LHHA, which may or may not have an id (they must have one
            // if they have AVTs).
            if (staticId ne null) {
                val prefixedId = prefix + staticId
                if (metadata.getNamespaceMapping(prefixedId) ne null) {
                    val scope = if (currentScope == XXBLScope.inner) innerScope else outerScope

                    // Enforce constraint that mapping must be unique
                    if (scope.contains(staticId))
                        throw new OXFException("Duplicate id found for static id: " + staticId)

                    // Index scope
                    partAnalysis.mapScopeIds(staticId, prefixedId, scope, ignoreIfPresent = false)

                    // Index AVT `for` if needed
                    if (uri == XXFORMS_NAMESPACE_URI && localname == "attribute") {
                        val forStaticId = attributes.getValue("for")
                        val forPrefixedId = prefix + forStaticId

                        partAnalysis.mapScopeIds(forStaticId, forPrefixedId, scope, ignoreIfPresent = true)
                    }
                }
            }
        }
    }

    // NOTE: We used to clear metadata, but since the enclosing PartAnalysisImpl keeps a reference to metadata, there is
    // no real point to it.
    def freeTransientState() =
        metadata.commitBindingIndex()

    // This function is not called as of 2011-06-28 but if/when we support removing scopes, check these notes:
    // - deindex prefixed ids ⇒ Scope
    // - remove models associated with scope
    // - remove control analysis
    // - deindex scope id ⇒ Scope
    //def removeScope(scope: Scope) = ???

    def hasBinding(controlPrefixedId: String)          = getBinding(controlPrefixedId).isDefined
    def getBinding(controlPrefixedId: String)          = concreteBindings.get(controlPrefixedId)
    def removeBinding(controlPrefixedId: String): Unit = concreteBindings -= controlPrefixedId
    // NOTE: Can't update abstractBindings, allScripts, allStyles, allGlobals without checking all again, so for now
    // leave that untouched.
}
