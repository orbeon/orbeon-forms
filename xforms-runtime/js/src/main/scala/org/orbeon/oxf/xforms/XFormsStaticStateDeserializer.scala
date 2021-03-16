package org.orbeon.oxf.xforms

import cats.syntax.option._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.{Decoder, DecodingFailure, HCursor}
import org.orbeon.datatypes.MaximumSize
import org.orbeon.dom
import org.orbeon.dom.QName
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, Modifier, StaticXPath, XPath}
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlUtil.TopLevelItemsetQNames
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model._
import org.orbeon.oxf.xforms.itemset.{Item, Itemset, LHHAValue}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.{CommonBinding, ConcreteBinding, XBLAssets}
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsId}
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.analysis.{Perform, Propagate}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import shapeless.syntax.typeable.typeableOps

import java.util.Base64
import java.{util => ju}
import scala.collection.mutable


object XFormsStaticStateDeserializer {

  // Our own `Int` decoder which assumes that all `Int` values fit within 56 bits and
  // are returned as `Double`. This is because otherwise we get many instantiations of
  // `BigDecimal` which are costly. One issue is that `toDouble` truncates. Can we
  // detect that efficiently? Or guaranteed that we never store a value which will be
  // truncated?
  implicit final val decodeInt: Decoder[Int] = (c: HCursor) =>
    c.value.asNumber.map(_.toDouble.toInt).toRight(DecodingFailure("Custom Int", c.history))

  def deserialize(
    jsonString      : String,
    functionLibrary : FunctionLibrary)(implicit
    logger          : IndentedLogger
  ): XFormsStaticState = {

    require(functionLibrary ne null)

    val namePool = StaticXPath.GlobalNamePool

    var collectedStrings        = IndexedSeq[String]()
    var collectedNamespaces     = IndexedSeq[Map[String, String]]()
    var collectedQNames         = IndexedSeq[QName]()
    var collectedCommonBindings = IndexedSeq[CommonBinding]()
    var collectedScopes         = IndexedSeq[Scope]()
    var collectedModelRefs      = Map[String, List[ElementAnalysis]]()
    var collectedModelOrderings = Map[String, (Option[Iterable[String]], Option[Iterable[String]])]()

    var controlStack: List[ElementAnalysis] = Nil

    object Index {

      val controlAnalysisMap = mutable.LinkedHashMap[String, ElementAnalysis]()
      val controlTypes       = mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]]()
      val lhhas              = mutable.Buffer[LHHAAnalysis]()
      val eventHandlers      = mutable.Buffer[EventHandler]()
      val models             = mutable.Buffer[Model]()
      val attributes         = mutable.Buffer[AttributeControl]()

      var namespaces         = Map[String, NamespaceMapping]()

