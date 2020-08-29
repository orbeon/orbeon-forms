/**
  * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.itemset

import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsUtils.streamHTMLFragment
import org.orbeon.oxf.xforms.control.XFormsControl.getEscapedHTMLValue
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.dom.LocationData

case class LHHAValue(label: String, isHTML: Boolean) {

  def streamAsHTML(locationData: LocationData)(implicit xmlReceiver: XMLReceiver): Unit =
    if (label.nonAllBlank) {
      if (isHTML)
        streamHTMLFragment(xmlReceiver, label, locationData, "")
      else
        text(label)
    }

  def htmlValue(locationData: LocationData): String =
    if (isHTML)
      getEscapedHTMLValue(locationData, label)
    else
      label.escapeXmlMinimal

  def javaScriptValue(locationData: LocationData): String =
    htmlValue(locationData).escapeJavaScript
}

object LHHAValue {
  val Empty: LHHAValue = LHHAValue("", isHTML = false)
}