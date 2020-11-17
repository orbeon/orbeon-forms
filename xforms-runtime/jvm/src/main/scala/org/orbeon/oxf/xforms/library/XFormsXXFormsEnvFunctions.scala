/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.library

import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.`type`.Type
import org.orbeon.oxf.xforms.function._

/**
 * XForms standard and extension functions that depend on the XForms environment.
 */
trait XFormsXXFormsEnvFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val XFormsXXFormsEnvFunctionsNS: Seq[String]

  Namespace(XFormsXXFormsEnvFunctionsNS) {

    Fun("element", classOf[XFormsElement], op = 0, min = 1, Type.NODE_TYPE, EXACTLY_ONE,
      Arg(ANY_ATOMIC, EXACTLY_ONE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("attribute", classOf[XFormsAttribute], op = 0, min = 1, Type.NODE_TYPE, EXACTLY_ONE,
      Arg(ANY_ATOMIC, EXACTLY_ONE),
      Arg(ANY_ATOMIC, EXACTLY_ONE)
    )

    Fun("case", classOf[XFormsCase], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )
  }
}