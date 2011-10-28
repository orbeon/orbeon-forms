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
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis
import org.orbeon.oxf.xforms.XFormsConstants._


trait ActionTrait extends SimpleElementAnalysis {
    val ifCondition = Option(element.attributeValue("if")) orElse Option(element.attributeValue(EXFORMS_IF_ATTRIBUTE_QNAME))
    val whileCondition = Option(element.attributeValue("while")) orElse Option(element.attributeValue(EXFORMS_WHILE_ATTRIBUTE_QNAME))
    val iterate = Option(element.attributeValue(XXFORMS_ITERATE_ATTRIBUTE_QNAME)) orElse Option(element.attributeValue(EXFORMS_ITERATE_ATTRIBUTE_QNAME))
}