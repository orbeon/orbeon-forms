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
package org.orbeon.builder

import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.util.Try


// Client-side controller for Form Builder's responsive Form Preview dialog (#2362)
object TestPreview {

  import Private.*

  private var initialized = false
  private var devices     = PreviewDevicesJSON.DefaultDevices
  private var state       = PreviewState.Default

  def init(): Unit =
    if (! initialized) {
      dom.document.querySelectorOpt(".fb-test-dialog").foreach { dialogElem =>
        initialized = true
        setupDialog(dialogElem)
      }
    }

  def onIframeReplaced(iframe: html.IFrame): Unit = {
    if (! initialized)
      init()
    applyState(iframeOpt = Some(iframe))
  }

  def onIframeLoaded(iframe: html.IFrame): Unit = {
    applyIframeEnvironment(iframe, state)
  }

  private object Private {

    val SessionStorageKey = "orbeon.fb.testPreview"
    val StagePaddingPx    = 40.0
    val AllowedSchemes    = Set("light", "dark", "system")

    case class PreviewState(
      mode       : String,         // "responsive", "desktop", "tablet", "mobile", "custom"
      deviceId   : Option[String],
      width      : Int,
      height     : Int,
      zoom       : String,         // "fit", "0.5", "0.75", "1", "1.25"
      colorScheme: String,         // "light", "dark", "system"
      maximized  : Boolean
    )

    object PreviewState {
      val Default: PreviewState = PreviewState(
        mode        = "responsive",
        deviceId    = None,
        width       = 393,
        height      = 852,
        zoom        = "fit",
        colorScheme = "system",
        maximized   = false
      )

      implicit val encoder: Encoder[PreviewState] = Encoder.instance { s =>
        Json.obj(
          "mode"        -> Json.fromString(s.mode),
          "deviceId"    -> s.deviceId.fold(Json.Null)(Json.fromString),
          "width"       -> Json.fromInt(s.width),
          "height"      -> Json.fromInt(s.height),
          "zoom"        -> Json.fromString(s.zoom),
          "colorScheme" -> Json.fromString(s.colorScheme),
          "maximized"   -> Json.fromBoolean(s.maximized)
        )
      }

      implicit val decoder: Decoder[PreviewState] = Decoder.instance { c =>
        for {
          mode        <- c.getOrElse[String]("mode")(Default.mode)
          deviceId    <- c.getOrElse[Option[String]]("deviceId")(Default.deviceId)
          width       <- c.getOrElse[Int]("width")(Default.width)
          height      <- c.getOrElse[Int]("height")(Default.height)
          zoom        <- c.getOrElse[String]("zoom")(Default.zoom)
          colorScheme <- c.getOrElse[String]("colorScheme")(Default.colorScheme)
          maximized   <- c.getOrElse[Boolean]("maximized")(Default.maximized)
        } yield {
          val (clampedW, clampedH) = PreviewDevicesJSON.clampDimensions(width, height)
          val validMode =
            if (Set("responsive", "desktop", "tablet", "mobile", "custom").contains(mode)) mode
            else Default.mode
          val validZoom =
            if (Set("fit", "0.5", "0.75", "1", "1.25").contains(zoom)) zoom
            else Default.zoom
          val validScheme =
            if (AllowedSchemes.contains(colorScheme)) colorScheme
            else Default.colorScheme
          PreviewState(
            mode        = validMode,
            deviceId    = deviceId,
            width       = clampedW,
            height      = clampedH,
            zoom        = validZoom,
            colorScheme = validScheme,
            maximized   = maximized
          )
        }
      }
    }

    case class DialogElements(
      dialog          : html.Element,
      toolbarContainer: Option[html.Element],
      toolbar         : html.Element,
      statusElem      : Option[html.Element],
      stage           : html.Element,
      wrapper         : html.Element,
      frame           : html.Element,
      badge           : Option[html.Element],
      deviceSelect    : html.Select,
      widthInput      : html.Input,
      heightInput     : html.Input,
      rotateBtn       : html.Button,
      zoomSelect      : html.Select,
      maximizeBtn     : html.Button,
      modeButtons     : List[html.Button],
      schemeButtons   : List[html.Button]
    )

    private var elemsOpt: Option[DialogElements] = None
    private var currentScale: Double             = 1.0

