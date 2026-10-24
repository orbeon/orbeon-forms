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
package org.orbeon.builder.mcp

import io.circe.Json
import org.orbeon.builder.mcp.ControlCustomSettings.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*

object ControlCustomSettingsParser {

  private def kebabToCamel(s: String): String = {
    val parts = s.split('-')
    if (parts.isEmpty) ""
    else parts.head + parts.tail.map(_.capitalize).mkString
  }

  def fromBinding(binding: NodeInfo): Option[ControlSpec] =
    for {
      metadataElem       <- binding.child("*:metadata").headOption
      customSettingsElem <- metadataElem.child("*:custom-settings").headOption
    } yield {
      val localName = binding.attValueOpt("id")
        .map(_.stripPrefix("fr-").stripSuffix("-binding"))
        .getOrElse("")
      parseControlSpec(customSettingsElem, localName)
    }

  def parseControlSpec(customSettingsElem: NodeInfo, localName: String): ControlSpec = {
    val settingDefs = customSettingsElem.child("*:setting").map { settingElem =>
      val rawName = settingElem.attValueOpt("name").getOrElse("")
      val hasColon = rawName.contains(':')
      val prefixOpt = if (hasColon) Some(rawName.substring(0, rawName.indexOf(':'))) else None
      val bareName = if (hasColon) rawName.substring(rawName.indexOf(':') + 1) else rawName

      val canonicalName = kebabToCamel(bareName)

      val settingType = settingElem.attValueOpt("type").map(_.stripPrefix("xs:")) match {
        case Some("boolean")               => BooleanType
        case Some("integer") | Some("int") => IntegerType
        case _                             => StringType
      }

      val allowedValues = settingElem.attValueOpt("values").map(_.split("\\s+").filter(_.nonEmpty).toSet)
      val minInt        = settingElem.attValueOpt("min").orElse(settingElem.attValueOpt("minimum")).flatMap(_.toIntOption)
      val maxInt        = settingElem.attValueOpt("max").orElse(settingElem.attValueOpt("maximum")).flatMap(_.toIntOption)
      val maxLength     = settingElem.attValueOpt("max-length").flatMap(_.toIntOption)

      val defaultVal = settingElem.attValueOpt("default").map { v =>
        settingType match {
          case BooleanType => Json.fromBoolean(v == "true")
          case IntegerType => v.toIntOption.map(Json.fromInt).getOrElse(Json.fromString(v))
          case StringType  => Json.fromString(v)
        }
      }

      val isRef = settingElem.attValueOpt("ref").isDefined || Set("items", "item-label", "item-value", "item-hint").contains(bareName)
      val (xmlAttribute, xmlNamespace) =
        if (isRef) {
          (None, None)
        } else {
          val ns = prefixOpt match {
            case Some("fr") => Some(FRNamespace)
            case Some("fb") => Some(FBNamespace)
            case _          => None
          }
          val attrName = settingElem.attValueOpt("attribute").getOrElse(bareName)
          (Some(attrName), ns)
        }

      SettingDef(
        name          = canonicalName,
        settingType   = settingType,
        allowedValues = allowedValues,
        minInt        = minInt,
        maxInt        = maxInt,
        maxLength     = maxLength,
        xmlAttribute  = xmlAttribute,
        xmlNamespace  = xmlNamespace,
        defaultVal    = defaultVal
      )
    }.toList

    val constraints = customSettingsElem.child("*:constraint").map { cElem =>
      ConstraintDef(
        test    = cElem.attValueOpt("test").getOrElse(""),
        message = cElem.attValueOpt("message").getOrElse("")
      )
    }.toList

    ControlSpec(localName, settingDefs, constraints)
  }
}
