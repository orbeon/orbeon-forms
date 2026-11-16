/**
  * Copyright (C) 2024 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.fr.rpc.FormRunnerRpcClient
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.Constants.DummyImageUri
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{EventListenerSupport, XFormsApp, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.collection.mutable
import scala.scalajs.js


object ImageAttachment {

  XFormsXbl.declareCompanion("fr|image-attachment", js.constructorOf[ImageAttachmentCompanion])

  private case class LightboxItem(src: String, filename: String)

  private class ImageAttachmentCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private val blobUrls                         = mutable.Set.empty[String]
    private var observerOpt: Option[dom.MutationObserver] = None
    private var currentItems: Vector[LightboxItem] = Vector.empty
    private var currentIndex: Int                  = 0

    private def dialogOpt: Option[html.Element] =
      containerElem.querySelectorOpt("dialog.fr-image-attachment-lightbox")

    // Only in the JS environment, we may need to asynchronously produce a `blob:` URL for the image
    // https://github.com/orbeon/orbeon-forms/issues/7149
    private def resolveDummyImages(): Unit =
      if (XFormsApp.isBrowserEnvironment) {
        containerElem
          .querySelectorAllT(".xforms-mediatype-image:not(.xforms-disabled)")
          .foreach { outputElem =>
            outputElem.querySelectorOpt("img").foreach { imgElem =>
              val img = imgElem.asInstanceOf[html.Image]
              if (img.src.contains(DummyImageUri) && ! outputElem.hasAttribute("data-orbeon-retrieving")) {
                outputElem.setAttribute("data-orbeon-retrieving", "true")
                val targetId =
                  if (outputElem.id.nonEmpty)
                    outputElem.id
                  else
                    containerElem.id
                FormRunnerRpcClient.api
                  .retrieveResource(getXFormsFormOrThrow.deNamespaceIdIfNeeded(targetId))
                  .foreach { urlOpt =>
                    outputElem.removeAttribute("data-orbeon-retrieving")
                    urlOpt.foreach { url =>
                      img.src = url.toString
                      if (url.getScheme == "blob")
                        blobUrls += url.toString
                    }
                  }
              }
            }
          }
      }

    private def collectItemsAndIndex(clickedCard: html.Element): (Vector[LightboxItem], Int) = {
      val cards =
        containerElem
          .querySelectorAllT(".xforms-dnd-item:not(.xforms-disabled):not(.gu-mirror)")
          .toVector

      val itemsWithCard =
        cards.flatMap { card =>
          card
            .querySelectorOpt(".fr-attachment-thumbnail .xforms-mediatype-image:not(.xforms-disabled) img")
            .map(_.asInstanceOf[html.Image])
            .filter(img => img.src.nonEmpty && ! img.src.contains(DummyImageUri))
            .map { img =>
              val filename =
                card
                  .querySelectorAllT(".fr-attachment-filename:not(.xforms-disabled)")
                  .headOption
                  .map(_.textContent.trim)
                  .getOrElse("")
              card -> LightboxItem(img.src, filename)
            }
        }

      val idx = itemsWithCard.indexWhere(_._1 eq clickedCard).max(0)
      (itemsWithCard.map(_._2), idx)
    }

    private def updateLightboxView(): Unit =
      for {
        dialog <- dialogOpt
        item   <- currentItems.lift(currentIndex)
      } {
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-img").foreach { el =>
          val img = el.asInstanceOf[html.Image]
          img.src = item.src
          img.alt = item.filename
        }
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-filename").foreach { el =>
          el.textContent = item.filename
        }
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-counter").foreach { el =>
          el.textContent =
            if (currentItems.length > 1)
              s"${currentIndex + 1} / ${currentItems.length}"
            else
              ""
        }
        val hideNav = currentItems.length <= 1
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-prev").foreach(_.classList.toggle("xforms-hidden", hideNav))
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-next").foreach(_.classList.toggle("xforms-hidden", hideNav))
      }

    private def stepImage(delta: Int): Unit =
      if (currentItems.length > 1) {
        currentIndex = (currentIndex + delta + currentItems.length) % currentItems.length
        updateLightboxView()
      }

    private def openLightbox(items: Vector[LightboxItem], index: Int): Unit =
      if (items.nonEmpty) {
        currentItems = items
        currentIndex = index.max(0).min(items.length - 1)
        updateLightboxView()
        dialogOpt.foreach { dialog =>
          dialog.asInstanceOf[js.Dynamic].showModal()
        }
      }

    private def isOutsideDialog(ev: dom.MouseEvent, dialog: html.Element): Boolean = {
      val r = dialog.getBoundingClientRect()
      ev.clientX < r.left || ev.clientX > r.right || ev.clientY < r.top || ev.clientY > r.bottom
    }

    override def init(): Unit = {
      resolveDummyImages()

      if (XFormsApp.isBrowserEnvironment) {
        val observer = new dom.MutationObserver((_, _) => resolveDummyImages())
        observer.observe(
          containerElem,
          new dom.MutationObserverInit {
            childList = true
            subtree   = true
          }
        )
        observerOpt = Some(observer)
      }

      EventSupport.addListener(
        containerElem,
        DomEventNames.Click,
        (ev: dom.MouseEvent) =>
          ev.target match {
            case el: html.Element =>
              if (el.closestOpt(".fr-attachment-zoom").isDefined || el.matches(".fr-attachment-thumbnail img")) {
                el.closestOpt(".xforms-dnd-item").foreach { card =>
                  ev.preventDefault()
                  val (items, idx) = collectItemsAndIndex(card)
                  openLightbox(items, idx)
                }
              }
            case _ =>
          }
      )

      dialogOpt.foreach { dialog =>
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-close").foreach { closeBtn =>
          EventSupport.addListener(
            closeBtn,
            DomEventNames.Click,
            (ev: dom.MouseEvent) => {
              ev.preventDefault()
              dialog.asInstanceOf[js.Dynamic].close()
            }
          )
        }

        dialog.querySelectorOpt(".fr-image-attachment-lightbox-prev").foreach { prevBtn =>
          EventSupport.addListener(
            prevBtn,
            DomEventNames.Click,
            (ev: dom.MouseEvent) => {
              ev.preventDefault()
              stepImage(-1)
            }
          )
        }

        dialog.querySelectorOpt(".fr-image-attachment-lightbox-next").foreach { nextBtn =>
          EventSupport.addListener(
            nextBtn,
            DomEventNames.Click,
            (ev: dom.MouseEvent) => {
              ev.preventDefault()
              stepImage(1)
            }
          )
        }

        EventSupport.addListener(
          dialog,
          DomEventNames.KeyDown,
          (ev: dom.KeyboardEvent) =>
            if (ev.key == "ArrowLeft" && currentItems.length > 1) {
              ev.preventDefault()
              stepImage(-1)
            } else if (ev.key == "ArrowRight" && currentItems.length > 1) {
              ev.preventDefault()
              stepImage(1)
            }
        )

        // Fallback for browsers without native `closedby="any"` support
        val supportsClosedBy =
          ! js.isUndefined(dom.window.asInstanceOf[js.Dynamic].HTMLDialogElement) &&
            js.Dynamic.global.Object
              .hasOwn(dom.window.asInstanceOf[js.Dynamic].HTMLDialogElement.prototype, "closedBy")
              .asInstanceOf[Boolean]

        if (! supportsClosedBy) {
          var pressedOutside = false
          EventSupport.addListener(
            dialog,
            "pointerdown",
            (ev: dom.PointerEvent) => {
              pressedOutside = (ev.target eq dialog) && isOutsideDialog(ev, dialog)
            }
          )
          EventSupport.addListener(
            dialog,
            "pointerup",
            (ev: dom.PointerEvent) =>
              if (pressedOutside && (ev.target eq dialog) && isOutsideDialog(ev, dialog)) {
                dialog.asInstanceOf[js.Dynamic].close()
              }
          )
        }
      }
    }

    override def destroy(): Unit = {
      EventSupport.clearAllListeners()
      observerOpt.foreach(_.disconnect())
      observerOpt = None
      if (XFormsApp.isBrowserEnvironment) {
        blobUrls.foreach(dom.URL.revokeObjectURL)
        blobUrls.clear()
      }
    }
  }
}
