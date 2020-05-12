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

import java.{lang => jl}

import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.control.{LHHASupport, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.processor.handlers.HandlerSupport
import org.xml.sax.Attributes

abstract class XFormsGroupHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends
  XFormsControlLifecyleHandler(
    uri,
    localname,
    qName,
    localAtts,
    matched,
    handlerContext,
    repeating  = false,
    forwarding = true
  ) with HandlerSupport {

  protected def getLabelClasses(xformsControl: XFormsSingleNodeControl): jl.StringBuilder = {

    require(LHHASupport.hasLabel(containingDocument, getPrefixedId))

    val labelClasses = new jl.StringBuilder("xforms-label")

    // Handle relevance on label
    if ((xformsControl eq null) || ((xformsControl ne null) && ! xformsControl.isRelevant))
      labelClasses.append(" xforms-disabled")

    // Copy over existing label classes if any
    val labelClassAttribute =
      xformsHandlerContext.getPartAnalysis.getLHH(getPrefixedId, LHHA.Label).element.attributeValue(XFormsConstants.CLASS_QNAME)

    if (labelClassAttribute ne null) {
      labelClasses.append(' ')
      labelClasses.append(labelClassAttribute)
    }

    labelClasses
  }

  protected def getLabelValue(xformsControl: XFormsSingleNodeControl): String =
    if (xformsControl eq null) // TODO: can happen?
      null
    else
      xformsControl.getLabel
}