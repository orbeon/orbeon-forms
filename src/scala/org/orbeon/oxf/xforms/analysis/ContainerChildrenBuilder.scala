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

trait ContainerChildrenBuilder extends ChildrenBuilderTrait {
    // For <xf:group>, <xf:switch>, <xf:case>, <xxf:dialog>, consider only nested LHHA elements without @for attribute
    // Also, by default, the container scope of nested controls is the same as the current element's
    def findRelevantChildrenElements: Seq[(Element, Scope)] =
        Dom4jUtils.elements(element).asScala filterNot
            (e ⇒ LHHA.LHHAQNames(e.getQName) && (e.attribute(FOR_QNAME) eq null)) map
                ((_, containerScope))
}