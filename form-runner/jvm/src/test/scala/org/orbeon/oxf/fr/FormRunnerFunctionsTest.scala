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
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, NetUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.XMLConstants.{XS_ANYURI_QNAME, XS_BOOLEAN_QNAME, XS_STRING_QNAME}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.xbl.ErrorSummary
import org.orbeon.xforms.XFormsId
import org.orbeon.xml.NamespaceMapping
import org.scalatest.funspec.AnyFunSpecLike

import scala.jdk.CollectionConverters.*


class FormRunnerFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
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

    for ((formOrData, expected) <- Expected)
      it(s"must get headers for ${formOrData.entryName}") {
        val headersAsXml = getPersistenceHeadersAsXML(AppForm("cities", "form1"), formOrData)
        assert(TransformerUtils.tinyTreeToString(headersAsXml) == expected)
      }
  }

  describe("Language") {

    val App  = "acme"
    val Form = "order"

    it("`oxf.fr.default-language` not set so `en` is the default") {
      withTestExternalContext { _ =>
        assert("en" == getDefaultLang(getAppForm(App, Form)))
      }
    }

    it("`oxf.fr.available-languages` not set so all languages are allowed") {
      withTestExternalContext { _ =>
        assert(isAllowedLang(getAppForm(App, Form))("en"))
        assert(isAllowedLang(getAppForm(App, Form))("foo"))
      }
    }

    describe("Requested language") {
      for (v <- List(null, "   "))
        it (s"must default to `en` for `$v` requested language") {
          withTestExternalContext { _ =>
            assert(Some("en") == findRequestedLang(getAppForm(App, Form), v))
          }
        }

      for (v <- List("es", "en"))
        it(s"must find requested language for `$v`") {
          withTestExternalContext { _ =>
            assert(Some(v) == findRequestedLang(getAppForm(App, Form), v))
          }
        }

      it("must find language in session if present and not requested") {
        withTestExternalContext { _ =>
          NetUtils.getExternalContext.getRequest.getSession(true).setAttribute("fr-language", "fr")
          assert(Some("fr") == findRequestedLang(getAppForm(App, Form), null))
        }
      }

      it("must use requested language even if a language is present in session") {
        withTestExternalContext { _ =>
          assert(Some("it") == findRequestedLang(getAppForm(App, Form), "it"))
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

    val properties: PropertySet =
      PropertySet(
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
        assert(getProviders(appOpt, formOpt, formOrData, properties).toSet == expected)
      }
  }

  describe("`databaseConfigurationPresent()` function") {

    val properties: PropertySet =
      PropertySet(
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

    for ((desc, expected, get, properties) <- expected)
      it(s"must return `$expected` for `$desc`") {
        assert(RelationalUtils.databaseConfigurationPresent(PropertySet(properties), get) == expected)
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
            LazyList(
              ("filename", "IMG_9847.jpg"),
              ("mediatype", "image/jpeg"),
              ("size", "124073"),
              ("name", "_")
            ),
            "/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/045c3dfbe8b56144f996fc3f9c2e59e19735b3f8.bin"
          ),
          (
            "attachment",
            LazyList(
              ("filename", "IMG_9842.jpg"),
              ("mediatype", "image/jpeg"),
              ("size", "115511"),
              ("name", "multiple")
            ),
            "/fr/service/persistence/crud/issue/6530/data/ee1e28d0ea1e1666d2d76bd0ce9dac3304a6e783/c8a7e3e9f23e9e3c0d27943c2eb8fcd382ac1711.bin"
          ),
          (
            "attachment",
            LazyList(
              ("filename", "IMG_9844.jpg"),
              ("mediatype", "image/jpeg"),
              ("size", "122218"),
              ("name", "multiple")
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
}