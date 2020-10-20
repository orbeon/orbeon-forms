/**
  * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.orbeon.dom.Document
import org.orbeon.dom.io.XMLWriter
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.ExternalContext.Session
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorUtils._
import org.orbeon.oxf.processor.generator.URLGenerator
import org.orbeon.oxf.processor.{DOMSerializer, Processor}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.util.{PipelineUtils, XPath, XPathCache}
import org.orbeon.oxf.xml.dom.Comparator
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeInfoConversions.unsafeUnwrapElement
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpec

import scala.collection.compat._
import scala.util.control.NonFatal


abstract class ProcessorTestBase(
  testsDocUrl      : String,
  sessionCreated   : Session => Any = _ => (),
  sessionDestroyed : Session => Any = _ => ()
)
  extends AnyFunSpec
     with ResourceManagerSupport
     with XMLSupport {

  case class TestDescriptor(
    descriptionOpt     : Option[String],
    groupOpt           : Option[String],
    processor          : Processor,
    requestUrlOpt      : Option[String],
    docsAndSerializers : List[(Document, DOMSerializer)]
  )

  sealed trait TestResult
  case object SuccessTestResult                                      extends TestResult
  case class  FailedTestResult(expected: Document, actual: Document) extends TestResult
  case class  ErrorTestResult(t: Throwable)                          extends TestResult

  // Run tests
  findTestsToRun groupByKeepOrder (_.groupOpt) foreach { case (groupOpt, descriptors) =>
    describe(groupOpt getOrElse "[No group description provided]") {
      descriptors foreach { descriptor =>
        it (s"must pass ${descriptor.descriptionOpt getOrElse "[No test description provided]"}") {
          runOneTest(descriptor) match {
            case SuccessTestResult =>
            case FailedTestResult(expected, actual) =>
              fail(s"""|Expected:
                       |${expected.getRootElement.serializeToString(XMLWriter.PrettyFormat)}
                       |
                       |Actual:
                       |${actual.getRootElement.serializeToString(XMLWriter.PrettyFormat)}
                       |""".stripMargin)
            case ErrorTestResult(t) =>
              throw Exceptions.getRootThrowable(t)
          }
        }
      }
    }
  }

  private def runOneTest(d: TestDescriptor) =
    try {
      // Create pipeline context
      InitUtils.withPipelineContext { pipelineContext =>

         PipelineSupport.setExternalContext(
           pipelineContext,
           d.requestUrlOpt getOrElse PipelineSupport.DefaultRequestUrl,
           sessionCreated,
           sessionDestroyed
         )

        d.processor.reset(pipelineContext)

        if (d.docsAndSerializers.isEmpty) {
          // Just run (start) a processor with no output
          d.processor.start(pipelineContext)
          SuccessTestResult
        } else {

          val resultsIt =
            for {
              (doc, serializer) <- d.docsAndSerializers.iterator
              actualDoc         = serializer.runGetDocument(pipelineContext)
            } yield {
              // NOTE: We could make the comparison more configurable, for example to not collapse white space
              if (Comparator.compareDocumentsIgnoreNamespacesInScopeCollapse(doc, actualDoc))
                SuccessTestResult
              else
                FailedTestResult(doc, actualDoc)
            }

          resultsIt collectFirst { case f: FailedTestResult => f } getOrElse SuccessTestResult
        }
      }
    } catch {
      case NonFatal(t) => ErrorTestResult(t)
    }

  private def findTestsToRun: List[TestDescriptor] = {

    val testsDoc = {
      val urlGenerator  = new URLGenerator(testsDocUrl, true)
      val domSerializer = new DOMSerializer

      PipelineUtils.connect(urlGenerator, "data", domSerializer, "data")

      new DocumentWrapper(
        domSerializer.runGetDocument(new PipelineContext),
        null,
        XPath.GlobalConfiguration
      )
    }

    val expr =
      """
          let $only := (/tests/test | /tests/group/test)[
              ancestor-or-self::*/@only = 'true' and not(ancestor-or-self::*/@exclude = 'true')
          ] return
            if (exists($only)) then
                $only
            else
                (/tests/test | /tests/group/test)[
                    empty(
                      ancestor-or-self::*[
                        @exclude = 'true' or
                        exists(@edition) and lower-case(@edition) != lower-case($edition)]
                    )
                ]
      """

    implicit val ctx = XPathContext(vars = Map("edition" -> stringToStringValue(Version.Edition.toLowerCase)))

    val testDescriptors =
      for {
        testElem       <- XPathCache.evaluateKeepItems(expr, testsDoc) collect { case i: NodeInfo => i }
        groupElem      <- testElem.parentOption

        descriptionOpt = testElem.attValueNonBlankOpt("description")
        groupOpt       = if (groupElem.localname == "group") groupElem.attValueNonBlankOpt("description") else None
        requestUrlOpt  = testElem.attValueNonBlankOpt("request")
      } yield {

        // Create processor and connect its inputs
        val processor =
          createProcessorWithInputs(unsafeUnwrapElement(testElem)) |!>
            (_.setId("Main Test Processor"))

        // Connect outputs
        val docsAndSerializers =
          for {
            outputElem <- testElem child "output"
            name       = outputElem.attValueNonBlankOrThrow("name")
            unwrapped  = unsafeUnwrapElement(outputElem)
            doc        = createDocumentFromEmbeddedOrHref(unwrapped, outputElem.attValueNonBlankOpt("href").orNull)
            serializer = new DOMSerializer
          } yield {
            PipelineUtils.connect(processor, name, serializer, "data")
            (doc, serializer)
          }

        TestDescriptor(
          descriptionOpt,
          groupOpt,
          processor,
          requestUrlOpt,
          docsAndSerializers.to(List)
        )
      }

    testDescriptors.to(List)
  }

}
