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

import java.io.IOException
import org.orbeon.dom.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsNoSingleNodeContainerControl}
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.events.XXFormsLoadEvent
import org.orbeon.oxf.xforms.xbl.XBLContainer

// Control at the root of the control tree
// NOTE: This is also the root of a dynamic sub-tree, in which case the control is a child of xxf:dynamic
class XXFormsRootControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
  extends XFormsNoSingleNodeContainerControl(container, parent, element, effectiveId) {

  // If we are really at the root, register to ControlTree. This so that the tree is made available during
  // construction to XPath functions like index() or case()
  override def addChild(control: XFormsControl ): Unit = {
    super.addChild(control)

    if (parent eq null) // ne null when child of xxf:dynamic
      containingDocument.controls.getCurrentControlTree.setRoot(this)
  }

  override def performDefaultAction(event: XFormsEvent): Unit = event match {
    case load: XXFormsLoadEvent =>
      // Internal load event for "two-pass load" handling Ajax load in portlet environments (see `XFormsLoadAction`)
      try {
        NetUtils.getExternalContext.getResponse.sendRedirect(load.resource, false, false)
      } catch {
        case e: IOException => throw new ValidationException(e, getLocationData)
      }
    case _ =>
      super.performDefaultAction(event)
  }

  // FIXME: Support refresh events? Simply enabling below doesn't seem to work for enabled/disabled.
  //override def supportsRefreshEvents = true
}
