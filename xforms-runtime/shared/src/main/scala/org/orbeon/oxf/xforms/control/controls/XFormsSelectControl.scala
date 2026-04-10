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

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.{XFormsDeselectEvent, XFormsSelectEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.itemset
import org.orbeon.oxf.xforms.itemset.{Item, StaticItemsetSupport}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsId

import scala.collection.{mutable, Set as CSet}


class XFormsSelectControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  _effectiveId: String
) extends XFormsSelect1Control( // TODO: bad inheritance; use shared trait if needed
  container,
  parent,
  element,
  _effectiveId
) {

  selfControl =>

  import XFormsSelectControl.*

  /**
   * Set an external value. This consists of a list of space-separated tokens.
   *
   * - Itemset values which are in the list of tokens are merged with the bound control's value.
   * - Itemset values which are not in the list of tokens are removed from the bound control's value.
   */
  override def translateExternalValue(
    boundItem    : om.Item,
    externalValue: String,
    collector    : ErrorEventCollector
  ): Option[String] = {

    def filterItemsReturnValues(
      p: itemset.Item.ValueNode => Boolean
    ): List[Item.Value[om.Item]] =
      (getItemset(collector).allItemsWithValueIterator(reverse = false) filter (t => p(t._1)) map (_._2)).to(List)

    val dataItemValues =
      getCurrentItemValueFromData(boundItem, collector) match {
        case Left(dataValue) =>
          valueAsLinkedSet(dataValue).toList map Left.apply
        case Right(allDataItems) =>
          allDataItems map (dataItem => Right(List(dataItem)))
      }

    val itemsetItemValues = filterItemsReturnValues(_ => true)

    val incomingItemValues = {
      val newUIValues = valueAsSet(externalValue)
      filterItemsReturnValues(item => newUIValues(item.externalValue(mustEncodeValues)))
    }

    val (newlySelectedValues, newlyDeselectedValues, _) =
      XFormsSelectControl.updateSelection(
        dataItemValues,
        itemsetItemValues,
        incomingItemValues,
        SaxonUtils.attCompare(boundNodeOpt, _),
        staticControl.excludeWhitespaceTextNodesForCopy
      )

    // 2016-01-29: Switching order of event dispatch as `xf:select1` does it this way and XForms 1.1 says: "Newly
    // selected items receive the event xforms-select immediately after all newly deselected items receive the
    // event xforms-deselect."
    for (value <- newlyDeselectedValues)
      Dispatch.dispatchEvent(new XFormsDeselectEvent(selfControl, value), collector)

    for (value <- newlySelectedValues)
      Dispatch.dispatchEvent(new XFormsSelectEvent(selfControl, value), collector)

    // Value is updated via `xforms-select`/`xforms-deselect` events
    // Q: Could/should this be the case for other controls as well?
    None
  }

  override def markDirtyImpl(): Unit = {
    super.markDirtyImpl()
    if (! isExternalValueDirty && containingDocument.xpathDependencies.requireItemsetUpdate(staticControl, XFormsId.getEffectiveIdSuffixParts(effectiveId))) {
      // If the itemset has changed but the value has not changed, the external value might still need to be
      // re-evaluated.
      markExternalValueDirty()
    }
  }

  override def evaluateExternalValue(collector: ErrorEventCollector): Unit = {

    val selectedItems = findSelectedItems(collector)

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

  override def findSelectedItems(collector: ErrorEventCollector): List[Item.ValueNode] =
    boundItemOpt match {
      case Some(boundItem) =>
        getItemset(collector)
          .iterateSelectedItems(
            getCurrentItemValueFromData(boundItem, collector),
            SaxonUtils.attCompare(boundNodeOpt, _),
            staticControl.excludeWhitespaceTextNodesForCopy
          ).toList
      case None =>
        Nil
    }

  override def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    event match {
      case deselect: XFormsDeselectEvent =>
        boundNodeOpt match {
          case Some(boundNode) =>
            deselect.itemValue match {
              case Left(v)  =>
                DataModel.setValueIfChangedHandleErrors(
                  eventTarget  = selfControl,
                  locationData = getLocationData,
                  nodeInfo     = boundNode,
                  valueToSet   = valueAsLinkedSet(DataModel.getValue(boundNode)).diff(Set(v)) mkString " ",
                  source       = "deselect",
                  isCalculate  = false,
                  collector    = collector
                )
              case Right(v) =>
                XFormsAPI.delete(
                  ref =
                    StaticItemsetSupport.findMatchingItemsInData( // find nodes in the data matching the nodes in the item
                      dataXPathItems             = getCurrentItemValueFromData(boundNode, collector).getOrElse(throw new IllegalStateException),
                      itemXPathItems             = v,
                      findAttribute              = att => boundNodeOpt.flatMap(_.attOpt(QName(att.localname, att.prefix, att.getURI))),
                      excludeWhitespaceTextNodes = staticControl.excludeWhitespaceTextNodesForCopy
                    )
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
                  valueToSet   = valueAsLinkedSet(DataModel.getValue(boundNode)).union(Set(v)) mkString " ",
                  source       = "select",
                  isCalculate  = false,
                  collector    = collector
                )
              case Right(v) =>
                // Handle attributes separately because otherwise we insert "after", which doesn't work for attributes
                val (atts, other) = StaticItemsetSupport.partitionAttributes(v)
                if (atts.nonEmpty)
                  XFormsAPI.insert(
                    origin = atts,
                    into   = List(boundNode),
                  )
                XFormsAPI.insert(
                  origin = other,
                  into   = List(boundNode),
                  after  = boundNode child *
                )
            }
          case None =>
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
        }
      case _ =>
    }
    // Make sure not to call the method of XFormsSelect1Control
    // We should *not* use inheritance this way here!
    markVisitedOnSelectDeselect(event)
    super.valueControlPerformDefaultAction(event, collector)
  }
}

object XFormsSelectControl {

  def updateSelection(
    dataValues                 : List[Item.Value[om.NodeInfo]],
    itemsetValues              : List[Item.Value[om.Item]],
    incomingValues             : List[Item.Value[om.Item]],
    compareAtt                 : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes : Boolean
  ): (List[Item.Value[om.Item]], List[Item.Value[om.Item]], List[Item.Value[om.Item]]) = {

    def belongsTo(values: List[Item.Value[om.Item]])(value: Item.Value[om.Item]): Boolean =
      values.exists(StaticItemsetSupport.compareMultipleItemValues(_, value, compareAtt, excludeWhitespaceTextNodes))

    val newlySelectedValues   = incomingValues filterNot belongsTo(dataValues)
    val newlyDeselectedValues = itemsetValues filterNot belongsTo(incomingValues) filter belongsTo(dataValues)

    // Used for tests only
    val newInstanceValue = (dataValues ::: newlySelectedValues) filterNot belongsTo(newlyDeselectedValues)

    (newlySelectedValues, newlyDeselectedValues, newInstanceValue)
  }

  private def valueAsLinkedSet(s: String): CSet[String] = s.trimAllToOpt match {
    case Some(list) => (list split """\s+""").to(mutable.LinkedHashSet)
    case None       => Set.empty
  }

  private def valueAsSet(s: String): Set[String] = s.trimAllToOpt match {
    case Some(list) => (list split """\s+""").to(Set)
    case None       => Set.empty
  }
}