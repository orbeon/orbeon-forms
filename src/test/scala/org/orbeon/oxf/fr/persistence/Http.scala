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
package org.orbeon.oxf.fr.persistence

import org.dom4j.Document
import org.orbeon.oxf.fr.relational.ForDocument
import org.orbeon.oxf.fr.relational.Specific
import org.orbeon.oxf.fr.relational._
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{LoggerFactory, IndentedLogger, Connection, ConnectionResult}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.Array
import scala.Some
import scala.util.Try

private object Http {

    private val MySQLBase = "http://localhost:8080/orbeon/fr/service/mysql-ng"
    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[RestApiTest]), "")

    case class Credentials(username: String, roles: Set[String], group: String)

    private def request(url: String, method: String, version: Version, body: Option[Document], credentials: Option[Credentials]): ConnectionResult = {
        val documentUrl = URLFactory.createURL(MySQLBase + url)
        val headers = {
            val dataSourceHeader  = Seq("Orbeon-Datasource" → Array("mysql_tomcat"))
            val contentTypeHeader = body.map(_ ⇒ "Content-Type" → Array("application/xml")).toSeq
            val versionHeader =  version match {
                case Unspecified             ⇒ Nil
                case Next                    ⇒ Seq("Orbeon-Form-Definition-Version" → Array("next"))
                case Specific(version)       ⇒ Seq("Orbeon-Form-Definition-Version" → Array(version.toString))
                case ForDocument(documentId) ⇒ Seq("Orbeon-For-Document-Id" → Array(documentId))
            }
            val credentialHeaders = credentials.map( c ⇒ Seq(
                "Orbeon-Username" → Array(c.username),
                "Orbeon-Group"    → Array(c.group),
                "Orbeon-Roles"    → Array(c.roles.mkString(" "))
            )).toSeq.flatten
            val myHeaders = Seq(dataSourceHeader, contentTypeHeader, versionHeader, credentialHeaders).flatten.toMap
            Connection.buildConnectionHeaders(None, myHeaders, Option(Connection.getForwardHeaders))
        }
        val messageBody = body map Dom4jUtils.domToString map (_.getBytes)
        Connection(method, documentUrl, credentials = None, messageBody = messageBody, headers = headers,
            loadState = true, logBody = false).connect(saveState = true)
    }

    def put(url: String, version: Version, body: Document, credentials: Option[Credentials] = None): Integer =
        useAndClose(request(url, "PUT", version, Some(body), credentials))(_.statusCode)

    def del(url: String, version: Version, credentials: Option[Credentials] = None): Integer =
        useAndClose(request(url, "DELETE", version, None, credentials))(_.statusCode)

    def get(url: String, version: Version, credentials: Option[Credentials] = None): (Integer, Map[String, Seq[String]], Try[Document]) =
        useAndClose(request(url, "GET", version, None, credentials)) { connectionResult ⇒
            useAndClose(connectionResult.getResponseInputStream) { inputStream ⇒
                val statusCode = connectionResult.statusCode
                val headers = connectionResult.responseHeaders.toMap
                val body = Try(Dom4jUtils.readDom4j(inputStream))
                (statusCode, headers, body)
            }
        }
}

