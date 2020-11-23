/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import cats.syntax.option._
import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, NumberUtils, WhitespaceMatching}
import org.orbeon.oxf.xforms.XFormsProperties.{FunctionLibraryProperty, XblSupportProperty}
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlUtil.TopLevelItemsetQNames
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model._
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.{XBLBindingBuilder, XBLSupport}
import org.orbeon.oxf.xforms.{StaticStateBits, XFormsGlobalProperties, XFormsStaticStateImpl, _}
import org.orbeon.oxf.xml.XMLConstants.XML_LANG_QNAME
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult
import org.orbeon.oxf.xml._
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.xforms.XFormsNames.XFORMS_BIND_QNAME
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{Constants, Namespaces, XXBLScope}
import org.xml.sax.Attributes

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.Try


object PartAnalysisBuilder {

  val XFormsFunctionLibraryClassName = "org.orbeon.oxf.xforms.library.XFormsFunctionLibrary"

  // Create and analyze an entire part, whether a top-level, immutable part or a nested, mutable part.
  def apply[T >: TopLevelPartAnalysis with NestedPartAnalysis](
    staticState         : XFormsStaticState,
    parent              : Option[PartAnalysis],
    startScope          : Scope,
    metadata            : Metadata,
    staticStateDocument : StaticStateDocument)(implicit
    logger              : IndentedLogger
  ): T = {

    // We use the parent's `FunctionLibrary` if present, for compatibility with the initial implementation
    // which worked this way. That's also fairly reasonable. But what we could do at some point later is
    // allow a nested part to override with with an explicit `xxf:function-library`.
    val functionLibrary =
      parent map (_.functionLibrary) getOrElse {

        // Load by reflection as we don't have the function library available at this level during compilation
        val xformsFunctionLibrary =
          loadClassByName[FunctionLibrary](XFormsFunctionLibraryClassName) getOrElse
            (throw new IllegalStateException)

        loadClassFromProperty[FunctionLibrary](staticStateDocument, FunctionLibraryProperty) match {
          case Some(library) =>
            new FunctionLibraryList                         |!>
              (_.addFunctionLibrary(xformsFunctionLibrary)) |!>
              (_.addFunctionLibrary(library))
          case None =>
            xformsFunctionLibrary
        }
      }

    // Same thing here, we get the root's `XBLSupport` if present
    val xblSupport =
      (parent.iterator flatMap (_.ancestorOrSelfIterator)).lastOption() collect {
        case impl: StaticPartAnalysisImpl => impl.xblSupport
      } getOrElse
        loadClassFromProperty[XBLSupport](staticStateDocument, XblSupportProperty)

    // We now know all inline XBL bindings, which we didn't in `XFormsAnnotator`.
    // NOTE: Inline bindings are only extracted at the top level of a part. We could imagine extracting them within
    // all XBL components. They would then have to be properly scoped.
    metadata.extractInlineXBL(staticStateDocument.xblElements, startScope)

    val partAnalysisCtx = StaticPartAnalysisImpl(staticState, parent, startScope, metadata, xblSupport, functionLibrary)
    analyze(partAnalysisCtx, staticStateDocument.rootControl)

    if (XFormsGlobalProperties.getDebugLogXPathAnalysis)
      PartAnalysisDebugSupport.printPartAsXml(partAnalysisCtx)

    partAnalysisCtx
  }

  // Create analyzed static state for the given static state document.
  // Used by `XFormsToXHTML`.
  def createFromStaticStateBits(staticStateBits: StaticStateBits): XFormsStaticStateImpl = {

    val startScope = new Scope(None, "")
    val staticStateDocument = new StaticStateDocument(staticStateBits.staticStateDocument)

    new XFormsStaticStateImpl(
      staticStateDocument.asBase64,
      staticStateBits.staticStateDigest,
      startScope,
      staticStateBits.metadata,
      staticStateDocument.template map (_ => staticStateBits.template),    // only keep the template around if needed
      staticStateDocument
    )
  }

  // Create analyzed static state for the given XForms document.
  // Used by tests.
  def createFromDocument(formDocument: Document): XFormsStaticState = {

    val startScope = new Scope(None, "")

    def create(staticStateXML: Document, digest: String, metadata: Metadata, template: AnnotatedTemplate): XFormsStaticStateImpl = {

      val staticStateDocument = new StaticStateDocument(staticStateXML)

      new XFormsStaticStateImpl(
        staticStateDocument.asBase64,
        digest,
        startScope,
        metadata,
        staticStateDocument.template map (_ => template),    // only keep the template around if needed
        staticStateDocument
      )
    }

    createFromDocument(formDocument, startScope, create)._2
  }

