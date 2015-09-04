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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.oxf.fr.FormRunnerHome._

class RemoteServerTest extends AssertionsForJUnit {

  @Test def remoteServerPropertyParsing(): Unit = {

    assert(Nil === tryRemoteServersFromString("[]").get)

    assert(
      List(
        "Prod 1" → "http://prod1.acme.org/orbeon",
        "Prod 2" → "http://prod2.acme.org/orbeon",
        "Prod 3" → "http://prod2.acme.org/orbeon"
      ) === tryRemoteServersFromString(
        """
        [
          { "label": "Prod 1",   "url": "http://prod1.acme.org/orbeon" },
          { "label": "Prod 2",   "url": "http://prod2.acme.org/orbeon", "foo": 42, "bar": true },
          { "label": " Prod 3 ", "url": " http://prod2.acme.org/orbeon/ " }
        ]
        """
      ).get
    )

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

    for (json ← Failures)
      assert(tryRemoteServersFromString(json).isFailure)
  }
}
