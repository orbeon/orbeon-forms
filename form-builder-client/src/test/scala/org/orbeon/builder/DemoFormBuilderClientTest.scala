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

import org.junit.Assert._
import org.junit.Test
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global ⇒ g}

class DemoFormBuilderClientTest {

  @Test def userAgent(): Unit = {
    val userAgent = dom.window.navigator.userAgent

    println(s"Test user agent: $userAgent")

    assertTrue(userAgent contains "Node.js")
  }

  @Test def jQuery(): Unit = {
    val $ = g.$.asInstanceOf[org.scalajs.jquery.JQueryStatic]
    val jQueryVersion = $.fn.asInstanceOf[js.Dynamic].jquery.asInstanceOf[String]

    println(s"jQuery version: $jQueryVersion")

    assertTrue(jQueryVersion.startsWith("1."))
  }

}