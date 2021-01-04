package org.orbeon.oxf.xforms.library

import cats.syntax.option._
import org.orbeon.io.CharsetNames.Iso88591
import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.XFormsFunction.{currentLocale, relevantControl, resolveOrFindByStaticOrAbsoluteId}
import org.orbeon.oxf.xforms.model.{InstanceData, XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.om.SequenceTool
import org.orbeon.xforms.XFormsId

import java.text.MessageFormat
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
//
//    Fun("repeat-position", classOf[XXFormsRepeatPosition], op = 0, min = 0, INTEGER, EXACTLY_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
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
//
//    Fun("binding-context", classOf[XXFormsBindingContext], op = 0, min = 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
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
  ): Option[om.NodeInfo] = {
    val r = instanceId.trimAllToOpt flatMap (XFormsInstance.findInAncestorScopes(xfc.container, _))
//    println(s"xxx result of `xxf:instance('$instanceId')` is ${r map (_.getDisplayName)}")
    r
  }

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

  //
//    Fun("itemset", classOf[XXFormsItemset], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )

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
  def r(resourceKey: String, instanceArgumentOpt: Option[String] = None, templateParams: om.Item = null): Option[String] = {

    // XXX TODO: implement
    s"""[TODO: implement `xxf:r('$resourceKey')`]""".some

//
//
//    Fun("r", classOf[XXFormsResource], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE),
//      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE)
//    )
  }

  //    Fun("resource-elements", classOf[XXFormsResourceElem], op = 0, min = 1, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE),
//      Arg(STRING, EXACTLY_ONE)
//    )
//

  @XPathFunction
  def pendingUploads(): Int =
    XFormsFunction.context.containingDocument.countPendingUploads

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

//    Fun("focusable", classOf[XXFormsFocusable], op = 0, min = 1, BOOLEAN, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING,  EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    Fun("absolute-id", classOf[XXFormsAbsoluteId], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
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
  private def exformsMipFunction(items: Iterable[om.Item], operation: Int)(implicit xpc: XPathContext): Boolean = {

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
  def getRequestHeader(name: String, encoding: String = Iso88591): Iterable[String] = ???
//    GetRequestHeader.getAndDecodeHeader(
//      name     = name,
//      encoding = stringArgumentOpt(1),
//      getter   = containingDocument.getRequestHeaders.get
//    )

  @XPathFunction
  def getRequestParameter(name: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[String] =
    xfc.containingDocument.getRequestParameters.getOrElse(name, Nil)

  //
//    Fun(ExcludedDatesValidation.PropertyName, classOf[ExcludedDatesValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
//      Arg(DATE, ALLOWS_ZERO_OR_MORE)
//    )
}
