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
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.process.ProcessInterpreter._
import org.orbeon.oxf.fr.process.ProcessParser._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.saxon.value.BooleanValue
import org.parboiled.errors.ParsingException
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}


class SimpleProcessTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  val ConstantProcessId = "9fbcdd2e3caf46045e2d545c26d54ffd5b241e97"

  class TestProcessInterpreter extends ProcessInterpreter {

    implicit val logger = new IndentedLogger(LoggerFactory.createLogger(classOf[TestProcessInterpreter]), true)

    override def currentXFormsDocumentId = ""
    def findProcessByName(scope: String, name: String): Option[String] = None
    def processError(t: Throwable) = ()
    var xpathContext: Item = null
    val xpathFunctionLibrary = null
    def xpathFunctionContext = null

    // Just store the continuation locally
    def writeSuspendedProcess(process: String) = _suspendedProcess = Some(process)
    def readSuspendedProcess = _suspendedProcess.get

    // Constant so that we can test properly
    override def createUniqueProcessId = ConstantProcessId

    def transactionStart()    = ()
    def transactionRollback() = ()

    private var _suspendedProcess: Option[String] = None
    def savedProcess = _suspendedProcess

    override def beforeProcess() = {
      _trace.clear()
      Success(())
    }

    // a1-a20 successful actions which log a trace of their execution
    override def extensionActions = 1 to 20 map ("a" + _) map (name => name -> mySuccessAction(name) _)

    private val _trace = ListBuffer[String]()
    def trace = _trace mkString " "

    def mySuccessAction(name: String)(params: ActionParams): Try[Any] = {
      _trace += name
      Success(())
    }
  }

  def normalize(s: String) = "(" + s.trimAllToEmpty + ")"

  describe("serialization") {

    val processes = Seq(
      """save""",
      """save("draft")""",
      """visit-all then captcha then validate("error") recover (visit-all then expand-all then error-message("form-validation-error") then success)""",
      """if ("//secret = 42") then success-message(message = "yea") else error-message(message = "nay")"""
    )

    for (p <- processes)
      it(s"must pass with `$p`") {
        assert(normalize(p) === parse(p).serialize)
      }
  }

  describe("invalid") {
    val processes = Seq(
      """if ("xpath") then a1 then a2 else a3""",
      """if ("xpath") a1 then a2"""
    )

    for (p <- processes)
      it(s"must pass with `$p`") {
        intercept[ParsingException](parse(p))
      }
  }

  describe("suspendResume") {

    val interpreter = new TestProcessInterpreter {

      val processes = Map(
        "p1" -> """a1 then a2 then suspend then a3""",
        "p2" -> """a1 then (a2 then (a3 then if (". = true()") then (a4 then suspend then (a5 then a6)) else (a7 then suspend then (a8 then a9)) then a10) then a11) then a12""",
        "p3" -> """a13 then if ("true()") then p2 else p1 then a14""",
        "p4" -> """a1 then suspend""",
        "p5" -> """a2 then p4 then a3"""
      )

      override def findProcessByName(scope: String, name: String) = processes.get(name)
    }

    val expected = Seq(
      ("p1", null,               """nop then a3""",                                               "a1 a2"),
      ("p2", BooleanValue.TRUE,  """nop then (a5 then a6) then a10 then a11 then a12""",          "a1 a2 a3 a4"),
      ("p2", BooleanValue.FALSE, """nop then (a8 then a9) then a10 then a11 then a12""",          "a1 a2 a3 a7"),
      ("p3", BooleanValue.TRUE,  """nop then (a5 then a6) then a10 then a11 then a12 then a14""", "a13 a1 a2 a3 a4"),
      ("p3", BooleanValue.FALSE, """nop then (a8 then a9) then a10 then a11 then a12 then a14""", "a13 a1 a2 a3 a7"),
      ("p5", null,               """nop then a3""",                                               "a2 a1")
    )

    for ((process, context, continuation, trace) <- expected) {
      it(s"must pass with `$process/$context`") {
        withTestExternalContext { _ =>
          interpreter.xpathContext = context
          interpreter.runProcessByName("", process)
          assert(Some(ConstantProcessId + '|' + normalize(continuation)) === interpreter.savedProcess)
          assert(trace === interpreter.trace)
          interpreter.runProcess("", "resume").get
        }
      }
    }
  }

  describe("renderedFormatParametersSelection") {

    import FormRunnerRenderedFormat._
    import org.orbeon.scaxon.NodeConversions._

    val Tests = List[(String, NodeInfo, ActionParams, String, List[(String, String)])](
      (
        "no attachment",
        <attachments/>,
        Map.empty,
        "en",
        List(
          s"fr-$UsePdfTemplateParam" -> "false",
          "fr-remember-language"     -> "false",
          "fr-language"              -> "en"
        )
      ),
      (
        "legacy attachment with no name or language",
        <attachments>
          <pdf>data:</pdf>
        </attachments>,
        Map.empty,
        "en",
        List(
          s"fr-$UsePdfTemplateParam" -> "true"
        )
      ),
      (
        "empty name but languages, using default language",
        <attachments>
          <pdf name="" lang="en">data:</pdf>
          <pdf name="" lang="fr">data:</pdf>
        </attachments>,
        Map.empty,
        "fr",
        List(
          s"fr-$UsePdfTemplateParam"  -> "true",
          s"fr-$PdfTemplateLangParam" -> "fr"
        )
      ),
      (
        "empty name but languages, specifying language",
        <attachments>
          <pdf name="" lang="en">data:</pdf>
          <pdf name="" lang="fr">data:</pdf>
        </attachments>,
        Map(
          Some(PdfTemplateLangParam) -> "fr"
        ),
        "en",
        List(
          s"fr-$UsePdfTemplateParam"  -> "true",
          s"fr-$PdfTemplateLangParam" -> "fr"
        )
      ),
      (
        "names and languages, using default language",
        <attachments>
          <pdf name="foo" lang="en">data:</pdf>
          <pdf name="foo" lang="fr">data:</pdf>
          <pdf name="bar" lang="en">data:</pdf>
          <pdf name="bar" lang="fr">data:</pdf>
        </attachments>,
        Map(
          Some(PdfTemplateNameParam) -> "bar"
        ),
        "fr",
        List(
          s"fr-$UsePdfTemplateParam"  -> "true",
          s"fr-$PdfTemplateNameParam" -> "bar",
          s"fr-$PdfTemplateLangParam" -> "fr"
        )
      ),
      (
        "names and languages, specifying language",
        <attachments>
          <pdf name="foo" lang="en">data:</pdf>
          <pdf name="foo" lang="fr">data:</pdf>
          <pdf name="bar" lang="en">data:</pdf>
          <pdf name="bar" lang="fr">data:</pdf>
        </attachments>,
        Map(
          Some(PdfTemplateLangParam) -> "fr",
          Some(PdfTemplateNameParam) -> "bar"
        ),
        "en",
        List(
          s"fr-$UsePdfTemplateParam"  -> "true",
          s"fr-$PdfTemplateNameParam" -> "bar",
          s"fr-$PdfTemplateLangParam" -> "fr"
        )
      ),
      (
        "legacy empty attachment",
        <attachments>
          <pdf mediatype="application/pdf" filename="" size=""/>
        </attachments>,
        Map.empty,
        "en",
        List(
          s"fr-$UsePdfTemplateParam" -> "false",
          "fr-remember-language"     -> "false",
          "fr-language"              -> "en"
        )
      )
    )

    for ((description, elem, params, defaultLang, expected) <- Tests)
      it(s"must pass with $description") {
        assert(
          expected ===
            createPdfOrTiffParams(
              Some(elem),
              params,
              defaultLang
            )
        )
      }
  }
}
