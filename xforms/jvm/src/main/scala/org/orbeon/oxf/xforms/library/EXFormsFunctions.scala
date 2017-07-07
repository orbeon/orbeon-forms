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

import org.orbeon.oxf.xforms.function.exforms._
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.`type`.Type
import org.orbeon.oxf.xml.OrbeonFunctionLibrary

/**
 * eXforms functions.
 */
trait EXFormsFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val EXFormsFunctionsNS: Seq[String]

  Namespace(EXFormsFunctionsNS) {

    Fun("relevant", classOf[EXFormsMIP], 0, 0, BOOLEAN, EXACTLY_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("readonly", classOf[EXFormsMIP], 1, 0, BOOLEAN, EXACTLY_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("required", classOf[EXFormsMIP], 2, 0, BOOLEAN, EXACTLY_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("sort", classOf[EXFormsSort], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )
  }
}