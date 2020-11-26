package org.orbeon.oxf.xforms

import java.{util => ju}

import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.{Decoder, HCursor}
import org.orbeon.datatypes.MaximumSize
import org.orbeon.dom
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlUtil.TopLevelItemsetQNames
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Instance, InstanceMetadata, Model}
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.XBLAssets
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.expr.{Expression, StaticContext}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.trans.SymbolicName
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.collection.mutable


object XFormsStaticStateDeserializer {

  def deserialize(jsonString: String)(implicit logger: IndentedLogger): XFormsStaticState = {

    val scopes = mutable.Map[String, Scope]()

    var controlStack: List[ElementAnalysis] = Nil

    object Index {

      val controlAnalysisMap = mutable.LinkedHashMap[String, ElementAnalysis]()
      val controlTypes       = mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]]()
      val lhhas              = mutable.Buffer[LHHAAnalysis]()
      val eventHandlers      = mutable.Buffer[EventHandler]()
      val models             = mutable.Buffer[Model]()
      val attributes         = mutable.Buffer[AttributeControl]()

      def indexNewControl(elementAnalysis : ElementAnalysis): Unit = {

        // Index by prefixed id
        controlAnalysisMap += elementAnalysis.prefixedId -> elementAnalysis

        // Index by type
        val controlName = elementAnalysis.localName
        val controlsMap = controlTypes.getOrElseUpdate(controlName, mutable.LinkedHashMap[String, ElementAnalysis]())
        controlsMap += elementAnalysis.prefixedId -> elementAnalysis

        // Index special controls
        elementAnalysis match {
          case e: LHHAAnalysis if ! TopLevelItemsetQNames(e.getParent.element.getQName) => lhhas += e
          case e: EventHandler                                                          => eventHandlers += e
          case e: Model                                                                 => models        += e
          case e: AttributeControl                                                      => attributes    += e
          case _                                                                        =>
        }
      }
    }

    implicit val decodeSAXStore: Decoder[SAXStore] = (c: HCursor) => {

      for {
        eventBufferPosition          <- c.get[Int]("eventBufferPosition")
        eventBuffer                  <- c.get[Array[Byte]]("eventBuffer")
        charBufferPosition           <- c.get[Int]("charBufferPosition")
        charBuffer                   <- c.get[Array[Char]]("charBuffer")
        intBufferPosition            <- c.get[Int]("intBufferPosition")
        intBuffer                    <- c.get[Array[Int]]("intBuffer")
        lineBufferPosition           <- c.get[Int]("lineBufferPosition")
        lineBuffer                   <- c.get[Array[Int]]("lineBuffer")
        systemIdBufferPosition       <- c.get[Int]("systemIdBufferPosition")
        systemIdBuffer               <- c.get[Array[String]]("systemIdBuffer")
        attributeCountBufferPosition <- c.get[Int]("attributeCountBufferPosition")
        attributeCountBuffer         <- c.get[Array[Int]]("attributeCountBuffer")
        attributeCount               <- c.get[Int]("attributeCount")
        stringBuilder                <- c.get[Array[String]]("stringBuilder")
        hasDocumentLocator           <- c.get[Boolean]("hasDocumentLocator")
//        marks                        <- c.get[Array[]]("hasDocumentLocator")
        //    "systemIdBufferPosition" -> Json.fromInt(a.systemIdBufferPosition),
      //    "systemIdBuffer"         -> a.systemIdBuffer.slice(0, a.systemIdBufferPosition).asJson,

      } yield {
        val a = new SAXStore
        a.eventBufferPosition          = eventBufferPosition
        a.eventBuffer                  = eventBuffer
        a.charBufferPosition           = charBufferPosition
        a.charBuffer                   = charBuffer
        a.intBufferPosition            = intBufferPosition
        a.intBuffer                    = intBuffer
        a.lineBufferPosition           = lineBufferPosition
        a.lineBuffer                   = lineBuffer
        a.systemIdBufferPosition       = systemIdBufferPosition
        a.systemIdBuffer               = systemIdBuffer
        a.attributeCountBufferPosition = attributeCountBufferPosition
        a.attributeCountBuffer         = attributeCountBuffer
        a.attributeCount               = attributeCount
        a.stringBuilder                = new ju.ArrayList[String](ju.Arrays.asList(stringBuilder: _*))
        a.hasDocumentLocator           = hasDocumentLocator

        a
      }
    }





