package org.orbeon.oxf.fr

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.scalatest.funspec.AnyFunSpecLike


class BaseOpsTest extends AnyFunSpecLike {

  describe("The `isNotLessSpecificThan` function") {

    val Expected = List(
      ("*.*",         "*.*",        true ),
      ("*.*",         "acme.*",     true ),
      ("*.*",         "*.order",    true ),
      ("*.*",         "acme.order", true ),
      ("acme.*",      "acme.*",     true ),
      ("acme.*",      "acme.order", true ),
      ("acme.order",  "acme.order", true ),
      ("acme.*",      "*.*",        false),
      ("*.order",     "*.*",        false),
      ("acme.*",      "*.order",    false),
      ("acme.order",  "acme.*",     false),
    )

    def parseAppForm(requestedName: String): AppForm =
      requestedName.splitTo[List](".") match {
        case List(app, form) => AppForm(app, form)
        case _               => throw new IllegalArgumentException
      }

    for ((superConfig, subConfig, expected) <- Expected) {
      it(s"must satisfy `$subConfig` ${if (expected) ">" else "<"} `$superConfig`") {
        assert(parseAppForm(subConfig).isNotLessSpecificThan(parseAppForm(superConfig)) == expected)
      }
    }
  }

  describe(" The `trailingAppFormFromProperty` function") {

    import org.orbeon.oxf.properties.Property

    def make(s: String) =
      Property(XS_STRING_QNAME, "dummy", Map.empty, s)

    val Expected = List(
      ("oxf.fr.detail.pdf.header-footer", make("oxf.fr.detail.pdf.header-footer.*.*"),        Some(AppForm("*",    "*"))),
      ("oxf.fr.detail.pdf.header-footer", make("oxf.fr.detail.pdf.header-footer.acme.order"), Some(AppForm("acme", "order"))),
      ("oxf.fr.detail.pdf.header-footer", make("oxf.fr.detail.pdf.header-footer.*"),          None),
      ("oxf.fr.detail.pdf.header-footer", make("oxf.fr.detail.*.header-footer.*.*"),          None),
    )

    for ((requestedName, property, expected) <- Expected) {
      it(s"must satisfy `$requestedName`/`${property.name}`") {
        expected match {
          case Some(appName) => assert(FormRunner.trailingAppFormFromProperty(requestedName, property) == appName)
          case None          => assertThrows[IllegalArgumentException](FormRunner.trailingAppFormFromProperty(requestedName, property))
        }
      }
    }
  }

  describe("The `AppForm.specificity` method") {
    it("must return the expected specificity rankings") {
      assert(AppForm("acme", "order").specificity == 4)
      assert(AppForm("acme", "*").specificity == 3)
      assert(AppForm("*", "order").specificity == 2)
      assert(AppForm("*", "*").specificity == 1)
    }
  }

  describe("The `Wizard.normalizeTocPosition` function") {
    import org.orbeon.xbl.Wizard

    it("must accept valid toc-position values") {
      assert(Wizard.normalizeTocPosition("left", "") == "left")
      assert(Wizard.normalizeTocPosition("separate", "") == "separate")
      assert(Wizard.normalizeTocPosition("none", "") == "none")
    }

    it("must fallback to legacy separate-toc when toc-position is not valid") {
      assert(Wizard.normalizeTocPosition("", "true") == "separate")
      assert(Wizard.normalizeTocPosition("", "false") == "left")
      assert(Wizard.normalizeTocPosition("invalid", "true") == "separate")
      assert(Wizard.normalizeTocPosition("invalid", "false") == "left")
      assert(Wizard.normalizeTocPosition("", "") == "left")
    }
  }

  describe("The `resolveWizardTocPosition` function") {
    import org.orbeon.oxf.properties.PropertySet
    import org.orbeon.oxf.properties.PropertySet.PropertyParams
    import org.orbeon.oxf.xml.XMLConstants.{XS_BOOLEAN_QNAME, XS_STRING_QNAME}

    it("must prioritize metadata wizard-toc-position") {
      val ps = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.toc.position.*.*", XS_STRING_QNAME, "separate"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.separate-toc.*.*", XS_BOOLEAN_QNAME, "true")
      ))
      assert(FormRunner.resolveWizardTocPosition("acme", "order", Some("none"), Some("true"), None, ps) == "none")
      assert(FormRunner.resolveWizardTocPosition("acme", "order", Some("left"), Some("true"), None, ps) == "left")
      assert(FormRunner.resolveWizardTocPosition("acme", "order", Some("separate"), Some("false"), None, ps) == "separate")
    }

    it("must prioritize legacy metadata wizard-separate-toc when wizard-toc-position is absent") {
      val ps = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.toc.position.*.*", XS_STRING_QNAME, "none"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.separate-toc.*.*", XS_BOOLEAN_QNAME, "false")
      ))
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, Some("true"), None, ps) == "separate")
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, Some("false"), None, ps) == "left")
      assert(FormRunner.resolveWizardTocPosition("acme", "order", Some(""), Some("true"), None, ps) == "separate")
    }

    it("must correctly resolve property specificity between toc.position and separate-toc") {
      // Scenario from issue: separate-toc.*.* = true, toc.position.acme.order = none -> none
      val ps1 = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.separate-toc.*.*", XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.toc.position.acme.order", XS_STRING_QNAME, "none")
      ))
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, None, None, ps1) == "none")

      // Reverse specificity: separate-toc.acme.order = true, toc.position.*.* = none -> separate
      val ps2 = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.separate-toc.acme.order", XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.toc.position.*.*", XS_STRING_QNAME, "none")
      ))
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, None, None, ps2) == "separate")

      // Equal specificity tie-break: toc.position.*.* wins over separate-toc.*.*
      val ps3 = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.separate-toc.*.*", XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.toc.position.*.*", XS_STRING_QNAME, "none")
      ))
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, None, None, ps3) == "none")

      // Equal specificity tie-break at app level: toc.position.acme.* wins over separate-toc.acme.*
      val ps4 = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.separate-toc.acme.*", XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.toc.position.acme.*", XS_STRING_QNAME, "left")
      ))
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, None, None, ps4) == "left")

      // Empty string in toc.position.*.* is ignored, so separate-toc.*.* wins
      val ps5 = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.separate-toc.*.*", XS_BOOLEAN_QNAME, "true"),
        PropertyParams(Map.empty, "oxf.xforms.xbl.fr.wizard.toc.position.*.*", XS_STRING_QNAME, "")
      ))
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, None, None, ps5) == "separate")

      // When neither is set, fallback to "left"
      val psEmpty = PropertySet.forTests(Nil)
      assert(FormRunner.resolveWizardTocPosition("acme", "order", None, None, None, psEmpty) == "left")
    }
  }
}
