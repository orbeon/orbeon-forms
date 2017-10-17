/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import java.{lang ⇒ jl}

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsConstants.LHHA
import org.orbeon.oxf.xforms.analysis.controls.StaticLHHASupport
import org.orbeon.oxf.xforms.control.{ControlAjaxSupport, XFormsControl}
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

// Created this just so we can reuse `getContainerAttributes`. Fix this as we complete conversion of
// code to Scala.
class XFormsBaseHandlerXHTML2 (
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef,
  repeating      : Boolean,
  forwarding     : Boolean
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    attributes,
    matched,
    handlerContext,
    repeating,
    forwarding
  ) {

  val isTemplate     = xformsHandlerContext.isTemplate
  val getPrefixedId  = xformsHandlerContext.getPrefixedId(attributes)
  val getEffectiveId = xformsHandlerContext.getEffectiveId(attributes)

  def staticControlOpt = containingDocument.getStaticOps.findControlAnalysis(getPrefixedId)

  val currentControlOpt =
    ! xformsHandlerContext.isTemplate                            option
      containingDocument.getControlByEffectiveId(getEffectiveId) ensuring
      (! _.contains(null))

  def currentControlOrNull = currentControlOpt.orNull // legacy

  // May be overridden by subclasses
  protected def isDefaultIncremental                                                = false
  protected def addCustomClasses(classes: jl.StringBuilder, control: XFormsControl) = ()

  final protected def getContainerAttributes(
    uri           : String,
    localname     : String,
    attributes    : Attributes,
    prefixedId    : String,
    effectiveId   : String,
    xformsControl : XFormsControl
  ): AttributesImpl = {

    // Get classes
    // Initial classes: `xforms-control`, `xforms-[control name]`, `incremental`, `appearance`, `mediatype`, `xforms-static`
    val classes =
      getInitialClasses(uri, localname, attributes, xformsControl, isDefaultIncremental)

    // All MIP-related classes
    handleMIPClasses(classes, prefixedId, xformsControl)

    // Static classes
    containingDocument.getStaticOps.appendClasses(classes, prefixedId)

    // Dynamic classes added by the control
    addCustomClasses(classes, xformsControl)

    // Get attributes
    val newAttributes = getIdClassXHTMLAttributes(attributes, classes.toString, effectiveId)

    // Add extension attributes in no namespace if possible
    if (xformsControl ne null)
      xformsControl.addExtensionAttributesExceptClassAndAcceptForHandler(newAttributes, "")

    newAttributes
  }

  final protected def handleAriaLabelledBy(atts: AttributesImpl): Unit =
    for {
      staticControl ← staticControlOpt collect { case c: StaticLHHASupport ⇒ c }
      labelledBy    ← ControlAjaxSupport.findLabelledBy(staticControl, currentControlOpt, LHHA.label)(containingDocument)
    } locally {
      atts.addAttribute("", "aria-labelledby", "aria-labelledby", XMLReceiverHelper.CDATA, labelledBy)
    }
}