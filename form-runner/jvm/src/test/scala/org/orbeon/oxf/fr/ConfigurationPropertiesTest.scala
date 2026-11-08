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

import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.fr.persistence.PersistencePropertyProvider
import org.orbeon.oxf.fr.process.SimpleProcess
import org.orbeon.oxf.properties.{PropertyLoader, PropertyStore}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.jdk.CollectionConverters.*


class ConfigurationPropertiesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("FormRunnerConfiguration XML transformations") {

    it("must round-trip empty <properties> to <configuration> with defaults and back to empty <properties>") {
      val emptyPropsXml = """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema"/>"""
      val emptyPropsDoc = IOSupport.readOrbeonDom(emptyPropsXml)

      val configDoc = FormRunnerConfiguration.propertiesDomToConfigurationDom(emptyPropsDoc)
      val curatedElem = configDoc.getRootElement.element("curated")
      assert(curatedElem != null)

      val defaultLangElem = curatedElem.element("system").element("default-language")
      assert(defaultLangElem.attributeValue("override") == "false")
      assert(defaultLangElem.attributeValue("value") == "en")

      val navbarHomeElem = curatedElem.element("navbar").element("navbar-home-link")
      assert(navbarHomeElem.attributeValue("override") == "false")
      assert(navbarHomeElem.attributeValue("value") == "true")

      val landingElem = curatedElem.element("landing").element("landing-page-size")
      assert(landingElem.attributeValue("override") == "false")
      assert(landingElem.attributeValue("value") == "8")

      val adminElem = curatedElem.element("admin").element("home-page-size")
      assert(adminElem.attributeValue("override") == "false")
      assert(adminElem.attributeValue("value") == "20")

      val importElem = curatedElem.element("import").element("export-page-enable")
      assert(importElem.attributeValue("override") == "false")
      assert(importElem.attributeValue("value") == "false")

      val roundTripPropsDoc = FormRunnerConfiguration.configurationDomToPropertiesDom(configDoc)
      assert(roundTripPropsDoc.getRootElement.elements("property").isEmpty)
    }

    it("must round-trip global curated properties, app/form overrides, and custom properties") {
      val inputPropsXml =
        """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:saxon="http://saxon.sf.net/">
          |    <property as="xs:string"  name="oxf.fr.default-language.*.*"                        value="fr"/>
          |    <property as="xs:integer" name="oxf.fr.detail.autosave-delay.*.*"                   value="2500"/>
          |    <property as="xs:boolean" name="oxf.xforms.session-heartbeat"                       value="false"/>
          |    <property as="xs:string"  name="oxf.fr.detail.view.appearance.*.*"                  value="wizard"/>
          |    <property as="xs:boolean" name="oxf.xforms.analysis.calculate"                      value="true"/>
          |    <property as="xs:string"  name="oxf.fr.email.smtp.host.*.*"                         value="smtp.example.org"/>
          |    <property as="xs:boolean" name="oxf.fb.mcp.enable"                                  value="true"/>
          |    <property as="xs:string"  name="oxf.xforms.format.input.date"                       value="[D]/[M]/[Y]"/>
          |    <property as="xs:boolean" name="oxf.fr.authentication.user-menu.enable"             value="true"/>
          |    <property as="xs:string"  name="oxf.fr.default-language.acme.order"                 value="de"/>
          |    <property as="xs:integer" name="oxf.fr.summary.page-size.acme.order"                value="50"/>
          |    <property as="xs:string"  name="oxf.fr.detail.rendered-page-orientation.acme.order" value="landscape"/>
          |    <property as="xs:string"  name="my.custom.property" profiles="prod" processor-name="oxf:page-flow">line 1
          |line 2</property>
          |</properties>""".stripMargin

      val inputPropsDoc     = IOSupport.readOrbeonDom(inputPropsXml)
      val configDoc         = FormRunnerConfiguration.propertiesDomToConfigurationDom(inputPropsDoc)
      val roundTripPropsDoc = FormRunnerConfiguration.configurationDomToPropertiesDom(configDoc)

      val defs = PropertyStore.parseToPropertyDefinitions(roundTripPropsDoc)
      assert(defs.size == 13)

      val byName = defs.asScala.map(d => d.getName -> d).toMap
      assert(byName("oxf.fr.default-language.*.*").getValue == "fr")
      assert(byName("oxf.fr.detail.autosave-delay.*.*").getValue == "2500")
      assert(byName("oxf.xforms.session-heartbeat").getValue == "false")
      assert(byName("oxf.fr.detail.view.appearance.*.*").getValue == "wizard")
      assert(byName("oxf.xforms.analysis.calculate").getValue == "true")
      assert(byName("oxf.fr.email.smtp.host.*.*").getValue == "smtp.example.org")
      assert(byName("oxf.fb.mcp.enable").getValue == "true")
      assert(byName("oxf.xforms.format.input.date").getValue == "[D]/[M]/[Y]")
      assert(byName("oxf.fr.authentication.user-menu.enable").getValue == "true")
      assert(byName("oxf.fr.default-language.acme.order").getValue == "de")
      assert(byName("oxf.fr.summary.page-size.acme.order").getValue == "50")
      assert(byName("oxf.fr.detail.rendered-page-orientation.acme.order").getValue == "landscape")

      val customDef = byName("my.custom.property")
      assert(customDef.getValue.toString.contains("line 1"))
      assert(customDef.getValue.toString.contains("line 2"))
      assert(customDef.getProfiles.contains("prod"))
    }

