package org.orbeon.oxf.xforms

import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.orbeon.dom
import org.orbeon.dom.QName
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, LoggerFactory, StaticXPath}
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model, StaticBind}
import org.orbeon.oxf.xforms.itemset.{Item, ItemNode, Itemset, LHHAValue}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.{CommonBinding, ConcreteBinding}
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import shapeless.syntax.typeable.typeableOps

import scala.collection.mutable
import scala.jdk.CollectionConverters._


//
// TODO: Optimize output size.
//
// We already output namespaces only once. Ideas for the rest:
//
// - don't output attribute prefix/namespace when they are blank
// - optimize `SAXStore`/template
// - don't output common default values, including `null`, `None`, and `""`
// - XML: omit `"atts": []`
//
object XFormsStaticStateSerializer {

  val Logger = LoggerFactory.createLogger(XFormsStaticStateSerializer.getClass)

  // NOTE: `deriveEncoder` doesn't work because of `private` case class constructor.
  implicit val encodeQName: Encoder[dom.QName] = (a: dom.QName) => Json.obj(
    "localName"          -> Json.fromString(a.localName),
    "prefix"             -> Json.fromString(a.namespace.prefix),
    "uri"                -> Json.fromString(a.namespace.uri),
  )

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

    Json.obj(
      "eventBufferPosition"          -> Json.fromInt(a.eventBufferPosition),
      "eventBuffer"                  -> a.eventBuffer.slice(0, a.eventBufferPosition).asJson,
      "charBufferPosition"           -> Json.fromInt(a.charBufferPosition),
      "charBuffer"                   -> a.charBuffer.slice(0, a.charBufferPosition).asJson,
      "intBufferPosition"            -> Json.fromInt(a.intBufferPosition),
      "intBuffer"                    -> a.intBuffer.slice(0, a.intBufferPosition).asJson,
      "lineBufferPosition"           -> Json.fromInt(a.lineBufferPosition),
      "lineBuffer"                   -> a.lineBuffer.slice(0, a.lineBufferPosition).asJson,
      "systemIdBufferPosition"       -> Json.fromInt(a.systemIdBufferPosition),
      "systemIdBuffer"               -> a.systemIdBuffer.slice(0, a.systemIdBufferPosition).map(x => if (x eq null) "" else x).asJson,
      "attributeCountBufferPosition" -> Json.fromInt(a.attributeCountBufferPosition),
      "attributeCountBuffer"         -> a.attributeCountBuffer.slice(0, a.attributeCountBufferPosition).asJson,
      "attributeCount"               -> Json.fromInt(a.attributeCount),
      "stringBuilder"                -> a.stringBuilder.asScala.asJson,
      "hasDocumentLocator"           -> Json.fromBoolean(a.hasDocumentLocator),
      //    write(out, if (a.publicId == null) "" else a.publicId)
      "marks"                        -> a.getMarks.asScala.asJson
    )
  }

  implicit val encodeAnnotatedTemplate: Encoder[AnnotatedTemplate] = deriveEncoder

  // Serialize as an XML string. Consider whether we are happy with this serialization.
  implicit val encodeDocumentInfo: Encoder[StaticXPath.DocumentNodeInfoType] =
    (a: StaticXPath.DocumentNodeInfoType) => Json.fromString(StaticXPath.tinyTreeToString(a))

  def serialize(template: SAXStore, staticState: XFormsStaticState): String = {

    val namePool = StaticXPath.GlobalNamePool

    def splitAnalysisPath(path: String): List[QName] =
      path match {
        case "" => Nil
        case s =>
          s.splitTo[List]("/") map { e =>
            val isAtt = e.startsWith("@")
            val code = (if (isAtt) e.substring(1) else e).toInt

            val localName = namePool.getLocalName(code)
            val uri       = namePool.getURI(code)

            //namePool.getUnprefixedQName() // Saxon 10
            QName(localName, "", uri)
          }
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

      def updateDistinctCommon(e: ElementAnalysis) = {

        distinct += e.element.getQName

        // XPath analysis QNames
        for {
          analysisOpt <- List(e.bindingAnalysis, e.valueAnalysis) ::: (e.narrowTo[SelectionControlTrait].toList map (_.itemsetAnalysis))
          analysis    <- analysisOpt
          mapSet      <- List(analysis.valueDependentPaths, analysis.returnablePaths)
          pathSet     <- mapSet.map.values
          path        <- pathSet
        } locally {
          splitAnalysisPath(path) foreach { qName =>
            distinct += qName
          }
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
          // TODO: Consider serializing instance as plain XML?
          e.inlineRootElemOpt.iterator flatMap (_.descendantElementIterator(includeSelf = true)) foreach { e =>
            distinct += e.getQName
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
          case s  => splitAnalysisPath(s) map collectedQNamesWithPositions mkString "/"
        }
      }

    implicit val encodeScope: Encoder[Scope] = (a: Scope) => Json.obj(
      "parentRef" -> a.parent.map(_.scopeId).asJson,
      "scopeId"   -> Json.fromString(a.scopeId),
      "idMap"     -> a.idMap.asJson
    )

    implicit val concreteBindingEncoder: Encoder[ConcreteBinding]   = (a: ConcreteBinding) => Json.obj(
      "innerScopeRef" -> Json.fromInt(collectedScopesWithPositions(a.innerScope)),
      "templateTree"  -> a.templateTree.asJson,
      // We do not need to encode `boundElementAtts` as they are just a copy of the attributes on the bound
      // element, and we already have those. So we can save that during serialization, at least until
      // we decide to remove attributes on elements from the serialization.
    )

    implicit val commonBindingEncoder: Encoder[CommonBinding] = (a: CommonBinding) => Json.obj(
      "bindingElemId"               -> a.bindingElemId.asJson,
      "bindingElemNamespaceMapping" -> Json.fromInt(collectedNamespacesWithPositions(a.bindingElemNamespaceMapping.mapping)),
      "directName"                  -> (a.directName map collectedQNamesWithPositions).asJson,
      "cssName"                     -> a.cssName.asJson,
      "containerElementName"        -> Json.fromString(a.containerElementName),
      "modeBinding"                 -> Json.fromBoolean(a.modeBinding),
      "modeValue"                   -> Json.fromBoolean(a.modeValue),
      "modeExternalValue"           -> Json.fromBoolean(a.modeExternalValue),
      "modeJavaScriptLifecycle"     -> Json.fromBoolean(a.modeJavaScriptLifecycle),
      "modeLHHA"                    -> Json.fromBoolean(a.modeLHHA),
      "modeFocus"                   -> Json.fromBoolean(a.modeFocus),
      "modeItemset"                 -> Json.fromBoolean(a.modeItemset),
      "modeSelection"               -> Json.fromBoolean(a.modeSelection),
      "modeHandlers"                -> Json.fromBoolean(a.modeHandlers),
      "standardLhhaAsSeq"           -> a.standardLhhaAsSeq.asJson,
      "standardLhhaAsSet"           -> a.standardLhhaAsSet.asJson,
      "labelFor"                    -> a.labelFor.asJson,
      "formatOpt"                   -> a.formatOpt.asJson,
      "serializeExternalValueOpt"   -> a.serializeExternalValueOpt.asJson,
      "deserializeExternalValueOpt" -> a.deserializeExternalValueOpt.asJson,
      "debugBindingName"            -> Json.fromString(a.debugBindingName),
      "cssClasses"                  -> Json.fromString(a.cssClasses),
      "allowedExternalEvents"       -> a.allowedExternalEvents.asJson,
      "constantInstances"           -> a.constantInstances.toIterable.asJson // NOTE: Keep `.toIterable` to trigger right encoder.
    )

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
      "foo" -> Json.fromString("bar") // XXX FIXME
    )

    implicit val encodeLangRef           : Encoder[LangRef]           = deriveEncoder
    implicit val encodeNamespace         : Encoder[dom.Namespace]     = deriveEncoder
    implicit val encodeBasicCredentials  : Encoder[BasicCredentials]  = deriveEncoder

    implicit val encodeNamespaceMapping: Encoder[NamespaceMapping] = (a: NamespaceMapping) => Json.obj(
      "hash"    -> Json.fromString(a.hash),
      "mapping" -> a.mapping.asJson
    )

    implicit lazy val encodeElementTree: Encoder[dom.Element] = (a: dom.Element) => Json.obj(
      "name"     -> Json.fromInt(collectedQNamesWithPositions(a.getQName)),
      "atts"     -> (a.attributeIterator map (a => (a.getQName, a.getValue)) toList).asJson, // TODO: collectedQNamesWithPositions
      "children" -> Json.arr(a.content collect {
        case n: dom.Element               => n.asJson // recurse
        case n: dom.Text                  => Json.fromString(n.getStringValue)
      }: _*)
    )

    val encodeLocalElementOnly: Encoder[dom.Element] = (a: dom.Element) => Json.obj(
      "name" -> Json.fromInt(collectedQNamesWithPositions(a.getQName)),
      "atts" -> (a.attributeIterator map (a => (a.getQName, a.getValue)) toList).asJson, // TODO: collectedQNamesWithPositions
    )

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

    def maybeWithSpecificElementAnalysisFields(a: ElementAnalysis): List[(String, Json)] =
      a match {
        case c: Model         =>
          List(
            "bindInstances"                    -> c.bindInstances.asJson,
            "computedBindExpressionsInstances" -> c.computedBindExpressionsInstances.asJson,
            "validationBindInstances"          -> c.validationBindInstances.asJson,
            "figuredAllBindRefAnalysis"        -> Json.fromBoolean(c.figuredAllBindRefAnalysis),
            "recalculateOrder"                 -> (c.recalculateOrder  filter (_.nonEmpty) map (_ map (_.staticId))).asJson,
            "defaultValueOrder"                -> (c.defaultValueOrder filter (_.nonEmpty) map (_ map (_.staticId))).asJson
          )
        case c: Instance      =>
          List(
            "readonly"              -> Json.fromBoolean(c.readonly),
            "cache"                 -> Json.fromBoolean(c.cache),
            "timeToLive"            -> Json.fromLong(c.timeToLive),
            "exposeXPathTypes"      -> Json.fromBoolean(c.exposeXPathTypes),
            "indexIds"              -> Json.fromBoolean(c.indexIds),
            "indexClasses"          -> Json.fromBoolean(c.indexClasses),
            "isLaxValidation"       -> Json.fromBoolean(c.isLaxValidation),
            "isStrictValidation"    -> Json.fromBoolean(c.isStrictValidation),
            "isSchemaValidation"    -> Json.fromBoolean(c.isSchemaValidation),
            "credentials"           -> c.credentials.asJson,
            "excludeResultPrefixes" -> c.excludeResultPrefixes.asJson,
            "useInlineContent"      -> Json.fromBoolean(c.useInlineContent),
            "useExternalContent"    -> Json.fromBoolean(c.useExternalContent),
            "instanceSource"        -> c.instanceSource.asJson,
            "inlineRootElem"        -> c.inlineRootElemOpt.asJson
          )
        case c: StaticBind    =>

          implicit val encodeTypeMIP: Encoder[StaticBind.TypeMIP] = (a: StaticBind.TypeMIP) => Json.obj(
            "id"         -> Json.fromString(a.id),
            "datatype"   -> Json.fromString(a.datatype)
          )

          implicit val encodeWhitespaceMIP: Encoder[StaticBind.WhitespaceMIP] = (a: StaticBind.WhitespaceMIP) => Json.obj(
            "id"         -> Json.fromString(a.id),
            "policy"     -> a.policy.asJson
          )

          implicit val encodeXPathMIP: Encoder[StaticBind.XPathMIP] = (a: StaticBind.XPathMIP) => Json.obj(
            "id"         -> Json.fromString(a.id),
            "name"       -> Json.fromString(a.name),
            "level"      -> a.level.asJson,
            "expression" -> Json.fromString(a.expression)
          )

          List(
            "typeMIPOpt"                  -> c.typeMIPOpt.asJson,
//            "dataType"                    -> c.dataType.asJson,
//            "nonPreserveWhitespaceMIPOpt" -> c.nonPreserveWhitespaceMIPOpt.asJson,
            "mipNameToXPathMIP"           -> c.mipNameToXPathMIP.asJson,
            "customMIPNameToXPathMIP"     -> c.customMIPNameToXPathMIP.asJson
//            //allMIPNameToXPathMIP combines both above
//            constraintsByLevel // ValidationLevel // depends on `allMIPNameToXPathMIP`
          )
        case c: OutputControl =>
          List(
            "isImageMediatype"     -> Json.fromBoolean(c.isImageMediatype),
            "isHtmlMediatype"      -> Json.fromBoolean(c.isHtmlMediatype),
            "isDownloadAppearance" -> Json.fromBoolean(c.isDownloadAppearance),
            "staticValue"          -> c.staticValue.asJson
          )
        case c: LHHAAnalysis  =>
          List(
            "expressionOrConstant"      -> c.expressionOrConstant.asJson,
            "isPlaceholder"             -> Json.fromBoolean(c.isPlaceholder),
            "containsHTML"              -> Json.fromBoolean(c.containsHTML),
            "hasLocalMinimalAppearance" -> Json.fromBoolean(c.hasLocalMinimalAppearance),
            "hasLocalFullAppearance"    -> Json.fromBoolean(c.hasLocalFullAppearance),
            "hasLocalLeftAppearance"    -> Json.fromBoolean(c.hasLocalLeftAppearance)
          )
        case c: SelectionControl =>
          List(
            "staticItemset"    -> c.staticItemset.asJson,
            "useCopy"          -> Json.fromBoolean(c.useCopy),
            "mustEncodeValues" -> c.mustEncodeValues.asJson,
            "itemsetAnalysis"  -> c.itemsetAnalysis.asJson
          )
        case c: CaseControl            =>
          List(
            "valueExpression" -> c.valueExpression.asJson,
            "valueLiteral"    -> c.valueLiteral.asJson
          )
        case c: ComponentControl       =>
          List(
            "commonBindingRef" -> Json.fromInt(collectedCommonBindingsWithPositions(c.commonBinding)),
            "binding"          -> c.bindingOrThrow.asJson
          )
        case c: VariableAnalysisTrait  =>
          List(
            "name"                   -> Json.fromString(c.name),
            "expressionOrConstant"   -> c.expressionOrConstant.asJson
          )
        case c: EventHandler           =>
          List(
            "keyText"                -> c.keyText.asJson,
            "keyModifiers"           -> c.keyModifiers.asJson,
            "eventNames"             -> c.eventNames.asJson,
            "isAllEvents"            -> Json.fromBoolean(c.isAllEvents),
            "isCapturePhaseOnly"     -> Json.fromBoolean(c.isCapturePhaseOnly),
            "isTargetPhase"          -> Json.fromBoolean(c.isTargetPhase),
            "isBubblingPhase"        -> Json.fromBoolean(c.isBubblingPhase),
            "propagate"              -> c.propagate.asJson,
            "isPerformDefaultAction" -> c.isPerformDefaultAction.asJson,
            "isPhantom"              -> Json.fromBoolean(c.isPhantom),
            "isIfNonRelevant"        -> Json.fromBoolean(c.isIfNonRelevant),
            "isXBLHandler"           -> Json.fromBoolean(c.isXBLHandler),
            "observersPrefixedIds"   -> c.observersPrefixedIds.asJson,
            "targetPrefixedIds"      -> c.targetPrefixedIds.asJson,
          ) :::
            c.cast[WithExpressionOrConstantTrait].map{v =>
              "expressionOrConstant" -> v.expressionOrConstant.asJson
            }.toList
        case c: WithExpressionOrConstantTrait => // includes `NestedNameOrValueControl` and `xf:message` action
          List(
            "expressionOrConstant" -> c.expressionOrConstant.asJson,
          )
//          // xf:range (skip!)
//        case _: UploadControl          => Nil
//        case _: SwitchControl          => Nil
//        case _: GroupControl           => Nil
//        case _: DialogControl          => Nil
//        case _: AttributeControl       => Nil // ok, all extracted from attributes
//        case _: RepeatControl          => Nil
//        case _: RepeatIterationControl => Nil
//        case _: InputControl           => Nil
//        case _: TriggerControl         => Nil // ok
//        case _: TextareaControl        => Nil // ok
//        case _: SecretControl          => Nil // ok
        case _                         => Nil
      }

    implicit lazy val encodeMapSet: Encoder[MapSet[String, String]] = (a: MapSet[String, String]) =>
      convertMapSet(a).asJson

    implicit lazy val encodeXPathAnalysis: Encoder[XPathAnalysis] = (a: XPathAnalysis) =>
      Json.obj(
        "xpathString"            -> Json.fromString(a.xpathString),
        "figuredOutDependencies" -> Json.fromBoolean(a.figuredOutDependencies),
        "valueDependentPaths"    -> a.valueDependentPaths.asJson,
        "returnablePaths"        -> a.returnablePaths.asJson,
        "dependentModels"        -> a.dependentModels.asJson,
        "dependentInstances"     -> a.dependentInstances.asJson,
      )

    implicit lazy val encodeElementAnalysis: Encoder[ElementAnalysis] = (a: ElementAnalysis) =>
      Json.fromFields(
        List(
          "index"             -> Json.fromInt(a.index),
          "element"           -> encodeLocalElementOnly(a.element),
          "staticId"          -> Json.fromString(a.staticId),
          "prefixedId"        -> Json.fromString(a.prefixedId),
          "nsRef"             -> Json.fromInt(collectedNamespacesWithPositions(a.namespaceMapping.mapping)),
          "scopeRef"          -> Json.fromInt(collectedScopesWithPositions(a.scope)),
          "containerScopeRef" -> Json.fromInt(collectedScopesWithPositions(a.containerScope)),
          "modelRef"          -> (a.model map (_.prefixedId) map Json.fromString).asJson,
          "langRef"           -> a.lang.asJson,
          "bindingAnalysis"   -> a.bindingAnalysis.asJson,
          "valueAnalysis"     -> a.valueAnalysis.asJson,
        ) ++
          maybeWithSpecificElementAnalysisFields(a) ++
          maybeWithChildrenFields(a)
      )

    def maybeWithChildrenFields(a: ElementAnalysis): List[(String, Json)] =
      a match {
        case w: WithChildrenTrait if w.children.nonEmpty => List("children" -> withChildrenEncoder(w))
        case _                                           => Nil
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
