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

import org.orbeon.oxf.externalcontext.{Credentials, SafeRequestContext}
import org.orbeon.oxf.fr.FormRunnerPersistence.{OrbeonHashAlogrithm, OrbeonHashValue}
import org.orbeon.oxf.fr.Version
import org.orbeon.oxf.fr.permission.{Operations, SpecificOperations}
import org.orbeon.oxf.fr.persistence.relational.StageHeader
import org.orbeon.oxf.fr.persistence.relational.rest.LockInfo
import org.orbeon.oxf.fr.workflow.definitions20201.Stage
import org.orbeon.oxf.http.{Headers, HttpRange, StatusCode}
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xml.dom.IOSupport
import org.scalactic.Equality

import java.io.ByteArrayInputStream


private[persistence] object HttpAssert extends XMLSupport {

  implicit val operationsEquality: Equality[Operations] =
    (left: Operations, right: Any) => (left, right) match {
      case (SpecificOperations(leftSpecific), SpecificOperations(rightSpecific)) =>
        leftSpecific == rightSpecific
      case _ =>
        left == right
    }

  sealed trait Expected
  case   class ExpectedBody(
    body              : HttpCall.Body,
    operations        : Operations,
    formVersion       : Option[Int],
    stage             : Option[Stage] = None,
    contentRangeHeader: Option[String] = None,
    etag              : Option[String] = None,
    hashAlgorithm     : Option[String] = None,
    hashValue         : Option[String] = None,
    statusCode        : Int = StatusCode.Ok
  ) extends Expected
  case   class ExpectedCode(code: Int) extends Expected

  private def assertETag(headers: Map[String, List[String]], expectedETagOpt: Option[String]): Unit =
    expectedETagOpt.foreach { expectedEtag =>
      val etagValues = Headers.allItemsIgnoreCase(headers, Headers.ETag).toSet
      expectedEtag match {
        case "*"   => assert(etagValues.nonEmpty)
        case value => assert(etagValues.contains(value))
      }
    }

  def get(
    url           : String,
    version       : Version,
    expected      : Expected,
    credentials   : Option[Credentials] = None,
    httpRange     : Option[HttpRange] = None)(implicit
    logger        : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Unit = {

    val (resultCode, headers, resultBody) = {
      val (resultCode, headers, resultBody) = HttpCall.get(url, version, credentials, httpRange)
      val lowerCaseHeaders = headers.map{case (header, value) => header.toLowerCase -> value}
      (resultCode, lowerCaseHeaders, resultBody)
    }

    expected match {
      case expectedBody@ExpectedBody(_, _, _, _, _, _, _, _, _) =>

        assert(resultCode == expectedBody.statusCode)
        // Check body
        expectedBody.body match {
          case HttpCall.XML(expectedDoc) =>
            val resultDoc = IOSupport.readOrbeonDom(new ByteArrayInputStream(resultBody.get))
            assertXMLDocumentsIgnoreNamespacesInScope(expectedDoc, resultDoc)
          case HttpCall.Binary(expectedFile) =>
            // Compare `java.lang.Array[Byte]` with `sameElements`, as `==` always returns `false` on `java.lang.Array`
            assert(resultBody.get sameElements expectedFile)
        }
        // Check operations
        assert(Operations.parseFromHeaders(headers).getOrElse(Operations.None) == expectedBody.operations)
        // Check form version
        val resultFormVersion = Headers.firstItemIgnoreCase(headers, Version.OrbeonFormDefinitionVersion).map(_.toInt)
        assert(expectedBody.formVersion == resultFormVersion)
        // Check stage
        val resultStage = headers.get(StageHeader.HeaderNameLower).map(_.head).map(Stage.apply(_, ""))
        assert(expectedBody.stage == resultStage)
        // Check content range header
        expectedBody.contentRangeHeader.foreach { expectedContentRangeHeader =>
          val resultContentRangeHeader = headers.get(Headers.ContentRange.toLowerCase).map(_.head)
          assert(resultContentRangeHeader.contains(expectedContentRangeHeader))
        }
        // Check ETag header if specified
        assertETag(headers, expectedBody.etag)
        // Check hash algorithm header if specified
        expectedBody.hashAlgorithm.foreach { expectedHashAlgorithm =>
          val hashAlgorithmHeader = headers.get(OrbeonHashAlogrithm.toLowerCase)
          assert(hashAlgorithmHeader.contains(List(expectedHashAlgorithm)))
        }
        // Check hash value header if specified
        expectedBody.hashValue.foreach { expectedHashValue =>
          val hashValueHeader = headers.get(OrbeonHashValue.toLowerCase)
          assert(hashValueHeader.contains(List(expectedHashValue)))
        }

      case ExpectedCode(expectedCode) =>
        assert(resultCode == expectedCode)
    }
  }

  def post(
    url           : String,
    version       : Version,
    body          : HttpCall.Body,
    expectedCode  : Int,
    credentials   : Option[Credentials] = None
  )(implicit
    logger        : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Unit = {
    val actualCode = HttpCall.post(url, version, body, credentials).statusCode
    assert(actualCode == expectedCode)
  }

  def put(
    url           : String,
    version       : Version,
    body          : HttpCall.Body,
    expectedCode  : Int,
    credentials   : Option[Credentials] = None,
    stage         : Option[Stage]       = None,
    ifMatch       : Option[String]      = None,
    hashAlgorithm : Option[String]      = None,
    hashValue     : Option[String]      = None,
    expectedETag  : Option[String]      = None
  )(implicit
    logger        : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Unit = {
    val httpResponse = HttpCall.put(url, version, stage, body, credentials, ifMatch, hashAlgorithm, hashValue)
    assert(httpResponse.statusCode == expectedCode)
    assertETag(httpResponse.headers, expectedETag)
  }

  def del(
    url           : String,
    version       : Version,
    expectedCode  : Int,
    credentials   : Option[Credentials] = None
  )(implicit
    logger        : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Unit = {
    val actualCode = HttpCall.del(url, version, credentials).statusCode
    assert(actualCode == expectedCode)
  }

  def lock(
    url          : String,
    lockInfo     : LockInfo,
    expectedCode : Int
  )(implicit
    logger       : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Unit = {
    val actualCode = HttpCall.lock(url, lockInfo, 60)
    assert(actualCode == expectedCode)
  }

  def unlock(
    url         : String,
    lockInfo    : LockInfo,
    expectedCode: Int
  )(implicit
    logger      : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Unit = {
    val actualCode = HttpCall.unlock(url, lockInfo)
    assert(actualCode == expectedCode)
  }
}