    it("must support property profiles on profile-capable curated properties and preserve non-profile properties in custom-properties") {
      val inputPropsXml =
        """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
          |    <property as="xs:string"  name="oxf.fr.detail.buttons.*.*"               profiles="internal"       value="save-final pdf"/>
          |    <property as="xs:boolean" name="oxf.fr.detail.pdf.barcode.hr.onboarding" profiles="internal admin" value="true"/>
          |    <property as="xs:integer" name="oxf.fr.summary.page-size.*.*"            profiles="internal"       value="100"/>
          |    <property as="xs:string"  name="oxf.fr.permissions.hr.onboarding"        profiles="internal"       value="{}"/>
          |</properties>""".stripMargin

      val inputPropsDoc = IOSupport.readOrbeonDom(inputPropsXml)
      val configDoc     = FormRunnerConfiguration.propertiesDomToConfigurationDom(inputPropsDoc)
      val configRoot    = configDoc.getRootElement

      // 1. Check <app-form-overrides> contains the 2 profile-capable overrides
      val overrides = configRoot.element("app-form-overrides").elements("override")
      assert(overrides.size == 2)

      val firstOverride = overrides.head
      assert(firstOverride.attributeValue("app") == "*")
      assert(firstOverride.attributeValue("form") == "*")
      assert(firstOverride.attributeValue("profiles") == "internal")
      val buttonsElem = firstOverride.element("curated").element("detail").element("detail-buttons")
      assert(buttonsElem.attributeValue("override") == "true")
      assert(buttonsElem.attributeValue("value") == "save-final pdf")
      assert(buttonsElem.attributeValue("has-profiles") == "true")

      val secondOverride = overrides(1)
      assert(secondOverride.attributeValue("app") == "hr")
      assert(secondOverride.attributeValue("form") == "onboarding")
      assert(secondOverride.attributeValue("profiles") == "internal admin")
      val barcodeElem = secondOverride.element("curated").element("pdf").element("pdf-barcode")
      assert(barcodeElem.attributeValue("override") == "true")
      assert(barcodeElem.attributeValue("value") == "true")
      assert(barcodeElem.attributeValue("has-profiles") == "true")

      // 2. Check non-profile curated properties with profiles="..." fall back to <custom-properties>
      val customProps = configRoot.element("custom-properties").elements("property")
      assert(customProps.size == 2)
      assert(customProps.map(_.attributeValue("name")).toSet == Set("oxf.fr.summary.page-size.*.*", "oxf.fr.permissions.hr.onboarding"))

      // 3. Round-trip back to <properties>
      val roundTripPropsDoc = FormRunnerConfiguration.configurationDomToPropertiesDom(configDoc)
      val defs              = PropertyStore.parseToPropertyDefinitions(roundTripPropsDoc).asScala
      assert(defs.size == 4)

      val byName = defs.map(d => d.getName -> d).toMap
      assert(byName("oxf.fr.detail.buttons.*.*").getProfiles.asScala.toList == List("internal"))
      assert(byName("oxf.fr.detail.buttons.*.*").getValue == "save-final pdf")
      assert(byName("oxf.fr.detail.pdf.barcode.hr.onboarding").getProfiles.asScala.toList == List("internal", "admin"))
      assert(byName("oxf.fr.detail.pdf.barcode.hr.onboarding").getValue == "true")
      assert(byName("oxf.fr.summary.page-size.*.*").getProfiles.asScala.toList == List("internal"))
      assert(byName("oxf.fr.permissions.hr.onboarding").getProfiles.asScala.toList == List("internal"))
    }

