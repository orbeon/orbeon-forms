/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.properties.{Property, PropertySet}
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.AllowedFunctions


object FormRunnerAllowedFunctions {

  val AllowedFunctionsPrivatePropertyName = "oxf.fr.detail.formula.allowed-functions.private"
  val AllowedFunctionsPropertyName        = "oxf.fr.detail.formula.allowed-functions"

  private def rawPropertyOpt(
    name              : String,
    appForm           : AppForm,
    propertyProfileOpt: Option[String],
    propertySet       : PropertySet
  ): Option[Property] =
    frc.formRunnerRawProperty(name, appForm, propertyProfileOpt, propertySet)
      .orElse(propertySet.getPropertyOpt(name, propertyProfileOpt))

  def allowedFunctions(
    app               : String,
    form              : String,
    propertyProfileOpt: Option[String] = None,
    propertySet       : PropertySet    = CoreCrossPlatformSupport.properties
  ): AllowedFunctions = {
    val appForm = AppForm(app, form)

    val base =
      rawPropertyOpt(AllowedFunctionsPrivatePropertyName, appForm, propertyProfileOpt, propertySet) match {
        case Some(prop: Property) =>
          prop.associatedValue { _ =>
            AllowedFunctions.parse(prop.stringValue, prop.namespaceMapping)
          }
        case None =>
          AllowedFunctions.Default
      }

    rawPropertyOpt(AllowedFunctionsPropertyName, appForm, propertyProfileOpt, propertySet) match {
      case Some(prop: Property) if prop.stringValue.nonAllBlank =>
        AllowedFunctions.parse(prop.stringValue, prop.namespaceMapping, base = Some(base))
      case _ =>
        base
    }
  }

  def allowedFunctions(implicit p: FormRunnerParams): AllowedFunctions =
    allowedFunctions(p.app, p.form)

  def allowedFunctionsPropertyAsUriQualifiedNames(
    app               : String,
    form              : String,
    propertyProfileOpt: Option[String],
    propertySet       : PropertySet = CoreCrossPlatformSupport.properties
  ): Option[String] = {
    val appForm = AppForm(app, form)

    val convertedParts =
      List(AllowedFunctionsPrivatePropertyName, AllowedFunctionsPropertyName).flatMap { propName =>
        rawPropertyOpt(propName, appForm, propertyProfileOpt, propertySet).flatMap { property =>
          property.stringValue.trimAllToOpt.map { spec =>
            AllowedFunctions.convertToUriQualifiedNames(spec, property.namespaceMapping)
          }
        }
      }

    if (convertedParts.nonEmpty)
      Some(convertedParts.mkString(" "))
    else
      None
  }
}
