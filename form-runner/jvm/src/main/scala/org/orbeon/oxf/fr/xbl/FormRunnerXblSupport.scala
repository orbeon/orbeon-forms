/**
 * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.oxf.fr.xbl

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.fr.library.FRComponentParamSupport
import org.orbeon.oxf.fr.{AppForm, XMLNames}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.PartAnalysisForXblSupport
import org.orbeon.oxf.xforms.xbl.XBLSupport
import org.orbeon.saxon.function.Property


object FormRunnerXblSupport extends XBLSupport {

  private val FRKeepIfParamQName               = QName("keep-if-param-non-blank",      XMLNames.FRNamespace)
  private val FRKeepIfDesignTimeQName          = QName("keep-if-design-time",          XMLNames.FRNamespace)

  def keepElement(
    partAnalysisCtx : PartAnalysisForXblSupport,
    boundElement    : Element,
    directNameOpt   : Option[QName],
    elem            : Element
  ): Boolean = {

    def fromAttribute(paramName: QName): Option[String] =
      boundElement.attributeValueOpt(paramName)

    def fromMetadataAndProperties(paramName: QName): Option[String] =
      FRComponentParamSupport.fromMetadataAndProperties(
        partAnalysis  = partAnalysisCtx,
        directNameOpt = directNameOpt,
        paramName     = paramName,
        property      = Property.property
      ) map
        (_.getStringValue)

    def keepIfParamNonBlank: Boolean =
      elem.attributeValueOpt(FRKeepIfParamQName) match {
        case Some(att) =>

          val paramName = QName(att)

          fromAttribute(paramName)               orElse
            fromMetadataAndProperties(paramName) exists
            (_.nonAllBlank)

        case None => true
      }

    // Design time = we are in the `<xxf:dynamic>` used by Form Builder to render the form.
    // We test on `<xxf:dynamic>` being the one with `id="fb"` to exclude the `<xxf:dynamic>` used in the
    // Control Settings dialog for XBL custom properties.
    def isDesignTime =
      ! partAnalysisCtx.isTopLevelPart &&
        partAnalysisCtx.startScope.scopeId == "fb"

    def keepIfDesignTime: Boolean =
      elem.attributeValueOpt(FRKeepIfDesignTimeQName) match {
        case Some("true")  => isDesignTime
        case Some("false") => ! isDesignTime
        case _             => true
      }

    // TODO: Should also remove processed attributes.
    ! (! keepIfParamNonBlank || ! keepIfDesignTime)
  }
}
