package org.orbeon.oxf.xforms.analysis.controls

import cats.syntax.option._
import org.orbeon.datatypes.ExtendedLocationData
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Element, QName, Text}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Modifier, StaticXPath, XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsProperties.ExposeXpathTypesProperty
import org.orbeon.oxf.xforms.XFormsStaticElementValue
import org.orbeon.oxf.xforms.analysis.ElementAnalysis.attSet
import org.orbeon.oxf.xforms.analysis.EventHandler._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.model.ModelDefs.{Constraint, QNameToXPathMIP, buildInternalCustomMIPName}
import org.orbeon.oxf.xforms.analysis.model.StaticBind.{TypeMIP, XPathMIP}
import org.orbeon.oxf.xforms.analysis.model.{Instance, InstanceMetadata, ModelVariable, StaticBind}
import org.orbeon.oxf.xforms.itemset.{Item, ItemContainer, Itemset, LHHAValue}
import org.orbeon.oxf.xforms.xbl.CommonBinding
import org.orbeon.oxf.xml.dom.Extensions.{DomElemOps, VisitorListener}
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.expr.StringLiteral
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.analysis.{Perform, Propagate}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{BasicNamespaceMapping, EventNames, XFormsNames}
import org.orbeon.xml.NamespaceMapping

import scala.util.{Failure, Success, Try}


object OutputControlBuilder {

  import UploadControlBuilder._

  def apply(
    partAnalysisCtx   : PartAnalysisContextForTree,
    index             : Int,
    element           : Element,
    parent            : Option[ElementAnalysis],
    preceding         : Option[ElementAnalysis],
    staticId          : String,
    prefixedId        : String,
    namespaceMapping  : NamespaceMapping,
    scope             : Scope,
    containerScope    : Scope
  ): OutputControl = {

    // TODO: Duplication in trait
    val appearances: Set[QName]     = ElementAnalysis.attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)

    // Control-specific
    val isImageMediatype    : Boolean = element.attributeValueOpt("mediatype") exists (_.startsWith("image/"))
    val isVideoMediatype    : Boolean = element.attributeValueOpt("mediatype") exists (_.startsWith("video/"))
    val isHtmlMediatype     : Boolean = element.attributeValueOpt("mediatype") contains "text/html"
    val isDownloadAppearance: Boolean = appearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME)

    val staticValue: Option[String] =
      (! isImageMediatype && ! isVideoMediatype && ! isDownloadAppearance && ElementAnalysisTreeBuilder.hasStaticValue(element)) option
        XFormsStaticElementValue.getStaticChildElementValue(containerScope.fullPrefix, element, acceptHTML = true, null)

    new OutputControl(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope
    )(
      isImageMediatype     = isImageMediatype,
      isVideoMediatype     = isVideoMediatype,
      isHtmlMediatype      = isHtmlMediatype,
      isDownloadAppearance = isDownloadAppearance,
      staticValue          = staticValue,
      mediatypeBinding     = element.elementOpt(XFORMS_MEDIATYPE_QNAME).map(makeSingleItemBinding(partAnalysisCtx, containerScope, _)),
      filenameBinding      = element.elementOpt(XFORMS_FILENAME_QNAME).map(makeSingleItemBinding(partAnalysisCtx, containerScope, _)),
      sizeBinding          = element.elementOpt(XXFORMS_SIZE_QNAME).map(makeSingleItemBinding(partAnalysisCtx, containerScope, _))
    )
  }
}

object LHHAAnalysisBuilder {

  // TODO: Duplication from `XFormsProperties`
  val LabelAppearanceProperty = "label.appearance"
  val HintAppearanceProperty  = "hint.appearance"
  val HelpAppearanceProperty  = "help.appearance"

  def apply(
    partAnalysisCtx   : PartAnalysisContextForTree,
    index             : Int,
    element           : Element,
    parent            : Option[ElementAnalysis],
    preceding         : Option[ElementAnalysis],
    staticId          : String,
    prefixedId        : String,
    namespaceMapping  : NamespaceMapping,
    scope             : Scope,
    containerScope    : Scope
  ): LHHAAnalysis = {

    // TODO: Duplication in trait
    val appearances: Set[QName] = ElementAnalysis.attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)

    // TODO: make use of static value
    //
    // - output static value in HTML markup
    // - if has static value, don't attempt to compare values upon diff, and never send new related information to client
    // - 2017-10-17: Now using this in `XFormsLHHAControl`.
    //
    // TODO: figure out whether to allow HTML or not (could default to true?)

