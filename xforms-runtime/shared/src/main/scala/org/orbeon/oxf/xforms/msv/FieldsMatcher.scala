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
import org.orbeon.msv.driver.textui.Debug
import org.orbeon.msv.grammar.xmlschema.{KeyConstraint, KeyRefConstraint}

/**
 * Coordinator of FieldMatcher.
 */
class FieldsMatcher(
  val selector : SelectorMatcher,
  val element  : dom.Element
) extends MatcherBundle(selector.owner) {

  children = selector.idConst.fields.map(new FieldMatcher(this, _))

  override def onRemoved(): Unit = {
    val values = new Array[AnyRef](children.length)

    var i = 0
    while (i < children.length) {
      val v = children(i).asInstanceOf[FieldMatcher].value
      if (v == null) {
        if (!selector.idConst.isInstanceOf[KeyConstraint])
          return

        owner.reportError(
          element,
          IDConstraintChecker.ERR_UNMATCHED_KEY_FIELD,
          Array(
            selector.idConst.namespaceURI,
            selector.idConst.localName,
            Integer.valueOf(i + 1)
          )
        )
        return
      }
      values(i) = v
      i += 1
    }

    if (Debug.debug)
      println(s"fields collected for ${selector.idConst.localName}")

    val kv = new KeyValue(values, element)
    if (owner.addKeyValue(selector, kv))
      return

    if (selector.idConst.isInstanceOf[KeyRefConstraint])
      return

    val items = owner.getKeyValues(selector)
    val collisionIndex = items.indexWhere(_ == kv)
    val collisionElement = if (collisionIndex >= 0) items(collisionIndex).element else element

    owner.reportError(
      element,
      IDConstraintChecker.ERR_NOT_UNIQUE,
      Array(selector.idConst.namespaceURI, selector.idConst.localName)
    )
    owner.reportError(
      collisionElement,
      IDConstraintChecker.ERR_NOT_UNIQUE_DIAG,
      Array(selector.idConst.namespaceURI, selector.idConst.localName)
    )
  }
}
