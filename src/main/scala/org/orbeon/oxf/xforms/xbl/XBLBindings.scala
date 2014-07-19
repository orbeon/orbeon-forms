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

import XBLBindings._
import org.orbeon.oxf.xforms._
import XFormsConstants._
import collection.mutable.LinkedHashMap
import org.dom4j._
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.{Whitespace, IndentedLogger, Logging}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.XBLResources.HeadElement
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.{LocationDocumentResult, Dom4jUtils, LocationData}
import org.xml.sax.Attributes
import collection.JavaConverters._


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
class XBLBindings(indentedLogger: IndentedLogger, partAnalysis: PartAnalysisImpl, var metadata: Metadata, inlineXBL: Seq[Element])
        extends Logging {

    private implicit val Logger = indentedLogger

    // For unit test written in Java
    def this(indentedLogger: IndentedLogger, partAnalysis: PartAnalysisImpl, metadata: Metadata) =
        this(indentedLogger, partAnalysis, metadata: Metadata, Seq.empty)
    
    private val logShadowTrees = true                  // whether to log shadow trees as they are built
    
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
     *   o requires XFormsAnnotator to provide start/end of XForms element
     *
     * As of 2009-09-14, we use an IdGenerator shared among top-level and all XBL bindings.
     */

    case class Global(templateTree: SAXStore, compactShadowTree: Document)

    val abstractBindings = LinkedHashMap[QName, AbstractBinding]()  // FIXME: might no longer need order as we sort bindings when needed
    val concreteBindings = LinkedHashMap[String, ConcreteBinding]() // FIXME: might no longer need order as we sort bindings when needed

    val allGlobals = LinkedHashMap[QName, Global]()

    // Inline <xbl:xbl> and automatically-included XBL documents
    private val xblDocuments = (inlineXBL map ((_, 0L))) ++
        (metadata.getBindingIncludesJava.asScala map readXBLResourceUpdateLastModified)

    // Process <xbl:xbl>
    if (xblDocuments.nonEmpty) {
        withDebug("generating global XBL shadow content") {

            val bindingCounts = xblDocuments map { case (element, lastModified) ⇒
                val bindingsForDoc = extractXBLBindings(None, element, lastModified)
                registerXBLBindings(bindingsForDoc)
                bindingsForDoc.size
            }

            debugResults(Seq("xbl:xbl count" → xblDocuments.size.toString, "xbl:binding count" → bindingCounts.sum.toString))

            bindingCounts
        }
    }

    // Create concrete binding if there is an applicable abstract binding
    def processElementIfNeeded(
            controlElement: Element,
            controlPrefixedId: String,
            locationData: LocationData,
            containerScope: Scope): Option[ConcreteBinding] =

        abstractBindings.get(controlElement.getQName) flatMap { abstractBinding ⇒

            // Generate the shadow content for this particular binding
            withProcessAutomaticXBL {
                generateRawShadowTree(controlElement, abstractBinding) map {
                    rawShadowTree ⇒
                        val newBinding =
                            createConcreteBinding(
                                controlElement,
                                controlPrefixedId,
                                locationData,
                                containerScope,
                                abstractBinding,
                                rawShadowTree)
                        concreteBindings += controlPrefixedId → newBinding
                        newBinding
                }
            }
        }

    private def registerXBLBindings(bindings: Traversable[AbstractBinding]) =
        bindings foreach (binding ⇒ abstractBindings += binding.qNameMatch → binding)

    private def withProcessAutomaticXBL[T](body: ⇒ T) = {
        // Check how many automatic XBL includes we have so far
        val initialIncludesCount = metadata.bindingIncludes.size

        // Run body
        val result = body

        // Process newly added automatic XBL includes if any
        val finalIncludesCount = metadata.bindingIncludes.size
        if (finalIncludesCount > initialIncludesCount) {
            withDebug("adding XBL bindings") {

                // Get new paths
                val newPaths = metadata.bindingIncludes.slice(initialIncludesCount, finalIncludesCount)

                // Extract and register new bindings
                val bindingCounts =
                    newPaths map { path ⇒
                        val (document, lastModified) = readXBLResourceUpdateLastModified(path)
                        val bindingsForDoc = extractXBLBindings(Some(path), document, lastModified)
                        registerXBLBindings(bindingsForDoc)
                        bindingsForDoc.size
                    }

                debugResults(Seq(
                    "xbl:xbl count" → (finalIncludesCount - initialIncludesCount).toString,
                    "xbl:binding count" → bindingCounts.sum.toString,
                    "total xbl:binding count" → abstractBindings.size.toString))

                None
            }
        }

        result
    }

    private def readXBLResourceUpdateLastModified(path: String) = {
        // Update last modified so that dependencies on external XBL files can be handled
        val result = readXBLResource(path)
        metadata.updateBindingsLastModified(result._2)
        result
    }

    private def createConcreteBinding(
            boundElement: Element,
            boundControlPrefixedId: String,
            locationData: LocationData,
            containerScope: Scope,
            abstractBinding: AbstractBinding,
            rawShadowTree: Document) = {

        // New prefix corresponds to bound element prefixed id
        //val newPrefix = boundControlPrefixedId + COMPONENT_SEPARATOR

        val newInnerScope = partAnalysis.newScope(containerScope, boundControlPrefixedId)
        // NOTE: Outer scope is not necessarily the container scope!
        val outerScope = partAnalysis.scopeForPrefixedId(boundControlPrefixedId)

        // Annotate control tree
        val (templateTree, compactShadowTree) =
            annotateAndExtractSubtree(Some(boundElement), rawShadowTree, newInnerScope, outerScope, XXBLScope.inner, newInnerScope, hasFullUpdate(rawShadowTree), ignoreRoot = true)

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
                compactShadowTree)

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
            boundElement: Element,
            element: Element,
            innerScope: Scope,
            outerScope: Scope,
            startScope: XXBLScope,
            containerScope: Scope) =
        annotateSubtree1(
            Some(boundElement),
            Dom4jUtils.createDocumentCopyParentNamespaces(element, false),
            innerScope,
            outerScope,
            startScope,
            containerScope,
            hasFullUpdate = false,
            ignoreRoot = false).getRootElement

    // Annotate a tree
    def annotateSubtree1(
           boundElement: Option[Element], // for xml:base resolution
           rawTree: Node,
           innerScope: Scope,
           outerScope: Scope,
           startScope: XXBLScope,
           containerScope: Scope,
           hasFullUpdate: Boolean,
           ignoreRoot: Boolean) = {

        withDebug("annotating tree") {
            
            val baseURI = XFormsUtils.resolveXMLBase(boundElement.orNull, null, ".").toString
            val fullAnnotatedTree = annotateShadowTree(rawTree, containerScope.fullPrefix)
    
            TransformerUtils.writeDom4j(
                fullAnnotatedTree,
                new ScopeExtractor(null, innerScope, outerScope, startScope, containerScope.fullPrefix, baseURI)
            )
            
            fullAnnotatedTree
        }
    }
    
    // Annotate a subtree and return a template and compact tree
    def annotateAndExtractSubtree(
           boundElement: Option[Element], // for xml:base resolution
           rawTree: Node,
           innerScope: Scope,
           outerScope: Scope,
           startScope: XXBLScope,
           containerScope: Scope,
           hasFullUpdate: Boolean,
           ignoreRoot: Boolean) = {

        withDebug("annotating and extracting tree") {
            
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
                            metadata) {
                            // Use prefixed id for marks and namespaces in order to avoid clashes between top-level controls and shadow trees
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
    }

    private def processGlobalsIfNeeded(abstractBinding: AbstractBinding, locationData: LocationData) {
        abstractBinding.global match {
            case Some(globalDocument) if ! allGlobals.contains(abstractBinding.qNameMatch) ⇒

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

                allGlobals += abstractBinding.qNameMatch → Global(globalTemplateTree, globalCompactShadowTree)

            case _ ⇒ // no global to process
        }
    }

    /**
     * Generate raw (non-annotated) shadow content for the given control id and XBL binding.
     *
     * @param boundElement      element to which the binding applies
     * @param abstractBinding  corresponding <xbl:binding>
     * @return Some shadow tree document if there is a template, None otherwise
     */
    private def generateRawShadowTree(boundElement: Element,
                                      abstractBinding: AbstractBinding): Option[Document] = {
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
                    XBLTransformer.transform(shadowTreeDocument, boundElement, abstractBinding.modeHandlers, abstractBinding.modeLHHA)
                }
        }
    }

    private def hasFullUpdate(shadowTreeDocument: Document) = {
        if (Version.isPE) {
            var hasUpdateFull = false

            Dom4jUtils.visitSubtree(shadowTreeDocument.getRootElement, new Dom4jUtils.VisitorListener {
                def startElement(element: Element) {
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
    }

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
        TransformerUtils.writeDom4j(shadowTree, new XFormsAnnotator(output, null, metadata) {
            // Use prefixed id for marks and namespaces in order to avoid clashes between top-level controls and shadow trees
            protected override def rewriteId(id: String) = prefix + id
        })

        // Return annotated document
        documentResult.getDocument
    }

    /**
     *
     * @param xmlReceiver           output of transformation or null
     * @param innerScope            inner scope
     * @param outerScope            outer scope, i.e. scope of the bound element
     * @param startScope            scope of root element
     * @param prefix                prefix of the ids within the new shadow tree, e.g. "my-stuff$my-foo-bar$"
     * @param baseURI               base URI of new tree
     */
    private class ScopeExtractor(
        xmlReceiver: XMLReceiver,
        innerScope: Scope,
        outerScope: Scope,
        startScope: XXBLScope,
        prefix: String,
        baseURI: String)
    extends XFormsExtractor(xmlReceiver, metadata, null, baseURI, startScope, false, false, true) {

        assert(innerScope ne null)
        assert(outerScope ne null)

        override def indexElementWithScope(uri: String, localname: String, attributes: Attributes, currentScope: XXBLScope) {

            // Index prefixed id ⇒ scope
            val staticId = attributes.getValue("id")

            // NOTE: We can be called on HTML elements within LHHA, which may or may not have an id (they must have one if they have AVTs)
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

    def freeTransientState() {
        // Not needed after analysis
        if (partAnalysis.isTopLevel)
            metadata = null
    }

    // This function is not called as of 2011-06-28 but if/when we support removing scopes, check these notes:
    // - deindex prefixed ids ⇒ Scope
    // - remove models associated with scope
    // - remove control analysis
    // - deindex scope id ⇒ Scope
    def removeScope(scope: Scope) = ???

    /**
     * Return whether the given QName has an associated binding.
     *
     * @param qName QName to check
     * @return      true iif there is a binding
     */
    def isComponent(qName: QName) = abstractBindings.contains(qName)

    /**
     * Return the id of the <xbl:binding> element associated with the given prefixed control id.
     *
     * @param controlPrefixedId     prefixed control id
     * @return binding id or null if not found
     */
    def getBindingId(controlPrefixedId: String) = getBinding(controlPrefixedId) map (_.bindingId) orNull

    // Return true if the given prefixed control id has a binding
    def hasBinding(controlPrefixedId: String) = getBinding(controlPrefixedId).isDefined

    // Return the given binding
    def getBinding(controlPrefixedId: String) = concreteBindings.get(controlPrefixedId)

    // Remove the given binding
    def removeBinding(controlPrefixedId: String): Unit = {
        concreteBindings -= controlPrefixedId
        // NOTE: Can't update abstractBindings, allScripts, allStyles, allGlobals without checking all again, so for now leave that untouched
    }
}

object XBLBindings {

    def readXBLResource(path: String) = {
        // Update last modified so that dependencies on external XBL files can be handled
        val lastModified = ResourceManagerWrapper.instance.lastModified(path, false)

        // Read content
        val sourceXBL = ResourceManagerWrapper.instance.getContentAsDOM4J(path, XMLParsing.ParserConfiguration.XINCLUDE_ONLY, false)

        (Transform.transformXBLDocumentIfNeeded(path, sourceXBL, lastModified).getRootElement, lastModified)
    }

    // path == None in case of inline XBL
    def extractXBLBindings(path: Option[String], xblElement: Element, lastModified: Long) = {

        // Extract xbl:xbl/xbl:script
        // TODO: should do this differently, in order to include only the scripts and resources actually used
        val scriptElements = Dom4j.elements(xblElement, XBL_SCRIPT_QNAME) map HeadElement.apply

        // Find abstract bindings
        val resultingBindings =
            for {
                // Find xbl:binding/@element
                bindingElement ← Dom4jUtils.elements(xblElement, XBL_BINDING_QNAME).asScala
                currentElementAttribute = bindingElement.attributeValue(ELEMENT_QNAME)
                if currentElementAttribute ne null
            } yield
                AbstractBinding.findOrCreate(path, bindingElement, lastModified, scriptElements)

        resultingBindings.toList
    }
}
