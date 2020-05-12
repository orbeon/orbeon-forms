/**
 * Copyright (C) 2007 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.xforms.analysis.controls.LHHA

// Mix in this trait to prevent the control from trying to handle LHHA
trait NoLHHATrait extends ControlLHHASupport {

  self: XFormsControl =>

  override def markLHHADirty()                     = ()
  override def forceDirtyAlert()                   = ()
  override def updateLHHACopy(copy: XFormsControl) = ()
  override def lhhaProperty(lhhaType: LHHA)        = LHHASupport.NullLHHA
  override def compareLHHA(other: XFormsControl)   = true
}