    it("must round-trip global and app/form Form Runner and XBL component resource overrides, including HTML flags") {
      val inputPropsXml =
        """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          |    <property as="xs:string"  name="oxf.fr.resource.*.*.en.detail.messages.save-success"         value="&lt;b&gt;Saved!&lt;/b&gt;"/>
          |    <property as="xs:boolean" name="oxf.fr.resource.*.*.en.detail.messages.save-success.html"    value="true"/>
          |    <property as="xs:string"  name="oxf.fr.resource.*.*.en.xbl.fr:content-filter.alert"          value="Inappropriate content."/>
          |    <property as="xs:string"  name="oxf.fr.resource.acme.order.fr.buttons.save-final"            value="Enregistrer la commande"/>
          |    <property as="xs:string"  name="oxf.fr.resource.acme.order.fr.xbl.fr:attachment.empty"       value="Aucun fichier joint"/>
          |</properties>""".stripMargin

      val inputPropsDoc = IOSupport.readOrbeonDom(inputPropsXml)
      val configDoc     = FormRunnerConfiguration.propertiesDomToConfigurationDom(inputPropsDoc)
      val configRoot    = configDoc.getRootElement

      // 1. Check global <resources>
      val globalMessages = configRoot.element("resources").elements("message")
      assert(globalMessages.size == 2)

      val msg1 = globalMessages.head
      assert(msg1.attributeValue(org.orbeon.oxf.xml.XMLConstants.XML_LANG_QNAME) == "en")
      assert(msg1.attributeValue("name") == "detail.messages.save-success")
      assert(msg1.attributeValue("html") == "true")
      assert(msg1.getText == "<b>Saved!</b>")

      val msg2 = globalMessages(1)
      assert(msg2.attributeValue(org.orbeon.oxf.xml.XMLConstants.XML_LANG_QNAME) == "en")
      assert(msg2.attributeValue("name") == "xbl.fr:content-filter.alert")
      assert(msg2.attributeValue("html") == "false")
      assert(msg2.getText == "Inappropriate content.")

      // 2. Check app/form <override><resources>
      val overrides = configRoot.element("app-form-overrides").elements("override")
      assert(overrides.size == 1)
      val acmeOrder = overrides.head
      assert(acmeOrder.attributeValue("app") == "acme")
      assert(acmeOrder.attributeValue("form") == "order")
      val acmeMessages = acmeOrder.element("resources").elements("message")
      assert(acmeMessages.size == 2)
      assert(acmeMessages.head.attributeValue("name") == "buttons.save-final")
      assert(acmeMessages.head.getText == "Enregistrer la commande")
      assert(acmeMessages(1).attributeValue("name") == "xbl.fr:attachment.empty")
      assert(acmeMessages(1).getText == "Aucun fichier joint")

      // 3. Round-trip back to <properties>
      val roundTripPropsDoc = FormRunnerConfiguration.configurationDomToPropertiesDom(configDoc)
      val defs              = PropertyStore.parseToPropertyDefinitions(roundTripPropsDoc).asScala
      assert(defs.size == 5)

      val byName = defs.map(d => d.getName -> d).toMap
      assert(byName("oxf.fr.resource.*.*.en.detail.messages.save-success").getValue == "<b>Saved!</b>")
      assert(byName("oxf.fr.resource.*.*.en.detail.messages.save-success.html").getValue == "true")
      assert(byName("oxf.fr.resource.*.*.en.detail.messages.save-success.html").getType == "boolean")
      assert(byName("oxf.fr.resource.*.*.en.xbl.fr:content-filter.alert").getValue == "Inappropriate content.")
      assert(byName("oxf.fr.resource.*.*.en.xbl.fr:content-filter.alert").getNamespaces.get("fr") == "http://orbeon.org/oxf/xml/form-runner")
      assert(byName("oxf.fr.resource.acme.order.fr.buttons.save-final").getValue == "Enregistrer la commande")
      assert(byName("oxf.fr.resource.acme.order.fr.xbl.fr:attachment.empty").getValue == "Aucun fichier joint")
    }
  }

  describe("PersistencePropertyProvider and built-in orbeon/configuration form") {

    it("must save configuration via orbeon/configuration form and load properties via PersistencePropertyProvider") {
      withTempSQLite { connection =>

        // 1. Open /fr/orbeon/configuration/new
        val (processorService, docOpt, _) =
          runFormRunner(
            app  = "orbeon",
            form = "configuration",
            mode = "new"
          )

        assert(docOpt.isDefined)
        val doc = docOpt.get

        // 2. Modify configuration via UI controls / instance and save
        withTestExternalContext { ec =>
          withFormRunnerDocument(processorService, doc) {
            val overrideControl       = resolveObject[XFormsSingleNodeControl]("default-language-override-control").get
            val overrideSelectControl = ControlsIterator(overrideControl, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val valueControl          = resolveObject[XFormsSingleNodeControl]("default-language-value-control").get

            // Initially, override checkbox must be readwrite and value control must be readonly
            assert(! overrideControl.isReadonly)
            assert(! overrideSelectControl.isReadonly)
            assert(valueControl.isReadonly)

            // Check the Override checkbox via client event and verify value control becomes readwrite
            val encodedTrue = overrideSelectControl.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).next()._1.externalValue(overrideSelectControl.mustEncodeValues)
            setControlValueWithEventSearchNested(overrideSelectControl.effectiveId, encodedTrue)
            assert(! valueControl.isReadonly)

            // Set the value control to "fr" via client event
            setControlValueWithEventSearchNested(valueControl.effectiveId, "fr")

            // Test XPath 2.0 validation on pdf-filename-value-control
            val pdfOverrideControl       = resolveObject[XFormsSingleNodeControl]("pdf-filename-override-control").get
            val pdfOverrideSelectControl = ControlsIterator(pdfOverrideControl, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val pdfValueControl          = resolveObject[XFormsSingleNodeControl]("pdf-filename-value-control").get

            assert(pdfValueControl.isValid)
            setControlValueWithEventSearchNested(pdfOverrideSelectControl.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(pdfValueControl.effectiveId, "concat('unclosed")
            assert(! pdfValueControl.isValid)
            setControlValueWithEventSearchNested(pdfValueControl.effectiveId, "concat(fr:form-title(), '.pdf')")
            assert(pdfValueControl.isValid)

            // Test XPath 2.0 Value Template (AVT) validation on auth-user-menu-uri-login-value-control
            val loginOverrideControl       = resolveObject[XFormsSingleNodeControl]("auth-user-menu-uri-login-override-control").get
            val loginOverrideSelectControl = ControlsIterator(loginOverrideControl, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val loginValueControl          = resolveObject[XFormsSingleNodeControl]("auth-user-menu-uri-login-value-control").get

            assert(loginValueControl.isValid)
            setControlValueWithEventSearchNested(loginOverrideSelectControl.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(loginValueControl.effectiveId, "/fr/auth?source={if (}")
            assert(! loginValueControl.isValid)
            setControlValueWithEventSearchNested(loginValueControl.effectiveId, "/fr/auth?source={xxf:get-request-path()}")
            assert(loginValueControl.isValid)

            val configInstance = instance("fr-configuration-instance").get
            val configRoot     = configInstance.rootElement

            // Add a custom property
            val customPropsNodes  = configRoot / "custom-properties"
            val templatePropNodes = configRoot / "templates" / "property"
            val inserted          = XFormsAPI.insert(into = customPropsNodes, origin = templatePropNodes)
            XFormsAPI.setvalue(inserted /@ "name", "oxf.test.custom.persistence.prop")
            XFormsAPI.setvalue(inserted /@ "type", "xs:integer")
            XFormsAPI.setvalue(inserted, "42")

            // Run save-final process
            SimpleProcess.runProcessByName("oxf.fr.detail.process", "save-final")
          }

          // 3. Verify that data was saved in orbeon_form_data under orbeon / configuration / _
          val SelectXmlSql =
            """SELECT xml
              |FROM orbeon_form_data
              |WHERE app = 'orbeon' AND form = 'configuration' AND document_id = '_' AND draft = 'N' AND deleted = 'N'""".stripMargin
          useAndClose(connection.prepareStatement(SelectXmlSql)) { ps =>
            useAndClose(ps.executeQuery()) { rs =>
              assert(rs.next())
              val savedXml = rs.getString(1)
              assert(savedXml.contains("oxf.fr.default-language.*.*"))
              assert(savedXml.contains("oxf.test.custom.persistence.prop"))
              assert(! savedXml.contains("data-format-version"))
            }
          }

          // 4. Verify that PropertyLoader loads the saved properties via PersistencePropertyProvider
          ProcessorService.withProcessorService(processorService) {
            PersistencePropertyProvider.invalidateCache()
            ec.getRequest.getAttributesMap.remove(PropertyLoader.RequestPropertiesAttributeName)

            val store = PropertyLoader.getPropertyStore(Option(ec.getRequest))
            assert(store.globalPropertySet.getString("oxf.fr.default-language.my-app.my-form").contains("fr"))
            assert(store.globalPropertySet.getIntOpt("oxf.test.custom.persistence.prop").contains(42))
          }
        }

        // 5. Open /fr/orbeon/configuration/edit/_ and verify UI instance reflects saved properties
        val (processorService2, docOpt2, _) =
          runFormRunner(
            app        = "orbeon",
            form       = "configuration",
            mode       = "edit",
            documentId = Some("_")
          )

        assert(docOpt2.isDefined)
        val doc2 = docOpt2.get

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService2, doc2) {
            val configInstance  = instance("fr-configuration-instance").get
            val configRoot      = configInstance.rootElement
            val defaultLangNode = (configRoot / "curated" / "system" / "default-language").head

            assert((defaultLangNode /@ "override").stringValue == "true")
            assert((defaultLangNode /@ "value").stringValue == "fr")

            val customPropNode = (configRoot / "custom-properties" / "property").head
            assert((customPropNode /@ "name").stringValue == "oxf.test.custom.persistence.prop")
            assert((customPropNode /@ "type").stringValue == "xs:integer")
            assert(customPropNode.stringValue == "42")
          }
        }
      }
    }
  }
}
