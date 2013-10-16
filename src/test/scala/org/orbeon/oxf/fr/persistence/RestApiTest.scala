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
import org.junit.Test
import org.orbeon.oxf.fr.relational._
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.test.{TestSupport, ResourceManagerTestBase}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.scalatest.junit.AssertionsForJUnit
import util.Try
import ScalaUtils._
import org.orbeon.oxf.fr.relational.{Specific, Next, Latest, Version}

class RestApiTest extends ResourceManagerTestBase with AssertionsForJUnit with DatabaseConnection with TestSupport {

    val MySQLBase = "http://localhost:8080/orbeon/fr/service/mysql"
    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[RestApiTest]), "")

    def withOrbeonTables[T](block: java.sql.Connection ⇒ T) {
        asTomcat { connection ⇒
            val statement = connection.createStatement
            try {
                // Create tables
                val createDDL = readSQL(s"$Base/mysql-4_4.sql")
                createDDL foreach statement.executeUpdate

                // Run the interesting code
                block(connection)
            } finally {
                // Clean-up database dropping tables
                (getTableNames(connection)
                    map ("drop table " + _)
                    foreach statement.executeUpdate)
            }
        }
    }

    private def http(url: String, method: String, version: Version, body: Option[Document]): ConnectionResult = {
        val documentUrl = URLFactory.createURL(MySQLBase + url)
        val headers = {
            val dataSourceHeader  = Some("Orbeon-Datasource" → Array("mysql_test"))
            val contentTypeHeader = body map (_ ⇒ "Content-Type" → Array("application/xml"))
            val versionHeader =  version match {
                case Latest                  ⇒ None
                case Next                    ⇒ Some("Orbeon-Form-Definition-Version" → Array("next"))
                case Specific(version)       ⇒ Some("Orbeon-Form-Definition-Version" → Array(version.toString))
                case ForDocument(documentId) ⇒ Some("Orbeon-For-Document-Id" → Array(documentId))
            }
            val myHeaders = Seq(dataSourceHeader, contentTypeHeader, versionHeader).flatten.toMap
            Connection.buildConnectionHeaders(None, myHeaders, Option(Connection.getForwardHeaders))
        }
        val messageBody = body map Dom4jUtils.domToString map (_.getBytes)
        Connection(method, documentUrl, credentials = None, messageBody = messageBody, headers = headers,
            loadState = true, logBody = false).connect(saveState = true)
    }

    private def httpPut(url: String, version: Version, body: Document): Unit = http(url, "PUT", version, Some(body)).close()
    private def httpGet(url: String, version: Version): Try[Document] =
        useAndClose(http(url, "GET", version, None)) { connectionResult ⇒
            useAndClose(connectionResult.getResponseInputStream) { inputStream ⇒
                Try(Dom4jUtils.readDom4j(inputStream))
            }
        }

    @Test def formDefinitionVersionTest(): Unit = {
        withOrbeonTables { connection =>
            val FormURL = "/crud/acme/address/form/form.xml"

            // First time we put with "latest"
            val first: Document = <gaga1/>
            httpPut(FormURL, Latest, first)
            assertXMLDocuments(first, httpGet(FormURL, Specific(1)).get)
            assertXMLDocuments(first, httpGet(FormURL, Latest     ).get)
            assert(httpGet(FormURL, Specific(2)).isFailure)

            // Put again with "latest" updates the current version
            val second: Document = <gaga2/>
            httpPut(FormURL, Latest, second)
            assertXMLDocuments(second, httpGet(FormURL, Specific(1)).get)
            assertXMLDocuments(second, httpGet(FormURL, Latest     ).get)
            assert(httpGet(FormURL, Specific(2)).isFailure)

            // Put with "next" to get two versions
            val third: Document = <gaga3/>
            httpPut(FormURL, Next, third)
            assertXMLDocuments(second, httpGet(FormURL, Specific(1)).get)
            assertXMLDocuments(third , httpGet(FormURL, Specific(2)).get)
            assertXMLDocuments(third , httpGet(FormURL, Latest     ).get)
            assert(httpGet(FormURL, Specific(3)).isFailure)

//            // Put a specific version
//            val fourth: Document = <gaga4/>
//            httpPut(FormURL, Specific(1), fourth)
//            assertXMLDocuments(fourth, httpGet(FormURL, Specific(1)).get)
//            assertXMLDocuments(third , httpGet(FormURL, Specific(2)).get)
//            assertXMLDocuments(third , httpGet(FormURL, Latest     ).get)
//            assert(httpGet(FormURL, Specific(3)).isFailure)
        }
    }
}
