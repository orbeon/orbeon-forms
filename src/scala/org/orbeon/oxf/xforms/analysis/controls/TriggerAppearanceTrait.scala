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

import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis._

trait TriggerAppearanceTrait extends AppearanceTrait {

    val isModal = element.attributeValue(XXFORMS_MODAL_QNAME) == "true"

    // Normalize appearances
    override val appearances = {
        // val initialAppearances = super.appearances
        val initialAppearances = attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)

        // In noscript mode, use xxforms:minimal instead of minimal
        if (staticStateContext.partAnalysis.staticState.isNoscript && initialAppearances(XFORMS_MINIMAL_APPEARANCE_QNAME))
            initialAppearances - XFORMS_MINIMAL_APPEARANCE_QNAME + XXFORMS_MINIMAL_APPEARANCE_QNAME
        else
            initialAppearances
    }
}