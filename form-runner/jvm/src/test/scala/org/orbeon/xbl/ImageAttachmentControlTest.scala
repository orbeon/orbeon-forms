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

import org.orbeon.oxf.fr.definitions.ControlValue
import org.orbeon.oxf.fr.{FormRunner, FormRunnerUploadSupport}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.processor.handlers.XHTMLOutput
import org.orbeon.oxf.xforms.state.{AnnotatedTemplate, XFormsStateManager}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.orbeon.xforms.Constants.DummyImageUri
import org.scalatest.funspec.AnyFunSpecLike


class ImageAttachmentControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:image-attachment single and multiple controls (#7629)") {

    it("must render single and multiple image attachments, thumbnails, lightbox dialog, and support removal") {

      val signedImg1 = XFormsUploadControl.hmacURL(DummyImageUri, Some("photo-1.png"), Some("image/png"), Some("1024"))
      val signedImg2 = XFormsUploadControl.hmacURL(DummyImageUri, Some("photo-2.jpg"), Some("image/jpeg"), Some("2048"))

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model
              id="fr-form-model"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary"
              xxf:custom-mips="upload-mediatypes upload-max-files-per-control">
              <xf:instance id="fr-form-instance">
                <form>
                  <single-photo filename="single.png" mediatype="image/png" size="512">{signedImg1}</single-photo>
                  <gallery>
                    <_ filename="photo-1.png" mediatype="image/png" size="1024">{signedImg1}</_>
                    <_ filename="photo-2.jpg" mediatype="image/jpeg" size="2048">{signedImg2}</_>
                  </gallery>
                  <carousel-gallery>
                    <_ filename="slide-1.png" mediatype="image/png" size="1024">{signedImg1}</_>
                    <_ filename="slide-2.jpg" mediatype="image/jpeg" size="2048">{signedImg2}</_>
                  </carousel-gallery>
                </form>
              </xf:instance>
              <xf:bind ref="single-photo" type="xf:anyURI" constraint="xxf:upload-mediatypes('image/*')"/>
              <xf:bind ref="gallery" constraint="xxf:upload-mediatypes('image/*') and xxf:upload-max-files-per-control(5)"/>
              <xf:bind ref="carousel-gallery" constraint="xxf:upload-mediatypes('image/*')"/>
            </xf:model>
            <xf:model id="fr-parameters-model">
              <xf:instance id="fr-parameters-instance">
                <request>
                  <app>acme</app>
                  <form>gallery-test</form>
                  <form-version>1</form-version>
                  <document/>
                  <mode>new</mode>
                </request>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:image-attachment id="single-photo-control" ref="single-photo" class="fr-attachment">
              <xf:label>Single Photo</xf:label>
            </fr:image-attachment>
            <fr:image-attachment id="gallery-control" ref="gallery" multiple="true" columns="2" max-rows="1" class="fr-attachment">
              <xf:label>Photo Gallery</xf:label>
            </fr:image-attachment>
            <fr:image-attachment id="carousel-gallery-control" ref="carousel-gallery" multiple="true" view-mode="carousel" class="fr-attachment">
              <xf:label>Carousel Gallery</xf:label>
            </fr:image-attachment>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {

            val singleControl  = doc.getControlByEffectiveId("single-photo-control")
            val galleryControl = doc.getControlByEffectiveId("gallery-control")

            assert(singleControl.isRelevant)
            assert(galleryControl.isRelevant)

            // Verify FormRunner.findControlValue for both single and multiple fr:image-attachment
            assert(
              FormRunner.findControlValue(singleControl, EventCollector.Throw)
                .contains(ControlValue.SingleControlValue(signedImg1, Some("single.png"), isHtml = false))
            )
            assert(
              FormRunner.findControlValue(galleryControl, EventCollector.Throw)
                .contains(
                  ControlValue.MultipleControlValue(
                    s"$signedImg1 $signedImg2",
                    List("photo-1.png", "photo-2.jpg"),
                    isHtml = false
                  )
                )
            )

            // Verify FormRunnerUploadSupport counts and aggregate size on the underlying upload control
            val galleryUploadOpt =
              doc.controls.getCurrentControlTree.getRelevantUploadControls
                .find(_.effectiveId.startsWith("gallery-control"))

            assert(galleryUploadOpt.isDefined)
            val galleryUploadEffectiveId = galleryUploadOpt.get.effectiveId

            assert(FormRunnerUploadSupport.currentUploadFilesForControl(doc.controls, galleryUploadEffectiveId).contains(2))
            assert(FormRunnerUploadSupport.currentUploadSizeAggregateForControl(doc.controls, galleryUploadEffectiveId).contains(3072L))

            // Render XHTML and inspect the DOM structure
            val template = AnnotatedTemplate(PartAnalysisBuilder.createFromDocument(xhtml)(XFormsStateManager.newIndentedLogger)._1)

            def renderXhtml() = {
              val receiver = new LocationSAXContentHandler
              XHTMLOutput.send(doc, template, ec)(receiver)
              receiver.getDocument
            }

            val rendered = renderXhtml()
            val allElems = rendered.getRootElement.descendantElementIterator(includeSelf = true).toList

            val galleryElem = allElems.find(_.attributeValueOpt("id").contains("gallery-control")).get
            val galleryDescendants = galleryElem.descendantElementIterator(includeSelf = true).toList

            // Grid mode with columns="2" and max-rows="1"
            val galleryRootDivOpt = galleryDescendants.find(_.attributeValueOpt("data-view-mode").contains("grid"))
            assert(galleryRootDivOpt.isDefined)
            assert(galleryRootDivOpt.get.attributeValueOpt("data-columns").contains("2"))
            assert(galleryRootDivOpt.get.attributeValueOpt("data-max-rows").contains("1"))
            val galleryRootClasses = galleryRootDivOpt.get.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
            assert(galleryRootClasses("fr-image-attachment-view-grid"))
            assert(galleryRootClasses("fr-image-attachment-has-columns"))
            assert(galleryRootClasses("fr-image-attachment-has-max-rows"))

            // Carousel mode (`view-mode="carousel"`)
            val carouselElem = allElems.find(_.attributeValueOpt("id").contains("carousel-gallery-control")).get
            val carouselDescendants = carouselElem.descendantElementIterator(includeSelf = true).toList
            val carouselRootDivOpt = carouselDescendants.find(_.attributeValueOpt("data-view-mode").contains("carousel"))
            assert(carouselRootDivOpt.isDefined)
            val carouselRootClasses = carouselRootDivOpt.get.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
            assert(carouselRootClasses("fr-image-attachment-view-carousel"))
            val carouselStageOpt = carouselDescendants.find { e =>
              val classes = e.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
              classes("fr-image-attachment-carousel-stage") && ! classes("xforms-disabled")
            }
            assert(carouselStageOpt.isDefined)

            // Multiple image attachment has `.fr-attachment-image-preview`
            assert(
              galleryDescendants.exists(e =>
                e.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment-image-preview"))
              )
            )

            // Must render 2 active `.xforms-dnd-item` cards (excluding repeat template)
            val activeCards =
              galleryDescendants.filter { e =>
                val classes   = e.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
                val ancestors = Iterator.iterate(e.getParent)(_.getParent).takeWhile(_ ne null)
                classes("xforms-dnd-item") && ! ancestors.exists(a =>
                  a.attributeValueOpt("class").exists(_.splitTo[Set]().contains("xforms-repeat-template"))
                )
              }
            assert(activeCards.size == 2)

            // Each card must contain arelevant `.fr-attachment-thumbnail` with an `<img>` and a `.fr-attachment-zoom` button
            activeCards.foreach { card =>
              val cardElems = card.descendantElementIterator(includeSelf = true).toList
              val thumbnailOpt = cardElems.find { e =>
                val classes = e.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
                classes("fr-attachment-thumbnail") && ! classes("xforms-disabled")
              }
              assert(thumbnailOpt.isDefined)
              assert(cardElems.exists(_.getName == "img"))
              assert(
                cardElems.exists(e =>
                  e.getName == "button" &&
                    e.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment-zoom"))
                )
              )
            }

            // Lightbox `<dialog>` must be rendered with `closedby="any"`
            val dialogOpt = galleryDescendants.find(e =>
              e.getName == "dialog" &&
                e.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-image-attachment-lightbox"))
            )
            assert(dialogOpt.isDefined)
            assert(dialogOpt.get.attributeValueOpt("closedby").contains("any"))

            // Remove the first attached image via the `.fr-attachment-remove` trigger (`x` button)
            val firstCardRemoveTriggerOpt =
              activeCards.head.descendantElementIterator(includeSelf = true).find(e =>
                e.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-attachment-remove")) &&
                  e.attributeValueOpt("id").isDefined
              )
            assert(firstCardRemoveTriggerOpt.isDefined)

            activateControlWithEvent(firstCardRemoveTriggerOpt.get.attributeValue("id"))

            assert(
              FormRunner.findControlValue(galleryControl, EventCollector.Throw)
                .contains(
                  ControlValue.MultipleControlValue(
                    signedImg2,
                    List("photo-2.jpg"),
                    isHtml = false
                  )
                )
            )
            assert(FormRunnerUploadSupport.currentUploadFilesForControl(doc.controls, galleryUploadEffectiveId).contains(1))
            assert(FormRunnerUploadSupport.currentUploadSizeAggregateForControl(doc.controls, galleryUploadEffectiveId).contains(2048L))
          }
        }
      }
    }
  }
}
