package org.orbeon.oxf.xforms.processor.handlers.xhtml

import cats.syntax.option.*
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport.*
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


/**
 * Handle xxf:dialog.
 *
 * TODO: Subclasses per appearance.
 */
class XXFormsDialogHandler(
  uri             : String,
  localname       : String,
  qName           : String,
  attributes      : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    attributes,
    handlerContext,
    repeating  = false,
    forwarding = true
  ) {

  override def start(): Unit = {

    val effectiveDialogId = handlerContext.getEffectiveId(attributes)
    val dialogXFormsControl = containingDocument.getControlByEffectiveId(effectiveDialogId).asInstanceOf[XXFormsDialogControl]

    // Find classes to add
    // NOTE: attributes logic duplicated in `XXFormsDialogControl`
    // Get values statically so we can handle the case of the repeat template
    // TODO: 2020-02-27: There are no more repeat templates. Check this.

    // TODO: Pass `dialogXFormsControl` instead of `null`?
    val classes = getInitialClasses(uri, localname, attributes, elementAnalysis, null, incrementalDefault = false, staticLabel = None)
    classes.append(" xforms-dialog-")

    val explicitLevel = attributes.getValue("level")

    val level =
      if (explicitLevel eq null)
        if (XFormsControl.appearances(elementAnalysis).contains(XFormsNames.XFORMS_MINIMAL_APPEARANCE_QNAME))
          "modeless"
        else
          "modal"
      else
        explicitLevel

    classes.append(level)
    classes.append(" xforms-dialog-close-")
    classes.append(!("false" == attributes.getValue("close")))
    classes.append(" xforms-dialog-draggable-false")
    classes.append(" xforms-dialog-visible-")
    classes.append(dialogXFormsControl.isDialogVisible)

    // Start main `xh:dialog`
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val dialogQName = XMLUtils.buildQName(xhtmlPrefix, "dialog")
    val divQName = XMLUtils.buildQName(xhtmlPrefix, "div")
    val contentHandler = handlerContext.controller.output

    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "dialog", dialogQName, XFormsBaseHandler.getIdClassXHTMLAttributes(containingDocument, attributes, classes.toString, effectiveDialogId.some))

    // Child `xh:div` for label
    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes(XFormsNames.CLASS_QNAME, "xxforms-dialog-head"))
    val labelValue =
      if (dialogXFormsControl != null)
        dialogXFormsControl.getLabel(handlerContext.collector)
      else
        null

    if (labelValue != null)
      contentHandler.characters(labelValue.toCharArray, 0, labelValue.length)
    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName)

    // Child `xh:div` for body
    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes(XFormsNames.CLASS_QNAME, "xxforms-dialog-body"))
  }

  override def end(): Unit = {
    // Close `xh:div`'s
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val dialogQName = XMLUtils.buildQName(xhtmlPrefix, "dialog")
    val divQName = XMLUtils.buildQName(xhtmlPrefix, "div")
    val contentHandler = handlerContext.controller.output
    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName)
    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "dialog", dialogQName)
  }
}