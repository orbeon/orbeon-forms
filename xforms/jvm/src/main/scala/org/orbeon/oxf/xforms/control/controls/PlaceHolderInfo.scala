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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl

case class PlaceHolderInfo(isLabelPlaceholder: Boolean, value: String)

object PlaceHolderInfo {

  // Return:
  //
  // - `None` if there is no placeholder
  // - `Some("")` if there is a placeholder for a non-concrete control
  // - `Some(placeHolderValue)` otherwise
  def placeHolderValueOpt(
    containingDocument : XFormsContainingDocument,
    control            : XFormsControl
  ): Option[PlaceHolderInfo] = {

    val elementAnalysis = control.staticControl

    val isLabelPlaceholder = LHHAAnalysis.hasLHHAPlaceholder(elementAnalysis, "label")
    val isHintPlaceholder  = ! isLabelPlaceholder && LHHAAnalysis.hasLHHAPlaceholder(elementAnalysis, "hint")

    (isLabelPlaceholder || isHintPlaceholder) option {
      val placeholderValue =
        if (control.isRelevant) {
          if (isLabelPlaceholder)
            control.getLabel
          else
            control.getHint
        } else {
          ""
        }

      PlaceHolderInfo(isLabelPlaceholder, placeholderValue)
    }
  }

}