/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.control.controls.XXFormsTextControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.xml.sax.Attributes


// Only use within `xh:head/xh:title`
class XXFormsTextHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  handlerContext : HandlerContext
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    attributes,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) {

  override def start(): Unit = {

    val effectiveId = handlerContext.getEffectiveId(attributes)
    val textControl = containingDocument.getControlByEffectiveId(effectiveId).asInstanceOf[XXFormsTextControl]

    val externalValue = textControl.getExternalValue()
    if ((externalValue ne null) && externalValue.nonEmpty)
      handlerContext.controller.output.characters(externalValue.toCharArray, 0, externalValue.length)
  }
}