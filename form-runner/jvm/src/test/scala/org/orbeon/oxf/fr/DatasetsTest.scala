/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.scalatest.FunSpecLike

class DatasetsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormRunnerSupport
     with XFormsSupport {

  describe("Form with datasets") {

    it("must call services and actions and return expected dataset values") {

      val (processorService, docOpt, _) =
        runFormRunner("tests", "datasets", "new", document = "", noscript = false, initialize = true)

      withFormRunnerDocument(processorService, docOpt.get) {

        val weatherControl             = resolveObject[XFormsValueControl]("weather-control").get
        val activityControl            = resolveObject[XFormsValueControl]("activity-control").get
        val weatherFromDatasetControl  = resolveObject[XFormsValueControl]("weather-from-dataset-control").get
        val activityFromDatasetControl = resolveObject[XFormsValueControl]("activity-from-dataset-control").get

        assert("sunny"  === weatherControl.getValue)
        assert("hiking" === activityControl.getValue)
        assert("sunny"  === weatherFromDatasetControl.getValue)
        assert("hiking" === activityFromDatasetControl.getValue)


      }
    }
  }
}
