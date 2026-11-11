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
package org.orbeon.xbl

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.processor.handlers.XHTMLOutput
import org.orbeon.oxf.xforms.state.{AnnotatedTemplate, XFormsStateManager}
import org.orbeon.oxf.xforms.upload.AllowedMediatypes
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.orbeon.xforms.Constants.DummyAudioUri
import org.scalatest.funspec.AnyFunSpecLike


class AudioControlsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:audio and fr:audio-attachment controls") {

    it("must render HTML <audio> players, proxy audio URIs, and enforce audio/* upload mediatypes") {

      val signedAudioTmp = XFormsUploadControl.hmacURL("file:/tmp/sample.wav", Some("sample.wav"), Some("audio/wav"), Some("44"))

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model" xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <static-audio filename="" mediatype=""/>
                  <empty-static-audio filename="" mediatype=""/>
                  <voice-note filename="" mediatype="" size=""/>
                </form>
              </xf:instance>
              <xf:bind ref="static-audio" type="xf:anyURI"/>
              <xf:bind ref="empty-static-audio" type="xf:anyURI"/>
              <xf:bind ref="voice-note" type="xf:anyURI" constraint="xxf:upload-mediatypes('audio/*')"/>
            </xf:model>
            <xf:model id="fr-parameters-model">
              <xf:instance id="fr-parameters-instance">
                <request>
                  <app>acme</app>
                  <form>audio-test</form>
                  <form-version>1</form-version>
                  <document/>
                  <mode>new</mode>
                </request>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:audio id="static-audio-control" ref="static-audio" class="fr-static-attachment">
              <xf:label>Intro Audio</xf:label>
            </fr:audio>
            <fr:audio id="empty-static-audio-control" ref="empty-static-audio" class="fr-static-attachment">
              <xf:label>Empty Audio</xf:label>
            </fr:audio>
            <fr:audio-attachment id="voice-note-control" ref="voice-note" class="fr-attachment">
              <xf:label>Voice Note</xf:label>
            </fr:audio-attachment>
            <xf:trigger id="set-static-audio-trigger">
              <xf:label>Set Static Audio</xf:label>
              <xf:action event="DOMActivate">
                <xf:setvalue ref="static-audio">{DummyAudioUri}</xf:setvalue>
                <xf:setvalue ref="static-audio/@filename">intro.wav</xf:setvalue>
                <xf:setvalue ref="static-audio/@mediatype">audio/wav</xf:setvalue>
              </xf:action>
            </xf:trigger>
            <xf:trigger id="set-valid-audio-trigger">
              <xf:label>Set Audio</xf:label>
              <xf:action event="DOMActivate">
                <xf:setvalue ref="voice-note">{signedAudioTmp}</xf:setvalue>
                <xf:setvalue ref="voice-note/@filename">sample.wav</xf:setvalue>
                <xf:setvalue ref="voice-note/@mediatype">audio/wav</xf:setvalue>
                <xf:setvalue ref="voice-note/@size">44</xf:setvalue>
              </xf:action>
            </xf:trigger>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {

            val template = AnnotatedTemplate(PartAnalysisBuilder.createFromDocument(xhtml)(XFormsStateManager.newIndentedLogger)._1)

            def renderXhtml() = {
              val receiver = new LocationSAXContentHandler
              XHTMLOutput.send(doc, template, ec)(receiver)
              receiver.getDocument
            }

            activateControlWithEvent("set-static-audio-trigger")

            val initialRendered = renderXhtml()
            val allElems        = initialRendered.getRootElement.descendantElementIterator(includeSelf = true).toList

            val populatedStaticAudio =
              allElems
                .find(_.attributeValue("id") == "static-audio-control")
                .flatMap(_.descendantElementIterator(includeSelf = true).find(_.getName == "audio"))
            assert(populatedStaticAudio.isDefined)
            assert(populatedStaticAudio.get.attributeValue("controls") != null)
            assert(populatedStaticAudio.get.attributeValue("preload") == "metadata")
            assert(populatedStaticAudio.get.attributeValue("autoplay") == null)
            assert(! populatedStaticAudio.get.attributeValue("class").splitTo[Set]().contains("empty-source"))

            val populatedSource = populatedStaticAudio.get.element("source")
            assert(populatedSource != null)
            assert(populatedSource.attributeValue("src").startsWith("/xforms-server/dynamic/"))
            assert(populatedSource.attributeValue("type") == "audio/wav")

            val emptyStaticAudio =
              allElems
                .find(_.attributeValue("id") == "empty-static-audio-control")
                .flatMap(_.descendantElementIterator(includeSelf = true).find(_.getName == "audio"))
            assert(emptyStaticAudio.isDefined)
            assert(emptyStaticAudio.get.attributeValue("class").splitTo[Set]().contains("empty-source"))
            assert(emptyStaticAudio.get.element("source").attributeValue("src") == "")

            // Initially voice-note is blank, so its inner fr:audio is non-relevant (disabled)
            val voiceNoteContainer = allElems.find(_.attributeValue("id") == "voice-note-control").get
            val initialVoiceAudio  = voiceNoteContainer.descendantElementIterator(includeSelf = true).find(_.getName == "audio").get
            assert(initialVoiceAudio.element("source").attributeValue("src") == DummyAudioUri)

            // Simulate a valid audio upload into voice-note
            activateControlWithEvent("set-valid-audio-trigger")
            assert(getControlValue("voice-note-control") == signedAudioTmp)
            assert(isValid("voice-note-control"))

            // Re-render and verify voice-note's inner <audio> player now has a proxied src and audio/wav type
            val afterUploadRendered = renderXhtml()
            val updatedVoiceContainer =
              afterUploadRendered.getRootElement.descendantElementIterator(includeSelf = true).find(_.attributeValue("id") == "voice-note-control").get
            val updatedVoiceAudio = updatedVoiceContainer.descendantElementIterator(includeSelf = true).find(_.getName == "audio").get
            assert(! updatedVoiceAudio.attributeValue("class").splitTo[Set]().contains("empty-source"))
            assert(updatedVoiceAudio.element("source").attributeValue("src").startsWith("/xforms-server/dynamic/"))
            assert(updatedVoiceAudio.element("source").attributeValue("type") == "audio/wav")

            // Verify `xxf:upload-mediatypes('audio/*')` sets `AllowedMediatypes` and propagates `accept` to the file input
            assert(doc.getUploadConstraintsForControl("voice-note-control")._3 == AllowedMediatypes.unapply("audio/*").get)
            val fileInputOpt =
              voiceNoteContainer.descendantElementIterator(includeSelf = true)
                .find(e => e.getName == "input" && e.attributeValue("type") == "file")
            assert(fileInputOpt.isDefined)
            val acceptTokens = fileInputOpt.get.attributeValue("accept").splitTo[Set](",")
            assert(acceptTokens.exists(t => t.startsWith("audio/") || t == ".mp3" || t == ".wav"))
          }
        }
      }
    }
  }
}
