/**
  * Copyright (C) 2018 Orbeon, Inc.
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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.scalatest.FunSpecLike

class ActionsFormat20182Test
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormRunnerSupport {

  describe("Form Runner actions in 2018.2 format") {

    val (processorService, docOpt, _) =
      runFormRunner("tests", "actions-format-20182", "new", document = "", initialize = true)

    val doc = docOpt.get

    it("must pass all service result checks") {
      withTestExternalContext { _ ⇒
        withFormRunnerDocument(processorService, doc) {
          // TODO
        }
      }
    }
  }

}