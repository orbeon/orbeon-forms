/**
 * Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.msv

import org.orbeon.dom
import org.orbeon.msv.relaxng.datatype.Datatype

/**
 * Base implementation of Matcher coordinator.
 *
 * This class behaves as a parent of several other matchers, or as a composite
 * XPath matcher. Those child matchers are not directly registered to IDConstraintChecker.
 * Instead, they receive notifications through this object.
 */
class MatcherBundle(owner: IDConstraintChecker) extends Matcher(owner) {

  var children: Array[Matcher] = _
  private var depth: Int = 0

  def getDepth: Int = depth

  def startElement(elt: dom.Element): Unit = {
    depth += 1
    var i = 0
    while (i < children.length) {
      children(i).startElement(elt)
      i += 1
    }
  }

  def onAttribute(att: dom.Attribute, `type`: Datatype): Unit = {
    var i = 0
    while (i < children.length) {
      children(i).onAttribute(att, `type`)
      i += 1
    }
  }

  def endElement(`type`: Datatype): Unit = {
    var i = 0
    while (i < children.length) {
      children(i).endElement(`type`)
      i += 1
    }
    val prevDepth = depth
    depth -= 1
    if (prevDepth == 0) {
      owner.remove(this)
      onRemoved()
    }
  }

  def onRemoved(): Unit = ()
}
