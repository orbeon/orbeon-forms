/**
 *  Copyright (C) 2014 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

class ErrorSummaryTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("fr:error-summary") {

    it("#1689: show errors when placed before observed") {
      withTestExternalContext { _ =>

        val doc = this setupDocument
          <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
              xmlns:xf="http://www.w3.org/2002/xforms"
              xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
              xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <xh:head>
              <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                <xf:instance>
                  <invalid/>
                </xf:instance>
                <xf:bind ref="." constraint="false()"/>
                <xf:dispatch
                  event="xforms-ready"
                  name="fr-show-relevant-errors"
                  targetid="error-summary"/>
              </xf:model>
            </xh:head>
            <xh:body>
              <fr:error-summary id="error-summary" observer="my-group"/>
              <xf:group id="my-group">
                <xf:input ref="." id="my-input">
                  <xf:alert>alert</xf:alert>
                </xf:input>
              </xf:group>
            </xh:body>
          </xh:html>

        withContainingDocument(doc) {
          val errorSummary           = resolveObject[XFormsComponentControl]("error-summary").get
          val stateInstance          = errorSummary.nestedContainerOpt.get.models.head.getInstance("fr-state-instance").documentInfo
          val visibleAlertCountAttr  = stateInstance / * / "visible-counts" /@ "alert"
          val visibleAlertCountValue = visibleAlertCountAttr.headOption.map(_.stringValue).getOrElse("")

          assert(visibleAlertCountValue === "1")
        }
      }
    }
  }
}
