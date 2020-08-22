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
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.SelectionControl
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlTrait
import org.orbeon.oxf.xforms.control.XFormsControl.{ControlProperty, ImmutableControlProperty}
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.event.events.{XFormsDeselectEvent, XFormsSelectEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.itemset.{Item, Itemset, ItemsetSupport}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._

import scala.util.control.NonFatal

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
  with SingleNodeFocusableTrait {

  selfControl =>

  override type Control <: SelectionControl

  // This is a var just for getBackCopy
  private[XFormsSelect1Control] var itemsetProperty: ControlProperty[Itemset] = new MutableItemsetProperty(selfControl)

  def mustEncodeValues: Boolean = XFormsSelect1Control.mustEncodeValues(containingDocument, staticControl)
  def isFullAppearance: Boolean = staticControl.isFull

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

  override def hasJavaScriptInitialization: Boolean =
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
        containingDocument.controls.getConstantItems(getPrefixedId) getOrElse {
          val newItemset = ItemsetSupport.evaluateItemset(selfControl)
          containingDocument.controls.setConstantItems(getPrefixedId, newItemset)
          newItemset
        }
      else
        // Items are stored in the control
        itemsetProperty.value()
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(
          t,
          new ExtendedLocationData(getLocationData, "evaluating itemset", element)
        )
    }

  override def evaluateExternalValue(): Unit = {

    // If the control is relevant, its internal value and itemset must be defined
    getValue   ensuring (_ ne null)
    getItemset ensuring (_ ne null)

    setExternalValue(
      if (! isStaticReadonly)
        findSelectedItem map (_.externalValue(mustEncodeValues)) orNull
      else
        findSelectedItem map (_.label.htmlValue(getLocationData)) orNull // external value is the label
    )
  }

  // Q: In theory, multiple items could have the same value and therefore be selected, right?
  def findSelectedItems: List[Item.ValueNode] =
    findSelectedItem.toList

  def findSelectedItem: Option[Item.ValueNode] =
    boundItemOpt map getCurrentItemValueFromData flatMap { current =>
      getItemset.ensuring(_ ne null).allItemsWithValueIterator(reverse = false) collectFirst {
        case (item, itemValue) if ItemsetSupport.compareSingleItemValues(
          dataValue                  = current,
          itemValue                  = itemValue,
          compareAtt                 = XFormsSelect1Control.attCompare(boundNodeOpt, _),
          excludeWhitespaceTextNodes = staticControl.excludeWhitespaceTextNodesForCopy
        ) => item
      }
    }

  // The current value depends on whether we follow `xf:copy` or `xf:value` semantics
  def getCurrentItemValueFromData(boundItem: om.Item): Item.Value[om.NodeInfo] = {
    if (staticControl.useCopy)
      Right(
        boundItem match {
          case node: om.NodeInfo => (node child Node).toList
          case _ => Nil
        }
      )
    else
      Left(getValue)
  }

  override def translateExternalValue(boundItem: om.Item, externalValue: String): Option[String] = {

    val (selectEvents, deselectEvents) =
      gatherEventsForExternalValue(getItemset, getCurrentItemValueFromData(boundItem), externalValue)

    for (currentEvent <- deselectEvents)
      Dispatch.dispatchEvent(currentEvent)

    for (currentEvent <- selectEvents)
      Dispatch.dispatchEvent(currentEvent)

    // Value is updated via `xforms-select`/`xforms-deselect` events
    // Q: Could/should this be the case for other controls as well?
    None
  }

  override def performDefaultAction(event: XFormsEvent): Unit = {
    event match {
      case deselect: XFormsDeselectEvent =>
        boundNodeOpt match {
          case Some(boundNode) =>
            deselect.itemValue match {
              case Left(_)  =>
                DataModel.setValueIfChangedHandleErrors(
                  eventTarget  = selfControl,
                  locationData = getLocationData,
                  nodeInfo     = boundNode,
                  valueToSet   = "",
                  source       = "select",
                  isCalculate  = false
                )
              case Right(v) =>

                // If the deselected value contains attributes, remove all of those from the bound node
                val (atts, other) = ItemsetSupport.partitionAttributes(v)

                if (atts.nonEmpty)
                  XFormsAPI.delete(
                    ref = atts flatMap (att => boundNode att (att.namespaceURI, att.localname))
                  )

                // If the deselected value contains a node that is not an attribute, then clear the element
                // content. We could clear the element content no matter what but this enables the use case
                // of selecting attributes independently from the element's content.
                if (other.nonEmpty)
                  XFormsAPI.delete(
                    ref = boundNode child Node
                  )
            }
          case None =>
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
        }
      case select: XFormsSelectEvent =>
        boundNodeOpt match {
          case Some(boundNode) =>
            select.itemValue match {
              case Left(v)  =>
                DataModel.setValueIfChangedHandleErrors(
                  eventTarget  = selfControl,
                  locationData = getLocationData,
                  nodeInfo     = boundNode,
                  valueToSet   = v,
                  source       = "select",
                  isCalculate  = false
                )
              case Right(v) =>
                XFormsAPI.delete(
                  ref = boundNode child Node
                )
                XFormsAPI.insert(
                  origin = v,
                  into   = List(boundNode)
                )
            }
          case None =>
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
        }
      case _ =>
    }
    super.performDefaultAction(event)
  }

  // For XFormsSelectControl
  // We should *not* use inheritance this way here!
  protected def valueControlPerformDefaultAction(event: XFormsEvent): Unit =
    super.performDefaultAction(event)

  private def gatherEventsForExternalValue(
    itemset          : Itemset,
    dataValue        : Item.Value[om.NodeInfo],
    newExternalValue : String
  ): (List[XFormsSelectEvent], List[XFormsDeselectEvent]) =
    itemset.allItemsWithValueIterator(reverse = true).foldLeft((Nil: List[XFormsSelectEvent], Nil: List[XFormsDeselectEvent])) {
      case (result @ (selected, deselected), (item, itemValue)) =>

        val itemWasSelected =
          ItemsetSupport.compareSingleItemValues(
            dataValue                  = dataValue,
            itemValue                  = itemValue,
            compareAtt                 = XFormsSelect1Control.attCompare(boundNodeOpt, _),
            excludeWhitespaceTextNodes = staticControl.excludeWhitespaceTextNodesForCopy
          )

        val itemIsSelected =
          item.externalValue(mustEncodeValues) == newExternalValue

        val getsSelected   = ! itemWasSelected &&   itemIsSelected
        val getsDeselected =   itemWasSelected && ! itemIsSelected

        val newSelected =
          if (getsSelected)
            new XFormsSelectEvent(selfControl, itemValue) :: selected
          else
            selected

        val newDeselected =
          if (getsDeselected)
            new XFormsDeselectEvent(selfControl, itemValue) :: deselected
          else
            deselected

        if (getsSelected || getsDeselected)
          (newSelected, newDeselected)
        else
          result // optimization
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

  private def mustSendItemsetUpdate(otherSelect1Control: XFormsSelect1Control): Boolean =
    if (staticControl.hasStaticItemset) {
      // There is no need to send an update:
      //
      // 1. Items are static...
      // 2. ...and they have been output statically in the HTML page
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
      } else if (! XFormsSingleNodeControl.isRelevant(selfControl)) {
        // We were and are non-relevant, no update
        false
      } else {
        // If the itemsets changed, then we need to send an update
        otherSelect1Control.getItemset != getItemset
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

        val result = itemset.asJSON(None, mustEncodeValues, staticControl.excludeWhitespaceTextNodesForCopy, getLocationData)
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
  override def findAriaByControlEffectiveId: Option[String] =
    super.findAriaByControlEffectiveId

  // Don't accept focus if we have the internal appearance
  override def isDirectlyFocusableMaybeWithToggle: Boolean =
    ! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME) && super.isDirectlyFocusableMaybeWithToggle

  override def supportAjaxUpdates: Boolean =
    ! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME)
}

object XFormsSelect1Control {

  def attCompare(boundNodeOpt: Option[om.NodeInfo], att: om.NodeInfo): Boolean =
    boundNodeOpt exists (_.getAttributeValue(att.getFingerprint) == att.getStringValue)

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