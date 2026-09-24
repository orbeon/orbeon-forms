/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.test

import cats.effect.*
import cats.syntax.all.*
import org.orbeon.oxf.fr.Version.*
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport, XMLSupport}
import org.orbeon.oxf.util.CoreCrossPlatformSupport.runtime
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Random


class ConcurrentSaveSearchTest
  extends DocumentTestBase
     with AnyFunSpecLike
     with ResourceManagerSupport
     with XMLSupport
     with XFormsSupport {

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[ConcurrentSaveSearchTest]), true)

  describe("Concurrent non-draft save and search with draft data") {
    it("must not fail when non-draft save removes draft and indexed values") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("concurrent non-draft save and search") { (_, provider) =>

          val SeedDocumentCount   = 120
          val WriterWorkers       = 3
          val ReaderWorkers       = 3
          val IterationsPerWorker = 40
          val testForms           = List("search-save-deadlock-1", "search-save-deadlock-2").map { formName =>
            TestForm(provider, controls = Seq(TestForm.Control("control label")), formName = formName)
          }
          val version             = Specific(1)
          testForms.foreach { testForm =>
            testForm.putFormDefinition(version)
            testForm.putFormData(version, (1 to SeedDocumentCount).map(i => FormData(s"${testForm.appForm.form}-$i", s"seed-$i")))
          }

          def writer(worker: Int): IO[List[Int]] = {
            val testForm   = testForms(worker % testForms.size)
            val documentId = s"${testForm.appForm.form}-1"
            val baseURL    = HttpCall.crudURLPrefix(provider, testForm.appForm.form)
            val draftURL   = baseURL + s"draft/$documentId/data.xml"
            val dataURL    = baseURL + s"data/$documentId/data.xml"
            (1 to IterationsPerWorker).toList.traverse { iteration =>
              val draftBody = HttpCall.XML(testForm.formData(Seq(s"draft-w$worker-$iteration")))
              val dataBody  = HttpCall.XML(testForm.formData(Seq(s"data-w$worker-$iteration")))
              for {
                _ <- IO.sleep(Random.between(0, 8).millis)
                statusCodes <- IO.blocking {
                  val draftResponse = HttpCall.put(draftURL, version, stage = None, draftBody)
                  val dataResponse  = HttpCall.put(dataURL, version, stage = None, dataBody)
                  List(draftResponse.statusCode, dataResponse.statusCode)
                }
              } yield
                statusCodes
            }.map(_.flatten)
          }

          def reader(worker: Int): IO[List[Int]] = {
            val testForm   = testForms(worker % testForms.size)
            val searchURL  = HttpCall.searchURL(provider, testForm.appForm.form)
            val searchBody =
              HttpCall.XML(
                <search>
                  <query/>
                  <query path={testForm.controlPath(0)} sort="asc">seed</query>
                  <drafts>include</drafts>
                  <page-size>200</page-size>
                  <page-number>1</page-number>
                  <lang>en</lang>
                </search>.toDocument
              )
            (1 to IterationsPerWorker).toList.traverse { _ =>
              for {
                _          <- IO.sleep(Random.between(0, 8).millis)
                statusCode <- IO.blocking(HttpCall.post(searchURL, version, searchBody).statusCode)
              } yield
                statusCode
            }
          }

          val allStatusCodes =
            Await.result(
              awaitable =
                (
                  (1 to WriterWorkers).map(writer).toList :::
                  (1 to ReaderWorkers).map(reader).toList
                ).parSequence.map(_.flatten).unsafeToFuture(),
              atMost = Duration.Inf
            )

          val nonSuccessStatusCodes = allStatusCodes.filterNot(StatusCode.isSuccessCode)
          assert(nonSuccessStatusCodes.isEmpty, s"non-success status codes: ${nonSuccessStatusCodes.take(20).mkString(", ")}")
        }
      }
    }
  }
}
