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
package org.orbeon.oxf.xforms.function.xxforms

import java.text.MessageFormat

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.{PathMap, XPathContext}
import org.orbeon.saxon.value.{StringValue, Value}
import org.orbeon.scaxon.Implicits._

class XXFormsFormatMessage extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    val template         = stringArgument(0)
    val messageArguments = argument(1).iterate(xpathContext)

    // Convert sequence to array of Java objects
    val arguments = asScalaIterator(messageArguments) map Value.convertToJava toArray

    new MessageFormat(template, currentLocale).format(arguments)
  }

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
    XXFormsLang.addXMLLangDependency(pathMap)
    arguments foreach (_.addToPathMap(pathMap, pathMapNodeSet))
    null
  }
}