    def loadSessionState(): PreviewState =
      Try(Option(dom.window.sessionStorage.getItem(SessionStorageKey)))
        .toOption
        .flatten
        .flatMap(decode[PreviewState](_).toOption)
        .getOrElse(PreviewState.Default)

    def saveSessionState(s: PreviewState): Unit =
      Try(dom.window.sessionStorage.setItem(SessionStorageKey, s.asJson.noSpaces))

    def setupDialog(dialogElem: html.Element): Unit = {
      val resolvedOpt =
        for {
          toolbar      <- dialogElem.querySelectorOpt(".fb-test-preview-toolbar")
          stage        <- dialogElem.querySelectorOpt(".fb-test-stage")
          wrapper      <- dialogElem.querySelectorOpt(".fb-test-viewport-wrapper")
          frame        <- dialogElem.querySelectorOpt(".fb-test-viewport-frame")
          deviceSelect <- toolbar.querySelectorOpt(".fb-test-device-select").collect { case s: html.Select => s }
          widthInput   <- toolbar.querySelectorOpt(".fb-test-width-input").collect  { case i: html.Input  => i }
          heightInput  <- toolbar.querySelectorOpt(".fb-test-height-input").collect { case i: html.Input  => i }
          rotateBtn    <- toolbar.querySelectorOpt(".fb-test-rotate-btn").collect    { case b: html.Button => b }
          zoomSelect   <- toolbar.querySelectorOpt(".fb-test-zoom-select").collect   { case s: html.Select => s }
          maximizeBtn  <- toolbar.querySelectorOpt(".fb-test-maximize-btn").collect  { case b: html.Button => b }
        } yield DialogElements(
          dialog           = dialogElem,
          toolbarContainer = dialogElem.querySelectorOpt(".fb-test-preview-toolbar-container"),
          toolbar          = toolbar,
          statusElem       = dialogElem.querySelectorOpt(".fb-test-status"),
          stage            = stage,
          wrapper          = wrapper,
          frame            = frame,
          badge            = dialogElem.querySelectorOpt(".fb-test-resize-badge"),
          deviceSelect     = deviceSelect,
          widthInput       = widthInput,
          heightInput      = heightInput,
          rotateBtn        = rotateBtn,
          zoomSelect       = zoomSelect,
          maximizeBtn      = maximizeBtn,
          modeButtons      = toolbar.querySelectorAllT(".fb-test-mode-btn").collect   { case b: html.Button => b }.toList,
          schemeButtons    = toolbar.querySelectorAllT(".fb-test-scheme-btn").collect { case b: html.Button => b }.toList
        )

      resolvedOpt.foreach { els =>
        elemsOpt = Some(els)

        // Parse configured devices from `oxf.fb.test.devices`
        devices = PreviewDevicesJSON.parseDevices(els.toolbar.getAttribute("data-fb-test-devices").trimAllToEmpty)
        populateDeviceSelect(els)

        // Restore saved session state
        state = loadSessionState()

        // Wire toolbar controls
        bindToolbarEvents(els)

        // Wire drag-to-resize handles
        bindResizeHandles(els)

        // Wire toolbar keyboard navigation fallback when native `focusgroup` is unsupported
        installToolbarKeyboardFallback(els.toolbar)

        // Observe stage size changes (window resize, dialog open, console toggle, maximize/restore)
        installResizeObserver(els.stage)

        applyState(iframeOpt = None)
      }
    }

    def labelAttr(toolbar: html.Element, name: String, fallback: String): String =
      Option(toolbar.getAttribute(s"data-label-$name")).flatMap(_.trimAllToOpt).getOrElse(fallback)

    def populateDeviceSelect(els: DialogElements): Unit = {
      val select  = els.deviceSelect
      val toolbar = els.toolbar
      select.innerHTML = ""

      def addOption(parent: dom.Node, value: String, text: String): Unit = {
        val opt = dom.document.createElement("option").asInstanceOf[html.Option]
        opt.value = value
        opt.textContent = text
        parent.appendChild(opt)
      }

      addOption(select, "responsive", labelAttr(toolbar, "responsive", "Responsive"))
      addOption(select, "custom",     labelAttr(toolbar, "custom",     "Custom"))

      val categories = List(
        PreviewDeviceCategory.Mobile  -> labelAttr(toolbar, "mobile",  "Mobile"),
        PreviewDeviceCategory.Tablet  -> labelAttr(toolbar, "tablet",  "Tablet"),
        PreviewDeviceCategory.Desktop -> labelAttr(toolbar, "desktop", "Desktop")
      )

      for ((cat, catLabel) <- categories) {
        val catDevices = devices.filter(_.category == cat)
        if (catDevices.nonEmpty) {
          val group = dom.document.createElement("optgroup").asInstanceOf[html.OptGroup]
          group.label = catLabel
          catDevices.foreach { dev =>
            addOption(group, dev.id, s"${dev.name} (${dev.width}×${dev.height})")
          }
          select.appendChild(group)
        }
      }
    }

