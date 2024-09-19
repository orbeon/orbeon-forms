/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.http

import org.apache.commons.io.IOUtils.toByteArray
import org.orbeon.connection.{BufferedContent, ConnectionResult, StreamedContent}
import org.orbeon.dom.Document
import org.orbeon.dom.io.XMLWriter
import org.orbeon.io.IOUtils
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.{Credentials, ExternalContext}
import org.orbeon.oxf.fr.Version.Unspecified
import org.orbeon.oxf.fr.permission.Operations
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.PersistenceBase
import org.orbeon.oxf.fr.persistence.relational.rest.LockInfo
import org.orbeon.oxf.fr.persistence.relational.{Provider, StageHeader}
import org.orbeon.oxf.fr.workflow.definitions20201.Stage
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, SearchVersion, Version}
import org.orbeon.oxf.http.HttpMethod.*
import org.orbeon.oxf.http.*
import org.orbeon.oxf.test.TestHttpClient
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.{Connection, ContentTypes, CoreCrossPlatformSupportTrait, IndentedLogger}
import org.orbeon.oxf.xml.dom.{Comparator, IOSupport}
import org.scalatest.Assertions.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.URI
import scala.util.Try


private[persistence] object HttpCall {

  sealed trait Body
  case class XML   (doc : Document   ) extends Body
  case class Binary(file: Array[Byte]) extends Body

  case class SolicitedRequest(
    path              : String,
    method            : HttpMethod,
    version           : Version                      = Unspecified,
    stage             : Option[Stage]                = None,
    body              : Option[Body]                 = None,
    credentials       : Option[Credentials]          = None,
    timeout           : Option[Int]                  = None,
    xmlResponseFilter : Option[Document => Document] = None
  )

  case class ExpectedResponse(
    code        : Int,
    operations  : Option[Operations]  = None,
    formVersion : Option[Int]         = None,
    body        : Option[Body]        = None
  )

  case class Response(
    code        : Int,
    operations  : Operations,
    formVersion : Option[Int],
    body        : Array[Byte]
  )

  def assertCall(
    actualRequest            : SolicitedRequest,
    expectedResponse         : ExpectedResponse)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Unit =
    assertCall(
      actualRequest  = actualRequest,
      assertResponse = actualResponse => {
        // Check response code
        assert(actualResponse.code == expectedResponse.code)

        // Check operations
        expectedResponse.operations.foreach { expectedOperations =>
          assert(actualResponse.operations == expectedOperations)
        }

        // Check form version
        assert(actualResponse.formVersion == expectedResponse.formVersion)

        // Check body
        expectedResponse.body.foreach {
          case HttpCall.XML(originalExpectedDoc) =>
            val originalResultDoc  = IOSupport.readOrbeonDom(new ByteArrayInputStream(actualResponse.body))

            val (resultDoc, expectedDoc) = actualRequest.xmlResponseFilter match {
              case None         => (       originalResultDoc,         originalExpectedDoc)
              case Some(filter) => (filter(originalResultDoc), filter(originalExpectedDoc))
            }

            if (! Comparator.compareDocumentsIgnoreNamespacesInScope(resultDoc, expectedDoc))
              assert(
                resultDoc.getRootElement.serializeToString(XMLWriter.PrettyFormat) ===
                  expectedDoc.getRootElement.serializeToString(XMLWriter.PrettyFormat)
              )

          case HttpCall.Binary(expectedFile) =>
            assert(actualResponse.body sameElements expectedFile)
        }
      }
    )

  def assertCall(
    actualRequest            : SolicitedRequest,
    assertResponse           : Response => Unit)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Unit = {
    useAndClose(
      request(
        path        = actualRequest.path,
        method      = actualRequest.method,
        version     = actualRequest.version,
        stage       = actualRequest.stage,
        body        = actualRequest.body,
        credentials = actualRequest.credentials,
        timeout     = actualRequest.timeout
      )
    ) { closableHttpResponse =>

      val actualResponse = closableHttpResponse.httpResponse
      val actualHeaders  = actualResponse.headers

      val response = Response(
        code        = actualResponse.statusCode,
        operations  = Operations.parseFromHeaders(actualHeaders).getOrElse(Operations.None),
        formVersion = Headers.firstItemIgnoreCase(actualHeaders, Version.OrbeonFormDefinitionVersion).map(_.toInt),
        body        = {
          val outputStream = new ByteArrayOutputStream
          IOUtils.copyStreamAndClose(actualResponse.content.stream, outputStream)
          outputStream.toByteArray
        }
      )

      assertResponse(response)
    }
  }

  private def request(
    path                     : String,
    method                   : HttpMethod,
    version                  : Version,
    stage                    : Option[Stage],
    body                     : Option[Body],
    credentials              : Option[Credentials],
    httpRange                : Option[HttpRange] = None,
    timeout                  : Option[Int] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): ClosableHttpResponse = {

    val documentURL = PersistenceBase.appendSlash + path.dropStartingSlash

    val headersCapitalized = {

      import Version._

      val timeoutHeader  = timeout.map(t => Headers.Timeout -> List(Headers.TimeoutValuePrefix + t.toString))
      val versionHeaders = version match {
        case Unspecified                      => Nil
        case Next                             => List(OrbeonFormDefinitionVersion -> List("next"))
        case Specific(version)                => List(OrbeonFormDefinitionVersion -> List(version.toString))
        case ForDocument(documentId, isDraft) => List(OrbeonForDocumentId         -> List(documentId),
                                                      OrbeonForDocumentIsDraft    -> List(isDraft.toString))
      }
      val stageHeader      = stage.map(_.name).map(StageHeader.HeaderName -> List(_))
      val httpRangeHeaders = httpRange.map(_.requestHeaders).getOrElse(Map.empty)
      val headers          = (timeoutHeader.toList ++ versionHeaders ++ stageHeader.toList).toMap ++ httpRangeHeaders

      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = URI.create(documentURL),
        hasCredentials   = false,
        customHeaders    = headers,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = _ => None
      )
    }

    val contentType = body.map {
      case XML   (_) => ContentTypes.XmlContentType
      case Binary(_) => "application/octet-stream"
    }

    val messageBody = body map {
      case XML   (doc ) => doc.getRootElement.serializeToString().getBytes
      case Binary(file) => file
    }

    val content = messageBody map
      (StreamedContent.fromBytes(_, contentType))

    val (_, httpResponse, _, _) =
      TestHttpClient.connect(
        url          = documentURL,
        method       = method,
        headers      = headersCapitalized,
        content      = content,
        credentials  = credentials
      )

    new ClosableHttpResponse(httpResponse)
  }

  // Wrap `HttpResponse`, so we can later use `ScalaUtils.useAndClose()`, which expects a `.close()`,
  // while `HttpResponse` has a `.disconnect()`.
  private class ClosableHttpResponse(val httpResponse: HttpResponse) {
    def close(): Unit = httpResponse.disconnect()
  }

  val DefaultFormName = "my-form"
  def crudURLPrefix         (appForm: AppForm): String                                       = s"crud/${appForm.app}/${appForm.form}/"
  def crudURLPrefix         (provider: Provider, formName: String = DefaultFormName): String = crudURLPrefix(AppForm(provider.entryName, formName))
  def searchURLPrefix       (provider: Provider, formName: String = DefaultFormName): String = s"search/${provider.entryName}/$formName"
  def metadataURL           (provider: Provider, formName: String = DefaultFormName): String = s"form/${provider.entryName}/$formName"
  def distinctValueURLPrefix(provider: Provider, formName: String = DefaultFormName): String = s"distinct-values/${provider.entryName}/$formName"

  def post(
    url                      : String,
    version                  : Version,
    body                     : Body,
    credentials              : Option[Credentials] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): HttpResponse =
    useAndClose(request(url, POST, version, None, Some(body), credentials))(_.httpResponse)

  def put(
    url                      : String,
    version                  : Version,
    stage                    : Option[Stage],
    body                     : Body,
    credentials              : Option[Credentials] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): HttpResponse =
    useAndClose(request(url, PUT, version, stage, Some(body), credentials))(_.httpResponse)

  def del(
    url                      : String,
    version                  : Version,
    credentials              : Option[Credentials] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): HttpResponse =
    useAndClose(request(url, DELETE, version, None, None, credentials))(_.httpResponse)

  def get(
    url                      : String,
    version                  : Version,
    credentials              : Option[Credentials] = None,
    httpRange                : Option[HttpRange] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): (Int, Map[String, List[String]], Try[Array[Byte]]) =
    useAndClose(request(url, GET, version, None, None, credentials, httpRange)) { chr =>

      val httpResponse = chr.httpResponse
      val statusCode   = httpResponse.statusCode
      val headers      = httpResponse.headers

      val body =
        Try {
          val outputStream = new ByteArrayOutputStream
          IOUtils.copyStreamAndClose(httpResponse.content.stream, outputStream)
          outputStream.toByteArray
        }

      (statusCode, headers, body)
    }

  def lock(
    url                      : String,
    lockInfo                 : LockInfo,
    timeout                  : Int)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Int =
    Private.lockUnlock(LOCK, url, lockInfo, Some(timeout))

  def unlock(
    url                      : String,
    lockInfo                 : LockInfo)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Int =
    Private.lockUnlock(UNLOCK, url, lockInfo, None)

  private object Private {
    def lockUnlock(
      method                   : HttpMethod,
      url                      : String,
      lockInfo                 : LockInfo,
      timeout                  : Option[Int])(implicit
      logger                   : IndentedLogger,
      externalContext          : ExternalContext,
      coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
    ): Int = {
      val body = Some(XML(LockInfo.toOrbeonDom(lockInfo)))
      useAndClose(request(url, method, Version.Unspecified, None, body, None, None, timeout))(_.httpResponse.statusCode)
    }
  }

  // Used to test PersistenceApi (dataHistory, etc.)
  def connectPersistence(
    method                   : HttpMethod,
    path                     : String,
    requestBodyContent       : Option[StreamedContent] = None,
    formVersionOpt           : Option[Either[FormDefinitionVersion, SearchVersion]]
  )(implicit
    logger                   : IndentedLogger,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): ConnectionResult = {

    implicit val ec: ExternalContext = coreCrossPlatformSupport.externalContext

    val version = formVersionOpt.map {
      case Left (FormDefinitionVersion.Latest)      => Version.Unspecified
      case Left (FormDefinitionVersion.Specific(v)) => Version.Specific(v)
      case Right(SearchVersion.Unspecified)         => Version.Unspecified
      case Right(SearchVersion.All)                 => throw new IllegalArgumentException("`SearchVersion.All` not supported")
      case Right(SearchVersion.Specific(v))         => Version.Specific(v)
    }.getOrElse(Version.Unspecified)

    val chr = HttpCall.request(
      path        = path.stripPrefix(PersistenceBase),
      method      = method,
      version     = version,
      stage       = None,
      body        = requestBodyContent.map(sc => Binary(BufferedContent(sc)(toByteArray).body)),
      credentials = None
    )

    ConnectionResult(
      url                = path,
      statusCode         = chr.httpResponse.statusCode,
      headers            = chr.httpResponse.headers,
      content            = chr.httpResponse.content,
      dontHandleResponse = false
    )
  }
}
