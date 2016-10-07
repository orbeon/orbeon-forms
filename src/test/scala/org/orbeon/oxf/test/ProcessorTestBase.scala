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

import java.{util ⇒ ju}

import org.orbeon.dom.{Document, Element}
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.URLGenerator
import org.orbeon.oxf.processor.{DOMSerializer, Processor, ProcessorUtils}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{Dom4j, XPathUtils}
import org.scalatest.FunSpecLike

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

abstract class ProcessorTestBase(testsDocUrl: String) extends ResourceManagerTestBase with FunSpecLike with XMLSupport {

  case class TestDescriptor(
    description        : Option[String],
    group              : Option[String],
    processor          : Processor,
    requestURL         : String,
    docsAndSerializers : List[(Document, DOMSerializer)]
  )

  sealed trait TestResult
  case object SuccessTestResult                                      extends TestResult
  case class  FailedTestResult(expected: Document, actual: Document) extends TestResult
  case class  ErrorTestResult(t: Throwable)                          extends TestResult

  ResourceManagerTestBase.staticSetup()

  findTestsToRun groupByKeepOrder (_.group) foreach { case (groupOpt, descriptors) ⇒
    describe(groupOpt getOrElse "[No group description provided]") {
      descriptors foreach { descriptor ⇒
        it (s"must pass ${descriptor.description getOrElse "[No test description provided]"}") {
          runTest(descriptor) match {
            case SuccessTestResult ⇒
            case FailedTestResult(expected, actual) ⇒
              assert(Dom4jUtils.domToPrettyString(expected) === Dom4jUtils.domToPrettyString(actual))
              assert(false)
            case ErrorTestResult(t) ⇒
              throw Exceptions.getRootThrowable(t)
          }
        }
      }
    }

  }

  private def runTest(d: TestDescriptor): TestResult = {
    try {
      // Create pipeline context
      val pipelineContext =
        if (d.requestURL.nonBlank)
          createPipelineContextWithExternalContext(d.requestURL)
        else
          createPipelineContextWithExternalContext

      d.processor.reset(pipelineContext)

      if (d.docsAndSerializers.isEmpty) {
        // Processor with no output: just run it
        d.processor.start(pipelineContext)
        SuccessTestResult
      } else {

        val resultsIt =
          for {
            (doc, serializer) ← d.docsAndSerializers.iterator
          } yield {

            val actualDoc = serializer.runGetDocument(pipelineContext)

            // NOTE: We could make the comparison more configurable, for example to not collapse white space
            if (Dom4j.compareDocumentsIgnoreNamespacesInScopeCollapse(doc, actualDoc))
              SuccessTestResult
            else
              FailedTestResult(doc, actualDoc)
          }

        resultsIt collectFirst { case f: FailedTestResult ⇒ f } match {
          case Some(firstFailure) ⇒ firstFailure
          case None               ⇒ SuccessTestResult
        }

      }
    } catch {
      case NonFatal(t)⇒ ErrorTestResult(t)
    }
  }

  private def findTestsToRun: List[TestDescriptor] = {

    val testsDoc = {
      val urlGenerator  = new URLGenerator(testsDocUrl, true)
      val domSerializer = new DOMSerializer

      PipelineUtils.connect(urlGenerator, "data", domSerializer, "data")
      domSerializer.runGetDocument(new PipelineContext)
    }

    // If there are tests with a true "only" attribute but not a true "exclude" attribute, execute only those
    var i = XPathUtils.selectNodeIterator(
      testsDoc,
      "(/tests/test | /tests/group/test)[ancestor-or-self::*/@only = 'true' and not(ancestor-or-self::*/@exclude = 'true')]"
    )

    // Otherwise, run all tests that are not excluded
    if (! i.hasNext)
      i = XPathUtils.selectNodeIterator(
        testsDoc,
        "(/tests/test | /tests/group/test)[not(ancestor-or-self::*/@exclude = 'true')]"
      )

    // Iterate over tests
    val testDescriptorsIt =
      for {
        testElem ← i.asInstanceOf[ju.Iterator[Element]].asScala
        if ! (testElem.attributeValue("ignore") == "true")
        if ! ("pe".equalsIgnoreCase(testElem.attributeValue("edition")) && ! Version.isPE)
        if ! ("ce".equalsIgnoreCase(testElem.attributeValue("edition")) && Version.isPE)
      } yield {

        val descriptionOpt = Option(testElem.attributeValue("description"))

        val groupOpt = {
          val groupElem = testElem.getParent
          if (groupElem.getName == "group")
            Option(groupElem.attributeValue("description"))
          else
            None
        }

        // Create processor and connect its inputs
        val processor = ProcessorUtils.createProcessorWithInputs(testElem)
        processor.setId("Main Test Processor")

        // Connect outputs
        val docsAndSerializersIt =
          for (outputElem ← XPathUtils.selectNodeIterator(testElem, "output").asInstanceOf[ju.Iterator[Element]].asScala) yield {

            val name = XPathUtils.selectStringValue(outputElem, "@name")
            if (name.isBlank)
              throw new OXFException("Output name is mandatory")

            val doc = ProcessorUtils.createDocumentFromEmbeddedOrHref(outputElem, XPathUtils.selectStringValue(outputElem, "@href"))
            val serializer = new DOMSerializer

            PipelineUtils.connect(processor, name, serializer, "data")

            (doc, serializer)
          }

        val requestURL = testElem.attributeValue("request", "")

        TestDescriptor(descriptionOpt, groupOpt, processor, requestURL, docsAndSerializersIt.to[List])
      }

    testDescriptorsIt.to[List]
  }

}
