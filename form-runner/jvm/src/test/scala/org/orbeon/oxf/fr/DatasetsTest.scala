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
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.xforms.Constants
import org.scalatest.funspec.AnyFunSpecLike

class DatasetsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XFormsSupport {

  describe("Form with datasets") {

    it("must call services and actions and return expected dataset values") {

      val (processorService, Some(doc), _) =
        runFormRunner("tests", "datasets", "new", document = "", initialize = true)

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {

          def sectionTemplateResolver(sectionId: String) = {

            val sectionControl  = resolveObject[XFormsComponentControl](sectionId)
            val sectionTemplate = FormRunner.sectionTemplateForSection(sectionControl.get).get

            sectionTemplate.nestedContainerOpt.get -> sectionTemplate.innerRootControl.effectiveId
          }

          for ((container, sourceEffectiveId) <- List(document -> Constants.DocumentId, sectionTemplateResolver("main-section-section-template-control"))) {

            val weatherControl             = resolveObject[XFormsValueControl]("weather-control",               sourceEffectiveId = sourceEffectiveId, container = container).get
            val activityControl            = resolveObject[XFormsValueControl]("activity-control",              sourceEffectiveId = sourceEffectiveId, container = container).get
            val weatherFromDatasetControl  = resolveObject[XFormsValueControl]("weather-from-dataset-control",  sourceEffectiveId = sourceEffectiveId, container = container).get
            val activityFromDatasetControl = resolveObject[XFormsValueControl]("activity-from-dataset-control", sourceEffectiveId = sourceEffectiveId, container = container).get

            assert("sunny"  === weatherControl.getValue(EventCollector.Throw))
            assert("hiking" === activityControl.getValue(EventCollector.Throw))
            assert("sunny"  === weatherFromDatasetControl.getValue(EventCollector.Throw))
            assert("hiking" === activityFromDatasetControl.getValue(EventCollector.Throw))
          }

          // TODO: Uncomment once #3132 is fixed.
          for ((container, sourceEffectiveId) <- List(document -> Constants.DocumentId/*, sectionTemplateResolver("initial-values-section-section-template-control")*/)) {

            val initialValueFromDatasetControl                   = resolveObject[XFormsValueControl]("initial-value-from-dataset-control"                     , container = container).get
            val staticInitialValueNotOverwrittenByDatasetControl = resolveObject[XFormsValueControl]("static-initial-value-not-overwritten-by-dataset-control", container = container).get
            val valueFromAfterDataServiceControl                 = resolveObject[XFormsValueControl]("value-from-after-data-service-control"                  , container = container).get

            assert("42" === initialValueFromDatasetControl.getValue(EventCollector.Throw))
            assert(""   === staticInitialValueNotOverwrittenByDatasetControl.getValue(EventCollector.Throw))
            assert("43" === valueFromAfterDataServiceControl.getValue(EventCollector.Throw))
          }
        }
      }
    }
  }
}
