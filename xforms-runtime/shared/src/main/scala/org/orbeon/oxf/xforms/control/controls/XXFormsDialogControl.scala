/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.control.ControlLocalSupport.XFormsControlLocal
import org.orbeon.oxf.xforms.control.{Focus, XFormsControl, XFormsNoSingleNodeContainerControl}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.*
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.XMLReceiverHelper.CDATA
import org.orbeon.xforms.XFormsNames.XXFORMS_NAMESPACE_URI
import org.xml.sax.helpers.AttributesImpl

import java.{util, util as ju}


private case class XXFormsDialogControlLocal(
  var dialogVisible       : Boolean,
  var constrainToViewport : Boolean,
  var neighborControlId   : Option[String]
) extends XFormsControlLocal

// Represents an extension xxf:dialog control
class XXFormsDialogControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsNoSingleNodeContainerControl(container, parent, element, effectiveId) {

  // NOTE: Attributes logic duplicated in XXFormsDialogHandler.
  // TODO: Should be in the static state.

  // Commented out as those are not used currently.
  // private var level = Option(element.attributeValue("level")) getOrElse {
  //     // Default is "modeless" for "minimal" appearance, "modal" otherwise
  //     if (appearances(XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME)) "modeless" else "modal"
  // }
  //
  // private val close                    = ! ("false" == element.attributeValue("close"))
  // private val draggable                = ! ("false" == element.attributeValue("draggable"))

  private val defaultNeighborControlId = element.attributeValueOpt("neighbor")
  private val initiallyVisible         = element.attributeValue("visible") == "true"

  // Initial local state
  setLocal(XXFormsDialogControlLocal(initiallyVisible, constrainToViewport = false, None))

