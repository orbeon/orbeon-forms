package org.orbeon.oxf.xforms.analysis.controls

import cats.syntax.option._
import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.{AbstractBinding, ConcreteBinding, XBLBindingBuilder}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.XFormsNames


class ComponentControl(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends ContainerControl(staticStateContext, element, parent, preceding, scope)
     with WithChildrenTrait
     with OptionalSingleNode {   // binding could be mandatory, optional, or prohibited

  val hasLazyBinding: Boolean =
    ! part.isTopLevel &&
      element.attributeValueOpt(XFormsNames.XXFORMS_UPDATE_QNAME).contains(XFormsNames.XFORMS_FULL_UPDATE)

  val abstractBinding: AbstractBinding =
    part.metadata.findAbstractBindingByPrefixedId(prefixedId) getOrElse (throw new IllegalStateException)

  // The `ConcreteBinding` is mutable in some cases when used from `xxf:dynamic`
  private var _concreteBindingOpt: Option[ConcreteBinding] = None//part.getBinding(prefixedId)

  def bindingOpt      : Option[ConcreteBinding] = _concreteBindingOpt ensuring (_.isDefined || ! part.isTopLevel)
  def bindingOrThrow  : ConcreteBinding         = _concreteBindingOpt getOrElse (throw new IllegalStateException)

  // Set the component's binding
  // Might not create the binding if the binding does not have a template.
  // Also called indirectly by `XXFormsDynamicControl`.
  def setConcreteBinding(elemInSource: Element): Unit = {

    assert(_concreteBindingOpt.isEmpty)

    XBLBindingBuilder.createConcreteBindingFromElem(
      part,
      abstractBinding,
      elemInSource,
      prefixedId,
      containerScope
    ) foreach { case (newBinding, globalOpt) =>

      globalOpt foreach { global =>
        part.abstractBindingsWithGlobals += abstractBinding
        part.allGlobals += global
      }

      part.addBinding(prefixedId, newBinding)

      _concreteBindingOpt = newBinding.some
    }
  }

  // Remove the component's binding
  def removeConcreteBinding(): Unit = {

    assert(bindingOpt.isDefined)

    bindingOpt foreach { binding =>
      // Remove all descendants only, keeping the current control
      part.deindexTree(this, self = false)

      part.deregisterScope(binding.innerScope)
      part.removeBinding(prefixedId)

      _concreteBindingOpt = None
    }
  }

  // Only support binding if the control defines it has a binding
  override def hasBinding = abstractBinding.modeBinding && super.hasBinding

  // If control does not have an XPath binding, return one anyway so that controls w/o their own binding also get updated.
  override protected def computeBindingAnalysis =
    if (abstractBinding.modeBinding) super.computeBindingAnalysis else getContextAnalysis

  // Leave as 'def' as the binding can, in theory, mutate
  override protected def externalEventsDef = super.externalEventsDef ++ abstractBinding.allowedExternalEvents
  override def externalEvents              = externalEventsDef
}

trait ValueComponentTrait extends ComponentControl with ValueTrait with FormatTrait {
  override val format  : Option[String] = abstractBinding.formatOpt
  override val unformat: Option[String] = None
}