    val (expressionOrConstant, containsHTML) =
      XFormsStaticElementValue.findElementExpressionOrConstantDirectOrNested(
        outerElem       = element,
        containerPrefix = containerScope.fullPrefix,
        isWithinRepeat  = parent.exists(_.isWithinRepeat),
        acceptHTML      = true,
        makeString      = StaticXPath.makeStringExpression
      )

    val lhhaType: LHHA =
      LHHA.withNameOption(element.getName) getOrElse
        LHHA.Label // FIXME: Because `SelectionControlTrait` calls this for `value`!

    val hasLocalMinimalAppearance = appearances(XFORMS_MINIMAL_APPEARANCE_QNAME) || appearances(XXFORMS_PLACEHOLDER_APPEARANCE_QNAME)
    val hasLocalFullAppearance    = appearances(XFORMS_FULL_APPEARANCE_QNAME)
    val hasLocalLeftAppearance    = appearances(XXFORMS_LEFT_APPEARANCE_QNAME)

    // Placeholder is only supported for label or hint. This in fact only makes sense for a limited set
    // of controls, namely text fields or text areas at this point.
    val isPlaceholder: Boolean =
      lhhaType match {
        case LHHA.Label | LHHA.Hint =>
          hasLocalMinimalAppearance || (
            ! hasLocalFullAppearance &&
              partAnalysisCtx.staticProperties.staticStringProperty(
                if (lhhaType == LHHA.Hint) HintAppearanceProperty else LabelAppearanceProperty
              )
            .tokenizeToSet.contains(XFORMS_MINIMAL_APPEARANCE_QNAME.localName)
          )
        case _ => false
      }

    new LHHAAnalysis(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope,
      expressionOrConstant,
      isPlaceholder,
      containsHTML,
      hasLocalMinimalAppearance,
      hasLocalFullAppearance,
      hasLocalLeftAppearance
    )
  }
}

object SelectionControlBuilder {

  def apply(
    partAnalysisCtx  : PartAnalysisContextForTree,
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope
  ): SelectionControl = {

    val locationData = ElementAnalysis.createLocationData(element)

    // Try to figure out if we have dynamic items. This attempts to cover all cases, including
    // nested xf:output controls. Check only under xf:choices, xf:item and xf:itemset so that we
    // don't check things like event handlers. Also check for AVTs.
    val hasStaticItemset: Boolean =
      ! XPathCache.evaluateSingle(
        contextItem = newElemWrapper(element),
        xpathString =
          """
          exists(
            (xf:choices | xf:item | xf:itemset)/
            descendant-or-self::*[
              @ref     or
              @nodeset or
              @bind    or
              @value   or
              @*[
                contains(., '{')
              ]
            ]
          )
        """,
        namespaceMapping   = BasicNamespaceMapping.Mapping,
        variableToValueMap = null,
        functionLibrary    = null,
        functionContext    = null,
        baseURI            = null,
        locationData       = locationData,
        reporter           = null
      ).asInstanceOf[Boolean]

    val isMultiple = element.getName == "select"

    // TODO: Duplication in trait
    val appearances = {

      // Ignore no longer supported `xxf:autocomplete` (which would require `selection="open"` anyway)
      val initialAppearances =
        ElementAnalysis.attQNameSet(element, XFormsNames.APPEARANCE_QNAME, namespaceMapping) -
          XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME

      if (isMultiple && initialAppearances(XFORMS_MINIMAL_APPEARANCE_QNAME))
        // Select with minimal appearance is handled as a compact appearance
        initialAppearances - XFORMS_MINIMAL_APPEARANCE_QNAME + XFORMS_COMPACT_APPEARANCE_QNAME
      else if (initialAppearances.nonEmpty)
        initialAppearances
      else if (isMultiple)
        Set(XFORMS_COMPACT_APPEARANCE_QNAME) // default for xf:select
      else
        Set(XFORMS_MINIMAL_APPEARANCE_QNAME) // default for xf:select1
    }

    val isFull    = appearances(XFORMS_FULL_APPEARANCE_QNAME)
    val isCompact = appearances(XFORMS_COMPACT_APPEARANCE_QNAME)

    // Return the control's static itemset if any
    val staticItemset: Option[Itemset] =
      hasStaticItemset option evaluateStaticItemset(element, isMultiple, isFull, containerScope)

    val useCopy: Boolean = {

      val wrapper = newElemWrapper(element)

      val hasCopyElem  = wrapper descendant XFORMS_COPY_QNAME  nonEmpty
      val hasValueElem = wrapper descendant XFORMS_VALUE_QNAME nonEmpty

      // This limitation could be lifted in the future
      if (hasValueElem && hasCopyElem)
        throw new ValidationException(
          s"an itemset cannot have both `xf:copy` and `xf:value` elements",
          ElementAnalysis.createLocationData(element)
        )

      hasCopyElem
    }

    val mustEncodeValues: Option[Boolean] =
      if (useCopy)
        true.some
      else
        element.attributeValueOpt(ENCRYPT_ITEM_VALUES) map (_.toBoolean)

    new SelectionControl(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope
    )(
      staticItemset,
      useCopy,
      mustEncodeValues
    )
  }