  // Create template and analyzed part for the given XForms document.
  // Used by `xxf:dynamic`.
  def createPart(
    staticState  : XFormsStaticState,
    parent       : PartAnalysis,
    formDocument : Document,
    startScope   : Scope)(implicit
    logger       : IndentedLogger
  ): (SAXStore, NestedPartAnalysis) =
    createFromDocument(formDocument, startScope, (staticStateDocument: Document, _: String, metadata: Metadata, _) => {
      PartAnalysisBuilder(staticState, Some(parent), startScope, metadata, new StaticStateDocument(staticStateDocument))
    })

  // Extractor with prefix
  private class Extractor(
    extractorReceiver : XMLReceiver,
    metadata          : Metadata,
    startScope        : Scope,
    template          : SAXStore,
    prefix            : String
  ) extends XFormsExtractor(
    xmlReceiverOpt               = Some(extractorReceiver),
    metadata                     = metadata,
    templateUnderConstructionOpt = Some(AnnotatedTemplate(template)),
    baseURI                      = ".",
    startScope                   = XXBLScope.Inner,
    isTopLevel                   = startScope.isTopLevelScope,
    outputSingleTemplate         = false
  ) {

    override def getPrefixedId(staticId: String) = prefix + staticId

    override def indexElementWithScope(uri: String, localname: String, attributes: Attributes, scope: XXBLScope): Unit = {
      val staticId = attributes.getValue("id")
      if (staticId ne null) {
        val prefixedId = prefix + staticId
        if (metadata.getNamespaceMapping(prefixedId).isDefined) {
          if (startScope.contains(staticId))
            throw new OXFException("Duplicate id found for static id: " + staticId)
          startScope += staticId -> prefixedId

          if (uri == Namespaces.XXF && localname == "attribute") {
            val forStaticId = attributes.getValue("for")
            val forPrefixedId = prefix + forStaticId
            startScope += forStaticId -> forPrefixedId
          }
        }
      }
    }
  }

  // Annotator with prefix
  private class Annotator(
    extractorReceiver : XMLReceiver,
    metadata          : Metadata,
    startScope        : Scope,
    template          : XMLReceiver,
    prefix            : String
  ) extends XFormsAnnotator(
    template,
    extractorReceiver,
    metadata,
    startScope.isTopLevelScope
  ) {
    protected override def rewriteId(id: String) = prefix + id
  }

  // Used by `xxf:dynamic` and tests.
  private def createFromDocument[T](
    formDocument : Document,
    startScope   : Scope,
    create       : (Document, String, Metadata, AnnotatedTemplate) => T
  ): (SAXStore, T) = {
    val identity = TransformerUtils.getIdentityTransformerHandler

    val documentResult = new LocationDocumentResult
    identity.setResult(documentResult)

    val metadata             = Metadata(isTopLevelPart = startScope.isTopLevelScope)
    val digestContentHandler = new DigestContentHandler
    val template             = new SAXStore
    val prefix               = startScope.fullPrefix

    // Read the input through the annotator and gather namespace mappings
    TransformerUtils.writeDom4j(
      formDocument,
      new WhitespaceXMLReceiver(
        new Annotator(
          new Extractor(
            new WhitespaceXMLReceiver(
              new TeeXMLReceiver(identity, digestContentHandler),
              WhitespaceMatching.defaultBasePolicy,
              WhitespaceMatching.basePolicyMatcher
            ),
            metadata,
            startScope,
            template,
            prefix
          ),
          metadata,
          startScope,
          template,
          prefix
        ),
        WhitespaceMatching.defaultHTMLPolicy,
        WhitespaceMatching.htmlPolicyMatcher
      )
    )

    // Get static state document and create static state object
    val staticStateXML = documentResult.getDocument
    val digest = NumberUtils.toHexString(digestContentHandler.getResult)

    (template, create(staticStateXML, digest, metadata, AnnotatedTemplate(template)))
  }

  private def loadClassFromProperty[T <: AnyRef : ClassTag](staticStateDocument: StaticStateDocument, propertyName: String): Option[T] =
    staticStateDocument
      .nonDefaultProperties
      .get(propertyName) map
      (_._1)             flatMap
      (_.trimAllToOpt)   flatMap
      loadClassByName[T]

