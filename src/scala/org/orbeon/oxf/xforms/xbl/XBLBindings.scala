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

import org.orbeon.oxf.xforms._
import analysis.{XFormsExtractorContentHandler, XFormsAnnotatorContentHandler, PartAnalysisImpl, Metadata}
import processor.handlers.xhtml.XHTMLHeadHandler
import org.orbeon.oxf.properties.PropertySet
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.resources.ResourceManagerWrapper
import scala.collection.JavaConverters._

import XBLBindings._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.ScalaUtils._
import XFormsConstants._
import org.orbeon.oxf.xml.dom4j.{LocationDocumentResult, Dom4jUtils, LocationData}
import org.orbeon.oxf.pipeline.api.XMLReceiver
import org.orbeon.oxf.xml.{SAXStore, TransformerUtils, XMLUtils}
import org.orbeon.oxf.common.{OXFException, Version}
import org.xml.sax.Attributes
import collection.mutable.{LinkedHashSet, ArrayBuffer, LinkedHashMap}
import org.orbeon.oxf.util.Logging
import org.dom4j._

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
     *   o requires XFormsAnnotatorContentHandler to provide start/end of XForms element
     *
     * As of 2009-09-14, we use an IdGenerator shared among top-level and all XBL bindings.
     */

    case class Global(fullShadowTree: Document, compactShadowTree: Document)

    val abstractBindings = LinkedHashMap[QName, AbstractBinding]()
    val concreteBindings = LinkedHashMap[String, ConcreteBinding]()

    val allScripts = ArrayBuffer[Element]()
    val allStyles = ArrayBuffer[Element]()
    val allGlobals = LinkedHashMap[QName, Global]()

    // Inline <xbl:xbl> and automatically-included XBL documents
    private val xblDocuments = (inlineXBL map ((_, 0L))) ++
        (metadata.getBindingIncludesJava.asScala map readXBLResource)

    // Process <xbl:xbl>
    if (xblDocuments.nonEmpty) {
        withDebug("generating global XBL shadow content") {

            val bindingCounts = xblDocuments map { case (element, lastModified) ⇒
                val bindingsForDoc = extractXBLBindings(None, element, lastModified, partAnalysis)
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

    private def registerXBLBindings(bindings: Seq[AbstractBinding]) {

        // All bindings are expected to have the same scripts, so just add scripts for the first binding received
        allScripts ++= bindings.headOption.toSeq flatMap (_.scripts)

        // Register each individual binding
        bindings foreach { binding ⇒
            allStyles ++= binding.styles
            abstractBindings += binding.qNameMatch → binding
        }
    }

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
                        val (document, lastModified) = readXBLResource(path)
                        val bindingsForDoc = extractXBLBindings(Some(path), document, lastModified, partAnalysis)
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

    private def readXBLResource(path: String) = {
        // Update last modified so that dependencies on external XBL files can be handled
        val lastModified = ResourceManagerWrapper.instance.lastModified(path, false)
        metadata.updateBindingsLastModified(lastModified)

        // Read content
        val sourceXBL = ResourceManagerWrapper.instance.getContentAsDOM4J(path, XMLUtils.ParserConfiguration.XINCLUDE_ONLY, false)

        (Transform.transformXBLDocumentIfNeeded(path,sourceXBL, lastModified).getRootElement, lastModified)
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
        val (fullShadowTree, compactShadowTree) =
            annotateSubtree(Some(boundElement), rawShadowTree, newInnerScope, outerScope, XXBLScope.inner, newInnerScope, hasFullUpdate(rawShadowTree), ignoreRoot = true, needCompact = true)

        // Annotate event handlers and implementation models
        def annotateByElement(element: Element) =
            annotateSubtreeByElement(boundElement, element, newInnerScope, outerScope, XXBLScope.inner, newInnerScope)

        val annotatedHandlers = abstractBinding.handlers        map annotateByElement
        val annotatedModels   = abstractBinding.implementations map annotateByElement

        // Remember concrete binding information
        val newConcreteBinding =
            ConcreteBinding(
                abstractBinding,
                newInnerScope,
                outerScope,
                annotatedHandlers,
                annotatedModels,
                fullShadowTree,
                compactShadowTree)

        // Process globals here as the component is in use
        processGlobals(abstractBinding, locationData)

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
        annotateSubtree(
            Some(boundElement),
            Dom4jUtils.createDocumentCopyParentNamespaces(element, false),
            innerScope,
            outerScope,
            startScope,
            containerScope,
            hasFullUpdate = false,
            ignoreRoot = false,
            needCompact = false)._1.getRootElement

    /**
     * From a raw tree produce a full annotated tree and, optionally, a compact tree.
     */
    def annotateSubtree(
           boundElement: Option[Element],
           rawTree: Node,
           innerScope: Scope,
           outerScope: Scope,
           startScope: XXBLScope,
           containerScope: Scope,
           hasFullUpdate: Boolean,
           ignoreRoot: Boolean,
           needCompact: Boolean) = {

        withDebug("annotating tree") {
            
            val baseURI = XFormsUtils.resolveXMLBase(boundElement.orNull, null, ".").toString
    
            // Annotate tree
            val fullAnnotatedTree = annotateShadowTree(rawTree, containerScope.fullPrefix, hasFullUpdate = false)
    
            // Create transformer if compact tree is needed
            val (transformer, result) =
                if (needCompact) {
                    val transformer = TransformerUtils.getIdentityTransformerHandler
                    val result = new LocationDocumentResult
                    transformer.setResult(result)
                    (transformer, result)
                } else
                    (null, null)
    
            // Gather scopes, namespace mappings, and if needed produce compact tree
            TransformerUtils.writeDom4j(
                fullAnnotatedTree,
                new ScopeExtractorContentHandler(transformer, innerScope, outerScope, startScope, containerScope.fullPrefix, baseURI, ignoreRoot = false)
            )
    
            // Extractor produces /static-state/root/(xbl:template|xxbl:global), so extract the nested element
            val compactTreeOption = Option(result) map { result ⇒
                Dom4jUtils.createDocumentCopyParentNamespaces(result.getDocument.getRootElement.element("root").element(fullAnnotatedTree.getRootElement.getQName), true)
            }

            if (logShadowTrees)
                debugResults(Seq(
                    "full tree" → Dom4jUtils.domToString(fullAnnotatedTree),
                    "compact tree" → (compactTreeOption map Dom4jUtils.domToString orNull)
                ))
            
            // Result is full annotated tree and, if needed, the compact tree
            (fullAnnotatedTree, compactTreeOption.orNull)
        }
    }

    private def processGlobals(abstractBinding: AbstractBinding, locationData: LocationData) {
        abstractBinding.global match {
            case Some(globalDocument) if ! allGlobals.contains(abstractBinding.qNameMatch) ⇒

                val (globalFullShadowTreeDocument, globalCompactShadowTreeDocument) =
                    withDebug("generating global XBL shadow content", Seq("binding id" → abstractBinding.bindingId.orNull)) {

                        val topLevelScopeForGlobals = partAnalysis.startScope
        
                        // TODO: in script mode, XHTML elements in template should only be kept during page generation
                        annotateSubtree(
                            None,
                            globalDocument,
                            topLevelScopeForGlobals,
                            topLevelScopeForGlobals,
                            XXBLScope.inner,
                            topLevelScopeForGlobals,
                            hasFullUpdate = hasFullUpdate(globalDocument),
                            ignoreRoot = true,
                            needCompact = true
                        )
                    }

                allGlobals += abstractBinding.qNameMatch → Global(globalFullShadowTreeDocument, globalCompactShadowTreeDocument)

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
    def annotateShadowTree(shadowTree: Node, prefix: String, hasFullUpdate: Boolean): Document = {

        // Create transformer
        val identity = TransformerUtils.getIdentityTransformerHandler

        // Set result
        val documentResult = new LocationDocumentResult
        identity.setResult(documentResult)

        // Put SAXStore in the middle if we have full updates
        val output = if (hasFullUpdate) new SAXStore(identity) else identity

        // Write the document through the annotator
        // TODO: this adds xml:base on root element, must fix
        TransformerUtils.writeDom4j(shadowTree, new XFormsAnnotatorContentHandler(output, null, metadata) {
            // Use prefixed id for marks and namespaces in order to avoid clashes between top-level controls and shadow trees
            protected override def rewriteId(id: String) = prefix + id
        })

        // Return annotated document
        documentResult.getDocument
    }

    /**
     *
     * @param xmlReceiver           output of transformation
     * @param innerScope            inner scope
     * @param outerScope            outer scope, i.e. scope of the bound element
     * @param startScope            scope of root element
     * @param prefix                prefix of the ids within the new shadow tree, e.g. "my-stuff$my-foo-bar$"
     * @param baseURI               base URI of new tree
     * @param ignoreRoot            whether root element must just be skipped
     */
    private class ScopeExtractorContentHandler(
        xmlReceiver: XMLReceiver,
        innerScope: Scope,
        outerScope: Scope,
        startScope: XXBLScope,
        prefix: String,
        baseURI: String,
        ignoreRoot: Boolean)
    extends XFormsExtractorContentHandler(xmlReceiver, metadata, null, baseURI, startScope, false, ignoreRoot) {

        assert(innerScope ne null)
        assert(outerScope ne null)

        override def startXFormsOrExtension(uri: String, localname: String, attributes: Attributes, currentScope: XXBLScope) {

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
                    partAnalysis.mapScopeIds(staticId, prefixedId, scope)
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

    lazy val baselineResources = {
    
        val metadata = partAnalysis.metadata

        // Register baseline includes
        XFormsProperties.getResourcesBaseline match {
            case baselineProperty: PropertySet.Property ⇒
                val tokens = LinkedHashSet(StringUtils.split(baselineProperty.value.toString): _*) toIterable

                val (scripts, styles) =
                    (for {
                        token ← tokens
                        qName = Dom4jUtils.extractTextValueQName(baselineProperty.namespaces, token, true)
                    } yield
                        if (metadata.isXBLBinding(qName.getNamespaceURI, qName.getName)) {
                            // Binding is in use by this document
                            val binding = abstractBindings(qName)
                            (binding.scripts, binding.styles)
                        } else {
                            metadata.getAutomaticXBLMappingPath(qName.getNamespaceURI, qName.getName) match {
                                case Some(path) ⇒
                                    BindingCache.get(path, qName, 0) match {
                                        case Some(binding) ⇒
                                            // Binding is in cache
                                            (binding.scripts, binding.styles)
                                        case None ⇒
                                            // Load XBL document
                                            // TODO: Would be nice to read and cache, so that if several forms have the same
                                            // baseline for unused mappings, those are not re-read every time.
                                            val xblElement = readXBLResource(path)._1

                                            // Extract xbl:xbl/xbl:script
                                            val scripts = Dom4jUtils.elements(xblElement, XBL_SCRIPT_QNAME).asScala

                                            // Try to find binding
                                            (Dom4jUtils.elements(xblElement, XBL_BINDING_QNAME).asScala map
                                                (e ⇒ AbstractBinding(e, 0, scripts, partAnalysis.getNamespaceMapping("", e))) find
                                                    (_.qNameMatch == qName)) match {
                                                case Some(binding) ⇒ (binding.scripts, binding.styles)
                                                case None ⇒ (Seq[Element](), Seq[Element]())
                                            }
                                    }
                                case None ⇒ (Seq[Element](), Seq[Element]())
                            }
                        }) unzip

                // Return tuple with two sets
                (LinkedHashSet(XHTMLHeadHandler.xblResourcesToSeq(scripts.flatten): _*),
                    LinkedHashSet(XHTMLHeadHandler.xblResourcesToSeq(styles.flatten): _*))
            case _ ⇒ (collection.Set.empty[String], collection.Set.empty[String])
        }
    }
}

object XBLBindings {

    val XBL_MAPPING_PROPERTY_PREFIX = "oxf.xforms.xbl.mapping."

    // path == None in case of inline XBL
    private def extractXBLBindings(path: Option[String], xblElement: Element, lastModified: Long, partAnalysis: PartAnalysis) = {

        // Extract xbl:xbl/xbl:script
        // TODO: should do this differently, in order to include only the scripts and resources actually used
        val scriptElements = Dom4jUtils.elements(xblElement, XBL_SCRIPT_QNAME).asScala

        // Find abstract bindings
        val resultingBindings =
            for {
                // Find xbl:binding/@element
                bindingElement ← Dom4jUtils.elements(xblElement, XBL_BINDING_QNAME).asScala
                currentElementAttribute = bindingElement.attributeValue(ELEMENT_QNAME)
                if currentElementAttribute ne null
                namespaceMapping = partAnalysis.getNamespaceMapping("", bindingElement)
            } yield
                AbstractBinding.findOrCreate(path, bindingElement, lastModified, scriptElements, namespaceMapping)

        resultingBindings
    }
}
