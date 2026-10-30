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
package org.orbeon.oxf.fb.mcp

import io.circe.Json
import org.orbeon.builder.mcp.ControlCustomSettings.*
import org.orbeon.builder.mcp.ControlCustomSettingsParser
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.scalatest.funspec.AnyFunSpecLike

class ControlCustomSettingsParserTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("ControlCustomSettingsParser") {
    it("must parse basic settings from XBL binding metadata") {
      val binding: NodeInfo =
        <xbl:binding id="fr-number" xmlns:xbl="http://www.w3.org/ns/xbl">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <custom-settings>
              <setting name="prefix" type="xs:string"/>
              <setting name="suffix" type="xs:string"/>
              <setting name="decimal-separator" type="xs:string" values="comma dot"/>
              <setting name="grouping-separator" type="xs:string" values="space comma dot apostrophe empty"/>
              <setting name="digits-after-decimal" type="xs:integer" minimum="0"/>
              <setting name="round-to" type="xs:decimal"/>
              <setting name="fr:pdf-appearance" type="xs:string" values="separated fields digits"/>
              <setting name="fr:pdf-digits-count" type="xs:integer" minimum="1" maximum="30"/>
            </custom-settings>
          </metadata>
        </xbl:binding>

      val specOpt = ControlCustomSettingsParser.fromBinding(binding)
      assert(specOpt.isDefined)
      val spec = specOpt.get
      assert(spec.localName == "number")
      assert(spec.settings.size == 8)

      val prefixDef = spec.findSetting("prefix").get
      assert(prefixDef.settingType == StringType)
      assert(prefixDef.xmlAttribute.contains("prefix"))
      assert(prefixDef.xmlNamespace.isEmpty)

      val decDef = spec.findSetting("decimalSeparator").get
      assert(decDef.settingType == StringType)
      assert(decDef.allowedValues.contains(Set("comma", "dot")))
      assert(decDef.xmlAttribute.contains("decimal-separator"))

      val digitsDef = spec.findSetting("digitsAfterDecimal").get
      assert(digitsDef.settingType == IntegerType)
      assert(digitsDef.minInt.contains(0))

      val pdfDigitsDef = spec.findSetting("pdfDigitsCount").get
      assert(pdfDigitsDef.xmlNamespace.contains(FRNamespace))
      assert(pdfDigitsDef.xmlAttribute.contains("pdf-digits-count"))
      assert(pdfDigitsDef.minInt.contains(1))
      assert(pdfDigitsDef.maxInt.contains(30))
    }

    it("must parse select/itemset settings with items/itemLabel/itemValue/itemHint") {
      val binding: NodeInfo =
        <xbl:binding id="fr-databound-select1" xmlns:xbl="http://www.w3.org/ns/xbl">
          <metadata xmlns="http://orbeon.org/oxf/xml/form-builder">
            <custom-settings>
              <setting name="items" type="xs:string"/>
              <setting name="item-label" type="xs:string"/>
              <setting name="item-value" type="xs:string"/>
              <setting name="item-hint" type="xs:string"/>
              <setting name="fr:cache" type="xs:boolean" default="true"/>
              <setting name="fb:selection" type="xs:string" values="open closed"/>
            </custom-settings>
          </metadata>
        </xbl:binding>

      val spec = ControlCustomSettingsParser.fromBinding(binding).get
      assert(spec.localName == "databound-select1")

      val itemsDef = spec.findSetting("items").get
      assert(itemsDef.xmlAttribute.isEmpty)

      val itemLabelDef = spec.findSetting("itemLabel").get
      assert(itemLabelDef.xmlAttribute.isEmpty)

      val cacheDef = spec.findSetting("cache").get
      assert(cacheDef.defaultVal.contains(Json.fromBoolean(true)))
      assert(cacheDef.xmlNamespace.contains(FRNamespace))

      val selectionDef = spec.findSetting("selection").get
      assert(selectionDef.xmlNamespace.contains(FBNamespace))
      assert(selectionDef.allowedValues.contains(Set("open", "closed")))
    }

