/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.fr

import org.orbeon.xforms._
import org.scalajs.dom
import org.scalatest.funspec.AnyFunSpec

import scala.scalajs.js

class ClientUserAgentAndJQueryTest extends AnyFunSpec {

  describe("The environment's user agent string") {
    val userAgent = dom.window.navigator.userAgent
    it("must be the Node.js/jsdom one") {
      assert(userAgent contains "jsdom")
    }
  }

  describe("jQuery") {
    it ("must be in scope") {
      assert(! js.isUndefined($))
    }
    it ("must match the version we are expecting") {
      val jQueryVersion = $.fn.asInstanceOf[js.Dynamic].jquery.asInstanceOf[String]
      assert(jQueryVersion.startsWith("3.6"))
    }
  }
}