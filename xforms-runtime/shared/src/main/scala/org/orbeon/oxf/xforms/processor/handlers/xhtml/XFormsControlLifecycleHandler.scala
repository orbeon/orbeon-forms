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

import cats.syntax.option.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.ControlAjaxSupport.AriaReadonlyQName
import org.orbeon.oxf.xforms.control.{ControlAjaxSupport, XFormsControl}
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport.*
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver, XMLUtils}
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.XFormsNames.{CLASS_QNAME, ROLE_QNAME, TABINDEX_QNAME}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable.*


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

  // TODO: Should be in static hierarchy
  // By default, controls are enclosed with a <span>
  def getContainingElementName = "span"

  // TODO: Should be from static hierarchy with updated XHTML prefix if needed
  protected def getContainingElementQName: String =
    XMLUtils.buildQName(handlerContext.findXHTMLPrefix, getContainingElementName)

  override final def start(): Unit =
    if (isMustOutputControl(currentControl)) {

      // Open control element, usually `<span>`
      if (isMustOutputContainerElement) {
        val containerAttributes =
          getContainerAttributes(
            uri,
            localname,
            attributes,
            getEffectiveId,
            elementAnalysis,
            currentControl,
            firstLocalLhha(LHHA.Label)
          )

        // Remove autocomplete attribute if it doesn't make sense on this element
        val filteredContainerAttributes =
          if (XFormsBaseHandler.canHaveAutocompleteAttribute(attributes, getContainingElementName, containerAttributes))
            containerAttributes
          else
            containerAttributes.remove("", "autocomplete")

        handlerContext.controller.output.startElement(
          XMLConstants.XHTML_NAMESPACE_URI,
          getContainingElementName,
          getContainingElementQName,
          filteredContainerAttributes
        )
      }

      // Process everything up to and including the control
      for (current <- beforeAfterTokens._1)
        current match {
          case "control" => handleControlStart()
          case "label"   => firstLocalLhha(LHHA.Label).foreach(handleLabel)
          case "alert"   => firstLocalLhha(LHHA.Alert).foreach(handleAlert)
          case "hint"    => firstLocalLhha(LHHA.Hint).foreach(handleHint)
          case "help"    => firstLocalLhha(LHHA.Help).foreach(handleHelp)
        }
    }

  override final def end(): Unit =
    if (isMustOutputControl(currentControl)) {

      // Process everything after the control has been shown
      for (current <- beforeAfterTokens._2)
        current match {
          case "control" => handleControlEnd()
          case "label"   => firstLocalLhha(LHHA.Label).foreach(handleLabel)
          case "alert"   => firstLocalLhha(LHHA.Alert).foreach(handleAlert)
          case "hint"    => firstLocalLhha(LHHA.Hint).foreach(handleHint)
          case "help"    => firstLocalLhha(LHHA.Help).foreach(handleHelp)
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
      controlEffectiveIdOpt   = XFormsBaseHandler.isStaticReadonly(currentControl) option getEffectiveId,
      forEffectiveIdWithNsOpt = getForEffectiveIdWithNs(lhhaAnalysis),
      requestedElementNameOpt = XFormsBaseHandler.isStaticReadonly(currentControl) option "span",
      control                 = currentControl
    )

  protected def handleAlert(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyAlert)
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        controlEffectiveIdOpt   = None,
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = None,
        control                 = currentControl
      )

  protected def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyHint)
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        controlEffectiveIdOpt   = getEffectiveId.some,
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = None,
        control                 = currentControl
      )

  protected def handleHelp(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl))
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        controlEffectiveIdOpt   = getEffectiveId.some,
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = None,
        control                 = currentControl
      )

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
    containingDocument.findControlByEffectiveId(getEffectiveId)
      .getOrElse(throw new IllegalStateException(s"control not found for effective id: `$getEffectiveId`"))

  final protected def handleAriaByAtts(atts: AttributesImpl, condition: LHHAAnalysis => Boolean): Boolean = {
    val it = ControlAjaxSupport.iterateAriaByAtts(elementAnalysis, currentControl.effectiveId, condition)(containingDocument)
    val nonEmpty = it.nonEmpty
    it foreach { case (attName, attValues) =>
      atts.addOrReplace(attName, attValues.mkString(" "))
    }
    nonEmpty
  }

  final protected def findStaticLhhaOrLhhaBy(lhhaType: LHHA): Option[LHHAAnalysis] =
    elementAnalysis.narrowTo[StaticLHHASupport]
      .flatMap(lhhaSupport => lhhaSupport.firstByOrDirectLhhaOpt(lhhaType))

  def outputStaticReadonlyField[T](xhtmlPrefix: String, localName: String = "span")(body: => T)(implicit xmlReceiver: XMLReceiver): T = {

    // NOTE: No need to handle `LHHA.Alert` as that's not handled by `handleAriaByAtts`
    def ariaByCondition(lhhaAnalysis: LHHAAnalysis): Boolean = (
      lhhaAnalysis.lhhaType != LHHA.Hint                   ||
      ! XFormsBaseHandler.isStaticReadonly(currentControl) ||
      containingDocument.staticReadonlyHint
    )

    val atts: AttributesImpl =
      List( // Q: if placeholder label?
        CLASS_QNAME       -> "xforms-field",
        TABINDEX_QNAME    -> "0",
        ROLE_QNAME        -> "textbox",
        AriaReadonlyQName -> "true",
      )

    handleAriaByAtts(atts, ariaByCondition)

    withElement(
      localName = localName,
      prefix    = xhtmlPrefix,
      uri       = XHTML_NAMESPACE_URI,
      atts      = atts
    ) {
      body
    }
  }

  private object Private {

    val beforeAfterTokens: (List[String], List[String]) =
      elementAnalysis.narrowTo[StaticLHHASupport] flatMap
      (_.beforeAfterTokensOpt)                    getOrElse
      handlerContext.documentOrder

    def firstLocalLhha(lhhaType: LHHA): Option[LHHAAnalysis] =
      elementAnalysis.narrowTo[StaticLHHASupport].iterator.flatMap(_.allDirectLhha(lhhaType).iterator).find(_.isLocal)
  }
}
