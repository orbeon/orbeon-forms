package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.Scope

class ComponentControl(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends ContainerControl(staticStateContext, element, parent, preceding, scope)
  with ShadowChildrenBuilder
  with OptionalSingleNode {   // binding could be mandatory, optional, or prohibited

  // Binding at the time the component is created
  private var _binding = part.xblBindings.getBinding(prefixedId) orElse (throw new IllegalStateException)
  def binding = _binding.get

  // Remove the component's binding
  def removeBinding(): Unit = {

    assert(! part.isTopLevel)

    // Remove all descendants only, keeping the current control
    part.deindexTree(this, self = false)

    part.deregisterScope(binding.innerScope)
    part.xblBindings.removeBinding(prefixedId)

    _binding = None
  }

  // Set the component's binding
  def setBinding(elementInSource: Element): Unit = {
    assert(! part.isTopLevel)

    _binding = part.xblBindings.processElementIfNeeded(elementInSource, prefixedId, locationData, scope)
  }

  // Only support binding if the control defines it has a binding
  override def hasBinding = binding.abstractBinding.modeBinding && super.hasBinding

  // If control does not have an XPath binding, return one anyway so that controls w/o their own binding also get updated.
  override protected def computeBindingAnalysis =
    if (binding.abstractBinding.modeBinding) super.computeBindingAnalysis else getContextAnalysis

  // Leave as 'def' as the binding can, in theory, mutate
  override protected def externalEventsDef = super.externalEventsDef ++ binding.abstractBinding.allowedExternalEvents
  override def externalEvents              = externalEventsDef
}