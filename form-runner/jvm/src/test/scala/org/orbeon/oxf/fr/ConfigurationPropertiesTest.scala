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
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.fr.persistence.PersistencePropertyProvider
import org.orbeon.oxf.fr.process.SimpleProcess
import org.orbeon.oxf.properties.{PropertyLoader, PropertyStore}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.controls.{XFormsSelect1Control, XFormsSelectControl}
import org.orbeon.oxf.xforms.control.{XFormsSingleNodeControl, XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.state.{XFormsDocumentCache, XFormsStaticStateCache}
import org.orbeon.oxf.xml.XMLConstants
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

      val docsSizeElem = curatedElem.element("caches").element("cache-documents-size")
      assert(docsSizeElem.attributeValue("override") == "false")
      assert(docsSizeElem.attributeValue("value") == "500")

      val staticSizeElem = curatedElem.element("caches").element("cache-static-state-size")
      assert(staticSizeElem.attributeValue("override") == "false")
      assert(staticSizeElem.attributeValue("value") == "200")

      val staticBytesElem = curatedElem.element("caches").element("cache-static-state-max-size-bytes")
      assert(staticBytesElem.attributeValue("override") == "false")
      assert(staticBytesElem.attributeValue("value") == "15%")
      assert(staticBytesElem.attributeValue("name") == "oxf.xforms.cache.static-states.max-size-bytes")

      // Legacy property names saved in older configuration XML must migrate into the curated controls seamlessly
      val legacyCachePropsDoc =
        IOSupport.readOrbeonDom(
          """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
            |    <property as="xs:integer" name="oxf.xforms.cache.documents.size" value="33"/>
            |</properties>""".stripMargin
        )
      val migratedConfigDoc = FormRunnerConfiguration.propertiesDomToConfigurationDom(legacyCachePropsDoc)
      val migratedDocsElem  = migratedConfigDoc.getRootElement.element("curated").element("caches").element("cache-documents-size")
      assert(migratedDocsElem.attributeValue("override") == "true")
      assert(migratedDocsElem.attributeValue("value") == "33")
      val migratedOutDoc = FormRunnerConfiguration.configurationDomToPropertiesDom(migratedConfigDoc)
      assert(migratedOutDoc.getRootElement.element("property").attributeValue("name") == "oxf.xforms.cache.live-documents.size")

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

    it("must round-trip detail process overrides and custom processes across scopes and exclude non-detail / dotted / internal processes") {
      val inputXml =
        """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
          |    <property as="xs:string" name="oxf.fr.detail.process.save-final.*.*" value="require-valid then save"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.my-custom.*.*" value="validate then save"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.save-final.acme.*" value="save(draft = 'false')"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.submit.acme.order" value="validate then send(uri = 'https://example.org')"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.save-final.*.*" profiles="mobile" value="save"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.custom-mobile.acme.order" profiles="mobile" value="email"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.after-controls.foreground.new.*.*" value="log(message = 'loaded')"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.save.orbeon.builder" value="save"/>
          |    <property as="xs:string" name="oxf.fr.detail.process.save-final.orbeon.configuration" value="save"/>
          |    <property as="xs:string" name="oxf.fr.summary.process.delete.*.*" value="delete"/>
          |</properties>""".stripMargin

      val inputDoc  = IOSupport.readOrbeonDom(inputXml)
      val configDoc = FormRunnerConfiguration.propertiesDomToConfigurationDom(inputDoc)
      val root      = configDoc.getRootElement

      // Global processes
      val globalProcs = root.element("processes").elements("process").map(e => e.attributeValue("name") -> e.getText).toMap
      assert(globalProcs == Map(
        "save-final" -> "require-valid then save",
        "my-custom"  -> "validate then save"
      ))

      // Scoped processes (including profiles)
      val overrides = root.element("app-form-overrides").elements("override").toList
      def procsFor(app: String, form: String, profiles: String): Map[String, String] =
        overrides
          .find(o => o.attributeValue("app") == app && o.attributeValue("form") == form && o.attributeValue("profiles") == profiles)
          .map(_.element("processes").elements("process").map(e => e.attributeValue("name") -> e.getText).toMap)
          .getOrElse(Map.empty)

      assert(procsFor("*", "*", "mobile")         == Map("save-final" -> "save"))
      assert(procsFor("acme", "*", "")            == Map("save-final" -> "save(draft = 'false')"))
      assert(procsFor("acme", "order", "")        == Map("submit" -> "validate then send(uri = 'https://example.org')"))
      assert(procsFor("acme", "order", "mobile")  == Map("custom-mobile" -> "email"))

      // Excluded properties remain in custom-properties
      val customNames = root.element("custom-properties").elements("property").map(_.attributeValue("name")).toSet
      assert(customNames == Set(
        "oxf.fr.detail.process.after-controls.foreground.new.*.*",
        "oxf.fr.detail.process.save.orbeon.builder",
        "oxf.fr.detail.process.save-final.orbeon.configuration",
        "oxf.fr.summary.process.delete.*.*"
      ))

      // Round-trip back to <properties>
      val outDoc   = FormRunnerConfiguration.configurationDomToPropertiesDom(configDoc)
      val outProps = outDoc.getRootElement.elements("property").map { p =>
        (
          p.attributeValue("name"),
          Option(p.attributeValue("profiles")).getOrElse(""),
          Option(p.attributeValue("value")).getOrElse(p.getText)
        )
      }.toSet

      assert(outProps == Set(
        ("oxf.fr.detail.process.save-final.*.*", "", "require-valid then save"),
        ("oxf.fr.detail.process.my-custom.*.*", "", "validate then save"),
        ("oxf.fr.detail.process.save-final.*.*", "mobile", "save"),
        ("oxf.fr.detail.process.save-final.acme.*", "", "save(draft = 'false')"),
        ("oxf.fr.detail.process.submit.acme.order", "", "validate then send(uri = 'https://example.org')"),
        ("oxf.fr.detail.process.custom-mobile.acme.order", "mobile", "email"),
        ("oxf.fr.detail.process.after-controls.foreground.new.*.*", "", "log(message = 'loaded')"),
        ("oxf.fr.detail.process.save.orbeon.builder", "", "save"),
        ("oxf.fr.detail.process.save-final.orbeon.configuration", "", "save"),
        ("oxf.fr.summary.process.delete.*.*", "", "delete")
      ))

      // Built-in detail process helpers
      val builtins = FormRunnerConfiguration.builtinDetailProcessNames().toSet
      assert(builtins.contains("save-final"))
      assert(builtins.contains("require-uploads"))
      assert(builtins.contains("submit"))
      assert(FormRunnerConfiguration.defaultDetailProcessDefinition("save-final", "*", "*").nonEmpty)
      assert(FormRunnerConfiguration.isValidProcessDefinition("validate then save"))
      assert(! FormRunnerConfiguration.isValidProcessDefinition("validate then (save"))
      assert(FormRunnerConfiguration.formatProcessDefinition("validate then save") == "validate\nthen save")
      assert(FormRunnerConfiguration.formatProcessDefinition("validate then (save") == "validate then (save")
    }

    it("must provide complete metadata and resource translations for all 20 Form Runner languages") {
      val expectedLangs = List(
        "ar", "ca", "da", "de", "en", "es", "fi", "fr", "hu", "it",
        "ja", "nl", "no", "pl", "pt", "ru", "sv", "tr", "zh-Hans", "zh-Hant"
      )

      val formIs = getClass.getResourceAsStream("/forms/orbeon/configuration/form/form.xhtml")
      assert(formIs ne null)
      val formDoc = useAndClose(formIs)(IOSupport.readOrbeonDom)

      val metadataElem =
        formDoc.getRootElement
          .element("head")
          .elements("model")
          .find(_.attributeValue("id") == "fr-form-metadata")
          .orElse(
            formDoc.getRootElement
              .element("head")
              .elements("model")
              .flatMap(_.elements("instance"))
              .find(_.attributeValue("id") == "fr-form-metadata")
          )
          .get
          .element("metadata")

      val titleLangs = metadataElem.elements("title").flatMap(e => Option(e.attributeValue(XMLConstants.XML_LANG_QNAME)).filter(_ => e.getText.trim.nonEmpty)).toSet
      val descLangs  = metadataElem.elements("description").flatMap(e => Option(e.attributeValue(XMLConstants.XML_LANG_QNAME)).filter(_ => e.getText.trim.nonEmpty)).toSet
      assert(titleLangs == expectedLangs.toSet)
      assert(descLangs  == expectedLangs.toSet)

      def collectLeaves(el: org.orbeon.dom.Element, prefix: String = ""): List[(String, String)] =
        el.elements.toList.flatMap { ch =>
          val p = s"$prefix/${ch.getName}"
          if (ch.elements.isEmpty) List(p -> ch.getText) else collectLeaves(ch, p)
        }

      val enIs = getClass.getResourceAsStream("/forms/orbeon/configuration/form/config-resources-en.xml")
      assert(enIs ne null)
      val enRoot   = useAndClose(enIs)(IOSupport.readOrbeonDom).getRootElement
      val enLeaves = collectLeaves(enRoot)
      val enPaths  = enLeaves.map(_._1)
      val enValues = enLeaves.collect { case (p, v) if p.endsWith("/item/value") => v }

      for (lang <- expectedLangs) {
        val resPath = s"/forms/orbeon/configuration/form/config-resources-$lang.xml"
        val is      = getClass.getResourceAsStream(resPath)
        assert(is ne null, s"Missing resource file $resPath")
        val root    = useAndClose(is)(IOSupport.readOrbeonDom).getRootElement
        assert(root.attributeValue(XMLConstants.XML_LANG_QNAME) == lang, s"Unexpected xml:lang in $resPath")

        val leaves = collectLeaves(root)
        assert(leaves.map(_._1) == enPaths, s"Element tree mismatch in $resPath")
        assert(leaves.collect { case (p, v) if p.endsWith("/item/value") => v } == enValues, s"Item values mismatch in $resPath")
        for (((p, txt), (_, enTxt)) <- leaves.zip(enLeaves) if ! p.endsWith("/item/value") && enTxt.trim.nonEmpty)
          assert(txt.trim.nonEmpty, s"Blank translation at $p in $resPath")
      }
    }

    it("must provide a default empty custom property row when none exist and omit it when saving") {
      val inputPropsXml     = """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema"/>"""
      val inputPropsDoc     = IOSupport.readOrbeonDom(inputPropsXml)
      val configDoc         = FormRunnerConfiguration.propertiesDomToConfigurationDom(inputPropsDoc)
      val customProps       = configDoc.getRootElement.element("custom-properties").elements("property")

      assert(customProps.size == 1)
      assert(customProps.head.attributeValue("name") == "")
      assert(customProps.head.attributeValue("type") == "xs:string")
      assert(customProps.head.attributeValue("profiles") == "")
      assert(customProps.head.getText == "")

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

      // 1. Check <app-form-overrides> contains the 2 profile-capable overrides plus the auto-materialized 0-override parent rows (hr / * and hr / onboarding)
      val overrides = configRoot.element("app-form-overrides").elements("override")
      assert(overrides.size == 4)

      val firstOverride = overrides.head
      assert(firstOverride.attributeValue("app") == "*")
      assert(firstOverride.attributeValue("form") == "*")
      assert(firstOverride.attributeValue("profiles") == "internal")
      val buttonsElem = firstOverride.element("curated").element("detail").element("detail-buttons")
      assert(buttonsElem.attributeValue("override") == "true")
      assert(buttonsElem.attributeValue("value") == "save-final pdf")
      assert(buttonsElem.attributeValue("has-profiles") == "true")

      val parentHrOverride = overrides(1)
      assert(parentHrOverride.attributeValue("app") == "hr")
      assert(parentHrOverride.attributeValue("form") == "*")
      assert(parentHrOverride.attributeValue("profiles") == "")

      val parentFormOverride = overrides(2)
      assert(parentFormOverride.attributeValue("app") == "hr")
      assert(parentFormOverride.attributeValue("form") == "onboarding")
      assert(parentFormOverride.attributeValue("profiles") == "")

      val secondOverride = overrides(3)
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

      // 2. Check app/form <override><resources> (with auto-materialized acme / * parent row before acme / order)
      val overrides = configRoot.element("app-form-overrides").elements("override")
      assert(overrides.size == 2)
      val acmeApp = overrides.head
      assert(acmeApp.attributeValue("app") == "acme")
      assert(acmeApp.attributeValue("form") == "*")
      val acmeOrder = overrides(1)
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

    it("must validate JSON strings via isValidJson and normalize home-table-link-to selection whitespace") {
      assert(FormRunnerConfiguration.isValidJson(""))
      assert(FormRunnerConfiguration.isValidJson("   "))
      assert(FormRunnerConfiguration.isValidJson("""{"apps": []}"""))
      assert(FormRunnerConfiguration.isValidJson("""[{"label": "Prod", "url": "https://example.org/fr"}]"""))
      assert(! FormRunnerConfiguration.isValidJson("{bad json"))
      assert(! FormRunnerConfiguration.isValidJson("[1, 2,]"))

      val propsDoc =
        IOSupport.readOrbeonDom(
          """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
            |    <property as="xs:string" name="oxf.fr.home.table.link-to" value="  new   summary "/>
            |</properties>""".stripMargin
        )
      val configDoc = FormRunnerConfiguration.propertiesDomToConfigurationDom(propsDoc)
      val linkToElem = configDoc.getRootElement.element("curated").element("admin").element("home-table-link-to")
      assert(linkToElem.attributeValue("override") == "true")
      assert(linkToElem.attributeValue("selection") == "new summary")
    }

    it("must round-trip LandingCardsConfigXml across all card types, localization modes, and legacy tokens") {
      val complexCards = List(
        LandingCard.QuickLinks(),
        LandingCard.QuickLinks(
          title       = Some(LocalizedString.ByLanguage(Map("_" -> "Team Links"))),
          description = Some(LocalizedString.ByLanguage(Map("en" -> "Shortcuts", "fr" -> "Raccourcis"))),
          links       = List(QuickLinkToken.FormBuilderNew, QuickLinkToken.FormsAdmin),
          layout      = Some(QuickLinksLayout.List)
        ),
        LandingCard.PublishedForms(
          title       = LocalizedString.ByLanguage(Map("_" -> "HR Forms")),
          description = None,
          thumbnail   = None,
          app         = Some("hr"),
          linkTo      = List(LinkTo.Summary, LinkTo.New),
          sort        = Some((SortBy.Title, Some(SortDirection.Ascending)))
        ),
        LandingCard.PublishedForms(
          title       = LocalizedString.ByResource("landing.titles.published-forms"),
          description = Some(LocalizedString.ByResource("landing.descriptions.published-forms")),
          thumbnail   = Some("https://example.org/custom-icon.svg"),
          app         = None,
          linkTo      = List(LinkTo.New),
          sort        = Some((SortBy.LastModified, None))
        ),
        LandingCard.FormData(
          title       = None,
          description = None,
          thumbnail   = None,
          app         = "orbeon",
          form        = "bookshelf",
          version     = Some(2)
        ),
        LandingCard.FormData(
          title       = Some(LocalizedString.ByLanguage(Map("en" -> "Orders", "fr" -> "Commandes"))),
          description = Some(LocalizedString.ByLanguage(Map("_" -> "Recent orders"))),
          thumbnail   = Some("/apps/fr/style/images/orbeon/book.svg"),
          app         = "acme",
          form        = "order",
          version     = None
        )
      )

      val inputJson   = LandingCardsJSON.serializeToJson(complexCards).spaces2
      val editorDom   = LandingCardsConfigXml.toEditorDom(inputJson, "")
      val outputCards = LandingCardsConfigXml.fromEditorDom(editorDom)
      assert(outputCards == complexCards)
      assert(LandingCardsConfigXml.isValidLandingCards(LandingCardsConfigXml.toJsonString(editorDom)))

      // Legacy tokens must upgrade cleanly into structured editor cards
      val legacyDom   = LandingCardsConfigXml.toEditorDom("quick-links admin orbeon/bookshelf/1", "")
      val legacyCards = LandingCardsConfigXml.fromEditorDom(legacyDom)
      assert(legacyCards.size == 3)

      // Validation checks
      assert(FormRunnerConfiguration.isValidLandingCards(""))
      assert(FormRunnerConfiguration.isValidLandingCards("[]"))
      assert(! FormRunnerConfiguration.isValidLandingCards("""[{"card-type": "published-forms", "title": {"_": "   "}}]"""))
      assert(! FormRunnerConfiguration.isValidLandingCards("""[{"card-type": "form-data", "app": "orbeon", "form": ""}]"""))
      assert(! FormRunnerConfiguration.isValidLandingCards("""[{"card-type": "form-data", "app": "bad slug!", "form": "bookshelf"}]"""))

      assert(FormRunnerConfiguration.isValidPermissionsJson(""))
      assert(FormRunnerConfiguration.isValidPermissionsJson("{}"))
      assert(FormRunnerConfiguration.isValidPermissionsJson("""{"anyone": ["create"], "anyone-with-token": ["read"], "any-authenticated-user": ["read"], "roles": {"clerk": ["read", "list"]}}"""))
      assert(! FormRunnerConfiguration.isValidPermissionsJson("""{"anybody": ["create"]}"""))
      assert(! FormRunnerConfiguration.isValidPermissionsJson("""{"roles": {"": ["read"]}}"""))
    }

    it("must auto-create 0-override parent Application rows, sort scopes in canonical tree order, preserve unique @id attributes, and omit empty parent rows on save") {
      val inputPropsXml =
        """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
          |    <property as="xs:string" name="oxf.fr.default-language.zeta.claim"   value="es"/>
          |    <property as="xs:string" name="oxf.fr.default-language.acme.order"   value="fr"/>
          |    <property as="xs:string" name="oxf.fr.detail.buttons.acme.w4"        profiles="admin"    value="pdf"/>
          |    <property as="xs:string" name="oxf.fr.detail.buttons.acme.*"         profiles="admin"    value="save-final"/>
          |    <property as="xs:string" name="oxf.fr.detail.buttons.acme.quote"     profiles="external" value="send"/>
          |    <property as="xs:string" name="oxf.fr.default-language.acme.invoice" value="de"/>
          |    <property as="xs:string" name="oxf.fr.detail.buttons.solo.*"         profiles="abc"      value="save-final"/>
          |    <property as="xs:string" name="oxf.fr.detail.buttons.*.*"            profiles="internal" value="pdf"/>
          |</properties>""".stripMargin

      val configDoc = FormRunnerConfiguration.propertiesDomToConfigurationDom(IOSupport.readOrbeonDom(inputPropsXml))
      val overridesElem = configDoc.getRootElement.element("app-form-overrides")
      val initialScopes =
        overridesElem.elements("override").map { el =>
          (el.attributeValue("app"), el.attributeValue("form"), el.attributeValue("profiles"))
        }.toList

      // Profiled app overrides indent under their unprofiled (app / *) parent, and profiled form overrides
      // indent under their unprofiled (app / form) parent (with 0-override parents auto-materialized as needed)
      assert(initialScopes == List(
        ("*",    "*",       "internal"),
        ("acme", "*",       ""),
        ("acme", "*",       "admin"),
        ("acme", "invoice", ""),
        ("acme", "order",   ""),
        ("acme", "quote",   ""),
        ("acme", "quote",   "external"),
        ("acme", "w4",      ""),
        ("acme", "w4",      "admin"),
        ("solo", "*",       ""),
        ("solo", "*",       "abc"),
        ("zeta", "*",       ""),
        ("zeta", "claim",   "")
      ))

      val initialIds = overridesElem.elements("override").map(_.attributeValue("id")).toList
      assert(initialIds.forall(_.nonEmpty))
      assert(initialIds.distinct.size == initialIds.size)

      // Inserting a new form override with an explicit @id must preserve that @id even when a parent app row is synthesized before it
      val templateOverride = configDoc.getRootElement.element("templates").element("override").createCopy
      templateOverride.addAttribute("id", "scope-99")
      templateOverride.addAttribute("app", "beta")
      templateOverride.addAttribute("form", "survey")
      templateOverride.addAttribute("profiles", "")
      overridesElem.add(templateOverride)

      FormRunnerConfiguration.normalizeOverridesDom(configDoc)

      val afterInsert = overridesElem.elements("override").toList
      val betaApp     = afterInsert.find(el => el.attributeValue("app") == "beta" && el.attributeValue("form") == "*").get
      val betaSurvey  = afterInsert.find(el => el.attributeValue("app") == "beta" && el.attributeValue("form") == "survey").get
      assert(afterInsert.indexOf(betaApp) < afterInsert.indexOf(betaSurvey))
      assert(betaSurvey.attributeValue("id") == "scope-99")
      assert(betaApp.attributeValue("id") != "scope-99")
      assert(afterInsert.map(_.attributeValue("id")).distinct.size == afterInsert.size)

      // Round-tripping back to <properties> must emit only the 8 overridden properties (zero noise from 0-override rows)
      val roundTripPropsDoc = FormRunnerConfiguration.configurationDomToPropertiesDom(configDoc)
      val defs              = PropertyStore.parseToPropertyDefinitions(roundTripPropsDoc).asScala
      assert(defs.size == 8)
    }

    it("must convert fr:quantity data sizes to exact integer bytes and decompose saved byte values cleanly") {
      assert(FormRunnerConfiguration.dataSizeQuantityToBytesOpt("10", "MB").contains(10_000_000L))
      assert(FormRunnerConfiguration.dataSizeQuantityToBytesOpt("1.5", "MB").contains(1_500_000L))
      assert(FormRunnerConfiguration.dataSizeQuantityToBytesOpt("500", "KB").contains(500_000L))
      assert(FormRunnerConfiguration.dataSizeQuantityToBytesOpt("2", "GB").contains(2_000_000_000L))
      assert(FormRunnerConfiguration.dataSizeQuantityToBytesOpt("0", "B").contains(0L))
      assert(FormRunnerConfiguration.dataSizeQuantityToBytesOpt("0.5", "B").isEmpty)
      assert(FormRunnerConfiguration.dataSizeQuantityToBytesOpt("-5", "MB").isEmpty)

      assert(FormRunnerConfiguration.bytesToDisplayQuantity(10_000_000L) == ("10", "MB"))
      assert(FormRunnerConfiguration.bytesToDisplayQuantity(1_500_000L) == ("1.5", "MB"))
      assert(FormRunnerConfiguration.bytesToDisplayQuantity(500_000L) == ("500", "KB"))
      assert(FormRunnerConfiguration.bytesToDisplayQuantity(2_000_000_000L) == ("2", "GB"))
      assert(FormRunnerConfiguration.bytesToDisplayQuantity(1_048_576L) == ("1048576", "B"))

      val propsDoc =
        IOSupport.readOrbeonDom(
          """<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
            |    <property as="xs:string" name="oxf.fr.detail.attachment.max-size-per-file.*.*"              value="10000000"/>
            |    <property as="xs:string" name="oxf.fr.detail.attachment.max-size-aggregate-per-control.*.*" value="1048576"/>
            |    <property as="xs:string" name="oxf.fr.detail.attachment.max-size-aggregate-per-form.*.*"    value="-1"/>
            |    <property as="xs:string" name="oxf.xforms.cache.live-documents.max-size-bytes"              value="256000000"/>
            |</properties>""".stripMargin
        )
      val configDoc = FormRunnerConfiguration.propertiesDomToConfigurationDom(propsDoc)
      val detailEl  = configDoc.getRootElement.element("curated").element("detail")
      val perFileEl = detailEl.element("attachment-max-size-per-file")
      assert(perFileEl.element("mode").getText == "size")
      assert(perFileEl.element("quantity").getText == "10")
      assert(perFileEl.element("quantity").attributeValue("unit") == "MB")

      val perCtrlEl = detailEl.element("attachment-max-size-aggregate-per-control")
      assert(perCtrlEl.element("mode").getText == "size")
      assert(perCtrlEl.element("quantity").getText == "1048576")
      assert(perCtrlEl.element("quantity").attributeValue("unit") == "B")

      val perFormEl = detailEl.element("attachment-max-size-aggregate-per-form")
      assert(perFormEl.element("mode").getText == "unlimited")
      assert(perFormEl.attributeValue("value") == "-1")

      val docsCacheEl = configDoc.getRootElement.element("curated").element("caches").element("cache-documents-max-size-bytes")
      assert(docsCacheEl.element("mode").getText == "size")
      assert(docsCacheEl.element("quantity").getText == "256")
      assert(docsCacheEl.element("quantity").attributeValue("unit") == "MB")
      assert(docsCacheEl.attributeValue("value") == "256000000")
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
            val loadedResourceLangs =
              (instance("fr-form-resources").get.rootElement / "resource").map(_.attValue("*:lang")).toSet
            assert(loadedResourceLangs.size == 20)

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

            // Set the value control to "fr" via client event (exercises <fr:language-choice> single-select)
            setControlValueWithEventSearchNested(valueControl.effectiveId, "fr")

            // Test <fr:language-choice multiple="true" allow-wildcard="true"> on available-languages-value-control
            val availLangOverride       = resolveObject[XFormsSingleNodeControl]("available-languages-override-control").get
            val availLangOverrideSelect = ControlsIterator(availLangOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val availLangValue          = resolveObject[XFormsValueControl]("available-languages-value-control").get

            assert(availLangValue.getValue(EventCollector.Throw) == "*")
            setControlValueWithEventSearchNested(availLangOverrideSelect.effectiveId, encodedTrue)
            // Switch mode radio inside fr:language-choice from '*' ('all') to 'specific', then select multiple languages
            val availModeSelect = ControlsIterator(availLangValue, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val specificItem    = availModeSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("")).get._1
            setControlValueWithEventSearchNested(availModeSelect.effectiveId, specificItem.externalValue(availModeSelect.mustEncodeValues))
            val availMultiSelect = ControlsIterator(availLangValue, includeSelf = false).collectFirst { case c: XFormsSelectControl => c }.get
            setControlValueWithEventSearchNested(availMultiSelect.effectiveId, "en fr es")
            assert(availLangValue.getValue(EventCollector.Throw) == "en fr es")

            // Test <fr:language-choice multiple="true"> on global-available-languages-value-control
            val globalAvailOverride       = resolveObject[XFormsSingleNodeControl]("global-available-languages-override-control").get
            val globalAvailOverrideSelect = ControlsIterator(globalAvailOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val globalAvailValue          = resolveObject[XFormsValueControl]("global-available-languages-value-control").get

            setControlValueWithEventSearchNested(globalAvailOverrideSelect.effectiveId, encodedTrue)
            val globalAvailMultiSelect = ControlsIterator(globalAvailValue, includeSelf = false).collectFirst { case c: XFormsSelectControl => c }.get
            setControlValueWithEventSearchNested(globalAvailMultiSelect.effectiveId, "en fr de")
            assert(globalAvailValue.getValue(EventCollector.Throw) == "en fr de")

            // Test <fr:percentage> on pdf-jpeg-compression-value-control (0.9 <-> 90%, unit-interval validation)
            def numberExternalValue(editValue: String): String =
              s"""{"displayValue":"","editValue":"$editValue","decimalSeparator":"."}"""

            val jpegOverride       = resolveObject[XFormsSingleNodeControl]("pdf-jpeg-compression-override-control").get
            val jpegOverrideSelect = ControlsIterator(jpegOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val jpegValue          = resolveObject[XFormsValueControl]("pdf-jpeg-compression-value-control").get

            assert(jpegValue.getExternalValue(EventCollector.Throw) == """{"displayValue":"90","editValue":"90","decimalSeparator":"."}""")
            assert(jpegValue.getFormattedValue(EventCollector.Throw).contains("90 %"))
            setControlValueWithEventSearchNested(jpegOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(jpegValue.effectiveId, numberExternalValue("150"))
            assert(jpegValue.getValue(EventCollector.Throw) == "1.5")
            assert(! jpegValue.isValid)
            setControlValueWithEventSearchNested(jpegValue.effectiveId, numberExternalValue("85"))
            assert(jpegValue.getValue(EventCollector.Throw) == "0.85")
            assert(jpegValue.isValid)

            // Test <xf:select1> enum dropdown on home-table-link-to-value-control
            val homeLinksSelect = resolveObject[XFormsSelect1Control]("home-table-link-to-value-control").get
            val newSummaryItem  = homeLinksSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("new summary")).get._1
            setControlValueWithEventSearchNested(homeLinksSelect.effectiveId, newSummaryItem.externalValue(homeLinksSelect.mustEncodeValues))

            // Test <fr:code-mirror> + Circe JSON validation on home-remote-servers-value-control
            val remoteServersOverride       = resolveObject[XFormsSingleNodeControl]("home-remote-servers-override-control").get
            val remoteServersOverrideSelect = ControlsIterator(remoteServersOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val remoteServersValue          = resolveObject[XFormsValueControl]("home-remote-servers-value-control").get

            setControlValueWithEventSearchNested(remoteServersOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(remoteServersValue.effectiveId, "[{broken json")
            assert(! remoteServersValue.isValid)
            setControlValueWithEventSearchNested(remoteServersValue.effectiveId, """[{"label": "Staging", "url": "https://staging.example.org/fr"}]""")
            assert(remoteServersValue.isValid)

            // Test <fr:boolean-or-formula> on warn-when-data-unsafe-value-control
            val warnOverride       = resolveObject[XFormsSingleNodeControl]("warn-when-data-unsafe-override-control").get
            val warnOverrideSelect = ControlsIterator(warnOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val warnValue          = resolveObject[XFormsSingleNodeControl]("warn-when-data-unsafe-value-control").get

            setControlValueWithEventSearchNested(warnOverrideSelect.effectiveId, encodedTrue)
            val warnRadioSelect = ControlsIterator(warnValue, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val formulaItem     = warnRadioSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("")).get._1
            setControlValueWithEventSearchNested(warnRadioSelect.effectiveId, formulaItem.externalValue(warnRadioSelect.mustEncodeValues))
            // Empty formula when overridden must be invalid (required), and malformed XPath must be invalid
            assert(! warnValue.isValid)
            val warnTextarea = ControlsIterator(warnValue, includeSelf = false).collectFirst { case c: XFormsValueComponentControl if c.staticControl.commonBinding.bindingElemId.contains("fr-xpath-completion") && c.isRelevant => c }.get
            setControlValueWithEventSearchNested(warnTextarea.effectiveId, "if (")
            assert(! warnValue.isValid)
            setControlValueWithEventSearchNested(warnTextarea.effectiveId, "count(//*[xxf:non-blank()]) gt 0")
            assert(warnValue.isValid)

            // Test <fr:number> port validation on email-smtp-port-value-control
            val smtpPortOverride       = resolveObject[XFormsSingleNodeControl]("email-smtp-port-override-control").get
            val smtpPortOverrideSelect = ControlsIterator(smtpPortOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val smtpPortValue          = resolveObject[XFormsValueControl]("email-smtp-port-value-control").get

            setControlValueWithEventSearchNested(smtpPortOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(smtpPortValue.effectiveId, numberExternalValue("70000"))
            assert(! smtpPortValue.isValid)
            setControlValueWithEventSearchNested(smtpPortValue.effectiveId, numberExternalValue("587"))
            assert(smtpPortValue.isValid)

            // Test XPath 2.0 fr:xpath-completion editor & validation on pdf-filename-value-control and format-output-date-value-control
            val formatDateControl        = resolveObject[XFormsValueComponentControl]("format-output-date-value-control").get
            assert(formatDateControl.staticControl.commonBinding.bindingElemId.contains("fr-xpath-completion"))

            val pdfOverrideControl       = resolveObject[XFormsSingleNodeControl]("pdf-filename-override-control").get
            val pdfOverrideSelectControl = ControlsIterator(pdfOverrideControl, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val pdfValueControl          = resolveObject[XFormsValueComponentControl]("pdf-filename-value-control").get
            assert(pdfValueControl.staticControl.commonBinding.bindingElemId.contains("fr-xpath-completion"))

            assert(pdfValueControl.isValid)
            setControlValueWithEventSearchNested(pdfOverrideSelectControl.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(pdfValueControl.effectiveId, "concat('unclosed")
            assert(! pdfValueControl.isValid)
            setControlValueWithEventSearchNested(pdfValueControl.effectiveId, "concat(fr:form-title(), '.pdf')")
            assert(pdfValueControl.isValid)

            // Test XPath 2.0 Value Template (AVT) fr:xpath-completion editor & validation on auth-user-menu-uri-login-value-control
            val loginOverrideControl       = resolveObject[XFormsSingleNodeControl]("auth-user-menu-uri-login-override-control").get
            val loginOverrideSelectControl = ControlsIterator(loginOverrideControl, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val loginValueControl          = resolveObject[XFormsValueComponentControl]("auth-user-menu-uri-login-value-control").get
            assert(loginValueControl.staticControl.commonBinding.bindingElemId.contains("fr-xpath-completion"))

            assert(loginValueControl.isValid)
            setControlValueWithEventSearchNested(loginOverrideSelectControl.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(loginValueControl.effectiveId, "/fr/auth?source={if (}")
            assert(! loginValueControl.isValid)
            setControlValueWithEventSearchNested(loginValueControl.effectiveId, "/fr/auth?source={xxf:get-request-path()}")
            assert(loginValueControl.isValid)

            // Test curated cache byte-budget (<fr:percentage> + <fr:quantity>), max-idle, and entry-count validation & overrides
            val staticBytesOverride       = resolveObject[XFormsSingleNodeControl]("cache-static-state-max-size-bytes-override-control").get
            val staticBytesOverrideSelect = ControlsIterator(staticBytesOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val staticBytesModeSelect     = resolveObject[XFormsSelect1Control]("cache-static-state-max-size-bytes-mode-control").get
            val staticBytesPercent        = resolveObject[XFormsSingleNodeControl]("cache-static-state-max-size-bytes-percent-control").get
            val staticBytesQuantity       = resolveObject[XFormsSingleNodeControl]("cache-static-state-max-size-bytes-value-control").get

            setControlValueWithEventSearchNested(staticBytesOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(staticBytesPercent.effectiveId, numberExternalValue("150"))
            assert(! staticBytesPercent.isValid)
            setControlValueWithEventSearchNested(staticBytesPercent.effectiveId, numberExternalValue("20"))
            assert(staticBytesPercent.isValid)
            val fixedSizeItem = staticBytesModeSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("size")).get._1
            setControlValueWithEventSearchNested(staticBytesModeSelect.effectiveId, fixedSizeItem.externalValue(staticBytesModeSelect.mustEncodeValues))
            assert(staticBytesQuantity.isRelevant)
            setControlValueWithEventSearchNested(staticBytesQuantity.effectiveId, numberExternalValue("256"))
            assert(staticBytesQuantity.isValid)

            // Test <fr:quantity unit-preset="data"> on attachment-max-size-per-file and attachment-max-size-aggregate-per-control
            val attachPerFileOverride       = resolveObject[XFormsSingleNodeControl]("attachment-max-size-per-file-override-control").get
            val attachPerFileOverrideSelect = ControlsIterator(attachPerFileOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val attachPerFileQuantity       = resolveObject[XFormsSingleNodeControl]("attachment-max-size-per-file-value-control").get

            setControlValueWithEventSearchNested(attachPerFileOverrideSelect.effectiveId, encodedTrue)
            assert(attachPerFileQuantity.isRelevant)
            assert(! attachPerFileQuantity.isValid) // Required when mode = 'size'
            setControlValueWithEventSearchNested(attachPerFileQuantity.effectiveId, numberExternalValue("10"))
            assert(attachPerFileQuantity.isValid)

            val attachPerCtrlOverride       = resolveObject[XFormsSingleNodeControl]("attachment-max-size-aggregate-per-control-override-control").get
            val attachPerCtrlOverrideSelect = ControlsIterator(attachPerCtrlOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val attachPerCtrlQuantity       = resolveObject[XFormsSingleNodeControl]("attachment-max-size-aggregate-per-control-value-control").get

            setControlValueWithEventSearchNested(attachPerCtrlOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(attachPerCtrlQuantity.effectiveId, numberExternalValue("500"))
            val attachPerCtrlUnitSelect = ControlsIterator(attachPerCtrlQuantity, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            setControlValueWithEventSearchNested(attachPerCtrlUnitSelect.effectiveId, "KB")
            assert(attachPerCtrlQuantity.isValid)

            val staticIdleOverride       = resolveObject[XFormsSingleNodeControl]("cache-static-state-max-idle-override-control").get
            val staticIdleOverrideSelect = ControlsIterator(staticIdleOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val staticIdleValue          = resolveObject[XFormsSingleNodeControl]("cache-static-state-max-idle-value-control").get

            setControlValueWithEventSearchNested(staticIdleOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(staticIdleValue.effectiveId, "invalid-duration")
            assert(! staticIdleValue.isValid)
            setControlValueWithEventSearchNested(staticIdleValue.effectiveId, "45m")
            assert(staticIdleValue.isValid)

            val docsSizeOverride       = resolveObject[XFormsSingleNodeControl]("cache-documents-size-override-control").get
            val docsSizeOverrideSelect = ControlsIterator(docsSizeOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val docsSizeValue          = resolveObject[XFormsSingleNodeControl]("cache-documents-size-value-control").get

            setControlValueWithEventSearchNested(docsSizeOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(docsSizeValue.effectiveId, numberExternalValue("-5"))
            assert(! docsSizeValue.isValid)
            setControlValueWithEventSearchNested(docsSizeValue.effectiveId, numberExternalValue("25"))
            assert(docsSizeValue.isValid)

            // Test <fr:landing-cards-editor> visual configurator: enable override, reorder via DnD, remove card, add custom card
            val landingCardsOverride       = resolveObject[XFormsSingleNodeControl]("landing-cards-override-control").get
            val landingCardsOverrideSelect = ControlsIterator(landingCardsOverride, includeSelf = false).collectFirst { case c: XFormsSelect1Control => c }.get
            val landingCardsEditor         = resolveObject[org.orbeon.oxf.xforms.control.XFormsComponentControl]("landing-cards-value-control").get

            assert(landingCardsEditor.isInstanceOf[XFormsValueControl])
            assert(landingCardsEditor.asInstanceOf[XFormsSingleNodeControl].isReadonly)

            setControlValueWithEventSearchNested(landingCardsOverrideSelect.effectiveId, encodedTrue)
            assert(! landingCardsEditor.asInstanceOf[XFormsSingleNodeControl].isReadonly)

            // Reorder card 2 (Admin) to position 1 via XXFormsDndEvent on the inner dnd-repeat
            val dndRepeatControl =
              ControlsIterator(landingCardsEditor, includeSelf = false)
                .collectFirst { case r: org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl if r.effectiveId.contains("cards-dnd-repeat") => r }
                .get
            withAction {
              org.orbeon.oxf.xforms.event.Dispatch.dispatchEvent(
                new org.orbeon.oxf.xforms.event.events.XXFormsDndEvent(
                  dndRepeatControl,
                  Map("dnd-start" -> Some("2"), "dnd-end" -> Some("1"))
                ),
                EventCollector.Throw
              )
            }

            val reorderedJson  = landingCardsEditor.asInstanceOf[XFormsValueControl].getValue(EventCollector.Throw)
            val reorderedCards = LandingCardsJSON.parseString(reorderedJson).get.get._1
            assert(reorderedCards.head == LandingCard.QuickLinks(
              title = Some(LocalizedString.ByResource("landing.titles.admin")),
              links = List(QuickLinkToken.Admin)
            ))

            // Also verify Move Down / Move Up buttons and Add / Remove card triggers inside <fr:landing-cards-editor>
            def findCardTrigger(subId: String, iterationIndex: Int): org.orbeon.oxf.xforms.control.XFormsControl =
              ControlsIterator(landingCardsEditor, includeSelf = false)
                .find(c => c.effectiveId.contains(s"$subId⊙$iterationIndex"))
                .get

            // Move card 1 (Admin) down to position 2 -> Quick Links returns to position 1
            activateControlWithEvent(findCardTrigger("fr-lce-move-down-btn", 1).effectiveId)(ec)
            val afterMoveDownCards = LandingCardsJSON.parseString(landingCardsEditor.asInstanceOf[XFormsValueControl].getValue(EventCollector.Throw)).get.get._1
            assert(afterMoveDownCards.head == LandingCard.QuickLinks(links = List(QuickLinkToken.Main)))

            // Move card 2 (Admin) back up to position 1 -> Admin returns to position 1
            activateControlWithEvent(findCardTrigger("fr-lce-move-up-btn", 2).effectiveId)(ec)
            val afterMoveUpCards = LandingCardsJSON.parseString(landingCardsEditor.asInstanceOf[XFormsValueControl].getValue(EventCollector.Throw)).get.get._1
            assert(afterMoveUpCards.head == LandingCard.QuickLinks(
              title = Some(LocalizedString.ByResource("landing.titles.admin")),
              links = List(QuickLinkToken.Admin)
            ))

            // Add a new Published Forms card via toolbar button ( requires setting title to be valid ), then remove it
            val addPfBtn = ControlsIterator(landingCardsEditor, includeSelf = false)
              .find(_.effectiveId.contains("fr-lce-add-published-forms")).get
            activateControlWithEvent(addPfBtn.effectiveId)(ec)
            val countAfterAdd = afterMoveUpCards.size + 1
            assert(! landingCardsEditor.asInstanceOf[XFormsSingleNodeControl].isValid)
            activateControlWithEvent(findCardTrigger("fr-lce-remove-btn", countAfterAdd).effectiveId)(ec)
            assert(landingCardsEditor.asInstanceOf[XFormsSingleNodeControl].isValid)

            // Add a new Application / Form override (acme / order) via the Add Override modal dialog
            activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("add-override-button").get.effectiveId)(ec)
            val levelSelect  = resolveObject[XFormsSelect1Control]("scope-dialog-level-control").get
            val appFormItem  = levelSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("app-form")).get._1
            setControlValueWithEventSearchNested(levelSelect.effectiveId, appFormItem.externalValue(levelSelect.mustEncodeValues))

            val dlgAppControl =
              resolveObject[XFormsValueControl]("scope-dialog-app-select").filter(_.isRelevant)
                .orElse(resolveObject[XFormsValueControl]("scope-dialog-app-input").filter(_.isRelevant))
                .get
            setControlValueWithEventSearchNested(dlgAppControl.effectiveId, "acme")

            val dlgFormControl =
              resolveObject[XFormsValueControl]("scope-dialog-form-select").filter(_.isRelevant)
                .orElse(resolveObject[XFormsValueControl]("scope-dialog-form-input").filter(_.isRelevant))
                .get
            setControlValueWithEventSearchNested(dlgFormControl.effectiveId, "order")

            activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("confirm-scope-dialog-button").get.effectiveId)(ec)

            val configInstance = instance("fr-configuration-instance").get
            def configRoot     = configInstance.rootElement
            val uiInstance     = instance("fr-configuration-ui-instance").get

            // Parent App row (acme / *) must be auto-created before (acme / order), and (acme / order) must be selected
            val overrideNodes = (configRoot / "app-form-overrides" / "override").toList
            assert(overrideNodes.size == 2)
            assert((overrideNodes.head /@ "app").stringValue == "acme" && (overrideNodes.head /@ "form").stringValue == "*")
            assert((overrideNodes(1) /@ "app").stringValue == "acme" && (overrideNodes(1) /@ "form").stringValue == "order")
            assert((uiInstance.rootElement / "selected-scope").stringValue == (overrideNodes(1) /@ "id").stringValue)

            // Verify duplicate detection when editing a profiled app scope onto an existing (app, profiles) combination
            def setDialogAppValue(appName: String): Unit =
              resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("scope-dialog-app-select").filter(_.isRelevant) match {
                case Some(openSelect) =>
                  val sel1 = ControlsIterator(openSelect, includeSelf = false).collectFirst { case s: XFormsSelect1Control if s.getId == "select1" && s.isRelevant => s }.get
                  sel1.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left(appName)) match {
                    case Some((item, _)) =>
                      setControlValueWithEventSearchNested(sel1.effectiveId, item.externalValue(sel1.mustEncodeValues))
                    case None =>
                      val otherItem = sel1.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).toList.last._1
                      setControlValueWithEventSearchNested(sel1.effectiveId, otherItem.externalValue(sel1.mustEncodeValues))
                      val otherInput = ControlsIterator(openSelect, includeSelf = false).collectFirst { case vc: XFormsValueControl if vc.getId == "input" && vc.isRelevant => vc }.get
                      setControlValueWithEventSearchNested(otherInput.effectiveId, appName)
                  }
                case None =>
                  val plainInput = resolveObject[XFormsValueControl]("scope-dialog-app-input").get
                  setControlValueWithEventSearchNested(plainInput.effectiveId, appName)
              }

            def openAddAndCreateAppProfile(appName: String, profilesValue: String): Unit = {
              activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("add-override-button").get.effectiveId)(ec)
              val lvlSel  = resolveObject[XFormsSelect1Control]("scope-dialog-level-control").get
              val appItem = lvlSel.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("app")).get._1
              setControlValueWithEventSearchNested(lvlSel.effectiveId, appItem.externalValue(lvlSel.mustEncodeValues))
              setDialogAppValue(appName)
              val profCtrl     = resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("scope-dialog-profiles-control").get
              val closedSelect = ControlsIterator(profCtrl, includeSelf = false).collectFirst { case s: XFormsSelectControl if s.getId == "select" && s.isRelevant => s }.get
              val closedItems  = closedSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).toList
              val desiredProfs = profilesValue.split(" ").toList.filter(_.nonEmpty)
              val matchedItems = desiredProfs.flatMap(p => closedItems.find(_._2 == Left(p)).map(_._1))
              if (matchedItems.size == desiredProfs.size && matchedItems.nonEmpty) {
                val encodedSelected = matchedItems.map(_.externalValue(closedSelect.mustEncodeValues)).mkString(" ")
                setControlValueWithEventSearchNested(closedSelect.effectiveId, encodedSelected)
              } else {
                val openTokensCtrl =
                  ControlsIterator(profCtrl, includeSelf = false)
                    .collectFirst { case c: org.orbeon.oxf.xforms.control.XFormsComponentControl if c.getId == "open-tokens-input" => c }
                    .get
                setControlValueWithEventSearchNested(openTokensCtrl.effectiveId, profilesValue)
              }
              activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("confirm-scope-dialog-button").get.effectiveId)(ec)
            }

            openAddAndCreateAppProfile("orbeon", "internal admin")
            openAddAndCreateAppProfile("acme", "admin internal")

            val editTriggers =
              ControlsIterator(doc.controls.getCurrentControlTree.rootOpt.get, includeSelf = false)
                .filter(c => c.getId == "edit-override-scope-button" && c.isRelevant)
                .toList
            assert(editTriggers.size == 5)
            // Scope order now: acme (0), acme [admin internal] (1), acme / order (2), orbeon (3), orbeon [internal admin] (4)
            activateControlWithEvent(editTriggers(1).effectiveId)(ec)
            val scopeDialog = resolveObject[org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl]("fr-config-scope-dialog").get
            assert(scopeDialog.isDialogVisible)

            // Changing acme [admin internal] -> orbeon must be blocked as a duplicate of orbeon [internal admin]
            setDialogAppValue("orbeon")
            activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("confirm-scope-dialog-button").get.effectiveId)(ec)
            assert(scopeDialog.isDialogVisible)
            activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("cancel-scope-dialog-button").get.effectiveId)(ec)
            assert(! scopeDialog.isDialogVisible)

            // Clean up temporary profiled scopes and re-select acme / order
            val removeTriggers =
              ControlsIterator(doc.controls.getCurrentControlTree.rootOpt.get, includeSelf = false)
                .filter(c => c.getId == "remove-override-scope-button" && c.isRelevant)
                .toList
            // Remove orbeon [internal admin], then orbeon, then acme [admin internal]
            activateControlWithEvent(removeTriggers.last.effectiveId)(ec)
            val removeTriggers2 =
              ControlsIterator(doc.controls.getCurrentControlTree.rootOpt.get, includeSelf = false)
                .filter(c => c.getId == "remove-override-scope-button" && c.isRelevant)
                .toList
            activateControlWithEvent(removeTriggers2.last.effectiveId)(ec)
            val removeTriggers3 =
              ControlsIterator(doc.controls.getCurrentControlTree.rootOpt.get, includeSelf = false)
                .filter(c => c.getId == "remove-override-scope-button" && c.isRelevant)
                .toList
            activateControlWithEvent(removeTriggers3.head.effectiveId)(ec)

            val selectAcmeOrder =
              ControlsIterator(doc.controls.getCurrentControlTree.rootOpt.get, includeSelf = false)
                .filter(c => c.getId == "select-override-scope-button" && c.isRelevant)
                .toList(1)
            activateControlWithEvent(selectAcmeOrder.effectiveId)(ec)

            // Global-only controls must be non-relevant while an override scope is selected
            assert(! resolveObject[XFormsSingleNodeControl]("global-available-languages-value-control").get.isRelevant)
            assert(! resolveObject[XFormsSingleNodeControl]("cache-documents-size-value-control").get.isRelevant)

            // Override default-language to "de" and attachment-max-size-per-file back to Unlimited ("-1") on acme / order using the shared controls
            assert(valueControl.isReadonly)
            setControlValueWithEventSearchNested(overrideSelectControl.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(valueControl.effectiveId, "de")
            setControlValueWithEventSearchNested(attachPerFileOverrideSelect.effectiveId, encodedTrue)
            assert(! attachPerFileQuantity.isRelevant) // Mode stays 'unlimited' on override scope -> stores '-1'

            // Set an invalid XPath on pdf-filename on acme / order, then switch back to Global Settings:
            // unselected-scopes-validation-repeat must surface the error and prevent save-final from persisting invalid data
            setControlValueWithEventSearchNested(pdfOverrideSelectControl.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(pdfValueControl.effectiveId, "concat('unclosed")
            assert(! pdfValueControl.isValid)

            activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("select-global-scope-button").get.effectiveId)(ec)
            assert((uiInstance.rootElement / "selected-scope").stringValue == "__global__")
            assert(resolveObject[XFormsSingleNodeControl]("global-available-languages-value-control").get.isRelevant)
            assert(pdfValueControl.isValid) // Global's own pdf-filename is valid

            SimpleProcess.runProcessByName("oxf.fr.detail.process", "save-final")
            useAndClose(connection.prepareStatement("SELECT count(*) FROM orbeon_form_data WHERE app = 'orbeon' AND form = 'configuration' AND draft = 'N'")) { ps =>
              useAndClose(ps.executeQuery()) { rs =>
                assert(rs.next() && rs.getInt(1) == 0)
              }
            }

            // Select acme / order (iteration 2 of scope-tree-repeat) to fix the invalid XPath, and set smtp port on acme / * (iteration 1)
            val selectScopeTriggers =
              ControlsIterator(doc.controls.getCurrentControlTree.rootOpt.get, includeSelf = false)
                .filter(c => c.getId == "select-override-scope-button" && c.isRelevant)
                .toList
            assert(selectScopeTriggers.size == 2)

            activateControlWithEvent(selectScopeTriggers(1).effectiveId)(ec)
            assert(! pdfValueControl.isValid)
            setControlValueWithEventSearchNested(pdfValueControl.effectiveId, "concat('order-', fr:document-id())")
            assert(pdfValueControl.isValid)

            activateControlWithEvent(selectScopeTriggers.head.effectiveId)(ec)
            setControlValueWithEventSearchNested(smtpPortOverrideSelect.effectiveId, encodedTrue)
            setControlValueWithEventSearchNested(smtpPortValue.effectiveId, numberExternalValue("2525"))
            assert(smtpPortValue.isValid)

            // Switch back to Global Settings
            activateControlWithEvent(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("select-global-scope-button").get.effectiveId)(ec)

            // Verify default empty custom property row: present on load, valid and unbadged when blank,
            // invalid if a value is entered without a name, and omitted upon save when left blank
            val customPropsNodes    = configRoot / "custom-properties"
            val initialCustomProps  = (customPropsNodes / "property").toList
            assert(initialCustomProps.size == 1)
            assert((initialCustomProps.head /@ "name").stringValue == "")
            assert((initialCustomProps.head /@ "type").stringValue == "xs:string")
            assert(initialCustomProps.head.stringValue == "")

            def customTabLabelHtml: String =
              ControlsIterator(resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("fr-configuration-tabs").get, includeSelf = false)
                .collect { case c: XFormsValueControl => c.getValue(EventCollector.Throw) }
                .find(_.contains("Custom Properties"))
                .get

            assert(! customTabLabelHtml.contains("fr-config-tab-badge"))

            val customNameControl   = resolveObject[XFormsValueControl]("custom-property-name-control").get
            val customValueControl  = resolveObject[XFormsValueControl]("custom-property-value-control").get
            assert(customNameControl.isValid)

            setControlValueWithEventSearchNested(customValueControl.effectiveId, "orphan-value")
            assert(! customNameControl.isValid)
            assert(customTabLabelHtml.contains("fr-config-tab-badge"))

            setControlValueWithEventSearchNested(customValueControl.effectiveId, "")
            assert(customNameControl.isValid)
            assert(! customTabLabelHtml.contains("fr-config-tab-badge"))

            // Leave the default first row empty and add a second custom property
            val templatePropNodes = configRoot / "templates" / "property"
            val inserted          = XFormsAPI.insert(into = customPropsNodes, origin = templatePropNodes, after = customPropsNodes / "property")
            XFormsAPI.setvalue(inserted /@ "name", "oxf.test.custom.persistence.prop")
            XFormsAPI.setvalue(inserted /@ "type", "xs:integer")
            XFormsAPI.setvalue(inserted, "42")
            XFormsAPI.rebuild("fr-form-model")
            XFormsAPI.recalculate("fr-form-model")
            XFormsAPI.refresh("fr-form-model")
            assert((customPropsNodes / "property").size == 2)
            assert(customTabLabelHtml.contains(">1<"))

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
              assert(savedXml.contains("oxf.fr.default-language.acme.order"))
              assert(savedXml.contains("oxf.fr.email.smtp.port.acme.*"))
              assert(savedXml.contains("oxf.test.custom.persistence.prop"))
              assert(! savedXml.contains("""name="""""))
              assert(! savedXml.contains("data-format-version"))
            }
          }

          // 4. Verify that PropertyLoader loads the saved properties via PersistencePropertyProvider
          ProcessorService.withProcessorService(processorService) {
            PersistencePropertyProvider.invalidateCache()
            ec.getRequest.getAttributesMap.remove(PropertyLoader.RequestPropertiesAttributeName)

            val store = PropertyLoader.getPropertyStore(Option(ec.getRequest))
            assert(store.globalPropertySet.getString("oxf.fr.default-language.my-app.my-form").contains("fr"))
            assert(store.globalPropertySet.getString("oxf.fr.default-language.acme.order").contains("de"))
            assert(store.globalPropertySet.getString("oxf.fr.detail.pdf.filename.acme.order").contains("concat('order-', fr:document-id())"))
            assert(store.globalPropertySet.getString("oxf.fr.available-languages.my-app.my-form").contains("en fr es"))
            assert(store.globalPropertySet.getString("oxf.fr.available-languages").contains("en fr de"))
            assert(store.globalPropertySet.getString("oxf.fr.pdf.jpeg.compression").contains("0.85"))
            assert(store.globalPropertySet.getString("oxf.fr.home.table.link-to").contains("new summary"))
            assert(store.globalPropertySet.getString("oxf.fr.home.remote-servers").contains("staging.example.org"))
            assert(store.globalPropertySet.getString("oxf.fr.detail.warn-when-data-unsafe.my-app.my-form").contains("count(//*[xxf:non-blank()]) gt 0"))
            assert(store.globalPropertySet.getString("oxf.fr.email.smtp.port.my-app.my-form").contains("587"))
            assert(store.globalPropertySet.getString("oxf.fr.email.smtp.port.acme.order").contains("2525"))
            assert(store.globalPropertySet.getString("oxf.fr.detail.attachment.max-size-per-file.my-app.my-form").contains("10000000"))
            assert(store.globalPropertySet.getString("oxf.fr.detail.attachment.max-size-per-file.acme.order").contains("-1"))
            assert(store.globalPropertySet.getString("oxf.fr.detail.attachment.max-size-aggregate-per-control.my-app.my-form").contains("500000"))
            assert(store.globalPropertySet.getIntOpt("oxf.test.custom.persistence.prop").contains(42))
            assert(store.globalPropertySet.getString("oxf.xforms.cache.static-states.max-size-bytes").contains("256000000"))
            assert(store.globalPropertySet.getString("oxf.xforms.cache.static-states.max-idle").contains("45m"))
            assert(store.globalPropertySet.getIntOpt("oxf.xforms.cache.live-documents.size").contains(25))
            val savedLandingCards = LandingCardsJSON.parseString(store.globalPropertySet.getString("oxf.fr.landing.cards")).get.get._1
            assert(savedLandingCards.head == LandingCard.QuickLinks(
              title = Some(LocalizedString.ByResource("landing.titles.admin")),
              links = List(QuickLinkToken.Admin)
            ))

            // Verify live ObjectCache reconfiguration from reloaded PropertySet without restart
            val _ = (XFormsStaticStateCache.getCacheForDiagnostics, XFormsDocumentCache.getCacheForDiagnostics)
            ObjectCache.configureAllIfNeeded(store.globalPropertySet)
            assert(XFormsStaticStateCache.getCacheForDiagnostics.getMaxSizeBytes.contains(256_000_000L))
            assert(XFormsStaticStateCache.getCacheForDiagnostics.getMaxIdleMillis.contains(45L * 60_000L))
            assert(XFormsDocumentCache.getCacheForDiagnostics.getMaxSize == 25)
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

            val reloadedPerFileNode = (configRoot / "curated" / "detail" / "attachment-max-size-per-file").head
            assert((reloadedPerFileNode /@ "value").stringValue == "10000000")
            assert((reloadedPerFileNode / "quantity").stringValue == "10")
            assert((reloadedPerFileNode / "quantity" /@ "unit").stringValue == "MB")

            val reloadedPerCtrlNode = (configRoot / "curated" / "detail" / "attachment-max-size-aggregate-per-control").head
            assert((reloadedPerCtrlNode /@ "value").stringValue == "500000")
            assert((reloadedPerCtrlNode / "quantity").stringValue == "500")
            assert((reloadedPerCtrlNode / "quantity" /@ "unit").stringValue == "KB")

            val reloadedLandingCardsJson = resolveObject[XFormsValueControl]("landing-cards-value-control").get.getValue(EventCollector.Throw)
            assert(LandingCardsJSON.parseString(reloadedLandingCardsJson).get.get._1.head == LandingCard.QuickLinks(
              title = Some(LocalizedString.ByResource("landing.titles.admin")),
              links = List(QuickLinkToken.Admin)
            ))

            val reloadedOverrides = (configRoot / "app-form-overrides" / "override").toList
            assert(reloadedOverrides.size == 2)
            assert((reloadedOverrides.head /@ "app").stringValue == "acme" && (reloadedOverrides.head /@ "form").stringValue == "*")
            assert((reloadedOverrides(1) /@ "app").stringValue == "acme" && (reloadedOverrides(1) /@ "form").stringValue == "order")
            assert(((reloadedOverrides(1) / "curated" / "system" / "default-language").head /@ "value").stringValue == "de")

            val reloadedCustomProps = (configRoot / "custom-properties" / "property").toList
            assert(reloadedCustomProps.size == 1)
            val customPropNode = reloadedCustomProps.head
            assert((customPropNode /@ "name").stringValue == "oxf.test.custom.persistence.prop")
            assert((customPropNode /@ "type").stringValue == "xs:integer")
            assert(customPropNode.stringValue == "42")
          }
        }
      }
      PersistencePropertyProvider.invalidateCache()
      ObjectCache.configureAllIfNeeded(org.orbeon.oxf.properties.Properties.instance.getPropertySet)
    }
  }
}
