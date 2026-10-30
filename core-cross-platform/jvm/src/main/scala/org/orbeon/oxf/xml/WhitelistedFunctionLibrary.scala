/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.orbeon.saxon.expr.{Expression, StaticContext}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.StructuredQName
import org.orbeon.saxon.trans.XPathException


/**
 * Saxon `FunctionLibrary` decorator enforcing an `AllowedFunctions` whitelist policy.
 *
 * Calls to disallowed functions throw a static `XPathException` in `bind()`, preventing
 * compilation and preventing fall-through to subsequently configured Saxon libraries.
 * In `isAvailable()`, disallowed functions return `false`.
 */
case class WhitelistedFunctionLibrary(
  delegate        : FunctionLibrary,
  allowedFunctions: AllowedFunctions
) extends FunctionLibrary {

  def isAvailable(symbolicName: StructuredQName, arity: Int): Boolean =
    allowedFunctions.isAllowed(symbolicName.getNamespaceURI, symbolicName.getLocalName) &&
      (delegate ne null) && delegate.isAvailable(symbolicName, arity)

  def bind(
    symbolicName: StructuredQName,
    staticArgs  : Array[Expression],
    env         : StaticContext
  ): Expression = {
    val uri       = symbolicName.getNamespaceURI
    val localName = symbolicName.getLocalName

    if (!allowedFunctions.isAllowed(uri, localName))
      throw new XPathException(
        s"Function '${symbolicName.getClarkName}' is not allowed in user-defined form expressions"
      )
    else if (delegate ne null)
      delegate.bind(symbolicName, staticArgs, env)
    else
      null
  }

  def copy(): FunctionLibrary =
    WhitelistedFunctionLibrary(if (delegate ne null) delegate.copy() else null, allowedFunctions)
}