  private def newElemWrapper(element: Element): om.NodeInfo = new DocumentWrapper(
    element.getDocument,
    null,
    XPath.GlobalConfiguration
  ).wrap(element)

  private def evaluateStaticItemset(element: Element, isMultiple: Boolean, isFull: Boolean, containerScope: Scope) = {

    // TODO: operate on nested ElementAnalysis instead of Element

    val result = new Itemset(isMultiple, hasCopy = false)

    element.visitDescendants(
      new VisitorListener {

        private var position = 0
        private var currentContainer: ItemContainer = result

        def startElement(element: Element): Unit = {

          def findLhhValue(qName: QName, required: Boolean): Option[LHHAValue] = {

            element.elementOpt(qName) match {
              case Some(lhhaElem) =>

                val containsHTML = Array[Boolean](false)

                val valueOpt =
                  XFormsStaticElementValue.getStaticChildElementValue(
                    containerScope.fullPrefix,
                    lhhaElem,
                    isFull,
                    containsHTML
                  ).trimAllToOpt

                if (required)
                  LHHAValue(valueOpt getOrElse "", containsHTML(0)).some
                else
                  valueOpt map (LHHAValue(_, containsHTML(0)))

              case None =>
                if (required)
                  throw new ValidationException(
                    "`xf:item` or `xf:itemset` must contain an `xf:label` element",
                    ElementAnalysis.createLocationData(element)
                  )
                else
                  None
            }
          }

          element.getQName match {

            case XFORMS_ITEM_QNAME => // xf:item

              val labelOpt = findLhhValue(LABEL_QNAME, required = true)
              val helpOpt  = findLhhValue(HELP_QNAME,  required = false)
              val hintOpt  = findLhhValue(HINT_QNAME,  required = false)

              val valueOpt = {

                val rawValue =
                  element.elementOpt(XFORMS_VALUE_QNAME) map (
                    XFormsStaticElementValue.getStaticChildElementValue(
                      containerScope.fullPrefix,
                      _,
                      acceptHTML = false,
                      null
                    )
                  ) getOrElse (
                    throw new ValidationException(
                      "xf:item must contain an xf:value element.",
                      ElementAnalysis.createLocationData(element)
                    )
                  )

                if (isMultiple)
                  rawValue.trimAllToOpt
                else
                  rawValue.some
              }

              valueOpt foreach { value =>
                currentContainer.addChildItem(
                  Item.ValueNode(
                    label      = labelOpt getOrElse LHHAValue.Empty,
                    help       = helpOpt,
                    hint       = hintOpt,
                    value      = Left(value),
                    attributes = SelectionControlUtil.getAttributes(element)
                  )(
                    position   = position
                  )
                )
                position += 1
              }

            case XFORMS_ITEMSET_QNAME => // xf:itemset

              throw new ValidationException(
                "xf:itemset must not appear in static itemset.",
                ElementAnalysis.createLocationData(element)
              )

            case XFORMS_CHOICES_QNAME => // xf:choices

              val labelOpt = findLhhValue(LABEL_QNAME, required = false)

              labelOpt foreach { _ =>
                val newContainer = Item.ChoiceNode(
                  label      = labelOpt getOrElse LHHAValue.Empty,
                  attributes = SelectionControlUtil.getAttributes(element)
                )(
                  position   = position
                )
                position += 1
                currentContainer.addChildItem(newContainer)
                currentContainer = newContainer
              }

            case _ => // ignore
          }
        }

        def endElement(element: Element): Unit =
          if (element.getQName == XFORMS_CHOICES_QNAME) {
            if (element.elementOpt(LABEL_QNAME).isDefined)
              currentContainer = currentContainer.parent
          }

        def text(text: Text): Unit = ()
      },
      mutable = false
    )
    result
  }
}

