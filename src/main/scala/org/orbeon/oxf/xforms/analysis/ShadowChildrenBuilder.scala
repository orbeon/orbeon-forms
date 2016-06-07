/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import controls.{LHHA, ComponentControl}
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.XFormsConstants.{XXBLScope, FOR_QNAME}

trait ShadowChildrenBuilder extends ChildrenBuilderTrait {

  self: ComponentControl ⇒

  // HACK: maybe due to a compiler bug, we cannot access self.element, etc.
  private def component = self

  private def annotateChild(child: Element) = {
    // Inner scope in effect for the component element itself (NOT the shadow tree's scope)
    def innerScope = containerScope

    // Outer scope in effect for the component element itself
    def outerScope =
      if (innerScope.isTopLevelScope)
        innerScope
      else {
        // Search in ancestor parts too
        val controlId = containerScope.fullPrefix.init
        val controlAnalysis = component.part.ancestorOrSelfIterator map (_.getControlAnalysis(controlId)) find (_ ne null) get

        controlAnalysis.scope
      }

    // Children elements have not been annotated earlier (because they are nested within the bound element)
    component.part.xblBindings.annotateSubtreeByElement(
      component.element,  // bound element
      child,              // child tree to annotate
      innerScope,         // handler's inner scope is the same as the component's
      outerScope,         // handler's outer scope is the same as the component's
      if (component.scope == innerScope) XXBLScope.inner else XXBLScope.outer,
      binding.innerScope  // handler is within the current component (this determines the prefix of ids)
    )
  }

  // Directly nested handlers (if enabled)
  private def directlyNestedHandlers =
    if (binding.abstractBinding.modeHandlers)
      Dom4j.elements(component.element) filter
        EventHandlerImpl.isEventHandler map
          annotateChild
    else
      Nil

  // Directly nested LHHA (if enabled)
  private def directlyNestedLHHA =
    if (binding.abstractBinding.modeLHHA)
      Dom4j.elements(component.element) filter
        (e ⇒ LHHA.isLHHA(e) && (e.attribute(FOR_QNAME) eq null)) map
          annotateChild
    else
      Nil

  // Return all the children to consider, including relevant shadow tree elements
  override def findRelevantChildrenElements =
    directlyNestedHandlers ++ directlyNestedLHHA ++ binding.handlers ++ binding.models :+ binding.compactShadowTree.getRootElement map ((_, binding.innerScope))
}