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
import org.orbeon.saxon.function.EnvironmentVariable
import org.orbeon.saxon.functions.{FormatDate, FormatNumber}
import org.orbeon.saxon.om.StandardNames

/**
 * Useful XSLT functions missing in XPath 2/XForms.
 */
trait XSLTFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val XSLTFunctionsNS: Seq[String]

  Namespace(XSLTFunctionsNS) {

    Fun("format-date", classOf[FormatDate], op = StandardNames.XS_DATE, min = 2, STRING, EXACTLY_ONE,
      Arg(DATE, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("format-dateTime", classOf[FormatDate], op = StandardNames.XS_DATE_TIME, min = 2, STRING, EXACTLY_ONE,
      Arg(DATE_TIME, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("format-number", classOf[FormatNumber], op = 0, min = 2, STRING, EXACTLY_ONE,
      Arg(NUMERIC, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("format-time", classOf[FormatDate], op = StandardNames.XS_TIME, min = 2, STRING, EXACTLY_ONE,
      Arg(TIME, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("environment-variable", classOf[EnvironmentVariable], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )
  }
}