    def defaultDeviceForCategory(cat: PreviewDeviceCategory): Option[PreviewDevice] = {
      val inCat = devices.filter(_.category == cat)
      cat match {
        case PreviewDeviceCategory.Mobile  => inCat.find(_.id == "iphone-16").orElse(inCat.headOption)
        case PreviewDeviceCategory.Tablet  => inCat.find(_.id == "ipad-air").orElse(inCat.headOption)
        case PreviewDeviceCategory.Desktop => inCat.find(_.id == "desktop").orElse(inCat.headOption)
      }
    }

    def bindToolbarEvents(els: DialogElements): Unit = {
      // Viewport mode buttons
      els.modeButtons.foreach { btn =>
        btn.addEventListener("click", (_: dom.Event) => {
          val targetMode = Option(btn.getAttribute("data-preview-mode")).getOrElse("responsive")
          targetMode match {
            case "responsive" =>
              updateState(state.copy(mode = "responsive", deviceId = None))
              announce(els, labelAttr(els.toolbar, "responsive", "Responsive"))
            case "mobile" | "tablet" | "desktop" =>
              val cat = PreviewDeviceCategory.withName(targetMode)
              // If already on a device in this category, keep it; otherwise pick the default device for this category
              val chosen =
                state.deviceId.flatMap(id => devices.find(d => d.id == id && d.category == cat))
                  .orElse(defaultDeviceForCategory(cat))
              chosen.foreach { dev =>
                updateState(state.copy(
                  mode     = targetMode,
                  deviceId = Some(dev.id),
                  width    = dev.width,
                  height   = dev.height
                ))
                announce(els, s"${dev.name} ${dev.width}×${dev.height}")
              }
            case _ =>
          }
        })
      }

      // Device preset dropdown
      els.deviceSelect.addEventListener("change", (_: dom.Event) => {
        els.deviceSelect.value match {
          case "responsive" =>
            updateState(state.copy(mode = "responsive", deviceId = None))
          case "custom" =>
            val (w, h) = currentIframeOrStateDimensions(els)
            updateState(state.copy(mode = "custom", deviceId = None, width = w, height = h))
          case deviceId =>
            devices.find(_.id == deviceId).foreach { dev =>
              updateState(state.copy(
                mode     = dev.category.entryName,
                deviceId = Some(dev.id),
                width    = dev.width,
                height   = dev.height
              ))
              announce(els, s"${dev.name} ${dev.width}×${dev.height}")
            }
        }
      })

      // Width & Height inputs
      def onDimensionInputCommit(): Unit = {
        val rawW = Try(els.widthInput.value.toInt).getOrElse(state.width)
        val rawH = Try(els.heightInput.value.toInt).getOrElse(state.height)
        val (clampedW, clampedH) = PreviewDevicesJSON.clampDimensions(rawW, rawH)
        val matchingDevice = devices.find(d =>
          (d.width == clampedW && d.height == clampedH) ||
          (d.height == clampedW && d.width == clampedH)
        )
        updateState(state.copy(
          mode     = matchingDevice.map(_.category.entryName).getOrElse("custom"),
          deviceId = matchingDevice.map(_.id),
          width    = clampedW,
          height   = clampedH
        ))
      }

      els.widthInput.addEventListener("change",  (_: dom.Event) => onDimensionInputCommit())
      els.heightInput.addEventListener("change", (_: dom.Event) => onDimensionInputCommit())

      // Rotate button
      els.rotateBtn.addEventListener("click", (_: dom.Event) => {
        if (els.rotateBtn.getAttribute("aria-disabled") != "true" && state.mode != "responsive") {
          val (newW, newH) = PreviewDevicesJSON.rotateDimensions(state.width, state.height)
          updateState(state.copy(width = newW, height = newH))
          announce(els, s"${newW}×${newH}")
        }
      })

      // Zoom dropdown
      els.zoomSelect.addEventListener("change", (_: dom.Event) => {
        updateState(state.copy(zoom = els.zoomSelect.value))
      })

      // Color scheme toggle buttons
      els.schemeButtons.foreach { btn =>
        btn.addEventListener("click", (_: dom.Event) => {
          val scheme = Option(btn.getAttribute("data-color-scheme")).filter(AllowedSchemes.contains).getOrElse("system")
          updateState(state.copy(colorScheme = scheme))
        })
      }

      // Maximize / Restore dialog button
      els.maximizeBtn.addEventListener("click", (_: dom.Event) => {
        updateState(state.copy(maximized = ! state.maximized))
      })
    }

