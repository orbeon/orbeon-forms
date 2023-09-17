package org.orbeon.oxf.xforms

import cats.syntax.option._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.orbeon.dom
import org.orbeon.dom.QName
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, LoggerFactory, StaticXPath}
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model, StaticBind}
import org.orbeon.oxf.xforms.itemset.{Item, ItemNode, Itemset, LHHAValue}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.{CommonBinding, ConcreteBinding}
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.analysis.{Perform, Propagate}
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import shapeless.syntax.typeable.typeableOps

import java.util.Base64
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._


//
// Ideas for more optimizations:
//
// - make more use of string table
//   - `dependentModels`, `dependentInstances`
//   - `expressionOrConstant`: are all the values in the string table?
// - `ElementAnalysis`: only output element attributes that are needed
//   - for example don't output `id`
//   - check all usages of attributes in static controls and callers of `.element`
// - optimize `SAXStore`/template
//   - are we outputting unneeded content?
//   - can we find a better representation?
//
object XFormsStaticStateSerializer {

  val Logger = LoggerFactory.createLogger(XFormsStaticStateSerializer.getClass)

  // NOTE: `deriveEncoder` doesn't work because of `private` case class constructor.
  implicit val encodeQName: Encoder[dom.QName] = (a: dom.QName) => {

    val b = ListBuffer[(String, Json)]()

    b += "localName" -> Json.fromString(a.localName)
    if (a.namespace.prefix.nonEmpty)
      b += "prefix"  -> Json.fromString(a.namespace.prefix)
    if (a.namespace.uri.nonEmpty)
      b += "uri"     -> Json.fromString(a.namespace.uri)

    Json.fromFields(b)
  }

  implicit val encodeAnnotatedTemplate: Encoder[AnnotatedTemplate] = deriveEncoder

  // Serialize as an XML string. Consider whether we are happy with this serialization.
  implicit val encodeDocumentInfo: Encoder[StaticXPath.DocumentNodeInfoType] =
    (a: StaticXPath.DocumentNodeInfoType) => Json.fromString(StaticXPath.tinyTreeToString(a))

