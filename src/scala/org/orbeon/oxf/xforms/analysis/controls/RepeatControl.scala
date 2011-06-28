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
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase
import java.lang.IllegalArgumentException

class RepeatControl(staticStateContext: StaticStateContext, element: Element, parent: ContainerTrait, preceding: Option[ElementAnalysis], scope: XBLBindingsBase.Scope)
        extends ContainerControl(staticStateContext, element, parent, preceding, scope) {
    // TODO: add repeat hierarchy information
}

object RepeatControl {
    /**
     * Find the closest ancestor repeat if any.
     */
    def getAncestorRepeat(elementAnalysis: ElementAnalysis): Option[RepeatControl] =
        ElementAnalysis.getAllAncestors(elementAnalysis.parent) find (_.isInstanceOf[RepeatControl]) match {
            case Some(repeatControl: RepeatControl) => Some(repeatControl)
            case _ => None
        }

    // Get the first ancestor repeats across parts
    def getAncestorRepeatAcrossParts(elementAnalysis: ElementAnalysis): Option[RepeatControl] = elementAnalysis match {
        case simpleElementAnalysis: SimpleElementAnalysis =>
            val currentPart = simpleElementAnalysis.staticStateContext.partAnalysis
            // First search ancestor local to this part, or else try the ancestor repeat associated with the parent part if any
            getAncestorRepeat(simpleElementAnalysis) orElse
                (currentPart.parent map
                    (_.getControlAnalysis(currentPart.startScope.getFullPrefix.init)) // .init removes the trailing '$'
                        flatMap (getAncestorRepeatAcrossParts(_))) // recursively search ancestor parts
        case _ => throw new IllegalArgumentException
    }

    // Get all ancestor repeats across parts, from leaf to root
    def getAllAncestorRepeatAcrossParts(elementAnalysis: ElementAnalysis): List[RepeatControl] =
        getAncestorRepeatAcrossParts(elementAnalysis) match {
            case Some(ancestor) => ancestor :: getAllAncestorRepeatAcrossParts(ancestor)
            case None => Nil
        }
}