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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.event.XFormsEventHandler
import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import collection.Seq
import org.orbeon.oxf.xforms.control.{XFormsContainerControl, XFormsControl}


class XFormsActionControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
  extends XFormsControl(container, parent, element, effectiveId) with XFormsEventHandler {

  // Tell the parent about us if the parent is not a container
  Option(parent) foreach {
    case _: XFormsContainerControl ⇒
    case nonContainer ⇒ nonContainer.addChildAction(this)
  }

  // Don't push the actual binding for actions because it's unnecessary at build/refresh time and the binding needs to
  // be re-evaluated when the action runs anyway.
  override def computeBinding(parentContext: BindingContext) = computeBindingCopy(parentContext)

  // Don't build any children, as in the view we don't support event handlers nested within event handlers, and nested
  // actions are evaluated dynamically.
  override def buildChildren(buildTree: (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl], idSuffix: Seq[Int]) = ()
}
