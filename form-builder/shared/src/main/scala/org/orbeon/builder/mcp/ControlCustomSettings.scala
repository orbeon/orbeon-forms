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

object ControlCustomSettings {

  val FRNamespace = "http://orbeon.org/oxf/xml/form-runner"
  val FBNamespace = "http://orbeon.org/oxf/xml/form-builder"
  val XFormsNamespace = "http://www.w3.org/2002/xforms"

  sealed trait SettingType
  case object StringType  extends SettingType
  case object BooleanType extends SettingType
  case object IntegerType extends SettingType

  case class SettingDef(
    name          : String,
    settingType   : SettingType,
    allowedValues : Option[Set[String]] = None,
    minInt        : Option[Int]         = None,
    maxInt        : Option[Int]         = None,
    maxLength     : Option[Int]         = None,
    xmlAttribute  : Option[String]      = None,
    xmlNamespace  : Option[String]      = None,
    defaultVal    : Option[Json]        = None
  ) {
    def allNames: Set[String] = Set(name) ++ xmlAttribute
  }

  def normalizeKey(key: String): String =
    key.toLowerCase.replaceAll("[\\s\\-_]", "")

  case class ConstraintDef(
    test   : String,
    message: String
  )

  case class ControlSpec(
    localName  : String,
    settings   : List[SettingDef],
    constraints: List[ConstraintDef] = Nil
  ) {
    private val settingsByNormalizedName: Map[String, SettingDef] =
      settings.flatMap { s =>
        s.allNames.map(name => normalizeKey(name) -> s)
      }.toMap

    def findSetting(key: String): Option[SettingDef] =
      settingsByNormalizedName.get(normalizeKey(key))
  }

  def validateCustomSettings(
    specOpt    : Option[ControlSpec],
    controlType: String,
    controlName: String,
    custom     : Map[String, Json]
  ): Unit = {
    specOpt match {
      case None =>
        val formattedKeys = custom.keys.toList.sorted.mkString(", ")
        throw new IllegalArgumentException(
          s"Control '$controlName' of type '$controlType' does not support custom settings: $formattedKeys"
        )
      case Some(spec) =>
        for ((key, jsonVal) <- custom) {
          spec.findSetting(key) match {
            case None =>
              throw new IllegalArgumentException(
                s"Custom setting '$key' is not supported for control '$controlName' of type '${spec.localName}'"
              )
            case Some(settingDef) =>
              validateSettingValue(settingDef, key, jsonVal)
          }
        }
        // Specific checks: decimal-separator and grouping-separator must differ if both set
        // This is currently a hack, as the constraint is not actually interpreted.
        if (spec.localName == "number" || spec.localName == "currency" || spec.constraints.exists(_.test.contains("decimal-separator != grouping-separator"))) {
          val decOpt = findStringVal(spec, custom, "decimalSeparator")
          val grpOpt = findStringVal(spec, custom, "groupingSeparator")
          for {
            dec <- decOpt if dec.nonEmpty
            grp <- grpOpt if grp.nonEmpty
            if dec == grp
          } {
            throw new IllegalArgumentException(
              s"Decimal separator and grouping separator must be different (both were set to '$dec')"
            )
          }
        }
    }
  }

  private def findStringVal(spec: ControlSpec, custom: Map[String, Json], canonicalName: String): Option[String] =
    custom.collectFirst {
      case (k, j) if spec.findSetting(k).exists(_.name == canonicalName) && !j.isNull =>
        j.asString.getOrElse("")
    }

  private def validateSettingValue(settingDef: SettingDef, rawKey: String, jsonVal: Json): Unit = {
    if (!jsonVal.isNull) {
      settingDef.settingType match {
        case StringType =>
          val str = jsonVal.asString.getOrElse(
            throw new IllegalArgumentException(s"Custom setting '$rawKey' must be a string")
          )
          settingDef.allowedValues.foreach { allowed =>
            if (str.nonEmpty && !allowed.contains(str)) {
              throw new IllegalArgumentException(
                s"Invalid value '$str' for custom setting '$rawKey'. Allowed values: ${allowed.toList.sorted.mkString(", ")}"
              )
            }
          }
          settingDef.maxLength.foreach { maxLen =>
            if (str.length > maxLen) {
              val msg = if (maxLen == 1) "a single character" else s"at most $maxLen characters"
              throw new IllegalArgumentException(
                s"Custom setting '$rawKey' must be at most $msg, got '$str'"
              )
            }
          }
        case BooleanType =>
          val isBool = jsonVal.isBoolean || jsonVal.asString.exists(s => s == "true" || s == "false")
          if (!isBool) {
            throw new IllegalArgumentException(s"Custom setting '$rawKey' must be a boolean")
          }
        case IntegerType =>
          val intOpt = jsonVal.asNumber.flatMap(_.toInt).orElse(jsonVal.asString.flatMap(_.toIntOption))
          intOpt match {
            case None =>
              throw new IllegalArgumentException(s"Custom setting '$rawKey' must be an integer")
            case Some(n) =>
              settingDef.minInt.foreach { min =>
                if (n < min) {
                  throw new IllegalArgumentException(s"Custom setting '$rawKey' must be at least $min, got $n")
                }
              }
              settingDef.maxInt.foreach { max =>
                if (n > max) {
                  throw new IllegalArgumentException(s"Custom setting '$rawKey' must be at most $max, got $n")
                }
              }
          }
      }
    }
  }
}
