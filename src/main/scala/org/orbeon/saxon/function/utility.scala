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
package org.orbeon.saxon.function

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.{DefaultFunctionSupport, DependsOnContextItemIfSingleArgumentMissing}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.saxon.value.{BooleanValue, StringValue}
import org.orbeon.scaxon.Implicits._

class IsBlank extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(context: XPathContext): BooleanValue =
    ! (stringArgumentOrContextOpt(0)(context) exists (_.trimAllToEmpty.nonEmpty))
}

class NonBlank extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(context: XPathContext): BooleanValue =
    stringArgumentOrContextOpt(0)(context) exists (_.trimAllToEmpty.nonEmpty)
}

class Split extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    val separator = stringArgumentOpt(1)(xpathContext)
    stringArgumentOrContextOpt(0)(xpathContext) map (_.splitTo[List](separator.orNull))
  }
}

class Trim extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(context: XPathContext): StringValue =
    stringArgumentOrContextOpt(0)(context) map (_.trimAllToEmpty)
}