  private def loadClassByName[T <: AnyRef : ClassTag](className: String): Option[T] = {

      def tryFromScalaObject: Try[AnyRef] = Try {
        Class.forName(className + "$").getDeclaredField("MODULE$").get(null)
      }

      def fromJavaClass: AnyRef =
        Class.forName(className).getDeclaredMethod("instance").invoke(null)

      tryFromScalaObject getOrElse fromJavaClass match {
        case instance: T => Some(instance)
        case _ =>
          throw new ClassCastException(
            s"class `$className` does not refer to a ${implicitly[ClassTag[T]].runtimeClass.getName}"
          )
      }
    }

  // Analyze a subtree of controls (for `xxf:dynamic` and components with lazy bindings)
  def analyzeSubtree(partAnalysisCtx: NestedPartAnalysis, container: ComponentControl)(implicit logger: IndentedLogger): Unit = {

    withDebug("performing static analysis of subtree", Seq("prefixed id" -> container.prefixedId)) {

      // Global lists of external LHHA and handlers
      val lhhas         = mutable.Buffer[LHHAAnalysis]()
      val eventHandlers = mutable.Buffer[EventHandler]()
      val models        = mutable.Buffer[Model]()
      val attributes    = mutable.Buffer[AttributeControl]()

      ElementAnalysisTreeBuilder.buildAllElemDescendants(
        partAnalysisCtx,
        container,
        buildOne(partAnalysisCtx, _, _, _, _, indexNewControl(partAnalysisCtx, _, lhhas, eventHandlers, models, attributes)),
        ElementAnalysisTreeBuilder.componentChildrenForBindingUpdate(partAnalysisCtx, container).some // explicit children
      )

      for (model <- models)
        partAnalysisCtx.indexModel(model)

      for (lhha <- lhhas)
        LHHAAnalysisBuilder.attachToControl(partAnalysisCtx, lhha)

      partAnalysisCtx.registerEventHandlers(eventHandlers)
      partAnalysisCtx.analyzeCustomControls(attributes)

      ElementAnalysisTreeBuilder.setModelAndLangOnAllDescendants(partAnalysisCtx, container)

      // NOTE: doesn't handle globals, models nested within UI, update to resources
      // NOTE: No XPath analysis in nested parts.
    }
  }

  def rebuildBindTree(
    partAnalysisCtx : NestedPartAnalysis,
    model           : Model,
    rawModelElement : Element)(implicit
    logger          : IndentedLogger
  ): Unit =
    withDebug("performing static analysis of bind tree", Seq("prefixed id" -> model.prefixedId)) {

      // Deindex the `StaticBind` tree
      model.topLevelBinds foreach (partAnalysisCtx.deindexTree(_, self = true))
      model.bindsById.values foreach partAnalysisCtx.unmapScopeIds
      model.resetBinds()

      val elemsWithScope = {

        def annotateSubTree(rawElement: Element): Document =
          XBLBindingBuilder.annotateSubtree(
            partAnalysisCtx,
            None,
            rawElement.createDocumentCopyParentNamespaces(detach = false),
            model.scope,
            model.scope,
            XXBLScope.Inner,
            model.containerScope,
            hasFullUpdate = false,
            ignoreRoot = false
          )

        rawModelElement.elements(XFORMS_BIND_QNAME) map
          (annotateSubTree(_).getRootElement)       map
          (_ -> model.scope)
      }

      // Here we know there are no new models, event handlers, etc.
      ElementAnalysisTreeBuilder.buildAllElemDescendants(
        partAnalysisCtx,
        model,
        buildOne(partAnalysisCtx, _, _, _, _, indexNewControlOnly(partAnalysisCtx, _)),
        elemsWithScope.some // explicit children
      )

      model.topLevelBinds foreach
        (ElementAnalysisTreeBuilder.setModelAndLangOnAllDescendants(partAnalysisCtx, _))
    }

