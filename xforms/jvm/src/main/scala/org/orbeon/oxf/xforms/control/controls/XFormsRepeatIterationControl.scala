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
package org.orbeon.oxf.xforms.control.controls


import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xforms.analysis.controls.RepeatIterationControl
import org.orbeon.oxf.xforms.control.{NoLHHATrait, XFormsControl, XFormsSingleNodeContainerControl}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.xforms.{XFormsConstants, XFormsId}
import org.xml.sax.helpers.AttributesImpl

/**
 * Represents xf:repeat iteration information.
 *
 * This is not really a control, but an abstraction for xf:repeat iterations.
 *
 * TODO: Use inheritance to make this a single-node control that doesn't hold a value.
 */
class XFormsRepeatIterationControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsSingleNodeContainerControl(
  container,
  parent,
  element,
  effectiveId
) with NoLHHATrait {

  override type Control <: RepeatIterationControl

  // Initialize based on the effective id
  private var _iterationIndex = XFormsId.getEffectiveIdSuffixParts(effectiveId).lastOption getOrElse -1
  def iterationIndex: Int = _iterationIndex

  // Set a new iteration index. This will cause the nested effective ids to update.
  // This is used to "shuffle" around repeat iterations when repeat nodesets change.
  def setIterationIndex(iterationIndex: Int): Unit = {
    if (_iterationIndex != iterationIndex) {
      _iterationIndex = iterationIndex
      updateEffectiveId()
    }
  }

  // Whether this iteration is the repeat's current iteration
  def isCurrentIteration: Boolean = iterationIndex == repeat.getIndex

  // This iteration's parent repeat control
  def repeat: XFormsRepeatControl = parent.asInstanceOf[XFormsRepeatControl]

  // Does not support refresh events for now (could make sense though)
  override def supportsRefreshEvents = false
  override def isStaticReadonly = false
  override def valueType: QName = null

  override def supportFullAjaxUpdates = false

  // Update this control's effective id and its descendants based on the parent's effective id.
  override def updateEffectiveId(): Unit = {
    // Update this iteration's effective id
    setEffectiveId(XFormsId.getIterationEffectiveId(parent.getEffectiveId, _iterationIndex))
    children foreach (_.updateEffectiveId())
  }

  override def compareExternalUseExternalValue(
    previousExternalValueOpt : Option[String],
    previousControlOpt       : Option[XFormsControl]
  ): Boolean =
    previousControlOpt match {
      case Some(previousRepeatIterationControl: XFormsRepeatIterationControl) =>
        // Ad-hoc comparison, because we basically only care about relevance changes. So we don't delegate
        // to `VisitableTrait` and `XFormsControl` implementations.
        ! mustSendIterationUpdate(Some(previousRepeatIterationControl))
      case _ => false
    }

  private def mustSendIterationUpdate(previousRepeatIterationControlOpt: Option[XFormsRepeatIterationControl]) = {
    // NOTE: We only care about relevance changes. We should care about moving iterations around, but that's not
    // handled that way yet!

    // NOTE: We output if we are NOT relevant as the client must mark non-relevant elements. Ideally, we should not
    // have non-relevant iterations actually present on the client.
    previousRepeatIterationControlOpt.isEmpty && ! isRelevant || previousRepeatIterationControlOpt.exists(_.isRelevant != isRelevant)
  }

  final override def outputAjaxDiff(
    previousControlOpt    : Option[XFormsControl],
    content               : Option[XMLReceiverHelper => Unit])(implicit
    ch                    : XMLReceiverHelper
  ): Unit = {
    val repeatIterationControl1Opt = previousControlOpt.asInstanceOf[Option[XFormsRepeatIterationControl]]
    if (mustSendIterationUpdate(repeatIterationControl1Opt)) {

      val atts = new AttributesImpl

      // Use the effective id of the parent repeat
      atts.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, parent.getEffectiveId)

      // Relevance
      atts.addAttribute("", XFormsConstants.RELEVANT_ATTRIBUTE_NAME, XFormsConstants.RELEVANT_ATTRIBUTE_NAME, XMLReceiverHelper.CDATA, isRelevant.toString)
      atts.addAttribute("", "iteration", "iteration", XMLReceiverHelper.CDATA, iterationIndex.toString)

      ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", atts)
    }
    // NOTE: in this case, don't do the regular Ajax output (maybe in the future we should to be more consistent?)
  }

  override def computeBinding(parentContext: BindingContext): BindingContext = {
    val contextStack = container.getContextStack
    contextStack.setBinding(parentContext)
    contextStack.pushIteration(iterationIndex)
  }
}
