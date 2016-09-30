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
package org.orbeon.builder

import org.scalajs.dom
import org.scalatest.FunSuite

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global ⇒ g}

class DemoFormBuilderClientTest extends FunSuite {

  test("Node.js user agent string") {
    val userAgent = dom.window.navigator.userAgent
    assert(userAgent contains "Node.js")
  }

  test("jQuery is in scope and matches version we are expecting") {
    val $ = g.$.asInstanceOf[org.scalajs.jquery.JQueryStatic]
    val jQueryVersion = $.fn.asInstanceOf[js.Dynamic].jquery.asInstanceOf[String]
    assert(jQueryVersion.startsWith("1."))
  }

}