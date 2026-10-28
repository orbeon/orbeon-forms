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
 * Base abstract implementation of XPath matcher.
 *
 * XPath matcher tracks the startElement event and the endElement event.
 * The characters event is also used by some derived classes.
 */
abstract class Matcher(val owner: IDConstraintChecker) {

  def startElement(elt: dom.Element): Unit
  def onAttribute(att: dom.Attribute, `type`: Datatype): Unit
  def endElement(`type`: Datatype): Unit

  def characters(buf: Array[Char], start: Int, len: Int): Unit = ()
}
