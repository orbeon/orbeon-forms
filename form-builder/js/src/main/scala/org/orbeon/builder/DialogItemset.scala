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

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.scalajs.dom


object DialogItemset {

  locally {
    val LabelInputSelector = ".xforms-dialog.fb-dialog-itemsets .fb-itemset-label-input"

    def suggestValueFromLabel(label: String): Option[String] =
      label.trimAllToOpt map (_.replaceAll("""\s+""", "-").toLowerCase)

    // Automatically set a corresponding value when the user changes a label
    dom.document.addEventListener("change", (event: dom.Event) =>
      event.targetT.closestOpt(LabelInputSelector).foreach { labelInput =>

        val valueXFormsInput =
          labelInput.closestT(".fr-grid-tr").querySelectorT(".fb-itemset-value-input")

        if (DocumentAPI.getValue(valueXFormsInput).toOption.exists(_.isAllBlank)) {
          DocumentAPI
            .getValue(labelInput)
            .toOption
            .flatMap(suggestValueFromLabel)
            .foreach { suggestedValue =>
              DocumentAPI.setValue(valueXFormsInput, suggestedValue)
            }
        }
      }
    )
  }
}
