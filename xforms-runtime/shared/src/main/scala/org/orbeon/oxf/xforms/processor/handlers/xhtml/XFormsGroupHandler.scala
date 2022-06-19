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

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes

import java.{lang => jl}


abstract class XFormsGroupHandler(
  uri             : String,
  localname       : String,
  qName           : String,
  localAtts       : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsControlLifecyleHandler(
    uri,
    localname,
    qName,
    localAtts,
    elementAnalysis,
    handlerContext,
    repeating  = false,
    forwarding = true
  ) {

  protected def getLabelClasses(xformsControl: XFormsSingleNodeControl, lhhaAnalysis: LHHAAnalysis): jl.StringBuilder = {

    val labelClasses = new jl.StringBuilder("xforms-label")

    // Handle relevance on label
    if ((xformsControl eq null) || ((xformsControl ne null) && ! xformsControl.isRelevant))
      labelClasses.append(" xforms-disabled")

    // Copy over existing label classes if any
    lhhaAnalysis.element.attributeValueOpt(XFormsNames.CLASS_QNAME) foreach { labelClassAttribute =>
      labelClasses.append(' ')
      labelClasses.append(labelClassAttribute)
    }

    labelClasses
  }

  protected def getLabelValue(xformsControl: XFormsSingleNodeControl): String =
    if (xformsControl eq null) {
      // Q: Can this happen?
      // 2020-11-13: Not 100% sure but haven't seen it yet. Probably safe to remove.
      null
    } else
      xformsControl.getLabel
}