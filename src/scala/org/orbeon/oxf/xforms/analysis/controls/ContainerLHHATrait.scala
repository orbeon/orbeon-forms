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

import org.dom4j.{Element, QName}
import org.orbeon.oxf.xforms.XFormsConstants

trait ContainerLHHATrait extends LHHATrait {

    override protected def findNestedLHHAElement(qName: QName) =
        // For e.g. <xforms:group>, consider only nested element without @for attribute
        // NOTE: Should probably be child::xforms:label[not(exists(@for))] to get first such element, but e.g. group
        // label if any should probably be first anyway.
        element.element(qName) match {
            case lhhaElement: Element if lhhaElement.attribute(XFormsConstants.FOR_QNAME) eq null => Some(lhhaElement)
            case _ => None
        }
}