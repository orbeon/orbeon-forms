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
package org.orbeon.oxf.xforms.itemset

import org.orbeon.saxon.om


class Itemset(val multiple: Boolean, val hasCopy: Boolean) extends ItemContainer {

  def iterateSelectedItems(
    dataValue                  : Item.Value[om.NodeInfo],
    compareAtt                 : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes : Boolean
  ): Iterator[Item.ValueNode] =
    allItemsWithValueIterator(reverse = false) collect {
      case (item, itemValue)
        if StaticItemsetSupport.isSelected(multiple, dataValue, itemValue, compareAtt, excludeWhitespaceTextNodes) => item
    }
}
