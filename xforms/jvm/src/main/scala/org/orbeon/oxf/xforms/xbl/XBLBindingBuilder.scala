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

import org.orbeon.dom._
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{IndentedLogger, WhitespaceMatching}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.XXBLScope
import org.orbeon.xforms.xbl.Scope
import org.xml.sax.Attributes

import scala.collection.JavaConverters._


/**
 * Gather static information about XBL bindings.
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
 *
 * Notes about id generation
 *
 * Two approaches:
 *
 * - use shared `IdGenerator`
 *   - simpler
 *   - drawback: automatic ids grow larger
 *   - works for id allocation, but not for checking duplicate ids, but we do duplicate id check separately for XBL
 *     anyway in `ScopeExtractor`
 * - use separate outer/inner scope `IdGenerator`
 *   - more complex
 *   - requires to know inner/outer scope at annotation time
 *   - requires `XFormsAnnotator` to provide start/end of XForms element
 *
 * As of 2009-09-14, we use an `IdGenerator` shared among top-level and all XBL bindings.
 */
object XBLBindingBuilder {

  import Private._

  // Can return `None` if the `AbstractBinding` does not have a template.
  //
  // As a side-effect, this updates `partAnalysis`. We don't like these side-effects. How can we do better?
  //
  // - `newScope`
  // - `mapScopeIds`
  // - updates to `Metadata`
  //
  //  Indexing of the result, OTOH, is done by the caller.
  //
  def createConcreteBindingFromElem(
    partAnalysis      : PartAnalysisImpl,
    abstractBinding   : AbstractBinding,
    controlElement    : Element,
    controlPrefixedId : String,
    containerScope    : Scope)(implicit
    indentedLogger    : IndentedLogger
  ): Option[(ConcreteBinding, Option[Global])] =
    for (rawShadowTree <- generateRawShadowTree(partAnalysis, controlElement, abstractBinding)(indentedLogger))
      yield
        createScopeAndConcreteBinding(
          partAnalysis,
          controlElement,
          controlPrefixedId,
          containerScope,
          abstractBinding,
          rawShadowTree
        )

  // From a raw non-control tree (handlers, models) rooted at an element, produce a full annotated tree.
  def annotateSubtreeByElement(
    partAnalysis   : PartAnalysisImpl, // for `Metadata` and `mapScopeIds`
    boundElement   : Element,
    element        : Element,
    innerScope     : Scope,
    outerScope     : Scope,
    startScope     : XXBLScope,
    containerScope : Scope
  ): Element =
    annotateSubtree(
      partAnalysis,
      Some(boundElement),
      element.createDocumentCopyParentNamespaces(detach = false),
      innerScope,
      outerScope,
      startScope,
      containerScope,
      hasFullUpdate = false,
      ignoreRoot = false
    ).getRootElement

  // Annotate a tree
  def annotateSubtree(
    partAnalysis   : PartAnalysisImpl, // for `Metadata` and `mapScopeIds`
    boundElement   : Option[Element],  // for `xml:base` resolution
    rawTree        : Document,
    innerScope     : Scope,
    outerScope     : Scope,
    startScope     : XXBLScope,
    containerScope : Scope,
    hasFullUpdate  : Boolean,
    ignoreRoot     : Boolean
  ): Document =
    withDebug("annotating tree") {

      val baseURI = boundElement map (_.resolveXMLBase(None, ".").toString) getOrElse "."
      val fullAnnotatedTree = annotateShadowTree(partAnalysis.metadata, rawTree, containerScope.fullPrefix)

      TransformerUtils.writeDom4j(
        fullAnnotatedTree,
        new ScopeExtractor(partAnalysis, None, innerScope, outerScope, startScope, containerScope.fullPrefix, baseURI)
      )

      fullAnnotatedTree
    }

  // Keep public for unit tests
  def annotateShadowTree(
    metadata   : Metadata,
    shadowTree : Document,
    prefix     : String
  ): Document = {

    val identity = TransformerUtils.getIdentityTransformerHandler

    val documentResult = new LocationDocumentResult
    identity.setResult(documentResult)

    // FIXME: This adds `xml:base` on root element.
    TransformerUtils.writeDom4j(shadowTree, new XFormsAnnotator(identity, null, metadata, false) {
      // Use prefixed id for marks and namespaces to avoid clashes between top-level controls and shadow trees
      protected override def rewriteId(id: String): String = prefix + id
    })

    documentResult.getDocument
  }

