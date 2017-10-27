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
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.controls.ContainerControl
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.LHHAC
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes

// Default group handler
class XFormsGroupDefaultHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsGroupHandler(uri, localname, qName, attributes, matched, handlerContext) {

  // Use explicit container element name if present, otherwise use default
  override def getContainingElementName =
    matched match {
      case control: ContainerControl if control.elementQName ne null ⇒
        control.elementQName.name
      case _ ⇒
        super.getContainingElementName
    }

  override def getContainingElementQName =
    matched match {
      case control: ContainerControl if control.elementQName ne null ⇒
        control.elementQName.qualifiedName
      case _ ⇒
        super.getContainingElementQName
    }

  override protected def handleControlStart() = ()

  override protected def handleLabel(): Unit = {
    // TODO: check why we output our own label here

    val groupControl = currentControlOrNull.asInstanceOf[XFormsSingleNodeControl]
    val effectiveId = getEffectiveId

    reusableAttributes.clear()
    reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, getLabelClasses(groupControl))

    XFormsBaseHandlerXHTML.outputLabelFor(
      xformsHandlerContext,
      reusableAttributes,
      effectiveId,
      effectiveId,
      LHHAC.LABEL,
      xformsHandlerContext.getLabelElementName,
      getLabelValue(groupControl),
      (groupControl ne null) && groupControl.isHTMLLabel,
      false
    )
  }
}