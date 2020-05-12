/**
  * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function

import java.util.{GregorianCalendar, TimeZone}

import org.orbeon.oxf.xml.RuntimeDependentFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.{DateTimeValue, StringValue}

class Now extends XFormsFunction with RuntimeDependentFunction {
  override def evaluateItem(context: XPathContext): StringValue = {
    val value =
      stringArgumentOpt(0)(context) match {
        case Some("test") => DateTimeValue.makeDateTimeValue("2004-12-31T12:00:00.000Z").asAtomic.asInstanceOf[DateTimeValue]
        case _ =>
          new DateTimeValue(new GregorianCalendar(TimeZone.getTimeZone("UTC")), true)
      }
    new StringValue(value.getStringValue)
  }
}