      def indexNewControl(elementAnalysis : ElementAnalysis): Unit = {

        // Index by prefixed id
        controlAnalysisMap += elementAnalysis.prefixedId -> elementAnalysis

        // Index by type
        val controlName = elementAnalysis.localName
        val controlsMap = controlTypes.getOrElseUpdate(controlName, mutable.LinkedHashMap[String, ElementAnalysis]())
        controlsMap += elementAnalysis.prefixedId -> elementAnalysis

        // Index namespaces
        namespaces += elementAnalysis.prefixedId -> elementAnalysis.namespaceMapping

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

    // Update this as side-effect of deserializing a `Mark` as we need to index all of them
    var collectedSAXStoreMarks: Map[String, SAXStore#Mark] = Map.empty

    implicit val decodeSAXStore: Decoder[SAXStore] = (c: HCursor) => {

      val a: SAXStore = new SAXStore

      implicit val decodeMark: Decoder[a.Mark] = (c2: HCursor) => {
        for {
          _id                          <- c2.get[String]("_id")
          eventBufferPosition          <- c2.get[Int]("eventBufferPosition")
          charBufferPosition           <- c2.get[Int]("charBufferPosition")
          intBufferPosition            <- c2.get[Int]("intBufferPosition")
          lineBufferPosition           <- c2.get[Int]("lineBufferPosition")
          systemIdBufferPosition       <- c2.get[Int]("systemIdBufferPosition")
          attributeCountBufferPosition <- c2.get[Int]("attributeCountBufferPosition")
          stringBuilderPosition        <- c2.get[Int]("stringBuilderPosition")
        } yield
          a.newMark(
            Array(
              eventBufferPosition,
              charBufferPosition,
              intBufferPosition,
              lineBufferPosition,
              systemIdBufferPosition,
              attributeCountBufferPosition,
              stringBuilderPosition
            ),
            _id
          ) |!>
            (collectedSAXStoreMarks += _id -> _) // collect as we go
      }

      for {
        eventBufferPosition          <- c.get[Int]("eventBufferPosition")
        eventBuffer                  <- c.get[String]("eventBuffer").map(Base64.getDecoder.decode)
        charBufferPosition           <- c.get[Int]("charBufferPosition")
        charBuffer                   <- c.get[String]("charBuffer").map(_.toCharArray)
        intBufferPosition            <- c.get[Int]("intBufferPosition")
        intBuffer                    <- c.get[Array[Int]]("intBuffer")
        lineBufferPosition           <- c.getOrElse[Int]("lineBufferPosition")(0)
        lineBuffer                   <- c.getOrElse[Array[Int]]("lineBuffer")(Array.empty)
        systemIdBufferPosition       <- c.getOrElse[Int]("systemIdBufferPosition")(0)
        systemIdBuffer               <- c.getOrElse[Array[String]]("systemIdBuffer")(Array.empty)
        attributeCountBufferPosition <- c.get[Int]("attributeCountBufferPosition")
        attributeCountBuffer         <- c.get[Array[Int]]("attributeCountBuffer")
        attributeCount               <- c.get[Int]("attributeCount")
        stringBuilder                <- c.get[Array[Int]]("stringBuilder").map(_.map(collectedStrings))
        hasDocumentLocator           <- c.getOrElse[Boolean]("hasDocumentLocator")(false)
        _                            <- c.getOrElse[Iterable[a.Mark]]("marks")(Nil) // decoding registers marks as side-effect!
      } yield {

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
        a.hasDocumentLocator           = hasDocumentLocator && false // XXX TODO: don't use location due to bug AND we don't need it

        a
      }
    }

    // TODO: Later must be optimized by sharing based on hash!
    implicit val decodeNamespaceMapping: Decoder[NamespaceMapping] = (c: HCursor) =>
      for {
        hash    <- c.get[String]("hash")
        mapping <- c.get[Map[String, String]]("mapping")
      } yield
        NamespaceMapping(hash, mapping)

    implicit val decodeQName: Decoder[dom.QName] = (c: HCursor) =>
      for {
        localName <- c.get[String]("localName")
        prefix    <- c.getOrElse[String]("prefix")("")
        uri       <- c.getOrElse[String]("uri")("")
      } yield
        dom.QName(localName: String, prefix: String, uri: String)

    implicit val decodeDocumentInfo: Decoder[StaticXPath.DocumentNodeInfoType] = (c: HCursor) => {

      val docOpt =
        c.value.asString map
          (XFormsCrossPlatformSupport.stringToTinyTree(XPath.GlobalConfiguration, _, handleXInclude = false, handleLexical = false))

      docOpt.toRight(DecodingFailure("missing XML", Nil))
    }

    implicit val decodeCommonBinding: Decoder[CommonBinding] = (c: HCursor) =>
      for {
        bindingElemId               <- c.get[Option[String]]("bindingElemId")
        bindingElemNamespaceMapping <- c.get[Int]("bindingElemNamespaceMapping")
        directName                  <- c.get[Option[Int]]("directName")
        cssName                     <- c.get[Option[String]]("cssName")
        containerElementName        <- c.getOrElse[String]("containerElementName")("div")
        modeBinding                 <- c.getOrElse[Boolean]("modeBinding")(false)
        modeValue                   <- c.getOrElse[Boolean]("modeValue")(false)
        modeExternalValue           <- c.getOrElse[Boolean]("modeExternalValue")(false)
        modeJavaScriptLifecycle     <- c.getOrElse[Boolean]("modeJavaScriptLifecycle")(false)
        modeLHHA                    <- c.getOrElse[Boolean]("modeLHHA")(false)
        modeFocus                   <- c.getOrElse[Boolean]("modeFocus")(false)
        modeItemset                 <- c.getOrElse[Boolean]("modeItemset")(false)
        modeSelection               <- c.getOrElse[Boolean]("modeSelection")(false)
        modeHandlers                <- c.getOrElse[Boolean]("modeHandlers")(false)
        standardLhhaAsSeq           <- c.getOrElse[Seq[LHHA]]("standardLhhaAsSeq")(Nil)
        labelFor                    <- c.getOrElse[Option[String]]("labelFor")(None)
        formatOpt                   <- c.getOrElse[Option[String]]("formatOpt")(None)
        serializeExternalValueOpt   <- c.getOrElse[Option[String]]("serializeExternalValueOpt")(None)
        deserializeExternalValueOpt <- c.getOrElse[Option[String]]("deserializeExternalValueOpt")(None)
        cssClasses                  <- c.get[String]("cssClasses")
        allowedExternalEvents       <- c.getOrElse[Set[String]]("allowedExternalEvents")(Set.empty)
        constantInstances           <- c.getOrElse[Iterable[((Int, Int), StaticXPath.DocumentNodeInfoType)]]("constantInstances")(Map.empty)
      } yield
        CommonBinding(
          bindingElemId,
          NamespaceMapping(collectedNamespaces(bindingElemNamespaceMapping)),
          directName map collectedQNames,
          cssName,
          containerElementName,
          modeBinding,
          modeValue,
          modeExternalValue,
          modeJavaScriptLifecycle,
          modeLHHA,
          modeFocus,
          modeItemset,
          modeSelection,
          modeHandlers,
          standardLhhaAsSeq,
          labelFor,
          formatOpt,
          serializeExternalValueOpt,
          deserializeExternalValueOpt,
          cssClasses,
          allowedExternalEvents,
          constantInstances.toMap,
        )

    // This is only used by `decodeScope`, with the assumption that we
    // decode `Scope`s in order.
    val parentScopesById = mutable.Map[String, Scope]()

    implicit val decodeScope: Decoder[Scope] = (c: HCursor) =>
      for {
        parentRef      <- c.get[Option[String]]("parentRef")
        scopeId        <- c.get[String]("scopeId")
        simplyPrefixed <- c.getOrElse[Iterable[String]]("simplyPrefixed")(Nil)
        other          <- c.getOrElse[Iterable[String]]("other")(Nil)
      } yield {

        val r = new Scope(parentRef.map(parentScopesById), scopeId)

        val prefix = r.fullPrefix

        simplyPrefixed foreach (staticId   => r += staticId -> (prefix + staticId))
        other          foreach (prefixedId => r += XFormsId.getStaticIdFromId(prefixedId) -> prefixedId)

        parentScopesById += r.scopeId -> r

        r
      }

    implicit val decodeConcreteBinding: Decoder[ConcreteBinding] = (c: HCursor) =>
      for {
        innerScope   <- c.get[Int]("innerScopeRef").map(collectedScopes)
        templateTree <- c.get[SAXStore]("templateTree")
      } yield
        ConcreteBinding(
          innerScope,
          templateTree,
          Map.empty // replaced later
        )

    implicit val decodeAnnotatedTemplate : Decoder[AnnotatedTemplate] = deriveDecoder
  //  implicit val decodeLangRef           : Decoder[LangRef]           = deriveDecoder
    implicit val decodeNamespace         : Decoder[dom.Namespace]     = deriveDecoder
    implicit val decodeBasicCredentials  : Decoder[BasicCredentials]  = deriveDecoder
    implicit val decodeLHHAValue         : Decoder[LHHAValue]         = deriveDecoder

    implicit lazy val decodeElement: Decoder[dom.Element] = (c: HCursor) =>
      for {
        nameIndex   <- c.get[Int]("name")
        attsIndexes <- c.getOrElse[List[(Int, String)]]("atts")(Nil)
        children <- {

          val childIt =
            if (c.value.asObject.exists(_.contains("children")))
              c.downField("children").values.toIterator.flatten map {
                case s if s.isString => s.asString.map(dom.Text.apply).toRight(throw new IllegalArgumentException)
                case s if s.isObject => decodeElement.decodeJson(s)
                case s               => throw new IllegalArgumentException
              }
            else
              Iterator.empty

            Right(childIt map (_.right.get)) // TODO: `.get`; ideally should get the first error
        }
      } yield {
        val r = dom.Element(collectedQNames(nameIndex))
        attsIndexes foreach { case (index, value) => r.addAttribute(collectedQNames(index), value) }
        children foreach r.add
        r
      }

    // Dummy as the source must not contain the `om.Item` case
    implicit val decodeOmItem: Decoder[om.Item] = (c: HCursor) =>
      throw new NotImplementedError("decodeOmItem")

    implicit def eitherDecoder[A, B](implicit a: Decoder[A], b: Decoder[B]): Decoder[Either[A, B]] = (c: HCursor) =>
      if (c.value.asObject.exists(_.contains("left")))
        c.get[A]("left").map(Left.apply)
      else
        c.get[B]("right").map(Right.apply)

    //decodeValueNode.asInstanceOf[Decoder[Item.ValueNode]]
    implicit val decodeValueNode: Decoder[Item.ValueNode] = (c: HCursor) =>
      for {
        label      <- c.get[LHHAValue]("label")
        attributes <- c.get[List[(QName, String)]]("attributes") // TODO: QName
        position   <- c.get[Int]("position")
        help       <- c.get[Option[LHHAValue]]("help")
        hint       <- c.get[Option[LHHAValue]]("hint")
        value      <- c.get[String]("value")
      } yield {
        Item.ValueNode(label, help, hint, Left(value), attributes)(position)
      }

    implicit val decodeItemset: Decoder[Itemset] = (c: HCursor) =>
      for {
        multiple <- c.get[Boolean]("multiple")
        hasCopy  <- c.get[Boolean]("hasCopy")
        children <- c.getOrElse[Iterable[Item.ValueNode]]("children")(Nil) // TODO: ChoiceNode
      } yield {
        val r = new Itemset(multiple, hasCopy)
        children foreach r.addChildItem
        r
      }

    implicit val decodeMapSet: Decoder[MapSet[String, String]] = (c: HCursor) => {

      def splitAnalysisPath(path: String): List[Int] =
        path match {
          case "" => Nil
          case s  =>
            s.splitTo[List]("/") map { e =>
              val isAtt = e.startsWith("@")
              val code = (if (isAtt) e.substring(1) else e).toInt

              val qName = collectedQNames(code)
              namePool.allocateFingerprint(qName.namespace.uri, qName.localName)
            }
          }

      def convertPaths(path: String) =
        splitAnalysisPath(path) mkString "/"

      for {
        map <- c.as[mutable.LinkedHashMap[String, mutable.LinkedHashSet[String]]]
      } yield {
        val ms = new MapSet[String, String]
        ms.map ++= map.mapValues(_ map convertPaths)
        ms
      }
    }

    implicit val decodeXPathAnalysis: Decoder[XPathAnalysis] = (c: HCursor) =>
      for {
        _valueDependentPaths    <- c.getOrElse[MapSet[String, String]]("valueDependentPaths")(MapSet.empty)
        _returnablePaths        <- c.getOrElse[MapSet[String, String]]("returnablePaths")(MapSet.empty)
        _dependentModels        <- c.getOrElse[Set[String]]("dependentModels")(Set.empty)
        _dependentInstances     <- c.getOrElse[Set[String]]("dependentInstances")(Set.empty)
      } yield
        new XPathAnalysis {
          val xpathString            = "N/A"
          val figuredOutDependencies = true
          val valueDependentPaths    = _valueDependentPaths
          val returnablePaths        = _returnablePaths
          val dependentModels        = _dependentModels
          val dependentInstances     = _dependentInstances
        }

    var currentIndex = 0

    implicit lazy val decodeElementAnalysis: Decoder[ElementAnalysis] = (c: HCursor) =>
      for {
        element             <- c.get[dom.Element]("element")
        prefixedId          <- c.get[String]("prefixedId")
        namespaceMapping    <- c.get[Int]("nsRef").map(nsRef => NamespaceMapping(collectedNamespaces(nsRef)))
        scopeIndex          <- c.get[Int]("scopeRef")
        containerScopeIndex <- c.getOrElse[Int]("containerScopeRef")(scopeIndex)
        modelOptRef         <- c.get[Option[String]]("modelRef")
  //      "langRef"           <- a.lang.asJson, // default is `LangRef.Undefined`
        bindingAnalysis     <- c.getOrElse[Option[XPathAnalysis]]("bindingAnalysis")(None)
        valueAnalysis       <- c.getOrElse[Option[XPathAnalysis]]("valueAnalysis")(None)
      } yield {

        import org.orbeon.xforms.XFormsNames._

        val index = currentIndex
        currentIndex += 1

        val staticId       = XFormsId.getStaticIdFromId(prefixedId)
        val scope          = collectedScopes(scopeIndex)
        val containerScope = collectedScopes(containerScopeIndex)

        // TODO: Review how we do this. Maybe we should use a map to builders, like `ControlAnalysisFactory`.
        val newControl =
          element.getQName match {

            case _ if c.value.asObject.exists(_.contains("commonBindingRef")) =>

              val componentControl =
                for {
                  commonBindingRef <- c.get[Int]("commonBindingRef")
                  commonBinding    = collectedCommonBindings(commonBindingRef)
                  concreteBinding  <- c.get[ConcreteBinding]("binding")
                } yield {
                  val componentControl =
                    (commonBinding.modeValue, commonBinding.modeLHHA) match {
                      case (false, false) => new ComponentControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope, true)
                      case (false, true)  => new ComponentControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope, true) with                          StaticLHHASupport
                      case (true,  false) => new ComponentControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope, true) with ValueComponentTrait
                      case (true,  true)  => new ComponentControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope, true) with ValueComponentTrait with StaticLHHASupport
                    }

                  componentControl.commonBinding = commonBinding

                  // We don't serialize the attributes separately as we have them on the bound element, so we
                  // copy them again here.
                  componentControl.setConcreteBinding(
                    concreteBinding.copy(boundElementAtts = element.attributes map { att => att.getQName -> att.getValue } toMap)
                  )

                  componentControl
                }

