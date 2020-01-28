/**
 * Copyright (C) 2009 Orbeon, Inc.
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
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.events.{XFormsDeselectEvent, XFormsSelectEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.xbl.XBLContainer

import scala.collection.{mutable, Set => CSet}
import scala.collection.compat._

/**
 * Represents an xf:select control.
 *
 * xf:select represents items as a list of space-separated tokens.
 */
class XFormsSelectControl(
  container : XBLContainer,
  parent    : XFormsControl,
  element   : Element,
  id        : String
) extends XFormsSelect1Control(
  container,
  parent,
  element,
  id
) {

  import XFormsSelectControl._

  /**
   * Set an external value. This consists of a list of space-separated tokens.
   *
   * - Itemset values which are in the list of tokens are merged with the bound control's value.
   * - Itemset values which are not in the list of tokens are removed from the bound control's value.
   */
  override def translateExternalValue(externalValue: String): Option[String] = {

    val existingValues = valueAsLinkedSet(getValue)
    val itemsetValues  = mutable.LinkedHashSet(getItemset.allItemsIterator map (_.value) toList: _*)

    val incomingValuesFiltered = {
      val newUIValues = valueAsSet(externalValue)

      val matches: Item => Boolean =
        if (mustEncodeValues)
          item => newUIValues(item.position.toString)
        else
          item => newUIValues(item.value)

      mutable.LinkedHashSet(getItemset.allItemsIterator filter matches map (_.value) toList: _*)
    }

    val (newlySelectedValues, newlyDeselectedValues, _) =
      updateSelection(existingValues, itemsetValues, incomingValuesFiltered)

    // 2016-01-29: Switching order of event dispatch as `xf:select1` does it this way and XForms 1.1 says: "Newly
    // selected items receive the event xforms-select immediately after all newly deselected items receive the
    // event xforms-deselect."
    for (value <- newlyDeselectedValues)
      Dispatch.dispatchEvent(new XFormsDeselectEvent(this, value))

    for (value <- newlySelectedValues)
      Dispatch.dispatchEvent(new XFormsSelectEvent(this, value))

    // Value is updated via `xforms-select`/`xforms-deselect` events
    None
  }

  override def markDirtyImpl(): Unit = {
    super.markDirtyImpl()
    if (! isExternalValueDirty && containingDocument.getXPathDependencies.requireItemsetUpdate(staticControl, effectiveId)) {
      // If the itemset has changed but the value has not changed, the external value might still need to be
      // re-evaluated.
      markExternalValueDirty()
    }
  }

  override def evaluateExternalValue(): Unit = {

    val selectedItems = findSelectedItems

    val updatedValue =
      if (selectedItems.isEmpty) {
        ""
      } else {

        val localMustEncodeValues = mustEncodeValues

        // NOTE: In encoded mode, external values are guaranteed to be distinct, but in non-encoded mode,
        // there might be duplicates.
        selectedItems.iterator map (_.externalValue(localMustEncodeValues)) mkString " "
      }

    setExternalValue(updatedValue)
  }

  override def findSelectedItems: List[Item] = {

    // If the control is relevant, its internal value and itemset must be defined
    val internalValue = getValue   ensuring (_ ne null)
    val itemset       = getItemset ensuring (_ ne null)

    if (internalValue == "") {
        // Nothing is selected
        Nil
      } else {
        // Values in the itemset
        val instanceValues = valueAsSet(internalValue)

        // All itemset external values for which the value exists in the instance
        val intersection =
          for {
            item <- itemset.allItemsIterator
            if instanceValues(item.value)
          } yield
            item

        intersection.to(List)
      }
  }

  override def performDefaultAction(event: XFormsEvent): Unit = {
    event match {
      case deselect: XFormsDeselectEvent =>
        boundNodeOpt match {
          case Some(boundNode) =>
            DataModel.setValueIfChangedHandleErrors(
              containingDocument = containingDocument,
              eventTarget        = this,
              locationData       = getLocationData,
              nodeInfo           = boundNode,
              valueToSet         = (valueAsLinkedSet(DataModel.getValue(boundNode)) - deselect.itemValue) mkString " ",
              source             = "deselect",
              isCalculate        = false
            )
          case None =>
            // Q: Can this happen?
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
        }
      case select: XFormsSelectEvent =>
        boundNodeOpt match {
          case Some(boundNode) =>
            DataModel.setValueIfChangedHandleErrors(
              containingDocument = containingDocument,
              eventTarget        = this,
              locationData       = getLocationData,
              nodeInfo           = boundNode,
              valueToSet         = (valueAsLinkedSet(DataModel.getValue(boundNode)) + select.itemValue) mkString " ",
              source             = "select",
              isCalculate        = false
            )
          case None =>
            // Q: Can this happen?
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
        }
      case _ =>
    }
    // Make sure not to call the method of XFormsSelect1Control
    // We should *not* use inheritance this way here!
    super.valueControlPerformDefaultAction(event)
  }
}

object XFormsSelectControl {

  def updateSelection(
    existingValues         : CSet[String],
    itemsetValues          : CSet[String],
    incomingValuesFiltered : CSet[String]
  ) = {

    val newlySelectedValues   = incomingValuesFiltered -- existingValues
    val newlyDeselectedValues = itemsetValues -- incomingValuesFiltered intersect existingValues

    val newInstanceValue = existingValues ++ newlySelectedValues -- newlyDeselectedValues

    (newlySelectedValues, newlyDeselectedValues, newInstanceValue)
  }

  private def valueAsLinkedSet(s: String) = s.trimAllToOpt match {
    case Some(list) => mutable.LinkedHashSet(list split """\s+""": _*)
    case None => Set[String]()
  }

  private def valueAsSet(s: String) = s.trimAllToOpt match {
    case Some(list) => list split """\s+""" toSet
    case None => Set[String]()
  }
}