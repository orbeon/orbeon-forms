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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.dom.Element
import org.orbeon.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl, XFormsValueControl}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.saxon.om.Item

// Represent an xf:range control.
// NOTE: This should instead be implemented with an XBL component.
class XFormsRangeControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsSingleNodeControl(
  container,
  parent,
  element,
  effectiveId
) with XFormsValueControl {

  private val start = element.attributeValue("start")
  private val end   = element.attributeValue("end")
//  private val step  = element.attributeValue("step")

  override def hasJavaScriptInitialization = true

  override def translateExternalValue(boundItem: Item, externalValue: String): Option[String] =
    Option(convertFromExternalValue(externalValue))

  private def convertFromExternalValue(externalValue: String) = {
    if (start != null && end != null && getBuiltinTypeName == "integer") {
      val startInt = start.toInt
      val endInt   = end.toInt
      val value    = startInt + (externalValue.toDouble * (endInt - startInt).toDouble).toInt

      value.toString
    } else {
      externalValue
    }
  }

  override def evaluateExternalValue(): Unit = {
    val internalValue = getValue

    val updatedValue =
      if (internalValue == null) {
        null
      } else if (start != null && end != null && (valueType == XS_INTEGER_QNAME || valueType == XFORMS_INTEGER_QNAME)) {
        val startInt = start.toInt
        val endInt   = end.toInt
        val value     = (internalValue.toInt - startInt).toDouble / (endInt.toDouble - startInt)

        value.toString
      } else {
        internalValue
      }
    setExternalValue(updatedValue)
  }

  override def findAriaByControlEffectiveId = None
}