              componentControl.right.get // XXX TODO

            case XFORMS_MODEL_QNAME            =>

              val model =
                for {
                  bindInstances                    <- c.getOrElse[Iterable[String]]("bindInstances")(Nil)
                  computedBindExpressionsInstances <- c.getOrElse[Iterable[String]]("computedBindExpressionsInstances")(Nil)
                  validationBindInstances          <- c.getOrElse[Iterable[String]]("validationBindInstances")(Nil)
                  figuredAllBindRefAnalysis        <- c.getOrElse[Boolean]("figuredAllBindRefAnalysis")(true)
                  recalculateOrder                 <- c.getOrElse[Option[Iterable[String]]]("recalculateOrder")(None)
                  defaultValueOrder                <- c.getOrElse[Option[Iterable[String]]]("defaultValueOrder")(None)
                } yield {
                  val model = new Model(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)

                  model.bindInstances                    ++= bindInstances
                  model.computedBindExpressionsInstances ++= computedBindExpressionsInstances
                  model.validationBindInstances          ++= validationBindInstances
                  model.figuredAllBindRefAnalysis        = figuredAllBindRefAnalysis

                  if (recalculateOrder.isDefined || defaultValueOrder.isDefined) {
                    collectedModelOrderings += prefixedId -> (recalculateOrder map (_.toList), defaultValueOrder map (_.toList))
                  }

                  model
                }

