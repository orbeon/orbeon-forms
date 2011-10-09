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

import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.oxf.xforms.function.{Power, CountNonEmpty, IsCardNumber, BooleanFromString}
/**
 * XForms functions that are a bit funny.
 */
trait XFormsFunnyFunctions extends OrbeonFunctionLibrary {
    
    Fun("boolean-from-string", classOf[BooleanFromString], 0, 1, 1, BOOLEAN, EXACTLY_ONE,
        Arg(STRING, EXACTLY_ONE)
    )

    Fun("is-card-number", classOf[IsCardNumber], 0, 1, 1, BOOLEAN, EXACTLY_ONE,
        Arg(STRING, EXACTLY_ONE)
    )

    Fun("count-non-empty", classOf[CountNonEmpty], 0, 1, 1, INTEGER, EXACTLY_ONE,
        Arg(ANY_ATOMIC, ALLOWS_ZERO_OR_MORE)
    )

    Fun("power", classOf[Power], 0, 2, 2, NUMERIC, EXACTLY_ONE,
        Arg(NUMERIC, EXACTLY_ONE),
        Arg(NUMERIC, EXACTLY_ONE)
    )
}