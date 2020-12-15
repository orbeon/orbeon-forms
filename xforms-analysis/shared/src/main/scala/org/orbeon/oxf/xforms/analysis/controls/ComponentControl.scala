package org.orbeon.oxf.xforms.analysis.controls

import cats.syntax.option._
import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.{CommonBinding, ConcreteBinding}
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


class ComponentControl(
  index              : Int,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  staticId           : String,
  prefixedId         : String,
  namespaceMapping   : NamespaceMapping,
  scope              : Scope,
  containerScope     : Scope,
  val isTopLevelPart : Boolean
) extends ContainerControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with WithChildrenTrait
     with OptionalSingleNode { // binding could be mandatory, optional, or prohibited

  val hasLazyBinding: Boolean =
    ! isTopLevelPart &&
      element.attributeValueOpt(XFormsNames.XXFORMS_UPDATE_QNAME).contains(XFormsNames.XFORMS_FULL_UPDATE)

  var commonBinding: CommonBinding = null // TODO: pass via constructor
  var rootElem: Element = element // default, can be updated by `xxf:dynamic`

  private var _concreteBindingOpt: Option[ConcreteBinding] = None //part.getBinding(prefixedId)

  def hasConcreteBinding: Boolean                 = _concreteBindingOpt.isDefined
  def bindingOpt        : Option[ConcreteBinding] = _concreteBindingOpt ensuring (_.isDefined || ! isTopLevelPart)
  def bindingOrThrow    : ConcreteBinding         = bindingOpt getOrElse (throw new IllegalStateException)

  def setConcreteBinding(concreteBinding: ConcreteBinding): Unit = {
    assert(! hasConcreteBinding)
    _concreteBindingOpt = concreteBinding.some
  }

  def clearBinding(): Unit =
    _concreteBindingOpt = None

  // Only support binding if the control defines it has a binding
  override def hasBinding: Boolean = commonBinding.modeBinding && super.hasBinding

  // Leave as 'def' as the binding can, in theory, mutate
  override protected def externalEventsDef = super.externalEventsDef ++ commonBinding.allowedExternalEvents
  override def externalEvents              = externalEventsDef
}

trait ValueComponentTrait extends ComponentControl with ValueTrait with FormatTrait {
  override def format  : Option[String] = commonBinding.formatOpt
  override def unformat: Option[String] = None
}
