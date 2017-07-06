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
package org.orbeon.saxon

import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.function._

trait CryptoFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val CryptoFunctionsNS: Seq[String]

  Namespace(CryptoFunctionsNS) {

    Fun("digest", classOf[Digest], op = 0, min = 2, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("hmac", classOf[Hmac], op = 0, min = 3, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("random", classOf[Random], op = 0, min = 0, NUMERIC, EXACTLY_ONE,
      Arg(BOOLEAN, ALLOWS_ZERO_OR_ONE)
    )
  }
}