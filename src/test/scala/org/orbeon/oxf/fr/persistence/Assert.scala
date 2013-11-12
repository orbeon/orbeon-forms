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

import org.orbeon.oxf.fr.relational.Version
import org.orbeon.oxf.test.TestSupport
import org.orbeon.oxf.util.ScalaUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import java.io.ByteArrayInputStream

private object Assert extends TestSupport {

    sealed trait Expected
    case   class ExpectedBody(body: Http.Body, operations: Set[String]) extends Expected
    case   class ExpectedCode(code: Integer) extends Expected

    def get(url: String, version: Version, expected: Expected, credentials: Option[Http.Credentials] = None): Unit = {
        val (resultCode, headers, resultBody) = Http.get(url, version, credentials)
        expected match {
            case ExpectedBody(body, expectedOperations) ⇒
                assert(resultCode === 200)
                body match {
                    case Http.XML(expectedDoc) ⇒
                        val resultDoc = Dom4jUtils.readDom4j(new ByteArrayInputStream(resultBody.get))
                        assertXMLDocuments(resultDoc, expectedDoc)
                    case Http.Binary(expectedFile) ⇒
                        assert(resultBody.get === expectedFile)
                }
                val resultOperationsString = headers.get("orbeon-operations").map(_.head)
                val resultOperationsSet = resultOperationsString.map(ScalaUtils.split[Set](_)).getOrElse(Set.empty)
                assert(expectedOperations === resultOperationsSet)
            case ExpectedCode(expectedCode) ⇒
                assert(resultCode === expectedCode)
        }
    }

    def put(url: String, version: Version, body: Http.Body, expectedCode: Integer, credentials: Option[Http.Credentials] = None): Unit = {
        val actualCode = Http.put(url, version, body, credentials)
        assert(actualCode === expectedCode)
    }

    def del(url: String, version: Version, expectedCode: Integer, credentials: Option[Http.Credentials] = None): Unit = {
        val actualCode = Http.del(url, version, credentials)
        assert(actualCode === expectedCode)
    }
}
