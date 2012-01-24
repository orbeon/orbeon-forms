/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.dom4j.Element
import org.orbeon.oxf.xforms.xbl.Scope

trait ChildrenBuilderTrait extends ElementAnalysis {

    type Builder = (ElementAnalysis, Option[ElementAnalysis], Element, Scope) ⇒ Option[ElementAnalysis]
    def findRelevantChildrenElements: Seq[(Element, Scope)]

    // This element's children (valid after build() has been called)
    private var _children = Seq[ElementAnalysis]()
    def children = _children

    // NOTE: Should probably make it so that controls add themselves to their container upon creation
    def addChildren(children: Seq[ElementAnalysis]) = _children ++= children

    // All this element's descendants (valid after build() has been called)
    def descendants: Seq[ElementAnalysis] = {

        def nestedChildrenBuilderTraits =
            _children collect { case child: ChildrenBuilderTrait ⇒ child }

        _children ++ (nestedChildrenBuilderTraits flatMap (_.descendants))
    }
    
    // Build this element's children and its descendants
    final def build(builder: Builder) {

        def buildChildren() = {

            // NOTE: Making `preceding` hold a side effect here is a bit unclear and error-prone.
            var preceding: Option[ElementAnalysis] = None

            // Build and collect the children
            val childrenOptions =
                for ((childElement, childContainerScope) ← findRelevantChildrenElements)
                    yield builder(this, preceding, childElement, childContainerScope) collect {
                        // The element has children
                        case newControl: ChildrenBuilderTrait ⇒
                            newControl.build(builder)
                            preceding = Some(newControl)
                            newControl
                        // The element does not have children
                        case newControl ⇒
                            preceding = Some(newControl)
                            newControl
                    }

            // Return the direct children built
            childrenOptions.flatten
        }

        // Build direct children
        _children = buildChildren()
    }
}