  private object Private {

    val logShadowTrees = XFormsProperties.getDebugLogging.contains("analysis-xbl-tree")

    // Generate raw (non-annotated) shadow content for the given control id and XBL binding.
    def generateRawShadowTree(
      partAnalysis    : PartAnalysisImpl,
      boundElement    : Element,
      abstractBinding : AbstractBinding)(
      indentedLogger  : IndentedLogger
    ): Option[Document] =
      abstractBinding.templateElementOpt map {
        templateElement =>
          withDebug("generating raw XBL shadow content", Seq("binding id" -> abstractBinding.bindingId.orNull)) {

            // TODO: in script mode, XHTML elements in template should only be kept during page generation

            // Here we create a completely separate document

            // 1. Apply optional preprocessing step (usually XSLT)
            // If @xxbl:transform is not present, just use a copy of the template element itself
            val shadowTreeDocument =
              abstractBinding.newTransform(boundElement) getOrElse
                templateElement.createDocumentCopyParentNamespaces(detach = false)

            // 2. Apply xbl:attr, xbl:content, xxbl:attr and index xxbl:scope
            XBLTransformer.transform(
              partAnalysis          = partAnalysis, // `XBLSupport` implementations use this
              xblSupport            = partAnalysis.staticState.xblSupport,
              shadowTreeDocument    = shadowTreeDocument,
              boundElement          = boundElement,
              abstractBindingOpt    = Some(abstractBinding),
              excludeNestedHandlers = abstractBinding.modeHandlers,
              excludeNestedLHHA     = abstractBinding.modeLHHA,
              supportAVTs           = abstractBinding.supportAVTs
            )
          }(indentedLogger)
      }

    def createScopeAndConcreteBinding(
      partAnalysis           : PartAnalysisImpl,
      boundElement           : Element,
      boundControlPrefixedId : String,
      containerScope         : Scope,
      abstractBinding        : AbstractBinding,
      rawShadowTree          : Document
    ): (ConcreteBinding, Option[Global]) = {

      // New prefix corresponds to bound element prefixed id
      //val newPrefix = boundControlPrefixedId + COMPONENT_SEPARATOR

      val newInnerScope = partAnalysis.newScope(containerScope, boundControlPrefixedId)
      // NOTE: Outer scope is not necessarily the container scope!
      val outerScope = partAnalysis.scopeForPrefixedId(boundControlPrefixedId)

      // Annotate control tree
      val (templateTree, compactShadowTree) =
        annotateAndExtractSubtree(
          partAnalysis,
          Some(boundElement),
          rawShadowTree,
          newInnerScope,
          outerScope,
          XXBLScope.Inner,
          newInnerScope,
          hasFullUpdate(rawShadowTree),
          ignoreRoot = true
        )

      // Annotate event handlers and implementation models
      def annotateByElement(element: Element) =
        annotateSubtreeByElement(
          partAnalysis,
          boundElement,
          element,
          newInnerScope,
          outerScope,
          XXBLScope.Inner,
          newInnerScope
        )

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
          compactShadowTree,
          boundElement.jAttributes.asScala map { att => att.getQName -> att.getValue } toMap
        )

      // See also "Issues with `xxbl:global`" in PartAnalysisImpl
      val globalsAlreadyProcessed = partAnalysis.abstractBindingsWithGlobals.exists(abstractBinding eq)
      val globalOpt =
        partAnalysis.isTopLevel && ! globalsAlreadyProcessed flatOption
          processGlobalsIfPresent(partAnalysis, abstractBinding)

      // Extract xbl:xbl/xbl:script and xbl:binding/xbl:resources/xbl:style
      // TODO: should do this here, in order to include only the scripts and resources actually used

      (newConcreteBinding, globalOpt)
    }

