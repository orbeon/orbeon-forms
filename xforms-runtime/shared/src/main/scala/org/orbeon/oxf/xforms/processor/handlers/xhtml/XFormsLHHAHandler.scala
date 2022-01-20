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
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis}
import org.orbeon.oxf.xforms.control.controls.XFormsLHHAControl
import org.orbeon.oxf.xforms.control.{Controls, XFormsControl}
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.xml.sax.Attributes
import shapeless.syntax.typeable._


/**
 * Handler for label, help, hint and alert when those are placed outside controls.
 */
class XFormsLHHAHandler(
  uri             : String,
  localname       : String,
  qName           : String,
  localAtts       : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    localAtts,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) {

  import XFormsLHHAHandler._

  override def start(): Unit = {

    val lhhaEffectiveId = getEffectiveId

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    // For https://github.com/orbeon/orbeon-forms/issues/3989
    def mustOmitStaticReadonlyHint(staticLhha: LHHAAnalysis, currentControlOpt: Option[XFormsControl]): Boolean =
      staticLhha.lhhaType == LHHA.Hint && ! containingDocument.staticReadonlyHint && XFormsBaseHandler.isStaticReadonly(currentControlOpt.orNull)

    elementAnalysis match {
      case staticLhha: LHHAAnalysis if staticLhha.isForRepeat =>

        val currentControl =
          containingDocument.getControlByEffectiveId(getEffectiveId) ensuring (_ ne null)

        // Case where the LHHA has a dynamic representation and is in a lower nesting of repeats.
        // NOTE: In this case, we don't output a `for` attribute. Instead, the repeated control will use
        // `aria-*` attributes to point to this element.

        if (! mustOmitStaticReadonlyHint(staticLhha, currentControl.some)) {
          val containerAtts =
            getContainerAttributes(uri, localname, attributes, getPrefixedId, getEffectiveId, elementAnalysis, currentControl, None)

          withElement(
            localName = lhhaElementName(staticLhha.lhhaType),
            prefix    = handlerContext.findXHTMLPrefix,
            uri       = XHTML_NAMESPACE_URI,
            atts      = containerAtts
          ) {
            for {
              currentLHHAControl <- currentControl.narrowTo[XFormsLHHAControl]
              externalValue      <- currentLHHAControl.externalValueOpt
              if externalValue.nonEmpty
            } locally {
              if (staticLhha.element.attributeValueOpt("mediatype") contains "text/html") {
                XFormsCrossPlatformSupport.streamHTMLFragment(externalValue, currentLHHAControl.getLocationData, handlerContext.findXHTMLPrefix)
              } else {
                xmlReceiver.characters(externalValue.toCharArray, 0, externalValue.length)
              }
            }
          }
        }

      case staticLhha: LHHAAnalysis if ! staticLhha.isForRepeat && ! staticLhha.isLocal =>

        // Non-repeated case of an external label.
        // Here we have a `for` attribute.

        def resolveControlOpt(staticControl: ElementAnalysis) =
          Controls.resolveControlsById(containingDocument, lhhaEffectiveId, staticControl.staticId, followIndexes = true).headOption collect {
            case control: XFormsControl => control
          }

        val effectiveTargetControlOpt =
          staticLhha.effectiveTargetControlOrPrefixedIdOpt match {
            case Some(Left(effectiveTargetControl)) => resolveControlOpt(effectiveTargetControl)
            case Some(Right(_))                     => None
            case None                               => resolveControlOpt(staticLhha.directTargetControl)
          }

        if (! mustOmitStaticReadonlyHint(staticLhha, effectiveTargetControlOpt)) {
          val forEffectiveIdWithNsOpt =
            staticLhha.lhhaType == LHHA.Label option {
              staticLhha.effectiveTargetControlOrPrefixedIdOpt match {
                case Some(Left(effectiveTargetControl)) =>
                  findTargetControlForEffectiveIdWithNs(
                    handlerContext,
                    effectiveTargetControl,
                    XFormsId.getRelatedEffectiveId(lhhaEffectiveId, effectiveTargetControl.staticId)
                  )
                case Some(Right(targetPrefixedId)) =>
                  Some(XFormsId.getRelatedEffectiveId(lhhaEffectiveId, XFormsId.getStaticIdFromId(targetPrefixedId)))
                case None =>
                  findTargetControlForEffectiveIdWithNs(
                    handlerContext,
                    staticLhha.directTargetControl,
                    XFormsId.getRelatedEffectiveId(lhhaEffectiveId, staticLhha.directTargetControl.staticId)
                  )
              }
            }

          handleLabelHintHelpAlert(
            lhhaAnalysis             = staticLhha,
            targetControlEffectiveId = XFormsId.getRelatedEffectiveId(lhhaEffectiveId, staticLhha.directTargetControl.staticId), // `id` placed on the label itself
            forEffectiveIdWithNs     = forEffectiveIdWithNsOpt.flatten,
            lhha                     = staticLhha.lhhaType,
            requestedElementNameOpt  = None,
            controlOrNull            = effectiveTargetControlOpt.orNull, // to get the value; Q: When can this be `null`?
            isExternal               = true
          )
        }

      case _ => // `if staticLhha.isLocal && ! staticLhha.isForRepeat`
        // Q: Can this happen? Do we match on local LHHA?
        // 2020-11-13: This seems to happen.
    }
  }
}

object XFormsLHHAHandler {

  def findTargetControlForEffectiveIdWithNs(
    handlerContext           : HandlerContext,
    targetControl            : ElementAnalysis,
    targetControlEffectiveId : String
  ): Option[String] = {

    // The purpose of this code is to identify the id of the target of the `for` attribute for the given target
    // control. In order to do that, we:
    //
    // - find which handler will process that control
    // - instantiate that handler
    // - so we can call `getForEffectiveId` on it
    //
    // NOTE: A possibly simpler better solution would be to always use the `foo$bar$$c.1-2-3` scheme for the `@for` id
    // of a control.
    handlerContext.controller.findHandlerFromElem(targetControl.element) match {
      case Some(handler: XFormsControlLifecyleHandler) => handler.getForEffectiveIdWithNs(targetControlEffectiveId)
      case _                                           => None
    }
  }
}