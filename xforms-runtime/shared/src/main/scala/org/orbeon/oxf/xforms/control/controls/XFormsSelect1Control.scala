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
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.controls.{SelectionControl, SelectionControlTrait}
import org.orbeon.oxf.xforms.control.XFormsControl.{ControlProperty, ImmutableControlProperty}
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.{XFormsDeselectEvent, XFormsSelectEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.itemset.{Item, Itemset, ItemsetSupport, StaticItemsetSupport}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.oxf.xml.{SaxonUtils, XMLReceiverHelper}
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames._

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
  with ReadonlySingleNodeFocusableTrait {

  selfControl =>

  override type Control <: SelectionControl

  // This is a var just for getBackCopy
  private[XFormsSelect1Control] var itemsetProperty: ControlProperty[Itemset] = new MutableItemsetProperty(selfControl)

  def mustEncodeValues: Boolean = XFormsSelect1Control.mustEncodeValues(containingDocument, staticControl)
  def isFullAppearance: Boolean = staticControl.isFull

  override def onCreate(
    restoreState: Boolean,
    state       : Option[ControlState],
    update      : Boolean,
    collector   : ErrorEventCollector
  ): Unit = {
    super.onCreate(restoreState, state, update, collector)
    // Evaluate itemsets only if restoring dynamic state
    // NOTE: This doesn't sound like it is the right place to do this, does it?
    if (restoreState)
      getItemset(collector)
  }

  // The static control ensures that this can be present only for `xf|select1[appearance = full]`
  def getGroupName: Option[String] =
    extensionAttributeValue(XXFORMS_GROUP_QNAME)

  override def hasJavaScriptInitialization: Boolean =
    staticControl.appearances contains XFORMS_COMPACT_APPEARANCE_QNAME

  override def markDirtyImpl(): Unit = {
    super.markDirtyImpl()
    itemsetProperty.handleMarkDirty()
  }

  // Get this control's itemset
  // This requires the control to be relevant.
  def getItemset(collector: ErrorEventCollector): Itemset =
    try {
      // Non-relevant control doesn't have an itemset
      require(isRelevant)

      if (staticControl.isNorefresh)
        // Items are not automatically refreshed and stored globally
        // NOTE: Store them by prefixed id because the itemset might be different between XBL template instantiations
        containingDocument.controls.getConstantItems(getPrefixedId) getOrElse {
          val newItemset = ItemsetSupport.evaluateItemset(selfControl, collector)
          containingDocument.controls.setConstantItems(getPrefixedId, newItemset)
          newItemset
        }
      else
        // Items are stored in the control
        itemsetProperty.value(collector)
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(
          t,
          XmlExtendedLocationData(getLocationData, "evaluating itemset".some, element = Some(element))
        )
    }

  override def evaluateExternalValue(collector: ErrorEventCollector): Unit = {

    // If the control is relevant, its internal value and itemset must be defined
    getValue(collector)   ensuring (_ ne null)
    getItemset(collector) ensuring (_ ne null)

    setExternalValue(
      if (! isStaticReadonly)
        findSelectedItem(collector) map (_.externalValue(mustEncodeValues)) orNull
      else
        findSelectedItem(collector) map (i => ItemsetSupport.htmlValue(i.label, getLocationData)) orNull // external value is the label
    )
  }

  // Q: In theory, multiple items could have the same value and therefore be selected, right?
  def findSelectedItems(collector: ErrorEventCollector): List[Item.ValueNode] =
    findSelectedItem(collector).toList

  def findSelectedItem(collector: ErrorEventCollector): Option[Item.ValueNode] =
    boundItemOpt.map(getCurrentItemValueFromData(_, collector)).flatMap { current =>
      getItemset(collector).ensuring(_ ne null).allItemsWithValueIterator(reverse = false) collectFirst {
        case (item, itemValue) if StaticItemsetSupport.compareSingleItemValues(
          dataValue                  = current,
          itemValue                  = itemValue,
          compareAtt                 = SaxonUtils.attCompare(boundNodeOpt, _),
          excludeWhitespaceTextNodes = staticControl.excludeWhitespaceTextNodesForCopy
        ) => item
      }
    }

  // The current value depends on whether we follow `xf:copy` or `xf:value` semantics
  def getCurrentItemValueFromData(boundItem: om.Item, collector: ErrorEventCollector): Item.Value[om.NodeInfo] = {
    if (staticControl.useCopy)
      Right(
        boundItem match {
          case node: om.NodeInfo => (node child Node).toList
          case _ => Nil
        }
      )
    else
      Left(getValue(collector))
  }

  override def translateExternalValue(
    boundItem    : om.Item,
    externalValue: String,
    collector    : ErrorEventCollector
  ): Option[String] = {

    val (selectEvents, deselectEvents) =
      gatherEventsForExternalValue(
        getItemset(collector),
        getCurrentItemValueFromData(boundItem, collector),
        externalValue
      )

    for (currentEvent <- deselectEvents)
      Dispatch.dispatchEvent(currentEvent, collector)

    for (currentEvent <- selectEvents)
      Dispatch.dispatchEvent(currentEvent, collector)

    // Value is updated via `xforms-select`/`xforms-deselect` events
    // Q: Could/should this be the case for other controls as well?
    None
  }

  // We take selection controls to be visited as soon as a selection is made, without requiring the field to loose
  // the focus, as browsers implementation of focus events on selection controls is inconsistent, and considering the
  // field visited on selection is generally what is expected by form authors (see issue #5040)
  protected def markVisitedOnSelectDeselect(event: XFormsEvent): Unit =
    event match {
      case _: XFormsDeselectEvent | _: XFormsSelectEvent => visited = true
      case _                                             => // nop
    }

  override def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
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
                  isCalculate  = false,
                  collector    = collector
                )
              case Right(v) =>

                // If the deselected value contains attributes, remove all of those from the bound node
                val (atts, other) = StaticItemsetSupport.partitionAttributes(v)

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
                  isCalculate  = false,
                  collector    = collector
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
    markVisitedOnSelectDeselect(event)
    super.performDefaultAction(event, collector)
  }

  // For XFormsSelectControl
  // We should *not* use inheritance this way here!
  protected def valueControlPerformDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit =
    super.performDefaultAction(event, collector)

  private def gatherEventsForExternalValue(
    itemset          : Itemset,
    dataValue        : Item.Value[om.NodeInfo],
    newExternalValue : String
  ): (List[XFormsSelectEvent], List[XFormsDeselectEvent]) =
    itemset.allItemsWithValueIterator(reverse = true).foldLeft((Nil: List[XFormsSelectEvent], Nil: List[XFormsDeselectEvent])) {
      case (result @ (selected, deselected), (item, itemValue)) =>

        val itemWasSelected =
          StaticItemsetSupport.compareSingleItemValues(
            dataValue                  = dataValue,
            itemValue                  = itemValue,
            compareAtt                 = SaxonUtils.attCompare(boundNodeOpt, _),
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

  override def getBackCopy(collector: ErrorEventCollector): AnyRef = {
    val cloned = super.getBackCopy(collector).asInstanceOf[XFormsSelect1Control]
    // If we have an itemset, make sure the computed value is used as basis for comparison
    cloned.itemsetProperty = new ImmutableControlProperty(itemsetProperty.value(collector))
    cloned
  }

  override def compareExternalUseExternalValue(
    previousExternalValue: Option[String],
    previousControl      : Option[XFormsControl],
    collector            : ErrorEventCollector
  ): Boolean =
    previousControl match {
      case Some(other: XFormsSelect1Control) =>
        ! mustSendItemsetUpdate(other, collector) &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl, collector)
      case _ => false
    }

  private def mustSendItemsetUpdate(otherSelect1Control: XFormsSelect1Control, collector: ErrorEventCollector): Boolean =
    if (staticControl.staticItemset.isDefined) {
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
        otherSelect1Control.getItemset(collector) != getItemset(collector)
      }
    }

  final override def outputAjaxDiffUseClientValue(
    previousValue   : Option[String],
    previousControl : Option[XFormsValueControl],
    content         : Option[XMLReceiverHelper => Unit],
    collector       : ErrorEventCollector
  )(implicit
    ch              : XMLReceiverHelper
  ): Unit = {

    val hasNestedContent =
      mustSendItemsetUpdate(previousControl.map(_.asInstanceOf[XFormsSelect1Control]).orNull, collector)

    // Make sure the external value is up-to-date, as it might have changed due to the itemset changing
    if (hasNestedContent)
      markExternalValueDirty()

    val outputNestedContent = (ch: XMLReceiverHelper) => {

      val atts =
        getGroupName.map(name => Array("group", name)).getOrElse(Array.empty)

      ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "itemset", atts)

      val itemset = getItemset(collector)
      if (itemset ne null) {

        val result = ItemsetSupport.asJSON(itemset, None, mustEncodeValues, staticControl.excludeWhitespaceTextNodesForCopy, getLocationData)
        if (result.nonEmpty)
          ch.text(result)
      }

      ch.endElement()
    }

    // Output regular diff
    super.outputAjaxDiffUseClientValue(
      previousValue,
      previousControl,
      hasNestedContent option outputNestedContent,
      collector
    )
  }

  // https://github.com/orbeon/orbeon-forms/issues/3383
  override def findAriaByControlEffectiveIdWithNs: Option[String] =
    super.findAriaByControlEffectiveIdWithNs

  // Don't accept focus if we have the internal appearance
  override def isDirectlyFocusableMaybeWithToggle: Boolean =
    ! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME) && super.isDirectlyFocusableMaybeWithToggle

  override def supportAjaxUpdates: Boolean =
    ! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME)
}

object XFormsSelect1Control {

  // Get itemset for a selection control given either directly or by id. If the control is null or non-relevant,
  // lookup by id takes place and the control must have a static itemset or otherwise `None` is returned.
  def getInitialItemset(
    control      : XFormsSelect1Control,
    staticControl: SelectionControlTrait,
    collector    : ErrorEventCollector
  ): Option[Itemset] =
    if ((control ne null) && control.isRelevant) {
      // Control is there and relevant so just ask it (this will include static itemsets evaluation as well)
      control.getItemset(collector).some
    } else {
      // Control is not there or is not relevant, so use static itemsets
      // NOTE: This way we output static itemsets during initialization as well, even for non-relevant controls
      staticControl.staticItemset
    }

  def mustEncodeValues(containingDocument: XFormsContainingDocument, control: SelectionControlTrait): Boolean =
    control.mustEncodeValues getOrElse containingDocument.encodeItemValues
}