  // Analyze the entire tree of controls
  private def analyze(
    partAnalysisCtx : PartAnalysisContextAfterTree,
    rootElem        : Element
  )(implicit logger: IndentedLogger): Unit =
    withDebug("performing static analysis") {

      partAnalysisCtx.initializeScopes()

      // Global lists LHHA and handlers
      val lhhas         = mutable.Buffer[LHHAAnalysis]()
      val eventHandlers = mutable.Buffer[EventHandler]()
      val models        = mutable.Buffer[Model]()
      val attributes    = mutable.Buffer[AttributeControl]()

      // Create and index root control
      val rootControlPrefixedId = partAnalysisCtx.startScope.fullPrefix + Constants.DocumentId

      val elementInParentPartOpt =
        partAnalysisCtx.parent map
          (_.getControlAnalysis(partAnalysisCtx.startScope.fullPrefix.init)) // `.init` removes the trailing component separator

      val rootControlAnalysis =
        new RootControl(
          index            = 0,
          element          = rootElem,
          staticId         = Constants.DocumentId,
          prefixedId       = rootControlPrefixedId,
          namespaceMapping = partAnalysisCtx.getNamespaceMapping(rootControlPrefixedId).orNull,
          scope            = partAnalysisCtx.startScope,
          containerScope   = partAnalysisCtx.startScope,
          elementInParent  = elementInParentPartOpt
        )

      indexNewControl(partAnalysisCtx, rootControlAnalysis, lhhas, eventHandlers, models, attributes)

      // Build the entire tree
      val buildOneGatherStuff: ElementAnalysisTreeBuilder.Builder =
        buildOne(partAnalysisCtx, _, _, _, _, indexNewControl(partAnalysisCtx, _, lhhas, eventHandlers, models, attributes))

      ElementAnalysisTreeBuilder.buildAllElemDescendants(partAnalysisCtx, rootControlAnalysis, buildOneGatherStuff)

      // Issues with `xxbl:global`
      //
      // 1. It's unclear what should happen with nested parts if they have globals. Without the condition below,
      //    globals can be duplicated, once per part. This can cause issues in Form Builder for example, where a
      //    global can assume visibility on top-level Form Runner resources. As of 2013-11-14, only outputting
      //    globals at the top-level.
      // 2. Global controls are placed in the part's start scope. Is there an alternative?
      // 3. Should we allow for recursive globals?
      // 4. The code below doesn't set the `preceding` value. The main impact is no resolution of variables.
      //    It might be desirable not to scope them anyway.
      if (partAnalysisCtx.isTopLevelPart) {
        val globals =
          for {
            global        <- partAnalysisCtx.iterateGlobals
            globalElement <- global.compactShadowTree.getRootElement.elements // children of xxbl:global
          } yield
            buildOneGatherStuff(rootControlAnalysis, None, globalElement, partAnalysisCtx.startScope) match {
              case newParent: WithChildrenTrait =>
                ElementAnalysisTreeBuilder.buildAllElemDescendants(partAnalysisCtx, newParent, buildOneGatherStuff)
                newParent
              case other =>
                other
            }

        // Add globals to the root analysis
        rootControlAnalysis.addChildren(globals)
      } else if (partAnalysisCtx.iterateGlobals.nonEmpty)
        warn(s"There are ${partAnalysisCtx.iterateGlobals.size} `xxbl:global` in a child part. Those won't be processed.")

      for (model <- models)
        partAnalysisCtx.indexModel(model)

      for (lhha <- lhhas)
        LHHAAnalysisBuilder.attachToControl(partAnalysisCtx, lhha)

      partAnalysisCtx.registerEventHandlers(eventHandlers)
      partAnalysisCtx.analyzeCustomControls(attributes)

      // Language on the root element is handled differently, but it must be done after the tree has been built
      // as we need access to attribute controls
      rootControlAnalysis.lang = {

        // Assign a top-level lang based on the first `xml:lang` found on a top-level control. This allows
        // `xxbl:global` controls to inherit that `xml:lang`. All other top-level controls already have an `xml:lang`
        // placed by `XFormsExtractor`.
        def fromChildElements =
          rootControlAnalysis.element.elements collectFirst {
            case e if e.hasAttribute(XML_LANG_QNAME) =>
              ElementAnalysisTreeBuilder.extractXMLLang(partAnalysisCtx, rootControlAnalysis, e.attributeValue(XML_LANG_QNAME))
          }

        // Ask the parent part
        def fromParentPart =
          elementInParentPartOpt map (_.getLangUpdateIfUndefined)

        fromChildElements orElse fromParentPart getOrElse LangRef.None
      }

      ElementAnalysisTreeBuilder.setModelOnElement(partAnalysisCtx, rootControlAnalysis)
      ElementAnalysisTreeBuilder.setModelAndLangOnAllDescendants(partAnalysisCtx, rootControlAnalysis)

      // NOTE: For now, we don't analyze the XPath of nested (dynamic) parts
      if (partAnalysisCtx.isTopLevelPart && partAnalysisCtx.isXPathAnalysis) {
        ElementAnalysisTreeXPathAnalyzer.analyzeXPath(partAnalysisCtx, rootControlAnalysis) // first as nested models might ask for its context
        partAnalysisCtx.iterateModels ++ partAnalysisCtx.iterateControlsNoModels foreach
          (ElementAnalysisTreeXPathAnalyzer.analyzeXPath(partAnalysisCtx, _))
      }

      debugResults(Seq("controls" -> partAnalysisCtx.controlAnalysisMap.size.toString))

      // Clean-up to finish initialization
      partAnalysisCtx.freeTransientState()
    }

