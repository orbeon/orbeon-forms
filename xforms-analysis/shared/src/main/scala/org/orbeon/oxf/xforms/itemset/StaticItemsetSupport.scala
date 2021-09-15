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

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId

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
      compareMultipleItemValues(dataValue, itemValue)
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
    dataValue                  : Item.Value[om.Item],
    itemValue                  : Item.Value[om.Item],
    compareAtt                 : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes : Boolean
  ): Boolean =
    (dataValue, itemValue) match {
      case (Left(dataValue), Left(itemValue)) =>
        dataValue == itemValue
      case (Right(dataXPathItems), Right(itemXPathItems)) =>

        val (attItems, otherItems) = partitionAttributes(itemXPathItems)

        def compareContent =
          SaxonUtils.deepCompare(
            config                     = StaticXPath.GlobalConfiguration,
            it1                        = dataXPathItems.iterator,
            it2                        = otherItems.iterator,
            excludeWhitespaceTextNodes = excludeWhitespaceTextNodes
          )

        (attItems forall compareAtt) && compareContent

      case _ =>
        // Mixing and matching `xf:copy` and `xf:value` is not supported for now
        false
    }

  private def compareMultipleItemValues(
    dataValue : Item.Value[om.NodeInfo],
    itemValue : Item.Value[om.Item]
  ): Boolean =
    (dataValue, itemValue) match {
      case (Left(dataValue), Left(trimmedItemValue)) =>
        val trimmedControlValue = dataValue.trimAllToEmpty

        if (trimmedControlValue.isEmpty)
          trimmedItemValue.isEmpty // special case
        else
          trimmedControlValue.splitTo[scala.Iterator]() contains trimmedItemValue
      case (Right(allDataItems), Right(firstItemXPathItem :: _)) =>
        allDataItems exists { oneDataXPathItem =>
          SaxonUtils.deepCompare(
            config                     = StaticXPath.GlobalConfiguration,
            it1                        = Iterator(oneDataXPathItem),
            it2                        = Iterator(firstItemXPathItem),
            excludeWhitespaceTextNodes = false
          )
        }
      case (Right(_), Right(Nil)) =>
        // Itemset construction doesn't ever produce an empty `List[om.Item]` for multiple selection
        false
      case _ =>
        // Mixing and matching `xf:copy` and `xf:value` is not supported
        false
    }
}
