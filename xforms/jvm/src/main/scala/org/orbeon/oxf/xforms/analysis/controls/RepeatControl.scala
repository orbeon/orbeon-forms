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

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.event.XFormsEvents._

class RepeatControl(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends ContainerControl(staticStateContext, element, parent, preceding, scope)
   with ChildrenBuilderTrait
   with AppearanceTrait {      // for separator appearance

  val iterationElement = element.element(XFORMS_REPEAT_ITERATION_QNAME)
  require(iterationElement ne null)

  lazy val iteration = children collectFirst { case i: RepeatIterationControl ⇒ i }

  val isAroundTableOrListElement = appearances(XXFORMS_SEPARATOR_APPEARANCE_QNAME)

  override protected def externalEventsDef = super.externalEventsDef + XXFORMS_DND
  override val externalEvents              = externalEventsDef
}
