/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, SingleItemBinding}
import org.orbeon.oxf.xforms.event.XFormsEvents.*
import org.orbeon.oxf.xforms.model.StaticDataModel
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

class OutputControl(
  index                   : Int,
  element                 : Element,
  parent                  : Option[ElementAnalysis],
  preceding               : Option[ElementAnalysis],
  staticId                : String,
  prefixedId              : String,
  namespaceMapping        : NamespaceMapping,
  scope                   : Scope,
  containerScope          : Scope
)(
  val isImageMediatype    : Boolean,
  val isVideoMediatype    : Boolean,
  val isHtmlMediatype     : Boolean,
  val isDownloadAppearance: Boolean,
  val staticValue         : Option[String], // TODO: `expressionOrConstant`
  val mediatypeBinding    : Option[SingleItemBinding],
  val filenameBinding     : Option[SingleItemBinding],
  val sizeBinding         : Option[SingleItemBinding],
) extends ValueControl(index, element, parent, preceding, staticId, prefixedId,  namespaceMapping,  scope,  containerScope)
     with OptionalSingleNode
     with WithFileMetadata {

  // Unlike other value controls, don't restrict to simple content (even though the spec says it should!)
  override def isAllowedBoundItem(item: om.Item): Boolean = StaticDataModel.isAllowedBoundItem(item)

  override protected val allowedExtensionAttributes: Set[QName] =
    (isImageMediatype set XXFORMS_ALT_QNAME) ++
      (if (isDownloadAppearance) Set(XXFORMS_TARGET_QNAME, XXFORMS_DOWNLOAD_QNAME) else Set.empty)

  override protected def externalEventsDef: Set[String] = super.externalEventsDef ++ Set(XFORMS_HELP, DOM_ACTIVATE, XFORMS_FOCUS, XXFORMS_BLUR)
  override val externalEvents: Set[String] = externalEventsDef
}
