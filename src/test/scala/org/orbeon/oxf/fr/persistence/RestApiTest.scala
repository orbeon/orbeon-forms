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
import org.orbeon.oxf.fr.relational.{Specific, Next, Unspecified, Version}
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

        def del(url: String, version: Version, expectedCode: Integer, credentials: Option[Http.Credentials] = None): Unit = {
            val actualCode = Http.del(url, version, credentials)
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
            Assert.put(FormURL, Unspecified, first, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc (first, Set.empty))
            Assert.get(FormURL, Unspecified, Assert.ExpectedDoc (first, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedCode(404))
            Assert.del(FormURL, Specific(2), 404)

            // Put again with "latest" updates the current version
            val second = <gaga2/>
            Assert.put(FormURL, Unspecified, second, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(second, Set.empty))
            Assert.get(FormURL, Unspecified, Assert.ExpectedDoc(second, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedCode(404))

            // Put with "next" to get two versions
            val third = <gaga3/>
            Assert.put(FormURL, Next, third, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(second, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Unspecified, Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Specific(3), Assert.ExpectedCode(404))

            // Put a specific version
            val fourth = <gaga4/>
            Assert.put(FormURL, Specific(1), fourth, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(fourth, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Unspecified, Assert.ExpectedDoc(third,  Set.empty))
            Assert.get(FormURL, Specific(3), Assert.ExpectedCode(404))

            // Delete the latest version
            Assert.del(FormURL, Unspecified, 204)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(fourth, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedCode(404))
            Assert.get(FormURL, Unspecified, Assert.ExpectedDoc(fourth, Set.empty))

            // After a delete the version number is reused
            val fifth = <gaga5/>
            Assert.put(FormURL, Next, fifth, 201)
            Assert.get(FormURL, Specific(1), Assert.ExpectedDoc(fourth, Set.empty))
            Assert.get(FormURL, Specific(2), Assert.ExpectedDoc(fifth,  Set.empty))
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
            Assert.get(DataURL, Unspecified, Assert.ExpectedDoc(first, AllOperations))
            Assert.del(DataURL, Unspecified, 204)
            Assert.get(DataURL, Specific(1), Assert.ExpectedCode(404))

            // Version must be specified when storing data
            Assert.put(DataURL, Unspecified       , first, 400)
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
            val data    = <data/>
            val clerk   = Some(Http.Credentials("tom", Set("clerk"  ), "clerk"))
            val manager = Some(Http.Credentials("jim", Set("manager"), "manager"))
            val admin   = Some(Http.Credentials("tim", Set("admin"  ), "admin"))

            {
                val DataURL = "/crud/acme/address/data/123/data.xml"

                // Anonymous: no permission defined
                Assert.put(FormURL, Unspecified, formDefinitionWithPermissions(None), 201)
                Assert.put(DataURL, Specific(1), data, 201)
                Assert.get(DataURL, Unspecified, Assert.ExpectedDoc(data, AllOperations))

                // Anonymous: create and read
                Assert.put(FormURL, Unspecified, formDefinitionWithPermissions(Some(Seq(Permission(Anyone, Set("read", "create"))))), 201)
                Assert.get(DataURL, Unspecified, Assert.ExpectedDoc(data, Set("create", "read")))

                // Anonymous: just create, then can't read data
                Assert.put(FormURL, Unspecified, formDefinitionWithPermissions(Some(Seq(Permission(Anyone, Set("create"))))), 201)
                Assert.get(DataURL, Unspecified, Assert.ExpectedCode(403))
            }
            {
                val DataURL = "/crud/acme/address/data/456/data.xml"

                // More complex permissions based on roles
                Assert.put(FormURL, Unspecified, formDefinitionWithPermissions(Some(Seq(
                    Permission(Anyone,          Set("create")),
                    Permission(Role("clerk"),   Set("read")),
                    Permission(Role("manager"), Set("read update")),
                    Permission(Role("admin"),   Set("read update delete"))
                ))), 201)
                Assert.put(DataURL, Specific(1), data, 201)

                // Everyone can read
                Assert.get(DataURL, Unspecified, Assert.ExpectedCode(403))
                Assert.get(DataURL, Unspecified, Assert.ExpectedDoc(data, Set("create", "read")), clerk)
                Assert.get(DataURL, Unspecified, Assert.ExpectedDoc(data, Set("create", "read", "update")), manager)
                Assert.get(DataURL, Unspecified, Assert.ExpectedDoc(data, Set("create", "read", "update", "delete")), admin)

                // Only managers and admins can update
                Assert.put(DataURL, Unspecified, data, 403)
                Assert.put(DataURL, Unspecified, data, 403, clerk)
                Assert.put(DataURL, Unspecified, data, 201, manager)
                Assert.put(DataURL, Unspecified, data, 201, admin)

                // Only admins can delete
                Assert.del(DataURL, Unspecified, 403)
                Assert.del(DataURL, Unspecified, 403, clerk)
                Assert.del(DataURL, Unspecified, 403, manager)
                Assert.del(DataURL, Unspecified, 204, admin)

                // Status code when deleting non-existent data depends on permissions
                Assert.del(DataURL, Unspecified, 403)
                Assert.del(DataURL, Unspecified, 404, clerk)
                Assert.del(DataURL, Unspecified, 404, manager)
                Assert.del(DataURL, Unspecified, 404, admin)
            }
        }
    }
}
