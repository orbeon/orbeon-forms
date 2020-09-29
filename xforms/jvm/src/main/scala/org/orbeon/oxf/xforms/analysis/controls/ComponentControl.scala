package org.orbeon.oxf.xforms.analysis.controls

import cats.syntax.option._
import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.{AbstractBinding, CommonBinding, ConcreteBinding}
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.xbl.Scope


class ComponentControl(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends ContainerControl(staticStateContext, element, parent, preceding, scope)
     with WithChildrenTrait
     with OptionalSingleNode { // binding could be mandatory, optional, or prohibited

  val hasLazyBinding: Boolean =
    ! part.isTopLevel &&
      element.attributeValueOpt(XFormsNames.XXFORMS_UPDATE_QNAME).contains(XFormsNames.XFORMS_FULL_UPDATE)

  var commonBinding: CommonBinding = null // TODO: pass via constructor

  // The `ConcreteBinding` is mutable in some cases when used from `xxf:dynamic`
  private var _concreteBindingOpt: Option[ConcreteBinding] = None //part.getBinding(prefixedId)

  def hasConcreteBinding: Boolean                 = _concreteBindingOpt.isDefined
  def bindingOpt        : Option[ConcreteBinding] = _concreteBindingOpt ensuring (_.isDefined || ! part.isTopLevel)
  def bindingOrThrow    : ConcreteBinding         = _concreteBindingOpt getOrElse (throw new IllegalStateException)

  def setConcreteBinding(concreteBinding: ConcreteBinding): Unit = {
    assert(! hasConcreteBinding)
    _concreteBindingOpt = concreteBinding.some
  }

  // Remove the component's binding
  def removeConcreteBinding(): Unit = {

    assert(hasConcreteBinding)

    bindingOpt foreach { binding =>
      // Remove all descendants only, keeping the current control
      part.deindexTree(this, self = false)

      part.deregisterScope(binding.innerScope)
      part.removeBinding(prefixedId)

      _concreteBindingOpt = None
    }
  }

  // Only support binding if the control defines it has a binding
  override def hasBinding: Boolean = commonBinding.modeBinding && super.hasBinding

  // Leave as 'def' as the binding can, in theory, mutate
  override protected def externalEventsDef = super.externalEventsDef ++ commonBinding.allowedExternalEvents
  override def externalEvents              = externalEventsDef
}

trait ValueComponentTrait extends ComponentControl with ValueTrait with FormatTrait {
  override def format  : Option[String] = commonBinding.formatOpt
  override val unformat: Option[String] = None
}