              model.right.get // XXX TODO

            case XFORMS_SUBMISSION_QNAME       => new Submission            (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_INPUT_QNAME            => new InputControl          (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_TEXTAREA_QNAME         => new TextareaControl       (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_SECRET_QNAME           => new SecretControl         (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_TRIGGER_QNAME          => new TriggerControl        (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_SWITCH_QNAME           => new SwitchControl         (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_GROUP_QNAME            => new GroupControl          (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_UPLOAD_QNAME           => new UploadControl         (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XXFORMS_DIALOG_QNAME          => new DialogControl         (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XXFORMS_ATTRIBUTE_QNAME       => new AttributeControl      (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_REPEAT_QNAME           => new RepeatControl         (index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_REPEAT_ITERATION_QNAME => new RepeatIterationControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)

            case XFORMS_CASE_QNAME =>

              val caseControl =
                for {
                  valueExpression <- c.getOrElse[Option[String]]("valueExpression")(None)
                  valueLiteral    <- c.getOrElse[Option[String]]("valueLiteral")(None)
                } yield
                  new CaseControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope,
                    valueExpression,
                    valueLiteral
                  )

              caseControl.right.get // XXX TODO

            case XFORMS_INSTANCE_QNAME =>

              implicit val decodeBasicCredentials: Decoder[BasicCredentials] = deriveDecoder

              val instance =
                for {
                  readonly                <- c.getOrElse[Boolean]("readonly")(false)
                  cache                   <- c.getOrElse[Boolean]("cache")(false)
                  timeToLive              <- c.getOrElse[Long]("timeToLive")(-1L)
                  exposeXPathTypes        <- c.getOrElse[Boolean]("exposeXPathTypes")(false)
                  indexIds                <- c.getOrElse[Boolean]("indexIds")(false)
                  indexClasses            <- c.getOrElse[Boolean]("indexClasses")(false)
                  isLaxValidation         <- c.getOrElse[Boolean]("isLaxValidation")(false)
                  isStrictValidation      <- c.getOrElse[Boolean]("isStrictValidation")(false)
                  isSchemaValidation      <- c.getOrElse[Boolean]("isSchemaValidation")(false)
                  credentials             <- c.getOrElse[Option[BasicCredentials]]("credentials")(None)
                  excludeResultPrefixes   <- c.getOrElse[Set[String]]("excludeResultPrefixes")(Set.empty)
                  useInlineContent        <- c.getOrElse[Boolean]("useInlineContent")(true)
                  useExternalContent      <- c.getOrElse[Boolean]("useExternalContent")(false)
                  instanceSource          <- c.getOrElse[Option[String]]("instanceSource")(None)
                  inlineRootElemOpt       <- c.getOrElse[Option[dom.Element]]("inlineRootElem")(None)
//                  inlineRootElemStringOpt <- c.get[Option[String]]("inlineRootElem")
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
                        credentials           = credentials,
                        excludeResultPrefixes = excludeResultPrefixes,
                        inlineRootElemOpt     = inlineRootElemOpt,
//                        inlineRootElemOpt     = inlineRootElemStringOpt.map(s => XFormsCrossPlatformSupport.readDom4j(s).getRootElement),
                        useInlineContent      = useInlineContent,
                        useExternalContent    = useExternalContent,
                        instanceSource        = instanceSource,
                        dependencyURL         = None // not serialized
                      )
                    )
                  r.useExternalContent
                  r
                }

              instance.right.get // XXX TODO

            case XFORMS_BIND_QNAME =>

              val xpathMipLocationData = ElementAnalysis.createLocationData(element)

              implicit val decodeTypeMIP: Decoder[StaticBind.TypeMIP] = (c: HCursor) =>
                for {
                  id         <- c.get[String]("id")
                  datatype   <- c.get[String]("datatype")
                } yield
                  new StaticBind.TypeMIP(id, datatype)

              implicit val decodeXPathMIP: Decoder[StaticBind.XPathMIP] = (c: HCursor) =>
                for {
                  id         <- c.get[String]("id")
                  name       <- c.get[String]("name")
                  level      <- c.getOrElse[ValidationLevel]("level")(ValidationLevel.ErrorLevel)
                  expression <- c.get[String]("expression")
                } yield
                  new StaticBind.XPathMIP(id, name, level, expression, namespaceMapping, xpathMipLocationData, functionLibrary)

              val staticBind =
                for {
                  typeMIPOpt                  <- c.getOrElse[Option[StaticBind.TypeMIP]]("typeMIPOpt")(None)
                  mipNameToXPathMIP           <- c.getOrElse[Iterable[(String, List[StaticBind.XPathMIP])]]("mipNameToXPathMIP")(Nil)
                  customMIPNameToXPathMIP     <- c.getOrElse[Map[String, List[StaticBind.XPathMIP]]]("customMIPNameToXPathMIP")(Map.empty)
                } yield
                  new StaticBind(
                    index,
                    element,
                    controlStack.headOption,
                    None,
                    staticId,
                    prefixedId,
                    namespaceMapping,
                    scope,
                    containerScope,
                    typeMIPOpt,
                    mipNameToXPathMIP,
                    customMIPNameToXPathMIP,
                    StaticBind.getBindTree(controlStack.headOption)
                  )

            //              var figuredAllBindRefAnalysis: Boolean = false

              staticBind.right.get // XXX TODO

            case XFORMS_OUTPUT_QNAME | XXFORMS_TEXT_QNAME =>

              val output =
                for {
                  isImageMediatype     <- c.getOrElse[Boolean]("isImageMediatype")(false)
                  isHtmlMediatype      <- c.getOrElse[Boolean]("isHtmlMediatype")(false)
                  isDownloadAppearance <- c.getOrElse[Boolean]("isDownloadAppearance")(false)
                  staticValue          <- c.getOrElse[Option[String]]("staticValue")(None)
                } yield
                  new OutputControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope,
                    isImageMediatype,
                    isHtmlMediatype,
                    isDownloadAppearance,
                    staticValue
                  )

              output.right.get // XXX TODO

            case XFORMS_SELECT_QNAME | XFORMS_SELECT1_QNAME =>

              val select =
                for {
                  staticItemset    <- c.getOrElse[Option[Itemset]]("staticItemset")(None)
                  useCopy          <- c.getOrElse[Boolean]("useCopy")(false)
                  mustEncodeValues <- c.getOrElse[Option[Boolean]]("mustEncodeValues")(None)
                  itemsetAnalysis  <- c.getOrElse[Option[XPathAnalysis]]("itemsetAnalysis")(None)
                } yield
                  new SelectionControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope,
                    staticItemset,
                    useCopy,
                    mustEncodeValues
                  ) |!> (_.itemsetAnalysis = itemsetAnalysis)

              select.right.get // XXX TODO

            case qName if LHHA.QNamesSet(qName) =>

              val lhha =
                for {
                  expressionOrConstant      <- c.get[Either[String, String]]("expressionOrConstant")
                  isPlaceholder             <- c.getOrElse[Boolean]("isPlaceholder")(false)
                  containsHTML              <- c.getOrElse[Boolean]("containsHTML")(false)
                  hasLocalMinimalAppearance <- c.getOrElse[Boolean]("hasLocalMinimalAppearance")(false)
                  hasLocalFullAppearance    <- c.getOrElse[Boolean]("hasLocalFullAppearance")(false)
                  hasLocalLeftAppearance    <- c.getOrElse[Boolean]("hasLocalLeftAppearance")(false)
                } yield
                  new LHHAAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope,
                    expressionOrConstant,
                    isPlaceholder,
                    containsHTML,
                    hasLocalMinimalAppearance,
                    hasLocalFullAppearance,
                    hasLocalLeftAppearance
                  )

              lhha.right.get // XXX TODO

            case qName: QName if EventHandler.isAction(qName) =>

              if (EventHandler.isEventHandler(element)) {

                val eventHandler =
                  for {
                    keyText                <- c.getOrElse[Option[String]]("keyText")(None)
                    keyModifiers           <- c.getOrElse[Set[Modifier]]("keyModifiers")(Set.empty)
                    eventNames             <- c.getOrElse[Set[String]]("eventNames")(Set.empty)
                    isAllEvents            <- c.getOrElse[Boolean]("isAllEvents")(false)
                    isCapturePhaseOnly     <- c.getOrElse[Boolean]("isCapturePhaseOnly")(false)
                    isTargetPhase          <- c.getOrElse[Boolean]("isTargetPhase")(true)
                    isBubblingPhase        <- c.getOrElse[Boolean]("isBubblingPhase")(true)
                    propagate              <- c.getOrElse[Propagate]("propagate")(Propagate.Continue)
                    isPerformDefaultAction <- c.getOrElse[Perform]("isPerformDefaultAction")(Perform.Perform)
                    isPhantom              <- c.getOrElse[Boolean]("isPhantom")(false)
                    isIfNonRelevant        <- c.getOrElse[Boolean]("isIfNonRelevant")(false)
                    isXBLHandler           <- c.getOrElse[Boolean]("isXBLHandler")(false)
                    observersPrefixedIds   <- c.getOrElse[Set[String]]("observersPrefixedIds")(Set.empty)
                    targetPrefixedIds      <- c.getOrElse[Set[String]]("targetPrefixedIds")(Set.empty)
                  } yield {
                    // NOTE: See comment in `MessageActionBuilder` about the duplication, etc.
                    val eh =
                      if (EventHandler.isContainerAction(element.getQName)) {
                        new EventHandler(
                          index,
                          element,
                          controlStack.headOption,
                          None,
                          staticId,
                          prefixedId,
                          namespaceMapping,
                          scope,
                          containerScope,
                          keyText,
                          Set.empty,//keyModifiers,
                          eventNames,
                          isAllEvents,
                          isCapturePhaseOnly,
                          isTargetPhase,
                          isBubblingPhase,
                          Propagate.Continue,// propagate,
                          Perform.Perform,//isPerformDefaultAction,
                          isPhantom,
                          isIfNonRelevant,
                          isXBLHandler
                        ) with WithChildrenTrait
                      } else if (element.getQName == XFORMS_MESSAGE_QNAME)
                        new EventHandler(
                          index,
                          element,
                          controlStack.headOption,
                          None,
                          staticId,
                          prefixedId,
                          namespaceMapping,
                          scope,
                          containerScope,
                          keyText,
                          keyModifiers,
                          eventNames,
                          isAllEvents,
                          isCapturePhaseOnly,
                          isTargetPhase,
                          isBubblingPhase,
                          propagate,
                          isPerformDefaultAction,
                          isPhantom,
                          isIfNonRelevant,
                          isXBLHandler
                        ) with WithExpressionOrConstantTrait {
                          val expressionOrConstant: Either[String, String] = c.get[Either[String, String]]("expressionOrConstant").right.get // XXX TODO
                        }
                      else
                        new EventHandler(
                          index,
                          element,
                          controlStack.headOption,
                          None,
                          staticId,
                          prefixedId,
                          namespaceMapping,
                          scope,
                          containerScope,
                          keyText,
                          keyModifiers,
                          eventNames,
                          isAllEvents,
                          isCapturePhaseOnly,
                          isTargetPhase,
                          isBubblingPhase,
                          propagate,
                          isPerformDefaultAction,
                          isPhantom,
                          isIfNonRelevant,
                          isXBLHandler
                        )
                    eh.observersPrefixedIds = observersPrefixedIds
                    eh.targetPrefixedIds    = targetPrefixedIds

                    eh
                  }

                eventHandler.right.get // XXX TODO

              } else {
                if (EventHandler.isContainerAction(element.getQName))
                  new ElementAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope) with ActionTrait with WithChildrenTrait
                else if (element.getQName == XFORMS_MESSAGE_QNAME)
                  new ElementAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope) with ActionTrait with WithExpressionOrConstantTrait {
                    val expressionOrConstant: Either[String, String] = c.get[Either[String, String]]("expressionOrConstant").right.get // XXX TODO
                  }
                else
                  new ElementAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope) with ActionTrait
              }

            case qName if VariableAnalysis.VariableQNames(qName) =>

              val variable =
                for {
                  name                 <- c.get[String]("name")
                  expressionOrConstant <- c.get[Either[String, String]]("expressionOrConstant")
                } yield {
                  if (controlStack.headOption exists (_.localName == XFORMS_MODEL_QNAME.localName))
                    new ModelVariable(
                      index,
                      element,
                      controlStack.headOption,
                      None,
                      staticId,
                      prefixedId,
                      namespaceMapping,
                      scope,
                      containerScope,
                      name,
                      expressionOrConstant
                    )
                  else
                    new VariableControl(
                      index,
                      element,
                      controlStack.headOption,
                      None,
                      staticId,
                      prefixedId,
                      namespaceMapping,
                      scope,
                      containerScope,
                      name,
                      expressionOrConstant
                    )
                }

              variable.right.get // XXX TODO

            // Itemsets
            case XFORMS_CHOICES_QNAME | XFORMS_ITEM_QNAME | XFORMS_ITEMSET_QNAME =>
              new ElementAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope) with WithChildrenTrait
            case XFORMS_HEADER_QNAME =>
              new HeaderControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case XFORMS_VALUE_QNAME | XFORMS_NAME_QNAME =>