    it("must validate settings correctly") {
      val spec = ControlSpec(
        localName = "test",
        settings = List(
          SettingDef("strSetting", StringType, allowedValues = Some(Set("foo", "bar"))),
          SettingDef("intSetting", IntegerType, minInt = Some(1), maxInt = Some(10)),
          SettingDef("boolSetting", BooleanType)
        )
      )

      // Valid settings
      validateCustomSettings(Some(spec), "test", "test-ctrl", Map(
        "strSetting" -> Json.fromString("foo"),
        "intSetting" -> Json.fromInt(5),
        "boolSetting" -> Json.fromBoolean(true)
      ))

      // Disallowed setting name
      assertThrows[IllegalArgumentException] {
        validateCustomSettings(Some(spec), "test", "test-ctrl", Map("unknown" -> Json.fromString("val")))
      }

      // Disallowed string value
      assertThrows[IllegalArgumentException] {
        validateCustomSettings(Some(spec), "test", "test-ctrl", Map("strSetting" -> Json.fromString("baz")))
      }

      // Integer below minimum
      assertThrows[IllegalArgumentException] {
        validateCustomSettings(Some(spec), "test", "test-ctrl", Map("intSetting" -> Json.fromInt(0)))
      }

      // Integer above maximum
      assertThrows[IllegalArgumentException] {
        validateCustomSettings(Some(spec), "test", "test-ctrl", Map("intSetting" -> Json.fromInt(11)))
      }

      // Type mismatch
      assertThrows[IllegalArgumentException] {
        validateCustomSettings(Some(spec), "test", "test-ctrl", Map("boolSetting" -> Json.fromString("foo")))
      }
    }

    it("must parse `<fr:country>` custom settings from `country.xbl`") {
      val binding = {
        import org.orbeon.scaxon.SimplePath.*
        val docWrapper = new org.orbeon.dom.saxon.DocumentWrapper(
          org.orbeon.oxf.resources.ResourceManagerWrapper.instance.getContentAsOrbeonDom("/xbl/orbeon/country/country.xbl"),
          null,
          org.orbeon.oxf.util.XPath.GlobalConfiguration
        )
        (docWrapper.rootElement / "*:binding").head
      }

      val specOpt = ControlCustomSettingsParser.fromBinding(binding)
      assert(specOpt.isDefined)
      val spec = specOpt.get
      assert(spec.localName == "country")
      assert(spec.settings.map(_.name) == List("preferredCountries", "includeCountries", "excludeCountries", "showCode", "showFlag"))
      assert(spec.findSetting("preferredCountries").exists(_.settingType == StringType))
      assert(spec.findSetting("includeCountries").exists(_.settingType == StringType))
      assert(spec.findSetting("excludeCountries").exists(_.settingType == StringType))
      assert(spec.findSetting("showCode").exists(_.settingType == BooleanType))
      assert(spec.findSetting("showFlag").exists(_.settingType == BooleanType))
    }

    it("must parse `<fr:phone>` custom settings from `phone.xbl`") {
      val binding = {
        import org.orbeon.scaxon.SimplePath.*
        val docWrapper = new org.orbeon.dom.saxon.DocumentWrapper(
          org.orbeon.oxf.resources.ResourceManagerWrapper.instance.getContentAsOrbeonDom("/xbl/orbeon/phone/phone.xbl"),
          null,
          org.orbeon.oxf.util.XPath.GlobalConfiguration
        )
        (docWrapper.rootElement / "*:binding").head
      }

      val specOpt = ControlCustomSettingsParser.fromBinding(binding)
      assert(specOpt.isDefined)
      val spec = specOpt.get
      assert(spec.localName == "phone")
      assert(spec.settings.map(_.name) == List("defaultCountry", "preferredCountries", "includeCountries", "excludeCountries", "showCode", "showFlag"))
      assert(spec.findSetting("defaultCountry").exists(_.settingType == StringType))
      assert(spec.findSetting("preferredCountries").exists(_.settingType == StringType))
      assert(spec.findSetting("includeCountries").exists(_.settingType == StringType))
      assert(spec.findSetting("excludeCountries").exists(_.settingType == StringType))
      assert(spec.findSetting("showCode").exists(_.settingType == BooleanType))
      assert(spec.findSetting("showFlag").exists(_.settingType == BooleanType))
    }
  }
}