    // Annotate a subtree and return a template and compact tree
    def annotateAndExtractSubtree(
      partAnalysis   : PartAnalysisImpl,
      boundElement   : Option[Element], // for xml:base resolution
      rawTree        : Document,
      innerScope     : Scope,
      outerScope     : Scope,
      startScope     : XXBLScope,
      containerScope : Scope,
      hasFullUpdate  : Boolean,
      ignoreRoot     : Boolean
    ): (SAXStore, Document) = withDebug("annotating and extracting tree") {

      val baseURI = boundElement map (_.resolveXMLBase(None, ".").toString) getOrElse "."

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
                partAnalysis,
                Some(
                  new WhitespaceXMLReceiver(
                    extractorOutput,
                    WhitespaceMatching.defaultBasePolicy,
                    WhitespaceMatching.basePolicyMatcher
                  )
                ),
                innerScope,
                outerScope,
                startScope,
                containerScope.fullPrefix,
                baseURI
              ),
              partAnalysis.metadata,
              false
            ) {
              // Use prefixed id for marks and namespaces in order to avoid clashes between top-level
              // controls and shadow trees
              protected override def rewriteId(id: String): String = containerScope.fullPrefix + id
            },
            WhitespaceMatching.defaultHTMLPolicy,
            WhitespaceMatching.htmlPolicyMatcher
          )
        )

        (templateOutput, extractorDocument.getDocument)
      }

      if (logShadowTrees)
        debugResults(Seq(
          "full tree"    -> TransformerUtils.saxStoreToDom4jDocument(templateTree).getRootElement.serializeToString(),
          "compact tree" -> compactTree.getRootElement.serializeToString()
        ))

      // Result is full annotated tree and, if needed, the compact tree
      (templateTree, compactTree)
    }

    def processGlobalsIfPresent(
      partAnalysis    : PartAnalysisImpl,
      abstractBinding : AbstractBinding
    ): Option[Global] =
      abstractBinding.global map { globalDocument =>

        val (globalTemplateTree, globalCompactShadowTree) =
          withDebug("generating global XBL shadow content", Seq("binding id" -> abstractBinding.bindingId.orNull)) {

            val topLevelScopeForGlobals = partAnalysis.startScope

            annotateAndExtractSubtree(
              partAnalysis   = partAnalysis,
              boundElement   = None,
              rawTree        = globalDocument,
              innerScope     = topLevelScopeForGlobals,
              outerScope     = topLevelScopeForGlobals,
              startScope     = XXBLScope.Inner,
              containerScope = topLevelScopeForGlobals,
              hasFullUpdate  = hasFullUpdate(globalDocument),
              ignoreRoot     = true
            )
          }

        Global(globalTemplateTree, globalCompactShadowTree)
      }

    def hasFullUpdate(shadowTreeDocument: Document) =
      if (Version.isPE) {
        var hasUpdateFull = false

        shadowTreeDocument.getRootElement.visitDescendants(
          new VisitorListener {
            def startElement(element: Element): Unit = {
              val xxformsUpdate = element.attributeValue(XXFORMS_UPDATE_QNAME)
              if (XFORMS_FULL_UPDATE == xxformsUpdate)
                hasUpdateFull = true
            }

            def endElement(element: Element) = ()
            def text(text: Text) = ()
          },
          mutable = true
        )

        hasUpdateFull
      } else
        false

    class ScopeExtractor(
      partAnalysis : PartAnalysisImpl,    // for `Metadata` and `mapScopeIds`
      xmlReceiver  : Option[XMLReceiver], // output of transformation or `None`
      innerScope   : Scope,               // inner scope
      outerScope   : Scope,               // outer scope, i.e. scope of the bound
      startScope   : XXBLScope,           // scope of root element
      prefix       : String,              // prefix of the ids within the new shadow tree
      baseURI      : String               // base URI of new tree
    ) extends XFormsExtractor(
      xmlReceiverOpt               = xmlReceiver,
      metadata                     = partAnalysis.metadata,
      templateUnderConstructionOpt = None,
      baseURI                      = baseURI,
      startScope                   = startScope,
      isTopLevel                   = false,
      outputSingleTemplate         = true
    ) {

      require(innerScope ne null)
      require(outerScope ne null)

      override def getPrefixedId(staticId: String): String = prefix + staticId

      override def indexElementWithScope(
        uri          : String,
        localname    : String,
        attributes   : Attributes,
        currentScope : XXBLScope
      ): Unit = {

        // Index prefixed id => scope
        val staticId = attributes.getValue("id")

        // NOTE: We can be called on HTML elements within LHHA, which may or may not have an id (they must have one
        // if they have AVTs).
        if (staticId ne null) {
          val prefixedId = prefix + staticId
          if (metadata.getNamespaceMapping(prefixedId).isDefined) {
            val scope = if (currentScope == XXBLScope.Inner) innerScope else outerScope

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
  }
}