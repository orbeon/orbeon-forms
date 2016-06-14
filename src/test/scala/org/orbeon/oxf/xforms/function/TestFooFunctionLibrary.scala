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
package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary}
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue

object TestFooFunctionLibrary extends OrbeonFunctionLibrary {

  def instance = this

  Namespace("http://orbeon.org/oxf/xml/foo") {
    Fun("echo", classOf[FooBarFunction], op = 0, min = 0, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )
  }

  class FooBarFunction extends FunctionSupport {
    override def evaluateItem(xpathContext: XPathContext): StringValue =
      stringArgument(0)(xpathContext)
  }
}
