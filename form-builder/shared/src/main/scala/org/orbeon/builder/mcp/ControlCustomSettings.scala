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
    aliases       : Set[String]         = Set.empty,
    allowedValues : Option[Set[String]] = None,
    minInt        : Option[Int]         = None,
    maxInt        : Option[Int]         = None,
    xmlAttribute  : Option[String]      = None,
    xmlNamespace  : Option[String]      = None,
    defaultVal    : Option[Json]        = None
  ) {
    def allNames: Set[String] = aliases + name
  }

  private def normalizeKey(key: String): String =
    key.toLowerCase.replaceAll("[\\s\\-_]", "")

  case class ControlSpec(
    localName: String,
    settings : List[SettingDef]
  ) {
    private val settingsByNormalizedName: Map[String, SettingDef] =
      settings.flatMap { s =>
        s.allNames.map(name => normalizeKey(name) -> s)
      }.toMap

    def findSetting(key: String): Option[SettingDef] =
      settingsByNormalizedName.get(normalizeKey(key))
  }

  // Common number/currency settings
  private val NumberSettings: List[SettingDef] = List(
    SettingDef("prefix", StringType, Set("prefix", "Prefix"), xmlAttribute = Some("prefix")),
    SettingDef("suffix", StringType, Set("suffix", "Suffix"), xmlAttribute = Some("suffix")),
    SettingDef(
      name         = "decimalSeparator",
      settingType  = StringType,
      aliases      = Set("decimal-separator", "decimalSeparator", "Decimal Separator", "decimal_separator"),
      xmlAttribute = Some("decimal-separator")
    ),
    SettingDef(
      name         = "groupingSeparator",
      settingType  = StringType,
      aliases      = Set("grouping-separator", "groupingSeparator", "Grouping Separator", "grouping_separator"),
      xmlAttribute = Some("grouping-separator")
    ),
    SettingDef(
      name         = "digitsAfterDecimal",
      settingType  = IntegerType,
      aliases      = Set("digits-after-decimal", "digitsAfterDecimal", "Digits after decimal", "digits_after_decimal"),
      minInt       = Some(0),
      xmlAttribute = Some("digits-after-decimal")
    ),
    SettingDef(
      name         = "roundWhenFormatting",
      settingType  = BooleanType,
      aliases      = Set("round-when-formatting", "roundWhenFormatting", "Round when formatting", "round_when_formatting"),
      xmlAttribute = Some("round-when-formatting")
    ),
    SettingDef(
      name         = "roundWhenStoring",
      settingType  = BooleanType,
      aliases      = Set("round-when-storing", "roundWhenStoring", "Round when storing", "round_when_storing"),
      xmlAttribute = Some("round-when-storing")
    ),
    SettingDef(
      name          = "fieldWidth",
      settingType   = StringType,
      aliases       = Set("field-width", "fieldWidth", "Field width", "field_width"),
      allowedValues = Some(Set("full", "natural")),
      xmlAttribute  = Some("field-width")
    ),
    SettingDef(
      name          = "fieldAlign",
      settingType   = StringType,
      aliases       = Set("field-align", "fieldAlign", "Field align", "field_align"),
      allowedValues = Some(Set("left", "right")),
      xmlAttribute  = Some("field-align")
    )
  )

  private val DateSettings: List[SettingDef] = List(
    SettingDef(
      name          = "fieldWidth",
      settingType   = StringType,
      aliases       = Set("field-width", "fieldWidth", "Field width", "field_width"),
      allowedValues = Some(Set("full", "natural")),
      xmlAttribute  = Some("field-width")
    ),
    SettingDef(
      name          = "nativePicker",
      settingType   = StringType,
      aliases       = Set("native-picker", "nativePicker", "Native picker", "native_picker"),
      allowedValues = Some(Set("ios", "always")),
      xmlAttribute  = Some("native-picker")
    ),
    SettingDef(
      name          = "weekStartDay",
      settingType   = StringType,
      aliases       = Set("week-start-day", "weekStartDay", "Week start day", "week_start_day"),
      allowedValues = Some(Set("saturday", "sunday", "monday")),
      xmlAttribute  = Some("week-start-day")
    ),
    SettingDef(
      name         = "outputFormat",
      settingType  = StringType,
      aliases      = Set("output-format", "outputFormat", "Output format", "output_format"),
      xmlAttribute = Some("output-format")
    )
  )

  private val TimeSettings: List[SettingDef] = List(
    SettingDef(
      name          = "fieldWidth",
      settingType   = StringType,
      aliases       = Set("field-width", "fieldWidth", "Field width", "field_width"),
      allowedValues = Some(Set("full", "natural")),
      xmlAttribute  = Some("field-width")
    ),
    SettingDef(
      name          = "nativePicker",
      settingType   = StringType,
      aliases       = Set("native-picker", "nativePicker", "Native picker", "native_picker"),
      allowedValues = Some(Set("ios", "always")),
      xmlAttribute  = Some("native-picker")
    ),
    SettingDef(
      name         = "outputFormat",
      settingType  = StringType,
      aliases      = Set("output-format", "outputFormat", "Output format", "output_format"),
      xmlAttribute = Some("output-format")
    )
  )

  private val DatetimeSettings: List[SettingDef] = List(
    SettingDef(
      name          = "fieldWidth",
      settingType   = StringType,
      aliases       = Set("field-width", "fieldWidth", "Field width", "field_width"),
      allowedValues = Some(Set("full", "natural")),
      xmlAttribute  = Some("field-width")
    )
  )

  private val DropdownDateSettings: List[SettingDef] = List(
    SettingDef(
      name         = "startYear",
      settingType  = StringType,
      aliases      = Set("start-year", "startYear", "Start year", "start_year"),
      xmlAttribute = Some("start-year")
    ),
    SettingDef(
      name         = "endYear",
      settingType  = StringType,
      aliases      = Set("end-year", "endYear", "End year", "end_year"),
      xmlAttribute = Some("end-year")
    )
  )

  private val UsPhoneSettings: List[SettingDef] = List(
    SettingDef(
      name          = "fieldWidth",
      settingType   = StringType,
      aliases       = Set("field-width", "fieldWidth", "Field width", "field_width"),
      allowedValues = Some(Set("full", "natural")),
      xmlAttribute  = Some("field-width")
    )
  )

  private val UsSsnSettings: List[SettingDef] = List(
    SettingDef(
      name          = "editFormat",
      settingType   = StringType,
      aliases       = Set("edit-format", "editFormat", "Edit format", "edit_format"),
      allowedValues = Some(Set("visible", "obscured", "reveal")),
      xmlAttribute  = Some("edit-format")
    ),
    SettingDef(
      name          = "viewFormat",
      settingType   = StringType,
      aliases       = Set("view-format", "viewFormat", "View format", "view_format"),
      allowedValues = Some(Set("visible", "obscured", "partial")),
      xmlAttribute  = Some("view-format")
    ),
    SettingDef(
      name          = "pdfFormat",
      settingType   = StringType,
      aliases       = Set("pdf-format", "pdfFormat", "PDF format", "pdf_format"),
      allowedValues = Some(Set("visible", "obscured", "partial")),
      xmlAttribute  = Some("pdf-format")
    ),
    SettingDef(
      name          = "pdfTemplateFormat",
      settingType   = StringType,
      aliases       = Set("pdf-template-format", "pdfTemplateFormat", "PDF template format", "pdf_template_format"),
      allowedValues = Some(Set("visible", "obscured", "partial")),
      xmlAttribute  = Some("pdf-template-format")
    )
  )

  private val AttachmentSettings: List[SettingDef] = List(
    SettingDef(
      name         = "allowDownload",
      settingType  = BooleanType,
      aliases      = Set("allow-download", "allowDownload", "Allow download", "allow_download"),
      xmlAttribute = Some("allow-download"),
      defaultVal   = Some(Json.fromBoolean(true))
    ),
    SettingDef(
      name         = "showSize",
      settingType  = BooleanType,
      aliases      = Set("show-size", "showSize", "Show size", "show_size"),
      xmlAttribute = Some("show-size"),
      defaultVal   = Some(Json.fromBoolean(true))
    ),
    SettingDef(
      name          = "uploadErrorAppearance",
      settingType   = StringType,
      aliases       = Set("upload-error-appearance", "uploadErrorAppearance", "Upload error appearance", "upload_error_appearance"),
      allowedValues = Some(Set("dialog", "inline")),
      xmlAttribute  = Some("upload-error-appearance")
    )
  )

  private val ImageAttachmentSettings: List[SettingDef] = List(
    SettingDef(
      name         = "maxWidth",
      settingType  = IntegerType,
      aliases      = Set("max-width", "maxWidth", "Max width", "max_width", "transformMaxWidth", "transform-max-width"),
      minInt       = Some(1),
      xmlAttribute = Some("transform-max-width")
    ),
    SettingDef(
      name         = "maxHeight",
      settingType  = IntegerType,
      aliases      = Set("max-height", "maxHeight", "Max height", "max_height", "transformMaxHeight", "transform-max-height"),
      minInt       = Some(1),
      xmlAttribute = Some("transform-max-height")
    ),
    SettingDef(
      name         = "quality",
      settingType  = IntegerType,
      aliases      = Set("quality", "Quality", "transformQuality", "transform-quality"),
      minInt       = Some(1),
      maxInt       = Some(100),
      xmlAttribute = Some("transform-quality")
    ),
    SettingDef(
      name          = "format",
      settingType   = StringType,
      aliases       = Set("format", "Format", "transformFormat", "transform-format"),
      allowedValues = Some(Set("jpg", "png")),
      xmlAttribute  = Some("transform-format")
    )
  )

  private val DropdownSelect1Settings: List[SettingDef] = List(
    SettingDef(
      name          = "pdfAppearance",
      settingType   = StringType,
      aliases       = Set("pdf-appearance", "pdfAppearance", "PDF appearance", "pdf_appearance"),
      allowedValues = Some(Set("dropdown", "full")),
      xmlAttribute  = Some("pdf-appearance"),
      xmlNamespace  = Some(FRNamespace)
    )
  )

  private val DropdownSelect1SearchSettings: List[SettingDef] = List(
    SettingDef(
      name          = "pdfAppearance",
      settingType   = StringType,
      aliases       = Set("pdf-appearance", "pdfAppearance", "PDF appearance", "pdf_appearance"),
      allowedValues = Some(Set("search", "full")),
      xmlAttribute  = Some("pdf-appearance"),
      xmlNamespace  = Some(FRNamespace)
    )
  )

  private val DataboundSelect1Settings: List[SettingDef] = List(
    SettingDef("resource",  StringType, Set("resource", "Resource"), xmlAttribute = Some("resource")),
    SettingDef("items",     StringType, Set("items", "itemset", "Items")),
    SettingDef("itemLabel", StringType, Set("item-label", "itemLabel", "Item label", "item_label")),
    SettingDef("itemValue", StringType, Set("item-value", "itemValue", "Item value", "item_value")),
    SettingDef("itemHint",  StringType, Set("item-hint", "itemHint", "Item hint", "item_hint"))
  )

  private val DataboundSelect1SearchSettings: List[SettingDef] = List(
    SettingDef("selection",             StringType,  Set("selection", "openSelection", "open-selection", "Open selection"), xmlAttribute = Some("selection"), xmlNamespace = Some(FBNamespace)),
    SettingDef("servicePerformsSearch", BooleanType, Set("service-performs-search", "servicePerformsSearch", "Service performs search"), xmlAttribute = Some("service-performs-search")),
    SettingDef("serviceSupportsPaging", BooleanType, Set("service-supports-paging", "serviceSupportsPaging", "Service supports paging"), xmlAttribute = Some("service-supports-paging")),
    SettingDef("resource",              StringType,  Set("resource", "Resource"), xmlAttribute = Some("resource")),
    SettingDef("isLastPage",            StringType,  Set("is-last-page", "isLastPage", "Is last page"), xmlAttribute = Some("is-last-page")),
    SettingDef("items",                 StringType,  Set("items", "itemset", "Items")),
    SettingDef("itemLabel",             StringType,  Set("item-label", "itemLabel", "Item label", "item_label")),
    SettingDef("itemValue",             StringType,  Set("item-value", "itemValue", "Item value", "item_value")),
    SettingDef("itemHint",              StringType,  Set("item-hint", "itemHint", "Item hint", "item_hint")),
    SettingDef("autoSelectUniqueChoice",BooleanType, Set("auto-select-unique-choice", "autoSelectUniqueChoice", "Auto select unique choice"), xmlAttribute = Some("auto-select-unique-choice")),
    SettingDef("storeLabel",            BooleanType, Set("store-label", "storeLabel", "Store label"), xmlAttribute = Some("store-label"))
  )

  private val DataboundSelectSearchSettings: List[SettingDef] = List(
    SettingDef("selection",             StringType,  Set("selection", "openSelection", "open-selection", "Open selection"), xmlAttribute = Some("selection"), xmlNamespace = Some(FBNamespace)),
    SettingDef("resource",              StringType,  Set("resource", "Resource"), xmlAttribute = Some("resource")),
    SettingDef("items",                 StringType,  Set("items", "itemset", "Items")),
    SettingDef("itemLabel",             StringType,  Set("item-label", "itemLabel", "Item label", "item_label")),
    SettingDef("itemValue",             StringType,  Set("item-value", "itemValue", "Item value", "item_value")),
    SettingDef("itemHint",              StringType,  Set("item-hint", "itemHint", "Item hint", "item_hint")),
    SettingDef("autoSelectUniqueChoice",BooleanType, Set("auto-select-unique-choice", "autoSelectUniqueChoice", "Auto select unique choice"), xmlAttribute = Some("auto-select-unique-choice"))
  )

  private val AllSpecs: List[ControlSpec] = List(
    ControlSpec("number",                  NumberSettings),
    ControlSpec("currency",                NumberSettings),
    ControlSpec("date",                    DateSettings),
    ControlSpec("time",                    TimeSettings),
    ControlSpec("datetime",                DatetimeSettings),
    ControlSpec("dropdown-date",           DropdownDateSettings),
    ControlSpec("us-phone",                UsPhoneSettings),
    ControlSpec("us-ssn",                  UsSsnSettings),
    ControlSpec("attachment",              AttachmentSettings),
    ControlSpec("image-attachment",        ImageAttachmentSettings),
    ControlSpec("dropdown-select1",        DropdownSelect1Settings),
    ControlSpec("dropdown-select1-search", DropdownSelect1SearchSettings),
    ControlSpec("databound-select1",       DataboundSelect1Settings),
    ControlSpec("databound-select1-search",DataboundSelect1SearchSettings),
    ControlSpec("databound-select-search", DataboundSelectSearchSettings),
  )

  private val specsByLocalName: Map[String, ControlSpec] =
    AllSpecs.map(s => s.localName -> s).toMap

  def isCustomSettingsSupported(uri: String, localName: String): Boolean =
    uri == FRNamespace && specsByLocalName.contains(localName)

  def getSpec(uri: String, localName: String): Option[ControlSpec] =
    if (uri == FRNamespace) specsByLocalName.get(localName) else None

  def getSpec(specName: String): Option[ControlSpec] =
    specsByLocalName.get(specName)

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
        // Specific checks: for number/currency, decimal-separator and grouping-separator must differ if both set
        if (spec.localName == "number" || spec.localName == "currency") {
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

  def validateCustomSettings(
    uri        : String,
    localName  : String,
    controlName: String,
    custom     : Map[String, Json]
  ): Unit =
    validateCustomSettings(getSpec(uri, localName), localName, controlName, custom)

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
          if (settingDef.name == "decimalSeparator" && str.length > 1) {
            throw new IllegalArgumentException(
              s"Custom setting '$rawKey' must be at most a single character, got '$str'"
            )
          }
          if (settingDef.name == "groupingSeparator" && str.length > 1) {
            throw new IllegalArgumentException(
              s"Custom setting '$rawKey' must be at most a single character, got '$str'"
            )
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