    def bindResizeHandles(els: DialogElements): Unit = {
      val handles = els.stage.querySelectorAllT(".fb-test-resize-handle")
      handles.foreach { handle =>
        val edge = Option(handle.getAttribute("data-resize-edge")).getOrElse("right")

        var dragging      = false
        var startX        = 0.0
        var startY        = 0.0
        var startWidth    = 0
        var startHeight   = 0
        var activeScale   = 1.0
        var pointerIdOpt  = Option.empty[Double]

        handle.addEventListener("pointerdown", (e: dom.PointerEvent) => {
          if (e.button == 0) {
            e.preventDefault()
            val (w, h) = currentIframeOrStateDimensions(els)
            startWidth  = w
            startHeight = h
            startX      = e.clientX
            startY      = e.clientY
            activeScale = if (state.mode == "responsive") 1.0 else currentScale
            dragging    = true
            pointerIdOpt = Some(e.pointerId)

            handle.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
            els.stage.classList.add("fb-test-dragging")

            if (state.mode == "responsive") {
              state = state.copy(mode = "custom", deviceId = None, width = w, height = h)
              applyLayout(els)
            }
            updateResizeBadge(els, state.width, state.height)
          }
        })

        handle.addEventListener("pointermove", (e: dom.PointerEvent) => {
          if (dragging) {
            val dx = (e.clientX - startX) / activeScale
            val dy = (e.clientY - startY) / activeScale

            val rawW = edge match {
              case "right" | "corner" => math.round(startWidth + dx * 2.0).toInt
              case "left"             => math.round(startWidth - dx * 2.0).toInt
              case _                  => startWidth
            }
            val rawH = edge match {
              case "bottom" | "corner" => math.round(startHeight + dy).toInt
              case _                   => startHeight
            }

            val (clampedW, clampedH) = PreviewDevicesJSON.clampDimensions(rawW, rawH)
            if (clampedW != state.width || clampedH != state.height) {
              state = state.copy(mode = "custom", deviceId = None, width = clampedW, height = clampedH)
              applyLayout(els)
              syncToolbarUI(els)
              updateResizeBadge(els, clampedW, clampedH)
            }
          }
        })

        def endDrag(): Unit =
          if (dragging) {
            dragging = false
            pointerIdOpt.foreach { pid =>
              Try(handle.asInstanceOf[js.Dynamic].releasePointerCapture(pid))
            }
            pointerIdOpt = None
            els.stage.classList.remove("fb-test-dragging")
            saveSessionState(state)
            applyLayout(els)
            syncToolbarUI(els)
          }

        handle.addEventListener("pointerup",     (_: dom.PointerEvent) => endDrag())
        handle.addEventListener("pointercancel", (_: dom.PointerEvent) => endDrag())
      }
    }

    def updateResizeBadge(els: DialogElements, width: Int, height: Int): Unit =
      els.badge.foreach { badge =>
        val bpKey = PreviewDevicesJSON.responsiveBreakpointKey(width)
        val bpLabel = labelAttr(els.toolbar, s"bp-$bpKey", bpKey)
        badge.textContent = s"$width × $height · $bpLabel"
      }

    def currentIframeOrStateDimensions(els: DialogElements): (Int, Int) =
      if (state.mode == "responsive") {
        val rect = els.frame.getBoundingClientRect()
        PreviewDevicesJSON.clampDimensions(math.round(rect.width).toInt, math.round(rect.height).toInt)
      } else {
        (state.width, state.height)
      }

    def updateState(next: PreviewState): Unit = {
      state = next
      saveSessionState(state)
      applyState(iframeOpt = None)
    }

