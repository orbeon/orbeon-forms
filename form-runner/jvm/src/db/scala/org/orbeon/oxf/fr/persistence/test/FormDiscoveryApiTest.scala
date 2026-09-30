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
package org.orbeon.oxf.fr.persistence.test

import cats.implicits.catsSyntaxOptionId
import io.circe.Json
import io.circe.syntax.*
import org.orbeon.dom.Document
import org.orbeon.oxf.externalcontext.SafeRequestContext
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.fr.persistence.http.HttpCall.Check
import org.orbeon.oxf.fr.{AppForm, Version}
import org.orbeon.oxf.http.HttpMethod.*
import org.orbeon.oxf.http.{Headers, StatusCode}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class FormDiscoveryApiTest
  extends DocumentTestBase
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  private val applicationCounts = 4

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[FormDiscoveryApiTest]), true)

  private def assertGetXml(
    path          : String,
    expectedXml   : Document,
    headers       : Map[String, List[String]] = Map.empty,
    expectedCode  : Int = StatusCode.Ok
  )(implicit
    safeRequestCtx: SafeRequestContext
  ): Unit =
    HttpCall.assertCall(
      actualRequest = HttpCall.SolicitedRequest(
        path    = path,
        method  = GET,
        headers = headers
      ),
      expectedResponse = HttpCall.ExpectedResponse(
        code        = expectedCode,
        contentType = Check.Some(ContentTypes.XmlContentType),
        body        = HttpCall.XML(expectedXml).some
      )
    )

  private def assertGet(
    path          : String,
    expectedXml   : Document,
    expectedCode  : Int = StatusCode.Ok
  )(implicit
    safeRequestCtx: SafeRequestContext
  ): Unit =
    assertGetXml(path, expectedXml, headers = Map.empty, expectedCode)

  private def assertGetJson(
    path          : String,
    expectedJson  : Json,
    headers       : Map[String, List[String]] = Map(Headers.Accept -> List(ContentTypes.JsonContentType)),
    expectedCode  : Int = StatusCode.Ok
  )(implicit
    safeRequestCtx: SafeRequestContext
  ): Unit =
    HttpCall.assertCall(
      actualRequest = HttpCall.SolicitedRequest(
        path    = path,
        method  = GET,
        headers = headers
      ),
      expectedResponse = HttpCall.ExpectedResponse(
        code        = expectedCode,
        contentType = Check.Some(ContentTypes.JsonContentType),
        body        = HttpCall.JSON(expectedJson).some
      )
    )

  private def assertMethodNotAllowed(
    path          : String,
    method        : org.orbeon.oxf.http.HttpMethod
  )(implicit
    safeRequestCtx: SafeRequestContext
  ): Unit =
    HttpCall.assertCall(
      actualRequest = HttpCall.SolicitedRequest(
        path   = path,
        method = method
      ),
      expectedResponse = HttpCall.ExpectedResponse(
        code = StatusCode.MethodNotAllowed
      )
    )

  describe("Form Discovery API") {

    it("returns empty XML and JSON when there are no form definitions") {
      FormMetadataApiTest.withProvider(applicationCounts) { provider => externalContext =>
        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        assertGet("distinct-apps", <_/>.toDocument)
        assertGet(s"distinct-forms/${provider.entryName}-1", <_/>.toDocument)
        assertGet(s"distinct-versions/${provider.entryName}-1/test-form-1", <_/>.toDocument)

        assertGetJson("distinct-apps", Json.arr())
        assertGetJson(s"distinct-forms/${provider.entryName}-1", Json.arr())
        assertGetJson(s"distinct-versions/${provider.entryName}-1/test-form-1", Json.arr())
      }
    }

    it("returns distinct applications, forms, and versions in XML and JSON") {
      FormMetadataApiTest.withProvider(applicationCounts) { provider => externalContext =>
        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        val app1 = s"${provider.entryName}-1"
        val app2 = s"${provider.entryName}-2"

        // App 1: form-b with versions 1, 2; form-a with version 1
        val form1b = TestForm(AppForm(app1, "form-b"), Map("en" -> "Form B"), Seq.empty, "read list".some)
        val form1a = TestForm(AppForm(app1, "form-a"), Map("en" -> "Form A"), Seq.empty, "read list".some)

        // App 2: form-c with versions 1, 3, 2
        val form2c = TestForm(AppForm(app2, "form-c"), Map("en" -> "Form C"), Seq.empty, "read list".some)

        form1b.putFormDefinition(version = Version.Specific(1))
        form1b.putFormDefinition(version = Version.Specific(2))
        form1a.putFormDefinition(version = Version.Specific(1))

        form2c.putFormDefinition(version = Version.Specific(1))
        form2c.putFormDefinition(version = Version.Specific(3))
        form2c.putFormDefinition(version = Version.Specific(2))

        // Distinct apps: should be sorted
        val expectedApps =
          <_>
            <_>{app1}</_>
            <_>{app2}</_>
          </_>.toDocument

        assertGet("distinct-apps", expectedApps)
        assertGetJson("distinct-apps", Json.arr(app1.asJson, app2.asJson))

        // Distinct forms for app1: sorted alphabetically (form-a, form-b)
        val expectedFormsApp1 =
          <_>
            <_>form-a</_>
            <_>form-b</_>
          </_>.toDocument

        assertGet(s"distinct-forms/$app1", expectedFormsApp1)
        assertGetJson(s"distinct-forms/$app1", Json.arr("form-a".asJson, "form-b".asJson))

        // Distinct forms for app2: form-c
        val expectedFormsApp2 =
          <_>
            <_>form-c</_>
          </_>.toDocument

        assertGet(s"distinct-forms/$app2", expectedFormsApp2)
        assertGetJson(s"distinct-forms/$app2", Json.arr("form-c".asJson))

        // Distinct forms for non-existent app
        assertGet(s"distinct-forms/non-existent-app", <_/>.toDocument)
        assertGetJson(s"distinct-forms/non-existent-app", Json.arr())

        // Distinct versions for app1 / form-b: 1, 2 (sorted numerically)
        val expectedVersions1b =
          <_>
            <_>1</_>
            <_>2</_>
          </_>.toDocument

        assertGet(s"distinct-versions/$app1/form-b", expectedVersions1b)
        assertGetJson(s"distinct-versions/$app1/form-b", Json.arr(1.asJson, 2.asJson))

        // Distinct versions for app1 / form-a: 1
        val expectedVersions1a =
          <_>
            <_>1</_>
          </_>.toDocument

        assertGet(s"distinct-versions/$app1/form-a", expectedVersions1a)
        assertGetJson(s"distinct-versions/$app1/form-a", Json.arr(1.asJson))

        // Distinct versions for app2 / form-c: 1, 2, 3 (sorted numerically even though inserted 1, 3, 2)
        val expectedVersions2c =
          <_>
            <_>1</_>
            <_>2</_>
            <_>3</_>
          </_>.toDocument

        assertGet(s"distinct-versions/$app2/form-c", expectedVersions2c)
        assertGetJson(s"distinct-versions/$app2/form-c", Json.arr(1.asJson, 2.asJson, 3.asJson))

        // Distinct versions for non-existent form
        assertGet(s"distinct-versions/$app1/non-existent-form", <_/>.toDocument)
        assertGetJson(s"distinct-versions/$app1/non-existent-form", Json.arr())
      }
    }

    it("supports content negotiation via Accept header") {
      FormMetadataApiTest.withProvider(applicationCounts) { provider => externalContext =>
        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        val app = s"${provider.entryName}-1"
        val form = TestForm(AppForm(app, "form-1"), Map("en" -> "Form 1"), Seq.empty, "read list".some)
        form.putFormDefinition(version = Version.Specific(1))

        val expectedXml  = <_><_>{app}</_></_>.toDocument
        val expectedJson = Json.arr(app.asJson)

        def acceptHeader(value: String): Map[String, List[String]] =
          Map(Headers.Accept -> List(value))

        // No Accept header -> XML
        assertGetXml("distinct-apps", expectedXml, headers = Map.empty)

        // Explicit XML Accept headers -> XML
        assertGetXml("distinct-apps", expectedXml, headers = acceptHeader(ContentTypes.XmlContentType))
        assertGetXml("distinct-apps", expectedXml, headers = acceptHeader("text/xml"))
        assertGetXml("distinct-apps", expectedXml, headers = acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
        assertGetXml("distinct-apps", expectedXml, headers = acceptHeader("application/json;q=0.5, application/xml;q=0.9"))
        assertGetXml("distinct-apps", expectedXml, headers = acceptHeader("application/xml, */*"))

        // JSON Accept headers -> JSON
        assertGetJson("distinct-apps", expectedJson, headers = acceptHeader(ContentTypes.JsonContentType))
        assertGetJson("distinct-apps", expectedJson, headers = acceptHeader("*/*"))
        assertGetJson("distinct-apps", expectedJson, headers = acceptHeader("application/json, text/plain"))
        assertGetJson("distinct-apps", expectedJson, headers = acceptHeader("application/json;q=0.9, application/xml;q=0.8"))
        assertGetJson("distinct-apps", expectedJson, headers = acceptHeader("application/json, */*"))
      }
    }

    it("rejects non-GET HTTP methods with 405 Method Not Allowed") {
      FormMetadataApiTest.withProvider(applicationCounts) { provider => externalContext =>
        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        val app = s"${provider.entryName}-1"
        val form = "test-form-1"

        for (method <- Seq(POST, PUT, DELETE)) {
          assertMethodNotAllowed("distinct-apps", method)
          assertMethodNotAllowed(s"distinct-forms/$app", method)
          assertMethodNotAllowed(s"distinct-versions/$app/$form", method)
        }
      }
    }
  }
}
