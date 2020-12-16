/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.dom.QName
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.saxon.om

import scala.util.{Failure, Success}


object DateSupportJava {

  import io.circe.generic.auto._
  import io.circe.parser
  import io.circe.syntax._

  private val ExcludedDatesQName = QName("excluded-dates")

  //@XPathFunction
  def serializeExternalValueJava(
    binding : om.Item,
    format  : String
  ): String =
    DateExternalValue(
      value         = binding.getStringValue,
      format        = format,
      excludedDates = InstanceData.findCustomMip(binding, ExcludedDatesQName) map (_.splitTo[List]()) getOrElse Nil
    ).asJson.noSpaces

  //@XPathFunction
  def deserializeExternalValueJava(externalValue: String): String =
    parser.decode[DateExternalValue](externalValue).fold(Failure.apply, Success.apply) map (_.value) getOrElse ""
}
