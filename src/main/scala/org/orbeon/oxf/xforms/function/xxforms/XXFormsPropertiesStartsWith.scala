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

import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.RuntimeDependentFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{ListIterator, SequenceIterator}
import org.orbeon.saxon.value.AtomicValue

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * xxf:properties-starts-with() function.
  *
  * Return the name of all the properties that start with the given name.
  */
class XXFormsPropertiesStartsWith extends XFormsFunction with RuntimeDependentFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator =
    new ListIterator(XXFormsPropertiesStartsWith.propertiesStartsWith(stringArgument(0)(xpathContext)).asJava)
}

object XXFormsPropertiesStartsWith {

  def propertiesStartsWith(propertyName: String): mutable.Buffer[AtomicValue] =
    for {
      property ← Properties.instance.getPropertySet.getPropertiesStartsWith(propertyName).asScala
      if ! property.toLowerCase.contains("password")
    } yield
      XFormsUtils.convertJavaObjectToSaxonObject(property).asInstanceOf[AtomicValue]

}
