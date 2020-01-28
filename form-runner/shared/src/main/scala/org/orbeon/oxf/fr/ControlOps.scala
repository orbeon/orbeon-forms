/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr

import org.orbeon.xforms.XFormsId


object ControlOps {

  // Get the control name based on the control, bind, grid, section or template id
  def controlNameFromIdOpt(controlOrBindId: String): Option[String] =
    XFormsId.getStaticIdFromId(controlOrBindId) match {
      case ControlName(name, _) => Some(name)
      case _                    => None
    }

  private val ControlName = """(.+)-(control|bind|grid|section|template|repeat)""".r // `repeat` is for legacy FB
}
