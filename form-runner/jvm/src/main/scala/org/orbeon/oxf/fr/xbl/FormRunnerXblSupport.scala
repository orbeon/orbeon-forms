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
import org.orbeon.oxf.fr.XMLNames
import org.orbeon.oxf.fr.library.FRComponentParam
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.PartAnalysisImpl
import org.orbeon.oxf.xforms.xbl.XBLSupport

object FormRunnerXblSupport extends XBLSupport {

  private val XXBLUseIfParamQName = QName("use-if-param-non-blank", XMLNames.FRNamespace)

  def keepElement(
    partAnalysis  : PartAnalysisImpl,
    boundElement  : Element,
    directNameOpt : Option[QName],
    elem          : Element
  ): Boolean = {

    def fromAttribute(paramName: QName) =
      boundElement.attributeValueOpt(paramName)

    def fromMetadataAndProperties(paramName: QName) =
      FRComponentParam.fromMetadataAndProperties(
        constantMetadataRootElemOpt = FRComponentParam.findConstantMetadataRootElem(partAnalysis),
        directNameOpt               = directNameOpt,
        paramName                   = paramName
      ) map
        (_.getStringValue)

    elem.attributeValueOpt(XXBLUseIfParamQName) match {
      case Some(att) ⇒

        val paramName = QName(att)

        fromAttribute(paramName)               orElse
          fromMetadataAndProperties(paramName) exists
          (_.nonBlank)

      case None ⇒ true
    }
  }
}
