/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, ResourceManagerTestBase, XFormsSupport}
import org.scalatest.FunSpecLike

class XFormsResourceServerTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with XFormsSupport {

  describe("The `proxyURI` function") {

    implicit val indentedLogger = ResourceManagerTestBase.newIndentedLogger

    it("must work for an absolute path") {
      withTestExternalContext { _ ⇒
        assert("/xforms-server/dynamic/04fcb2850925c9064012678737bb76216020facf" ===
          XFormsResourceServer.proxyURI("/foo/bar.png", None, None, -1, Map(), Set(), _ ⇒ None))
      }
    }

    it("must work for an absolute URL") {
      withTestExternalContext { _ ⇒
        assert("/xforms-server/dynamic/563ec01cad20b038a8109ba984daac278a350f72" ===
          XFormsResourceServer.proxyURI("http://example.org/foo/bar.png", None, None, -1, Map(), Set(), _ ⇒ None))
      }
    }

  }
}