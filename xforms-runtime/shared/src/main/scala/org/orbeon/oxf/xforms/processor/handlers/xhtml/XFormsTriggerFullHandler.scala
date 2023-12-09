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

import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML._
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsTriggerFullHandler._
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.{XMLReceiver, XMLUtils}
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


/**
 * Default full appearance (button).
 */
class XFormsTriggerFullHandler(
  uri             : String,
  localname       : String,
  qName           : String,
  localAtts       : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsTriggerHandler(
    uri,
    localname,
    qName,
    localAtts,
    elementAnalysis,
    handlerContext
  ) {

  override protected def handleControlStart(): Unit = {

    val triggerControl = currentControl.asInstanceOf[XFormsTriggerControl]
    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    val containerAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, triggerControl, addId = true)

    val isHTMLLabel = (triggerControl ne null) && triggerControl.isHTMLLabel(handlerContext.collector)
    val xhtmlPrefix = handlerContext.findXHTMLPrefix

    containerAttributes.addOrReplace("type", "button")

    // Disabled attribute when needed
    if (isXFormsReadonlyButNotStaticReadonly(triggerControl))
      outputDisabledAttribute(containerAttributes)

    // Determine bootstrap classes, which go on the <button> element
    val bootstrapClasses = "btn" :: (XFormsControl.appearances(elementAnalysis) flatMap BootstrapAppearances.get toList)

    // xh:button or xh:input
    val elementName = "button"
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, elementName)
    xmlReceiver.startElement(XHTML_NAMESPACE_URI, elementName, spanQName, appendClasses(containerAttributes, bootstrapClasses))

    // Output content of <button> element
    if ("button" == elementName)
      outputLabelTextIfNotEmpty(getTriggerLabel(triggerControl), xhtmlPrefix, isHTMLLabel, Option(triggerControl.getLocationData))

    xmlReceiver.endElement(XHTML_NAMESPACE_URI, elementName, spanQName)
  }
}

private object XFormsTriggerFullHandler {

  // See also `appendToClassAttribute` which takes classes as strings
  def appendClasses(atts: AttributesImpl, classes: List[String]): AttributesImpl = {
    val existingClasses = Option(atts.getValue("class")).toList
    val newClasses = existingClasses ::: classes ::: Nil mkString " "
    atts.addOrReplace(XFormsNames.CLASS_QNAME, newClasses)
    atts
  }

  // Map appearances to Bootstrap classes, e.g. xxf:primary -> btn-primary
  val BootstrapAppearances: Map[QName, String] =
    Seq("primary", "info", "success", "warning", "danger", "inverse", "mini", "small", "large", "block") map
      (name => QName(name, XXFORMS_NAMESPACE) -> ("btn-" + name)) toMap
}