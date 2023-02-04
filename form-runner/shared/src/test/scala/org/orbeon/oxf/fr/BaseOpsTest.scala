package org.orbeon.oxf.fr

import org.orbeon.oxf.util.StringUtils._
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
}
