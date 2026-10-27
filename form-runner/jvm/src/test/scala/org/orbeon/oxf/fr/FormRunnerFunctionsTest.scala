/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import cats.syntax.option.*
import org.orbeon.dom
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerPersistence.*
import org.orbeon.oxf.fr.library.ErrorAlertSupport
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory, NetUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.XMLConstants.{XS_ANYURI_QNAME, XS_BOOLEAN_QNAME, XS_STRING_QNAME}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.xbl.ErrorSummary
import org.orbeon.xforms.XFormsId
import org.orbeon.xml.NamespaceMapping
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.jdk.CollectionConverters.*


class FormRunnerFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FormRunnerSupport
     with AnyFunSpecLike {

  private val Logger = LoggerFactory.createLogger(classOf[FormRunnerFunctionsTest])
  private implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  describe("Persistence headers") {

    val Expected = List(
      FormOrData.Form ->
        """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/Mexico_City</value></header><header><name>Orbeon-City-Name</name><value>Mexico City</value></header><header><name>Orbeon-Population</name><value>8851080</value></header></headers>""",
      FormOrData.Data ->
        """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/S%C3%A3o_Paulo</value></header><header><name>Orbeon-City-Name</name><value>São Paulo</value></header><header><name>Orbeon-Population</name><value>11244369</value></header></headers>"""
    )

    implicit val propertySet: PropertySet = CoreCrossPlatformSupport.properties

    for ((formOrData, expected) <- Expected)
      it(s"must get headers for ${formOrData.entryName}") {
        val headersAsXml = getPersistenceHeadersAsXML(AppForm("cities", "form1"), formOrData)
        assert(TransformerUtils.tinyTreeToString(headersAsXml) == expected)
      }
  }

  describe("Language") {

    val App  = "acme"
    val Form = "order"

    // Scope document as `findRequestedLang()` uses the in-scope `XFormsContainingDocument`, not the `ExternalContext`
    // https://github.com/orbeon/orbeon-forms/issues/6788
    def source: dom.Document =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
        <xh:head>
          <xf:model>
            <xf:instance>
              <_/>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body/>
      </xh:html>.toDocument

    it("`oxf.fr.default-language` not set so `en` is the default") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(source)) {
          assert("en" == getDefaultLang(getAppForm(App, Form)))
        }
      }
    }

    it("`oxf.fr.available-languages` not set so all languages are allowed") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(source)) {
          assert(isAllowedLang(getAppForm(App, Form))("en"))
          assert(isAllowedLang(getAppForm(App, Form))("foo"))
        }
      }
    }

    describe("Requested language") {
      for (v <- List(null, "   "))
        it (s"must default to `en` for `$v` requested language") {
          withTestExternalContext { _ =>
            withActionAndDoc(setupDocument(source)) {
              assert(findRequestedLang(getAppForm(App, Form), v).contains("en"))
            }
          }
        }

      for (v <- List("es", "en"))
        it(s"must find requested language for `$v`") {
          withTestExternalContext { _ =>
            withActionAndDoc(setupDocument(source)) {
              assert(findRequestedLang(getAppForm(App, Form), v).contains(v))
            }
          }
        }

      it("must find language in session if present and not requested") {
        withTestExternalContext { _ =>
          withActionAndDoc(setupDocument(source)) {
            NetUtils.getExternalContext.getRequest.getSession(true).setAttribute("fr-language", "fr")
            assert(findRequestedLang(getAppForm(App, Form), null).contains("fr"))
          }
        }
      }

      it("must use requested language even if a language is present in session") {
        withTestExternalContext { _ =>
          withActionAndDoc(setupDocument(source)) {
            assert(findRequestedLang(getAppForm(App, Form), "it").contains("it"))
          }
        }
      }
    }

    describe("Language selector") {

      describe("Selection") {
        it(s"default language is put first`") {
            withTestExternalContext { _ =>
              assert(List("en", "fr", "it") == getFormLangSelection(App, Form, List("fr", "it", "en").asJava))
            }
        }

        it(s"other languages order is preserved") {
            withTestExternalContext { _ =>
              assert(List("fr", "it", "es") == getFormLangSelection(App, Form, List("fr", "it", "es").asJava))
            }
        }

        it(s"no language available") {
            withTestExternalContext { _ =>
              assert(Nil                    == getFormLangSelection(App, Form, Nil.asJava))
            }
        }
      }

      describe("Form language") {
        val Expected = List(
          ("it" , "it", List("fr", "it", "en")),
          ("en" , "zh", List("fr", "it", "en")),
          ("fr" , "zh", List("fr", "it", "es")),
          (null , "fr", Nil)
        )

        for ((expected, requested, available) <- Expected)
          it(s"must select language for `$requested` and available `$available`") {
            withTestExternalContext { _ =>
              assert(expected == selectFormLang(App, Form, requested, available.asJava))
            }
          }
      }

      describe("Form Runner language") {
        val Expected = List(
          ("it" , "it", List("fr", "it", "en")),
          ("en" , "zh", List("fr", "it", "en")),
          ("fr" , "zh", List("fr", "it", "es"))
        )

        for ((expected, requested, available) <- Expected)
          it(s"must select language for `$requested` and available `$available`") {
            withTestExternalContext { _ =>
              assert(expected == selectFormRunnerLang(App, Form, requested,available.asJava))
            }
          }
      }
    }
  }

  describe("Language issue #2688") { // https://github.com/orbeon/orbeon-forms/issues/2688

    val App  = "acme"
    val Form = "order"

    it("must select the correct language") {
      withTestExternalContext { _ =>
        for (lang <- List("zh-Hans", "zh-Hant"))
          assert(lang == selectFormRunnerLang(App, Form, lang, List("en", "zh-Hans", "zh-Hant", "es").asJava))

        assert("en" == selectFormRunnerLang(App, Form, "en_US", List("en", "zh-Hans", "zh-Hant", "es").asJava))
      }
    }
  }

  describe("Error Summary sorting") {

    def source: dom.Document =
        <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
          <xh:head>
            <xf:model>
              <xf:instance>
                <data>
                  <c1/>
                  <c2/>
                  <c3>
                    <c4/>
                    <c5/>
                    <c6>
                      <c7/>
                      <c8/>
                    </c6>
                    <c6>
                      <c7/>
                      <c8/>
                    </c6>
                    <c9/>
                    <c10/>
                  </c3>
                  <c3>
                    <c4/>
                    <c5/>
                    <c6>
                      <c7/>
                      <c8/>
                    </c6>
                    <c6>
                      <c7/>
                      <c8/>
                    </c6>
                    <c9/>
                    <c10/>
                  </c3>
                  <c11/>
                  <c12/>
                </data>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="c1" ref="c1"/>
            <xf:input id="c2" ref="c2"/>
            <xf:repeat id="c3" ref="c3">
              <xf:input id="c4" ref="c4"/>
              <xf:input id="c5" ref="c5"/>
              <xf:repeat id="c6" ref="c6">
                <xf:input id="c7" ref="c7"/>
                <xf:input id="c8" ref="c8"/>
              </xf:repeat>
              <xf:input id="c9" ref="c9"/>
              <xf:input id="c10" ref="c10"/>
            </xf:repeat>
            <xf:input id="c11" ref="c11"/>
            <xf:input id="c12" ref="c12"/>
          </xh:body>
        </xh:html>.toDocument

    it("must order the controls in document order") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(source)) {

          val doc = inScopeContainingDocument

          val controlIds     = 1 to 12 map ("c" +)
          val controlIndexes = controlIds map doc.staticOps.getControlPosition

          // Static control position follows source document order
          assert(controlIndexes.sorted == controlIndexes)

          val effectiveAbsoluteIds =
            doc.controls.getCurrentControlTree.effectiveIdsToControls map
            { case (id, _) => XFormsId.effectiveIdToAbsoluteId(id) } toList

          assert(effectiveAbsoluteIds.sortBy(ErrorSummary.controlSearchIndexes)(ErrorSummary.IntIteratorOrdering) == effectiveAbsoluteIds)
        }
      }
    }
  }

  describe("Analyze known constraint") {

    import FormRunnerCommonConstraint.analyzeKnownConstraint
    import org.orbeon.oxf.xml.XMLConstants.*
    import org.orbeon.xforms.XFormsNames.*

    val Library = XFormsFunctionLibrary

    val Mapping =
      NamespaceMapping(
        Map(
          XFORMS_PREFIX        -> XFORMS_NAMESPACE_URI,
          XFORMS_SHORT_PREFIX  -> XFORMS_NAMESPACE_URI,
          XXFORMS_PREFIX       -> XXFORMS_NAMESPACE_URI,
          XXFORMS_SHORT_PREFIX -> XXFORMS_NAMESPACE_URI,
          XSD_PREFIX           -> XSD_URI
        )
      )

    //val Logger  = new IndentedLogger(LoggerFactory.createLogger(classOf[FormBuilderFunctionsTest]), true)

    val data = List(
      (Some("max-length"                            -> Some("5"))                                             , "xxf:max-length(5)"),
      (Some("min-length"                            -> Some("5"))                                             , "xxf:min-length(5)"),
      (Some("min-length"                            -> Some("5"))                                             , "xxf:min-length('5')"),
      (Some("min-length"                            -> Some("5"))                                             , "(xxf:min-length(5))"),
      (Some("non-negative"                          -> None)                                                  , "(xxf:non-negative())"),
      (Some("negative"                              -> None)                                                  , "(xxf:negative())"),
      (Some("non-positive"                          -> None)                                                  , "(xxf:non-positive())"),
      (Some("positive"                              -> None)                                                  , "(xxf:positive())"),
      (Some("upload-max-size-per-file"              -> Some("3221225472"))                                    , "xxf:upload-max-size-per-file(3221225472)"),
      (Some("upload-max-size-aggregate-per-control" -> Some("3221225472"))                                    , "xxf:upload-max-size-aggregate-per-control(3221225472)"),
      (Some("upload-mediatypes"                     -> Some("image/jpeg application/pdf"))                    , "xxf:upload-mediatypes('image/jpeg application/pdf')"),
      (Some("min-length"                            -> Some("foo"))                                           , "xxf:min-length(foo)"),
      (Some("excluded-dates"                        -> Some("xs:date('2018-11-29')"))                         , "xxf:excluded-dates(xs:date('2018-11-29'))"),
      (Some("excluded-dates"                        -> Some("xs:date('2018-11-29')"))                         , "xxf:excluded-dates((xs:date('2018-11-29')))"),
      (Some("excluded-dates"                        -> Some("xs:date('2018-11-29'), xs:date('2018-12-02')"))  , "xxf:excluded-dates((xs:date('2018-11-29'), xs:date('2018-12-02')))"),
      (None                                                                              , "xxf:foobar(5)")
    )

    for ((expected, expr) <- data)
      it(s"must pass checking `$expr`") {
        assert(expected == analyzeKnownConstraint(expr, Mapping, Library))
      }
  }

  describe("`getProviders()` function") {

    implicit val propertySet: PropertySet =
      PropertySet.forTests(
        List(
          PropertyParams(Map.empty, "oxf.fr.persistence.*.active",                       XS_BOOLEAN_QNAME, "true"), // this is the default, not strictly needed here
          PropertyParams(Map.empty, "oxf.fr.persistence.my-inactive-provider.active",    XS_BOOLEAN_QNAME, "false"),

          PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*.attachments",     XS_STRING_QNAME,  "filesystem"),

          PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*",                 XS_STRING_QNAME,  "mysql"),
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.orbeon.bookshelf.form", XS_STRING_QNAME,  "sqlite"),
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.form",     XS_STRING_QNAME,  "postgresql"),
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),

          PropertyParams(Map.empty, "oxf.fr.persistence.provider.foo.*.*",               XS_STRING_QNAME,  "p1"),
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.foo.bar.*",             XS_STRING_QNAME,  "p2"),
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.foo.*.form",            XS_STRING_QNAME,  "p3"),

          PropertyParams(Map.empty, "oxf.fr.persistence.provider.baz.*.*",               XS_STRING_QNAME,  "my-inactive-provider"),
        )
      )

    val expected =
      List(

        ("orbeon".some,     None,             FormOrData.Form) -> Set("sqlite", "mysql"),
        ("orbeon".some,     "contact".some,   FormOrData.Form) -> Set("mysql"),
        ("orbeon".some,     "contact".some,   FormOrData.Data) -> Set("mysql"),

        ("orbeon".some,     "bookshelf".some, FormOrData.Form) -> Set("sqlite"),
        ("orbeon".some,     "bookshelf".some, FormOrData.Data) -> Set("mysql"),

        ("acme".some,       "sales".some,     FormOrData.Form) -> Set("mysql"),
        ("acme".some,       "sales".some,     FormOrData.Data) -> Set("mysql"),

        ("postgresql".some, "foo".some,       FormOrData.Form) -> Set("postgresql"),
        ("postgresql".some, "foo".some,       FormOrData.Data) -> Set("postgresql"),

        ("postgresql".some, None,             FormOrData.Form) -> Set("postgresql"),
        ("postgresql".some, None,             FormOrData.Data) -> Set("postgresql"),

        ("acme".some,       None,             FormOrData.Form) -> Set("mysql"),
        ("acme".some,       None,             FormOrData.Data) -> Set("mysql"),

        ("foo".some,        None,             FormOrData.Data) -> Set("p1", "p2"),
        ("foo".some,        "bar".some,       FormOrData.Data) -> Set("p2"),
        ("foo".some,        None,             FormOrData.Form) -> Set("p2", "p3"),
        ("foo".some,        "bar".some,       FormOrData.Form) -> Set("p2"),

        ("baz".some,        None,             FormOrData.Form) -> Set.empty,
        ("baz".some,        "qux".some,       FormOrData.Form) -> Set.empty,
      )

    for (((appOpt, formOpt, formOrData), expected) <- expected)
      it(s"must return `$expected` for `$appOpt`/`$formOpt`/`$formOrData`") {
        assert(getProviders(appOpt, formOpt, formOrData).toSet == expected)
      }
  }

  describe("`databaseConfigurationPresent()` function") {

    val expected: List[(String, Boolean, String => Boolean, List[PropertyParams])] =
      List(
        (
          "no properties",
          true,
          _ => true,
          Nil
        ),
        (
          "single provider has datasource",
          true,
          Set("postgresql"),
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),
          )
        ),
        (
          "single provider doesn't have datasource",
          false,
          Set.empty,
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),
          )
        ),
        (
          "single provider has datasource and `filesystem` attachment is present",
          true,
          Set("postgresql"),
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*.attachments",     XS_STRING_QNAME,  "filesystem"),
          )
        ),
        (
          "multiple providers all have datasources and `filesystem` attachment is present",
          true,
          Set("postgresql", "mysql", "sqlite"),
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.mysql.*.*",             XS_STRING_QNAME,  "mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.mysql.uri",                      XS_ANYURI_QNAME,  "/fr/service/mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.mysql.datasource",               XS_STRING_QNAME,  "mysql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.sqlite.*.form",         XS_STRING_QNAME,  "sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.uri",                      XS_ANYURI_QNAME,  "/fr/service/sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.datasource",              XS_STRING_QNAME,  "sqlite"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*.attachments",     XS_STRING_QNAME,  "filesystem"),
          )
        ),
        (
          "multiple providers, one missing datasource, and `filesystem` attachment is present",
          false,
          Set("postgresql", "mysql"),
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.mysql.*.*",             XS_STRING_QNAME,  "mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.mysql.uri",                      XS_ANYURI_QNAME,  "/fr/service/mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.mysql.datasource",               XS_STRING_QNAME,  "mysql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.sqlite.*.form",         XS_STRING_QNAME,  "sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.uri",                      XS_ANYURI_QNAME,  "/fr/service/sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.datasource",              XS_STRING_QNAME,  "sqlite"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*.attachments",     XS_STRING_QNAME,  "filesystem"),
          )
        ),
        (
          "multiple providers, one missing datasource but inactive, and `filesystem` attachment is present",
          true,
          Set("postgresql", "mysql"),
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.mysql.*.*",             XS_STRING_QNAME,  "mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.mysql.datasource",               XS_STRING_QNAME,  "mysql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.sqlite.*.form",         XS_STRING_QNAME,  "sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.uri",                     XS_ANYURI_QNAME,  "/fr/service/sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.datasource",              XS_STRING_QNAME,  "sqlite"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*.attachments",     XS_STRING_QNAME,  "filesystem"),

            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.active",                  XS_BOOLEAN_QNAME, "false"),
          )
        ),
        (
          "multiple providers, one missing datasource but external, and `filesystem` attachment is present",
          true,
          Set("postgresql", "mysql"),
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.mysql.*.*",             XS_STRING_QNAME,  "mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.mysql.datasource",               XS_STRING_QNAME,  "mysql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.sqlite.*.form",         XS_STRING_QNAME,  "my-provider"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.uri",                     XS_ANYURI_QNAME,  "/fr/service/sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.datasource",              XS_STRING_QNAME,  "sqlite"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*.attachments",     XS_STRING_QNAME,  "filesystem"),
            PropertyParams(Map.empty, "oxf.fr.persistence.my-provider.uri",                XS_ANYURI_QNAME,  "https://example.com/orbeon/my-provider"),
          )
        ),
        (
          "multiple providers, one missing datasource and internal, and `filesystem` attachment is present",
          false,
          Set("postgresql", "mysql"),
          List(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.postgresql.*.*",        XS_STRING_QNAME,  "postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/postgresql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.datasource",          XS_STRING_QNAME,  "postgresql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.mysql.*.*",             XS_STRING_QNAME,  "mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.postgresql.uri",                 XS_ANYURI_QNAME,  "/fr/service/mysql"),
            PropertyParams(Map.empty, "oxf.fr.persistence.mysql.datasource",               XS_STRING_QNAME,  "mysql"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.sqlite.*.form",         XS_STRING_QNAME,  "my-provider"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.uri",                     XS_ANYURI_QNAME,  "/fr/service/sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.sqlite.datasource",              XS_STRING_QNAME,  "sqlite"),

            PropertyParams(Map.empty, "oxf.fr.persistence.provider.*.*.*.attachments",     XS_STRING_QNAME,  "filesystem"),
            PropertyParams(Map.empty, "oxf.fr.persistence.my-provider.uri",                XS_ANYURI_QNAME,  "/fr/service/my-provider"),
          )
        ),
      )

    for ((desc, expected, get, propertyParams) <- expected)
      it(s"must return `$expected` for `$desc`") {
        implicit val propertySet: PropertySet = PropertySet.forTests(propertyParams)
        assert(RelationalUtils.databaseConfigurationPresent(get) == expected)
      }
  }

  describe("List form data attachments") {

    val appName = "issue"
    val formName = "6530"
    val documentId = "ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783"

    import org.orbeon.saxon.om
    import org.orbeon.scaxon.NodeConversions.*
    import org.orbeon.scaxon.SimplePath.*

    val data: om.NodeInfo =
      <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
        <my-section>
            <_ filename="IMG_9847.jpg" mediatype="image/jpeg" size="124073">/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/045c3dfbe8b56144f996fc3f9c2e59e19735b3f8.bin</_>
            <multiple>
                <_ filename="IMG_9842.jpg" mediatype="image/jpeg" size="115511">/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/c8a7e3e9f23e9e3c0d27943c2eb8fcd382ac1711.bin</_>
                <_ filename="IMG_9844.jpg" mediatype="image/jpeg" size="122218">/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/6f6e4b83923bf72e0e4e1dba602746824ffc5ccd.bin</_>
            </multiple>
        </my-section>
    </form>

    val formDefinition: om.NodeInfo =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms"
             xmlns:xs="http://www.w3.org/2001/XMLSchema"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:ev="http://www.w3.org/2001/xml-events"
             xmlns:xi="http://www.w3.org/2001/XInclude"
             xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
             xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
             xmlns:map="http://www.w3.org/2005/xpath-functions/map"
             xmlns:array="http://www.w3.org/2005/xpath-functions/array"
             xmlns:math="http://www.w3.org/2005/xpath-functions/math"
             xmlns:exf="http://www.exforms.org/exf/1-0"
             xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
             xmlns:saxon="http://saxon.sf.net/"
             xmlns:sql="http://orbeon.org/oxf/xml/sql"
             xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
             xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
        <xh:head>
            <xh:title>List form data attachments API to return control name instead of _ #6530</xh:title>
            <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">

                <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all" xxf:index="id">
                    <form>
                        <my-section>
                            <my-grid>
                                <_ filename="" mediatype="" size=""/>
                                <multiple/>
                            </my-grid>
                        </my-section>
                    </form>
                </xf:instance>

                <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                    <xf:bind id="my-section-bind" name="my-section" ref="my-section">
                        <xf:bind id="my-grid-bind" ref="my-grid" name="my-grid">
                            <xf:bind id="_-bind" ref="_" name="_" type="xf:anyURI"/>
                            <xf:bind id="multiple-bind" ref="multiple" name="multiple"/>
                        </xf:bind>
                    </xf:bind>
                </xf:bind>

                <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
                    <metadata>
                        <application-name>issue</application-name>
                        <form-name>6530</form-name>
                        <title xml:lang="en">List form data attachments API to return control name instead of _ #6530</title>
                        <description xml:lang="en"/>
                        <migration version="2019.1.0">{{"migrations":[{{"containerPath":[{{"value":"my-section"}}],"newGridElem":{{"value":"my-grid"}},"afterElem":null,"content":[{{"value":"_"}},{{"value":"multiple"}}],"topLevel":true}}]}}</migration>
                    </metadata>
                </xf:instance>

                <xf:instance id="fr-form-attachments" xxf:exclude-result-prefixes="#all">
                    <attachments/>
                </xf:instance>

                <xf:instance xxf:readonly="true" id="fr-form-resources" xxf:exclude-result-prefixes="#all">
                    <resources>
                        <resource xml:lang="en">
                            <_>
                                <label/>
                                <hint/>
                            </_>
                            <multiple>
                                <label/>
                                <hint/>
                            </multiple>
                            <my-section/>
                            <my-grid/>
                        </resource>
                    </resources>
                </xf:instance>
            </xf:model>
        </xh:head>
        <xh:body>
            <fr:view>
                <fr:body>
                    <fr:section id="my-section-section" bind="my-section-bind">
                        <fr:grid id="my-grid-grid" bind="my-grid-bind">
                            <fr:c y="1" x="1" w="6">
                                <fr:attachment id="_-control"
                                               bind="_-bind"
                                               class="fr-attachment">
                                    <xf:label ref="$form-resources/_/label"/>
                                    <xf:hint ref="$form-resources/_/hint"/>
                                    <xf:alert ref="$fr-resources/detail/labels/alert"/>
                                </fr:attachment>
                            </fr:c>
                            <fr:c y="1" x="7" w="6">
                                <fr:attachment multiple="true"
                                               id="multiple-control"
                                               bind="multiple-bind"
                                               class="fr-attachment">
                                    <xf:label ref="$form-resources/multiple/label"/>
                                    <xf:hint ref="$form-resources/multiple/hint"/>
                                    <xf:alert ref="$fr-resources/detail/labels/alert"/>
                                </fr:attachment>
                            </fr:c>
                        </fr:grid>
                    </fr:section>
                </fr:body>
            </fr:view>
        </xh:body>
    </xh:html>

    it("must return the expected attachments") {

      val expected =
        List(
          (
            "attachment",
            List(
              "filename"  -> "IMG_9847.jpg",
              "mediatype" -> "image/jpeg",
              "size"      -> "124073",
              "name"      -> "_"
            ),
            "/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/045c3dfbe8b56144f996fc3f9c2e59e19735b3f8.bin"
          ),
          (
            "attachment",
            List(
              "filename"  -> "IMG_9842.jpg",
              "mediatype" -> "image/jpeg",
              "size"      -> "115511",
              "name"      -> "multiple"
            ),
            "/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/c8a7e3e9f23e9e3c0d27943c2eb8fcd382ac1711.bin"
          ),
          (
            "attachment",
            List(
              "filename"  -> "IMG_9844.jpg",
              "mediatype" -> "image/jpeg",
              "size"      -> "122218",
              "name"      -> "multiple"
            ),
            "/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/6f6e4b83923bf72e0e4e1dba602746824ffc5ccd.bin"
          )
        )

      val result =
        FormRunner.collectDataAttachmentNodesJava(
          app            = appName,
          form           = formName,
          formDefinition = formDefinition,
          data           = data,
          fromBasePath   = FormRunner.createFormDataBasePath(appName, formName, isDraft = false, documentId),
        )
        .asScala
        .map(n => (n.name, n /@ @* map (a => a.name -> a.stringValue), n.stringValue))

      assert(result == expected)
    }
  }

  describe("#6564: `xxf:get-session-attribute()` to support returning a Java object") {

    val (processorService, docOpt, _) =
      runFormRunner(
        "issue",
        "6564",
        "new",
        attributes = Map("my-attribute" -> MyTest("my-value")) // see `MyTest` case class below
      )

    // Before fix for #6544, this throws as the document initialization fails
    val doc = docOpt.get

    it("must have evaluated the value") {
      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          assert(resolveObject[XFormsValueControl]("control-1-control").get.getValue(EventCollector.Throw) == "my-value")
        }
      }
    }
  }

  describe("FormRunnerCompiler.isClientPropertyName") {

    it("must exclude server-only Form Runner properties and include client-eligible Form Runner properties") {
      val excluded = List(
        "oxf.crypto.password",
        "oxf.fb.toolbox.group.other.uri.*.*",
        "oxf.fr.authentication.method",
        "oxf.fr.access-token.validity",
        "oxf.fr.state-token.validity",
        "oxf.fr.field-encryption.reencrypt.chunk-size",
        "oxf.fr.email.smtp.host.*.*",
        "oxf.fr.s3.my-config.bucket",
        "oxf.fr.send-s3.my-config.overwrite",
        "oxf.fr.home.page-size",
        "oxf.fr.landing.cards",
        "oxf.fr.summary.page-size.*.*",
        "oxf.fr.import.allowed-values",
        "oxf.fr.export.filename",
        "oxf.fr.admin.all",
        "oxf.fr.forms.page-size",
        "oxf.fr.pdf.template.font.paths",
        "oxf.fr.detail.tiff.compression.type.*.*",
        "oxf.fr.detail.pdf.barcode.*.*",
        "oxf.fr.detail.pdf.filename.*.*",
        "oxf.fr.detail.button.keyboard-shortcut.toolbox.*",
        "oxf.fr.detail.button.keyboard-shortcut.summary.*",
        "oxf.fr.detail.send.success.headers.*.*",
        "oxf.fr.detail.send.error.headers.*.*",
        "oxf.fr.persistence.proxy.uri",
        "oxf.fr.persistence.service.log-wait",
        "oxf.fr.persistence.mysql.uri",
        "oxf.fr.persistence.mysql.datasource",
        "oxf.fr.persistence.filesystem.directory",
        "oxf.fr.persistence.filesystem.base-path",
        "oxf.fr.persistence.s3.s3-config",
        "oxf.fr.persistence.relational.reencrypt",
        "oxf.fr.persistence.relational.reindex",
        "oxf.fr.persistence.relational.max-batch-size",
        "oxf.fr.persistence.relational.create-flat-view",
        "oxf.fr.persistence.relational.flat-view",
        "oxf.fr.style.themes.*.*",
        "oxf.fr.detail.buttons.orbeon.builder",
        "oxf.xforms.xbl.fr.code-mirror.line-numbers.orbeon.builder"
      )
      for (name <- excluded)
        assert(! FormRunnerCompiler.isClientPropertyName(name), s"expected `$name` to be excluded")

      val included = List(
        "oxf.xforms.format.output.date",
        "oxf.fr.version",
        "oxf.fr.default-language.*.*",
        "oxf.fr.available-languages.*.*",
        "oxf.fr.detail.buttons.*.*",
        "oxf.fr.detail.process.save-final.*.*",
        "oxf.fr.detail.pdf.disable-if-invalid.*.*",
        "oxf.fr.detail.button.keyboard-shortcut.save-final.*",
        "oxf.fr.persistence.provider.*.*.*",
        "oxf.fr.persistence.mysql.active",
        "oxf.fr.persistence.mysql.autosave",
        "oxf.fr.persistence.mysql.lease",
        "oxf.fr.persistence.mysql.versioning",
        "oxf.fr.persistence.mysql.data-format-version",
        "oxf.fr.persistence.attachments.max-size",
        "oxf.fr.persistence.attachments.filename.*.*"
      )
      for (name <- included)
        assert(FormRunnerCompiler.isClientPropertyName(name), s"expected `$name` to be included")
    }

    it("must filter app/form-specific properties when appFormOpt is provided") {
      val appForm = Some(AppForm("my-app", "my-form"))

      val included = List(
        "oxf.fr.detail.buttons.*.*",
        "oxf.fr.detail.buttons.my-app.*",
        "oxf.fr.detail.buttons.*.my-form",
        "oxf.fr.detail.buttons.my-app.my-form",
        "oxf.fr.persistence.provider.*.*.*",
        "oxf.fr.persistence.provider.my-app.*.data",
        "oxf.fr.persistence.provider.my-app.my-form.data",
        "oxf.fr.resource.my-app.my-form.en.detail.buttons.save",
        "oxf.fr.resource.*.*.en.detail.buttons.save",
        "oxf.xforms.xbl.fr.currency.prefix.*.*",
        "oxf.xforms.xbl.fr.currency.prefix.my-app.my-form"
      )
      for (name <- included)
        assert(FormRunnerCompiler.isClientPropertyName(name, appForm), s"expected `$name` to be included for $appForm")

      val excluded = List(
        "oxf.fr.detail.buttons.other-app.*",
        "oxf.fr.detail.buttons.my-app.other-form",
        "oxf.fr.detail.buttons.other-app.my-form",
        "oxf.fr.detail.buttons.orbeon.builder",
        "oxf.fr.persistence.provider.other-app.*.data",
        "oxf.fr.persistence.provider.my-app.other-form.data",
        "oxf.fr.resource.other-app.my-form.en.detail.buttons.save",
        "oxf.xforms.xbl.fr.currency.prefix.other-app.my-form"
      )
      for (name <- excluded)
        assert(! FormRunnerCompiler.isClientPropertyName(name, appForm), s"expected `$name` to be excluded for $appForm")
    }
  }

  describe("Validation errors to CSV") {
    it("must generate valid CSV data URI with proper headers and escaping") {
      withTestExternalContext { _ =>
        val source: dom.Document =
          <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <xh:head>
              <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                <xf:instance id="fr-fr-resources">
                  <resources xml:lang="en">
                    <import>
                      <validation-details>
                        <row>Row</row>
                        <label>Label</label>
                        <name>Name</name>
                        <validation-type>Validation Type</validation-type>
                        <reason>Reason</reason>
                        <data-entered>Data Entered</data-entered>
                        <error>Error</error>
                        <warning>Warning</warning>
                        <info>Info</info>
                      </validation-details>
                    </import>
                  </resources>
                </xf:instance>
                <xf:instance id="invalid-rows-instance">
                  <invalid-rows>
                    <row index="2" excel-row="3">
                      <error id="first-name-control" name="first-name" label="First Name" level="error" alert="Value is required" value=""/>
                      <error id="comments-control" name="comments" label="Questions and Comments" level="warning" alert="Contains &quot;quotes&quot; and, comma" value="Line 1, Line 2"/>
                    </row>
                  </invalid-rows>
                </xf:instance>
                <xf:instance id="csv-result">
                  <result/>
                </xf:instance>
                <xf:bind ref="instance('csv-result')" calculate="fr:validation-errors-to-csv(instance('invalid-rows-instance'), 'excel-headings')"/>
              </xf:model>
            </xh:head>
            <xh:body/>
          </xh:html>.toDocument

        withActionAndDoc(setupDocument(source)) {
          val csvDataUri = inScopeContainingDocument.findInstance("csv-result").get.rootElement.stringValue
          assert(csvDataUri.startsWith("data:text/csv;charset=utf-8;base64,"))
          val base64Data = csvDataUri.stripPrefix("data:text/csv;charset=utf-8;base64,")
          val csvText = new String(java.util.Base64.getDecoder.decode(base64Data), java.nio.charset.StandardCharsets.UTF_8)
          assert(csvText.startsWith("\uFEFF"))
          val lines = csvText.stripPrefix("\uFEFF").split("\r\n")
          assert(lines(0) == "Row,Label,Name,Validation Type,Reason,Data Entered")
          assert(lines(1) == "3,First Name,first-name,Error,Value is required,")
          assert(lines(2) == "3,Questions and Comments,comments,Warning,\"Contains \"\"quotes\"\" and, comma\",\"Line 1, Line 2\"")
        }
      }
    }

    it("must split combined <ul><li> alerts into multiple CSV rows") {
      withTestExternalContext { _ =>
        val source: dom.Document =
          <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <xh:head>
              <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                <xf:instance id="fr-fr-resources">
                  <resources xml:lang="en">
                    <import>
                      <validation-details>
                        <row>Row</row>
                        <label>Label</label>
                        <name>Name</name>
                        <validation-type>Validation Type</validation-type>
                        <reason>Reason</reason>
                        <data-entered>Data Entered</data-entered>
                        <error>Error</error>
                        <warning>Warning</warning>
                        <info>Info</info>
                      </validation-details>
                    </import>
                  </resources>
                </xf:instance>
                <xf:instance id="invalid-rows-instance">
                  <invalid-rows>
                    <row index="1" excel-row="2">
                      <error id="code-control" name="code" label="Code" level="error" alert="&lt;ul>&lt;li>Must contain &quot;a&quot;&lt;/li>&lt;li>Must contain &quot;b&quot;&lt;/li>&lt;/ul>" value="xyz"/>
                    </row>
                  </invalid-rows>
                </xf:instance>
                <xf:instance id="csv-result">
                  <result/>
                </xf:instance>
                <xf:bind ref="instance('csv-result')" calculate="fr:validation-errors-to-csv(instance('invalid-rows-instance'), 'excel-headings')"/>
              </xf:model>
            </xh:head>
            <xh:body/>
          </xh:html>.toDocument

        withActionAndDoc(setupDocument(source)) {
          val csvDataUri = inScopeContainingDocument.findInstance("csv-result").get.rootElement.stringValue
          assert(csvDataUri.startsWith("data:text/csv;charset=utf-8;base64,"))
          val base64Data = csvDataUri.stripPrefix("data:text/csv;charset=utf-8;base64,")
          val csvText = new String(java.util.Base64.getDecoder.decode(base64Data), java.nio.charset.StandardCharsets.UTF_8)
          val lines = csvText.stripPrefix("\uFEFF").split("\r\n")
          assert(lines(0) == "Row,Label,Name,Validation Type,Reason,Data Entered")
          assert(lines(1) == "2,Code,code,Error,\"Must contain \"\"a\"\"\",xyz")
          assert(lines(2) == "2,Code,code,Error,\"Must contain \"\"b\"\"\",xyz")
        }
      }
    }
  }

  describe("ErrorAlertSupport.extractAlerts") {
    it("returns plain text alerts unchanged") {
      assert(ErrorAlertSupport.extractAlerts("Value is required") == List("Value is required"))
      assert(ErrorAlertSupport.extractAlerts("") == List(""))
    }

    it("extracts multiple alerts from <ul><li> HTML format") {
      val alert = """<ul><li>Must contain "a"</li><li>Must contain "b"</li></ul>"""
      assert(ErrorAlertSupport.extractAlerts(alert) == List("""Must contain "a"""", """Must contain "b""""))
    }

    it("unescapes XML/HTML entities in list items") {
      val alert = """<ul><li>Must contain &quot;quotes&quot; and, comma</li><li>Must be &lt; 10 &amp; &gt; 0</li></ul>"""
      assert(ErrorAlertSupport.extractAlerts(alert) == List("""Must contain "quotes" and, comma""", "Must be < 10 & > 0"))
    }

    it("handles whitespace and linebreaks around and inside <ul>/<li>") {
      val alert =
        """
          |<ul>
          |  <li>  First alert  </li>
          |  <li>  Second alert  </li>
          |</ul>
          |""".stripMargin
      assert(ErrorAlertSupport.extractAlerts(alert) == List("First alert", "Second alert"))
    }

    it("handles single item in <ul><li> format") {
      val alert = "<ul><li>Single error</li></ul>"
      assert(ErrorAlertSupport.extractAlerts(alert) == List("Single error"))
    }
  }

  describe("Import file hint") {
    it("must format hint with single and multiple supported file types in XForms") {
      withTestExternalContext { _ =>
        val source: dom.Document =
          <xh:html
              xmlns:xh="http://www.w3.org/1999/xhtml"
              xmlns:xf="http://www.w3.org/2002/xforms"
              xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
              xml:lang="en">
            <xh:head>
              <xf:model>
                <xf:instance id="fr-fr-resources">
                  <resources>
                    <resource xml:lang="en">
                      <import>
                        <file>
                          <hint>{{1,choice,1#Supported file type|1&lt;Supported file types}}: {{0}}</hint>
                          <excel-headings-format>Excel 2007 with named headings format (.xslx)</excel-headings-format>
                          <excel-named-ranges-format>Excel 2007 with named ranges format (.xslx)</excel-named-ranges-format>
                          <xml-form-structure-and-data-format>Orbeon XML export format (.xml)</xml-form-structure-and-data-format>
                        </file>
                      </import>
                    </resource>
                  </resources>
                </xf:instance>
              </xf:model>
            </xh:head>
            <xh:body>
              <xf:output id="single" value="
                let $allowed-formats := 'excel-headings',
                    $formats := (
                      xxf:r('import.file.excel-headings-format',               '|fr-fr-resources|')[$allowed-formats = 'excel-headings'],
                      xxf:r('import.file.excel-named-ranges-format',           '|fr-fr-resources|')[$allowed-formats = 'excel-named-ranges'],
                      xxf:r('import.file.xml-form-structure-and-data-format',  '|fr-fr-resources|')[$allowed-formats = 'xml-form-structure-and-data']
                    )
                return
                  if (exists($formats)) then
                    xxf:format-message(
                      xxf:r('import.file.hint', '|fr-fr-resources|'),
                      (
                        string-join($formats, ', '),
                        count($formats)
                      )
                    )
                  else
                    ()
              "/>
              <xf:output id="multiple" value="
                let $allowed-formats := ('excel-headings', 'xml-form-structure-and-data'),
                    $formats := (
                      xxf:r('import.file.excel-headings-format',               '|fr-fr-resources|')[$allowed-formats = 'excel-headings'],
                      xxf:r('import.file.excel-named-ranges-format',           '|fr-fr-resources|')[$allowed-formats = 'excel-named-ranges'],
                      xxf:r('import.file.xml-form-structure-and-data-format',  '|fr-fr-resources|')[$allowed-formats = 'xml-form-structure-and-data']
                    )
                return
                  if (exists($formats)) then
                    xxf:format-message(
                      xxf:r('import.file.hint', '|fr-fr-resources|'),
                      (
                        string-join($formats, ', '),
                        count($formats)
                      )
                    )
                  else
                    ()
              "/>
              <xf:output id="empty" value="
                let $allowed-formats := (),
                    $formats := (
                      xxf:r('import.file.excel-headings-format',               '|fr-fr-resources|')[$allowed-formats = 'excel-headings'],
                      xxf:r('import.file.excel-named-ranges-format',           '|fr-fr-resources|')[$allowed-formats = 'excel-named-ranges'],
                      xxf:r('import.file.xml-form-structure-and-data-format',  '|fr-fr-resources|')[$allowed-formats = 'xml-form-structure-and-data']
                    )
                return
                  if (exists($formats)) then
                    xxf:format-message(
                      xxf:r('import.file.hint', '|fr-fr-resources|'),
                      (
                        string-join($formats, ', '),
                        count($formats)
                      )
                    )
                  else
                    ()
              "/>
            </xh:body>
          </xh:html>.toDocument

        withActionAndDoc(setupDocument(source)) {
          assert(resolveObject[XFormsValueControl]("single").get.getValue(EventCollector.Throw) == "Supported file type: Excel 2007 with named headings format (.xslx)")
          assert(resolveObject[XFormsValueControl]("multiple").get.getValue(EventCollector.Throw) == "Supported file types: Excel 2007 with named headings format (.xslx), Orbeon XML export format (.xml)")
          assert(resolveObject[XFormsValueControl]("empty").get.getValue(EventCollector.Throw) == "")
        }
      }
    }

    it("must have valid hint format in all fr-resources-*.xml files") {
      val languages = List(
        "ar", "ca", "da", "de", "en", "es", "fi", "fr", "hu", "it",
        "ja", "nl", "no", "pl", "pt", "ru", "sv", "tr", "zh-Hans", "zh-Hant"
      )
      for (lang <- languages) {
        val resourcePath = s"/apps/fr/i18n/fr-resources-$lang.xml"
        val is = getClass.getResourceAsStream(resourcePath)
        assert(is ne null, s"Missing resource file $resourcePath")
        val doc =
          try
            TransformerUtils.readTinyTree(org.orbeon.oxf.util.XPath.GlobalConfiguration, is, resourcePath, false, false)
          finally
            is.close()
        val hintOpt = (doc.descendant("import") / "file" / "hint").headOption.map(_.stringValue)
        assert(hintOpt.isDefined, s"Missing import/file/hint in $resourcePath")
        val hintTemplate = hintOpt.get
        assert(hintTemplate.nonEmpty, s"Empty import/file/hint in $resourcePath")

        // Test formatting with 1 format
        val singleFormatted = new java.text.MessageFormat(hintTemplate).format(Array[AnyRef]("Format A", java.lang.Integer.valueOf(1)))
        assert(singleFormatted.contains("Format A"), s"Single format failed in $resourcePath: $singleFormatted")

        // Test formatting with 2 formats
        val multipleFormatted = new java.text.MessageFormat(hintTemplate).format(Array[AnyRef]("Format A, Format B", java.lang.Integer.valueOf(2)))
        assert(multipleFormatted.contains("Format A, Format B"), s"Multiple format failed in $resourcePath: $multipleFormatted")
      }
    }

    it("must have valid import section labels and completed-labels in all fr-resources-*.xml files") {
      val languages = List(
        "ar", "ca", "da", "de", "en", "es", "fi", "fr", "hu", "it",
        "ja", "nl", "no", "pl", "pt", "ru", "sv", "tr", "zh-Hans", "zh-Hant"
      )
      for (lang <- languages) {
        val resourcePath = s"/apps/fr/i18n/fr-resources-$lang.xml"
        val is = getClass.getResourceAsStream(resourcePath)
        assert(is ne null, s"Missing resource file $resourcePath")
        val doc =
          try
            TransformerUtils.readTinyTree(org.orbeon.oxf.util.XPath.GlobalConfiguration, is, resourcePath, false, false)
          finally
            is.close()

        val uploadLabel          = (doc.descendant("import") / "upload" / "label").headOption.map(_.stringValue)
        val uploadCompletedLabel = (doc.descendant("import") / "upload" / "completed-label").headOption.map(_.stringValue)
        val valLabel             = (doc.descendant("import") / "validation" / "label").headOption.map(_.stringValue)
        val valCompletedLabel    = (doc.descendant("import") / "validation" / "completed-label").headOption.map(_.stringValue)
        val importLabel          = (doc.descendant("import") / "import" / "label").headOption.map(_.stringValue)
        val importCompletedLabel = (doc.descendant("import") / "import" / "completed-label").headOption.map(_.stringValue)

        assert(uploadLabel.exists(_.nonEmpty),          s"Missing or empty import/upload/label in $resourcePath")
        assert(uploadCompletedLabel.exists(_.nonEmpty), s"Missing or empty import/upload/completed-label in $resourcePath")
        assert(valLabel.exists(_.nonEmpty),             s"Missing or empty import/validation/label in $resourcePath")
        assert(valCompletedLabel.exists(_.nonEmpty),    s"Missing or empty import/validation/completed-label in $resourcePath")
        assert(importLabel.exists(_.nonEmpty),          s"Missing or empty import/import/label in $resourcePath")
        assert(importCompletedLabel.exists(_.nonEmpty), s"Missing or empty import/import/completed-label in $resourcePath")

        if (lang == "en") {
          assert(uploadLabel.contains("Upload"))
          assert(uploadCompletedLabel.contains("Uploaded"))
          assert(valLabel.contains("Validate"))
          assert(valCompletedLabel.contains("Validated"))
          assert(importLabel.contains("Import"))
          assert(importCompletedLabel.contains("Imported"))
        }
      }
    }

    it("must dynamically update import section titles when steps complete") {
      withTestExternalContext { _ =>
        val source: dom.Document =
          <xh:html
              xmlns:xh="http://www.w3.org/1999/xhtml"
              xmlns:xf="http://www.w3.org/2002/xforms"
              xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
              xml:lang="en">
            <xh:head>
              <xf:model id="fr-form-model">
                <xf:instance id="fr-fr-resources">
                  <resources>
                    <resource xml:lang="en">
                      <import>
                        <upload>
                          <label>Upload</label>
                          <completed-label>Uploaded</completed-label>
                        </upload>
                        <validation>
                          <label>Validate</label>
                          <completed-label>Validated</completed-label>
                        </validation>
                        <import>
                          <label>Import</label>
                          <completed-label>Imported</completed-label>
                        </import>
                      </import>
                    </resource>
                  </resources>
                </xf:instance>

                <xf:instance id="fr-form-instance">
                  <wizard>
                    <upload>
                      <file/>
                    </upload>
                    <validate>
                      <validating>not-started</validating>
                      <stats>
                        <total>10</total>
                        <processed>0</processed>
                      </stats>
                    </validate>
                    <import>
                      <importing>not-started</importing>
                      <stats>
                        <total>10</total>
                        <processed>0</processed>
                      </stats>
                    </import>
                  </wizard>
                </xf:instance>

                <xf:var name="upload-completed"     context="instance('fr-form-instance')/upload/file"          value="xxf:non-blank(.)"/>
                <xf:var name="validation-done"      context="instance('fr-form-instance')/validate/validating" value=". = 'succeeded'"/>
                <xf:var name="validation-completed" context="instance('fr-form-instance')/validate/stats"      value="$validation-done and total = processed"/>
                <xf:var name="import-done"          context="instance('fr-form-instance')/import/importing"    value=". = 'succeeded'"/>
                <xf:var name="import-completed"     context="instance('fr-form-instance')/import/stats"        value="$import-done and total = processed"/>
              </xf:model>
            </xh:head>
            <xh:body>
              <xf:output id="upload-title"   value="xxf:r(if ($upload-completed) then 'import.upload.completed-label' else 'import.upload.label', '|fr-fr-resources|')"/>
              <xf:output id="validate-title" value="xxf:r(if ($validation-completed) then 'import.validation.completed-label' else 'import.validation.label', '|fr-fr-resources|')"/>
              <xf:output id="import-title"   value="xxf:r(if ($import-completed) then 'import.import.completed-label' else 'import.import.label', '|fr-fr-resources|')"/>

              <xf:input id="file-input" ref="instance('fr-form-instance')/upload/file"/>
              <xf:input id="validating-input" ref="instance('fr-form-instance')/validate/validating"/>
              <xf:input id="val-processed-input" ref="instance('fr-form-instance')/validate/stats/processed"/>
              <xf:input id="importing-input" ref="instance('fr-form-instance')/import/importing"/>
              <xf:input id="imp-processed-input" ref="instance('fr-form-instance')/import/stats/processed"/>
            </xh:body>
          </xh:html>.toDocument

        withActionAndDoc(setupDocument(source)) {
          // Initial state
          assert(resolveObject[XFormsValueControl]("upload-title").get.getValue(EventCollector.Throw) == "Upload")
          assert(resolveObject[XFormsValueControl]("validate-title").get.getValue(EventCollector.Throw) == "Validate")
          assert(resolveObject[XFormsValueControl]("import-title").get.getValue(EventCollector.Throw) == "Import")

          // Complete upload
          setControlValue("file-input", "file:/tmp/test.xlsx")

          assert(resolveObject[XFormsValueControl]("upload-title").get.getValue(EventCollector.Throw) == "Uploaded")
          assert(resolveObject[XFormsValueControl]("validate-title").get.getValue(EventCollector.Throw) == "Validate")
          assert(resolveObject[XFormsValueControl]("import-title").get.getValue(EventCollector.Throw) == "Import")

          // Complete validation
          setControlValue("validating-input", "succeeded")
          setControlValue("val-processed-input", "10")

          assert(resolveObject[XFormsValueControl]("upload-title").get.getValue(EventCollector.Throw) == "Uploaded")
          assert(resolveObject[XFormsValueControl]("validate-title").get.getValue(EventCollector.Throw) == "Validated")
          assert(resolveObject[XFormsValueControl]("import-title").get.getValue(EventCollector.Throw) == "Import")

          // Complete import
          setControlValue("importing-input", "succeeded")
          setControlValue("imp-processed-input", "10")

          assert(resolveObject[XFormsValueControl]("upload-title").get.getValue(EventCollector.Throw) == "Uploaded")
          assert(resolveObject[XFormsValueControl]("validate-title").get.getValue(EventCollector.Throw) == "Validated")
          assert(resolveObject[XFormsValueControl]("import-title").get.getValue(EventCollector.Throw) == "Imported")
        }
      }
    }
  }

  describe("Effective color scheme") {

    def formSource: dom.Document =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
               xmlns:xf="http://www.w3.org/2002/xforms"
               xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xh:head>
          <xf:model id="fr-form-model">
            <xf:instance id="fr-form-instance">
              <_/>
            </xf:instance>
            <xf:instance id="fr-form-metadata">
              <metadata>
                <application-name>acme</application-name>
                <form-name>order</form-name>
              </metadata>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body/>
      </xh:html>.toDocument

    it("must default to `light` when no property or cookie is set") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(formSource)) {
          assert(effectiveColorScheme() == "light")
        }
      }
    }

    it("must pick up scheme from URL parameter") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(formSource)) {
          val doc = inScopeContainingDocument
          val reqInfo = doc.getRequestInformation
          doc.setRequestInformation(reqInfo.copy(requestParameters = Map("fr-color-scheme" -> List("dark"))))
          assert(effectiveColorScheme() == "dark")
        }
      }
    }

    it("must pick up scheme from cookie header") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(formSource)) {
          val doc = inScopeContainingDocument
          val reqInfo = doc.getRequestInformation
          doc.setRequestInformation(reqInfo.copy(requestHeaders = Map("cookie" -> List("fr-color-scheme=system"))))
          assert(effectiveColorScheme() == "system")
        }
      }
    }

    it("must not pick up scheme from fr-color-scheme cookie header") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(formSource)) {
          val doc = inScopeContainingDocument
          val reqInfo = doc.getRequestInformation
          doc.setRequestInformation(reqInfo.copy(requestHeaders = Map("cookie" -> List("fr-color-scheme=dark"))))
          assert(effectiveColorScheme() == "light")
        }
      }
    }

    it("must prefer URL parameter over cookie") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(formSource)) {
          val doc = inScopeContainingDocument
          val reqInfo = doc.getRequestInformation
          doc.setRequestInformation(reqInfo.copy(
            requestParameters = Map("fr-color-scheme" -> List("light")),
            requestHeaders    = Map("cookie" -> List("fr-color-scheme=dark"))
          ))
          assert(effectiveColorScheme() == "light")
        }
      }
    }

    it("must pick up scheme from session") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(formSource)) {
          NetUtils.getExternalContext.getRequest.getSession(true).setAttribute("fr-color-scheme", "dark")
          assert(effectiveColorScheme() == "dark")
        }
      }
    }
  }
}

// Class for test above
case class MyTest(value: String)