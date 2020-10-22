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

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis.{LangRef, PartAnalysisImpl}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{Constants, EventNames}
import org.orbeon.xml.NamespaceMapping

/**
 * Single root container for a part, whether top-level or a nested part.
 */
class RootControl(
  part             : PartAnalysisImpl,
  index            : Int,
  element          : Element,
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ContainerControl(part, index, element, None, None, staticId, prefixedId, namespaceMapping, scope, containerScope) {


  override lazy val lang: Option[LangRef] = {

    // Assign a top-level lang based on the first xml:lang found on a top-level control. This allows
    // xxbl:global controls to inherit that xml:lang. All other top-level controls already have an xml:lang
    // placed by XFormsExtractor.
    def fromChildElements = {

      val firstChildXMLLang = element.elements collectFirst {
        case e if e.attribute(XML_LANG_QNAME) ne null => e.attributeValue(XML_LANG_QNAME)
      }

      firstChildXMLLang flatMap extractXMLLang
    }

    // Ask the parent part
    def fromParentPart =
      part.elementInParent flatMap (_.lang)

    fromChildElements orElse fromParentPart
  }

  override protected def externalEventsDef = super.externalEventsDef ++ Set(XXFORMS_LOAD, EventNames.XXFormsPoll)
  override val externalEvents              = externalEventsDef
}