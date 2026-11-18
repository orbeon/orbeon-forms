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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.option.*
import org.apache.hc.client5.http.cookie.BasicCookieStore
import org.mockito.{ArgumentMatchers, Mockito}
import org.orbeon.connection.{ConnectionContextSupport, ConnectionResult, StreamedContent}
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.http.Headers.*
import org.orbeon.oxf.http.{EmptyInputStream, Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.properties.PropertyLoader
import org.orbeon.oxf.servlet.*
import org.orbeon.oxf.test.{ResourceManagerSupport, ResourceManagerTestBase}
import org.orbeon.oxf.webapp.ProcessorService
import org.scalatest.funspec.AnyFunSpecLike

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.net.URI
import java.nio.file.Files
import java.util as ju
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
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
      override val getAttributesMap: ju.Map[String, AnyRef] = new ju.HashMap
    }

    // Needed by PropertyLoader.fromSafeRequestContext in buildConnectionHeadersCapitalized
    PropertyLoader.getPropertyStore(Some(incomingRequest))

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
      "/exist/crud/"                     -> true, // with #7955, this is no longer special-cased
      "/foo/bar.jsp"                     -> true, // with #7955, this is no longer special-cased
      OrbeonBaseUrl.toString             -> true,
      "http://example.org/other-prefix"  -> false,
      "https://example.org/other-prefix" -> false,
      "http://other.example.org/orbeon"  -> false,
      "https://other.example.org/orbeon" -> false,
      // #7944: the service prefix should be detected at the start of the URL only
      s"https://api.example.com/lookup/$OrbeonBaseUrl/fr/service/custom/orbeon/echo" -> false,
      s"http://other.example.org/x?u=$OrbeonBaseUrl/fr/service/custom/orbeon/echo"   -> false,
      s"http://other.example.org/x?u=$OrbeonBaseUrl/a.jsp"                           -> false,
      s"http://other.example.org/$OrbeonBaseUrl/"                                    -> false,
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

  describe("#6780: `ConnectionState` HTTP state saving and loading") {

    it("must not fail and must not save when there is no session") {
      val externalContext = newMockExternalContext(OrbeonBaseUrl)
      implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)
      implicit val logger: IndentedLogger = ResourceManagerTestBase.newIndentedLogger

      val cookieStore = new BasicCookieStore
      Connection.ConnectionState.saveHttpState(cookieStore)
      assert(Connection.ConnectionState.loadHttpState.isEmpty)
    }

    it("must save and load when there is a session") {
      val session = new SimpleSession("test-session")
      val incomingRequest = new RequestAdapter {
        override def getSession(create: Boolean): ExternalContext.Session = session
        override def incomingCookies: Iterable[(String, String)] = Nil
        override def getAttributesMap: ju.Map[String, AnyRef] = new ju.HashMap
        override val getHeaderValuesMap: ju.Map[String, Array[String]] = new ju.HashMap
        override val getContextPath: String = OrbeonBaseUrl.getPath
        override val servicePrefix: String = OrbeonBaseUrl.toString
      }
      val webAppContext = Mockito.mock(classOf[WebAppContext])
      Mockito when webAppContext.attributes thenReturn collection.mutable.Map[String, AnyRef]()

      val externalContext = Mockito.mock(classOf[ExternalContext])
      Mockito when externalContext.getRequest thenReturn incomingRequest
      Mockito when externalContext.getWebAppContext thenReturn webAppContext

      implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)
      implicit val logger: IndentedLogger = ResourceManagerTestBase.newIndentedLogger

      val cookieStore = new BasicCookieStore
      Connection.ConnectionState.saveHttpState(cookieStore)
      assert(Connection.ConnectionState.loadHttpState.contains(cookieStore))
    }
  }

  describe("#7066: Cookie forwarding") {

    def testCookieForwarding(
      cookiesToForward    : List[String],
      incomingCookiesList : List[(String, String)],
      externalSessionOpt  : Option[ExternalContext.Session] = None,
      requestedSessionId  : String                          = null
    ): Option[String] = {
      val incomingRequest = new RequestAdapter {
        override def getSession(create: Boolean): ExternalContext.Session = externalSessionOpt.orNull
        override def incomingCookies: Iterable[(String, String)] = incomingCookiesList
        override def getRequestedSessionId: String = requestedSessionId
        override def getAttributesMap: ju.Map[String, AnyRef] = new ju.HashMap
        override val getHeaderValuesMap: ju.Map[String, Array[String]] = new ju.HashMap
        override val getContextPath: String = OrbeonBaseUrl.getPath
        override val servicePrefix: String = OrbeonBaseUrl.toString
      }

      PropertyLoader.getPropertyStore(Some(incomingRequest))

      val webAppContext = Mockito.mock(classOf[WebAppContext])
      Mockito when webAppContext.attributes thenReturn collection.mutable.Map[String, AnyRef]()

      val externalContext = Mockito.mock(classOf[ExternalContext])
      Mockito when externalContext.getRequest thenReturn incomingRequest
      Mockito when externalContext.getWebAppContext thenReturn webAppContext

      implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)
      implicit val logger: IndentedLogger = ResourceManagerTestBase.newIndentedLogger

      val headers = Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = URI.create("/service"),
        hasCredentials   = false,
        customHeaders    = Map.empty,
        headersToForward = Set.empty,
        cookiesToForward = cookiesToForward,
        getHeader        = _ => None
      )

      headers.get(Headers.Cookie).map(_.head)
    }

    it("must forward custom cookie with its original value, and NOT with the session ID") {
      val session = new SimpleSession("session-123")
      val cookieHeader = testCookieForwarding(
        cookiesToForward    = List("MYCOOKIE"),
        incomingCookiesList = List("MYCOOKIE" -> "my-secret-token"),
        externalSessionOpt  = Some(session),
        requestedSessionId  = null
      )
      assert(cookieHeader.contains("MYCOOKIE=my-secret-token"))
    }

    it("must not synthesize a session cookie for a custom cookie when not in incoming cookies") {
      val session = new SimpleSession("session-123")
      val cookieHeader = testCookieForwarding(
        cookiesToForward    = List("MYCOOKIE"),
        incomingCookiesList = Nil,
        externalSessionOpt  = Some(session),
        requestedSessionId  = null
      )
      assert(cookieHeader.isEmpty)
    }

    it("must synthesize JSESSIONID when JSESSIONID is in cookiesToForward and session exists") {
      val session = new SimpleSession("session-123")
      val cookieHeader = testCookieForwarding(
        cookiesToForward    = List("JSESSIONID"),
        incomingCookiesList = Nil,
        externalSessionOpt  = Some(session),
        requestedSessionId  = null
      )
      assert(cookieHeader.contains("JSESSIONID=session-123"))
    }

    it("must forward incoming JSESSIONID when cookie value matches session ID (e.g. WebSphere prefix/suffix)") {
      val session = new SimpleSession("session-123")
      val cookieHeader = testCookieForwarding(
        cookiesToForward    = List("JSESSIONID"),
        incomingCookiesList = List("JSESSIONID" -> "0000session-123:-1"),
        externalSessionOpt  = Some(session),
        requestedSessionId  = "0000session-123:-1"
      )
      assert(cookieHeader.contains("JSESSIONID=0000session-123:-1"))
    }

    it("must handle clustered session cookie (e.g. WildFly route) and filter out non-matching JSESSIONID cookies") {
      val session = new SimpleSession("session-123")
      val cookieHeader = testCookieForwarding(
        cookiesToForward    = List("JSESSIONID", "JWT_AUTH_COOKIE"),
        incomingCookiesList = List(
          "JSESSIONID"      -> "other-app-session",
          "JSESSIONID"      -> "session-123.node1",
          "JWT_AUTH_COOKIE" -> "jwt.token.val"
        ),
        externalSessionOpt  = Some(session),
        requestedSessionId  = "session-123.node1"
      )
      assert(cookieHeader.contains("JSESSIONID=session-123.node1; JWT_AUTH_COOKIE=jwt.token.val"))
    }

    it("must ignore incoming JSESSIONID and synthesize from session when session is new") {
      val newSession = new SimpleSession("new-session-456") {
        override def isNew: Boolean = true
      }
      val cookieHeader = testCookieForwarding(
        cookiesToForward    = List("JSESSIONID"),
        incomingCookiesList = List("JSESSIONID" -> "new-session-456.node1"),
        externalSessionOpt  = Some(newSession),
        requestedSessionId  = "old-session-123"
      )
      assert(cookieHeader.contains("JSESSIONID=new-session-456"))
    }

    it("must preserve cookie forwarding order when multiple cookies are configured") {
      val session = new SimpleSession("session-123")
      val header1 = testCookieForwarding(
        cookiesToForward    = List("MYCOOKIE", "JSESSIONID"),
        incomingCookiesList = List("MYCOOKIE" -> "val1"),
        externalSessionOpt  = Some(session),
        requestedSessionId  = null
      )
      assert(header1.contains("MYCOOKIE=val1; JSESSIONID=session-123"))

      val header2 = testCookieForwarding(
        cookiesToForward    = List("JSESSIONID", "MYCOOKIE"),
        incomingCookiesList = List("MYCOOKIE" -> "val1"),
        externalSessionOpt  = Some(session),
        requestedSessionId  = null
      )
      assert(header2.contains("JSESSIONID=session-123; MYCOOKIE=val1"))
    }

    it("must return None if no matching cookies and no session") {
      val cookieHeader = testCookieForwarding(
        cookiesToForward    = List("JSESSIONID", "MYCOOKIE"),
        incomingCookiesList = Nil,
        externalSessionOpt  = None,
        requestedSessionId  = null
      )
      assert(cookieHeader.isEmpty)
    }
  }

  describe("ServletExternalContext incomingCookies parsing") {

    it("must parse cookies from the Cookie header") {
      val nativeRequest = Mockito.mock(classOf[HttpServletRequest])
      Mockito.when(nativeRequest.headerValuesList(Headers.Cookie))
        .thenReturn(List("SESSION=abc; JWT_TOKEN=xyz.123; OTHER="))

      val pipelineContext = Mockito.mock(classOf[PipelineContext])
      val webAppContext = Mockito.mock(classOf[WebAppContext])
      val nativeResponse = Mockito.mock(classOf[HttpServletResponse])

      val sec = new ServletExternalContext(pipelineContext, webAppContext, nativeRequest, nativeResponse)
      val cookies = sec.getRequest.incomingCookies.toList

      assert(cookies == List("SESSION" -> "abc", "JWT_TOKEN" -> "xyz.123", "OTHER" -> ""))
    }

    it("must fall back to nativeRequest.getCookies when Cookie header is absent") {
      val mockCookie = new Cookie {
        override def getNativeCookie: AnyRef = null
        override def getName: String = "FALLBACK"
        override def getValue: String = "cookie-val"
      }
      val nativeRequest = Mockito.mock(classOf[HttpServletRequest])
      Mockito.when(nativeRequest.headerValuesList(Headers.Cookie))
        .thenReturn(Nil)
      Mockito.when(nativeRequest.getCookies)
        .thenReturn(Array(mockCookie))

      val pipelineContext = Mockito.mock(classOf[PipelineContext])
      val webAppContext = Mockito.mock(classOf[WebAppContext])
      val nativeResponse = Mockito.mock(classOf[HttpServletResponse])

      val sec = new ServletExternalContext(pipelineContext, webAppContext, nativeRequest, nativeResponse)
      val cookies = sec.getRequest.incomingCookies.toList

      assert(cookies == List("FALLBACK" -> "cookie-val"))
    }
  }

  describe("#7531: Large attachment streaming and LocalResponse spooling") {

    def newDummyUrlRewriter: URLRewriter = new URLRewriter {
      def rewriteActionURL(urlString: String): String = urlString
      def rewriteRenderURL(urlString: String): String = urlString
      def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String = urlString
      def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String = urlString
      def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode): String = urlString
      def getNamespacePrefix: String = ""
    }

    def listLocalResponseTempFiles(): List[java.nio.file.Path] = {
      val tempDir = NetUtils.getTemporaryDirectory.toPath
      if (Files.exists(tempDir)) {
        val stream = Files.list(tempDir)
        try {
          stream
            .iterator()
            .asScala
            .filter(_.getFileName.toString.startsWith("orbeon-local-response-"))
            .toList
        } finally {
          stream.close()
        }
      } else {
        Nil
      }
    }

    it("must return -1 for LocalRequest.getContentLength and full Long in contentLengthOpt when > 2 GB") {
      val externalContext = newMockExternalContext(OrbeonBaseUrl)
      val fiveGb          = 5_000_000_000L

      val localRequest =
        LocalRequest(
          safeRequestCtx          = SafeRequestContext(externalContext),
          pathQuery               = "/fr/service/persistence/crud/app/form/data/123/file.bin",
          method                  = HttpMethod.PUT,
          headersMaybeCapitalized = Map.empty,
          content                 = Some(StreamedContent(EmptyInputStream, Some("application/octet-stream"), Some(fiveGb), None))
        )

      assert(localRequest.getContentLength == -1)
      assert(localRequest.contentLengthOpt.contains(fiveGb))
    }

    it("must pass InputStream through LocalResponse.sendInputStream lazily and close on LocalResponse.close()") {
      val bytesRead = new AtomicLong(0L)
      val isClosed  = new AtomicBoolean(false)
      val payload   = Array.tabulate[Byte](4096)(i => (i & 0xff).toByte)

      val trackingStream = new ByteArrayInputStream(payload) {
        override def read(): Int = {
          val r = super.read()
          if (r >= 0) bytesRead.incrementAndGet()
          r
        }
        override def read(b: Array[Byte], off: Int, len: Int): Int = {
          val r = super.read(b, off, len)
          if (r > 0) bytesRead.addAndGet(r.toLong)
          r
        }
        override def close(): Unit = {
          isClosed.set(true)
          super.close()
        }
      }

      val localResponse = new LocalResponse(newDummyUrlRewriter)
      localResponse.sendInputStream(trackingStream)

      // Zero bytes eagerly read on handoff
      assert(bytesRead.get() == 0L)
      assert(! isClosed.get())

      val out = new ByteArrayOutputStream
      IOUtils.copyStreamAndClose(localResponse.getInputStream, out)

      assert(bytesRead.get() == payload.length.toLong)
      assert(isClosed.get())
      assert(out.toByteArray.sameElements(payload))
    }

    it("must keep small LocalResponse bodies in memory and spill > threshold bodies to temporary files cleaned up on close/reset") {
      val beforeTempFiles = listLocalResponseTempFiles().toSet
      val threshold       = 1024

      // 1. Below threshold: stays in memory, no temp file created
      locally {
        val smallPayload = Array.tabulate[Byte](512)(i => (i & 0xff).toByte)
        val resp         = new LocalResponse(newDummyUrlRewriter, inMemoryThresholdBytes = threshold)
        resp.getOutputStream.write(smallPayload)
        assert((listLocalResponseTempFiles().toSet -- beforeTempFiles).isEmpty)

        val out = new ByteArrayOutputStream
        IOUtils.copyStreamAndClose(resp.getInputStream, out)
        assert(out.toByteArray.sameElements(smallPayload))
      }

      // 2. Above threshold: spills to temp file, reads back identically, deletes temp file on InputStream.close()
      locally {
        val largePayload = Array.tabulate[Byte](100 * 1024)(i => ((i * 31) & 0xff).toByte)
        val resp         = new LocalResponse(newDummyUrlRewriter, inMemoryThresholdBytes = threshold)
        resp.getOutputStream.write(largePayload)

        val spilledFiles = listLocalResponseTempFiles().toSet -- beforeTempFiles
        assert(spilledFiles.size == 1)

        val out = new ByteArrayOutputStream
        IOUtils.copyStreamAndClose(resp.getInputStream, out)
        assert(out.toByteArray.sameElements(largePayload))
        assert((listLocalResponseTempFiles().toSet -- beforeTempFiles).isEmpty)
      }

      // 3. Above threshold with reset() before reading: deletes temp file immediately
      locally {
        val largePayload = new Array[Byte](8 * 1024)
        val resp         = new LocalResponse(newDummyUrlRewriter, inMemoryThresholdBytes = threshold)
        resp.getOutputStream.write(largePayload)
        assert((listLocalResponseTempFiles().toSet -- beforeTempFiles).size == 1)

        resp.reset()
        assert((listLocalResponseTempFiles().toSet -- beforeTempFiles).isEmpty)
      }
    }

    it("must close underlying InputStream when AsyncConnectionResult is closed without draining or after draining") {
      // Closed without draining (e.g. HTTP error in trySuccessConnection)
      locally {
        val closed = new AtomicBoolean(false)
        val syncCxr = ConnectionResult(
          url        = "http://example.org/test",
          statusCode = StatusCode.InternalServerError,
          headers    = Map.empty,
          content    = StreamedContent(
            inputStream   = new ByteArrayInputStream(Array[Byte](1, 2, 3)) {
              override def close(): Unit = { closed.set(true); super.close() }
            },
            contentType   = Some("application/octet-stream"),
            contentLength = Some(3L),
            title         = None
          )
        )
        val asyncCxr = ConnectionResult.syncToAsync(syncCxr)
        assert(ConnectionResult.trySuccessConnection(asyncCxr).isFailure)
        assert(closed.get())
      }

      // Closed automatically when fs2.Stream is drained
      locally {
        val closed = new AtomicBoolean(false)
        val syncCxr = ConnectionResult(
          url        = "http://example.org/test",
          statusCode = StatusCode.Ok,
          headers    = Map.empty,
          content    = StreamedContent(
            inputStream   = new ByteArrayInputStream(Array[Byte](10, 20, 30, 40)) {
              override def close(): Unit = { closed.set(true); super.close() }
            },
            contentType   = Some("application/octet-stream"),
            contentLength = Some(4L),
            title         = None
          )
        )
        val asyncCxr = ConnectionResult.syncToAsync(syncCxr)
        val bytes    = asyncCxr.content.stream.compile.to(Array).unsafeRunSync()
        assert(bytes.sameElements(Array[Byte](10, 20, 30, 40)))
        assert(closed.get())
      }
    }

    it("must stream > 2 GB (exceeding Int.MaxValue) through Connection.connectAsync without Array[Byte] materialization") {
      val externalContext = newMockExternalContext(OrbeonBaseUrl)

      // 2.5 GB = 40,960 * 64 KB = 2,684,354,560 bytes (> Int.MaxValue, impossible to fit in a JVM Array[Byte])
      val chunkSize  = 65536
      val numChunks  = 40960
      val totalBytes = numChunks.toLong * chunkSize.toLong
      val chunkBytes = Array.tabulate[Byte](chunkSize)(i => (i & 0xff).toByte)
      val chunk      = fs2.Chunk.array(chunkBytes)

      val syntheticStream: fs2.Stream[IO, Byte] =
        fs2.Stream.chunk(chunk).repeatN(numChunks.toLong)

      val mockProcessorService = Mockito.mock(classOf[ProcessorService])
      Mockito.doAnswer { invocation =>
        val ec  = invocation.getArgument[ExternalContext](1)
        val req = ec.getRequest
        val res = ec.getResponse

        assert(req.getContentLength == -1)
        assert(req.contentLengthOpt.contains(totalBytes))

        val buf   = new Array[Byte](chunkSize)
        var count = 0L
        val is    = req.getInputStream
        try {
          var read = is.read(buf)
          while (read != -1) {
            count += read.toLong
            read = is.read(buf)
          }
        } finally {
          is.close()
        }

        res.setStatus(StatusCode.Ok)
        res.setContentType(ContentTypes.PlainTextContentType)
        res.getWriter.print(count.toString)
        res.getWriter.flush()
        null
      }.when(mockProcessorService).service(ArgumentMatchers.any(), ArgumentMatchers.any())

      val streamedBytesCountStr =
        ProcessorService.withProcessorService(mockProcessorService) {
          implicit val safeRequestCtx  : SafeRequestContext                          = SafeRequestContext(externalContext)
          implicit val connectionCtx   : ConnectionContextSupport.ConnectionContexts = ConnectionContextSupport.findContext(Map.empty)
          implicit val logger          : IndentedLogger                              = ResourceManagerTestBase.newIndentedLogger
          implicit val resourceResolver: Option[ResourceResolver]                    = None

          val io =
            for {
              cr   <- Connection.connectAsync(
                method      = HttpMethod.PUT,
                url         = URI.create(s"$OrbeonBaseUrl/fr/service/large-stream-test"),
                credentials = None,
                content     = Some(StreamedContent(syntheticStream, Some("application/octet-stream"), Some(totalBytes))),
                headers     = Map(Headers.ContentType -> List("application/octet-stream")),
                loadState   = false,
                logBody     = false
              )
              cxr  <- IO.fromTry(ConnectionResult.trySuccessConnection(cr))
              body <- cxr.content.stream.through(fs2.text.utf8.decode).compile.string
            } yield body

          io.unsafeRunSync()
        }

      assert(streamedBytesCountStr == totalBytes.toString)
    }
  }
}