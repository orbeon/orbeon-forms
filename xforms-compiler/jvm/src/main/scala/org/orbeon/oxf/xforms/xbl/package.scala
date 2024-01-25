/**
 * Copyright (C) 2015 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import org.log4s
import org.orbeon.oxf.util.LoggerFactory


/**
 * Lifecycle of XBL extraction:
 *
 * - for each part
 *   - instantiate Metadata
 *     - XBLLoader.getUpToDateLibraryAndBaseline
 *   - for each part top-level AND concrete binding
 *     - Annotator/Extractor
 *       - Metadata.findBindingForElement* (maybe)
 *       - Metadata.registerInlineBinding* (because they are under <xh:head>)
 *       - (Metadata.findBindingForElement, Metadata.mapBindingToElement?, Metadata.prefixedIdHasBinding)*
 *     - XBLBindings
 *       - Metadata.extractInlineXBL (only one at top-level of part)
 *       - Metadata.findBindingByPrefixedId*
 * - resources
 *   - PartXBLAnalysis.allBindingsMaybeDuplicates{2}
 *     - Metadata.allBindingsMaybeDuplicates
 *   - XBLAssets.baselineResources
 *     - XBLLoader.findMostSpecificBinding
 * - XFormsToSomething
 *     - bindingsIncludesAreUpToDate (to see if static state has expired)
 */
package object xbl {
  val Logger: log4s.Logger = LoggerFactory.createLogger("org.orbeon.xbl")
}