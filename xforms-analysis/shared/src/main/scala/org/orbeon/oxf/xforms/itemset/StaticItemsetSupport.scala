/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.itemset

import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.*

import scala.collection.mutable.ListBuffer


object StaticItemsetSupport {

  def isSelected(
    isMultiple                 : Boolean,
    dataValue                  : Item.Value[om.NodeInfo],
    itemValue                  : Item.Value[om.Item],
    compareAtt                 : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes : Boolean
  ): Boolean =
    if (isMultiple)
      compareMultipleItemValues(dataValue, itemValue, compareAtt, excludeWhitespaceTextNodes)
    else
      compareSingleItemValues(dataValue, itemValue, compareAtt, excludeWhitespaceTextNodes)

  def partitionAttributes(items: List[om.Item]): (List[om.NodeInfo], List[om.Item]) = {

    val hasAtt = items exists {
      case att: om.NodeInfo if att.isAttribute => true
      case _                                   => false
    }

    if (hasAtt) {
      val l = ListBuffer[om.NodeInfo]()
      val r = ListBuffer[om.Item]()

      items foreach {
        case att: om.NodeInfo if att.isAttribute => l += att
        case other                               => r += other
      }

      (l.result(), r.result())
    } else {
      (Nil, items)
    }
  }

  def compareSingleItemValues(
    dataValue                 : Item.Value[om.Item],
    itemValue                 : Item.Value[om.Item],
    compareAtt                : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes: Boolean
  ): Boolean =
    (dataValue, itemValue) match {
      case (Left(dataValue), Left(itemValue)) =>
        dataValue == itemValue
      case (Right(dataXPathItems), Right(itemXPathItems)) =>

        val (_,        otherDataItems) = partitionAttributes(dataXPathItems)
        val (attItems, otherItemItems) = partitionAttributes(itemXPathItems)

        def compareContent =
          SaxonUtils.deepCompare(
            config                     = StaticXPath.GlobalConfiguration,
            it1                        = otherDataItems.iterator,
            it2                        = otherItemItems.iterator,
            excludeWhitespaceTextNodes = excludeWhitespaceTextNodes
          )

        attItems.forall(compareAtt) && compareContent

      case _ =>
        // Mixing and matching `xf:copy` and `xf:value` is not supported for now
        false
    }

  def compareMultipleItemValues(
    dataValue                 : Item.Value[om.Item],
    itemValue                 : Item.Value[om.Item],
    compareAtt                : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes: Boolean
  ): Boolean =
    (dataValue, itemValue) match {
      case (Left(dataValue), Left(trimmedItemValue)) =>
        val trimmedControlValue = dataValue.trimAllToEmpty

        if (trimmedControlValue.isEmpty)
          trimmedItemValue.isEmpty // special case
        else
          trimmedControlValue.splitTo[scala.Iterator]().contains(trimmedItemValue)
      case (Right(dataXPathItems), Right(itemXPathItems @ _ :: _)) =>

        val (attItems, otherItems) = partitionAttributes(itemXPathItems)

        def compareContent(item: om.Item): Boolean =
          dataXPathItems.exists(dataItem =>
            SaxonUtils.deepCompare(
              config                     = StaticXPath.GlobalConfiguration,
              it1                        = Iterator(dataItem),
              it2                        = Iterator(item),
              excludeWhitespaceTextNodes = excludeWhitespaceTextNodes
            )
          )

        attItems.forall(compareAtt) && otherItems.forall(compareContent)

      case (Right(_), Right(Nil)) =>
        // Itemset construction doesn't ever produce an empty `List[om.Item]` for multiple selection
        // 2026-04-07: Then we should use `NonEmptyList` instead of `List`
        false
      case _ =>
        // Mixing and matching `xf:copy` and `xf:value` is not supported
        false
    }

  def findMatchingItemsInData(
    dataXPathItems            : List[om.Item],
    itemXPathItems            : List[om.Item],
    findAttribute             : om.NodeInfo => Option[om.NodeInfo],
    excludeWhitespaceTextNodes: Boolean
  ): List[om.NodeInfo] = {

      val (attItems, otherItems) = partitionAttributes(itemXPathItems)

      def compareContent(item: om.Item): List[om.Item] =
        dataXPathItems.filter(dataItem =>
          SaxonUtils.deepCompare(
            config                     = StaticXPath.GlobalConfiguration,
            it1                        = Iterator(dataItem),
            it2                        = Iterator(item),
            excludeWhitespaceTextNodes = excludeWhitespaceTextNodes
          )
        )

    (attItems.flatMap(findAttribute) ++ otherItems.flatMap(compareContent))
      .collect { case node: om.NodeInfo => node }
  }
}
