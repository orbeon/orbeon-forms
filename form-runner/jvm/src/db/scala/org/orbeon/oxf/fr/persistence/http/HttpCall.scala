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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.URI
import org.orbeon.dom.Document
import org.orbeon.dom.io.XMLWriter
import org.orbeon.io.IOUtils
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.{Credentials, ExternalContext}
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.fr.permission.Operations
import org.orbeon.oxf.fr.persistence.relational.Version.Unspecified
import org.orbeon.oxf.fr.persistence.relational.rest.LockInfo
import org.orbeon.oxf.fr.persistence.relational.{Provider, StageHeader, Version}
import org.orbeon.oxf.fr.workflow.definitions20201.Stage
import org.orbeon.oxf.http.HttpMethod._
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpResponse, StreamedContent}
import org.orbeon.oxf.test.TestHttpClient
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Connection, ContentTypes, CoreCrossPlatformSupportTrait, IndentedLogger}
import org.orbeon.oxf.xml.dom.{Comparator, IOSupport}
import org.scalatest.Assertions._

import scala.util.Try

private[persistence] object HttpCall {

  private val PersistenceBase = "/fr/service/persistence/"

  sealed trait Body
  case class XML   (doc : Document   ) extends Body
  case class Binary(file: Array[Byte]) extends Body

  case class SolicitedRequest(
    path        : String,
    method      : HttpMethod,
    version     : Version             = Unspecified,
    stage       : Option[Stage]       = None,
    body        : Option[Body]        = None,
    credentials : Option[Credentials] = None,
    timeout     : Option[Int]         = None
  )

  case class ExpectedResponse(
    code        : Int,
    operations  : Option[Operations]  = None,
    formVersion : Option[Int]         = None,
    body        : Option[Body]        = None
  )

  def assertCall(
    actualRequest            : SolicitedRequest,
    expectedResponse         : ExpectedResponse)(implicit
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

      // Check response code
      assert(actualResponse.statusCode == expectedResponse.code)

      // Check operations
      expectedResponse.operations.foreach { expectedOperations =>
        assert(Operations.parseFromHeaders(actualHeaders).contains(expectedOperations))
      }

      // Check form version
      val resultFormVersion = actualHeaders.get(Version.OrbeonFormDefinitionVersionLower).map(_.head).map(_.toInt)
      assert(expectedResponse.formVersion == resultFormVersion)

      // Check body
      expectedResponse.body.foreach { expectedBody =>
        val actualBody = {
          val outputStream = new ByteArrayOutputStream
          IOUtils.copyStreamAndClose(actualResponse.content.inputStream, outputStream)
          outputStream.toByteArray
        }
        expectedBody match {
          case HttpCall.XML(expectedDoc) =>
            val resultDoc  = IOSupport.readOrbeonDom(new ByteArrayInputStream(actualBody))
            if (! Comparator.compareDocumentsIgnoreNamespacesInScope(resultDoc, expectedDoc))
              assert(
                resultDoc.getRootElement.serializeToString(XMLWriter.PrettyFormat) ===
                  expectedDoc.getRootElement.serializeToString(XMLWriter.PrettyFormat)
              )
          case HttpCall.Binary(expectedFile) =>
            assert(actualBody == expectedFile)
        }
      }
    }
  }

  private def request(
    path                     : String,
    method                   : HttpMethod,
    version                  : Version,
    stage                    : Option[Stage],
    body                     : Option[Body],
    credentials              : Option[Credentials],
    timeout                  : Option[Int] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): ClosableHttpResponse = {

    val documentURL = PersistenceBase.appendSlash + path.dropStartingSlash

    val headersCapitalized = {

      import Version._

      val timeoutHeader = timeout.map(t => Headers.Timeout -> List(Headers.TimeoutValuePrefix + t.toString))
      val versionHeader = version match {
        case Unspecified             => None
        case Next                    => Some(OrbeonFormDefinitionVersion -> List("next"))
        case Specific(version)       => Some(OrbeonFormDefinitionVersion -> List(version.toString))
        case ForDocument(documentId) => Some(OrbeonForDocumentId         -> List(documentId))
      }
      val stageHeader   = stage.map(_.name).map(StageHeader.HeaderName -> List(_))
      val headers = (timeoutHeader.toList ++ versionHeader.toList ++ stageHeader.toList).toMap

      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = new URI(documentURL),
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

  private val FormName = "my-form"
  def crudURLPrefix   (provider: Provider) = s"crud/${provider.entryName}/$FormName/"
  def searchURLPrefix (provider: Provider) = s"search/${provider.entryName}/$FormName"
  def metadataURL     (provider: Provider) = s"form/${provider.entryName}/$FormName"

  def post(
    url                      : String,
    version                  : Version,
    body                     : Body,
    credentials              : Option[Credentials] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Int =
    useAndClose(request(url, POST, version, None, Some(body), credentials))(_.httpResponse.statusCode)

  def put(
    url                      : String,
    version                  : Version,
    stage                    : Option[Stage],
    body                     : Body,
    credentials              : Option[Credentials] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Int =
    useAndClose(request(url, PUT, version, stage, Some(body), credentials))(_.httpResponse.statusCode)

  def del(
    url                      : String,
    version                  : Version,
    credentials              : Option[Credentials] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Int =
    useAndClose(request(url, DELETE, version, None, None, credentials))(_.httpResponse.statusCode)

  def get(
    url                      : String,
    version                  : Version,
    credentials              : Option[Credentials] = None)(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): (Int, Map[String, List[String]], Try[Array[Byte]]) =
    useAndClose(request(url, GET, version, None, None, credentials)) { chr =>

      val httpResponse = chr.httpResponse
      val statusCode   = httpResponse.statusCode
      val headers      = httpResponse.headers

      val body =
        Try {
          val outputStream = new ByteArrayOutputStream
          IOUtils.copyStreamAndClose(httpResponse.content.inputStream, outputStream)
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
      useAndClose(request(url, method, Version.Unspecified, None, body, None, timeout))(_.httpResponse.statusCode)
    }
  }
}