object CaseControlBuilder {

  def apply(
    partAnalysisCtx  : PartAnalysisContextForTree,
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope
  ): CaseControl = {

    val locationData: ExtendedLocationData = ElementAnalysis.createLocationData(element)

    val valueExpression =
      element.attributeValueOpt(VALUE_QNAME)

    val valueLiteral =
      valueExpression flatMap { valueExpr =>

        val literal =
          XPath.evaluateAsLiteralIfPossible(
            xpathString      = StaticXPath.makeStringExpression(valueExpr),
            namespaceMapping = namespaceMapping,
            locationData     = locationData,
            functionLibrary  = partAnalysisCtx.functionLibrary,
            avt              = false)(
            logger           = null // TODO: pass a logger? Is passed down to `ShareableXPathStaticContext` for warnings only.
          )

        literal collect {
          case literal: StringLiteral => literal.getStringValue
        }
      }

    new CaseControl(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope,
      valueExpression,
      valueLiteral
    )
  }
}

object UploadControlBuilder {

  def apply(
    partAnalysisCtx  : PartAnalysisContextForTree,
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope
  ): UploadControl =
    new UploadControl(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope
    )(
      multiple         = element.attributeValueOpt(XXFORMS_MULTIPLE_QNAME).contains("true"),
      mediatypeBinding = element.elementOpt(XFORMS_MEDIATYPE_QNAME).map(makeSingleItemBinding(partAnalysisCtx, containerScope, _)),
      filenameBinding  = element.elementOpt(XFORMS_FILENAME_QNAME).map(makeSingleItemBinding(partAnalysisCtx, containerScope, _)),
      sizeBinding      = element.elementOpt(XXFORMS_SIZE_QNAME).map(makeSingleItemBinding(partAnalysisCtx, containerScope, _))
    )

  def makeSingleItemBinding(
    partAnalysisCtx  : PartAnalysisContextForTree,
    containerScope   : Scope,
    elem             : Element
  ): SingleItemBinding = {

    val childPrefixedId = containerScope.fullPrefix + elem.idOrThrow
    val childScope      = partAnalysisCtx.scopeForPrefixedId(childPrefixedId)

    elem.attributeValueOpt(BIND_QNAME) match {
      case Some(bind) =>
        BindSingleItemBinding(
          scope = childScope,
          model = elem.attributeValueOpt(MODEL_QNAME),
          bind  = bind
        )
      case None =>
        ElementAnalysis.getBindingExpression(elem) match {
          case Some(ref) =>
            RefSingleItemBinding(
              scope   = childScope,
              model   = elem.attributeValueOpt(MODEL_QNAME),
              context = elem.attributeValueOpt(CONTEXT_QNAME),
              ns      = partAnalysisCtx.getNamespaceMapping(childPrefixedId).getOrElse(throw new IllegalStateException),
              ref     = ref
            )
          case None =>
            throw new ValidationException(
              s"`${elem.getName}` must have either a `bind` or a `ref` attribute.",
              ElementAnalysis.createLocationData(elem)
            )
        }
    }
  }
}

object InstanceBuilder {

  def apply(
    partAnalysisCtx  : PartAnalysisContextForTree,
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope
  ): Instance =
    new Instance(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope,
      InstanceMetadataBuilder(
        element,
        partAnalysisCtx.staticProperties.staticBooleanProperty(ExposeXpathTypesProperty),
        XmlExtendedLocationData(
          ElementAnalysis.createLocationData(element),
          Some("processing XForms instance"),
          List("id" -> staticId),
          Option(element)
        )
      )
    )
}

object StaticBindBuilder {

