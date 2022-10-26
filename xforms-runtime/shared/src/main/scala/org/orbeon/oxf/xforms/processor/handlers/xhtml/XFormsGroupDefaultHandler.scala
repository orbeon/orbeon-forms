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

import cats.syntax.option._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{ContainerControl, LHHA, LHHAAnalysis}
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.forwardAccessibilityAttributes
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.xforms.XFormsNames


// Default group handler
class XFormsGroupDefaultHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends
  XFormsGroupHandler(
    uri,
    localname,
    qName,
    localAtts,
    matched,
    handlerContext
  ) {

  // Use explicit container element name if present, otherwise use default
  override def getContainingElementName: String =
    matched match {
      case control: ContainerControl =>
        control.elementQName map (_.localName) getOrElse super.getContainingElementName
      case _ =>
        super.getContainingElementName
    }

  override def getContainingElementQName: String =
    matched match {
      case control: ContainerControl =>
        control.elementQName map (_.qualifiedName) getOrElse super.getContainingElementQName
      case _ =>
        super.getContainingElementQName
    }

  protected def handleControlStart(): Unit = ()

  override protected def addCustomAtts(atts: AttributesImpl): Unit = {
    super.addCustomAtts(atts)
    // This handler is used when the group:
    //
    // - is not internal
    // - is not a separator
    // - is not a fieldset (legacy explicit `xxf:fieldset` appearance OR local LHHA)
    //
    // So remains the case of an external LHHA. This is where it makes sense to add the `aria-*` attributes.
    //
    if (handleAriaByAtts(atts, _ => true)) {
      // There is at least one reference with `aria-*`, so add new attributes
      atts.addOrReplace(XFormsNames.ROLE_QNAME, "group")
      if (handlerContext.a11yFocusOnGroups)
        atts.addOrReplace(XFormsNames.TABINDEX_QNAME, "0")
    }
    // After the above so that attributes can be overridden
    forwardAccessibilityAttributes(attributes, atts)
  }

  override protected def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit = {

    // TODO: check why we output our own label here

    val groupControl = currentControl.asInstanceOf[XFormsSingleNodeControl]
    val effectiveId = getEffectiveId

    val classes     = getLabelClasses(groupControl, lhhaAnalysis)
    val isHtmlLabel = lhhaAnalysis.containsHTML

    if (isHtmlLabel) {
      if (classes.length() > 0)
        classes.append(' ')
      classes.append("xforms-mediatype-text-html")
    }

    val atts = new AttributesImpl
    atts.addOrReplace(XFormsNames.CLASS_QNAME, classes.toString)

    XFormsBaseHandlerXHTML.outputLabelFor(
      handlerContext         = handlerContext,
      attributes             = atts,
      labelEffectiveIdOpt    = None,
      forEffectiveIdWithNs   = containingDocument.namespaceId(effectiveId).some,
      lhha                   = LHHA.Label,
      elementName            = handlerContext.labelElementName,
      labelValue             = getLabelValue(groupControl),
      mustOutputHTMLFragment = isHtmlLabel,
      isExternal             = false
    )
  }
}