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

import java.io.ByteArrayInputStream

import org.orbeon.oxf.fr.persistence.relational.Version
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.oxf.util.{IndentedLogger, StringUtils}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils

private object HttpAssert extends XMLSupport {

  sealed trait Expected
  case   class ExpectedBody(body: HttpRequest.Body, operations: Set[String], formVersion: Option[Int]) extends Expected
  case   class ExpectedCode(code: Int) extends Expected

  def get(
    url         : String,
    version     : Version,
    expected    : Expected,
    credentials : Option[HttpRequest.Credentials] = None)(implicit
    logger      : IndentedLogger
  ): Unit = {

    val (resultCode, headers, resultBody) = {
      val (resultCode, headers, resultBody) = HttpRequest.get(url, version, credentials)
      val lowerCaseHeaders = headers.map{case (header, value) ⇒ header.toLowerCase → value}
      (resultCode, lowerCaseHeaders, resultBody)
    }

    expected match {
      case ExpectedBody(body, expectedOperations, expectedFormVersion) ⇒
        assert(resultCode === 200)
        // Check body
        body match {
          case HttpRequest.XML(expectedDoc) ⇒
            val resultDoc = Dom4jUtils.readDom4j(new ByteArrayInputStream(resultBody.get))
            assertXMLDocumentsIgnoreNamespacesInScope(resultDoc, expectedDoc)
          case HttpRequest.Binary(expectedFile) ⇒
            assert(resultBody.get === expectedFile)
        }
        // Check operations
        val resultOperationsString = headers.get("orbeon-operations").map(_.head)
        val resultOperationsSet = resultOperationsString.map(StringUtils.split[Set](_)).getOrElse(Set.empty)
        assert(expectedOperations === resultOperationsSet)
        // Check form version
        val resultFormVersion = headers.get(Version.OrbeonFormDefinitionVersionLower).map(_.head).map(_.toInt)
        assert(expectedFormVersion === resultFormVersion)
      case ExpectedCode(expectedCode) ⇒
        assert(resultCode === expectedCode)
    }
  }

  def put(
    url          : String,
    version      : Version,
    body         : HttpRequest.Body,
    expectedCode : Int,
    credentials  : Option[HttpRequest.Credentials] = None)(implicit
    logger       : IndentedLogger
  ): Unit = {
    val actualCode = HttpRequest.put(url, version, body, credentials)
    assert(actualCode === expectedCode)
  }

  def del(
    url          : String,
    version      : Version,
    expectedCode : Int,
    credentials  : Option[HttpRequest.Credentials] = None)(implicit
    logger       : IndentedLogger
  ): Unit = {
    val actualCode = HttpRequest.del(url, version, credentials)
    assert(actualCode === expectedCode)
  }
}
