/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr._
import org.orbeon.saxon.value.AtomicValue

/**
  * xxf:property() function.
  *
  * Return the value of a property from properties.xml.
  */
class XXFormsProperty extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): AtomicValue =
    XXFormsProperty.property(stringArgument(0)(xpathContext)).orNull

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet =
    addSubExpressionsToPathMap(pathMap, pathMapNodeSet)
}

object XXFormsProperty {

  def property(propertyName: String): Option[AtomicValue] =
    ! propertyName.toLowerCase.contains("password")            option
    Properties.instance.getPropertySet.getObject(propertyName) map
    XFormsUtils.convertJavaObjectToSaxonObject                 flatMap
    collectByErasedType[AtomicValue]

  def propertyAsString(propertyName: String): Option[String] =
    property(propertyName) map (_.getStringValue)
}
