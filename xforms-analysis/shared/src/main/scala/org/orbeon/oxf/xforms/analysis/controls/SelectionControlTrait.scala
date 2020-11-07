/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.itemset.Itemset
import org.orbeon.oxf.xforms.model.StaticDataModel
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames._

trait SelectionControlTrait
  extends InputValueControl
     with SelectAppearanceTrait
     with WithChildrenTrait {

  if (element.attributeValue("selection") == "open")
    throw new ValidationException("Open selection is currently not supported.", locationData)

  val excludeWhitespaceTextNodesForCopy: Boolean =
    element.attributeValue(EXCLUDE_WHITESPACE_TEXT_NODES_QNAME) == "true"

  val isNorefresh: Boolean =
    element.attributeValue(XXFORMS_REFRESH_ITEMS_QNAME) == "false"

  final var itemsetAnalysis: Option[XPathAnalysis] = None

  def hasStaticItemset: Boolean
  def staticItemset: Option[Itemset]
  def useCopy: Boolean
  def mustEncodeValues: Option[Boolean]

  override def isAllowedBoundItem(item: om.Item): Boolean =
    if (useCopy)
      StaticDataModel.isAllowedBoundItem(item)
    else
      super.isAllowedBoundItem(item)

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    itemsetAnalysis foreach (_.freeTransientState())
  }
}

object SelectionControlUtil {

  val AttributesToPropagate = List(CLASS_QNAME, STYLE_QNAME, XXFORMS_OPEN_QNAME)
  val TopLevelItemsetQNames = Set(XFORMS_ITEM_QNAME, XFORMS_ITEMSET_QNAME, XFORMS_CHOICES_QNAME)

  def isTopLevelItemsetElement(e: Element): Boolean = TopLevelItemsetQNames(e.getQName)

  def getAttributes(itemChoiceItemset: Element): List[(QName, String)] =
    for {
      attributeName   <- AttributesToPropagate
      attributeValue = itemChoiceItemset.attributeValue(attributeName)
      if attributeValue ne null
    } yield
      attributeName -> attributeValue
}