  def apply(
    partAnalysisCtx  : PartAnalysisContextForTree,
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope
  ): StaticBind = {

    val xpathMipLocationData = ElementAnalysis.createLocationData(element) // TODO: this is always `null`, even with `oxf.xforms.location-mode` set to `smart`
    val bindTree             = StaticBind.getBindTree(parent)

    val bindNameOpt = Option(element.attributeValue(NAME_QNAME))

    // 1. Ignoring errors makes sense for Form Builder. For other dynamic components, like custom editors in
    //    Form Builder, we should also fail at this point. In short, this should probably be decided by an
    //    attribute  of `xxf:dynamic`. This should also allow providing feedback to the Form Builder user.
    // 2. When using `Try`, we should capture errors so that `xxf:dynamic` can report them.
    def createOrNone(
      id        : String,
      mipName   : String,
      level     : ValidationLevel,
      expression: String
    ): Option[XPathMIP] =
      if (partAnalysisCtx.isTopLevelPart && ! partAnalysisCtx.staticProperties.allowErrorRecoveryOnInit)
        Some(new XPathMIP(id, mipName, level, expression, namespaceMapping, xpathMipLocationData, partAnalysisCtx.functionLibrary))
      else
        Try(new XPathMIP(id, mipName, level, expression, namespaceMapping, xpathMipLocationData, partAnalysisCtx.functionLibrary)) match {
          case Success(mip) =>
            Some(mip)
          case Failure(t)   =>
            partAnalysisCtx.reportStaticXPathError(expression, t.some, XPathErrorDetails.ForBindMip(bindNameOpt, mipName))
            None
        }

    val typeMIPOpt: Option[TypeMIP] = {

      def fromAttribute =
        for (value <- Option(element.attributeValue(TYPE_QNAME)))
          yield new TypeMIP(staticId, value)

      // For a while we supported <xf:validation>
      def fromNestedElementsLegacy =
        for {
          e <- element.elements(XFORMS_VALIDATION_QNAME)
          value <- Option(e.attributeValue(TYPE_QNAME))
        } yield
          new TypeMIP(e.idOrNull, value)

      def fromNestedElement =
        for {
          e <- element.elements(XFORMS_TYPE_QNAME) // `<xf:type>`
          value <- e.getText.trimAllToOpt // text literal (doesn't support `@value`)
        } yield
          new TypeMIP(e.idOrNull, value)

      // Only support collecting one type MIP per bind (but at runtime more than one type MIP can touch a given node)
      fromAttribute orElse fromNestedElement.headOption orElse fromNestedElementsLegacy.headOption
    }

    // Built-in XPath MIPs
    val mipNameToXPathMIP: Iterable[(String, List[XPathMIP])] = {

      def fromAttribute(name: QName) =
        for (value <- Option(element.attributeValue(name)).toList)
        yield (staticId, value, ValidationLevel.ErrorLevel)

      // For a while we supported `<xf:validation>`
      def fromNestedElementLegacy(name: QName) =
        for {
          e     <- element.elements(XFORMS_VALIDATION_QNAME).toList
          value <- e.attributeValueOpt(name)
        } yield
          (e.idOrNull, value, e.attributeValueOpt(LEVEL_QNAME) map ValidationLevel.LevelByName getOrElse ValidationLevel.ErrorLevel)

      def fromNestedElement(name: QName) =
        for {
          e     <- element.elements(name).toList
          value <- Option(e.attributeValue(VALUE_QNAME))
        } yield
          (e.idOrNull, value, e.attributeValueOpt(LEVEL_QNAME) map ValidationLevel.LevelByName getOrElse ValidationLevel.ErrorLevel)

      for {
        mip              <- QNameToXPathMIP.values
        idValuesAndLevel = fromNestedElement(mip.eName) ::: fromNestedElementLegacy(mip.aName) ::: fromAttribute(mip.aName)
        if idValuesAndLevel.nonEmpty
        mips             = idValuesAndLevel flatMap {
          case (id, value, level) =>
            // Ignore level for non-constraint MIPs as it's not supported yet
            val overriddenLevel = if (mip.name == Constraint.name) level else ValidationLevel.ErrorLevel
            createOrNone(id, mip.name, overriddenLevel, value)
        }
      } yield
        mip.name -> mips
    }

    val customMIPNameToXPathMIP: Map[String, List[XPathMIP]] = { // Q: Why String -> List[XPathMIP] and not String -> XPathMIP?

      def attributeCustomMIP =
        for {
          att <- element.attributes.iterator
          if bindTree.isCustomMIP(att.getQName)
          value = att.getValue
          customMIPName = buildInternalCustomMIPName(att.getQName)
        } yield
          (staticId, customMIPName, value)

      // NOTE: We don't support custom MIP elements yet as those are pruned by the annotator/extractor. The following
      // function can be used to retrieve them once that is fixed.
      // def elementCustomMIPs =
      //     for {
      //         elem          <- elements(element).toList
      //         if bindTree.isCustomMIP(elem.getQName)
      //         value         <- Option(elem.attributeValue(VALUE_QNAME))
      //         customMIPName = buildCustomMIPName(elem.getQualifiedName)
      //     } yield
      //         (getElementId(elem), customMIPName, value)

      for {
        (name, idNameValue) <- attributeCustomMIP.toList groupBy (_._2)
        mips = idNameValue flatMap {
          case (id, _, value) =>
            createOrNone(id, name, ValidationLevel.ErrorLevel, value)
        }
      } yield
        name -> mips
    }

    new StaticBind(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope
    )(
      bindNameOpt,
      typeMIPOpt,
      mipNameToXPathMIP,
      customMIPNameToXPathMIP,
      bindTree
    )
  }
}

