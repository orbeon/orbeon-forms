/**
  * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.saxon

import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.`type`.BuiltInAtomicType.{BOOLEAN, INTEGER, STRING}
import org.orbeon.saxon.expr.StaticProperty.{ALLOWS_ZERO_OR_MORE, ALLOWS_ZERO_OR_ONE, EXACTLY_ONE}
import org.orbeon.saxon.function._


trait PureUriFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val PureUriFunctionsNS: Seq[String]

  Namespace(PureUriFunctionsNS) {

    Fun("uri-scheme", classOf[UriScheme], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("uri-scheme-specific-part", classOf[UriSchemeSpecificPart], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("uri-authority", classOf[UriAuthority], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("uri-user-info", classOf[UriUserInfo], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("uri-host", classOf[UriHost], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("uri-port", classOf[UriPort], op = 0, min = 1, INTEGER, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("uri-path", classOf[UriPath], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("uri-query", classOf[UriQuery], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("uri-fragment", classOf[UriFragment], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("uri-param-names", classOf[UriParamNames], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("uri-param-values", classOf[UriParamValues], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )
  }
}