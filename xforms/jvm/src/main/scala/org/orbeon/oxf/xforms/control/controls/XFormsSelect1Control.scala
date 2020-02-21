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

import cats.syntax.option._
import org.orbeon.dom.Element
import org.orbeon.oxf.common.{OXFException, OrbeonLocationException}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.SelectionControl
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlTrait
import org.orbeon.oxf.xforms.control.XFormsControl.{ControlProperty, ImmutableControlProperty}
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.event.events.{XFormsDeselectEvent, XFormsSelectEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.itemset.{Item, Itemset, XFormsItemUtils}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData

import scala.collection.mutable
import scala.util.control.NonFatal

/**
 * Represents an xf:select1 control.
 */
class XFormsSelect1Control(
  container : XBLContainer,
  parent    : XFormsControl,
  element   : Element,
  id        : String
) extends XFormsSingleNodeControl(
  container,
  parent,
  element,
  id
) with XFormsValueControl
  with FocusableTrait {

  override type Control <: SelectionControl

  // This is a var just for getBackCopy
  private[XFormsSelect1Control] var itemsetProperty: ControlProperty[Itemset] = new MutableItemsetProperty(this)

  def mustEncodeValues = XFormsSelect1Control.mustEncodeValues(containingDocument, staticControl)
  def isFullAppearance = staticControl.isFull

  override def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean): Unit = {
    super.onCreate(restoreState, state, update)
    // Evaluate itemsets only if restoring dynamic state
    // NOTE: This doesn't sound like it is the right place to do this, does it?
    if (restoreState)
      getItemset
  }

  // Return the custom group name if present, otherwise return the effective id
  def getGroupName: String =
    extensionAttributeValue(XXFORMS_GROUP_QNAME) getOrElse getEffectiveId

  override def hasJavaScriptInitialization =
    staticControl.appearances contains XFORMS_COMPACT_APPEARANCE_QNAME

  override def markDirtyImpl(): Unit = {
    super.markDirtyImpl()
    itemsetProperty.handleMarkDirty()
  }

  // Get this control's itemset
  // This requires the control to be relevant.
  def getItemset: Itemset =
    try {
      // Non-relevant control doesn't have an itemset
      require(isRelevant)

      if (staticControl.isNorefresh)
        // Items are not automatically refreshed and stored globally
        // NOTE: Store them by prefixed id because the itemset might be different between XBL template instantiations
        containingDocument.getControls.getConstantItems(getPrefixedId) getOrElse {
          val newItemset = XFormsItemUtils.evaluateItemset(XFormsSelect1Control.this)
          containingDocument.getControls.setConstantItems(getPrefixedId, newItemset)
          newItemset
        }
      else
        // Items are stored in the control
        itemsetProperty.value()
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(t, new ExtendedLocationData(getLocationData, "evaluating itemset", element))
    }

  override def evaluateExternalValue(): Unit = {

    // If the control is relevant, its internal value and itemset must be defined
    val internalValue = getValue   ensuring (_ ne null)
    val itemset       = getItemset ensuring (_ ne null)

    setExternalValue(
      if (! isStaticReadonly)
        findSelectedItem map (_.externalValue(mustEncodeValues)) orNull
      else
        findSelectedItem map (_.label.htmlValue(getLocationData)) orNull // external value is the label
    )
  }

  def findSelectedItems: List[Item] = findSelectedItem.toList

  def findSelectedItem: Option[Item] = {

    val internalValue = getValue   ensuring (_ ne null)
    val itemset       = getItemset ensuring (_ ne null)

    itemset.allItemsIterator find (_.value == internalValue)
  }

  override def translateExternalValue(externalValue: String): Option[String] = {

    val existingValue = getValue

    // Find what got selected/deselected
    val (selectEvents, deselectEvents) = gatherEvents(externalValue, existingValue)

    for (currentEvent <- deselectEvents)
      Dispatch.dispatchEvent(currentEvent)

    for (currentEvent <- selectEvents)
      Dispatch.dispatchEvent(currentEvent)

    None
  }

  override def performDefaultAction(event: XFormsEvent): Unit = {
    event match {
      case select: XFormsSelectEvent =>
        boundNodeOpt match {
          case Some(boundNode) =>
            DataModel.setValueIfChangedHandleErrors(
              containingDocument = containingDocument,
              eventTarget        = this,
              locationData       = getLocationData,
              nodeInfo           = boundNode,
              valueToSet         = select.itemValue,
              source             = "select",
              isCalculate        = false
            )
          case None =>
            // Q: Can this happen?
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
        }
      case _ =>
    }
    super.performDefaultAction(event)
  }

  // For XFormsSelectControl
  // We should *not* use inheritance this way here!
  protected def valueControlPerformDefaultAction(event: XFormsEvent): Unit = {
    super.performDefaultAction(event)
  }

  private def gatherEvents(newExternalValue: String, existingValue: String): (List[XFormsSelectEvent], List[XFormsDeselectEvent]) = {

    val selectEvents   = mutable.ListBuffer[XFormsSelectEvent]()
    val deselectEvents = mutable.ListBuffer[XFormsDeselectEvent]()

    for (currentItem <- getItemset.allItemsIterator) {
      val currentItemValue = currentItem.value

      val itemWasSelected = existingValue == currentItemValue
      val itemIsSelected  = currentItem.externalValue(mustEncodeValues) == newExternalValue

      // Handle xforms-select / xforms-deselect
      if (! itemWasSelected && itemIsSelected)
        selectEvents += new XFormsSelectEvent(this, currentItemValue)
      else if (itemWasSelected && ! itemIsSelected)
        deselectEvents += new XFormsDeselectEvent(this, currentItemValue)
    }

    (selectEvents.toList, deselectEvents.toList)
  }

  override def getBackCopy: AnyRef = {
    val cloned = super.getBackCopy.asInstanceOf[XFormsSelect1Control]
    // If we have an itemset, make sure the computed value is used as basis for comparison
    cloned.itemsetProperty = new ImmutableControlProperty(itemsetProperty.value())
    cloned
  }

  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControl       : Option[XFormsControl]
  ): Boolean =
    previousControl match {
      case Some(other: XFormsSelect1Control) =>
        ! mustSendItemsetUpdate(other) &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl)
      case _ => false
    }

  private def mustSendItemsetUpdate(otherSelect1Control: XFormsSelect1Control): Boolean = {
    if (staticControl.hasStaticItemset) {
      // There is no need to send an update:
      //
      // 1. Items are static...
      // 2. ...and they have been output statically in the HTML page, directly or in repeat template
      false
    } else if (isStaticReadonly) {
      // There is no need to send an update for static readonly controls
      false
    } else {
      // There is a possible change
      if (XFormsSingleNodeControl.isRelevant(otherSelect1Control) != isRelevant) {
        // Relevance changed
        // Here we decide to send an update only if we become relevant, as the client will know that the
        // new state of the control is non-relevant and can handle the itemset on the client as it wants.
        isRelevant
      } else if (! XFormsSingleNodeControl.isRelevant(this)) {
        // We were and are non-relevant, no update
        false
      } else {
        // If the itemsets changed, then we need to send an update
        otherSelect1Control.getItemset != getItemset
      }
    }
  }

  final override def outputAjaxDiffUseClientValue(
    previousValue   : Option[String],
    previousControl : Option[XFormsValueControl],
    content         : Option[XMLReceiverHelper => Unit])(implicit
    ch              : XMLReceiverHelper
  ): Unit = {

    val hasNestedContent =
      mustSendItemsetUpdate(previousControl map (_.asInstanceOf[XFormsSelect1Control]) orNull)

    val outputNestedContent = (ch: XMLReceiverHelper) => {
      ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "itemset", Array[String]())

      val itemset = getItemset
      if (itemset ne null) {
        val result = itemset.asJSON(null, mustEncodeValues, getLocationData)
        if (result.nonEmpty)
          ch.text(result)
      }

      ch.endElement()
    }

    // Output regular diff
    super.outputAjaxDiffUseClientValue(
      previousValue,
      previousControl,
      hasNestedContent option outputNestedContent
    )
  }

  // https://github.com/orbeon/orbeon-forms/issues/3383
  override def findAriaByControlEffectiveId =
    super.findAriaByControlEffectiveId

  // Don't accept focus if we have the internal appearance
  override def focusableControls =
    if (! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME))
      super.focusableControls
    else
      Iterator.empty

  override def supportAjaxUpdates =
    ! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME)
}

object XFormsSelect1Control {

  // Get itemset for a selection control given either directly or by id. If the control is null or non-relevant,
  // lookup by id takes place and the control must have a static itemset or otherwise `None` is returned.
  def getInitialItemset(control: XFormsSelect1Control, staticControl: SelectionControlTrait): Option[Itemset] =
    if ((control ne null) && control.isRelevant) {
      // Control is there and relevant so just ask it (this will include static itemsets evaluation as well)
      control.getItemset.some
    } else {
      // Control is not there or is not relevant, so use static itemsets
      // NOTE: This way we output static itemsets during initialization as well, even for non-relevant controls
      staticControl.staticItemset
    }

  def mustEncodeValues(containingDocument: XFormsContainingDocument, control: SelectionControlTrait): Boolean =
    control.mustEncodeValues getOrElse containingDocument.encodeItemValues
}