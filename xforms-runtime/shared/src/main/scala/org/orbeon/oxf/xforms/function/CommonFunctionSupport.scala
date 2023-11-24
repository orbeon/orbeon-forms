package org.orbeon.oxf.xforms.function

import org.orbeon.dom
import org.orbeon.dom.Namespace
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.{FunctionContext, XPath}
import org.orbeon.oxf.xforms.{BindingContext, XFormsContainingDocument}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.function.xxforms.XXFormsLang
import org.orbeon.oxf.xforms.model.{BindNode, XFormsModel}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.expr.{Expression, XPathContext}
import org.orbeon.saxon.om
import org.orbeon.saxon.value.{AtomicValue, QNameValue}
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.runtime.XFormsObject
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.collection.{mutable => m}


trait CommonFunctionSupport {

  case class Context(
    container        : XBLContainer,
    bindingContext   : BindingContext,
    sourceEffectiveId: String,
    modelOpt         : Option[XFormsModel],
    bindNodeOpt      : Option[BindNode] // used only for the `NamespaceMapping` and `XFormsModel.variableResolver`
  ) extends FunctionContext {

    def containingDocument = container.containingDocument

    def sourceEffectiveIdOrThrow: String =
      sourceEffectiveId ensuring (_ ne null, "Source effective id not available for resolution.")

    private var _properties: Option[m.Map[String, Option[String]]] = None
    def properties: Option[m.Map[String, Option[String]]] = _properties

    def setProperty(name: String, value: Option[String]): Unit = {
      if (_properties.isEmpty)
        _properties = Some(m.Map.empty[String, Option[String]])
      _properties foreach (_ += name -> value)
    }

    // TODO: We should just pass a `NamespaceMapping` to the `Context` constructor!
    def namespaceMapping: NamespaceMapping = bindNodeOpt match {
      case Some(bindNode: BindNode) =>
        // Function was called from a bind
        bindNode.parentBind.staticBind.namespaceMapping
      case None if bindingContext.controlElement ne null =>
        // Function was called from a control
        // `controlElement` is mainly used in `BindingContext` to handle repeats and context.
        container.getNamespaceMappings(bindingContext.controlElement)
      case None =>
        // Unclear which cases reach here!
        throw new IllegalArgumentException("cannot find namespace mapping")
    }
  }

  def context: Context =
    XPath.functionContext map (_.asInstanceOf[Context]) orNull

  // This ADT or something like it should be defined somewhere else
  sealed trait QNameType
  case   class UnprefixedName(local: String) extends QNameType
  case   class PrefixedName(prefix: String, local: String) extends QNameType

  def parseQNameToQNameType(lexicalQName: String): QNameType =
    SaxonUtils.parseQName(lexicalQName) match {
      case ("", local)     => UnprefixedName(local)
      case (prefix, local) => PrefixedName(prefix, local)
    }

  def qNameFromQNameValue(value: QNameValue): dom.QName =
    dom.QName(
      value.getLocalName,
      Namespace(value.getPrefix, value.getNamespaceURI)
    )

  def qNameFromStringValue(value: String, bindingContext: BindingContext): dom.QName =
    parseQNameToQNameType(value) match {
      case PrefixedName(prefix, local) =>

        val qNameURI =
          context.namespaceMapping.mapping.getOrElse(
            prefix,
            throw new OXFException(s"Namespace prefix not in scope for QName `$value`")
          )

        dom.QName(local, Namespace(prefix, qNameURI))
      case UnprefixedName(local) =>
        dom.QName(local)
    }

  def getSourceEffectiveId(implicit xfc: Context): String =
    xfc.sourceEffectiveId ensuring (_ ne null, "Source effective id not available for resolution.")

  def elementAnalysisForSource(implicit xfc: Context): Option[ElementAnalysis] = {
    val prefixedId = XFormsId.getPrefixedId(getSourceEffectiveId)
    xfc.container.partAnalysis.findControlAnalysis(prefixedId)
  }

  // Resolve a control by id
  def findControlsByStaticOrAbsoluteId(
    staticOrAbsoluteId : String,
    followIndexes      : Boolean)(implicit
    xpc                : XPathContext,
    xfc                : Context
  ): List[XFormsControl] = {

    val sourceEffectiveId        = getSourceEffectiveId
    val sourcePrefixedId         = XFormsId.getPrefixedId(sourceEffectiveId)
    val resolutionScopeContainer = context.container.findScopeRoot(sourcePrefixedId)

    resolutionScopeContainer.resolveObjectsById(
      sourceEffectiveId,
      staticOrAbsoluteId,
      contextItemOpt = None,
      followIndexes
    ) collect {
      case c: XFormsControl => c
    }
  }

  // Resolve an object by id
  def resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId: String)(implicit xpc: XPathContext, xfc: Context): Option[XFormsObject] =
    context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticOrAbsoluteId)

  def resolveStaticOrAbsoluteId(
    staticIdExpr : Option[Expression])(implicit
    xpc          : XPathContext,
    xfc          : Context
  ): Option[String] =
    staticIdExpr match {
      case None =>
        // If no argument is supplied, return the closest id (source id)
        Option(getSourceEffectiveId)
      case Some(expr) =>
        // Otherwise resolve the id passed against the source id
        val staticOrAbsoluteId = expr.evaluateAsString(xpc).toString
        resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId) map
          (_.getEffectiveId)
    }

  def sourceScope(implicit xpc: XPathContext, xfc: Context): Scope =
    xfc.container.partAnalysis.scopeForPrefixedId(XFormsId.getPrefixedId(getSourceEffectiveId))

  // Resolve a relevant control by id
  def findRelevantControls(
    staticOrAbsoluteId : String,
    followIndexes      : Boolean)(implicit
    xpc                : XPathContext,
    xfc                : Context
  ): List[XFormsControl] =
    findControlsByStaticOrAbsoluteId(staticOrAbsoluteId, followIndexes) collect
      { case control: XFormsControl if control.isRelevant => control }

  def currentLangOpt(implicit xpc: XPathContext, xfc: Context): Option[String] =
    elementAnalysisForSource flatMap (XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument, _))

  def getContainingDocument(implicit xpc: XPathContext): XFormsContainingDocument =
    Option(context) map (_.container.getContainingDocument) orNull

  def getQNameFromItem(
    evaluatedExpression: om.Item)(implicit
    xfc                : Context
  ): dom.QName =
  evaluatedExpression match {
    case qName: QNameValue =>
      // Directly got a QName so there is no need for namespace resolution
      qNameFromQNameValue(qName)
    case atomic: AtomicValue =>
      // Must resolve prefix if present
      qNameFromStringValue(atomic.getStringValue, xfc.bindingContext)
    case other =>
      throw new OXFException(s"Cannot create QName from non-atomic item of class '${other.getClass.getName}'")
  }

  // Resolve the relevant control by argument expression
  // TODO: Check callers and consider using `relevantControls`.
  def relevantControl(
    staticOrAbsoluteId : String)(implicit
    xpc                : XPathContext,
    xfc                : Context
  ): Option[XFormsControl] =
    findRelevantControls(staticOrAbsoluteId, followIndexes = true).headOption
}
