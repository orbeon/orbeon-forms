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

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xforms.analysis.controls.*
import org.orbeon.oxf.xforms.analysis.{LhhaControlRef, LhhaPlacementType}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.*
import org.orbeon.xforms.XFormsId
import org.xml.sax.{Attributes, Locator}
import shapeless.syntax.typeable.*

import java.lang as jl


class XXFormsComponentHandler(
  uri           : String,
  localname     : String,
  qName         : String,
  localAtts     : Attributes,
  matched       : ComponentControl,
  handlerContext: HandlerContext
) extends
  XFormsControlLifecycleHandler(
    uri,
    localname,
    qName,
    localAtts,
    matched,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) {

  import XXFormsComponentHandler._

  private lazy val placeHolderInfo: Option[PlaceHolderInfo] =
    matched.cast[StaticLHHASupport].flatMap(PlaceHolderInfo.placeHolderValueOpt(_, currentControl))

  override def getContainingElementName: String =
    matched.commonBinding.containerElementName

  protected override def getContainingElementQName: String =
    XMLUtils.buildQName(handlerContext.findXHTMLPrefix, matched.commonBinding.containerElementName)

  protected override def addCustomClasses(classes: jl.StringBuilder, control: XFormsControl): Unit = {
    if (classes.length != 0)
      classes.append(' ')

    classes.append(matched.commonBinding.cssClasses)
    // https://github.com/orbeon/orbeon-forms/issues/6707
    // If the component doesn't have a direct binding, it doesn't have a CSS name either. In that case, we still want to
    // add a CSS class based on the element name.
    if (matched.commonBinding.cssName.isEmpty) {
      classes.append(" xbl-")
      classes.append(matched.element.getQName.qualifiedName.replace(':', '-'))
    }
  }

  // This scenario is probably not useful right now, see:
  // https://github.com/orbeon/orbeon-forms/issues/5367
//  override protected def addCustomAtts(atts: AttributesImpl): Unit =
//    if (staticControl.commonBinding.modeLHHA && staticControl.commonBinding.labelFor.isEmpty) {
//      atts.addAttribute("", "tabindex", "tabindex", XMLReceiverHelper.CDATA, "0")
//      atts.addAttribute("", "role",     "role",     XMLReceiverHelper.CDATA, "group")
//      handleAriaByAtts(atts, XFormsLHHAHandler.coreControlLhhaByCondition)
//    }

  protected def handleControlStart(): Unit = {

    val prefixedId = getPrefixedId
    val controller = handlerContext.controller

    handlerContext.pushComponentContext(prefixedId)

    // Process shadow content
    matched.bindingOpt foreach { binding =>
      XXFormsComponentHandler.processShadowTree(controller, binding.templateTree)
    }
  }

  protected override def handleControlEnd(): Unit =
    handlerContext.popComponentContext()

  protected override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit =
    if (matched.commonBinding.standardLhhaAsSet(LHHA.Label) && ! placeHolderInfo.exists(_.isLabelPlaceholder)) { // also implied: label is local (from `XFormsControlLifecycleHandler`)

      val staticReadonly          = XFormsBaseHandler.isStaticReadonly(currentControl)
      val forEffectiveIdWithNsOpt = if (staticReadonly) None else getForEffectiveIdWithNs(lhhaAnalysis)

      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        controlEffectiveIdOpt   = forEffectiveIdWithNsOpt.isEmpty option getEffectiveId, // id could be omitted if unused
        forEffectiveIdWithNsOpt = forEffectiveIdWithNsOpt,
        requestedElementNameOpt = forEffectiveIdWithNsOpt.isEmpty option "span",
        control                 = currentControl
      )
    }

  protected override def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (matched.commonBinding.standardLhhaAsSet(LHHA.Hint) && placeHolderInfo.forall(_.isLabelPlaceholder))
      super.handleHint(lhhaAnalysis)

  protected override def handleAlert(lhhaAnalysis: LHHAAnalysis): Unit = if (matched.commonBinding.standardLhhaAsSet(LHHA.Alert)) super.handleAlert(lhhaAnalysis)
  protected override def handleHelp(lhhaAnalysis: LHHAAnalysis) : Unit = if (matched.commonBinding.standardLhhaAsSet(LHHA.Help))  super.handleHelp(lhhaAnalysis)

  // If there is a `label-for`, use that, otherwise don't use `@for` as we are not pointing to an HTML form control
  // NOTE: Used by `handleLabel()` above only if there is a local LHHA, and by `findTargetControlForEffectiveId`.
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] =
    mustFindLabelForEffectiveId(lhhaAnalysis.lhhaPlacementType) flatOption {
      val currentControlSuffix = XFormsId.getEffectiveIdSuffixWithSeparator(currentControl.effectiveId)
      XFormsLHHAHandler.findLabelForEffectiveIdWithNs(lhhaAnalysis, currentControlSuffix, handlerContext)
    }
}

object XXFormsComponentHandler {

  // https://developer.mozilla.org/en-US/docs/Web/Guide/HTML/Content_categories#labelable
//  val labelableElemName = Set("button", "input", "meter", "output", "progress", "select", "textarea")

  // Below, restrict to `CoreControl`, which is our approximation of our native "labelable" controls.
  // https://github.com/orbeon/orbeon-forms/issues/5367
  private def mustFindLabelForEffectiveId(lhhaPlacementType: LhhaPlacementType): Boolean =
    lhhaPlacementType match {
      case LhhaPlacementType.Local(_, LhhaControlRef.Control(_: AttributeControl)) => false
      case LhhaPlacementType.Local(directTargetControl, LhhaControlRef.Control(targetControl: CoreControl))
        if targetControl ne directTargetControl                                    => true
      case LhhaPlacementType.Local(_, LhhaControlRef.PrefixedId(_))                => true
      case _                                                                       => false
    }

  def processShadowTree[Ctx](controller: ElementHandlerController[Ctx], templateTree: SAXStore): Unit = {
    // Tell the controller we are providing a new body
    controller.startBody()

    // Forward shadow content to handler
    // TODO: Handle inclusion/namespaces with XIncludeProcessor instead of custom code.
    templateTree.replay(new EmbeddedDocumentXMLReceiver(controller) {

      var level = 0

      override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

        if (level != 0) // skip `xbl:template` element
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