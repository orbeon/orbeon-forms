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

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis._
import java.lang.IllegalArgumentException
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.event.XFormsEvents._

class RepeatControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
    extends ContainerControl(staticStateContext, element, parent, preceding, scope)
    with ChildrenBuilderTrait {

    // TODO: add repeat hierarchy information
    
    val iterationElement = element.element(XFORMS_REPEAT_ITERATION_QNAME)
    lazy val iteration = children collectFirst { case i: RepeatIterationControl ⇒ i }
    require(iterationElement ne null)

    override protected def externalEventsDef = super.externalEventsDef + XXFORMS_DND
    override val externalEvents              = externalEventsDef
}

object RepeatControl {

    // Find the closest ancestor repeat in the same scope, if any
    def getAncestorRepeatInScope(elementAnalysis: ElementAnalysis): Option[RepeatControl] =
        elementAnalysis.ancestorRepeats find (_.scope == elementAnalysis.scope)

    // Get the first ancestor repeats across parts
    def getAncestorRepeatAcrossParts(elementAnalysis: ElementAnalysis): Option[RepeatControl] =
        getAllAncestorRepeatsAcrossParts(elementAnalysis).headOption

    // Get all ancestor repeats across parts, from leaf to root
    def getAllAncestorRepeatsAcrossParts(elementAnalysis: ElementAnalysis): List[RepeatControl] = elementAnalysis match {
        case element: SimpleElementAnalysis ⇒ element.ancestorRepeatsAcrossParts
        case _                              ⇒ throw new IllegalArgumentException
    }
}