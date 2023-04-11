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
import org.scalatest.funspec.AnyFunSpecLike


class XFormsAssetServerTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("The `proxyURI` function") {

    implicit val indentedLogger = ResourceManagerTestBase.newIndentedLogger

    it("must work for an absolute path") {
      withTestExternalContext { _ =>
        assert("/xforms-server/dynamic/73fc31eb6b660322abb1ff7929cff31a909540d4" ===
          XFormsAssetServer.proxyURI("/foo/bar.png", None, None, -1, Map(), Set(), _ => None))
      }
    }

    it("must work for an absolute URL") {
      withTestExternalContext { _ =>
        assert("/xforms-server/dynamic/f4e9ff96622e4257216504cf4457468a912b3ae7" ===
          XFormsAssetServer.proxyURI("http://example.org/foo/bar.png", None, None, -1, Map(), Set(), _ => None))
      }
    }
  }
}