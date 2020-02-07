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

import java.net.URI
import java.{util => ju}

import cats.syntax.option._
import org.mockito.Mockito
import org.orbeon.io.{CharsetNames, UriScheme}
import org.orbeon.oxf.externalcontext.URLRewriter.REWRITE_MODE_ABSOLUTE
import org.orbeon.oxf.externalcontext.{ExternalContext, LocalRequest, RequestAdapter, WebAppContext}
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.{Headers, HttpMethod, StreamedContent}
import org.orbeon.oxf.test.{ResourceManagerSupport, ResourceManagerTestBase}
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.JavaConverters._
import scala.collection.mutable

class ConnectionTest
  extends ResourceManagerSupport // for properties in particular
     with AnyFunSpecLike {

  def newMockExternalContext: ExternalContext = {

    val incomingRequest = new RequestAdapter {
      override val getHeaderValuesMap: ju.Map[String, Array[String]] = mutable.LinkedHashMap(
        "user-agent"    -> Array("Mozilla 12.1"),
        "authorization" -> Array("xsifj1skf3"),
        "host"          -> Array("localhost"),
        "cookie"        -> Array("JSESSIONID=4FF78C3BD70905FAB502BC989450E40C")
      ).asJava
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

      val externalContext = newMockExternalContext

      val headersCapitalized =
        Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
          url              = new URI("/foo/bar"),
          method           = HttpMethod.GET,
          hasCredentials   = false,
          mediatype        = null,
          encodingForSOAP  = CharsetNames.Utf8,
          customHeaders    = customHeaderValuesMap,
          headersToForward = Set(Headers.Cookie, Headers.Authorization, "User-Agent"),
          getHeader        = Connection.getHeaderFromRequest(externalContext.getRequest))(
          logger           = ResourceManagerTestBase.newIndentedLogger,
          externalContext  = externalContext
        )

      val request =
        new LocalRequest(
          incomingRequest         = externalContext.getRequest,
          contextPath             = "/orbeon",
          pathQuery               = "/foo/bar",
          method                  = HttpMethod.GET,
          headersMaybeCapitalized = headersCapitalized,
          content                 = None
        )

      // Test standard headers received
      // See #3135 regarding `LocalRequest` capitalization. We might want to change how this works.
      val headerValuesMap = request.getHeaderValuesMap.asScala

      assert("Mozilla 12.1"                                === headerValuesMap("user-agent")(0))
      assert("xsifj1skf3"                                  === headerValuesMap("authorization")(0))
      assert("JSESSIONID=4FF78C3BD70905FAB502BC989450E40C" === headerValuesMap("cookie")(0))
      assert(None                                          === headerValuesMap.get("host"))
      assert(None                                          === headerValuesMap.get("foobar"))

      // Test custom headers received
      assert("my-value"                                    === headerValuesMap("my-stuff")(0))
      assert("your-value-1"                                === headerValuesMap("your-stuff")(0))
      assert("your-value-2"                                === headerValuesMap("your-stuff")(1))
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

      val externalContext = newMockExternalContext

      val headersCapitalized =
        Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
          url              = new URI("/foo/bar"),
          method           = method,
          hasCredentials   = false,
          mediatype        = bodyMediaType,
          encodingForSOAP  = CharsetNames.Utf8,
          customHeaders    = explicitHeaders,
          headersToForward = Set.empty,
          getHeader        = _ => None)(
          logger           = ResourceManagerTestBase.newIndentedLogger,
          externalContext  = externalContext
        )

      val wrapper =
        new LocalRequest(
          incomingRequest         = externalContext.getRequest,
          contextPath             = "/orbeon",
          pathQuery               = s"/foobar?$queryString",
          method                  = method,
          headersMaybeCapitalized = headersCapitalized,
          content                 = Some(StreamedContent.fromBytes(messageBody, Some(bodyMediaType)))
        )

      val parameters = wrapper.getParameterMap
      assert("name1=value1a&name1=value1b&name1=value1c&name2=value2a&name2=value2b&name3=value3" === NetUtils.encodeQueryString(parameters))
    }
  }

  describe("#4384: GET service must not send a `Content-Type` header") {

    val externalContext = newMockExternalContext

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
            url              = new URI("/foo/bar"),
            method           = method,
            hasCredentials   = false,
            mediatype        = contentType,
            encodingForSOAP  = CharsetNames.Utf8,
            customHeaders    = Map.empty,
            headersToForward = Set.empty,
            getHeader        = _ => None)(
            logger           = ResourceManagerTestBase.newIndentedLogger,
            externalContext  = externalContext
          )

        assert(expectedHeaderValue == firstItemIgnoreCase(headersCapitalized, Headers.ContentType))
      }
    }
  }

  describe("#4388: Don't send `Orbeon-Token` to external services") {

    val externalContext = newMockExternalContext

    val Expected = List(
      "/foo/bar"                       -> true,
      "/fr/service/custom/orbeon/echo" -> true,
      "/exist/crud/"                   -> true,
      "http://example.org/service"     -> false,
      "https://example.org/service"    -> false
    )

    for {
      (urlString, mustIncludeToken) <- Expected
      httpMethod                    <- List(HttpMethod.GET, HttpMethod.POST)
      serviceAbsoluteUrl            = URLRewriterUtils.rewriteServiceURL(externalContext.getRequest, urlString, REWRITE_MODE_ABSOLUTE)
    } locally {
      it(s"call to `$urlString` with `$httpMethod` must ${if (mustIncludeToken) "" else "not " }include an `Orbeon-Token` header") {
        val headersCapitalized =
          Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
            url              = new URI(serviceAbsoluteUrl),
            method           = httpMethod,
            hasCredentials   = false,
            mediatype        = ContentTypes.XmlContentType,
            encodingForSOAP  = CharsetNames.Utf8,
            customHeaders    = Map.empty,
            headersToForward = Set.empty,
            getHeader        = _ => None)(
            logger           = ResourceManagerTestBase.newIndentedLogger,
            externalContext  = externalContext
          )

        assert(mustIncludeToken == firstItemIgnoreCase(headersCapitalized, Headers.OrbeonToken).isDefined)
      }
    }
  }
}