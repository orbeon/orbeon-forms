/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.QName
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.xforms.xbl.Scope


// NOTE: 2018-02-23: This is only created if the `AbstractBinding` has a template. Wondering if we should support components with
// no templates (or creating an empty template in that case) so that we don't have to special-case bindings without templates.
case class ConcreteBinding(
  innerScope       : Scope,             // each binding defines a new scope
  templateTree     : SAXStore,          // template with relevant markup for output, including XHTML when needed
  boundElementAtts : Map[QName, String] // attributes on the bound element
)