//    implicit val encodeSAXStoreMark: Encoder[a.Mark] = (m: a.Mark) => Json.obj(
//      "_id"                          -> Json.fromString(m._id),
//      "eventBufferPosition"          -> Json.fromInt(m.eventBufferPosition),
//      "charBufferPosition"           -> Json.fromInt(m.charBufferPosition),
//      "intBufferPosition"            -> Json.fromInt(m.intBufferPosition),
//      "lineBufferPosition"           -> Json.fromInt(m.lineBufferPosition),
//      "systemIdBufferPosition"       -> Json.fromInt(m.systemIdBufferPosition),
//      "attributeCountBufferPosition" -> Json.fromInt(m.attributeCountBufferPosition),
//      "stringBuilderPosition"        -> Json.fromInt(m.stringBuilderPosition)
//    )
//
//    Json.obj(
//
//
//      //    write(out, if (a.publicId == null) "" else a.publicId)
//      "marks" -> Option(a.marks).map(_.asScala).getOrElse(Nil).asJson
//    )
//  }
//
//    }

    implicit val decodeScope: Decoder[Scope] = (c: HCursor) =>
      for {
//        parent       <- c.get[Scope]("parent") // TODO
        scopeId <- c.get[String]("scopeId")
        idMap   <- c.get[Map[String, String]]("idMap")
      } yield
        scopes.getOrElseUpdate(scopeId, {
          val r = new Scope(None, scopeId)
          idMap foreach (kv => r += kv)
          r
        })

  //  implicit val encodeAttributeControl: Decoder[AttributeControl] = (a: AttributeControl) => Json.obj(
  //    "foo" -> Json.fromString("bar")
  //  )

    implicit val decodeAnnotatedTemplate : Decoder[AnnotatedTemplate] = deriveDecoder
  //  implicit val decodeLangRef           : Decoder[LangRef]           = deriveDecoder
    implicit val decodeNamespace         : Decoder[dom.Namespace]     = deriveDecoder
    implicit val decodeBasicCredentials  : Decoder[BasicCredentials]  = deriveDecoder

  //  implicit val decodeNamespaceMapping: Decoder[NamespaceMapping] = (a: NamespaceMapping) => Json.obj(
  //    "hash" -> Json.fromString(a.hash),
  //    "mapping" -> a.mapping.asJson
  //  )
  //
  //  // NOTE: `deriveDecoder` doesn't work because of `private` case class constructor.
  //  implicit val decodeQName: Decoder[dom.QName] = (a: dom.QName) => Json.obj(
  //    "localName"          -> Json.fromString(a.localName),
  //    "prefix"             -> Json.fromString(a.namespace.prefix),
  //    "uri"                -> Json.fromString(a.namespace.uri),
  //  )
  //
  //    implicit val decodeElement: Decoder[dom.Element] = (a: dom.Element) => Json.obj(
  //      "name" -> a.getQName.asJson,
  //      "atts" -> (a.attributeIterator map (a => a.getQName) toList).asJson,
  //  )
  //
  //  def maybeWithSpecificElementAnalysisFields(a: ElementAnalysis): List[(String, Json)] =
  //    a match {
  //      case c: Model         => Nil // modelFields(c)
  //      case c: Instance      =>
  //        List(
  //          "readonly"              -> Json.fromBoolean(c.readonly),
  //          "cache"                 -> Json.fromBoolean(c.cache),
  //          "exposeXPathTypes"      -> Json.fromBoolean(c.exposeXPathTypes),
  //          "credentials"           -> c.credentials.asJson,
  //          "excludeResultPrefixes" -> c.excludeResultPrefixes.asJson,
  //          "useInlineContent"      -> Json.fromBoolean(c.useInlineContent),
  //          "useExternalContent"    -> Json.fromBoolean(c.useExternalContent),
  //          "instanceSource"        -> c.instanceSource.asJson,
  //          "inlineRootElem"        -> c.inlineRootElemOpt.asJson
  //
  //
  //          // TODO: `timeToLive`, `indexIds`, `indexClasses`
  //
  //        )
  //      case c: InputControl  => Nil // inputControlFields(c)
  //      case c: OutputControl => Nil // outputControlFields(c)
  //      case c: LHHAAnalysis  => Nil // lhhaAnalysisFields(c)
  //      case c                => Nil
  //    }
  //
  //  def maybeWithChildrenFields(a: ElementAnalysis): List[(String, Json)] =
  //    a match {
  //      case w: WithChildrenTrait if w.children.nonEmpty => List("children" -> withChildrenDecoder(w))
  //      case _                                           => Nil
  //    }
  //
  //  def withChildrenDecoder(a: WithChildrenTrait): Json = a.children.asJson
  //
  //  implicit val encodeElementAnalysis: Decoder[ElementAnalysis] = (a: ElementAnalysis) =>
  //    Json.fromFields(
  //      List(
  //        "index"             -> Json.fromInt(a.index),
  //        "name"              -> Json.fromString(a.localName),
  //        "element"           -> a.element.asJson,
  //        "staticId"          -> Json.fromString(a.staticId),
  //        "prefixedId"        -> Json.fromString(a.prefixedId),
  //        "namespaceMapping"  -> a.namespaceMapping.asJson,
  //        "scopeRef"          -> Json.fromString(a.scope.scopeId),
  //        "containerScopeRef" -> Json.fromString(a.containerScope.scopeId),
  //        "modelRef"          -> (a.model map (_.prefixedId) map Json.fromString).asJson,
  //        "langRef"           -> a.lang.asJson,
  //      ) ++
  //        maybeWithSpecificElementAnalysisFields(a) ++
  //        maybeWithChildrenFields(a)
  //    )

    implicit val decodeQName: Decoder[dom.QName] = (c: HCursor) =>
      for {
        localName <- c.get[String]("localName")
        prefix    <- c.get[String]("prefix")
        uri       <- c.get[String]("uri")
      } yield
        dom.QName(localName: String, prefix: String, uri: String)

    // TODO: Later must be optimized by sharing based on hash!
    implicit val decodeNamespaceMapping: Decoder[NamespaceMapping] = (c: HCursor) =>
      for {
        hash    <- c.get[String]("hash")
        mapping <- c.get[Map[String, String]]("mapping")
      } yield
        NamespaceMapping(hash, mapping)

    implicit val decodeElement: Decoder[dom.Element] = (c: HCursor) =>
      for {
        name <- c.get[dom.QName]("name")
        atts <- c.get[List[(dom.QName, String)]]("atts")
      } yield {
        val r = dom.Element(name)
        atts foreach { case (name, value) => r.addAttribute(name, value) }
        r
      }

    implicit lazy val decodeElementAnalysis: Decoder[ElementAnalysis] = (c: HCursor) =>
      for {
        index             <- c.get[Int]("index")
        name              <- c.get[String]("name")
        element           <- c.get[dom.Element]("element")
        staticId          <- c.get[String]("staticId")
        prefixedId        <- c.get[String]("prefixedId")
        namespaceMapping  <- c.get[NamespaceMapping]("namespaceMapping")
        scopeRef          <- c.get[String]("scopeRef")
        containerScopeRef <- c.get[String]("containerScopeRef")
        modelRef          <- c.get[String]("modelRef")
  //      "langRef"           <- a.lang.asJson,
      } yield {

        val scope = scopes.getOrElseUpdate(scopeRef, {
          val newScope = new Scope(None, scopeRef)
          // TODO: scopes must first be deserialized…
          newScope
        }) // TODO: parent scope

        val containerScope = scope // TODO

        println(s"xxx decoding element for $name / $prefixedId")

        val newControl =
          name match { // XXX TODO: use QName
            case "model" =>
              new Model(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case "instance" =>

              val instance =
                for {
                  readonly              <- c.get[Boolean]("readonly")
                  cache                 <- c.get[Boolean]("cache")
                  timeToLive            <- c.get[Long]("timeToLive")
                  exposeXPathTypes      <- c.get[Boolean]("exposeXPathTypes")
                  indexIds              <- c.get[Boolean]("indexIds")
                  indexClasses          <- c.get[Boolean]("indexClasses")
                  isLaxValidation       <- c.get[Boolean]("isLaxValidation")
                  isStrictValidation    <- c.get[Boolean]("isStrictValidation")
                  isSchemaValidation    <- c.get[Boolean]("isSchemaValidation")
                  indexClasses          <- c.get[Boolean]("indexClasses")
//                  credentials           <- c.get[BasicCredentials]("credentials")
                  excludeResultPrefixes <- c.get[Set[String]]("excludeResultPrefixes")
                  useInlineContent      <- c.get[Boolean]("useInlineContent")
                  useExternalContent    <- c.get[Boolean]("useExternalContent")
                  instanceSource        <- c.get[Option[String]]("instanceSource")
                  inlineRootElemOpt     <- c.get[Option[dom.Element]]("inlineRootElem")
                } yield {

                  val r =
                    new Instance(
                      index,
                      element,
                      controlStack.headOption,
                      None,
                      staticId,
                      prefixedId,
                      namespaceMapping,
                      scope,
                      containerScope,
                      InstanceMetadata(
                        readonly              = readonly,
                        cache                 = cache,
                        timeToLive            = timeToLive,
                        handleXInclude        = false, // not serialized
                        exposeXPathTypes      = exposeXPathTypes,
                        indexIds              = indexIds,
                        indexClasses          = indexClasses,
                        isLaxValidation       = isLaxValidation,
                        isStrictValidation    = isStrictValidation,
                        isSchemaValidation    = isSchemaValidation,
                        credentials           = null, // XXX TODO
                        excludeResultPrefixes = excludeResultPrefixes,
                        inlineRootElemOpt     = inlineRootElemOpt,
                        useInlineContent      = useInlineContent,
                        useExternalContent    = useExternalContent,
                        instanceSource        = instanceSource,
                        dependencyURL         = None // not serialized
                      )
                    )
                  r.useExternalContent
                  r
                }

              println(s"xxxx instance $instance")

              instance.right.get

            case "input" =>

              new InputControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)

            case "output" =>
              val isImageMediatype    : Boolean = false
              val isHtmlMediatype     : Boolean = false
              val isDownloadAppearance: Boolean = false
              val staticValue         : Option[String] = None

              new OutputControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope,
                isImageMediatype,
                isHtmlMediatype,
                isDownloadAppearance,
                staticValue
              )
            case "label" =>

              val staticValue               : Option[String] = None
              val isPlaceholder             : Boolean = false
              val containsHTML              : Boolean = false
              val hasLocalMinimalAppearance : Boolean = false
              val hasLocalFullAppearance    : Boolean = false
              val hasLocalLeftAppearance    : Boolean = false

              new LHHAAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope,
                staticValue,
                isPlaceholder,
                containsHTML,
                hasLocalMinimalAppearance,
                hasLocalFullAppearance,
                hasLocalLeftAppearance
              )
            case "root" =>
              new RootControl(index, element, staticId, prefixedId, namespaceMapping, scope, containerScope, None)
            case _ =>
              new ElementAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope) {}
          }

        Index.indexNewControl(newControl)

        newControl match {
          case withChildren: WithChildrenTrait =>

            controlStack ::= withChildren
            c.get[Iterable[ElementAnalysis]]("children") foreach withChildren.addChildren
            controlStack = controlStack.tail
          case _ =>
        }

        newControl
      }

    implicit val decodeTopLevelPartAnalysis: Decoder[TopLevelPartAnalysis] = (c: HCursor) =>
      for {
        startScope       <- c.get[Scope]("startScope")
        topLevelControls <- c.get[Iterable[ElementAnalysis]]("topLevelControls") // TODO
      } yield
        TopLevelPartAnalysisImpl(
          startScope,
          topLevelControls,
          Index.controlAnalysisMap,
          Index.controlTypes,
          Index.lhhas,
          Index.eventHandlers,
          Index.models,
          Index.attributes
        )

    implicit val decodeXFormsStaticState: Decoder[XFormsStaticState] = (c: HCursor) =>
      for {
        nonDefaultProperties <- c.get[Map[String, (String, Boolean)]]("nonDefaultProperties")
        topLevelPart         <- c.get[TopLevelPartAnalysis]("topLevelPart")
        template             <- c.get[Option[AnnotatedTemplate]]("template")
      } yield
        XFormsStaticStateImpl(nonDefaultProperties, Int.MaxValue, topLevelPart, template) // TODO: serialize `globalMaxSizeProperty` from server

    decode[XFormsStaticState](jsonString) match {
      case Left(error) =>
        println(error.getMessage)
        throw error
      case Right(result) => result
    }
  }
}


