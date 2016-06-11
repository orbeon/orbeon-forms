/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.rest

import java.io.ByteArrayOutputStream
import java.net.URI

import org.orbeon.dom.Document
import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils

import scala.util.Try

private object HttpRequest {

  private val PersistenceBase = "http://localhost:8080/orbeon/fr/service/persistence/"

  case class Credentials(username: String, roles: Set[String], group: String)

  sealed trait Body
  case class XML   (doc : Document   ) extends Body
  case class Binary(file: Array[Byte]) extends Body

  private def request(
    path        : String,
    method      : HttpMethod,
    version     : Version,
    body        : Option[Body],
    credentials : Option[Credentials])(implicit
    logger      : IndentedLogger
  ): ConnectionResult = {

    val documentURL = new URI(PersistenceBase + path)

    val headers = {

      import Version._

      val versionHeader = version match {
        case Unspecified             ⇒ Nil
        case Next                    ⇒ List(OrbeonFormDefinitionVersion → List("next"))
        case Specific(version)       ⇒ List(OrbeonFormDefinitionVersion → List(version.toString))
        case ForDocument(documentId) ⇒ List(OrbeonForDocumentId         → List(documentId))
      }

      val credentialHeaders = credentials.map(c ⇒ List(
        Headers.OrbeonUsernameLower → List(c.username),
        Headers.OrbeonGroupLower    → List(c.group),
        Headers.OrbeonRolesLower    → c.roles.to[List]
      )).to[List].flatten

      Connection.buildConnectionHeadersLowerIfNeeded(
        scheme           = documentURL.getScheme,
        hasCredentials   = false,
        customHeaders    = List(versionHeader, credentialHeaders).flatten.toMap,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = _ ⇒ None
      )
    }

    val contentType = body.map {
      case XML   (_) ⇒ "application/xml"
      case Binary(_) ⇒ "application/octet-stream"
    }

    val messageBody = body map {
      case XML   (doc ) ⇒ Dom4jUtils.domToString(doc).getBytes
      case Binary(file) ⇒ file
    }

    val content = messageBody map
      (StreamedContent.fromBytes(_, contentType))

    Connection(
      method      = method,
      url         = documentURL,
      credentials = None,
      content     = content,
      headers     = headers,
      loadState   = true,
      logBody     = false
    ).connect(
      saveState = true
    )
  }

  def put(url: String, version: Version, body: Body, credentials: Option[Credentials] = None)(implicit logger: IndentedLogger): Int =
    useAndClose(request(url, PUT, version, Some(body), credentials))(_.statusCode)

  def del(url: String, version: Version, credentials: Option[Credentials] = None)(implicit logger: IndentedLogger): Int =
    useAndClose(request(url, DELETE, version, None, credentials))(_.statusCode)

  def get(url: String, version: Version, credentials: Option[Credentials] = None)(implicit logger: IndentedLogger): (Int, Map[String, Seq[String]], Try[Array[Byte]]) =
    useAndClose(request(url, GET, version, None, credentials)) { cxr ⇒

      val statusCode = cxr.statusCode
      val headers    = cxr.headers

      val body =
        useAndClose(cxr.content.inputStream) { inputStream ⇒
          Try {
            val outputStream = new ByteArrayOutputStream
            NetUtils.copyStream(inputStream, outputStream)
            outputStream.toByteArray
          }
        }

      (statusCode, headers, body)
    }
}
