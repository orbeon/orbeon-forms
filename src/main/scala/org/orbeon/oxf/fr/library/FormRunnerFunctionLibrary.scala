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
package org.orbeon.oxf.fr.library

import org.orbeon.oxf.fr.{FormRunner, XMLNames}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.function.xxforms.XXFormsUserRoles
import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary, RuntimeDependentFunction}
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.XML._

// The Form Runner function library
object FormRunnerFunctionLibrary extends OrbeonFunctionLibrary {

  import FormRunnerFunctions._

  Namespace(List(XMLNames.FR)) {

    // Register simple string functions
    for {
      ((name, fun), index) ← NamedStringFunctions.zipWithIndex
    } locally {
      Fun(name, classOf[StringFunctions], index, 0, STRING, ALLOWS_ZERO_OR_ONE)
    }

    // Other functions
    Fun("user-roles", classOf[XXFormsUserRoles], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)
  }
}

object FormRunnerFunctions {

  val NamedStringFunctions = List(
    "mode"         → (() ⇒ FormRunner.FormRunnerParams().mode),
    "app-name"     → (() ⇒ FormRunner.FormRunnerParams().app),
    "form-name"    → (() ⇒ FormRunner.FormRunnerParams().form),
    "form-version" → (() ⇒ FormRunner.FormRunnerParams().formVersion),
    "document-id"  → (() ⇒ FormRunner.FormRunnerParams().document.orNull),
    "lang"         → (() ⇒ FormRunner.currentLang.stringValue),
    "username"     → (() ⇒ NetUtils.getExternalContext.getRequest.getUsername),
    "user-group"   → (() ⇒ NetUtils.getExternalContext.getRequest.getUserGroup)
  )

  val IndexedStringFunctions = (
    for {
      ((name, fun), index) ← FormRunnerFunctions.NamedStringFunctions.zipWithIndex
    } yield
      index → fun
  ).toMap

  class StringFunctions extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): StringValue =
      IndexedStringFunctions(operation).apply()
  }
}