object XFormsStaticStateImpl {

  def apply(
    _nonDefaultProperties : Map[String, (String, Boolean)],
    globalMaxSizeProperty : Int,
    _topLevelPart         : TopLevelPartAnalysis,
    _template             : Option[AnnotatedTemplate])(implicit
    _logger               : IndentedLogger
  ): XFormsStaticState = {

    val staticProperties = new XFormsStaticStateStaticPropertiesImpl(_nonDefaultProperties, globalMaxSizeProperty) {
      protected def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean = true
    }

    val dynamicProperties = new XFormsStaticStateDynamicPropertiesImpl(_nonDefaultProperties, staticProperties, _topLevelPart)

    new XFormsStaticState {

      def getIndentedLogger: IndentedLogger = _logger

      def digest: String = "" // XXX TODO
      def encodedState: String = "" // XXX TODO

      val topLevelPart: TopLevelPartAnalysis = _topLevelPart
      val template: Option[AnnotatedTemplate] = _template

      def isHTMLDocument: Boolean = true // TODO

      // TODO: serialize/deserialize
      def assets: XFormsAssets = XFormsAssets(Nil, Nil) // XXX TODO
      def sanitizeInput: String => String = identity // XXX TODO

      def nonDefaultProperties: Map[String, (String, Boolean)] = _nonDefaultProperties

      // Delegate (Scala 3's `export` would be nice!)
      def isClientStateHandling               : Boolean          = staticProperties.isClientStateHandling
      def isServerStateHandling               : Boolean          = staticProperties.isServerStateHandling
      def isXPathAnalysis                     : Boolean          = staticProperties.isXPathAnalysis
      def isCalculateDependencies             : Boolean          = staticProperties.isCalculateDependencies
      def isInlineResources                   : Boolean          = staticProperties.isInlineResources
      def uploadMaxSize                       : MaximumSize      = staticProperties.uploadMaxSize
      def uploadMaxSizeAggregate              : MaximumSize      = staticProperties.uploadMaxSizeAggregate
      def staticProperty       (name: String) : Any              = staticProperties.staticProperty       (name)
      def staticStringProperty (name: String) : String           = staticProperties.staticStringProperty (name)
      def staticBooleanProperty(name: String) : Boolean          = staticProperties.staticBooleanProperty(name)
      def staticIntProperty    (name: String) : Int              = staticProperties.staticIntProperty    (name)
      def clientNonDefaultProperties          : Map[String, Any] = staticProperties.clientNonDefaultProperties
      def allowedExternalEvents               : Set[String]      = staticProperties.allowedExternalEvents

      def uploadMaxSizeAggregateExpression        : Option[StaticXPath.CompiledExpression]      = dynamicProperties.uploadMaxSizeAggregateExpression
      def propertyMaybeAsExpression(name: String) : Either[Any, StaticXPath.CompiledExpression] = dynamicProperties.propertyMaybeAsExpression(name)
    }
  }
}