              val nestedNameOrValueControl =
                for {
                  expressionOrConstant <- c.get[Either[String, String]]("expressionOrConstant")
                } yield
                  new NestedNameOrValueControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope, expressionOrConstant)

              nestedNameOrValueControl.right.get // XXX TODO

            case XFORMS_COPY_QNAME =>
              new ElementAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope) with RequiredSingleNode

            // Roots
            case XBL_TEMPLATE_QNAME =>
              new ContainerControl(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope)
            case ROOT_QNAME =>
              new RootControl(index, element, staticId, prefixedId, namespaceMapping, scope, containerScope, None)
            case _ =>
              new ElementAnalysis(index, element, controlStack.headOption, None, staticId, prefixedId, namespaceMapping, scope, containerScope) {}
          }

        Index.indexNewControl(newControl)

        // Set XPath analysis
        newControl.contextAnalysis = None
        newControl.bindingAnalysis = bindingAnalysis
        newControl.valueAnalysis   = valueAnalysis

        // We can't set `Model`s immediately, so we store the mapping and do it once the whole tree has been built
        modelOptRef foreach { modelRef =>
          collectedModelRefs += modelRef -> (newControl :: collectedModelRefs.getOrElse(modelRef, Nil))
        }

        newControl match {
          case withChildren: WithChildrenTrait =>
            controlStack ::= withChildren
            c.getOrElse[Iterable[ElementAnalysis]]("children")(Nil) foreach withChildren.addChildren
            controlStack = controlStack.tail
          case _ =>
        }

        newControl
      }

    implicit val decodeScriptType     : Decoder[ScriptType]      = deriveDecoder
    implicit val decodeShareableScript: Decoder[ShareableScript] = deriveDecoder
    implicit val decodeStaticScript   : Decoder[StaticScript]    = deriveDecoder

    implicit val decodeTopLevelPartAnalysis: Decoder[TopLevelPartAnalysisImpl] = (c: HCursor) =>
      for {
        startScope          <- c.get[Int]("startScopeRef").map(collectedScopes)
        topLevelControls    <- c.get[Iterable[ElementAnalysis]]("topLevelControls")
        scriptsByPrefixedId <- c.get[Map[String, StaticScript]]("scriptsByPrefixedId")
        uniqueJsScripts     <- c.get[List[ShareableScript]]("uniqueJsScripts")
        globals             <- c.get[List[SAXStore]]("globals")
      } yield
        TopLevelPartAnalysisImpl(
          startScope,
          topLevelControls,
          scriptsByPrefixedId,
          uniqueJsScripts,
          globals,
          Index.controlAnalysisMap,
          Index.controlTypes,
          Index.lhhas,
          Index.eventHandlers,
          Index.models,
          Index.attributes,
          Index.namespaces,
          functionLibrary
        )

    implicit val decodeProperty: Decoder[PropertyParams] = (c: HCursor) =>
      for {
        name        <- c.get[String]("name")
        typeQName   <- c.get[Int]("type")
        stringValue <- c.get[String]("value")
        namespaces  <- c.get[Int]("namespaces")
      } yield
        PropertyParams(collectedNamespaces(namespaces), name, collectedQNames(typeQName), stringValue)

    implicit val decodeXFormsStaticState: Decoder[XFormsStaticState] = (c: HCursor) =>
      for {
        strings             <- c.get[IndexedSeq[String]]("strings")
        _ = {
          collectedStrings = strings
        }
        namespaces           <- c.get[IndexedSeq[Map[String, String]]]("namespaces")
        _ = {
          collectedNamespaces = namespaces
        }
        qNames               <- c.get[IndexedSeq[QName]]("qnames")
        _ = {
          collectedQNames = qNames
        }
        nonDefaultProperties <- c.get[Map[String, (String, Boolean)]]("nonDefaultProperties")
        properties           <- c.get[Iterable[PropertyParams]]("properties")
        commonBindings       <- c.get[IndexedSeq[CommonBinding]]("commonBindings")
        _ = {
          collectedCommonBindings = commonBindings
        }
        scopes               <- c.get[IndexedSeq[Scope]]("scopes")
        _ = {
          collectedScopes = scopes
        }
        topLevelPart         <- c.get[TopLevelPartAnalysisImpl]("topLevelPart")
        template             <- c.get[SAXStore]("template")
      } yield {

        // Do this *after* the top-level template has been deserialized
        topLevelPart.marks = collectedSAXStoreMarks

        CoreCrossPlatformSupport.properties = PropertySet(properties)

        val enLangRef = LangRef.Literal("en")

        // Set collected `Model` information on elements
        for {
          (modelRef, elems) <- collectedModelRefs
          modelOpt          = Index.controlAnalysisMap.get(modelRef) flatMap (_.narrowTo[Model])
          elem              <- elems
        } locally {
          elem.model = modelOpt
          elem.lang  = enLangRef // XXX TODO: must deserialize
        }

        // Set collected `Model` orderings
        for {
          (modelRef, (recalculateOrder, defaultValueOrder)) <- collectedModelOrderings
          model <- Index.controlAnalysisMap.get(modelRef) flatMap (_.narrowTo[Model])
        } locally {
          model.recalculateOrder  = recalculateOrder  map (_ map model.bindsById toList)
          model.defaultValueOrder = defaultValueOrder map (_ map model.bindsById toList)
        }

        XFormsStaticStateImpl(
          nonDefaultProperties,
          Int.MaxValue,
          topLevelPart,
          AnnotatedTemplate(template).some
        ) // TODO: serialize `globalMaxSizeProperty` from server
      }

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
      protected def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean = featureRequested
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
      // export staticProperties._
      // export dynamicProperties._
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

