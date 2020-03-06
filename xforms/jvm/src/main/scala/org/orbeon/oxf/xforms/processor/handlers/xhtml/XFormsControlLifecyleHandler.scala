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

import org.orbeon.oxf.util.CollectionUtils.collectByErasedType
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper, XMLUtils}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

import scala.xml.SAXException

/**
  * This class is a helper base class which handles the lifecycle of producing markup for a control. The following
  * phases are handled:
  *
  * - Give the handler a chance to do some prep work: `prepareHandler()`
  * - Get custom information: `addCustomClasses()`
  * - Check whether the control wants any output at all: `isMustOutputControl()`
  * - Output label, control, hint, help, and alert in order specified by properties
  *
  * Outputting the control is split into two parts: `handleControlStart()` and `handleControlEnd()`. In most cases, only
  * `handleControlStart()` is used, but container controls will use `handleControlEnd()`.
  */
abstract class XFormsControlLifecyleHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef,
  repeating      : Boolean,
  forwarding     : Boolean
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    localAtts,
    matched,
    handlerContext,
    repeating,
    forwarding
  ) with WithControl {

  import Private._

  // By default, controls are enclosed with a <span>
  def getContainingElementName = "span"

  protected def getContainingElementQName: String =
    XMLUtils.buildQName(xformsHandlerContext.findXHTMLPrefix, getContainingElementName)

  override final def start(): Unit =
    if (isMustOutputControl(currentControl)) {

      // Open control element, usually `<span>`
      if (isMustOutputContainerElement)
        xformsHandlerContext.getController.getOutput.startElement(
          XMLConstants.XHTML_NAMESPACE_URI,
          getContainingElementName,
          getContainingElementQName,
          getContainerAttributes(
            uri,
            localname,
            attributes,
            getPrefixedId,
            getEffectiveId,
            currentControl,
            Option(getStaticLHHA(getPrefixedId, LHHA.Label))
          )
        )

      // 2012-12-17: Removed nested `<a name="effective-id">` because the enclosing `<span`> for the control has the
      // same id and will be handled first by the browser as per HTML 5. This means the named anchor is actually
      // redundant.

      // Process everything up to and including the control
      for (current <- beforeAfterTokens._1)
        current match {
          case "control" => handleControlStart()
          case "label"   => if (hasLocalLabel) handleLabel()
          case "alert"   => if (hasLocalAlert) handleAlert()
          case "hint"    => if (hasLocalHint)  handleHint()
          case "help"    => if (hasLocalHelp)  handleHelp()
        }
    }

  override final def end(): Unit =
    if (isMustOutputControl(currentControl)) {

      // Process everything after the control has been shown
      for (current <- beforeAfterTokens._2)
        current match {
          case "control" => handleControlEnd()
          case "label"   => if (hasLocalLabel) handleLabel()
          case "alert"   => if (hasLocalAlert) handleAlert()
          case "hint"    => if (hasLocalHint)  handleHint()
          case "help"    => if (hasLocalHelp)  handleHelp()
        }

      // Close control element, usually `<span>`
      if (isMustOutputContainerElement)
        xformsHandlerContext.getController.getOutput.endElement(
          XMLConstants.XHTML_NAMESPACE_URI,
          getContainingElementName,
          getContainingElementQName
        )
    }

  // May be overridden by subclasses
  protected def isMustOutputControl(control: XFormsControl) = true
  protected def isMustOutputContainerElement                = true

  // TODO: Those should take the static LHHA
  @throws[SAXException]
  protected def handleLabel(): Unit =
    handleLabelHintHelpAlert(
      getStaticLHHA(getPrefixedId, LHHA.Label),
      getEffectiveId,
      getForEffectiveId(getEffectiveId),
      LHHA.Label,
      XFormsBaseHandler.isStaticReadonly(currentControl) option "span",
      currentControl,
      isExternal = false
    )

  @throws[SAXException]
  protected def handleAlert(): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyAlert)
      handleLabelHintHelpAlert(
        getStaticLHHA(getPrefixedId, LHHA.Alert),
        getEffectiveId,
        getForEffectiveId(getEffectiveId),
        LHHA.Alert,
        None,
        currentControl,
        isExternal = false
      )

  @throws[SAXException]
  protected def handleHint(): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyHint)
      handleLabelHintHelpAlert(
        getStaticLHHA(getPrefixedId, LHHA.Hint),
        getEffectiveId,
        getForEffectiveId(getEffectiveId),
        LHHA.Hint,
        None,
        currentControl,
        isExternal = false
      )

  @throws[SAXException]
  protected def handleHelp(): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl))
      handleLabelHintHelpAlert(
        getStaticLHHA(getPrefixedId, LHHA.Help),
        getEffectiveId,
        getForEffectiveId(getEffectiveId),
        LHHA.Help,
        None,
        currentControl,
        isExternal = false
      )

  // Must be overridden by subclasses
  @throws[SAXException]
  protected def handleControlStart(): Unit

  @throws[SAXException]
  protected def handleControlEnd() = ()

  protected def getEmptyNestedControlAttributesMaybeWithId(
    effectiveId : String,
    control     : XFormsControl,
    addId       : Boolean
  ): AttributesImpl = {
    reusableAttributes.clear()
    val containerAttributes = reusableAttributes
    if (addId)
      containerAttributes.addAttribute(
        "",
        "id",
        "id",
        XMLReceiverHelper.CDATA,
        XFormsBaseHandler.getLHHACId(containingDocument, effectiveId, XFormsBaseHandlerXHTML.ControlCode)
      )
    containerAttributes
  }

  // Return the effective id of the element to which label/@for, etc. must point to.
  // Default: point to `foo$bar$$c.1-2-3`
  def getForEffectiveId(effectiveId: String): String =
    XFormsBaseHandler.getLHHACId(containingDocument, getEffectiveId, XFormsBaseHandlerXHTML.ControlCode)

  private object Private {

    val beforeAfterTokens: (List[String], List[String]) =
      staticControlOpt                       flatMap
      collectByErasedType[StaticLHHASupport] flatMap
      (_.beforeAfterTokensOpt)               getOrElse
      xformsHandlerContext.getDocumentOrder

    def hasLocalLabel = hasLocalLHHA(LHHA.Label)
    def hasLocalHint  = hasLocalLHHA(LHHA.Hint)
    def hasLocalHelp  = hasLocalLHHA(LHHA.Help)
    def hasLocalAlert = hasLocalLHHA(LHHA.Alert)

    def hasLocalLHHA(lhhaType: LHHA): Boolean =
      xformsHandlerContext.getPartAnalysis.getControlAnalysis(getPrefixedId) match {
        case support: StaticLHHASupport => support.hasLocal(lhhaType)
        case _                          => false
      }
  }
}
