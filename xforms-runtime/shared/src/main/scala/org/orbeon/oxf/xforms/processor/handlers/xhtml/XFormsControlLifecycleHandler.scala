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
import org.orbeon.oxf.xforms.control.ControlAjaxSupport.AriaLabelledby
import org.orbeon.oxf.xforms.control.{ControlAjaxSupport, XFormsControl}
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
import org.orbeon.xforms.XFormsNames
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
abstract class XFormsControlLifecycleHandler(
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
            getEffectiveId,
            elementAnalysis,
            currentControl,
            lhhaIfLocal(LHHA.Label)
          )
        )

      // Process everything up to and including the control
      for (current <- beforeAfterTokens._1)
        current match {
          case "control" => handleControlStart()
          case "label"   => lhhaIfLocal(LHHA.Label).foreach(handleLabel)
          case "alert"   => lhhaIfLocal(LHHA.Alert).foreach(handleAlert)
          case "hint"    => lhhaIfLocal(LHHA.Hint).foreach(handleHint)
          case "help"    => lhhaIfLocal(LHHA.Help).foreach(handleHelp)
        }
    }

  override final def end(): Unit =
    if (isMustOutputControl(currentControl)) {

      // Process everything after the control has been shown
      for (current <- beforeAfterTokens._2)
        current match {
          case "control" => handleControlEnd()
          case "label"   => lhhaIfLocal(LHHA.Label).foreach(handleLabel)
          case "alert"   => lhhaIfLocal(LHHA.Alert).foreach(handleAlert)
          case "hint"    => lhhaIfLocal(LHHA.Hint).foreach(handleHint)
          case "help"    => lhhaIfLocal(LHHA.Help).foreach(handleHelp)
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

  protected def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit =
    handleLabelHintHelpAlert(
      lhhaAnalysis            = lhhaAnalysis,
      elemEffectiveIdOpt      = None,
      forEffectiveIdWithNsOpt = getForEffectiveIdWithNs(lhhaAnalysis),
      requestedElementNameOpt = XFormsBaseHandler.isStaticReadonly(currentControl) option "span",
      controlOrNull           = currentControl,
      isExternal              = false
    )

  protected def handleAlert(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyAlert)
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        elemEffectiveIdOpt      = None,
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = None,
        controlOrNull           = currentControl,
        isExternal              = false
      )

  protected def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyHint)
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        elemEffectiveIdOpt      = getEffectiveId.some,
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = None,
        controlOrNull           = currentControl,
        isExternal              = false
      )

  protected def handleHelp(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl))
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        elemEffectiveIdOpt      = getEffectiveId.some,
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = None,
        controlOrNull           = currentControl,
        isExternal              = false
      )

  // Must be overridden by subclasses
  protected def handleControlStart(): Unit
  protected def handleControlEnd  (): Unit = ()

  protected def getEmptyNestedControlAttributesMaybeWithId(
    effectiveId : String,
    control     : XFormsControl,
    addId       : Boolean
  ): AttributesImpl = {
    val atts = new AttributesImpl
    if (addId)
      atts.addOrReplace(
        XFormsNames.ID_QNAME,
        XFormsBaseHandler.getLHHACIdWithNs(containingDocument, effectiveId, XFormsBaseHandlerXHTML.ControlCode)
      )
    atts
  }

  // Return the effective id of the element to which `label/@for`, etc. must point to.
  // Default: point to `foo≡bar≡≡c⊙1-2-3`
  def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] =
    XFormsBaseHandler.getLHHACIdWithNs(containingDocument, getEffectiveId, XFormsBaseHandlerXHTML.ControlCode).some

  // See https://github.com/orbeon/orbeon-forms/issues/4046
  final lazy val currentControl: XFormsControl =
    containingDocument.getControlByEffectiveId(getEffectiveId) ensuring (_ ne null)

  final protected def handleAriaByAttForSelect1Full(atts: AttributesImpl): Unit =
    for {
      attValue <- ControlAjaxSupport.findAriaByWithNs(elementAnalysis, currentControl.getEffectiveId, LHHA.Label, condition = _ => true)(containingDocument)
    } locally {
      atts.addOrReplace(AriaLabelledby, attValue)
    }

  final protected def handleAriaByAtts(atts: AttributesImpl, condition: LHHAAnalysis => Boolean): Boolean = {
    val it = ControlAjaxSupport.iterateAriaByAtts(elementAnalysis, currentControl.getEffectiveId, condition)(containingDocument)
    val nonEmpty = it.nonEmpty
    it foreach { case (attName, attValues) =>
      atts.addOrReplace(attName, attValues.mkString(" "))
    }
    nonEmpty
  }

  final protected def getStaticLHHA(lhha: LHHA): Option[LHHAAnalysis] =
    elementAnalysis.narrowTo[StaticLHHASupport].flatMap { lhhaSupport =>
      lhhaSupport.firstLhha(lhha)
    }

  private object Private {

    val beforeAfterTokens: (List[String], List[String]) =
      elementAnalysis.narrowTo[StaticLHHASupport] flatMap
      (_.beforeAfterTokensOpt)                    getOrElse
      handlerContext.documentOrder

    def lhhaIfLocal(lhhaType: LHHA): Option[LHHAAnalysis] =
      getStaticLHHA(lhhaType).filter(_.isLocal)
  }
}
