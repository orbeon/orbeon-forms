package org.orbeon.oxf.xforms.function

import org.orbeon.dom
import org.orbeon.dom.Namespace
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.{FunctionContext, XPath}
import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xforms.model.{BindNode, XFormsModel}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.value.QNameValue
import org.orbeon.xml.NamespaceMapping

import scala.collection.{mutable => m}


trait CommonFunctionSupport {

  case class Context(
    container         : XBLContainer,
    bindingContext    : BindingContext,
    sourceEffectiveId : String,
    modelOpt          : Option[XFormsModel],
    data              : Any // 2023-01-21: `data` can only be `null` or a `BindNode`, and used only for the `NamespaceMapping`
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
    def namespaceMapping: NamespaceMapping = data match {
      case Some(bindNode: BindNode) =>
        // Function was called from a bind
        bindNode.parentBind.staticBind.namespaceMapping
      case _ if bindingContext.controlElement ne null =>
        // Function was called from a control
        // `controlElement` is mainly used in `BindingContext` to handle repeats and context.
        container.getNamespaceMappings(bindingContext.controlElement)
      case _ =>
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
}
