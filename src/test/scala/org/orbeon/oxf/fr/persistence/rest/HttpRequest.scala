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
import org.dom4j.Document
import org.orbeon.oxf.fr.relational._
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.util.Try
import org.orbeon.oxf.fr.FormRunnerPermissions._

private object HttpRequest {

    private val PersistenceBase = "http://localhost:8080/orbeon/fr/service/persistence/"

    case class Credentials(username: String, roles: Set[String], group: String)

    sealed trait Body
    case class XML   (doc : Document   ) extends Body
    case class Binary(file: Array[Byte]) extends Body

    private def request(url: String, method: String, version: Version, body: Option[Body], credentials: Option[Credentials])(implicit logger: IndentedLogger): ConnectionResult = {
        val documentUrl = URLFactory.createURL(PersistenceBase + url)
        val headers = {
            val contentTypeHeader = body.map {
                    case XML   (_) ⇒ "application/xml"
                    case Binary(_) ⇒ "application/octet-stream"
                }.map("Content-Type" → Array(_)).toSeq
            val versionHeader =  version match {
                case Unspecified             ⇒ Nil
                case Next                    ⇒ Seq("Orbeon-Form-Definition-Version" → Array("next"))
                case Specific(version)       ⇒ Seq("Orbeon-Form-Definition-Version" → Array(version.toString))
                case ForDocument(documentId) ⇒ Seq("Orbeon-For-Document-Id"         → Array(documentId))
            }
            val credentialHeaders = credentials.map(c ⇒ Seq(
                OrbeonUsernameHeaderName → Array(c.username),
                OrbeonGroupHeaderName    → Array(c.group),
                OrbeonRolesHeaderName    → Array(c.roles.mkString(","))// split on commas as for other HTTP headers
            )).toSeq.flatten
            val myHeaders = Seq(contentTypeHeader, versionHeader, credentialHeaders).flatten.toMap
            Connection.buildConnectionHeaders(None, myHeaders, Option(Connection.getForwardHeaders))
        }
        val messageBody = body map {
            case XML   (doc ) ⇒ Dom4jUtils.domToString(doc).getBytes
            case Binary(file) ⇒ file
        }
        Connection(method, documentUrl, credentials = None, messageBody = messageBody, headers = headers,
            loadState = true, logBody = false).connect(saveState = true)
    }

    def put(url: String, version: Version, body: Body, credentials: Option[Credentials] = None)(implicit logger: IndentedLogger): Int =
        useAndClose(request(url, "PUT", version, Some(body), credentials))(_.statusCode)

    def del(url: String, version: Version, credentials: Option[Credentials] = None)(implicit logger: IndentedLogger): Int =
        useAndClose(request(url, "DELETE", version, None, credentials))(_.statusCode)

    def get(url: String, version: Version, credentials: Option[Credentials] = None)(implicit logger: IndentedLogger): (Int, Map[String, Seq[String]], Try[Array[Byte]]) =
        useAndClose(request(url, "GET", version, None, credentials)) { connectionResult ⇒
            useAndClose(connectionResult.getResponseInputStream) { inputStream ⇒
                val statusCode = connectionResult.statusCode
                val headers = connectionResult.responseHeaders.toMap
                val body = Try({
                    val outputStream = new ByteArrayOutputStream
                    NetUtils.copyStream(inputStream, outputStream)
                    outputStream.toByteArray
                })
                (statusCode, headers, body)
            }
        }
}
