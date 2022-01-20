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
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.{ControlAjaxSupport, XFormsControl}
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper, XMLUtils}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable._


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
  uri                 : String,
  localname           : String,
  qName               : String,
  localAtts           : Attributes,
  val elementAnalysis : ElementAnalysis,
  handlerContext      : HandlerContext,
  repeating           : Boolean,
  forwarding          : Boolean
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    localAtts,
    handlerContext,
    repeating,
    forwarding
  ) {

  import Private._

  // By default, controls are enclosed with a <span>
  def getContainingElementName = "span"

  protected def getContainingElementQName: String =
    XMLUtils.buildQName(handlerContext.findXHTMLPrefix, getContainingElementName)

  override final def start(): Unit =
    if (isMustOutputControl(currentControl)) {

      // Open control element, usually `<span>`
      if (isMustOutputContainerElement)
        handlerContext.controller.output.startElement(
          XMLConstants.XHTML_NAMESPACE_URI,
          getContainingElementName,
          getContainingElementQName,
          getContainerAttributes(
            uri,
            localname,
            attributes,
            getPrefixedId,
            getEffectiveId,
            elementAnalysis,
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
        handlerContext.controller.output.endElement(
          XMLConstants.XHTML_NAMESPACE_URI,
          getContainingElementName,
          getContainingElementQName
        )
    }

  // May be overridden by subclasses
  protected def isMustOutputControl(control: XFormsControl) = true
  protected def isMustOutputContainerElement                = true

  // TODO: Those should take the static LHHA
  protected def handleLabel(): Unit =
    handleLabelHintHelpAlert(
      lhhaAnalysis             = getStaticLHHA(getPrefixedId, LHHA.Label),
      targetControlEffectiveId = getEffectiveId,
      forEffectiveIdWithNs     = getForEffectiveIdWithNs(getEffectiveId),
      lhha                     = LHHA.Label,
      requestedElementNameOpt  = XFormsBaseHandler.isStaticReadonly(currentControl) option "span",
      controlOrNull            = currentControl,
      isExternal               = false
    )

  protected def handleAlert(): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyAlert)
      handleLabelHintHelpAlert(
        lhhaAnalysis             = getStaticLHHA(getPrefixedId, LHHA.Alert),
        targetControlEffectiveId = getEffectiveId,
        forEffectiveIdWithNs     = getForEffectiveIdWithNs(getEffectiveId),
        lhha                     = LHHA.Alert,
        requestedElementNameOpt  = None,
        controlOrNull            = currentControl,
        isExternal               = false
      )

  protected def handleHint(): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyHint)
      handleLabelHintHelpAlert(
        lhhaAnalysis             = getStaticLHHA(getPrefixedId, LHHA.Hint),
        targetControlEffectiveId = getEffectiveId,
        forEffectiveIdWithNs     = getForEffectiveIdWithNs(getEffectiveId),
        lhha                     = LHHA.Hint,
        requestedElementNameOpt  = None,
        controlOrNull            = currentControl,
        isExternal               = false
      )

  protected def handleHelp(): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl))
      handleLabelHintHelpAlert(
        lhhaAnalysis             = getStaticLHHA(getPrefixedId, LHHA.Help),
        targetControlEffectiveId = getEffectiveId,
        forEffectiveIdWithNs     = getForEffectiveIdWithNs(getEffectiveId),
        lhha                     = LHHA.Help,
        requestedElementNameOpt  = None,
        controlOrNull            = currentControl,
        isExternal               = false
      )

  // Must be overridden by subclasses
  protected def handleControlStart(): Unit
  protected def handleControlEnd  (): Unit = ()

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
        XFormsBaseHandler.getLHHACIdWithNs(containingDocument, effectiveId, XFormsBaseHandlerXHTML.ControlCode)
      )
    containerAttributes
  }

  // Return the effective id of the element to which label/@for, etc. must point to.
  // Default: point to `foo$bar$$c.1-2-3`
  def getForEffectiveIdWithNs(effectiveId: String): Option[String] =
    XFormsBaseHandler.getLHHACIdWithNs(containingDocument, getEffectiveId, XFormsBaseHandlerXHTML.ControlCode).some

  // See https://github.com/orbeon/orbeon-forms/issues/4046
  final lazy val currentControl: XFormsControl =
    containingDocument.getControlByEffectiveId(getEffectiveId) ensuring (_ ne null)

  final protected def handleAriaByAttForSelect1Full(atts: AttributesImpl): Unit =
    for {
      attValue      <- ControlAjaxSupport.findAriaByWithNs(elementAnalysis, currentControl, LHHA.Label, condition = _ => true)(containingDocument)
      attName       = ControlAjaxSupport.AriaLabelledby
    } locally {
      atts.addAttribute("", attName, attName, XMLReceiverHelper.CDATA, attValue)
    }

  final protected def handleAriaByAtts(atts: AttributesImpl): Unit =
    for {
      (lhha, attName) <- ControlAjaxSupport.LhhaWithAriaAttName
      attValue        <- ControlAjaxSupport.findAriaByWithNs(elementAnalysis, currentControl, lhha, condition = _.isForRepeat)(containingDocument)
    } locally {
      atts.addAttribute("", attName, attName, XMLReceiverHelper.CDATA, attValue)
    }

  final protected def getStaticLHHA(controlPrefixedId: String, lhha: LHHA): LHHAAnalysis = {
    val globalOps = handlerContext.getPartAnalysis
    if (lhha == LHHA.Alert)
      globalOps.getAlerts(controlPrefixedId).head
    else // for alerts, take the first one, but does this make sense?
      globalOps.getLHH(controlPrefixedId, lhha)
  }

  private object Private {

    val beforeAfterTokens: (List[String], List[String]) =
      elementAnalysis.narrowTo[StaticLHHASupport] flatMap
      (_.beforeAfterTokensOpt)                    getOrElse
      handlerContext.documentOrder

    def hasLocalLabel: Boolean = hasLocalLHHA(LHHA.Label)
    def hasLocalHint : Boolean = hasLocalLHHA(LHHA.Hint)
    def hasLocalHelp : Boolean = hasLocalLHHA(LHHA.Help)
    def hasLocalAlert: Boolean = hasLocalLHHA(LHHA.Alert)

    def hasLocalLHHA(lhhaType: LHHA): Boolean =
      handlerContext.getPartAnalysis.getControlAnalysis(getPrefixedId) match {
        case support: StaticLHHASupport => support.hasLocal(lhhaType)
        case _                          => false
      }
  }

}
