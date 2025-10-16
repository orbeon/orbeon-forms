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

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.fr.FormRunnerHome.*
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
            RemoteServer(None, "Prod 1".some, "http://prod1.acme.org/orbeon"),
            RemoteServer(None, "Prod 2".some, "http://prod2.acme.org/orbeon"),
            RemoteServer(None, "Prod 3".some, "http://prod2.acme.org/orbeon")
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

    it("must parse valid configurations with name field") {
      assert(
        Success(
          List(
            RemoteServer("prod1".some, "Prod 1".some, "http://prod1.acme.org/orbeon"),
            RemoteServer("prod2".some, "Prod 2".some, "http://prod2.acme.org/orbeon"),
            RemoteServer("prod3".some, "Prod 3".some, "http://prod2.acme.org/orbeon")
          )
        ) === tryRemoteServersFromString(
          """
          [
            { "name": "prod1", "label": "Prod 1", "url": "http://prod1.acme.org/orbeon" },
            { "name": "prod2", "label": "Prod 2", "url": "http://prod2.acme.org/orbeon" },
            { "name": " prod3 ", "label": " Prod 3 ", "url": " http://prod2.acme.org/orbeon/ " }
          ]
          """
        )
      )
    }

    it("must return a `Failure` for invalid JSON configurations") {

      val Failures = Seq(
        "",
        """[ { "name": "prod1", "label": "Prod 1", "url": "  " } ]""",
        """[ { "name": "prod1", "label": "Prod 1", "url": "" } ]""",
        """[ { "name": "prod1", "label": "Prod 1" } ]""",
        """[ { "name": "prod1", "label": "Prod 1", "url": "http://prod1.acme.org/orbeon" }, ]"""
      )

      for (json <- Failures)
        assert(tryRemoteServersFromString(json).isFailure)
    }
  }
}
