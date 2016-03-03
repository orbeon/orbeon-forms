/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.dom4j.QName
import org.orbeon.oxf.pipeline.InitUtils._
import org.orbeon.oxf.pipeline.api.{PipelineContext, ProcessorDefinition}
import org.orbeon.oxf.processor.test.TestExternalContext
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.state.XFormsDocumentCache
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache.CacheTracer
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

import scala.collection.mutable.ListBuffer

trait FormRunnerSupport extends DocumentTestBase {

  import FormRunnerSupport._

  def withFormRunnerDocument[T](processorService: ProcessorService, doc: XFormsContainingDocument)(thunk: ⇒ T): T =
    ProcessorService.withProcessorService(processorService) {
      setupDocument(doc) // FIXME: to make it available to XFormsSupport
      withContainingDocument(doc) {
        thunk
      }
    }

  // Simulate a call to Form Runner by running the Form Runner PFC
  def runFormRunner(
    app         : String,
    form        : String,
    mode        : String,
    formVersion : String = "",
    document    : String = "",
    uuid        : String = "",
    noscript    : Boolean = false,
    initialize  : Boolean = true
  ): (ProcessorService, Option[XFormsContainingDocument], List[CacheEvent]) = {

    val processorService = {

      def newProcessorDef(name: String) =
        new ProcessorDefinition(new QName(name, OXF_PROCESSORS_NAMESPACE))

      val pfcProcessorDefinition =
        newProcessorDef("page-flow") |!> (_.addInput("controller", "oxf:/page-flow.xml"))

      new ProcessorService(pfcProcessorDefinition, None)
    }

    val events = ListBuffer[CacheEvent]()

    val tracer = new CacheTracer {
      override def digestAndTemplateStatus(digestIfFound: Option[String]) = events += DigestAndTemplate(digestIfFound)
      override def staticStateStatus(found: Boolean, digest: String)      = events += StaticState(found, digest)
    }

    val request = newFRRequest(s"/fr/$app/$form/$mode", noscript)

    val docOpt =
      withPipelineContext { pipelineContext ⇒

        val externalContext = new TestExternalContext(pipelineContext, TransformerUtils.tinyTreeToDom4j(request))

        pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)
        pipelineContext.setAttribute("orbeon.cache.test.tracer", tracer)
        pipelineContext.setAttribute("orbeon.cache.test.initialize-xforms-document", initialize)

        ProcessorService.withProcessorService(processorService) {
          processorService.service(pipelineContext, externalContext)
        }

        val bytesOpt = Option(externalContext.getResponseBytes)
        val Re = """(?s).+name="\$uuid"\s+value="([^"]+)".+""".r

        val uuidOpt = bytesOpt map (b ⇒ new String(b, "utf-8")) flatMap Re.findFirstMatchIn map (_.group(1))

        uuidOpt map XFormsDocumentCache.instance.takeDocument
      }

    (processorService, docOpt, events.toList)
  }

  def performSectionAction(sectionControl: XFormsControl, action: String): Unit = {
      // NOTE: We can't yet just dispatch `fr-insert-below` to the section, so find the nested repeater.
      val repeater =
        ControlsIterator(sectionControl, includeSelf = false) collectFirst {
          case c: XFormsComponentControl if c.localName == "repeater" ⇒  c
        } get

      dispatch(action, repeater.effectiveId)
  }

  def performGridAction(gridControl: XFormsControl, action: String): Unit =
      dispatch(action, gridControl.effectiveId)

  def setFormRunnerLanguage(lang: String): Unit =
    setControlValueWithEvent("fr-language-selector-select", lang)
}

object FormRunnerSupport {

  sealed trait CacheEvent
  case class DigestAndTemplate(digestIfFound: Option[String]) extends CacheEvent
  case class StaticState(found: Boolean, digest: String)      extends CacheEvent

  def newFRRequest(path: String, noscript: Boolean): NodeInfo =
    <request>
      <container-type>servlet</container-type>
      <content-length>-1</content-length>
      <parameters>
        <parameter>
          <name>fr-noscript</name>
          <value>{noscript.toString}</value>
        </parameter>
      </parameters>
      <protocol>HTTP/1.1</protocol>
      <remote-addr>127.0.0.1</remote-addr>
      <remote-host>localhost</remote-host>
      <scheme>http</scheme>
      <server-name>localhost</server-name>
      <server-port>8080</server-port>
      <is-secure>false</is-secure>
      <auth-type>BASIC</auth-type>
      <username>jdoe</username>
      <context-path>/orbeon</context-path>
      <headers>
        <header>
          <name>host</name>
          <value>localhost:8080</value>
        </header>
        <header>
          <name>user-agent</name>
          <value>Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.1) Gecko/20020826</value>
        </header>
        <header>
          <name>accept-language</name>
          <value>en-us, en;q=0.50</value>
        </header>
        <header>
          <name>accept-encoding</name>
          <value>gzip, deflate, compress;q=0.9</value>
        </header>
        <header>
          <name>accept-charset</name>
          <value>ISO-8859-1, utf-8;q=0.66, *;q=0.66</value>
        </header>
        <header>
          <name>keep-alive</name>
          <value>300</value>
        </header>
        <header>
          <name>connection</name>
          <value>keep-alive</value>
        </header>
        <header>
          <name>referer</name>
          <value>http://localhost:8080/orbeon/</value>
        </header>
        <header>
          <name>cookie</name>
          <value>JSESSIONID=DA6E64FC1E6DFF0499B5D6F46A32186A</value>
        </header>
      </headers>
      <method>GET</method>
      <path-info>{path}</path-info>
      <request-path>{path}</request-path>
      <path-translated/>
      <query-string/>
      <requested-session-id>DA6E64FC1E6DFF0499B5D6F46A32186A</requested-session-id>
      <request-uri>/orbeon/{path}</request-uri>
      <servlet-path/>
    </request>

  def newFRParams(app: String, form: String, mode: String, formVersion: String = "", document: String = "", uuid: String = ""): NodeInfo =
    <request>
      <app>{app}</app>
      <form>{form}</form>
      <form-version>{formVersion}</form-version>
      <document>{document}</document>
      <mode>{mode}</mode>
      <uuid>{uuid}</uuid>
    </request>

}