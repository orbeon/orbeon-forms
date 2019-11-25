/**
  * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.control.XFormsControl._
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.dom4j.LocationData

case class LHHAValue(label: String, isHTML: Boolean) {

  def streamAsHTML(ch: XMLReceiverHelper, locationData: LocationData): Unit =
    if (isHTML)
      streamHTMLFragment(ch.getXmlReceiver, label, locationData, "")
    else
      ch.text(StringUtils.defaultString(label))

  def htmlValue(locationData: LocationData): String =
    if (isHTML)
      getEscapedHTMLValue(locationData, label)
    else
      label.escapeXmlMinimal

  def javaScriptValue(locationData: LocationData): String =
    escapeJavaScript(htmlValue(locationData))
}