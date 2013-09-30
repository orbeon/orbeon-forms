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
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.test.{TestSupport, ResourceManagerTestBase}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.scalatest.junit.AssertionsForJUnit
import scala.Some

class RestApiTest extends ResourceManagerTestBase with AssertionsForJUnit with DatabaseConnection with TestSupport {

    val MySQLBase = "http://localhost:8080/orbeon/fr/service/mysql"
    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[RestApiTest]), "")

    def withOrbeonTables[T](block: java.sql.Connection ⇒ T) {
        asTomcat { connection =>
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

    private def http(url: String, method: String, body: Option[Document]): ConnectionResult = {
        val documentUrl = URLFactory.createURL(MySQLBase + url)
        val headers = {
            val dataSourceHeader  = Some("orbeon-datasource" → Array("mysql_test"))
            val contentTypeHeader = body map (_ ⇒ "content-type" → Array("application/xml"))
            val myHeaders = Seq(dataSourceHeader, contentTypeHeader).flatten.toMap
            Connection.buildConnectionHeaders(None, myHeaders, Option(Connection.getForwardHeaders))
        }
        val messageBody = body map Dom4jUtils.domToString map (_.getBytes)
        Connection(method, documentUrl, credentials = None, messageBody = messageBody, headers = headers,
            loadState = true, logBody = false).connect(saveState = true)
    }

    private def httpPut(url: String, body: Document): Unit = http(url, "PUT", Some(body)).close()
    private def httpGet(url: String): Document = {
        val connectionResult = http(url, "GET", None)
        val inputStream = connectionResult.getResponseInputStream
        try {
            Dom4jUtils.readDom4j(inputStream)
        } finally {
            inputStream.close()
            connectionResult.close()
        }
    }

    @Test def putGetTest(): Unit = {
        withOrbeonTables { connection =>
            val dataURL = "/crud/a/a/data/123/data.xml"
            val sent: Document = <gaga/>
            httpPut(dataURL, sent)
            val received = httpGet(dataURL)
            assertXMLDocuments(sent, received)
        }
    }
}
