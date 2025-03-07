/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.util

import cats.syntax.option.*
import org.mockito.Mockito
import org.orbeon.connection.StreamedContent
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.http.Headers.*
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.test.{ResourceManagerSupport, ResourceManagerTestBase}
import org.scalatest.funspec.AnyFunSpecLike

import java.net.URI
import java.util as ju
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


class ConnectionTest
  extends ResourceManagerSupport // for properties in particular
     with AnyFunSpecLike {

  private val OrbeonBaseUrl = URI.create("http://example.org/orbeon")

  def newMockExternalContext(servicePrefixUri: URI): ExternalContext = {

    val incomingRequest = new RequestAdapter {

      override val getHeaderValuesMap: ju.Map[String, Array[String]] = mutable.LinkedHashMap(
        "user-agent"    -> Array("Mozilla 12.1"),
        "authorization" -> Array("xsifj1skf3"),
        "host"          -> Array("localhost"),
        "cookie"        -> Array("JSESSIONID=4FF78C3BD70905FAB502BC989450E40C")
      ).asJava

      override val getContextPath: String = servicePrefixUri.getPath
      override val servicePrefix: String = servicePrefixUri.toString

      override def incomingCookies: Iterable[(String, String)] = Nil
      override def getAttributesMap: ju.Map[String, AnyRef] = new ju.HashMap
    }

    val webAppContext = Mockito.mock(classOf[WebAppContext])
    Mockito when webAppContext.attributes thenReturn collection.mutable.Map[String, AnyRef]()

    val externalContext = Mockito.mock(classOf[ExternalContext])
    Mockito when externalContext.getRequest thenReturn incomingRequest
    Mockito when externalContext.getWebAppContext thenReturn webAppContext

    externalContext
  }

  describe("Connection headers") {

    // Custom headers
    val customHeaderValuesMap = Map(
      "My-Stuff"   -> List("my-value"),
      "Your-Stuff" -> List("your-value-1", "your-value-2")
    )

    it("must process request and response headers") {

      val externalContext = newMockExternalContext(OrbeonBaseUrl)

      val headersCapitalized =
        Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
          url              = URI.create("/foo/bar"),
          method           = HttpMethod.GET,
          hasCredentials   = false,
          mediatypeOpt     = None,
          encodingForSOAP  = CharsetNames.Utf8,
          customHeaders    = customHeaderValuesMap,
          headersToForward = Set(Headers.Cookie, Headers.Authorization, "User-Agent"),
          getHeader        = Connection.getHeaderFromRequest(externalContext.getRequest)
        )(
          logger           = ResourceManagerTestBase.newIndentedLogger,
          safeRequestCtx   = SafeRequestContext(externalContext)
        )

      val request =
        LocalRequest(
          externalContext         = externalContext,
          pathQuery               = "/foo/bar",
          method                  = HttpMethod.GET,
          headersMaybeCapitalized = headersCapitalized,
          content                 = None
        )

      // Test standard headers received
      // See #3135 regarding `LocalRequest` capitalization. We might want to change how this works.
      val headerValuesMap = request.getHeaderValuesMap.asScala

      assert("Mozilla 12.1"                                == headerValuesMap("user-agent")(0))
      assert("xsifj1skf3"                                  == headerValuesMap("authorization")(0))
      assert("JSESSIONID=4FF78C3BD70905FAB502BC989450E40C" == headerValuesMap("cookie")(0))
      assert(None                                          == headerValuesMap.get("host"))
      assert(None                                          == headerValuesMap.get("foobar"))

      // Test custom headers received
      assert("my-value"                                    == headerValuesMap("my-stuff")(0))
      assert("your-value-1"                                == headerValuesMap("your-stuff")(0))
      assert("your-value-2"                                == headerValuesMap("your-stuff")(1))
    }
  }

  describe("Combining request parameters") {

    val queryString = "name1=value1a&name2=value2a&name3=value3"
    val messageBody = "name1=value1b&name1=value1c&name2=value2b".getBytes(CharsetNames.Utf8)

    // POST configuration
    val method = HttpMethod.POST
    val bodyMediaType = "application/x-www-form-urlencoded"
    val explicitHeaders = Map(ContentTypeLower -> List(bodyMediaType))

    it("must combine them") {

      val externalContext = newMockExternalContext(OrbeonBaseUrl)

      val headersCapitalized =
        Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
          url              = URI.create("/foo/bar"),
          method           = method,
          hasCredentials   = false,
          mediatypeOpt     = bodyMediaType.some,
          encodingForSOAP  = CharsetNames.Utf8,
          customHeaders    = explicitHeaders,
          headersToForward = Set.empty,
          getHeader        = _ => None
        )(
          logger           = ResourceManagerTestBase.newIndentedLogger,
          safeRequestCtx   = SafeRequestContext(externalContext)
        )

      val wrapper =
        LocalRequest(
          externalContext         = externalContext,
          pathQuery               = s"/foobar?$queryString",
          method                  = method,
          headersMaybeCapitalized = headersCapitalized,
          content                 = Some(StreamedContent.fromBytes(messageBody, Some(bodyMediaType)))
        )

      val parameters = wrapper.getParameterMap
      assert("name1=value1a&name1=value1b&name1=value1c&name2=value2a&name2=value2b&name3=value3" === PathUtils.encodeQueryString(parameters.asScala))
    }
  }

  describe("#4384: GET service must not send a `Content-Type` header") {

    val externalContext = newMockExternalContext(OrbeonBaseUrl)

    val contentType = ContentTypes.XmlContentType

    val Expected = List(
      HttpMethod.GET     -> None,
      HttpMethod.POST    -> contentType.some,
      HttpMethod.PUT     -> contentType.some,
      HttpMethod.DELETE  -> None,
      HttpMethod.HEAD    -> None,
      HttpMethod.OPTIONS -> None,
      HttpMethod.TRACE   -> None,
      HttpMethod.LOCK    -> contentType.some,
      HttpMethod.UNLOCK  -> contentType.some
    )

    for ((method, expectedHeaderValue) <- Expected) {
      it(s"must ${if (expectedHeaderValue.isDefined) "" else "not " }include a `Content-Type` header when using the `${method.entryName}` method") {
        val headersCapitalized =
          Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
            url              = URI.create("/foo/bar"),
            method           = method,
            hasCredentials   = false,
            mediatypeOpt     = contentType.some,
            encodingForSOAP  = CharsetNames.Utf8,
            customHeaders    = Map.empty,
            headersToForward = Set.empty,
            getHeader        = _ => None
          )(
            logger           = ResourceManagerTestBase.newIndentedLogger,
            safeRequestCtx   = SafeRequestContext(externalContext)
          )

        assert(expectedHeaderValue == firstItemIgnoreCase(headersCapitalized, Headers.ContentType))
      }
    }
  }

  describe("#4388: Don't send `Orbeon-Token` to external services") {

    val externalContext = newMockExternalContext(OrbeonBaseUrl)

    val Expected = List(
      "/foo/bar"                         -> true,
      "/fr/service/custom/orbeon/echo"   -> true,
      "/exist/crud/"                     -> true,
      OrbeonBaseUrl.toString             -> true,
      "http://example.org/other-prefix"  -> false,
      "https://example.org/other-prefix" -> false,
      "http://other.example.org/orbeon"  -> false,
      "https://other.example.org/orbeon" -> false,
    )

    for {
      (urlString, mustIncludeToken) <- Expected
      httpMethod                    <- List(HttpMethod.GET, HttpMethod.POST)
      serviceAbsoluteUrl            = URLRewriterImpl.rewriteServiceUrl(externalContext.getRequest, urlString, UrlRewriteMode.Absolute, OrbeonBaseUrl.toString)
    } locally {
      it(s"call to `$urlString` with `$httpMethod` must ${if (mustIncludeToken) "" else "not " }include an `Orbeon-Token` header") {
        val headersCapitalized =
          Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
            url              = URI.create(serviceAbsoluteUrl),
            method           = httpMethod,
            hasCredentials   = false,
            mediatypeOpt     = ContentTypes.XmlContentType.some,
            encodingForSOAP  = CharsetNames.Utf8,
            customHeaders    = Map.empty,
            headersToForward = Set.empty,
            getHeader        = _ => None
          )(
            logger           = ResourceManagerTestBase.newIndentedLogger,
            safeRequestCtx   = SafeRequestContext(externalContext)
          )

        assert(mustIncludeToken == firstItemIgnoreCase(headersCapitalized, Headers.OrbeonToken).isDefined)
      }
    }
  }
}