    def applyState(iframeOpt: Option[html.IFrame]): Unit =
      elemsOpt.foreach { els =>
        els.dialog.classList.toggle("fb-test-maximized", state.maximized)
        applyLayout(els)
        syncToolbarUI(els)
        val resolvedIframeOpt =
          iframeOpt.orElse(els.frame.querySelectorOpt(".fb-test-iframe").collect { case f: html.IFrame => f })
        resolvedIframeOpt.foreach(applyIframeEnvironment(_, state))
      }

    def isToolbarHidden(els: DialogElements): Boolean =
      els.toolbarContainer.exists(_.hasClass("xforms-disabled"))

    def applyLayout(els: DialogElements): Unit = {
      val effectiveResponsive = state.mode == "responsive" || isToolbarHidden(els)

      if (effectiveResponsive) {
        currentScale = 1.0
        els.stage.setAttribute("data-preview-mode", "responsive")
        els.wrapper.style.removeProperty("width")
        els.wrapper.style.removeProperty("height")
        els.frame.style.removeProperty("width")
        els.frame.style.removeProperty("height")
        els.frame.style.removeProperty("transform")
        updateFitOptionLabel(els, None)
      } else {
        els.stage.setAttribute("data-preview-mode", state.mode)

        val stageRect   = els.stage.getBoundingClientRect()
        val availWidth  = math.max(100.0, stageRect.width  - StagePaddingPx)
        val availHeight = math.max(100.0, stageRect.height - StagePaddingPx)

        val scale =
          if (state.zoom == "fit")
            PreviewDevicesJSON.computeFitScale(state.width, state.height, availWidth, availHeight)
          else
            Try(state.zoom.toDouble).getOrElse(1.0)

        currentScale = scale

        val scaledW = math.round(state.width  * scale).toInt
        val scaledH = math.round(state.height * scale).toInt

        els.wrapper.style.width  = s"${scaledW}px"
        els.wrapper.style.height = s"${scaledH}px"

        els.frame.style.width  = s"${state.width}px"
        els.frame.style.height = s"${state.height}px"
        els.frame.style.transform =
          if (math.abs(scale - 1.0) < 0.001) "none"
          else s"scale($scale)"

        updateFitOptionLabel(
          els,
          if (state.zoom == "fit") Some(math.round(scale * 100.0).toInt) else None
        )
      }
    }

    def updateFitOptionLabel(els: DialogElements, fitPercentOpt: Option[Int]): Unit = {
      val baseLabel = labelAttr(els.toolbar, "fit", "Fit")
      els.zoomSelect.querySelectorOpt("option[value='fit']").foreach { opt =>
        opt.textContent = fitPercentOpt match {
          case Some(pct) => s"$baseLabel ($pct%)"
          case None      => baseLabel
        }
      }
    }

    def syncToolbarUI(els: DialogElements): Unit = {
      // Mode buttons
      els.modeButtons.foreach { btn =>
        val btnMode = Option(btn.getAttribute("data-preview-mode")).getOrElse("")
        btn.setAttribute("aria-pressed", (btnMode == state.mode).toString)
      }

      // Device dropdown
      val selectValue =
        if (state.mode == "responsive") "responsive"
        else state.deviceId.getOrElse("custom")
      if (els.deviceSelect.value != selectValue)
        els.deviceSelect.value = selectValue

      // Width & Height inputs (don't clobber while user is actively typing)
      val activeEl = dom.document.activeElement
      val (displayW, displayH) = currentIframeOrStateDimensions(els)
      if (activeEl != els.widthInput)
        els.widthInput.value = displayW.toString
      if (activeEl != els.heightInput)
        els.heightInput.value = displayH.toString

      // Rotate button: use aria-disabled so arrow-key toolbar navigation still visits it
      els.rotateBtn.setAttribute("aria-disabled", (state.mode == "responsive").toString)

      // Zoom select
      if (els.zoomSelect.value != state.zoom)
        els.zoomSelect.value = state.zoom

      // Color scheme buttons
      els.schemeButtons.foreach { btn =>
        val btnScheme = Option(btn.getAttribute("data-color-scheme")).getOrElse("")
        btn.setAttribute("aria-pressed", (btnScheme == state.colorScheme).toString)
      }

      // Maximize button
      els.maximizeBtn.setAttribute("aria-pressed", state.maximized.toString)
      val maxLabel =
        if (state.maximized) labelAttr(els.toolbar, "restore",  "Restore")
        else                 labelAttr(els.toolbar, "maximize", "Maximize")
      els.maximizeBtn.setAttribute("aria-label", maxLabel)
      els.maximizeBtn.setAttribute("title", maxLabel)
      els.maximizeBtn.querySelectorOpt("i.fa").foreach { icon =>
        icon.classList.toggle("fa-expand",   ! state.maximized)
        icon.classList.toggle("fa-compress", state.maximized)
      }
    }