  // NOTE: Index controls directly, as we use this to know the index. We could do this
  // differently and keep a separate counter.
  private def indexNewControl(
    partAnalysisCtx : PartAnalysisContextForTree,
    elementAnalysis : ElementAnalysis,
    lhhas           : mutable.Buffer[LHHAAnalysis],
    eventHandlers   : mutable.Buffer[EventHandler],
    models          : mutable.Buffer[Model],
    attributes      : mutable.Buffer[AttributeControl]
  ): Unit = {

    indexNewControlOnly(partAnalysisCtx, elementAnalysis)

    // Index special controls
    elementAnalysis match {
      case e: LHHAAnalysis if ! TopLevelItemsetQNames(e.getParent.element.getQName) => lhhas += e
      case e: EventHandler                                                          => eventHandlers += e
      case e: Model                                                                 => models        += e
      case e: AttributeControl                                                      => attributes    += e
      case _                                                                        =>
    }
  }

  private def indexNewControlOnly(
    partAnalysisCtx : PartAnalysisContextForTree,
    elementAnalysis : ElementAnalysis
  ): Unit = {

    // Index by prefixed id
    partAnalysisCtx.controlAnalysisMap += elementAnalysis.prefixedId -> elementAnalysis

    // Index by type
    val controlName = elementAnalysis.localName
    val controlsMap = partAnalysisCtx.controlTypes.getOrElseUpdate(controlName, mutable.LinkedHashMap[String, ElementAnalysis]())
    controlsMap += elementAnalysis.prefixedId -> elementAnalysis
  }

  // Builder that produces an `ElementAnalysis` for a known incoming Element
  private def buildOne(
    partAnalysisCtx : PartAnalysisContextForTree,
    parent          : ElementAnalysis,
    preceding       : Option[ElementAnalysis],
    controlElement  : Element,
    containerScope  : Scope,
    index           : ElementAnalysis => Unit
  ): ElementAnalysis = {

    assert(containerScope ne null)

    val locationData = ElementAnalysis.createLocationData(controlElement)

    // Check for mandatory id
    val controlStaticId = controlElement.idOpt getOrElse
      (throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName, locationData))

    val controlPrefixedId = containerScope.fullPrefix + controlStaticId

    // Create new control if possible
    val elementAnalysisOpt =
      ControlAnalysisFactory.create(
        partAnalysisCtx   = partAnalysisCtx,
        index             = partAnalysisCtx.controlAnalysisMap.size + 1,
        controlElement    = controlElement,
        parent            = parent.some,
        preceding         = preceding,
        controlStaticId   = controlStaticId,
        controlPrefixedId = controlPrefixedId,
        namespaceMapping  = partAnalysisCtx.getNamespaceMapping(controlPrefixedId).orNull,
        scope             = partAnalysisCtx.scopeForPrefixedId(controlPrefixedId),
        containerScope    = containerScope
      )

    elementAnalysisOpt match {
      case Some(componentControl: ComponentControl) =>

        val abstractBinding =
          partAnalysisCtx.metadata.findAbstractBindingByPrefixedId(componentControl.prefixedId) getOrElse
            (throw new IllegalStateException)

        componentControl.commonBinding = abstractBinding.commonBinding

        index(componentControl)
        componentControl
      case Some(elementAnalysis) =>
        index(elementAnalysis)
        elementAnalysis
      case None =>
        throw new ValidationException(s"Unknown control: `${controlElement.getQualifiedName}`", locationData)
    }
  }
}