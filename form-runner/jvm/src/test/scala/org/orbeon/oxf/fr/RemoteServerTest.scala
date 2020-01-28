/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.oxf.fr.FormRunnerHome._
import org.scalatest.funspec.AnyFunSpecLike

import scala.util.Success

class RemoteServerTest extends AnyFunSpecLike {

  describe("Remote server configuration") {

    it("must return `Nil` for an empty JSON array") {
      assert(Nil === tryRemoteServersFromString("[]").get)
    }

    it("must parse valid configurations, trim spaces, and ignore extra properties") {
      assert(
        Success(
          List(
            "Prod 1" -> "http://prod1.acme.org/orbeon",
            "Prod 2" -> "http://prod2.acme.org/orbeon",
            "Prod 3" -> "http://prod2.acme.org/orbeon"
          )
        ) === tryRemoteServersFromString(
          """
          [
            { "label": "Prod 1",   "url": "http://prod1.acme.org/orbeon" },
            { "label": "Prod 2",   "url": "http://prod2.acme.org/orbeon", "foo": 42, "bar": true },
            { "label": " Prod 3 ", "url": " http://prod2.acme.org/orbeon/ " }
          ]
          """
        )
      )
    }

    it("must return a `Failure` for invalid JSON configurations") {

      val Failures = Seq(
        "",
        """[ { "label": "Prod 1", "url": "  " } ]""",
        """[ { "label": "Prod 1", "url": "" } ]""",
        """[ { "label": "Prod 1" } ]""",
        """[ { "label": "  ", "url": "http://prod1.acme.org/orbeon" } ]""",
        """[ { "label": "", "url": "http://prod1.acme.org/orbeon" } ]""",
        """[ { "url": "http://prod1.acme.org/orbeon" } ]""",
        """[ { "label": "Prod 1", "url": "http://prod1.acme.org/orbeon" }, ]"""
      )

      for (json <- Failures)
        assert(tryRemoteServersFromString(json).isFailure)
    }
  }
}
