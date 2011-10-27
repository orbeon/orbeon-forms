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

import org.orbeon.oxf.xforms.analysis.ElementAnalysis._
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsConstants._

trait SelectAppearanceTrait extends AppearanceTrait {

    val isMultiple = element.getName == "select"

    // Normalize appearances
    override val appearances = {
        // NOTE: Ignore no longer supported xxforms:autocomplete (which would require selection="open" anyway)
        // Ideally we would like to do this but we can't, see:
        // https://issues.scala-lang.org/browse/SI-1938?focusedCommentId=55131#comment-55131
        // val initialAppearances = super.appearances
        val initialAppearances = attQNameSet(element, XFormsConstants.APPEARANCE_QNAME, namespaceMapping) - XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME
        val size = initialAppearances.size

        initialAppearances match {
            case _ if isMultiple && initialAppearances(XFORMS_MINIMAL_APPEARANCE_QNAME) ⇒
                // Select with minimal appearance is handled as a compact appearance
                initialAppearances - XFORMS_MINIMAL_APPEARANCE_QNAME + XFORMS_COMPACT_APPEARANCE_QNAME
            case _ if size > 0 ⇒
                initialAppearances
            case _ if isMultiple ⇒
                Set(XFORMS_COMPACT_APPEARANCE_QNAME) // default for xforms:select
            case _ ⇒
                Set(XFORMS_MINIMAL_APPEARANCE_QNAME) // default for xforms:select1
        }
    }

    val isFull = appearances(XFORMS_FULL_APPEARANCE_QNAME)
    val isCompact = appearances(XFORMS_COMPACT_APPEARANCE_QNAME)
    val isTree = appearances(XXFORMS_TREE_APPEARANCE_QNAME)
    val isMenu = appearances(XXFORMS_MENU_APPEARANCE_QNAME)
}