object TopLevelPartAnalysisImpl {

  def apply(
    _startScope         : Scope,
    _topLevelControls   : Iterable[ElementAnalysis],
    _controlAnalysisMap : mutable.LinkedHashMap[String, ElementAnalysis],
    _controlTypes       : mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]],
    lhhas               : mutable.Buffer[LHHAAnalysis],
    eventHandlers       : mutable.Buffer[EventHandler],
    models              : mutable.Buffer[Model],
    attributes          : mutable.Buffer[AttributeControl])(implicit
    logger              : IndentedLogger
  ): TopLevelPartAnalysis = {

    val partAnalysis =
      new TopLevelPartAnalysis
        with PartModelAnalysis
        with PartControlsAnalysis
        with PartEventHandlerAnalysis {

        def bindingIncludes: Set[String] = Set.empty // XXX TODO

        def bindingsIncludesAreUpToDate: Boolean = true

        def debugOutOfDateBindingsIncludes: String = "" // XXX TODO

        override val controlAnalysisMap: mutable.LinkedHashMap[String, ElementAnalysis] = _controlAnalysisMap
        override val controlTypes: mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]] = _controlTypes

        val startScope: Scope = _startScope

        def findControlAnalysis(prefixedId: String): Option[ElementAnalysis] = controlAnalysisMap.get(prefixedId)

        def parent: Option[PartAnalysis] = None
        def isTopLevelPart: Boolean = true

        // TODO
        val functionLibrary: FunctionLibrary = new FunctionLibrary {

          def bind(functionName: SymbolicName.F, staticArgs: Array[Expression], env: StaticContext, reasons: java.util.List[String]): Expression = {
            println(s"xxx functionLibrary.bind $functionName")
            null
          }
          def getFunctionItem(functionName: SymbolicName.F,staticContext: StaticContext): om.Function = {
            println(s"xxx functionLibrary.getFunctionItem $functionName")
            null
          }
          def isAvailable(functionName: org.orbeon.saxon.trans.SymbolicName.F): Boolean = false
          def copy: FunctionLibrary = this
        }

        // XXX TODO
        def getNamespaceMapping(prefixedId: String): Option[NamespaceMapping] =
          Some(NamespaceMapping(Map("xf" -> "http://www.w3.org/2002/xforms", "xh" -> "http://www.w3.org/1999/xhtml")))

        def hasControls: Boolean =
          getTopLevelControls.nonEmpty || (iterateGlobals map (_.compactShadowTree.getRootElement)).nonEmpty // TODO: duplicated from `StaticPartAnalysisImpl`

        override val getTopLevelControls: List[ElementAnalysis] = _topLevelControls.toList

        def getNamespaceMapping(prefix: String, element: dom.Element): NamespaceMapping = {

          val id = element.idOrThrow
          val prefixedId = if (prefix ne null) prefix + id else id

          getNamespaceMapping(prefixedId) getOrElse
            (throw new IllegalStateException(s"namespace mappings not cached for prefix `$prefix` on element `${element.toDebugString}`"))
        }

        def getMark(prefixedId: String): Option[SAXStore#Mark] = ???

        def iterateGlobals: Iterator[Global] = Iterator.empty // XXX TODO

        def allXblAssetsMaybeDuplicates: Iterable[XBLAssets] = Nil

        def containingScope(prefixedId: String): Scope = ???

        def scopeForPrefixedId(prefixedId: String): Scope = ???

        def scriptsByPrefixedId: Map[String, StaticScript] = ???

        def uniqueJsScripts: List[ShareableScript] = Nil // XXX TODO

        def baselineResources: (List[String], List[String]) = (Nil, Nil) // XXX TODO
      }

    for (model <- models)
      partAnalysis.indexModel(model)

//    for (lhha <- lhhas)
//      LHHAAnalysisBuilder.attachToControl(partAnalysisCtx, lhha)

    partAnalysis.registerEventHandlers(eventHandlers)
    partAnalysis.indexAttributeControls(attributes)

//    ElementAnalysisTreeBuilder.setModelAndLangOnAllDescendants(partAnalysisCtx, container)

    partAnalysis
  }

}