abstract class TopLevelPartAnalysisImpl
  extends TopLevelPartAnalysis
    with PartModelAnalysis
    with PartControlsAnalysis
    with PartEventHandlerAnalysis {

  // This is set after construction because we deserialize the template and the `TopLevelPartAnalysis` separately
  var marks: Map[String, SAXStore#Mark] = Map.empty

  def getMark(prefixedId: String): Option[SAXStore#Mark] =
    marks.get(prefixedId)
}

object TopLevelPartAnalysisImpl {

  def apply(
    _startScope          : Scope,
    _topLevelControls    : Iterable[ElementAnalysis],
    _scriptsByPrefixedId : Map[String, StaticScript],
    _uniqueJsScripts     : List[ShareableScript],
    _globals             : List[SAXStore],
    _controlAnalysisMap  : mutable.LinkedHashMap[String, ElementAnalysis],
    _controlTypes        : mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]],
    lhhas                : mutable.Buffer[LHHAAnalysis],
    eventHandlers        : mutable.Buffer[EventHandler],
    models               : mutable.Buffer[Model],
    attributes           : mutable.Buffer[AttributeControl],
    namespaces           : Map[String, NamespaceMapping],
    _functionLibrary     : FunctionLibrary)(implicit
    logger               : IndentedLogger
  ): TopLevelPartAnalysisImpl = {

    val partAnalysis =
      new TopLevelPartAnalysisImpl {

        def bindingIncludes: Set[String] = Set.empty // XXX TODO

        def bindingsIncludesAreUpToDate: Boolean = true

        def debugOutOfDateBindingsIncludes: String = "" // XXX TODO

        override val controlAnalysisMap: mutable.LinkedHashMap[String, ElementAnalysis] = _controlAnalysisMap
        override val controlTypes: mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]] = _controlTypes

        val startScope: Scope = _startScope

        def findControlAnalysis(prefixedId: String): Option[ElementAnalysis] =
          controlAnalysisMap.get(prefixedId)

        def wrapElement(elem: dom.Element): om.NodeInfo =
          NodeInfoFactory.elementInfo(elem)

        def parent: Option[PartAnalysis] = None
        def isTopLevelPart: Boolean = true

        val functionLibrary: FunctionLibrary = _functionLibrary

        def getNamespaceMapping(prefixedId: String): Option[NamespaceMapping] =
          namespaces.get(prefixedId)

        def hasControls: Boolean =
          getTopLevelControls.nonEmpty || _globals.nonEmpty

        override val getTopLevelControls: List[ElementAnalysis] = _topLevelControls.toList

        def getNamespaceMapping(prefix: String, element: dom.Element): NamespaceMapping = {

          val id = element.idOrThrow
          val prefixedId = if (prefix ne null) prefix + id else id

          getNamespaceMapping(prefixedId) getOrElse
            (throw new IllegalStateException(s"namespace mappings not cached for prefix `$prefix` on element `${element.toDebugString}`"))
        }

        def iterateGlobals: Iterator[Global] = _globals.map(Global(_, null)).iterator

        def allXblAssetsMaybeDuplicates: Iterable[XBLAssets] = Nil

        def containingScope(prefixedId: String): Scope = throw new NotImplementedError("containingScope")

        def scopeForPrefixedId(prefixedId: String): Scope =
          findControlAnalysis(prefixedId) map
            (_.scope)                     getOrElse
            (throw new IllegalStateException(s"missing scope information for $prefixedId"))

        def scriptsByPrefixedId: Map[String, StaticScript] = _scriptsByPrefixedId
        def uniqueJsScripts: List[ShareableScript] = _uniqueJsScripts

        def baselineResources: (List[String], List[String]) = (Nil, Nil) // XXX TODO
      }

    for (model <- models)
      partAnalysis.indexModel(model)

    for (lhha <- lhhas)
      PartAnalysisSupport.attachToControl(partAnalysis.findControlAnalysis, lhha)

    partAnalysis.registerEventHandlers(eventHandlers)
    partAnalysis.indexAttributeControls(attributes)

//    ElementAnalysisTreeBuilder.setModelAndLangOnAllDescendants(partAnalysisCtx, container)

    partAnalysis
  }
}