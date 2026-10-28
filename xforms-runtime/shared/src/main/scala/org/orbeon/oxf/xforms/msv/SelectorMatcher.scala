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
import org.orbeon.msv.grammar.xmlschema.IdentityConstraint
import org.orbeon.msv.relaxng.datatype.Datatype

/**
 * XPath matcher that tests the selector of an identity constraint.
 */
class SelectorMatcher(
  owner       : IDConstraintChecker,
  val idConst : IdentityConstraint,
  val element : dom.Element
) extends PathMatcher(owner, idConst.selectors) {

  owner.pushActiveScope(idConst, this)

  if (Debug.debug) {
    println(s"new id scope is available for {${idConst.localName}}")
  }
  start(element)

  override def onRemoved(): Unit = {
    super.onRemoved()
    owner.popActiveScope(idConst, this)
  }

  override def onElementMatched(elt: dom.Element): Unit = {
    if (Debug.debug)
      println(s"find a match for a selector: ${idConst.localName}")
    owner.add(new FieldsMatcher(this, elt))
  }

  override def onAttributeMatched(att: dom.Attribute, `type`: Datatype): Unit =
    throw new Error("selectors cannot contain attribute steps")
}
