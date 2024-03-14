package org.orbeon.oxf.xml

import org.orbeon.saxon.om
import org.orbeon.saxon.pattern.NodeKindTest
import org.orbeon.saxon.tree.iter.{AxisIterator, EmptyIterator}

import scala.annotation.tailrec

/**
 * This Iterator returns a node's attributes and descendant nodes and attributes.
 */
class AttributesAndElementsIterator(start: om.NodeInfo, includeSelf: Boolean = true)
  extends Iterator[om.NodeInfo] {

  private var current = findNext()

  def next(): om.NodeInfo = {
    val result = current
    current = findNext()
    result
  }

  def hasNext: Boolean = current ne null

  private var attributes: AxisIterator = _
  private var descendants: Iterator[om.NodeInfo] = _
  private var children: AxisIterator = _

  @tailrec
  private def findNext(): om.NodeInfo = {

    // Exhaust attributes if any
    if (attributes ne null) {
      val next = attributes.next()
      if (next ne null)
        return next
      else
        attributes = null
    }

    // Exhaust descendants if any
    if (descendants ne null) {
      if (descendants.hasNext)
        return descendants.next()
      else
        descendants = null
    }

    // We have exhausted attributes and descendants
    if (children ne null) {
      // Move to next child
      val next = children.next()
      if (next ne null) {
        attributes = next.iterateAxis(om.AxisInfo.ATTRIBUTE)
        if (next.hasChildNodes)
          descendants = new AttributesAndElementsIterator(next, includeSelf = false)
        next
      } else
        null
    } else {
      // This is the start
      attributes = start.iterateAxis(om.AxisInfo.ATTRIBUTE)

      children =
        if (start.hasChildNodes)
          start.iterateAxis(om.AxisInfo.CHILD, NodeKindTest.ELEMENT)
        else
          EmptyIterator.ofNodes

      if (includeSelf)
        start
      else
        findNext()
    }
  }
}

object AttributesAndElementsIterator {
  def apply(start: om.NodeInfo, includeSelf: Boolean = true): AttributesAndElementsIterator =
    new AttributesAndElementsIterator(start, includeSelf)
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//

