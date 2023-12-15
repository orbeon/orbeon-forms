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
import org.orbeon.oxf.xforms.analysis.controls.{LHHAAnalysis, UploadControl}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl.mediatypeToAccept
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML.outputDisabledAttribute
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.xforms.Constants.ComponentSeparator
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.xml.sax._
import org.xml.sax.helpers.AttributesImpl

import java.{lang => jl}


/**
 * Handle xf:upload.
 */
class XFormsUploadHandler(
  uri             : String,
  localname       : String,
  qName           : String,
  localAtts       : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsControlLifecycleHandler(
    uri,
    localname,
    qName,
    localAtts,
    elementAnalysis,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) {

  protected override def addCustomClasses(classes: jl.StringBuilder, control: XFormsControl): Unit = {
    val uploadControl = control.asInstanceOf[XFormsUploadControl]
    val state = if (uploadControl eq null) "empty" else uploadControl.state(handlerContext.collector)
    classes.append(" xforms-upload-state-" + state)
  }

  override protected def handleControlStart(): Unit = {

    implicit val receiver: XMLReceiver = handlerContext.controller.output

    val uploadControl       = Option(currentControl.asInstanceOf[XFormsUploadControl])
    val containerAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, currentControl, addId = true)
    val xhtmlPrefix         = handlerContext.findXHTMLPrefix

    // Enclosing xh:span
    withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {

      // xh:input unless static readonly
      if (! XFormsBaseHandler.isStaticReadonly(currentControl)) {
        val atts = new AttributesImpl
        atts.addOrReplace(XFormsNames.CLASS_QNAME, "xforms-upload-select")
        atts.addOrReplace("type", "file")
        // Generate an id, because JS event handlers are not attached to elements that don't have an id, and
        // this causes issues with IE where we register handlers directly on controls
        atts.addOrReplace(XFormsNames.ID_QNAME, computeForEffectiveIdWithNs)
        atts.addOrReplace("name", getEffectiveId)
        // IE causes issues when the user types in or pastes in an incorrect file name. Some sites use this to
        // disable pasting in the file. See http://tinyurl.com/6dcd6a
        atts.addOrReplace("unselectable", "on")
        // NOTE: @value was meant to suggest an initial file name, but this is not supported by browsers

        if (isXFormsReadonlyButNotStaticReadonly(currentControl))
          outputDisabledAttribute(atts)

        // @accept
        uploadControl       flatMap
          (_.acceptValue)   map
          mediatypeToAccept foreach
          (accept => atts.addOrReplace("accept", accept))

        uploadControl foreach
          (_.addExtensionAttributesExceptClassAndAcceptForHandler(atts, XXFORMS_NAMESPACE_URI))

        XFormsBaseHandler.forwardAccessibilityAttributes(attributes, atts)
        handleAriaByAtts(atts, XFormsLHHAHandler.coreControlLhhaByCondition)

        // `@multiple="multiple"`
        if (elementAnalysis.asInstanceOf[UploadControl].multiple)
          atts.addOrReplace("multiple", "multiple")

        element("input", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = atts)
      }

      // Nested xh:span for xforms-upload-info
      withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = List(XFormsNames.CLASS_QNAME.localName -> "xforms-upload-info")) {

        // Metadata
        def outputSpan(name: String, value: XFormsUploadControl => Option[String]): Unit =
          withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = List(XFormsNames.CLASS_QNAME.localName -> s"xforms-upload-$name")) {
            uploadControl flatMap value foreach
              { v => receiver.characters(v.toCharArray, 0, v.length) }
          }

        outputSpan("filename",  _.filename(handlerContext.collector))
        outputSpan("mediatype", _.fileMediatype(handlerContext.collector))
        outputSpan("size",      _.humanReadableFileSize(handlerContext.collector))

        // Clickable image
        val imgAtts = List(
          XFormsNames.CLASS_QNAME.localName -> "xforms-upload-remove",
          "src"                             ->  "/ops/images/xforms/remove.gif",
          "alt"                             ->  "Remove File"
        )
        element("img", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = imgAtts)
      }
    }
  }

  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] =
    computeForEffectiveIdWithNs.some

  def computeForEffectiveIdWithNs: String =
    containingDocument.namespaceId(XFormsId.appendToEffectiveId(getEffectiveId, ComponentSeparator + "xforms-input"))
}