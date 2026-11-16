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

  private case class CardDetails(
    card       : html.Element,
    src        : String,
    filename   : String,
    size       : String,
    canDownload: Boolean,
    canRemove  : Boolean
  )

  private class ImageAttachmentCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private val blobUrls                           = mutable.Set.empty[String]
    private var observerOpt: Option[dom.MutationObserver] = None
    private var currentItems: Vector[LightboxItem] = Vector.empty
    private var currentIndex: Int                  = 0
    private var carouselIndex: Int                 = 0
    private var lastCardCount: Int                 = 0
    private var isSyncing: Boolean                 = false

    private def rootElemOpt: Option[html.Element] =
      containerElem
        .querySelectorOpt("[data-view-mode]")
        .orElse(containerElem.childrenT.headOption)

    private def isCarouselMode: Boolean =
      rootElemOpt.exists(_.hasClass("fr-image-attachment-view-carousel"))

    private def carouselStageOpt: Option[html.Element] =
      containerElem.querySelectorOpt(".fr-image-attachment-carousel-stage")

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
                      syncLayoutAndCarousel()
                    }
                  }
              }
            }
          }
      }

    private def collectCards(): Vector[CardDetails] = {
      val cards =
        containerElem
          .querySelectorAllT(".xforms-dnd-item:not(.xforms-disabled):not(.gu-mirror)")
          .toVector

      cards.flatMap { card =>
        card
          .querySelectorOpt(".fr-attachment-thumbnail .xforms-mediatype-image:not(.xforms-disabled) img")
          .map(_.asInstanceOf[html.Image])
          .filter(img => img.src.nonEmpty && ! img.src.contains(DummyImageUri))
          .map { img =>
            val filenameElems = card.querySelectorAllT(".fr-attachment-filename:not(.xforms-disabled)")
            val filename =
              filenameElems
                .headOption
                .map(_.textContent.trim)
                .getOrElse("")
            val canDownload =
              filenameElems.exists(el => el.matches("a, button") || el.querySelectorOpt("a, button").isDefined)
            val size =
              card
                .querySelectorOpt(".fr-attachment-size:not(.xforms-disabled)")
                .map(_.textContent.trim)
                .getOrElse("")
            val canRemove =
              card
                .querySelectorOpt(".fr-attachment-remove:not(.xforms-disabled)")
                .isDefined
            CardDetails(
              card        = card,
              src         = img.src,
              filename    = filename,
              size        = size,
              canDownload = canDownload,
              canRemove   = canRemove
            )
          }
      }
    }

    private def setClass(el: html.Element, className: String, present: Boolean): Unit =
      if (el.classList.contains(className) != present) {
        if (present)
          el.classList.add(className)
        else
          el.classList.remove(className)
      }

    private def syncLayoutAndCarousel(): Unit =
      if (! isSyncing) {
        isSyncing = true
        try {
          rootElemOpt.foreach { rootElem =>
            val colsOpt =
              rootElem.attValueOpt("data-columns").flatMap(_.toIntOption).filter(_ >= 1)
            colsOpt match {
              case Some(cols) =>
                rootElem.style.setProperty("--fr-image-attachment-columns", cols.toString)
              case None =>
                rootElem.style.removeProperty("--fr-image-attachment-columns")
            }

            val maxRowsOpt =
              rootElem.attValueOpt("data-max-rows").flatMap(_.toIntOption).filter(_ >= 1)
            rootElem.querySelectorOpt(".xbl-fr-dnd-repeat").foreach { dndRepeat =>
              (maxRowsOpt, isCarouselMode) match {
                case (Some(maxRows), false) =>
                  rootElem.style.setProperty("--fr-image-attachment-max-rows", maxRows.toString)
                  val activeCards =
                    dndRepeat.querySelectorAllT(".xforms-dnd-item:not(.xforms-disabled):not(.gu-mirror)")
                  activeCards.headOption.foreach { firstCard =>
                    val cardHeight = firstCard.getBoundingClientRect().height
                    if (cardHeight > 0) {
                      val gap       = 12.0
                      val maxHeight = maxRows * cardHeight + (maxRows - 1) * gap + 4.0
                      dndRepeat.style.maxHeight = s"${maxHeight}px"
                    }
                  }
                case _ =>
                  rootElem.style.removeProperty("--fr-image-attachment-max-rows")
                  dndRepeat.style.removeProperty("max-height")
              }
            }
          }

          if (isCarouselMode) {
            val cards = collectCards()
            // If new images were just added, advance to the first newly added image or keep current valid index
            if (lastCardCount == 0 && cards.nonEmpty)
              carouselIndex = 0
            else if (cards.nonEmpty)
              carouselIndex = carouselIndex.max(0).min(cards.length - 1)
            lastCardCount = cards.length

            carouselStageOpt.foreach { stage =>
              if (cards.isEmpty) {
                setClass(stage, "xforms-hidden", present = true)
              } else {
                setClass(stage, "xforms-hidden", present = false)
                val active = cards(carouselIndex)

                cards.zipWithIndex.foreach { case (c, idx) =>
                  setClass(c.card, "fr-image-attachment-carousel-active", present = idx == carouselIndex)
                }

                stage.querySelectorOpt(".fr-image-attachment-carousel-img").foreach { el =>
                  val img = el.asInstanceOf[html.Image]
                  if (img.src != active.src)
                    img.src = active.src
                  if (img.alt != active.filename)
                    img.alt = active.filename
                }

                stage.querySelectorOpt(".fr-image-attachment-carousel-filename").foreach { el =>
                  if (el.textContent != active.filename)
                    el.textContent = active.filename
                  setClass(el, "fr-image-attachment-carousel-filename-disabled", present = ! active.canDownload)
                }

                stage.querySelectorOpt(".fr-image-attachment-carousel-size").foreach { el =>
                  if (el.textContent != active.size)
                    el.textContent = active.size
                }

                val counterText = s"${carouselIndex + 1} / ${cards.length}"
                stage.querySelectorOpt(".fr-image-attachment-carousel-counter").foreach { el =>
                  if (el.textContent != counterText)
                    el.textContent = counterText
                }

                stage.querySelectorOpt(".fr-image-attachment-carousel-remove").foreach { el =>
                  setClass(el, "xforms-hidden", present = ! active.canRemove)
                }

                val hideNav = cards.length <= 1
                stage.querySelectorOpt(".fr-image-attachment-carousel-prev").foreach(setClass(_, "xforms-hidden", present = hideNav))
                stage.querySelectorOpt(".fr-image-attachment-carousel-next").foreach(setClass(_, "xforms-hidden", present = hideNav))
              }
            }
          }
        } finally {
          observerOpt.foreach(_.takeRecords())
          isSyncing = false
        }
      }

    private def selectCarouselIndex(newIndex: Int, scrollFilmstrip: Boolean = true): Unit = {
      val cards = collectCards()
      if (cards.nonEmpty) {
        carouselIndex = (newIndex + cards.length) % cards.length
        syncLayoutAndCarousel()
        if (scrollFilmstrip) {
          cards.lift(carouselIndex).foreach { active =>
            active.card.asInstanceOf[js.Dynamic].scrollIntoView(
              js.Dynamic.literal(block = "nearest", inline = "nearest", behavior = "smooth")
            )
          }
        }
      }
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
      syncLayoutAndCarousel()

      val observer = new dom.MutationObserver((_, _) => {
        resolveDummyImages()
        syncLayoutAndCarousel()
      })
      observer.observe(
        containerElem,
        new dom.MutationObserverInit {
          childList       = true
          subtree         = true
          attributes      = true
          attributeFilter = js.Array("class", "data-view-mode", "data-columns", "data-max-rows")
        }
      )
      observerOpt = Some(observer)

      EventSupport.addListener(
        containerElem,
        DomEventNames.Click,
        (ev: dom.MouseEvent) =>
          ev.target match {
            case el: html.Element =>
              if (isCarouselMode) {
                if (
                  el.closestOpt(".fr-image-attachment-carousel-zoom").isDefined ||
                  el.matches(".fr-image-attachment-carousel-img")
                ) {
                  ev.preventDefault()
                  val cards = collectCards()
                  openLightbox(cards.map(c => LightboxItem(c.src, c.filename)), carouselIndex)
                } else if (el.closestOpt(".fr-image-attachment-carousel-prev").isDefined) {
                  ev.preventDefault()
                  selectCarouselIndex(carouselIndex - 1)
                } else if (el.closestOpt(".fr-image-attachment-carousel-next").isDefined) {
                  ev.preventDefault()
                  selectCarouselIndex(carouselIndex + 1)
                } else if (el.closestOpt(".fr-image-attachment-carousel-filename").isDefined) {
                  ev.preventDefault()
                  val cards = collectCards()
                  cards.lift(carouselIndex).foreach { active =>
                    active.card
                      .querySelectorAllT(".fr-attachment-filename:not(.xforms-disabled)")
                      .flatMap(f => if (f.matches("a, button")) List(f) else f.querySelectorAllT("a, button").toList)
                      .headOption
                      .foreach(_.click())
                  }
                } else if (el.closestOpt(".fr-image-attachment-carousel-remove").isDefined) {
                  ev.preventDefault()
                  val cards = collectCards()
                  cards.lift(carouselIndex).foreach { active =>
                    active.card
                      .querySelectorOpt(".fr-attachment-remove:not(.xforms-disabled) button, .fr-attachment-remove:not(.xforms-disabled) a")
                      .foreach(_.click())
                  }
                } else if (el.closestOpt(".xforms-dnd-item").isDefined) {
                  el.closestOpt(".xforms-dnd-item").foreach { clickedCard =>
                    ev.preventDefault()
                    val cards = collectCards()
                    val idx   = cards.indexWhere(_.card eq clickedCard)
                    if (idx >= 0)
                      selectCarouselIndex(idx, scrollFilmstrip = false)
                  }
                }
              } else {
                if (el.closestOpt(".fr-attachment-zoom").isDefined || el.matches(".fr-attachment-thumbnail img")) {
                  el.closestOpt(".xforms-dnd-item").foreach { clickedCard =>
                    ev.preventDefault()
                    val cards = collectCards()
                    val idx   = cards.indexWhere(_.card eq clickedCard).max(0)
                    openLightbox(cards.map(c => LightboxItem(c.src, c.filename)), idx)
                  }
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