  override def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean, collector: ErrorEventCollector): Unit = {

    super.onCreate(restoreState, state, update, collector)

    state match {
      case Some(ControlState(_, _, keyValues)) =>
        setLocal(XXFormsDialogControlLocal(
          dialogVisible       = keyValues("visible") == "true",
          constrainToViewport = keyValues.get("constrain") contains "true",
          neighborControlId   = keyValues.get("neighbor")
        ))
      case None if restoreState =>
        // This can happen with xxf:dynamic, which does not guarantee the stability of ids, therefore state for
        // a particular control might not be found.
        setLocal(XXFormsDialogControlLocal(
          dialogVisible       = initiallyVisible,
          constrainToViewport = false,
          neighborControlId   = None
        ))
      case _ =>
    }
  }

  private def dialogCurrentLocal = getCurrentLocal.asInstanceOf[XXFormsDialogControlLocal]

  def isDialogVisible       = dialogCurrentLocal.dialogVisible
  def wasDialogVisible      = getInitialLocal.asInstanceOf[XXFormsDialogControlLocal].dialogVisible
  def neighborControlId     = dialogCurrentLocal.neighborControlId orElse defaultNeighborControlId
  def isConstrainToViewport = dialogCurrentLocal.constrainToViewport

  override def performTargetAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    super.performTargetAction(event, collector)
    event match {
      case dialogOpenEvent: XXFormsDialogOpenEvent =>

        if (! isDialogVisible) {
          val localForUpdate = getLocalForUpdate.asInstanceOf[XXFormsDialogControlLocal]
          localForUpdate.dialogVisible       = true
          localForUpdate.neighborControlId   = dialogOpenEvent.neighbor
          localForUpdate.constrainToViewport = dialogOpenEvent.constrainToViewport

          containingDocument.controls.markDirtySinceLastRequest(true)
          containingDocument.controls.doPartialRefresh(this, collector)

          Dispatch.dispatchEvent(new XFormsDialogShownEvent(this), collector)
        }

      case _: XXFormsDialogCloseEvent =>

        if (isDialogVisible) {
          val localForUpdate = getLocalForUpdate.asInstanceOf[XXFormsDialogControlLocal]
          localForUpdate.dialogVisible       = false
          localForUpdate.neighborControlId   = None
          localForUpdate.constrainToViewport = false

          containingDocument.controls.markDirtySinceLastRequest(false)
          containingDocument.controls.doPartialRefresh(this, collector)

          Dispatch.dispatchEvent(new XFormsDialogHiddenEvent(this), collector)
        }

      case _ =>
    }
  }

  override def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    event match {
      case _: XXFormsDialogOpenEvent =>
        // If dialog is closed and the focus is within the dialog, remove the focus
        // NOTE: Ideally, we should get back to the control that had focus before the dialog opened if possible.
        if (isDialogVisible && ! Focus.isFocusWithinContainer(this))
          Dispatch.dispatchEvent(new XFormsFocusEvent(this, Set.empty, Set.empty), collector)
      case _: XXFormsDialogCloseEvent =>
        // If dialog is open and the focus has not been set within the dialog, attempt to set the focus within
        if (! isDialogVisible && Focus.isFocusWithinContainer(this))
          Focus.removeFocus(containingDocument)
      case _ =>
    }
    super.performDefaultAction(event, collector)
  }

  override def serializeLocal: util.Map[String, String] = {

    val local = dialogCurrentLocal
    val result = new ju.HashMap[String, String](3)

    result.put("visible", local.dialogVisible.toString)
    if (local.dialogVisible) {
      result.put("constrain", local.constrainToViewport.toString)
      local.neighborControlId foreach (result.put("neighbor", _))
    }
    result
  }

  override def compareExternalUseExternalValue(previousExternalValue: Option[String], previousControl: Option[XFormsControl], collector: ErrorEventCollector): Boolean =
    previousControl match {
      case Some(other: XXFormsDialogControl) =>
        // NOTE: We only compare on isVisible as we don't support just changing other attributes for now
        other.wasDialogVisible == isDialogVisible &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl, collector)
      case _ => false
  }

  override def outputAjaxDiff(
    previousControl : Option[XFormsControl],
    content         : Option[XMLReceiverHelper => Unit],
    collector       : ErrorEventCollector
  )(implicit
    ch              : XMLReceiverHelper
  ): Unit = {

    locally {
      val atts = new AttributesImpl
      val doOutputElement = addAjaxAttributes(atts, previousControl, collector)
      if (doOutputElement)
        ch.element("xxf", XXFORMS_NAMESPACE_URI, "control", atts)
    }

    locally {
      // NOTE: This uses visible/hidden. But we could also handle this with relevant="true|false".
      // 2015-04-01: Unsure if note above still makes sense.
      val previousDialog = previousControl.asInstanceOf[Option[XXFormsDialogControl]]
      var doOutputElement = false

      val atts = new AttributesImpl
      atts.addAttribute("", "id", "id", CDATA, containingDocument.namespaceId(effectiveId))

      val visible = isDialogVisible
      if (previousControl.isEmpty || previousDialog.exists(_.wasDialogVisible != visible)) {
        atts.addAttribute("", "visibility", "visibility", CDATA, if (visible) "visible" else "hidden")
        doOutputElement = true
      }
      if (visible) {
        neighborControlId foreach { neighbor =>
          if (previousControl.isEmpty || previousDialog.exists(_.neighborControlId != neighborControlId)) {
              atts.addAttribute("", "neighbor", "neighbor", CDATA, containingDocument.namespaceId(neighbor))
              doOutputElement = true
          }
        }

        val constrain = isConstrainToViewport
        if (previousControl.isEmpty || previousDialog.exists(_.isConstrainToViewport != constrain)) {
          atts.addAttribute("", "constrain", "constrain", CDATA, constrain.toString)
          doOutputElement = true
        }
      }
      if (doOutputElement)
        ch.element("xxf", XXFORMS_NAMESPACE_URI, "dialog", atts)
    }

  }

  override def contentVisible = isDialogVisible
}