    def applyIframeEnvironment(iframe: html.IFrame, currentState: PreviewState): Unit = {
      // Hint browser color-scheme on the iframe element itself
      val cssScheme = currentState.colorScheme match {
        case "light" => "only light"
        case "dark"  => "only dark"
        case _       => "light dark"
      }
      iframe.style.setProperty("color-scheme", cssScheme)

      Try(Option(iframe.contentDocument.asInstanceOf[html.Document])).toOption.flatten.foreach { iframeDoc =>
        val schemeClasses = List("fr-color-scheme-light", "fr-color-scheme-dark", "fr-color-scheme-system")
        val targetClass   = s"fr-color-scheme-${currentState.colorScheme}"

        def applySchemeTo(elem: html.Element): Unit = {
          schemeClasses.foreach(elem.classList.remove)
          elem.classList.add(targetClass)
        }

        Option(iframeDoc.body).foreach(applySchemeTo)
        iframeDoc.getElementByIdOpt("fr-view").foreach(applySchemeTo)

        // Emulate `.xforms-mobile` on the `.orbeon` root when previewing a Mobile preset
        val isMobileMode = currentState.mode == "mobile"
        iframeDoc.querySelectorOpt(".orbeon").foreach { orbeonRoot =>
          orbeonRoot.classList.toggle("xforms-mobile", isMobileMode)
        }
      }
    }

    def announce(els: DialogElements, message: String): Unit =
      els.statusElem.foreach { status =>
        status.textContent = ""
        dom.window.setTimeout(() => status.textContent = message, 20)
      }

    def installResizeObserver(stage: html.Element): Unit = {
      val winDyn = dom.window.asInstanceOf[js.Dynamic]
      if (! js.isUndefined(winDyn.ResizeObserver)) {
        val observer = js.Dynamic.newInstance(winDyn.ResizeObserver)(
          (_: js.Any) =>
            elemsOpt.foreach { els =>
              if (! els.stage.hasClass("fb-test-dragging")) {
                applyLayout(els)
                syncToolbarUI(els)
              }
            }
        )
        observer.observe(stage)
      }
    }

    // Roving-tabindex keyboard fallback when native `focusgroup` is unavailable
    def installToolbarKeyboardFallback(toolbar: html.Element): Unit = {
      val hasFocusGroup = js.Dynamic.global.Object.prototype.hasOwnProperty
        .call(js.Dynamic.global.HTMLElement.prototype, "focusGroup")
        .asInstanceOf[Boolean]

      if (! hasFocusGroup) {
        def focusableItems(): List[html.Element] =
          toolbar.querySelectorAllT("button, select, input").toList

        val initialItems = focusableItems()
        initialItems.zipWithIndex.foreach { case (el, idx) =>
          el.tabIndex = if (idx == 0) 0 else -1
        }

        toolbar.addEventListener("focusin", (e: dom.FocusEvent) => {
          val items = focusableItems()
          e.targetOpt.foreach { target =>
            if (items.contains(target))
              items.foreach(el => el.tabIndex = if (el == target) 0 else -1)
          }
        })

        toolbar.addEventListener("keydown", (e: dom.KeyboardEvent) => {
          val items = focusableItems()
          e.targetOpt.foreach { target =>
            val idx = items.indexOf(target)
            // Don't hijack Left/Right inside number inputs so caret editing works normally
            val isTextInput = target.tagName.equalsIgnoreCase("INPUT")
            if (idx >= 0 && ! isTextInput) {
              val rtl = dom.window.getComputedStyle(toolbar).direction == "rtl"
              val nextIdxOpt = e.key match {
                case "ArrowRight" => Some((idx + (if (rtl) -1 else 1) + items.length) % items.length)
                case "ArrowLeft"  => Some((idx + (if (rtl) 1 else -1) + items.length) % items.length)
                case "Home"       => Some(0)
                case "End"        => Some(items.length - 1)
                case _            => None
              }
              nextIdxOpt.foreach { nextIdx =>
                e.preventDefault()
                items(nextIdx).focus()
              }
            }
          }
        })
      }
    }
  }
}
