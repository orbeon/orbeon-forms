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
import scala.xml.Elem

class RestApiTest extends ResourceManagerTestBase with AssertionsForJUnit with DatabaseConnection with TestSupport {

    val MySQLBase = "http://localhost:8080/orbeon/fr/service/mysql-ng"
    val AllOperations = Set("create", "read", "update", "delete")
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

    private object Http {

        case class Credentials(username: String, roles: Set[String], group: String)

        private def request(url: String, method: String, version: Version, body: Option[Document]): ConnectionResult = {
            val documentUrl = URLFactory.createURL(MySQLBase + url)
            val headers = {
                val dataSourceHeader  = Some("Orbeon-Datasource" → Array("mysql_tomcat"))
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

        def put(url: String, version: Version, body: Document, credentials: Option[Credentials] = None): Integer = {
            val result = request(url, "PUT", version, Some(body))
            val code = result.statusCode
            result.close()
            code
        }

        def get(url: String, version: Version, credentials: Option[Credentials] = None): (Integer, Map[String, Seq[String]], Try[Document]) =
            useAndClose(request(url, "GET", version, None)) { connectionResult ⇒
                useAndClose(connectionResult.getResponseInputStream) { inputStream ⇒
                    val statusCode = connectionResult.statusCode
                    val headers = connectionResult.responseHeaders.toMap
                    val body = Try(Dom4jUtils.readDom4j(inputStream))
                    (statusCode, headers, body)
                }
            }
    }

    private object Assert {

        sealed trait Expected
        case   class ExpectedDoc (doc:  Elem, operations: Set[String]) extends Expected
        case   class ExpectedCode(code: Integer) extends Expected

        def get(url: String, version: Version, expected: Expected, credentials: Option[Http.Credentials] = None): Unit = {
            val (resultCode, headers, resultDoc) = Http.get(url, version, credentials)
            expected match {
                case ExpectedDoc(expectedDoc, expectedOperations) ⇒
                    assert(resultCode === 200)
                    assertXMLDocuments(resultDoc.get, expectedDoc)
                    val resultOperationsString = headers.get("orbeon-operations").map(_.head)
                    val resultOperationsSet = resultOperationsString.map(ScalaUtils.split[Set](_)).getOrElse(Set.empty)
                    assert(expectedOperations === resultOperationsSet)
                case ExpectedCode(expectedCode) ⇒
                    assert(resultCode === expectedCode)
            }
        }

        def put(url: String, version: Version, body: Elem, expectedCode: Integer, credentials: Option[Http.Credentials] = None): Unit = {
            val actualCode = Http.put(url, version, body, credentials)
            assert(actualCode === expectedCode)
        }
    }


    /**
     * Test new form versioning introduced in 4.5, for form definitions.
     */
    @Test def formDefinitionVersionTest(): Unit = {
        withOrbeonTables { connection =>
            val FormURL = "/crud/acme/address/form/form.xml"

            // First time we put with "latest"
            val first = <gaga1/>
            Assert.put(FormURL, Latest, first, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc (first, Set.empty))
            Assert.get(FormURL, Latest     , Assert.ExpectedDoc (first, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedCode(404))

            // Put again with "latest" updates the current version
            val second = <gaga2/>
            Assert.put(FormURL, Latest, second, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(second, Set.empty))
            Assert.get(FormURL, Latest     , Assert.ExpectedDoc(second, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedCode(404))

            // Put with "next" to get two versions
            val third = <gaga3/>
            Assert.put(FormURL, Next, third, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(second, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Latest     , Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Specific(3), Assert.ExpectedCode(404))

            // Put a specific version
            val fourth = <gaga4/>
            Assert.put(FormURL, Specific(1), fourth, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(fourth, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Latest     , Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Specific(3), Assert.ExpectedCode(404))
        }
    }

    /**
     * Test new form versioning introduced in 4.5, for form data.
     */
    @Test def formDataVersionTest(): Unit = {
        withOrbeonTables { connection =>
            val DataURL = "/crud/acme/address/data/123/data.xml"

            // Storing for specific form version
            val first = <gaga1/>
            Assert.put(DataURL, Specific(1), first, 201)
            Assert.get(DataURL, Specific(1), Assert.ExpectedDoc(first, AllOperations))
            Assert.get(DataURL, Latest     , Assert.ExpectedDoc(first, AllOperations))

            // Version must be specified when storing data
            Assert.put(DataURL, Latest            , first, 400)
            Assert.put(DataURL, Next              , first, 400)
            Assert.put(DataURL, ForDocument("123"), first, 400)
        }
    }

    /**
     *
     */
    @Test def permissionsTest(): Unit = {

        withOrbeonTables { connection =>
            import Permissions._

            def formDefinitionWithPermissions(permissions: Permissions): Elem =
                <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
                    <xh:head>
                        <xf:model id="fr-form-model">
                            <xf:instance id="fr-form-metadata">
                                <metadata>
                                    { serialize(permissions).getOrElse("") }
                                </metadata>
                            </xf:instance>
                        </xf:model>
                    </xh:head>
                </xh:html>

            val FormURL = "/crud/acme/address/form/form.xml"
            val DataURL = "/crud/acme/address/data/123/data.xml"
            val data    = <data/>

            // Anonymous: no permission defined
            Assert.put(FormURL, Latest, formDefinitionWithPermissions(None), 201)
            Assert.put(DataURL, Specific(1), data, 201)
            Assert.get(DataURL, Latest, Assert.ExpectedDoc(data, AllOperations))

            // Anonymous: create and read
            Assert.put(FormURL, Latest, formDefinitionWithPermissions(Some(Seq(Permission(Anyone, Set("read", "create"))))), 201)
            Assert.get(DataURL, Latest, Assert.ExpectedDoc(data, Set("create", "read")))

            // Anonymous: just create, then can't read data
            Assert.put(FormURL, Latest, formDefinitionWithPermissions(Some(Seq(Permission(Anyone, Set("create"))))), 201)
            Assert.get(DataURL, Latest, Assert.ExpectedCode(403))

        }
    }
}
