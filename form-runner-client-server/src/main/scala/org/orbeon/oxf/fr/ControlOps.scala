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

  val SeparateLabelSuffix: String = "label"

  def labelId(controlName: String): String =
    s"$controlName-$SeparateLabelSuffix"

  // Get the control name based on the control, bind, grid, section or template id
  def controlNameFromIdOpt(controlOrBindId: String): Option[String] =
    nameFromIdWithSuffixesOpt(controlOrBindId, ControlSuffixes)

  def controlNameFromSeparateLabelIdOpt(labelId: String): Option[String] =
    nameFromIdWithSuffixesOpt(labelId, SeparateLabelSuffixes)

  def controlNameFromControlOrLabelIdOpt(id: String): Option[String] =
    controlNameFromIdOpt(id).orElse(controlNameFromSeparateLabelIdOpt(id))

  private def nameFromIdWithSuffixesOpt(id: String, suffixes: Set[String]): Option[String] = {
    val staticId = XFormsId.getStaticIdFromId(id)
    staticId.lastIndexOf('-') match {
      case -1 | 0                                   => None
      case i if suffixes(staticId.substring(i + 1)) => Some(staticId.substring(0, i))
      case _                                        => None
    }
  }

  private val ControlSuffixes = Set(
    "control",
    "bind",
    "grid",
    "section",
    "template",
    "repeat" // for legacy FB
  )

  private val SeparateLabelSuffixes = Set(SeparateLabelSuffix)
}
