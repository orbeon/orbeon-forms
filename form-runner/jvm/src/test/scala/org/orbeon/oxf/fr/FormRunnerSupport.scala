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

import org.apache.commons.io.IOUtils
import org.orbeon.connection.{BufferedContent, StreamedContent}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.FormRunnerSupport.*
import org.orbeon.oxf.http.HttpMethod.{GET, POST}
import org.orbeon.oxf.http.HttpResponse
import org.orbeon.oxf.test.TestHttpClient.CacheEvent
import org.orbeon.oxf.test.{DocumentTestBase, TestHttpClient}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.state.XFormsDocumentCache


object FormRunnerSupport {
  private val FindUUIDInHTMLBodyRE = """<script src="\/orbeon\/xforms-server\/form\/dynamic\/([^.]+)\.js"""".r
}

trait FormRunnerSupport extends DocumentTestBase {

  def withFormRunnerDocument[T](processorService: ProcessorService, doc: XFormsContainingDocument)(thunk: => T): T =
    ProcessorService.withProcessorService(processorService) {
      setupDocument(doc) // FIXME: to make it available to XFormsSupport
      withContainingDocument(doc) {
        thunk
      }
    }

  def performSectionAction(sectionControl: XFormsControl, action: String): Unit = {
    // NOTE: We can't yet just dispatch `fr-insert-below` to the section, so find the nested repeater.
    val repeater =
      ControlsIterator(sectionControl, includeSelf = false) collectFirst {
        case c: XFormsComponentControl if c.localName == "repeater" =>  c
      } get

    dispatch(name = action, effectiveId = repeater.effectiveId)
  }

  def performGridAction(gridControl: XFormsControl, action: String): Unit =
    dispatch(name = action, effectiveId = gridControl.effectiveId)

  def setFormRunnerLanguage(lang: String): Unit =
    setControlValueWithEventSearchNested("fr-language-selector-select", lang)

  def runFormRunner(
    app        : String,
    form       : String,
    mode       : String,
    formVersion: String  = "", // not used yet
    documentId : Option[String] = None,
    query      : IterableOnce[(String, String)] = Nil,
    initialize : Boolean = true,
    content    : Option[StreamedContent] = None,
    attributes : Map[String, AnyRef]     = Map.empty
  ): (ProcessorService, Option[XFormsContainingDocument], List[CacheEvent]) = {

    val (processorService, response, _, events) =
      TestHttpClient.connect(
        url        = PathUtils.recombineQuery(s"/fr/$app/$form/$mode${documentId map ("/" +) getOrElse ""}", query),
        method     = if (content.isDefined) POST else GET,
        headers    = Map.empty,
        content    = content,
        attributes = attributes
      )

    val responseContent = BufferedContent(response.content)(IOUtils.toByteArray)

    val uuidOpt = FindUUIDInHTMLBodyRE.findFirstMatchIn(new String(responseContent.body, CharsetNames.Utf8)) map (_.group(1))
    val docOpt = uuidOpt flatMap XFormsDocumentCache.peekForTests

    (processorService, docOpt, events)
  }

  def runFormRunnerReturnContent(
    app        : String,
    form       : String,
    mode       : String,
    formVersion: String  = "", // not used yet
    documentId : Option[String] = None,
    query      : IterableOnce[(String, String)] = Nil,
    initialize : Boolean = true,
    content    : Option[StreamedContent] = None,
    background : Boolean = false
  ): (ProcessorService, BufferedContent, HttpResponse) = {

    val (processorService, response, _, _) =
      TestHttpClient.connect(
        url     = PathUtils.recombineQuery(s"/fr/${if (background) "service/" else ""}$app/$form/$mode${documentId.map("/" +).getOrElse("")}", query),
        method  = if (content.isDefined) POST else GET,
        headers = Map.empty,
        content = content
      )

    (processorService, BufferedContent(response.content)(IOUtils.toByteArray), response)
  }
}