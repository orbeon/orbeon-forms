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
import org.orbeon.oxf.xforms.analysis.controls.{ContainerControl, LHHA}
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes

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

  override protected def handleLabel(): Unit = {

    // TODO: check why we output our own label here

    val staticLabelOpt = Option(getStaticLHHA(getPrefixedId, LHHA.Label))

    val groupControl = currentControl.asInstanceOf[XFormsSingleNodeControl]
    val effectiveId = getEffectiveId

    val classes = getLabelClasses(groupControl)

    // For LHH we know this statically
    val isHtmlLabel = staticLabelOpt.exists(_.containsHTML)

    if (isHtmlLabel) {
      if (classes.length() > 0)
        classes.append(' ')
      classes.append("xforms-mediatype-text-html")
    }

    reusableAttributes.clear()
    reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, classes.toString)

    XFormsBaseHandlerXHTML.outputLabelFor(
      handlerContext           = handlerContext,
      attributes               = reusableAttributes,
      targetControlEffectiveId = effectiveId.some,
      forEffectiveIdWithNs     = containingDocument.namespaceId(effectiveId).some,
      lhha                     = LHHA.Label,
      elementName              = handlerContext.labelElementName,
      labelValue               = getLabelValue(groupControl),
      mustOutputHTMLFragment   = isHtmlLabel,
      addIds                   = false
    )
  }
}