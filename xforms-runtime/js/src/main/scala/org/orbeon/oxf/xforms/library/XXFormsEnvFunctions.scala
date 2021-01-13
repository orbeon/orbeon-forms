package org.orbeon.oxf.xforms.library

import cats.syntax.option._
import org.orbeon.io.CharsetNames
import org.orbeon.io.CharsetNames.Iso88591
import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, XPath}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.XFormsFunction.{elementAnalysisForSource, relevantControl, resolveOrFindByStaticOrAbsoluteId}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsLang
import org.orbeon.oxf.xforms.function.xxforms.XXFormsResourceSupport.{findResourceElementForLang, pathFromTokens, splitResourceName}
import org.orbeon.oxf.xforms.itemset.ItemsetSupport
import org.orbeon.oxf.xforms.model.{InstanceData, XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.{OrbeonFunctionLibrary, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.value.StringValue
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable._

import scala.jdk.CollectionConverters._


trait XXFormsEnvFunctions extends OrbeonFunctionLibrary {

  // NOTE: This is deprecated and just points to the event() function.
//    Fun("event", classOf[Event], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("cases", classOf[XXFormsCases], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("repeat-current", classOf[XXFormsRepeatCurrent], op = 0, min = 0, Type.NODE_TYPE, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def repeatPosition(repeatId: String = null)(implicit xfc: XFormsFunction.Context): Int =
    xfc.bindingContext.enclosingRepeatIterationBindingContext(Option(repeatId)).position

//    Fun("context", classOf[XXFormsContext], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("repeat-items", classOf[XXFormsRepeatItems], op = 0, min = 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    // Backward compatibility, use repeat-items() instead
//    Fun("repeat-nodeset", classOf[XXFormsRepeatItems], op = 0, min = 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("evaluate-bind-property", classOf[XXFormsEvaluateBindProperty], op = 0, min = 2, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(ANY_ATOMIC, EXACTLY_ONE) // QName or String
//    )
//
//    Fun("valid", classOf[XXFormsValid], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(BOOLEAN, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    Fun("type", classOf[XXFormsType], op = 0, min = 0, QNAME, ALLOWS_ZERO_OR_MORE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
//    )
//
//    Fun("custom-mip", classOf[XXFormsCustomMIP], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("invalid-binds", classOf[XXFormsInvalidBinds], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
//    )
//
//    Fun("if", classOf[If], op = 0, min = 3, STRING, EXACTLY_ONE,
//      Arg(BOOLEAN, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("binding", classOf[XXFormsBinding], op = 0, min = 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def bindingContext(staticOrAbsoluteId : String)(implicit xpc: XPathContext): Option[om.Item] =
    relevantControl(staticOrAbsoluteId) flatMap (_.contextForBinding)

//    Fun("is-control-relevant", classOf[XXFormsIsControlRelevant], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("is-control-readonly", classOf[XXFormsIsControlReadonly], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("is-control-required", classOf[XXFormsIsControlRequired], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("is-control-valid", classOf[XXFormsIsControlValid], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("value", classOf[XXFormsValue], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    Fun("formatted-value", classOf[XXFormsFormattedValue], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    Fun("avt-value", classOf[XXFormsAVTValue], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//

  @XPathFunction
  def componentContext()(implicit xfc: XFormsFunction.Context): Iterable[om.Item] =
    for {
      componentControl <- xfc.container.associatedControlOpt.toIterable
      bindingContext   <- Option(componentControl.bindingContext.parent).toIterable
      item             <- bindingContext.nodeset.asScala
    } yield
      item

//    Fun("component-param-value", classOf[XXFormsComponentParam], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
//        Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def instance(
    instanceId : String)(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): Option[om.NodeInfo] =
    instanceId.trimAllToOpt flatMap (XFormsInstance.findInAncestorScopes(xfc.container, _))

  //  Fun("index", classOf[XXFormsIndex], op = 0, min = 0, INTEGER, EXACTLY_ONE,
//      Arg(STRING, ALLOWS_ZERO_OR_ONE)
//    )

  @XPathFunction
  def listModels()(implicit xfc: XFormsFunction.Context): Iterator[String] =
    for {
      model       <- xfc.containingDocument.allModels
      effectiveId = model.getEffectiveId
      absoluteId  = XFormsId.effectiveIdToAbsoluteId(effectiveId)
    } yield
      absoluteId

  @XPathFunction
  def listInstances(modelId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterator[String] = {

    val modelOpt =
      resolveOrFindByStaticOrAbsoluteId(modelId) collect { case model: XFormsModel => model }

    for {
      model       <- modelOpt.iterator
      instance    <- model.instancesIterator
      effectiveId = instance.getEffectiveId
      absoluteId  = XFormsId.effectiveIdToAbsoluteId(effectiveId)
    } yield
      absoluteId
  }

  //
//    Fun("list-variables", classOf[XXFormsListVariables], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def getVariable(modelEffectiveId: String, variableName: String)(implicit xfc: XFormsFunction.Context): Iterable[om.Item] = {
    xfc.containingDocument.getObjectByEffectiveId(modelEffectiveId) match {
      case model: XFormsModel =>
        val v = model.getTopLevelVariables(variableName)
        XPath.Logger.debug(s"zzz getVariable: $modelEffectiveId, $variableName, $v")
        v.asIterable().asScala
      case _                  => Nil
    }
  }

  @XPathFunction
  def itemset(controlId: String, format: String, selected: Boolean = false)(implicit xpc: XPathContext): Option[om.Item] =
    for {
      control        <- relevantControl(controlId)
      valueControl   <- control.narrowTo[XFormsValueControl]
      select1Control <- ItemsetSupport.findSelectionControl(valueControl)
      itemset        = select1Control.getItemset
    } yield {

      val controlValueForSelection =
        if (selected)
          select1Control.boundItemOpt map select1Control.getCurrentItemValueFromData map { v =>
            (v, SaxonUtils.attCompare(select1Control.boundNodeOpt, _))
          }
        else
          None

      if (format == "json") {
        // Return a string
        StringValue.makeStringValue(
          ItemsetSupport.asJSON(
            itemset                    = itemset,
            controlValue               = controlValueForSelection,
            encode                     = select1Control.mustEncodeValues,
            excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy,
            locationData               = control.getLocationData
          )
        )
      } else {
        // Return an XML document
        ItemsetSupport.asXML(
          itemset                    = itemset,
          configuration              = xpc.getConfiguration,
          controlValue               = controlValueForSelection,
          excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy,
          locationData               = control.getLocationData
        )
      }
    }

  @XPathFunction
  def formatMessage(template: String, args: Iterable[om.Item])(implicit xpc: XPathContext): String = {
    // TODO: `MessageFormat` is not supported by Scala.js as of now (2020-12-29).
    //
    s"[TODO] $template"
//    new MessageFormat(template, currentLocale)
//      .format(args map SequenceTool.convertToJava toArray)
  }

  //    Fun("lang", classOf[XXFormsLang], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)
//

  // TODO: last arg is`map(*)`
  @XPathFunction
  def r(
    resourceKey         : String,
    instanceArgumentOpt : Option[String] = None,
    templateParams      : om.Item = null)(implicit
    xpc                 : XPathContext,
    xfc                 : XFormsFunction.Context
  ): Option[String] = {

    // XXX TODO
//    val templateParamsOpt   = itemsArgumentOpt(2) map (it => MapFunctions.collectMapValues(it).next())
    val templateParamsOpt: Option[_]   = None

    def findInstance = instanceArgumentOpt match {
      case Some(instanceName) => resolveOrFindByStaticOrAbsoluteId(instanceName)
      case None               => resolveOrFindByStaticOrAbsoluteId("orbeon-resources") orElse resolveOrFindByStaticOrAbsoluteId("fr-form-resources")
    }

    def findResourcesElement = findInstance collect { case instance: XFormsInstance => instance.rootElement }

    def processResourceString(resourceOrTemplate: String): String =
      templateParamsOpt match {
        case Some(params) =>
//
//          val javaNamedParamsIt = params.iterator map {
//            case (key, value) =>
//              val javaParamOpt = asScalaIterator(Value.asIterator(value)) map Value.convertToJava nextOption()
//              key.getStringValue -> javaParamOpt.orNull
//          }
//
//          ProcessTemplate.processTemplateWithNames(resourceOrTemplate, javaNamedParamsIt.to(List), currentLocale)

          resourceOrTemplate

        case None =>
          resourceOrTemplate
      }

      for {
        elementAnalysis <- elementAnalysisForSource
        resources       <- findResourcesElement
        requestedLang   <- XXFormsLang.resolveXMLangHandleAVTs(xfc.containingDocument, elementAnalysis)
        resourceRoot    <- findResourceElementForLang(resources, requestedLang)
        leaf            <- pathFromTokens(resourceRoot, splitResourceName(resourceKey)).headOption
      } yield
        processResourceString(leaf.getStringValue)
  }

  //    Fun("resource-elements", classOf[XXFormsResourceElem], op = 0, min = 1, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//

  @XPathFunction
  def pendingUploads(): Int =
    XFormsFunction.context.containingDocument.countPendingUploads

  @XPathFunction
  def documentId()(implicit xfc: XFormsFunction.Context): String =
  xfc.containingDocument.uuid

  @XPathFunction
  def label(controlId: String)(implicit xpc: XPathContext): Option[String] =
    relevantControl(controlId) map (_.getLabel)

  @XPathFunction
  def help(controlId: String)(implicit xpc: XPathContext): Option[String] =
    relevantControl(controlId) map (_.getHelp)

  @XPathFunction
  def hint(controlId: String)(implicit xpc: XPathContext): Option[String] =
    relevantControl(controlId) map (_.getHint)

  @XPathFunction
  def alert(controlId: String)(implicit xpc: XPathContext): Option[String] =
    relevantControl(controlId) map (_.getAlert)

  @XPathFunction
  def visited(controlId: String)(implicit xpc: XPathContext): Option[Boolean] =
    relevantControl(controlId) map (_.visited)

  @XPathFunction
  def focusable(controlId: String, toggle: Boolean = true)(implicit xpc: XPathContext): Boolean = {

    val it =
      if (toggle)
        relevantControl(controlId).iterator flatMap (_.directlyFocusableControlsMaybeWithToggle)
      else
        relevantControl(controlId).iterator flatMap (_.directlyFocusableControls)

    it.nonEmpty
  }

  @XPathFunction
  def absoluteId(staticOrAbsoluteId: String)(implicit xpc: XPathContext): Option[String] =
    resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId) map (_.getEffectiveId) map XFormsId.effectiveIdToAbsoluteId

//
//    Fun("client-id", classOf[XXFormsClientId], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("control-element", classOf[XXFormsControlElement], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("extract-document", classOf[XXFormsExtractDocument], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(Type.NODE_TYPE, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    // RFE: Support XSLT 2.0-features such as multiple sort keys
//    Fun("sort", classOf[XXFormsSort], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(Type.ITEM_TYPE, EXACTLY_ONE),
//      Arg(STRING, ALLOWS_ZERO_OR_ONE),
//      Arg(STRING, ALLOWS_ZERO_OR_ONE),
//      Arg(STRING, ALLOWS_ZERO_OR_ONE)
//    )
//
//    // NOTE: also from exforms
//    Fun("relevant", classOf[EXFormsMIP], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
//    )

  // NOTES:
  //
  // - Also in exforms.
  // - This can take more than one item, but only the first one is considered.
  // - This is different from the XForms 2.0 functions.
  //
  def exformsMipFunction(items: Iterable[om.Item], operation: Int)(implicit xpc: XPathContext): Boolean = {

    val resolvedItems =
      items match {
        // NOTE: Use `null` to differentiate between the argument being statically present or not. Not great?
        case null  => Option(xpc.getContextItem).toList
        case i     => i
      }

    // "If the argument is omitted, it defaults to a node-set with the context node as its only member."
    val itemOption = resolvedItems.headOption

    def getOrDefault(item: om.Item, getFromNode: om.NodeInfo => Boolean, default: Boolean) = item match {
      case nodeInfo: om.NodeInfo => getFromNode(nodeInfo)
      case _                     => default
    }

    def get(item: om.Item) = operation match {
      case 0 => getOrDefault(item, InstanceData.getInheritedRelevant, default = true)
      case 1 => getOrDefault(item, InstanceData.getInheritedReadonly, default = true)
      case 2 => getOrDefault(item, InstanceData.getRequired,          default = false)
    }

    // "If the node-set is empty then the function returns false."
    itemOption exists (item => get(item))
  }

  @XPathFunction
  def relevant(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    exformsMipFunction(items, 0)

  @XPathFunction
  def readonly(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    exformsMipFunction(items, 1)

  @XPathFunction
  def required(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    exformsMipFunction(items, 2)

//
//    // Now available in XForms 2.0
//    Fun("bind", classOf[Bind], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    // Validation functions
//    Fun("max-length", classOf[MaxLengthValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
//    )
//
//    Fun("min-length", classOf[MinLengthValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
//    )
//
//    Fun("non-negative", classOf[NonNegativeValidation], op = 0, min = 0, BOOLEAN, EXACTLY_ONE)
//    Fun("negative",     classOf[NegativeValidation],    op = 0, min = 0, BOOLEAN, EXACTLY_ONE)
//    Fun("non-positive", classOf[NonPositiveValidation], op = 0, min = 0, BOOLEAN, EXACTLY_ONE)
//    Fun("positive",     classOf[PositiveValidation],    op = 0, min = 0, BOOLEAN, EXACTLY_ONE)
//
//    Fun("fraction-digits", classOf[MaxFractionDigitsValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
//    )
//
//    Fun(ValidationFunctionNames.UploadMaxSize, classOf[UploadMaxSizeValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
//    )
//
//    Fun(ValidationFunctionNames.UploadMediatypes, classOf[UploadMediatypesValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(STRING, ALLOWS_ZERO_OR_ONE)
//    )

  @XPathFunction
  def evaluateAvt(avt: String): Option[String] = {
//    val (avtExpression, newXPathContext) = prepareExpressionSaxonNoPool(xpathContext, argument(0), isAVT = true)
//    avtExpression.iterate(newXPathContext)
    s"""[TODO: evaluateAvt] $avt""".some
  }

  //
//    Fun("form-urlencode", classOf[XXFormsFormURLEncode], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(NODE_TYPE, EXACTLY_ONE)
//    )
//
//    Fun("get-request-method", classOf[GetRequestMethodTryXFormsDocument], op = 0, 0, STRING, ALLOWS_ONE)
//    Fun("get-request-context-path", classOf[GetRequestContextPathTryXFormsDocument], op = 0, 0, STRING, ALLOWS_ONE)
//
//    Fun("get-request-path", classOf[GetRequestPathTryXFormsDocument], op = 0, 0, STRING, ALLOWS_ONE)
//
//    Fun("get-request-header", classOf[GetRequestHeaderTryXFormsDocument], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )

  @XPathFunction
  def getRequestHeader(name: String, encoding: String = Iso88591): Iterable[String] = {

    import CharsetNames._

    val decode: String => String =
      encoding.toUpperCase match {
        case Iso88591 => identity
        case Utf8     => (s: String) => new String(s.getBytes(Iso88591), Utf8)
        case other    => throw new IllegalArgumentException(s"invalid `$$encoding` argument `$other`")
      }

    Option(CoreCrossPlatformSupport.externalContext.getRequest.getHeaderValuesMap.get(name)).toList flatMap
      (_.toList) map decode
  }

  @XPathFunction
  def getRequestParameter(name: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[String] =
    xfc.containingDocument.getRequestParameters.getOrElse(name, Nil)

  //
//    Fun(ExcludedDatesValidation.PropertyName, classOf[ExcludedDatesValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(DATE, ALLOWS_ZERO_OR_MORE)
//    )
}
