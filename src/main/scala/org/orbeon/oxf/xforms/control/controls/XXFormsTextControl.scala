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

import org.dom4j.Element
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.control.{ControlAjaxSupport, XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.xml.sax.helpers.AttributesImpl

/**
  * Represents an extension xxf:text control. This control is used to produce plain text child of
  * xhtml:title, for example. It is based on xf:output.
  */
class XXFormsTextControl(
  container: XBLContainer,
  parent: XFormsControl,
  element: Element,
  effectiveId: String
) extends XFormsOutputControl(
  container,
  parent,
  element,
  effectiveId
) {

  private val forAttribute = element.attributeValue(XFormsConstants.FOR_QNAME)

  // A kind of hacky way of getting the effective id of the HTML element
  def getEffectiveForAttribute =
    forAttribute + getEffectiveId.substring(getId.length)

  override def outputAjaxDiffUseClientValue(
    previousValue         : Option[String],
    previousControl       : Option[XFormsValueControl],
    isNewlyVisibleSubtree : Boolean)(implicit
    ch                    : XMLReceiverHelper
  ) = {

    val attributesImpl = new AttributesImpl

    // The client does not store an HTML representation of the xxf:text control, so we
    // have to output these attributes.
    ControlAjaxSupport.addOrAppendToAttributeIfNeeded(
      attributesImpl,
      "for",
      this.getEffectiveForAttribute,
      isNewlyVisibleSubtree,
      isDefaultValue = false
    )

    attributesImpl.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, this.getEffectiveId)
    outputValueElement(isNewlyVisibleSubtree, attributesImpl, "text")
  }

  override def supportFullAjaxUpdates = false
}