  def serialize(template: SAXStore, staticState: XFormsStaticState): String = {

    val namePool = StaticXPath.GlobalNamePool

    def splitAnalysisPath(path: String): List[(Boolean, QName)] =
      path match {
        case "" => Nil
        case s =>
          s.splitTo[List]("/") map { e =>
            val isAtt = e.startsWith("@")
            val code = (if (isAtt) e.substring(1) else e).toInt

            val localName = namePool.getLocalName(code)
            val uri       = namePool.getURI(code)

            //namePool.getUnprefixedQName() // Saxon 10
            val qName = QName(localName, "", uri)

            (isAtt, qName)
          }
        }

    val (
      collectedStringsInOrder      : Iterable[String],
      collectedStringsWithPositions: Map[String, Int]
    ) = {

      val distinct = mutable.LinkedHashSet[String]()

      def processSAXStore(s: SAXStore): Unit =
        s.stringBuilder.iterator.asScala foreach (distinct +=)

      processSAXStore(template)

      staticState.topLevelPart.iterateGlobals foreach { global =>
        processSAXStore(global.templateTree)
      }

      staticState.topLevelPart.iterateControls foreach {
        case e: ComponentControl => e.bindingOpt foreach (b => processSAXStore(b.templateTree))
        case _ =>
      }

      (distinct, distinct.zipWithIndex.toMap)
    }

    implicit val encodeSAXStore: Encoder[SAXStore] = (a: SAXStore) => {

      implicit val encodeSAXStoreMark: Encoder[a.Mark] = (m: a.Mark) => Json.obj(
        "_id"                          -> Json.fromString(m._id),
        "eventBufferPosition"          -> Json.fromInt(m.eventBufferPosition),
        "charBufferPosition"           -> Json.fromInt(m.charBufferPosition),
        "intBufferPosition"            -> Json.fromInt(m.intBufferPosition),
        "lineBufferPosition"           -> Json.fromInt(m.lineBufferPosition),
        "systemIdBufferPosition"       -> Json.fromInt(m.systemIdBufferPosition),
        "attributeCountBufferPosition" -> Json.fromInt(m.attributeCountBufferPosition),
        "stringBuilderPosition"        -> Json.fromInt(m.stringBuilderPosition)
      )

      val b = ListBuffer[(String, Json)]()

      b += "eventBufferPosition"          -> Json.fromInt(a.eventBufferPosition)
      b += "eventBuffer"                  -> Json.fromString(Base64.getEncoder.encodeToString(a.eventBuffer.slice(0, a.eventBufferPosition)))
      b += "charBufferPosition"           -> Json.fromInt(a.charBufferPosition)
      b += "charBuffer"                   -> Json.fromString(new String(a.charBuffer, 0, a.charBufferPosition))
      b += "intBufferPosition"            -> Json.fromInt(a.intBufferPosition)
      b += "intBuffer"                    -> a.intBuffer.slice(0, a.intBufferPosition).asJson
      if (a.lineBufferPosition > 0) {
        b += "lineBufferPosition"           -> Json.fromInt(a.lineBufferPosition)
        b += "lineBuffer"                   -> a.lineBuffer.slice(0, a.lineBufferPosition).asJson
      }
      if (a.systemIdBufferPosition > 0) {
        b += "systemIdBufferPosition"       -> Json.fromInt(a.systemIdBufferPosition)
        b += "systemIdBuffer"               -> a.systemIdBuffer.slice(0, a.systemIdBufferPosition).map(x => if (x eq null) "" else x).asJson
      }
      b += "attributeCountBufferPosition" -> Json.fromInt(a.attributeCountBufferPosition)
      b += "attributeCountBuffer"         -> a.attributeCountBuffer.slice(0, a.attributeCountBufferPosition).asJson
      b += "attributeCount"               -> Json.fromInt(a.attributeCount)
      b += "stringBuilder"                -> a.stringBuilder.asScala.map(collectedStringsWithPositions).asJson
      if (a.hasDocumentLocator)
        b += "hasDocumentLocator"           -> Json.fromBoolean(a.hasDocumentLocator)
      //    write(out, if (a.publicId == null) "" else a.publicId)
      if (a.getMarks.asScala.nonEmpty)
        b += "marks"                        -> a.getMarks.asScala.asJson

      Json.fromFields(b)
    }

    // We serialize the namespace mappings only once to save space. To give an idea, a basic Form Runner
    // form has 34 distinct namespace mappings. We should be able to reduce that by being more consistent
    // in how we declare namespaces in various files.
    val (
      collectedNamespacesInOrder      : Iterable[Map[String, String]],
      collectedNamespacesWithPositions: Map[Map[String, String], Int]
    ) = {

      val distinct = mutable.LinkedHashSet[Map[String, String]]()

      staticState.topLevelPart.iterateControls foreach {
        case e: ComponentControl =>
          distinct += e.namespaceMapping.mapping
          distinct += e.commonBinding.bindingElemNamespaceMapping.mapping
        case e =>
          distinct += e.namespaceMapping.mapping
      }

      // Namespaces associated with properties
      CoreCrossPlatformSupport.properties.propertyParams foreach {
        distinct += _.namespaces
      }

      (distinct, distinct.zipWithIndex.toMap)
    }

    val (
      collectedScopesInOrder      : Iterable[Scope],
      collectedScopesWithPositions: Map[Scope, Int]
    ) = {

      val distinct = mutable.LinkedHashSet[Scope]()

      // Top-level scope
      staticState.topLevelPart.getTopLevelControls.headOption foreach { c =>
        distinct += c.scope
      }

      // Only `ComponentControl` (and `xxf:dynamic` which is not yet supported here) create nested scopes
      staticState.topLevelPart.iterateControls foreach {
        case c: ComponentControl => distinct += c.bindingOrThrow.innerScope
        case _ =>
      }

      (distinct, distinct.zipWithIndex.toMap)
    }

    val (
      collectedQNamesInOrder      : Iterable[QName],
      collectedQNamesWithPositions: Map[QName, Int]
    ) = {

      val distinct = mutable.LinkedHashSet[QName]()

      def collectForXPathAnalysis(analysis: XPathAnalysis): Unit =
        if (analysis.figuredOutDependencies) // this should not even be preserved in the tree in the first place!
          for {
            mapSet      <- List(analysis.valueDependentPaths, analysis.returnablePaths)
            pathSet     <- mapSet.map.values
            path        <- pathSet
          } locally {
            splitAnalysisPath(path) foreach { case (_, qName) =>
              distinct += qName
            }
          }

      def updateDistinctCommon(e: ElementAnalysis) = {

        distinct += e.element.getQName
        e.element.attributeIterator foreach { att =>
          distinct += att.getQName
        }

        // XPath analysis QNames
        for {
          analysisOpt <- List(e.bindingAnalysis, e.valueAnalysis) ::: (e.narrowTo[SelectionControlTrait].toList map (_.itemsetAnalysis))
          analysis    <- analysisOpt
        } locally {
          collectForXPathAnalysis(analysis)
        }

        distinct += e.element.getQName
      }

      staticState.topLevelPart.iterateControls foreach {
        case e: ComponentControl =>
          updateDistinctCommon(e)
          e.commonBinding.directName foreach (distinct +=)
        case e: Instance =>
          updateDistinctCommon(e)
          // All inline instance element QNames
          // Considered serializing instance as plain XML, but it turns out the parsing on the other side is slow.
          e.inlineRootElemOpt.iterator flatMap (_.descendantElementIterator(includeSelf = true)) foreach { e =>
            distinct += e.getQName
            e.attributeIterator foreach { att =>
              distinct += att.getQName
            }
          }
        case e: StaticBind =>
          updateDistinctCommon(e)
          e.allMIPNameToXPathMIP.valuesIterator.flatten foreach { xpathMip =>
            collectForXPathAnalysis(xpathMip.analysis)
          }
        case e =>
          updateDistinctCommon(e)
      }

      CoreCrossPlatformSupport.properties.propertyParams foreach { c =>
        distinct += c.typeQName
      }

      (distinct, distinct.zipWithIndex.toMap)
    }

    def convertMapSet(m: MapSet[String, String]) =
      m.map.mapValues { set =>
        set map {
          case "" => ""
          case s  => splitAnalysisPath(s) map { case (isAtt, qName) =>
            val p = collectedQNamesWithPositions(qName)
            if (isAtt) "@" + p.toString else p.toString
          } mkString "/"
        }
      }

    implicit val encodeScope: Encoder[Scope] = (a: Scope) => {

      val b = ListBuffer[(String, Json)]()

      b += "parentRef" -> a.parent.map(_.scopeId).asJson
      b += "scopeId"   -> Json.fromString(a.scopeId)

      val prefix = a.fullPrefix

      val (simplyPrefixed, other) =
        a.idMap.toIterable.partition { case (k, v) => v == prefix + k }

      if (simplyPrefixed.nonEmpty)
        b += "simplyPrefixed" -> simplyPrefixed.map(_._1).asJson
      if (other.nonEmpty)
        b += "other"          -> other.map(_._2).asJson

      Json.fromFields(b)
    }

    implicit val concreteBindingEncoder: Encoder[ConcreteBinding]   = (a: ConcreteBinding) => Json.obj(
      "innerScopeRef" -> Json.fromInt(collectedScopesWithPositions(a.innerScope)),
      "templateTree"  -> a.templateTree.asJson,
      // We do not need to encode `boundElementAtts` as they are just a copy of the attributes on the bound
      // element, and we already have those. So we can save that during serialization, at least until
      // we decide to remove attributes on elements from the serialization.
    )

      implicit val commonBindingEncoder: Encoder[CommonBinding] = (a: CommonBinding) => {

        val b = ListBuffer[(String, Json)]()

        b += "bindingElemId"               -> a.bindingElemId.asJson
        b += "bindingElemNamespaceMapping" -> Json.fromInt(collectedNamespacesWithPositions(a.bindingElemNamespaceMapping.mapping))
        b += "directName"                  -> (a.directName map collectedQNamesWithPositions).asJson
        b += "cssName"                     -> a.cssName.asJson
        if (a.containerElementName != "div")
          b += "containerElementName"        -> Json.fromString(a.containerElementName)
        if (a.modeBinding)
          b += "modeBinding"                 -> Json.fromBoolean(a.modeBinding)
        if (a.modeValue)
          b += "modeValue"                   -> Json.fromBoolean(a.modeValue)
        if (a.modeExternalValue)
          b += "modeExternalValue"           -> Json.fromBoolean(a.modeExternalValue)
        if (a.modeJavaScriptLifecycle)
          b += "modeJavaScriptLifecycle"     -> Json.fromBoolean(a.modeJavaScriptLifecycle)
        if (a.modeLHHA)
          b += "modeLHHA"                    -> Json.fromBoolean(a.modeLHHA)
        if (a.modeFocus)
          b += "modeFocus"                   -> Json.fromBoolean(a.modeFocus)
        if (a.modeItemset)
          b += "modeItemset"                 -> Json.fromBoolean(a.modeItemset)
        if (a.modeSelection)
          b += "modeSelection"               -> Json.fromBoolean(a.modeSelection)
        if (a.modeHandlers)
          b += "modeHandlers"                -> Json.fromBoolean(a.modeHandlers)
        if (a.standardLhhaAsSeq.nonEmpty)
          b += "standardLhhaAsSeq"           -> a.standardLhhaAsSeq.asJson
        if (a.labelFor.isDefined)
          b += "labelFor"                    -> a.labelFor.asJson
        if (a.formatOpt.isDefined)
          b += "formatOpt"                   -> a.formatOpt.asJson
        if (a.serializeExternalValueOpt.isDefined)
          b += "serializeExternalValueOpt"   -> a.serializeExternalValueOpt.asJson
        if (a.deserializeExternalValueOpt.isDefined)
          b += "deserializeExternalValueOpt" -> a.deserializeExternalValueOpt.asJson
        b += "cssClasses"                  -> Json.fromString(a.cssClasses)
        if (a.allowedExternalEvents.nonEmpty)
          b += "allowedExternalEvents"       -> a.allowedExternalEvents.asJson
        if (a.constantInstances.nonEmpty)
          b += "constantInstances"           -> a.constantInstances.toIterable.asJson // NOTE: Keep `.toIterable` to trigger right encoder.

      Json.fromFields(b)
    }

    val (
      collectedCommonBindingsInOrder      : Iterable[CommonBinding],
      collectedCommonBindingsWithPositions: Map[CommonBinding, Int]
    ) = {

      val distinct = mutable.LinkedHashSet[CommonBinding]()

      staticState.topLevelPart.iterateControls foreach {
        case c: ComponentControl => distinct += c.commonBinding
        case _ =>
      }

      (distinct, distinct.zipWithIndex.toMap)
    }

    implicit val encodeAttributeControl: Encoder[AttributeControl] = (a: AttributeControl) => Json.obj(
      "foo" -> Json.fromString("bar") // XXX FIXME xxx
    )

    implicit val encodeLangRef           : Encoder[LangRef]           = deriveEncoder
    implicit val encodeNamespace         : Encoder[dom.Namespace]     = deriveEncoder
    implicit val encodeBasicCredentials  : Encoder[BasicCredentials]  = deriveEncoder

    implicit val encodeNamespaceMapping: Encoder[NamespaceMapping] = (a: NamespaceMapping) => Json.obj(
      "hash"    -> Json.fromString(a.hash),
      "mapping" -> a.mapping.asJson
    )

    def appendElemAndAtts(a: dom.Element, b: ListBuffer[(String, Json)]) = {
      b += "name" -> Json.fromInt(collectedQNamesWithPositions(a.getQName))
      val atts = (a.attributeIterator map (a => (collectedQNamesWithPositions(a.getQName), a.getValue))).toList
      if (atts.nonEmpty)
        b += "atts" -> atts.asJson
    }

    implicit lazy val encodeElementTree: Encoder[dom.Element] = (a: dom.Element) => {

      val b = ListBuffer[(String, Json)]()

      appendElemAndAtts(a, b)

      val children =
        a.content collect {
          case n: dom.Element => n.asJson // recurse
          case n: dom.Text    => Json.fromString(n.getStringValue)
        }

      if (children.nonEmpty)
        b += "children" -> Json.fromValues(children)

      Json.fromFields(b)
    }

    val encodeLocalElementOnly: Encoder[dom.Element] = (a: dom.Element) => {
      val b = ListBuffer[(String, Json)]()
      appendElemAndAtts(a, b)
      Json.fromFields(b)
    }

    implicit val encodeLHHAValue: Encoder[LHHAValue] = deriveEncoder
    implicit val encodeItemNode: Encoder[ItemNode] = (a: ItemNode) => Json.fromFields(
      List(
        "label"      -> a.label.asJson,
        "attributes" -> a.attributes.asJson,
        "position"   -> Json.fromInt(a.position)
      ) ++ (
        a match {
          case vn: Item.ValueNode =>
            List(
              "help"       -> vn.help.asJson,
              "hint"       -> vn.hint.asJson,
              "value"      -> vn.value.left.getOrElse(throw new IllegalStateException).asJson
            )
          case _: Item.ChoiceNode =>
            Nil
        }
      )
    )

    implicit val encodeItemset: Encoder[Itemset] = (a: Itemset) => Json.obj(
      "multiple" -> Json.fromBoolean(a.multiple),
      "hasCopy"  -> Json.fromBoolean(a.hasCopy),
      "children" -> a.children.asJson
    )

    implicit val eitherEncoder: Encoder[Either[String, String]] =
      Encoder.encodeEither("left", "right")

    def maybeWithSpecificElementAnalysisFields(a: ElementAnalysis, b: ListBuffer[(String, Json)]): Unit =
      a match {
        case c: Model         =>
          if (c.bindInstances.nonEmpty)
            b += "bindInstances"                    -> c.bindInstances.asJson
          if (c.computedBindExpressionsInstances.nonEmpty)
            b += "computedBindExpressionsInstances" -> c.computedBindExpressionsInstances.asJson
          if (c.validationBindInstances.nonEmpty)
            b += "validationBindInstances"          -> c.validationBindInstances.asJson
          if (! c.figuredAllBindRefAnalysis)
            b += "figuredAllBindRefAnalysis"        -> Json.fromBoolean(c.figuredAllBindRefAnalysis)
          val recalculateOrder = c.recalculateOrder filter (_.nonEmpty)
          if (recalculateOrder.isDefined)
            b += "recalculateOrder"                 -> (recalculateOrder map (_ map (_.staticId))).asJson
          val defaultValueOrder = c.defaultValueOrder filter (_.nonEmpty)
          if (defaultValueOrder.isDefined)
            b += "defaultValueOrder"                -> (defaultValueOrder map (_ map (_.staticId))).asJson
        case c: Instance      =>
            if (c.readonly)
              b += "readonly"              -> Json.fromBoolean(c.readonly)
            if (c.cache)
              b += "cache"                 -> Json.fromBoolean(c.cache)
            if (c.timeToLive != -1)
              b += "timeToLive"            -> Json.fromLong(c.timeToLive)
            if (c.exposeXPathTypes)
              b += "exposeXPathTypes"      -> Json.fromBoolean(c.exposeXPathTypes)
            if (c.indexIds)
              b += "indexIds"              -> Json.fromBoolean(c.indexIds)
            if (c.indexClasses)
              b += "indexClasses"          -> Json.fromBoolean(c.indexClasses)
            if (c.isLaxValidation)
              b += "isLaxValidation"       -> Json.fromBoolean(c.isLaxValidation)
            if (c.isStrictValidation)
              b += "isStrictValidation"    -> Json.fromBoolean(c.isStrictValidation)
            if (c.isSchemaValidation)
              b += "isSchemaValidation"    -> Json.fromBoolean(c.isSchemaValidation)
            if (c.credentials.isDefined)
              b += "credentials"           -> c.credentials.asJson
            if (c.excludeResultPrefixes.nonEmpty)
              b += "excludeResultPrefixes" -> c.excludeResultPrefixes.asJson
            if (! c.useInlineContent)
              b += "useInlineContent"      -> Json.fromBoolean(c.useInlineContent)
            if (c.useExternalContent)
              b += "useExternalContent"    -> Json.fromBoolean(c.useExternalContent)
            if (c.instanceSource.isDefined)
              b += "instanceSource"        -> c.instanceSource.asJson
            if (c.inlineRootElemOpt.isDefined)
              b += "inlineRootElem"        -> c.inlineRootElemOpt.asJson
            if (c.mirror)
              b += "mirror"                -> Json.fromBoolean(c.mirror)
//              "inlineRootElem"        -> (c.inlineRootElemOpt map (e => e.serializeToString())).asJson
        case c: StaticBind    =>

          implicit val encodeTypeMIP: Encoder[StaticBind.TypeMIP] = (a: StaticBind.TypeMIP) => Json.obj(
            "id"         -> Json.fromString(a.id),
            "datatype"   -> Json.fromString(a.datatype)
          )

          implicit val encodeWhitespaceMIP: Encoder[StaticBind.WhitespaceMIP] = (a: StaticBind.WhitespaceMIP) => Json.obj(
            "id"         -> Json.fromString(a.id),
            "policy"     -> a.policy.asJson
          )

          implicit val encodeXPathMIP: Encoder[StaticBind.XPathMIP] = (a: StaticBind.XPathMIP) => {

            val b = ListBuffer[(String, Json)]()

            b += "id"         -> Json.fromString(a.id)
            b += "name"       -> Json.fromString(a.name)
            if (a.level != ValidationLevel.ErrorLevel)
              b += "level"      -> a.level.asJson
            b += "expression" -> Json.fromString(a.expression)
            if (a.analysis.figuredOutDependencies)
              b += "analysis" -> a.analysis.some.asJson

            Json.fromFields(b)
          }

          if (c.typeMIPOpt.isDefined)
            b += "typeMIPOpt"                  -> c.typeMIPOpt.asJson
  //            "dataType"                    -> c.dataType.asJson,
  //            "nonPreserveWhitespaceMIPOpt" -> c.nonPreserveWhitespaceMIPOpt.asJson,
          if (c.mipNameToXPathMIP.nonEmpty)
            b += "mipNameToXPathMIP"           -> c.mipNameToXPathMIP.asJson
          if (c.customMIPNameToXPathMIP.nonEmpty)
            b += "customMIPNameToXPathMIP"     -> c.customMIPNameToXPathMIP.asJson
  //            //allMIPNameToXPathMIP combines both above
  //            constraintsByLevel // ValidationLevel // depends on `allMIPNameToXPathMIP`
        case c: OutputControl =>
          if (c.isImageMediatype)
            b += "isImageMediatype"     -> Json.fromBoolean(c.isImageMediatype)
          if (c.isVideoMediatype)
            b += "isVideoMediatype"     -> Json.fromBoolean(c.isVideoMediatype)
          if (c.isHtmlMediatype)
            b += "isHtmlMediatype"      -> Json.fromBoolean(c.isHtmlMediatype)
          if (c.isDownloadAppearance)
            b += "isDownloadAppearance" -> Json.fromBoolean(c.isDownloadAppearance)
          if (c.staticValue.isDefined)
            b += "staticValue"          -> c.staticValue.asJson
        case c: LHHAAnalysis  =>
          b += "expressionOrConstant"      -> c.expressionOrConstant.asJson
          if (c.isPlaceholder)
            b += "isPlaceholder"             -> Json.fromBoolean(c.isPlaceholder)
          if (c.containsHTML)
            b += "containsHTML"              -> Json.fromBoolean(c.containsHTML)
          if (c.hasLocalMinimalAppearance)
            b += "hasLocalMinimalAppearance" -> Json.fromBoolean(c.hasLocalMinimalAppearance)
          if (c.hasLocalFullAppearance)
            b += "hasLocalFullAppearance"    -> Json.fromBoolean(c.hasLocalFullAppearance)
          if (c.hasLocalLeftAppearance)
            b += "hasLocalLeftAppearance"    -> Json.fromBoolean(c.hasLocalLeftAppearance)
        case c: SelectionControl =>
          if (c.staticItemset.isDefined)
            b += "staticItemset"    -> c.staticItemset.asJson
          if (c.useCopy)
            b += "useCopy"          -> Json.fromBoolean(c.useCopy)
          if (c.mustEncodeValues.isDefined)
            b += "mustEncodeValues" -> c.mustEncodeValues.asJson
          val itemsetAnalysis = c.itemsetAnalysis.filter(_.figuredOutDependencies)
          if (itemsetAnalysis.isDefined)
            b += "itemsetAnalysis"  -> itemsetAnalysis.asJson
        case c: CaseControl            =>
          if (c.valueExpression.isDefined)
            b += "valueExpression" -> c.valueExpression.asJson
          if (c.valueLiteral.isDefined)
            b += "valueLiteral"    -> c.valueLiteral.asJson
        case c: ComponentControl       =>
          b += "commonBindingRef" -> Json.fromInt(collectedCommonBindingsWithPositions(c.commonBinding))
          b += "binding"          -> c.bindingOrThrow.asJson
        case c: VariableAnalysisTrait  =>
          b += "name"                   -> Json.fromString(c.name)
          b += "expressionOrConstant"   -> c.expressionOrConstant.asJson
        case c: EventHandler           =>
          if (c.keyText.isDefined)
            b += "keyText"                -> c.keyText.asJson
          if (c.keyModifiers.nonEmpty)
            b += "keyModifiers"           -> c.keyModifiers.asJson
          if (c.eventNames.nonEmpty)
            b+= "eventNames"             -> c.eventNames.asJson
          if (c.isAllEvents)
            b += "isAllEvents"            -> Json.fromBoolean(c.isAllEvents)
          if (c.isCapturePhaseOnly)
            b += "isCapturePhaseOnly"     -> Json.fromBoolean(c.isCapturePhaseOnly)
          if (! c.isTargetPhase)
            b += "isTargetPhase"          -> Json.fromBoolean(c.isTargetPhase)
          if (! c.isBubblingPhase)
            b += "isBubblingPhase"        -> Json.fromBoolean(c.isBubblingPhase)
          if (c.propagate != Propagate.Continue)
            b += "propagate"              -> c.propagate.asJson
          if (c.isPerformDefaultAction != Perform.Perform)
            b += "isPerformDefaultAction" -> c.isPerformDefaultAction.asJson
          if (c.isPhantom)
            b += "isPhantom"              -> Json.fromBoolean(c.isPhantom)
          if (c.isIfNonRelevant)
            b += "isIfNonRelevant"        -> Json.fromBoolean(c.isIfNonRelevant)
          if (c.isXBLHandler)
            b += "isXBLHandler"           -> Json.fromBoolean(c.isXBLHandler)
          if (c.observersPrefixedIds.nonEmpty)
            b += "observersPrefixedIds"   -> c.observersPrefixedIds.asJson
          if (c.targetPrefixedIds.nonEmpty)
            b += "targetPrefixedIds"      -> c.targetPrefixedIds.asJson
          c.cast[WithExpressionOrConstantTrait] foreach { v =>
            b += "expressionOrConstant" -> v.expressionOrConstant.asJson
          }
        case c: WithExpressionOrConstantTrait => // includes `NestedNameOrValueControl` and `xf:message` action
          b += "expressionOrConstant" -> c.expressionOrConstant.asJson
//        case _: UploadControl          =>
//        case _: SwitchControl          =>
//        case _: GroupControl           =>
//        case _: DialogControl          =>
//        case _: AttributeControl       => // ok, all extracted from attributes
//        case _: RepeatControl          =>
//        case _: RepeatIterationControl =>
//        case _: InputControl           =>
//        case _: TriggerControl         => // ok
//        case _: TextareaControl        => // ok
//        case _: SecretControl          => // ok
        case _                         =>
      }

    implicit lazy val encodeMapSet: Encoder[MapSet[String, String]] = (a: MapSet[String, String]) =>
      convertMapSet(a).asJson

    implicit lazy val encodeXPathAnalysis: Encoder[XPathAnalysis] = (a: XPathAnalysis) => {

      val b = ListBuffer[(String, Json)]()

      // Don't serialize `xpathString` as it's used only for debugging
      // Don't serialize `figuredOutDependencies` as it's always true
      if (a.valueDependentPaths.nonEmpty)
        b += "valueDependentPaths"    -> a.valueDependentPaths.asJson
      if (a.returnablePaths.nonEmpty)
        b += "returnablePaths"        -> a.returnablePaths.asJson
      if (a.dependentModels.nonEmpty)
        b += "dependentModels"        -> a.dependentModels.asJson
      if (a.dependentInstances.nonEmpty)
        b += "dependentInstances"     -> a.dependentInstances.asJson

      Json.fromFields(b)
    }

    implicit lazy val encodeElementAnalysis: Encoder[ElementAnalysis] = (a: ElementAnalysis) => {

      val b = ListBuffer[(String, Json)]()

      // Don't serialize the index and just recreate in order when deserializing
      b += "element"           -> encodeLocalElementOnly(a.element)
      // Reconstitute `staticId` from the `prefixedId` when deserializing
      b += "prefixedId"        -> Json.fromString(a.prefixedId)
      b += "nsRef"             -> Json.fromInt(collectedNamespacesWithPositions(a.namespaceMapping.mapping))
      b += "scopeRef"          -> Json.fromInt(collectedScopesWithPositions(a.scope))
      // Assume that in many case they are the same
      if (a.containerScope != a.scope)
        b += "containerScopeRef" -> Json.fromInt(collectedScopesWithPositions(a.containerScope))
      b += "modelRef"          -> (a.model map (_.prefixedId) map Json.fromString).asJson
      if (a.lang != LangRef.Undefined)
        b += "langRef"           -> a.lang.asJson

      val bindingAnalysis = a.bindingAnalysis.filter(_.figuredOutDependencies)
      if (bindingAnalysis.isDefined)
        b += "bindingAnalysis"   -> bindingAnalysis.asJson
      val valueAnalysis = a.valueAnalysis.filter(_.figuredOutDependencies)
      if (valueAnalysis.isDefined)
        b += "valueAnalysis"     -> valueAnalysis.asJson

      maybeWithSpecificElementAnalysisFields(a, b)
      maybeWithChildrenFields(a, b)

      Json.fromFields(b)
    }

    def maybeWithChildrenFields(a: ElementAnalysis, b: ListBuffer[(String, Json)]): Unit =
      a.narrowTo[WithChildrenTrait] foreach { w =>
        if (w.children.nonEmpty)
          b += "children" -> withChildrenEncoder(w)
      }

    def withChildrenEncoder(a: WithChildrenTrait): Json = a.children.asJson

    implicit val encodeShareableScript: Encoder[ShareableScript] = deriveEncoder
    implicit val encodeStaticScript   : Encoder[StaticScript]    = deriveEncoder

    implicit val encodeTopLevelPartAnalysis: Encoder[TopLevelPartAnalysis] = (a: TopLevelPartAnalysis) => Json.obj(
      "startScopeRef"       -> Json.fromInt(collectedScopesWithPositions(a.startScope)),
      "topLevelControls"    -> a.getTopLevelControls.asJson,
      "scriptsByPrefixedId" -> a.scriptsByPrefixedId.asJson,
      "uniqueJsScripts"     -> a.uniqueJsScripts.asJson,
      "globals"             -> a.iterateGlobals.map(_.templateTree).toList.asJson
    )

    implicit val encodePropertySet: Encoder[PropertyParams] = (a: PropertyParams) =>
      Json.obj(
        "name"       -> Json.fromString(a.name),
        "type"       -> Json.fromInt(collectedQNamesWithPositions(a.typeQName)),
        "value"      -> Json.fromString(a.stringValue),
        "namespaces" -> Json.fromInt(collectedNamespacesWithPositions(a.namespaces))
      )

    implicit val encodeXFormsStaticState: Encoder[XFormsStaticState] = (a: XFormsStaticState) => Json.obj(
      "strings"              -> collectedStringsInOrder.asJson,
      "namespaces"           -> collectedNamespacesInOrder.asJson,
      "qnames"               -> collectedQNamesInOrder.asJson,
      "nonDefaultProperties" -> a.nonDefaultProperties.asJson,
      "properties"           -> CoreCrossPlatformSupport.properties.propertyParams.asJson,
      "commonBindings"       -> collectedCommonBindingsInOrder.asJson,
      "scopes"               -> collectedScopesInOrder.asJson,
      "topLevelPart"         -> a.topLevelPart.asJson,
      "template"             -> template.asJson
    )

    staticState.asJson.noSpaces
  }
}
