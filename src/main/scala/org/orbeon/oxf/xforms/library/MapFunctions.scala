/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.library

import org.orbeon.oxf.xforms.function.map.{MapEntry, MapGet, MapMerge}
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.`type`.{BuiltInAtomicType, Type}
import org.orbeon.saxon.expr.StaticProperty.{ALLOWS_ZERO_OR_MORE, EXACTLY_ONE}


trait MapFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val MapFunctionsNS: Seq[String]

  Namespace(MapFunctionsNS) {

    Fun("entry", classOf[MapEntry], op = 0, min = 2, BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("merge", classOf[MapMerge], op = 0, min = 1,  BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, ALLOWS_ZERO_OR_MORE)
    )

    Fun("get", classOf[MapGet], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE),
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE)
    )
  }

}
