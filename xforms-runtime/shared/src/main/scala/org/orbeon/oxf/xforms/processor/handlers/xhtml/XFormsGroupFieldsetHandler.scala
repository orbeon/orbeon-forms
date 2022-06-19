package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.controls.{GroupControl, LHHA, LHHAAnalysis}
import org.orbeon.oxf.xforms.control.controls.XFormsGroupControl
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper, XMLUtils}
import org.xml.sax.Attributes


class XFormsGroupFieldsetHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  elementAnalysis: GroupControl,
  handlerContext : HandlerContext
) extends
  XFormsGroupHandler(
    uri,
    localname,
    qName,
    attributes,
    elementAnalysis,
    handlerContext
  ) {

  override def getContainingElementName = "fieldset"

  override def handleControlStart(): Unit = {

    val groupControl = currentControl.asInstanceOf[XFormsGroupControl]
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val contentHandler = handlerContext.controller.output

    // Output an `xh:legend` element if and only if there is an `xf:label` element. This help with
    // styling in particular.
    elementAnalysis.firstLhha(LHHA.Label) foreach { lhhaAnalysis =>
      // Handle label classes
      reusableAttributes.clear()
      reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, getLabelClasses(groupControl, lhhaAnalysis).toString)
      reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, XFormsBaseHandler.getLHHACIdWithNs(containingDocument, getEffectiveId, XFormsBaseHandlerXHTML.LHHACodes(LHHA.Label)))

      // Output `xh:legend` with label content
      val legendQName = XMLUtils.buildQName(xhtmlPrefix, "legend")
      contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName, reusableAttributes)
      val labelValue = getLabelValue(groupControl)
      if ((labelValue ne null) && labelValue.nonEmpty)
        contentHandler.characters(labelValue.toCharArray, 0, labelValue.length)
      contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName)
    }
  }

  override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit = () // NOP because we handle the label in a custom way
}