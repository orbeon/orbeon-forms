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
import org.orbeon.saxon.`type`.Type
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.oxf.xforms.function._
import org.orbeon.saxon.om.NamespaceConstant
import org.orbeon.xforms.XFormsNames

/**
 * XForms functions that are deprecated or not relevant with XPath 2.0.
 */
trait XFormsDeprecatedFunctions extends OrbeonFunctionLibrary {

  Namespace(Seq(NamespaceConstant.FN, XFormsNames.XFORMS_NAMESPACE_URI)) {

    Fun("local-date", classOf[LocalDate], op = 0, min = 0, STRING, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("local-dateTime", classOf[LocalDateTime], op = 0, min = 0, STRING, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("now", classOf[Now], op = 0, min = 0, STRING, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("days-from-date", classOf[DaysFromDate], op = 0, min = 1, INTEGER, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("days-to-date", classOf[DaysToDate], op = 0, min = 1, STRING, EXACTLY_ONE,
      Arg(INTEGER, EXACTLY_ONE)
    )

    Fun("seconds-to-dateTime", classOf[SecondsToDateTime], op = 0, min = 1, DATE_TIME, ALLOWS_ZERO_OR_ONE,
      Arg(NUMERIC, EXACTLY_ONE)
    )

    Fun("seconds", classOf[Seconds], op = 0, min = 1, DOUBLE, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("months", classOf[Months], op = 0, min = 1, INTEGER, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    // NOTE: Deprecated under this name. Use xxf:if() instead
    Fun("xfif", classOf[If], op = 0, min = 3, STRING, EXACTLY_ONE,
      Arg(BOOLEAN, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("choose", classOf[Choose], op = 0, min = 3, Type.ITEM_TYPE, EXACTLY_ONE,
      Arg(BOOLEAN, EXACTLY_ONE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )
  }

  Namespace(XFormsNames.XFORMS_NAMESPACE_URI) {

    Fun("if", classOf[If], op = 0, min = 3, STRING, EXACTLY_ONE,
      Arg(BOOLEAN, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    // NOTE: incompatible with the XPath 2.0 version
    Fun("seconds-from-dateTime", classOf[SecondsFromDateTime], op = 0, min = 1, DECIMAL, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )
  }
}