object ComponentControlBuilder {

  def apply(
    partAnalysisCtx  : PartAnalysisContextForTree,
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope,
    commonBinding    : CommonBinding,
    modeValue        : Boolean,
    modeLHHA         : Boolean
  ): ComponentControl =
    (modeValue, modeLHHA) match {
      case (false, false) => new ComponentControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope, partAnalysisCtx.isTopLevelPart, commonBinding)
      case (false, true)  => new ComponentControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope, partAnalysisCtx.isTopLevelPart, commonBinding) with                          StaticLHHASupport
      case (true,  false) => new ComponentControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope, partAnalysisCtx.isTopLevelPart, commonBinding) with ValueComponentTrait
      case (true,  true)  => new ComponentControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope, partAnalysisCtx.isTopLevelPart, commonBinding) with ValueComponentTrait with StaticLHHASupport
    }
}

object InstanceMetadataBuilder {

  def apply(
    element              : Element,
    partExposeXPathTypes : Boolean,
    extendedLocationData : => ExtendedLocationData
  ): InstanceMetadata = {

    import ElementAnalysis._

    val (indexIds, indexClasses) = {
      val tokens = attSet(element, XXFORMS_INDEX_QNAME)
      (tokens("id"), tokens("class"))
    }

    val validation = element.attributeValue(XXFORMS_VALIDATION_QNAME)

    val isLaxValidation    = (validation eq null) || validation == "lax"
    val isStrictValidation = validation == "strict"

    val credentials: Option[BasicCredentials] = {
      // NOTE: AVTs not supported because XPath expressions in those could access instances that haven't been loaded
      def usernameOpt    = element.attributeValueOpt(XXFORMS_USERNAME_QNAME)
      def password       = element.attributeValue(XXFORMS_PASSWORD_QNAME)
      def preemptiveAuth = element.attributeValue(XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME)
      def domain         = element.attributeValue(XXFORMS_DOMAIN_QNAME)

      usernameOpt map (BasicCredentials(_, password, preemptiveAuth, domain))
    }

    // Inline root element if any
    // TODO: When not needed, we should not keep a reference on this.
    // TODO: When needed, wwe should just keep a detached template.
    val inlineRootElemOpt: Option[Element] = element.elements.headOption
    val hasInlineContent = inlineRootElemOpt.isDefined

    // Don't allow more than one child element
    if (element.elements.size > 1)
      throw new ValidationException("xf:instance must contain at most one child element", extendedLocationData)

    def getAttributeEncode(qName: QName): Option[String] =
      element.attributeValueOpt(qName) map (att => att.trimAllToEmpty.encodeHRRI(processSpace = true))

    val src      = getAttributeEncode(SRC_QNAME)
    val resource = getAttributeEncode(RESOURCE_QNAME)

    // `@src` always wins, `@resource` always loses
    // Q: Is one always the opposite of the other after the checks further below?
    val useInlineContent   : Boolean = src.isEmpty && hasInlineContent
    val useExternalContent : Boolean = src.isDefined || ! hasInlineContent && resource.isDefined

    val (instanceSource, dependencyURL) =
      (if (useInlineContent) None else src orElse resource) match {
        case someSource @ Some(source) if Instance.isProcessorOutputScheme(source) =>
          someSource -> None // `input:*` doesn't add a URL dependency, but is handled by the pipeline engine
        case someSource @ Some(_) =>
          someSource -> someSource
        case _ =>
          None -> None
      }

    // Don't allow a blank `src` attribute
    if (useExternalContent && instanceSource.exists(_.isAllBlank))
      throw new ValidationException("`xf:instance` must not specify a blank URL", extendedLocationData)

    val localExposeXPathTypes = element.attributeValueOpt(XXFORMS_EXPOSE_XPATH_TYPES_QNAME) contains "true"
    val readonly              = element.attributeValueOpt(XXFORMS_READONLY_ATTRIBUTE_QNAME) contains "true"

    InstanceMetadata(
      readonly              = element.attributeValue(XXFORMS_READONLY_ATTRIBUTE_QNAME) == "true",
      cache                 = element.attributeValue(XXFORMS_CACHE_QNAME) == "true",
      timeToLive            = Instance.timeToLiveOrDefault(element),
      handleXInclude        = false,
      exposeXPathTypes      = localExposeXPathTypes || ! readonly && partExposeXPathTypes,
      indexIds              = indexIds,
      indexClasses          = indexClasses,
      isLaxValidation       = isLaxValidation,
      isStrictValidation    = isStrictValidation,
      isSchemaValidation    = isLaxValidation || isStrictValidation,
      credentials           = credentials,
      excludeResultPrefixes = element.attributeValue(XXFORMS_EXCLUDE_RESULT_PREFIXES).tokenizeToSet,
      inlineRootElemOpt     = inlineRootElemOpt,
      useInlineContent      = useInlineContent,
      useExternalContent    = useExternalContent,
      instanceSource        = instanceSource,
      dependencyURL         = dependencyURL,
      mirror                = element.attributeValue(XXBL_MIRROR_QNAME) == "true"
    )
  }
}

