/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunner.{formInstance, persistenceInstance}
import org.orbeon.oxf.fr.{FormRunnerSupport, Names}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport, XMLSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.FunSpecLike

import scala.xml.Elem

class RollbackTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormRunnerSupport
     with XFormsSupport
     with XMLSupport {

  describe("Process rollback") {

    val Expected = List[(String, String, Elem, String)](
      (
        "must rollback to the original value including save status to `clean`",
        s"""
          xf:setvalue(ref = "my-section/my-name", value = "'Sam'")
          then rollback
        """,
        <form>
          <my-section>
            <my-name/>
          </my-section>
        </form>,
        "clean"
      ),
      (
        "must keep changes if there is no rollback and save status must be set to `dirty`",
        s"""
          xf:setvalue(ref = "my-section/my-name", value = "'Sam'")
        """,
        <form>
          <my-section>
            <my-name>Sam</my-name>
          </my-section>
        </form>,
        "dirty"
      )
    )

    for ((desc, process, expected, saveStatus) ← Expected)
      it(desc) {

        val (processorService, docOpt, _) =
          runFormRunner("tests", "process-rollback", "new", document = "", noscript = false, initialize = true)

        setupResourceManagerTestPipelineContext()

        withFormRunnerDocument(processorService, docOpt.get) {

          XFormsAPI.dispatch(
            name       = "my-run-process",
            targetId   = Names.FormModel,
            properties = Map("process" → Some(process))
          )

          assertXMLDocumentsIgnoreNamespacesInScope((expected: NodeInfo).root, formInstance.root)

          assert(saveStatus === (persistenceInstance.rootElement elemValue "data-status"))
        }
      }
  }
}
