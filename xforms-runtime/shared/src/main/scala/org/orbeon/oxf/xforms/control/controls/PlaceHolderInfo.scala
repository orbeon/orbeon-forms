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
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.EventCollector


// TODO: Move to handlers as it's only used there
case class PlaceHolderInfo(isLabelPlaceholder: Boolean, value: String)

object PlaceHolderInfo {

  // Return:
  //
  // - `None` if there is no placeholder
  // - `Some("")` if there is a placeholder for a non-concrete control
  // - `Some(placeHolderValue)` otherwise
  def placeHolderValueOpt(
    staticControl : ElementAnalysis,
    control       : XFormsControl
  ): Option[PlaceHolderInfo] = {

    staticControl match {
      case lhhaSupport: StaticLHHASupport =>

        val isLabelPlaceholder = lhhaSupport.hasLHHAPlaceholder(LHHA.Label)
        val isHintPlaceholder  = ! isLabelPlaceholder && lhhaSupport.hasLHHAPlaceholder(LHHA.Hint)

        (isLabelPlaceholder || isHintPlaceholder) option {

          val placeholderValue =
            if (control.isRelevant) {
              if (isLabelPlaceholder)
                control.getLabel(EventCollector.Throw)
              else
                control.getHint(EventCollector.Throw)
            } else {
              ""
            }

          PlaceHolderInfo(isLabelPlaceholder, placeholderValue)
        }
      case _ =>
        None
    }
  }
}