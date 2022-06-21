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
import org.orbeon.oxf.xforms.analysis.controls.UploadControl
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl.mediatypeToAccept
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML.outputDisabledAttribute
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.xforms.Constants.ComponentSeparator
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames._
import org.xml.sax._

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
    val state = if (uploadControl eq null) "empty" else uploadControl.state
    classes.append(" xforms-upload-state-" + state)
  }

  override protected def handleControlStart(): Unit = {

    implicit val receiver: XMLReceiver = handlerContext.controller.output

    val uploadControl       = Option(currentControl.asInstanceOf[XFormsUploadControl])
    val containerAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, currentControl, addId = true)
    val xhtmlPrefix         = handlerContext.findXHTMLPrefix

    // Enclosing xhtml:span
    withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {

      // xhtml:input unless static readonly
      if (! XFormsBaseHandler.isStaticReadonly(currentControl)) {
        reusableAttributes.clear()
        reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-upload-select")
        reusableAttributes.addAttribute("", "type", "type", XMLReceiverHelper.CDATA, "file")
        // Generate an id, because JS event handlers are not attached to elements that don't have an id, and
        // this causes issues with IE where we register handlers directly on controls
        reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, getForEffectiveIdWithNs.getOrElse(throw new IllegalStateException))
        reusableAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, getEffectiveId)
        // IE causes issues when the user types in or pastes in an incorrect file name. Some sites use this to
        // disable pasting in the file. See http://tinyurl.com/6dcd6a
        reusableAttributes.addAttribute("", "unselectable", "unselectable", XMLReceiverHelper.CDATA, "on")
        // NOTE: @value was meant to suggest an initial file name, but this is not supported by browsers

        if (isXFormsReadonlyButNotStaticReadonly(currentControl))
          outputDisabledAttribute(reusableAttributes)

        // @accept
        uploadControl       flatMap
          (_.acceptValue)   map
          mediatypeToAccept foreach
          (accept => reusableAttributes.addAttribute("", "accept", "accept", XMLReceiverHelper.CDATA, accept))

        uploadControl foreach
          (_.addExtensionAttributesExceptClassAndAcceptForHandler(reusableAttributes, XXFORMS_NAMESPACE_URI))

        XFormsBaseHandler.handleAccessibilityAttributes(attributes, reusableAttributes)
        handleAriaByAtts(reusableAttributes)

        // `@multiple="multiple"`
        if (elementAnalysis.asInstanceOf[UploadControl].multiple)
          reusableAttributes.addAttribute("", "multiple", "multiple", XMLReceiverHelper.CDATA, "multiple")

        element("input", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = reusableAttributes)
      }

      // Nested xhtml:span for xforms-upload-info
      reusableAttributes.clear()
      reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-upload-info")
      withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = reusableAttributes) {

        // Metadata
        def outputSpan(name: String, value: XFormsUploadControl => Option[String]): Unit = {
          reusableAttributes.clear()
          reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-upload-" + name)

          withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = reusableAttributes) {
            uploadControl flatMap value foreach
              { v => receiver.characters(v.toCharArray, 0, v.length) }
          }
        }

        outputSpan("filename",  _.filename)
        outputSpan("mediatype", _.fileMediatype)
        outputSpan("size",      _.humanReadableFileSize)

        // Clickable image
        reusableAttributes.clear()
        reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-upload-remove")
        reusableAttributes.addAttribute("", "src",   "src",   XMLReceiverHelper.CDATA, "/ops/images/xforms/remove.gif")
        reusableAttributes.addAttribute("", "alt",   "alt",   XMLReceiverHelper.CDATA, "Remove File")

        element("img", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = reusableAttributes)
      }
    }
  }

  override def getForEffectiveIdWithNs: Option[String] =
    containingDocument.namespaceId(XFormsId.appendToEffectiveId(getEffectiveId, ComponentSeparator + "xforms-input")).some
}