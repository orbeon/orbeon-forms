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

import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis._

trait TriggerAppearanceTrait extends AppearanceTrait {

  // Normalize appearances
  override val appearances = {
    // val initialAppearances = super.appearances
    val initialAppearances = attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)

    val isMinimal = initialAppearances(XFORMS_MINIMAL_APPEARANCE_QNAME)
    val isCompact = initialAppearances(XFORMS_COMPACT_APPEARANCE_QNAME)

    val isModal = element.attributeValue(XXFORMS_MODAL_QNAME) == "true"

    // - When the appearance is not minimal, put a class for the full appearance, so we can style the full trigger
    //   properly without banging our head on walls due to poor CSS support in IE.
    // - Don't add xforms-trigger-appearance-full to all controls as it is usually not needed and would add more
    //   markup.
    val updated =
      if (! isMinimal && ! isCompact)
        initialAppearances + XFORMS_FULL_APPEARANCE_QNAME
      else
        initialAppearances

    // Add "modal" pseudo-appearance if needed
    if (isModal)
      updated + XFORMS_MODAL_APPEARANCE_QNAME
    else
      updated
  }
}