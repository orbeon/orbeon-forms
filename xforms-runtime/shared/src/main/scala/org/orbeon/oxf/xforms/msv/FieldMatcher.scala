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
import org.orbeon.msv.grammar.xmlschema.Field
import org.orbeon.msv.relaxng.datatype.Datatype

/**
 * XPath matcher that tests one field of a key.
 */
class FieldMatcher(
  val parent : FieldsMatcher,
  val field  : Field
) extends PathMatcher(parent.owner, field.paths) {

  var value: AnyRef = _
  private var element: dom.Element = _

  // Test the initial match
  start(parent.element)

  override def onElementMatched(elt: dom.Element): Unit = {
    if (Debug.debug)
      println(s"field match for ${parent.selector.idConst.localName}")
    element = elt
  }

  override def onAttributeMatched(att: dom.Attribute, `type`: Datatype): Unit = {
    if (Debug.debug)
      println(s"field match for ${parent.selector.idConst.localName}")
    val v = att.getValue
    setValue(v, `type`)
  }

  override def startElement(elt: dom.Element): Unit = {
    if (element != null) {
      element = null
    }
    super.startElement(elt)
  }

  override def endElement(`type`: Datatype): Unit = {
    super.endElement(`type`)
    if (element != null) {
      val v = element.getText
      setValue(v, `type`)
      element = null
    }
  }

  private def setValue(lexical: String, `type`: Datatype): Unit = {
    if (value != null) {
      doubleMatchError()
      return
    }

    if (`type` == null) {
      value = lexical
      if (Debug.debug)
        println("no type info available")
    } else {
      value = `type`.createValue(lexical, owner).asInstanceOf[AnyRef]
    }
  }

  private def doubleMatchError(): Unit = {
    val i = parent.children.indexOf(this)
    owner.reportError(
      parent.element,
      IDConstraintChecker.ERR_DOUBLE_MATCH,
      Array(
        parent.selector.idConst.namespaceURI,
        parent.selector.idConst.localName,
        Integer.valueOf(i + 1)
      )
    )
  }
}
