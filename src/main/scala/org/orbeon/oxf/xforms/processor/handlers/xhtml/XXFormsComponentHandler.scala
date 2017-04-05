/**
 * Copyright (C) 2007 Orbeon, Inc.
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

import java.lang.StringBuilder

import org.orbeon.oxf.xforms.XFormsConstants.COMPONENT_SEPARATOR
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.LHHAC
import org.orbeon.oxf.xml._
import org.xml.sax.{Attributes, Locator}

class XXFormsComponentHandler extends XFormsControlLifecyleHandler(false) {

  private var elementName: String = _
  private var elementQName: String = _

  protected override def getContainingElementName = elementName
  protected override def getContainingElementQName = elementQName

  private lazy val binding =
    containingDocument.getStaticOps.getBinding(getPrefixedId) getOrElse (throw new IllegalStateException)

  private lazy val handleLHHA =
    binding.abstractBinding.modeLHHA && ! binding.abstractBinding.modeLHHACustom

  private def hasLabelFor =
    binding.abstractBinding.labelFor.isDefined

  override def init(uri: String, localname: String, qName: String, attributes: Attributes, matched: AnyRef): Unit = {
    super.init(uri, localname, qName, attributes, matched)

    elementName  = binding.abstractBinding.containerElementName
    elementQName = XMLUtils.buildQName(handlerContext.findXHTMLPrefix, elementName)
  }

  protected override def addCustomClasses(classes: StringBuilder, control: XFormsControl): Unit = {
    if (classes.length != 0)
      classes.append(' ')

    classes.append(binding.abstractBinding.cssClasses)
  }

  protected def handleControlStart(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ): Unit = {

    val prefixedId = getPrefixedId
    val controller = handlerContext.getController

    handlerContext.pushComponentContext(prefixedId)

    // Process shadow content
    XXFormsComponentHandler.processShadowTree(controller, binding.templateTree)
  }

  protected override def handleControlEnd(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ) = handlerContext.popComponentContext()

  protected override def handleLabel() =
    if (handleLHHA && ! LHHAAnalysis.hasLHHAPlaceholder(containingDocument.getStaticOps.findControlAnalysis(getPrefixedId).get, "label")) {
      if (hasLabelFor) {
        super.handleLabel()
      } else {
          handleLabelHintHelpAlert(
            getStaticLHHA(getPrefixedId, LHHAC.LABEL),
            getEffectiveId,
            getForEffectiveId(getEffectiveId),
            LHHAC.LABEL,
            "span",
            currentControlOrNull,
            isTemplate,
            false
          )
      }
    }

  protected override def handleAlert() = if (handleLHHA) super.handleAlert()
  protected override def handleHint()  = if (handleLHHA) super.handleHint()
  protected override def handleHelp()  = if (handleLHHA) super.handleHelp()

  // If there is a label-for, use that, otherwise don't use @for as we are not pointing to an HTML form control
  // TODO: Most of this should be done statically, not dynamically. See also `findTargetControlFor`.
  override def getForEffectiveId(effectiveId: String) = {

    val labelForStaticIdOpt = binding.abstractBinding.labelFor

    val staticTargetAndLabelForPrefixedIdOpt =
      for {
        labelForStaticId   ← labelForStaticIdOpt
        labelForPrefixedId ← binding.innerScope.prefixedIdForStaticIdOpt(labelForStaticId)
        staticTarget       ← containingDocument.getStaticOps.findControlAnalysis(labelForPrefixedId)
      } yield
        (staticTarget, labelForPrefixedId)

    staticTargetAndLabelForPrefixedIdOpt match {
      case Some((staticTarget, labelForPrefixedId)) ⇒
        // `label-for` is known statically
        for {
          currentControl   ← currentControlOpt // can be missing if we are in template
          targetControlFor ← {
            // Assume the target is within the same repeat iteration
            val suffix              = getEffectiveIdSuffixWithSeparator(currentControl.getEffectiveId)
            val labelForEffectiveId = labelForPrefixedId + suffix

            // Push/pop component context so that handler resolution works
            handlerContext.pushComponentContext(getPrefixedId)
            try XFormsLHHAHandler.findTargetControlForEffectiveId(handlerContext, staticTarget, labelForEffectiveId)
            finally handlerContext.popComponentContext()
          }
        } yield
          targetControlFor
      case None ⇒
        // `label-for` is now known statically, assume it's a nested HTML element
        for {
          labelForStaticId ← labelForStaticIdOpt
          currentControl   ← currentControlOpt // can be missing if we are in template
        } yield
          getRelatedEffectiveId(currentControl.getEffectiveId + COMPONENT_SEPARATOR, labelForStaticId)
    }
  } orNull
}

object XXFormsComponentHandler {

  def processShadowTree(controller: ElementHandlerController, templateTree: SAXStore): Unit = {
    // Tell the controller we are providing a new body
    controller.startBody()

    // Forward shadow content to handler
    // TODO: Handle inclusion/namespaces with XIncludeProcessor instead of custom code.
    templateTree.replay(new EmbeddedDocumentXMLReceiver(controller) {

      var level = 0

      override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

        if (level != 0)
          super.startElement(uri, localname, qName, attributes)

        level += 1
      }

      override def endElement(uri: String, localname: String, qName: String): Unit = {

        level -= 1

        if (level != 0)
          super.endElement(uri, localname, qName)
      }

      override def setDocumentLocator(locator: Locator): Unit = {
        // NOP for now. In the future, we should push/pop the locator on ElementHandlerController
      }
    })

    // Tell the controller we are done with the new body
    controller.endBody()
  }
}