object EventHandlerBuilder {

  def apply(
    index                    : Int,
    element                  : Element,
    parent                   : Option[ElementAnalysis],
    preceding                : Option[ElementAnalysis],
    staticId                 : String,
    prefixedId               : String,
    namespaceMapping         : NamespaceMapping,
    scope                    : Scope,
    containerScope           : Scope,
    withChildren             : Boolean,
    withExpressionOrConstant : Boolean // see comment in `MessageActionBuilder.apply`
  ): ElementAnalysis = {

    // We check attributes in the `ev:*` or no namespace. We don't need to handle attributes in the `xbl:*` namespace.
    def att         (name: QName)               : String         = element.attributeValue(name)
    def attOpt      (name: QName)               : Option[String] = element.attributeValueOpt(name)
    def eitherAttOpt(name1: QName, name2: QName): Option[String] = attOpt(name1) orElse attOpt(name2)

    // These are only relevant when listening to keyboard events
    val keyText      : Option[String] = attOpt(XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME)
    val keyModifiers : Set[Modifier]  = parseKeyModifiers(attOpt(XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME))

    val eventNames: Set[String] = {

      val names =
        attSet(element, XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME) ++
          attSet(element, XML_EVENTS_EVENT_ATTRIBUTE_QNAME)

      // For backward compatibility, still support `keypress` even with modifiers, but translate that to `keydown`,
      // as modifiers require `keydown` in browsers.
      if (keyModifiers.nonEmpty)
        names map {
          case EventNames.KeyPress => EventNames.KeyDown
          case other               => other
        }
      else
        names
    }

    // NOTE: If `#all` is present, ignore all other specific events
    val (_, isAllEvents) =
      if (eventNames(XXFORMS_ALL_EVENTS))
        (Set(XXFORMS_ALL_EVENTS), true)
      else
        (eventNames, false)

    val (
      isCapturePhaseOnly: Boolean,
      isTargetPhase     : Boolean,
      isBubblingPhase   : Boolean
    ) = {
      val phaseAsSet = eitherAttOpt(XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME, XML_EVENTS_PHASE_ATTRIBUTE_QNAME).toSet

      val capture  = phaseAsSet("capture")
      val target   = phaseAsSet.isEmpty || phaseAsSet.exists(TargetPhaseTestSet)
      val bubbling = phaseAsSet.isEmpty || phaseAsSet.exists(BubblingPhaseTestSet)

      (capture, target, bubbling)
    }

    val propagate: Propagate =
      if (eitherAttOpt(XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME, XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME) contains "stop")
        Propagate.Stop
      else
        Propagate.Continue

    val isPerformDefaultAction: Perform =
      if (eitherAttOpt(XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME, XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME) contains "cancel")
        Perform.Cancel
      else
        Perform.Perform

    val isPhantom      : Boolean = att(XXFORMS_EVENTS_PHANTOM_ATTRIBUTE_QNAME) == "true"
    val isIfNonRelevant: Boolean = attOpt(XXFORMS_EVENTS_IF_NON_RELEVANT_ATTRIBUTE_QNAME) contains "true"
    val isXBLHandler   : Boolean = element.getQName == XBL_HANDLER_QNAME

    if (withChildren)
      new EventHandler(
        index,
        element,
        parent,
        preceding,
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
      ) with WithChildrenTrait
    else if (withExpressionOrConstant)
      new EventHandler(
        index,
        element,
        parent,
        preceding,
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
        val (expressionOrConstant, _) =
          XFormsStaticElementValue.findElementExpressionOrConstantDirectOrNested(
            outerElem       = element,
            containerPrefix = containerScope.fullPrefix,
            isWithinRepeat  = parent.exists(_.isWithinRepeat),
            acceptHTML      = false,
            makeString      = StaticXPath.makeStringExpression
          )
      }
    else
      new EventHandler(
        index,
        element,
        parent,
        preceding,
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
  }

  private val TargetPhaseTestSet   = Set("target", "default")
  private val BubblingPhaseTestSet = Set("bubbling", "default")
}

object VariableAnalysisBuilder {

