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

  import Private.*

  XFormsXbl.declareCompanion("fr|image-attachment", js.constructorOf[ImageAttachmentCompanion])

  private class ImageAttachmentCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private val blobUrls                                  = mutable.Set.empty[String]
    private var observerOpt: Option[dom.MutationObserver] = None
    private var currentItems: Vector[LightboxItem]        = Vector.empty
    private var currentIndex: Int                         = 0
    private var carouselIndex: Int                        = 0
    private var lastCardCount: Int                        = 0
    private var isSyncing: Boolean                        = false

    // Lightbox animation state
    private var isLightboxOpen: Boolean                   = false
    private var isLightboxClosing: Boolean                = false
    private var rafIdOpt: Option[Int]                     = None
    private var hiddenThumbOpt: Option[html.Image]        = None
    private var currentFitRect: Rect                      = Rect(0, 0, 0, 0)

    private def rootElemOpt: Option[html.Element] =
      containerElem
        .querySelectorOpt("[data-view-mode]")
        .orElse(containerElem.childrenT.headOption)

    private def isCarouselMode: Boolean =
      rootElemOpt.exists(_.hasClass("fr-image-attachment-view-carousel"))

    private def isLightboxEnabled: Boolean =
      rootElemOpt.exists(_.getAttribute("data-lightbox") == "true")

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
              thumbImg    = img,
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

    private def cancelSpring(): Unit = {
      rafIdOpt.foreach(dom.window.cancelAnimationFrame)
      rafIdOpt = None
    }

    private def restoreHiddenThumb(): Unit = {
      hiddenThumbOpt.foreach(_.style.visibility = "")
      hiddenThumbOpt = None
    }

    private def setHiddenThumb(thumbOpt: Option[html.Image]): Unit = {
      restoreHiddenThumb()
      if (dom.window.innerWidth > 600) {
        thumbOpt.foreach { thumb =>
          thumb.style.visibility = "hidden"
          hiddenThumbOpt = Some(thumb)
        }
      }
    }

    private def positionLightboxImage(dialog: html.Element, r: Rect): Unit =
      dialog.querySelectorOpt(".fr-image-attachment-lightbox-img").foreach { el =>
        el.style.left   = s"${r.x}px"
        el.style.top    = s"${r.y}px"
        el.style.width  = s"${r.width}px"
        el.style.height = s"${r.height}px"
      }

    private def resolveNaturalSize(item: LightboxItem, lightboxImg: html.Image): (Double, Double) = {
      val fromThumb =
        item.thumbImgOpt
          .filter(i => i.naturalWidth > 0 && i.naturalHeight > 0)
          .map(i => (i.naturalWidth.toDouble, i.naturalHeight.toDouble))
      val fromLightbox =
        if (lightboxImg.naturalWidth > 0 && lightboxImg.naturalHeight > 0)
          Some((lightboxImg.naturalWidth.toDouble, lightboxImg.naturalHeight.toDouble))
        else
          None
      val fromRect =
        item.thumbImgOpt
          .map(_.getBoundingClientRect())
          .filter(b => b.width > 0 && b.height > 0)
          .map(b => (b.width, b.height))

      fromThumb.orElse(fromLightbox).orElse(fromRect).getOrElse((800.0, 600.0))
    }

    private def updateLightboxContent(dialog: html.Element): Unit =
      currentItems.lift(currentIndex).foreach { item =>
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-img").foreach { el =>
          val img = el.asInstanceOf[html.Image]
          if (img.src != item.src)
            img.src = item.src
          img.alt = item.filename

          val (natW, natH) = resolveNaturalSize(item, img)
          currentFitRect = computeTargetRect(natW, natH, hasNav = currentItems.length > 1)
          positionLightboxImage(dialog, currentFitRect)

          if (! img.complete || img.naturalWidth == 0) {
            img.onload = (_: dom.Event) =>
              if (isLightboxOpen && ! isLightboxClosing && currentItems.lift(currentIndex).exists(_.src == item.src)) {
                currentFitRect = computeTargetRect(img.naturalWidth.toDouble, img.naturalHeight.toDouble, hasNav = currentItems.length > 1)
                positionLightboxImage(dialog, currentFitRect)
              }
          }
        }

        val hasMultiple = currentItems.length > 1
        val hasFilename = item.filename.nonEmpty

        dialog.querySelectorOpt(".fr-image-attachment-lightbox-filename").foreach { el =>
          el.textContent = item.filename
          setClass(el, "xforms-hidden", present = ! hasFilename)
        }
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-counter").foreach { el =>
          el.textContent = if (hasMultiple) s"${currentIndex + 1} / ${currentItems.length}" else ""
          setClass(el, "xforms-hidden", present = ! hasMultiple)
        }
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-chrome").foreach { el =>
          setClass(el, "fr-image-attachment-lightbox-chrome-minimal", present = ! hasMultiple && ! hasFilename)
        }
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-prev").foreach(setClass(_, "xforms-hidden", present = ! hasMultiple))
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-next").foreach(setClass(_, "xforms-hidden", present = ! hasMultiple))
      }

    private def applyAnimFrame(
      dialog     : html.Element,
      frame      : AnimValues,
      cropInsets : CropInsets,
      closing    : Boolean
    ): Unit = {
      dialog.querySelectorOpt(".fr-image-attachment-lightbox-img").foreach { img =>
        img.style.transform = s"translate(${frame.tx}px, ${frame.ty}px) scale(${frame.scale})"
        val scaledRadius = if (frame.scale > 0.02) frame.borderRadius / frame.scale else 0.0
        if (frame.crop > 0.001 && cropInsets.nonZero) {
          val t = frame.crop * cropInsets.top
          val r = frame.crop * cropInsets.right
          val b = frame.crop * cropInsets.bottom
          val l = frame.crop * cropInsets.left
          img.style.setProperty("clip-path", s"inset(${t}px ${r}px ${b}px ${l}px round ${scaledRadius}px)")
          img.style.borderRadius = ""
        } else {
          img.style.removeProperty("clip-path")
          img.style.borderRadius = if (scaledRadius > 0.1) s"${scaledRadius}px" else ""
        }
      }

      val clampedOp = frame.opacity.max(0.0).min(1.0)
      dialog.querySelectorOpt(".fr-image-attachment-lightbox-backdrop").foreach { bd =>
        bd.style.opacity = clampedOp.toString
      }

      val chromeOp = if (closing) clampedOp * clampedOp else clampedOp
      val driftY   = (1.0 - clampedOp) * 12.0
      dialog.querySelectorOpt(".fr-image-attachment-lightbox-chrome").foreach { ch =>
        ch.style.opacity   = chromeOp.toString
        ch.style.transform = s"translateX(-50%) translateY(${driftY}px)"
      }
      dialog.querySelectorOpt(".fr-image-attachment-lightbox-prev").foreach { btn =>
        btn.style.opacity = chromeOp.toString
      }
      dialog.querySelectorOpt(".fr-image-attachment-lightbox-next").foreach { btn =>
        btn.style.opacity = chromeOp.toString
      }
    }

    private def animateSpring(
      dialog     : html.Element,
      from       : AnimValues,
      to         : AnimValues,
      cfg        : SpringConfig,
      cropInsets : CropInsets,
      closing    : Boolean,
      onComplete : () => Unit
    ): Unit = {
      cancelSpring()

      if (prefersReducedMotion()) {
        applyAnimFrame(dialog, to, cropInsets, closing)
        onComplete()
      } else {
        var txS  = SpringState(from.tx, 0.0)
        var tyS  = SpringState(from.ty, 0.0)
        var scS  = SpringState(from.scale, 0.0)
        var opS  = SpringState(from.opacity, 0.0)
        var crS  = SpringState(from.crop, 0.0)
        var brS  = SpringState(from.borderRadius, 0.0)
        var prev = dom.window.performance.now()

        applyAnimFrame(dialog, from, cropInsets, closing)

        lazy val tick: js.Function1[Double, Unit] = (now: Double) => {
          val dt = math.min((now - prev) / 1000.0, 0.064).max(0.001)
          prev = now

          txS = stepSpring(cfg, txS, to.tx, dt)
          tyS = stepSpring(cfg, tyS, to.ty, dt)
          scS = stepSpring(cfg, scS, to.scale, dt)
          opS = stepSpring(cfg, opS, to.opacity, dt)
          crS = stepSpring(cfg, crS, to.crop, dt)
          brS = stepSpring(cfg, brS, to.borderRadius, dt)

          val cur = AnimValues(txS.position, tyS.position, scS.position, opS.position, crS.position, brS.position)
          applyAnimFrame(dialog, cur, cropInsets, closing)

          val settledEarly = closing && opS.position < 0.015 && math.abs(scS.position - to.scale) < 0.04
          val allSettled   = txS.settled && tyS.settled && scS.settled && opS.settled && crS.settled && brS.settled

          if (allSettled || settledEarly) {
            applyAnimFrame(dialog, to, cropInsets, closing)
            rafIdOpt = None
            onComplete()
          } else {
            rafIdOpt = Some(dom.window.requestAnimationFrame(tick))
          }
        }

        rafIdOpt = Some(dom.window.requestAnimationFrame(tick))
      }
    }

    private def stepImage(delta: Int): Unit =
      if (currentItems.length > 1 && isLightboxOpen && ! isLightboxClosing) {
        cancelSpring()
        currentIndex = (currentIndex + delta + currentItems.length) % currentItems.length
        if (isCarouselMode)
          selectCarouselIndex(currentIndex, scrollFilmstrip = false)
        dialogOpt.foreach { dialog =>
          val activeItem = currentItems(currentIndex)
          setHiddenThumb(activeItem.thumbImgOpt)
          updateLightboxContent(dialog)
          val targetRadius = readTargetBorderRadius(dialog)
          applyAnimFrame(
            dialog     = dialog,
            frame      = AnimValues(0.0, 0.0, 1.0, 1.0, 0.0, targetRadius),
            cropInsets = CropInsets.Zero,
            closing    = false
          )
        }
      }

    private def finishClose(): Unit = {
      cancelSpring()
      restoreHiddenThumb()
      isLightboxOpen    = false
      isLightboxClosing = false
      dialogOpt.foreach { dialog =>
        dialog.querySelectorOpt(".fr-image-attachment-lightbox-img").foreach { img =>
          img.style.transform    = ""
          img.style.borderRadius = ""
          img.style.removeProperty("clip-path")
        }
        if (dialog.hasAttribute("open"))
          dialog.asInstanceOf[js.Dynamic].close()
      }
    }

    private def closeLightbox(): Unit =
      if (isLightboxOpen && ! isLightboxClosing) {
        dialogOpt match {
          case Some(dialog) =>
            isLightboxClosing = true
            val targetRadius  = readTargetBorderRadius(dialog)
            val activeThumb   = currentItems.lift(currentIndex).flatMap(_.thumbImgOpt)

            computeFlipGeometry(activeThumb, currentFitRect) match {
              case Some((dx, dy, flipScale, thumbRadius, cropInsets)) if ! prefersReducedMotion() =>
                animateSpring(
                  dialog     = dialog,
                  from       = AnimValues(0.0, 0.0, 1.0, 1.0, 0.0, targetRadius),
                  to         = AnimValues(dx, dy, flipScale, 0.0, if (cropInsets.nonZero) 1.0 else 0.0, thumbRadius),
                  cfg        = SpringClose,
                  cropInsets = cropInsets,
                  closing    = true,
                  onComplete = () => finishClose()
                )
              case _ =>
                animateSpring(
                  dialog     = dialog,
                  from       = AnimValues(0.0, 0.0, 1.0, 1.0, 0.0, targetRadius),
                  to         = AnimValues(0.0, 0.0, 1.0, 0.0, 0.0, targetRadius),
                  cfg        = SpringClose,
                  cropInsets = CropInsets.Zero,
                  closing    = true,
                  onComplete = () => finishClose()
                )
            }
          case None =>
            finishClose()
        }
      }

    private def openLightbox(items: Vector[LightboxItem], index: Int): Unit =
      if (items.nonEmpty) {
        if (isLightboxOpen || isLightboxClosing)
          finishClose()

        dialogOpt.foreach { dialog =>
          currentItems      = items
          currentIndex      = index.max(0).min(items.length - 1)
          isLightboxOpen    = true
          isLightboxClosing = false

          val activeItem  = currentItems(currentIndex)
          val activeThumb = activeItem.thumbImgOpt

          // Measure source thumbnail rect before opening modal
          val preThumbData = activeThumb.map(t => (t, getThumbVirtualRect(t), readThumbBorderRadius(t)))

          dialog.asInstanceOf[js.Dynamic].showModal()
          updateLightboxContent(dialog)
          setHiddenThumb(activeThumb)

          val targetRadius = readTargetBorderRadius(dialog)

          preThumbData.filter { case (t, _, _) => isInViewport(t.getBoundingClientRect()) } match {
            case Some((thumb, virtualRect, thumbRadius)) if ! prefersReducedMotion() && currentFitRect.width > 0 && currentFitRect.height > 0 =>
              val (dx, dy, flipScale, cropInsets) = computeFlipFromRects(thumb, virtualRect, currentFitRect)
              animateSpring(
                dialog     = dialog,
                from       = AnimValues(dx, dy, flipScale, 0.0, if (cropInsets.nonZero) 1.0 else 0.0, thumbRadius),
                to         = AnimValues(0.0, 0.0, 1.0, 1.0, 0.0, targetRadius),
                cfg        = SpringOpen,
                cropInsets = cropInsets,
                closing    = false,
                onComplete = () => ()
              )
            case _ =>
              animateSpring(
                dialog     = dialog,
                from       = AnimValues(0.0, 0.0, 1.0, 0.0, 0.0, targetRadius),
                to         = AnimValues(0.0, 0.0, 1.0, 1.0, 0.0, targetRadius),
                cfg        = SpringOpen,
                cropInsets = CropInsets.Zero,
                closing    = false,
                onComplete = () => ()
              )
          }
        }
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
          attributeFilter = js.Array("class", "data-lightbox", "data-view-mode", "data-columns", "data-max-rows")
        }
      )
      observerOpt = Some(observer)

      EventSupport.addListener(
        dom.window,
        "resize",
        (_: dom.UIEvent) =>
          if (isLightboxOpen && ! isLightboxClosing) {
            dialogOpt.foreach { dialog =>
              updateLightboxContent(dialog)
            }
          }
      )

      EventSupport.addListener(
        containerElem,
        DomEventNames.Click,
        (ev: dom.MouseEvent) =>
          ev.target match {
            case el: html.Element if dialogOpt.forall(! _.contains(el)) =>
              if (isCarouselMode) {
                if (
                  isLightboxEnabled && (
                    el.closestOpt(".fr-image-attachment-carousel-zoom").isDefined ||
                    el.matches(".fr-image-attachment-carousel-img")
                  )
                ) {
                  ev.preventDefault()
                  val stageImgOpt =
                    carouselStageOpt
                      .flatMap(_.querySelectorOpt(".fr-image-attachment-carousel-img"))
                      .map(_.asInstanceOf[html.Image])
                  val cards = collectCards()
                  openLightbox(cards.map(c => LightboxItem(c.src, c.filename, stageImgOpt)), carouselIndex)
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
              } else if (
                isLightboxEnabled && (
                  el.closestOpt(".fr-attachment-zoom").isDefined ||
                  el.matches(".fr-attachment-thumbnail img")
                )
              ) {
                el.closestOpt(".xforms-dnd-item").foreach { clickedCard =>
                  ev.preventDefault()
                  val cards = collectCards()
                  val idx   = cards.indexWhere(_.card eq clickedCard).max(0)
                  openLightbox(cards.map(c => LightboxItem(c.src, c.filename, Some(c.thumbImg))), idx)
                }
              } else if (
                isLightboxEnabled && (
                  el.closestOpt(".fr-image-attachment-single-zoom").isDefined ||
                  el.matches(".fr-image-attachment-single-preview .xforms-mediatype-image img")
                )
              ) {
                containerElem
                  .querySelectorOpt(".fr-image-attachment-single-preview:not(.xforms-disabled) .xforms-mediatype-image:not(.xforms-disabled) img")
                  .map(_.asInstanceOf[html.Image])
                  .filter(img => img.src.nonEmpty && ! img.src.contains(DummyImageUri))
                  .foreach { img =>
                    ev.preventDefault()
                    val filename =
                      containerElem
                        .querySelectorAllT(".fr-attachment-filename:not(.xforms-disabled)")
                        .headOption
                        .map(_.textContent.trim)
                        .getOrElse("")
                    openLightbox(Vector(LightboxItem(img.src, filename, Some(img))), 0)
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
              ev.stopPropagation()
              closeLightbox()
            }
          )
        }

        dialog.querySelectorOpt(".fr-image-attachment-lightbox-prev").foreach { prevBtn =>
          EventSupport.addListener(
            prevBtn,
            DomEventNames.Click,
            (ev: dom.MouseEvent) => {
              ev.preventDefault()
              ev.stopPropagation()
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
              ev.stopPropagation()
              stepImage(1)
            }
          )
        }

        // Clicking the backdrop or the expanded image smoothly shrinks back and closes (like bruchez.name)
        EventSupport.addListener(
          dialog,
          DomEventNames.Click,
          (ev: dom.MouseEvent) =>
            ev.target match {
              case el: html.Element =>
                val inChrome = el.closestOpt(".fr-image-attachment-lightbox-chrome").isDefined
                val inArrow  = el.closestOpt(".fr-image-attachment-lightbox-arrow").isDefined
                if (! inChrome && ! inArrow) {
                  ev.preventDefault()
                  closeLightbox()
                }
              case _ =>
            }
        )

        // Intercept native Esc / platform close request so exit animates smoothly back to the thumbnail
        EventSupport.addListener(
          dialog,
          "cancel",
          (ev: dom.Event) => {
            ev.preventDefault()
            if (isLightboxClosing)
              finishClose()
            else
              closeLightbox()
          }
        )

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
      }
    }

    override def destroy(): Unit = {
      cancelSpring()
      restoreHiddenThumb()
      EventSupport.clearAllListeners()
      observerOpt.foreach(_.disconnect())
      observerOpt = None
      if (XFormsApp.isBrowserEnvironment) {
        blobUrls.foreach(dom.URL.revokeObjectURL)
        blobUrls.clear()
      }
    }
  }

  private object Private {

    case class LightboxItem(src: String, filename: String, thumbImgOpt: Option[html.Image])

    case class CardDetails(
      card       : html.Element,
      thumbImg   : html.Image,
      src        : String,
      filename   : String,
      size       : String,
      canDownload: Boolean,
      canRemove  : Boolean
    )

    case class Rect(x: Double, y: Double, width: Double, height: Double)

    case class CropInsets(top: Double, right: Double, bottom: Double, left: Double) {
      def nonZero: Boolean = top + right + bottom + left > 1.0
    }
    object CropInsets {
      val Zero: CropInsets = CropInsets(0.0, 0.0, 0.0, 0.0)
    }

    case class AnimValues(
      tx          : Double,
      ty          : Double,
      scale       : Double,
      opacity     : Double,
      crop        : Double,
      borderRadius: Double
    )

    case class SpringConfig(stiffness: Double, damping: Double, mass: Double = 1.0)
    case class SpringState(position: Double, velocity: Double, settled: Boolean = false)

    val SpringOpen : SpringConfig = SpringConfig(stiffness = 260.0, damping = 26.0)
    val SpringClose: SpringConfig = SpringConfig(stiffness = 500.0, damping = 38.0)

    private def stepSingle(cfg: SpringConfig, s: SpringState, target: Double, dt: Double): SpringState = {
      val accel = (-cfg.stiffness * (s.position - target) - cfg.damping * s.velocity) / cfg.mass
      val vNext = s.velocity + accel * dt
      val pNext = s.position + vNext * dt
      val done  = math.abs(pNext - target) < 0.01 && math.abs(vNext) < 0.01
      if (done) SpringState(target, 0.0, settled = true)
      else      SpringState(pNext, vNext, settled = false)
    }

    def stepSpring(cfg: SpringConfig, state: SpringState, target: Double, dt: Double): SpringState =
      if (dt > 0.02) {
        var cur = state
        var rem = dt
        while (rem > 0.0 && ! cur.settled) {
          val sub = math.min(rem, 0.008)
          cur = stepSingle(cfg, cur, target, sub)
          rem -= sub
        }
        cur
      } else {
        stepSingle(cfg, state, target, dt)
      }

    def prefersReducedMotion(): Boolean =
      ! js.isUndefined(dom.window.asInstanceOf[js.Dynamic].matchMedia) &&
        dom.window.matchMedia("(prefers-reduced-motion: reduce)").matches

    def isInViewport(r: dom.DOMRect): Boolean =
      r.width > 0 && r.height > 0 &&
        r.bottom > 0 && r.top < dom.window.innerHeight &&
        r.right > 0 && r.left < dom.window.innerWidth

    def readTargetBorderRadius(dialog: html.Element): Double = {
      val raw = dom.window.getComputedStyle(dialog).getPropertyValue("--orbeon-lightbox-image-border-radius").trim
      raw.stripSuffix("px").toDoubleOption.getOrElse(12.0)
    }

    def readThumbBorderRadius(thumb: html.Image): Double = {
      val imgR = dom.window.getComputedStyle(thumb).borderTopLeftRadius.stripSuffix("px").toDoubleOption.getOrElse(0.0)
      if (imgR > 0.0)
        imgR
      else
        Option(thumb.parentElement)
          .flatMap(p => dom.window.getComputedStyle(p).borderTopLeftRadius.stripSuffix("px").toDoubleOption)
          .getOrElse(0.0)
    }

    def computeTargetRect(natW: Double, natH: Double, hasNav: Boolean): Rect = {
      val vw     = dom.window.innerWidth
      val vh     = dom.window.innerHeight
      val padX   = if (vw <= 600) 12.0 else if (hasNav) 76.0 else 40.0
      val padTop = if (vw <= 600) 16.0 else 36.0
      val padBot = if (vw <= 600) 68.0 else 76.0
      val availW = (vw - 2.0 * padX).max(80.0)
      val availH = (vh - padTop - padBot).max(80.0)
      val safeW  = if (natW > 0) natW else 800.0
      val safeH  = if (natH > 0) natH else 600.0
      val scale  = math.min(math.min(availW / safeW, availH / safeH), 1.5)
      val w      = safeW * scale
      val h      = safeH * scale
      val x      = (vw - w) / 2.0
      val y      = padTop + (availH - h) / 2.0
      Rect(x, y, w, h)
    }

    def getThumbVirtualRect(thumb: html.Image): Rect = {
      val box    = thumb.getBoundingClientRect()
      val objFit = dom.window.getComputedStyle(thumb).getPropertyValue("object-fit").trim
      if (objFit != "cover" || thumb.naturalWidth <= 0 || thumb.naturalHeight <= 0 || box.width <= 0 || box.height <= 0) {
        Rect(box.left, box.top, box.width, box.height)
      } else {
        val natAspect = thumb.naturalWidth.toDouble / thumb.naturalHeight.toDouble
        val boxAspect = box.width / box.height
        val (vw, vh)  =
          if (natAspect > boxAspect) (box.height * natAspect, box.height)
          else                       (box.width, box.width / natAspect)
        Rect(box.left + (box.width - vw) / 2.0, box.top + (box.height - vh) / 2.0, vw, vh)
      }
    }

    def computeFlipFromRects(
      thumb      : html.Image,
      virtualRect: Rect,
      fitRect    : Rect
    ): (Double, Double, Double, CropInsets) = {
      val dx     = (virtualRect.x + virtualRect.width / 2.0) - (fitRect.x + fitRect.width / 2.0)
      val dy     = (virtualRect.y + virtualRect.height / 2.0) - (fitRect.y + fitRect.height / 2.0)
      val scaleX = virtualRect.width / fitRect.width
      val scaleY = virtualRect.height / fitRect.height

      val box    = thumb.getBoundingClientRect()
      val objFit = dom.window.getComputedStyle(thumb).getPropertyValue("object-fit").trim
      val cropInsets =
        if (objFit == "cover" && virtualRect.width > 0 && virtualRect.height > 0) {
          CropInsets(
            top    = math.max(0.0, box.top - virtualRect.y) / virtualRect.height * fitRect.height,
            right  = math.max(0.0, (virtualRect.x + virtualRect.width) - box.right) / virtualRect.width * fitRect.width,
            bottom = math.max(0.0, (virtualRect.y + virtualRect.height) - box.bottom) / virtualRect.height * fitRect.height,
            left   = math.max(0.0, box.left - virtualRect.x) / virtualRect.width * fitRect.width
          )
        } else {
          CropInsets.Zero
        }

      (dx, dy, math.min(scaleX, scaleY), cropInsets)
    }

    def computeFlipGeometry(
      thumbOpt: Option[html.Image],
      fitRect : Rect
    ): Option[(Double, Double, Double, Double, CropInsets)] =
      thumbOpt
        .filter(t => isInViewport(t.getBoundingClientRect()) && fitRect.width > 0 && fitRect.height > 0)
        .map { thumb =>
          val virtualRect                  = getThumbVirtualRect(thumb)
          val thumbRadius                  = readThumbBorderRadius(thumb)
          val (dx, dy, scale, cropInsets)  = computeFlipFromRects(thumb, virtualRect, fitRect)
          (dx, dy, scale, thumbRadius, cropInsets)
        }
  }
}
