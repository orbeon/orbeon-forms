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
package org.orbeon.builder

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms._
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js.Dynamic.{global ⇒ g}
import scala.scalajs.js.timers._

object DialogItemset {

  private val LabelInputSelector = ".xforms-dialog.fb-dialog-itemsets .fb-itemset-label-input"

  private def suggestValueFromLabel(label: String): Option[String] =
    label.trimAllToOpt map (_.replaceAll("""\s+""", "-").toLowerCase)

  // Automatically set a corresponding value when the user changes a label
  $(g.window.document).on(
    "change.orbeon",
    LabelInputSelector,
    (event: JQueryEventObject) ⇒ {

      val labelXFormsInput = $(event.target).closest(".fb-itemset-label-input")(0)
      val valueXFormsInput = $(labelXFormsInput).closest(".fr-grid-tr").find(".fb-itemset-value-input")(0)

      if (DocumentAPI.getValue(valueXFormsInput).toOption exists (_.isBlank)) {
        // If user pressed tab, after `change` on the label input, there is a `focus` on the value input,
        // which stores the value as a server value, so if we set the value before the `focus`, the value
        // isn't sent, hence the deferring.
        DocumentAPI.getValue(labelXFormsInput).toOption flatMap suggestValueFromLabel foreach { suggestedValue ⇒
          setTimeout(1) {
            DocumentAPI.setValue(valueXFormsInput, suggestedValue)
          }
        }
      }
    }
  )
}
