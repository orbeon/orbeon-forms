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

import controls.LHHA
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import collection.JavaConverters._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.dom4j.Element
import org.orbeon.oxf.xforms.xbl.Scope

// Recursively call the builder on all children elements
trait ContainerChildrenBuilder extends ChildrenBuilderTrait {

    // For <xf:group>, <xf:switch>, <xf:case>, <xxf:dialog>, consider only nested LHHA elements without @for attribute
    def findRelevantChildrenElements: Seq[Element] =
        Dom4jUtils.elements(element).asScala filterNot
            (e ⇒ LHHA.LHHAQNames(e.getQName) && (e.attribute(FOR_QNAME) eq null))

    def buildChildren(build: Builder, containerScope: Scope) {

        var preceding: Option[ElementAnalysis] = None

        for (childElement ← findRelevantChildrenElements)
            build(this, preceding, childElement, containerScope) match {
                case newPreceding @ Some(newControl: ChildrenBuilderTrait) ⇒
                    preceding = newPreceding
                    newControl.buildChildren(build, containerScope)
                case newPreceding @ Some(foo) ⇒
                    preceding = newPreceding
                case _ ⇒
            }
    }
}