  def apply(
    index             : Int,
    element           : Element,
    parent            : Option[ElementAnalysis],
    preceding         : Option[ElementAnalysis],
    staticId          : String,
    prefixedId        : String,
    namespaceMapping  : NamespaceMapping,
    scope             : Scope,
    containerScope    : Scope,
    forModel          : Boolean
  ): VariableAnalysisTrait = {

    // Variable name and value
    val name: String =
      element.attributeValueOpt(NAME_QNAME) getOrElse
        (
          throw new ValidationException(
            s"`${element.getQualifiedName}` element must have a `name` attribute",
            ElementAnalysis.createLocationData(element)
          )
        )

    val valueElement  = VariableAnalysis.valueOrSequenceElement(element) getOrElse element
    val expressionOpt = VariableAnalysis.valueOrSelectAttribute(valueElement)

    val expressionOrConstant = expressionOpt.toLeft(valueElement.getStringValue)

    if (forModel)
      new ModelVariable(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope,
        name,
        expressionOrConstant
      )
    else
      new VariableControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope,
        name,
        expressionOrConstant
      )
  }
}

object NestedNameOrValueControlBuilder {

  def apply(
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope
  ): NestedNameOrValueControl = {

    val (expressionOrConstant, _) =
      XFormsStaticElementValue.findElementExpressionOrConstantDirectOrNested(
        outerElem       = element,
        containerPrefix = containerScope.fullPrefix,
        isWithinRepeat  = parent.exists(_.isWithinRepeat),
        acceptHTML      = false,
        makeString      = StaticXPath.makeStringOptExpression
      )

    new NestedNameOrValueControl(
      index,
      element,
      parent,
      preceding,
      staticId,
      prefixedId,
      namespaceMapping,
      scope,
      containerScope,
      expressionOrConstant
    )
  }
}

object MessageActionBuilder {

  // NOTE: It's not great how we create actions which are also `EventHandler`. Here we need a mixin
  // of `EventHandler` and `WithExpressionOrConstantTrait` just to create the special case of
  // `MessageAction`. Maybe at some point we should separate the concerns of `EventHandler` and
  // actions, so that `EventHandler` just becomes a container for actions.
  def apply(
    index            : Int,
    element          : Element,
    parent           : Option[ElementAnalysis],
    preceding        : Option[ElementAnalysis],
    staticId         : String,
    prefixedId       : String,
    namespaceMapping : NamespaceMapping,
    scope            : Scope,
    containerScope   : Scope,
    isEventHandler   : Boolean
  ): ElementAnalysis = {

    if (isEventHandler)
      EventHandlerBuilder(
        index,
        element,
        parent,
        preceding,
        staticId,
        prefixedId,
        namespaceMapping,
        scope,
        containerScope,
        withChildren             = false,
        withExpressionOrConstant = true
      )
    else
      new ElementAnalysis(
        index,
        element,
        parent,
        preceding,
        staticId,
        prefixedId,
        namespaceMapping,
        scope,
        containerScope
      ) with ActionTrait
        with WithExpressionOrConstantTrait {

        val (expressionOrConstant, _) =
          XFormsStaticElementValue.findElementExpressionOrConstantDirectOrNested(
            outerElem       = element,
            containerPrefix = containerScope.fullPrefix,
            isWithinRepeat  = parent.exists(_.isWithinRepeat),
            acceptHTML      = false,
            makeString      = StaticXPath.makeStringExpression
          )
      }
  }
}
