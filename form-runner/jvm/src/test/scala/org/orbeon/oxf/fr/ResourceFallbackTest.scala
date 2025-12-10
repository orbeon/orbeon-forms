/**
  * Copyright (C) 2025 Orbeon, Inc.
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

import org.orbeon.dom.QName
import org.orbeon.oxf.fr.XMLNames.{FR, FRPrefix}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.xforms.XFormsNames
import org.scalatest.funspec.AnyFunSpecLike


class ResourceFallbackTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FormRunnerSupport
     with AnyFunSpecLike {

  describe("Resource fallback for unsupported languages (#7339)") {

    it("Korean form should fall back to English for Form Runner buttons") {

      val (processorService, docOpt, _) =
        runFormRunner("tests", "resource-fallback", "new", initialize = true)

      assert(docOpt.nonEmpty, "Form failed to load")
      val doc = docOpt.get

      val ButtonComponentQNames = Set(
        QName("ladda-button", FRPrefix, FR),
        QName("trigger",      FRPrefix, FR),
      )

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {

          // Find all Form Runner buttons
          val allButtons = ControlsIterator(doc.controls.getCurrentControlTree)
            .collect {
              case control: XFormsSingleNodeControl
                if ButtonComponentQNames(control.staticControl.element.getQName) =>
                control
            }
            .toList
          assert(allButtons.nonEmpty, "At least one button should exist")

          // Get labels from all buttons that have the fr-*-button class
          val buttonLabels = allButtons
            .filter(control =>
              control.extensionAttributeValue(XFormsNames.CLASS_QNAME).exists(classes =>
                classes.splitTo[List]().exists(_.startsWith("fr-")) && classes.splitTo[List]().exists(_.endsWith("-button"))
              )
            )
            .flatMap(_.getLabel(EventCollector.Throw))
          assert(buttonLabels.nonEmpty, "At least one button should have a label")

          // All button labels must be in English
          buttonLabels.foreach { label =>
            val labelIsInEnglish = Set("Summary", "Clear", "PDF", "Save", "Review").exists(label.contains)
            assert(labelIsInEnglish, s"Expected an English label, but got: $label")
          }
        }
      }
    }
  }
}
