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

import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.scaxon.SimplePath._
import org.scalatest.junit.AssertionsForJUnit

class ErrorSummaryTest extends DocumentTestBase with AssertionsForJUnit {

  // Test for issue #1689, where errors were not if the error summary was placed before what it was observing
  @Test def onTop(): Unit = {
    val doc = this setupDocument
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:ev="http://www.w3.org/2001/xml-events"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xh:head>
          <xf:model>
            <xf:instance>
              <invalid/>
            </xf:instance>
            <xf:bind ref="." constraint="false()"/>
            <xf:dispatch ev:event="xforms-ready" name="fr-visit-all" targetid="error-summary"/>
          </xf:model>
        </xh:head>
        <xh:body>
          <fr:error-summary id="error-summary" observer="output"/>
          <xf:input ref="." id="output">
            <xf:alert>alert</xf:alert>
          </xf:input>
        </xh:body>
      </xh:html>

    withContainingDocument(doc) {
      val errorSummary = resolveObject[XFormsComponentControl]("error-summary").get
      val stateInstance = errorSummary.nestedContainer.models.head.getInstance("fr-state-instance").documentInfo
      val visibleAlertCountAttr = stateInstance / "state" / "visible-counts" /@ "alert"
      val visibleAlertCountValue = visibleAlertCountAttr.headOption.map(_.stringValue).getOrElse("")
      assert(visibleAlertCountValue === "1")
    }
  }
}
