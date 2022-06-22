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
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, LhhaControlRef, LhhaPlacementType}
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
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  elementAnalysis: LHHAAnalysis,
  handlerContext : HandlerContext
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
    def mustOmitStaticReadonlyHint(currentControlOpt: Option[XFormsControl]): Boolean =
      elementAnalysis.lhhaType == LHHA.Hint && ! containingDocument.staticReadonlyHint && XFormsBaseHandler.isStaticReadonly(currentControlOpt.orNull)

    elementAnalysis.lhhaPlacementType match {
      case LhhaPlacementType.External(_, _, Some(_)) =>

        // In this case, we handle our own value and don't ask it to the (repeated) controls, since there might be
        // zero, one, or several of them. We also currently don't handle constraint classes.

        // This duplicates code in `XFormsControlLifecycleHandler` as this handler doesn't derive from it.
        val currentControl =
          containingDocument.getControlByEffectiveId(lhhaEffectiveId) ensuring (_ ne null)

        // Case where the LHHA is external and in a shallower level of nesting of repeats.
        // NOTE: In this case, we don't output a `for` attribute. Instead, the repeated control will use
        // `aria-*` attributes to point to this element.

        if (! mustOmitStaticReadonlyHint(currentControl.some)) {
          val containerAtts =
            getContainerAttributes(uri, localname, attributes, lhhaEffectiveId, elementAnalysis, currentControl, None)

          withElement(
            localName = lhhaElementName(elementAnalysis.lhhaType),
            prefix    = handlerContext.findXHTMLPrefix,
            uri       = XHTML_NAMESPACE_URI,
            atts      = containerAtts
          ) {
            for {
              currentLHHAControl <- currentControl.narrowTo[XFormsLHHAControl]
              externalValue      <- currentLHHAControl.externalValueOpt
              if externalValue.nonEmpty
            } locally {
              if (elementAnalysis.element.attributeValueOpt("mediatype") contains "text/html")
                XFormsCrossPlatformSupport.streamHTMLFragment(externalValue, currentLHHAControl.getLocationData, handlerContext.findXHTMLPrefix)
              else
                xmlReceiver.characters(externalValue.toCharArray, 0, externalValue.length)
            }
          }
        }

      case LhhaPlacementType.External(directTargetControl, lhhaControlRef, None) =>

        // One question is what control do we ask for the value of the LHHA? When we have a `xxbl:label-for`, we attach
        // the LHHA to both the direct control (so the XBL component itself) and the destination control if there is
        // one. This means that both will evaluate their LHHA values. We should be able to ask either one, is this
        // correct?

        val effectiveTargetControlOpt: Option[XFormsControl] =
          Controls.resolveControlsById(containingDocument, lhhaEffectiveId, directTargetControl.staticId, followIndexes = true).headOption

        if (! mustOmitStaticReadonlyHint(effectiveTargetControlOpt)) {

          val labelForEffectiveIdWithNsOpt =
            elementAnalysis.lhhaType == LHHA.Label flatOption
              XXFormsComponentHandler.findLabelForEffectiveIdWithNs(lhhaControlRef, XFormsId.getEffectiveIdSuffix(lhhaEffectiveId), handlerContext)

          handleLabelHintHelpAlert(
            lhhaAnalysis            = elementAnalysis,
            elemEffectiveIdOpt      = lhhaEffectiveId.some,
            forEffectiveIdWithNsOpt = labelForEffectiveIdWithNsOpt,
            requestedElementNameOpt = None,
            controlOrNull           = effectiveTargetControlOpt.orNull, // to get the value; Q: When can this be `null`?
            isExternal              = true
          )
        }

      case LhhaPlacementType.Local(_, _) =>
        // Q: Can this happen? Do we match on local LHHA?
        // 2020-11-13: This seems to happen.
        // 2022-06-08: still happens! `currentControl eq null`
    }
  }
}

object XFormsLHHAHandler {

  def findTargetControlForEffectiveIdWithNs(
    handlerContext: HandlerContext,
    targetControl : ElementAnalysis
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

    // Push/pop component context so that handler resolution works
    if (! targetControl.scope.isTopLevelScope)
      handlerContext.pushComponentContext(targetControl.scope.scopeId)
    try
      handlerContext.controller.findHandlerFromElem(targetControl.element) match {
        case Some(handler: XFormsControlLifecycleHandler) => handler.getForEffectiveIdWithNs
        case _                                           => None
      }
    finally
      if (! targetControl.scope.isTopLevelScope)
        handlerContext.popComponentContext()
  }
}