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
package org.orbeon.fr

import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.orbeon.facades.WeakMap
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.AjaxClient
import org.scalajs.dom
import org.scalajs.dom.{ResizeObserver, html}

import scala.collection.mutable
import scala.scalajs.js
import scala.util.Try


object SearchResultsTableSupport {

  import Private.*

  val TableSelector        = ".orbeon .fr-search-results table:not(.fr-table-no-form-data)"
  val EmptyTableSelector   = ".orbeon .fr-search-results table.fr-table-no-form-data"
  val FixedTableClass      = "fr-table-fixed"
  val MeasuringTableClass  = "fr-table-measuring"
  val ColgroupClass        = "fr-table-colgroup"
  val ColResizerClass      = "fr-col-resizer"
  val CellTruncatedClass   = "fr-cell-truncated"
  val ScrollWidthCssVar    = "--fr-cell-scroll-width"

  def init(): Unit =
    if (! initialized) {
      initialized = true
      DomSupport.onElementFoundOrAdded(
        dom.document.body,
        TableSelector,
        tableElem => syncTable(tableElem)
      )
      dom.document.addEventListener("orbeon:form-loaded", (_: dom.Event) => syncAll())
      AjaxClient.ajaxResponseProcessed.add(_ => syncAll())
    }

  def markUnlockForNextSync(): Unit = {
    unlockPending = true
    statesByScope.values.foreach(_.isLocked = false)
    clearStoredLockedWidthsForCurrentPath()
  }

  def resetAllStates(clearManual: Boolean = true): Unit = {
    unlockPending = false
    statesByScope.values.foreach { state =>
      state.wrapperObserver.foreach(_.disconnect())
      state.cellObserver.foreach(_.disconnect())
    }
    statesByScope.clear()
    if (clearManual)
      Try(dom.window.sessionStorage.removeItem(storageKeyForCurrentPath))
    else
      clearStoredLockedWidthsForCurrentPath()
  }

  def syncAll(): Unit = {
    val shouldUnlock = unlockPending
    unlockPending = false
    dom.document
      .querySelectorAllT(EmptyTableSelector)
      .foreach(clearFixedTableLayout)
    dom.document
      .querySelectorAllT(TableSelector)
      .foreach(tableElem => syncTable(tableElem, forceRemeasure = shouldUnlock))
  }

  def syncTable(tableElem: html.Element, forceRemeasure: Boolean = false): Unit =
    if (dom.document.body.contains(tableElem)) {
      if (tableElem.hasClass("fr-table-no-form-data")) {
        clearFixedTableLayout(tableElem)
      } else {
        tableElem.closestOpt(".fr-search-results").foreach { wrapperElem =>
          ensureWrapperAccessibility(wrapperElem)
          val state = getOrCreateTableState(tableElem, wrapperElem)
          if (forceRemeasure)
            state.isLocked = false

          if (isTableLaidOut(wrapperElem, tableElem)) {
            val visibleThs = findVisibleHeaders(tableElem)
            if (visibleThs.nonEmpty) {
              val cols = classifyColumns(visibleThs)
              val sig  = computeColumnSignature(cols)

              val signatureChanged = state.lastSignature.exists(_ != sig)
              if (forceRemeasure || signatureChanged)
                state.isLocked = false

              state.lastSignature = Some(sig)

              if (! state.isLocked) {
                val restoredFromSession =
                  ! forceRemeasure &&
                    hasActiveSearchUrlParam &&
                    tryRestoreLockedWidthsFromSession(sig, state)

                if (! restoredFromSession)
                  measureInitialWidths(tableElem, cols, state)

                state.isLocked = true
                persistToSession(sig, state)
              } else {
                // Measure any column that newly became visible on a later page (such as `.fr-repeat-labels` when a draft row appears)
                val missingCols    = cols.filterNot(c => state.lockedWidths.contains(c.key))
                var widthsAdjusted = false
                if (missingCols.nonEmpty) {
                  measureMissingColumns(tableElem, cols, missingCols, state)
                  widthsAdjusted = true
                }
                if (ensureActionsColumnFits(tableElem, cols, state))
                  widthsAdjusted = true
                if (widthsAdjusted)
                  persistToSession(sig, state)
              }

              attachResizers(tableElem, cols)
              applyWidthsToColgroup(wrapperElem, tableElem, cols, state)
              observeAndRefreshTruncations(tableElem, state)
            }
          }
        }
      }
    }

  def resetTableWidths(tableElem: html.Element, clearManual: Boolean = false): Unit =
    findTableStateOpt(tableElem).foreach { state =>
      state.isLocked = false
      state.lockedWidths.clear()
      if (clearManual)
        state.manualWidths.clear()
      syncTable(tableElem, forceRemeasure = true)
    }

  private object Private {

    val StorageKeyPrefix         = "orbeon.fr.table.widths:"
    val MinManualWidthPx         = 48.0
    val SelectionMinPx           = 24.0
    val MetadataDateCh           = 10.5
    val MetadataUserMinCh        = 13.0
    val MetadataUserMaxCh        = 22.0
    val MetadataStageMinCh       = 14.0
    val MetadataStageMaxCh       = 24.0
    val MetadataHeadroomCh       = 1.0
    val StatusMinCh              = 18.0
    val StatusHeadroomCh         = 2.0
    val VersionMaxCh             = 16.0
    val DynamicEmptyFloorCh      = 12.0
    val DynamicMaxCh             = 28.0
    val DynamicWideThresholdCh   = 30.0
    val DynamicWideMaxCh         = 34.0
    val DynamicFlexMinCh         = 24.0
    val DynamicFlexCapCh         = 32.0
    val DynamicTextHeadroomCh    = 2.5
    val DynamicNumHeadroomCh     = 1.5
    val DynamicCompactHeadroomCh = 1.0
    val KeyboardStepPx           = 8.0
    val KeyboardShiftStepPx      = 24.0

    val HiddenOrDelimiterClasses = Seq(
      "xforms-repeat-begin-end",
      "xforms-repeat-delimiter",
      "xforms-group-begin-end",
      "xforms-disabled",
      "xforms-hidden"
    )

    val TruncationCandidateSelector =
      "tbody .fr-summary-cell-value, tbody .fr-form-slug:not(.xforms-hidden), tbody td > a:not(.fr-summary-cell-link):not(.xforms-hidden), tbody td.fr-title, tbody td.fr-created, tbody td.fr-modified, tbody td.fr-created-by, tbody td.fr-last-modified-by, tbody td.fr-workflow-stage"

    sealed trait ColumnKind
    object ColumnKind {
      case object Selection     extends ColumnKind
      case object Labels        extends ColumnKind
      case object Actions       extends ColumnKind
      case object Status        extends ColumnKind
      case object Version       extends ColumnKind
      case object MetadataDate  extends ColumnKind
      case object MetadataUser  extends ColumnKind
      case object MetadataStage extends ColumnKind
      case object Dynamic       extends ColumnKind
    }

    case class ColumnDescriptor(
      index      : Int,
      key        : String,
      kind       : ColumnKind,
      thElem     : html.Element,
      alignRight : Boolean,
      labelText  : String
    ) {
      def isDataColumn: Boolean =
        kind != ColumnKind.Selection && kind != ColumnKind.Labels && kind != ColumnKind.Actions

      def isResizable: Boolean =
        isDataColumn
    }

    case class StoredWidthPayload(
      signature    : Vector[String],
      lockedWidths : Map[String, Double],
      manualWidths : Map[String, Double]
    )

    class TableState(
      val lockedWidths      : mutable.LinkedHashMap[String, Double] = mutable.LinkedHashMap.empty,
      val manualWidths      : mutable.LinkedHashMap[String, Double] = mutable.LinkedHashMap.empty,
      var isLocked          : Boolean                               = false,
      var lastSignature     : Option[Vector[String]]                = None,
      var lastWrapperWidth  : Int                                   = 0,
      var currentTableElem  : Option[html.Element]                  = None,
      var observedWrapper   : Option[html.Element]                  = None,
      var wrapperObserver   : Option[ResizeObserver]                = None,
      var cellObserver      : Option[ResizeObserver]                = None,
      val observedCells     : WeakMap[html.Element, Boolean]        = new WeakMap[html.Element, Boolean]()
    )

    var initialized   : Boolean                              = false
    var unlockPending : Boolean                              = false
    val tableStates   : WeakMap[html.Element, TableState]    = new WeakMap[html.Element, TableState]()
    val statesByScope : mutable.LinkedHashMap[String, TableState] = mutable.LinkedHashMap.empty

    def hasResizeObserver: Boolean =
      ! js.isUndefined(dom.window.asInstanceOf[js.Dynamic].ResizeObserver)

    def storageKeyForCurrentPath: String =
      s"$StorageKeyPrefix${dom.window.location.pathname}"

    def logicalTableKey(wrapperElem: html.Element): String = {
      val allWrappers = dom.document.querySelectorAllT(".orbeon .fr-search-results")
      val idx         = math.max(0, allWrappers.indexOf(wrapperElem))
      s"${dom.window.location.pathname}#$idx"
    }

    def hasActiveSearchUrlParam: Boolean = {
      val search = dom.window.location.search
      search.contains("fr-search=") ||
        search.contains("fr-search-app=") ||
        search.contains("fr-search-form=")
    }

    def ensureWrapperAccessibility(wrapperElem: html.Element): Unit = {
      if (! wrapperElem.hasAttribute("tabindex"))
        wrapperElem.tabIndex = 0
      if (! wrapperElem.hasAttribute("role"))
        wrapperElem.setAttribute("role", "region")
      if (! wrapperElem.hasAttribute("aria-label") && ! wrapperElem.hasAttribute("aria-labelledby"))
        Option(dom.document.title).flatMap(_.trimAllToOpt).foreach(wrapperElem.setAttribute("aria-label", _))
    }

    def clearFixedTableLayout(tableElem: html.Element): Unit = {
      tableElem.classList.remove(FixedTableClass)
      tableElem.classList.remove(MeasuringTableClass)
      tableElem.style.tableLayout = ""
      tableElem.style.width       = ""
      tableElem.querySelectorOpt(s":scope > colgroup.$ColgroupClass").foreach(_.remove())
    }

    def isTableLaidOut(wrapperElem: html.Element, tableElem: html.Element): Boolean =
      dom.document.body.contains(tableElem) &&
        tableElem.closestOpt(".xforms-disabled, .xforms-hidden, .xforms-initially-hidden").isEmpty &&
        (wrapperElem.clientWidth > 0 || ! hasResizeObserver)

    def isRenderedElement(elem: html.Element): Boolean =
      ! elem.hasAnyClass(HiddenOrDelimiterClasses*) &&
        dom.window.getComputedStyle(elem).display != "none"

    def findVisibleHeaders(tableElem: html.Element): Vector[html.Element] =
      tableElem
        .querySelectorOpt(":scope > thead > tr:not(.header-groups, .xforms-repeat-begin-end, .xforms-repeat-delimiter, .xforms-group-begin-end, .xforms-disabled, .xforms-hidden)")
        .toVector
        .flatMap(_.childrenT)
        .filter(el => el.localName == "th" && isRenderedElement(el))

    def findVisibleBodyRows(tableElem: html.Element): Vector[html.Element] =
      tableElem
        .querySelectorOpt(":scope > tbody")
        .toVector
        .flatMap(_.childrenT)
        .filter(el => el.localName == "tr" && isRenderedElement(el))

    def findVisibleRowCells(rowElem: html.Element): Vector[html.Element] =
      rowElem
        .childrenT
        .iterator
        .filter(el => el.localName == "td" && isRenderedElement(el))
        .toVector

    def extractHeaderLabelText(thElem: html.Element): String =
      thElem
        .querySelectorOpt("button.btn-link > .xforms-output")
        .orElse(thElem.querySelectorOpt("button.btn-link"))
        .map { el =>
          val clone = el.cloneNodeT(deep = true)
          clone.querySelectorAllT(".caret-container").foreach(_.remove())
          clone.textContent.trimAllToEmpty
        }
        .getOrElse {
          val clone = thElem.cloneNodeT(deep = true)
          clone.querySelectorAllT(".xforms-hidden, .visually-hidden").foreach(_.remove())
          clone.textContent.trimAllToEmpty
        }

    def classifyColumns(visibleThs: Vector[html.Element]): Vector[ColumnDescriptor] = {
      val seenCounts = mutable.HashMap.empty[String, Int]
      def uniqueKey(base: String): String = {
        val count = seenCounts.getOrElse(base, 0)
        seenCounts.put(base, count + 1)
        if (count == 0) base else s"$base#$count"
      }

      var dynCounter = 0
      visibleThs.zipWithIndex.map { case (th, idx) =>
        val (rawKey, kind) =
          if (th.hasAnyClass("fr-repeat-selection-checkbox", "fr-home-select")) {
            "_selection" -> ColumnKind.Selection
          } else if (th.hasClass("fr-repeat-labels")) {
            "_labels" -> ColumnKind.Labels
          } else if (th.hasClass("fr-form-actions")) {
            "_actions" -> ColumnKind.Actions
          } else if (th.hasClass("fr-status")) {
            "meta:status" -> ColumnKind.Status
          } else if (th.hasClass("fr-version")) {
            "meta:version" -> ColumnKind.Version
          } else if (th.hasClass("fr-created")) {
            "meta:created" -> ColumnKind.MetadataDate
          } else if (th.hasAnyClass("fr-last-modified", "fr-modified")) {
            "meta:last-modified" -> ColumnKind.MetadataDate
          } else if (th.hasClass("fr-created-by")) {
            "meta:created-by" -> ColumnKind.MetadataUser
          } else if (th.hasClass("fr-last-modified-by")) {
            "meta:last-modified-by" -> ColumnKind.MetadataUser
          } else if (th.hasClass("fr-workflow-stage")) {
            "meta:workflow-stage" -> ColumnKind.MetadataStage
          } else {
            val dIdx = dynCounter
            dynCounter += 1
            s"dyn:$dIdx" -> ColumnKind.Dynamic
          }

        ColumnDescriptor(
          index      = idx,
          key        = uniqueKey(rawKey),
          kind       = kind,
          thElem     = th,
          alignRight = th.hasAnyClass("fr-align-right", "fr-version", "fr-form-actions"),
          labelText  = extractHeaderLabelText(th)
        )
      }
    }

    // Exclude `_labels` from signature because `.fr-repeat-labels` toggles dynamically per page when drafts appear
    def computeColumnSignature(cols: Vector[ColumnDescriptor]): Vector[String] =
      cols
        .filterNot(_.kind == ColumnKind.Labels)
        .map(c => s"${c.key}:${c.labelText}")

    def findTableStateOpt(tableElem: html.Element): Option[TableState] =
      tableStates
        .get(tableElem)
        .toOption
        .orElse(tableElem.closestOpt(".fr-search-results").flatMap(w => statesByScope.get(logicalTableKey(w))))

    def getOrCreateTableState(tableElem: html.Element, wrapperElem: html.Element): TableState = {
      val scopeKey = logicalTableKey(wrapperElem)
      val state =
        tableStates
          .get(tableElem)
          .toOption
          .orElse(statesByScope.get(scopeKey))
          .getOrElse {
            val created = new TableState()
            loadManualWidthsFromSession(created)
            if (hasResizeObserver) {
              val cellObs = new ResizeObserver((entries, _) =>
                entries.foreach { entry =>
                  entry.target match {
                    case el: html.Element => refreshSingleCellTruncation(el)
                    case _                =>
                  }
                }
              )
              created.cellObserver = Some(cellObs)
            }
            created
          }

      state.currentTableElem = Some(tableElem)
      statesByScope.put(scopeKey, state)
      tableStates.set(tableElem, state)

      if (hasResizeObserver && ! state.observedWrapper.contains(wrapperElem)) {
        state.wrapperObserver.foreach(_.disconnect())
        val wrapObs = new ResizeObserver((_, _) => {
          state.currentTableElem.foreach { activeTable =>
            val w = wrapperElem.clientWidth
            if (w > 0 && isTableLaidOut(wrapperElem, activeTable) && ! activeTable.hasClass("fr-table-no-form-data")) {
              if (! state.isLocked) {
                syncTable(activeTable)
              } else if (w != state.lastWrapperWidth) {
                state.lastWrapperWidth = w
                val cols = classifyColumns(findVisibleHeaders(activeTable))
                if (cols.nonEmpty) {
                  applyWidthsToColgroup(wrapperElem, activeTable, cols, state)
                  observeAndRefreshTruncations(activeTable, state)
                }
              }
            }
          }
        })
        wrapObs.observe(wrapperElem)
        state.observedWrapper = Some(wrapperElem)
        state.wrapperObserver = Some(wrapObs)
      }

      state
    }

    def loadManualWidthsFromSession(state: TableState): Unit =
      readSessionPayloadOpt.foreach { payload =>
        payload.manualWidths.foreach { case (k, v) =>
          if (v >= MinManualWidthPx)
            state.manualWidths.put(k, v)
        }
      }

    def tryRestoreLockedWidthsFromSession(sig: Vector[String], state: TableState): Boolean =
      readSessionPayloadOpt.exists { payload =>
        val matches = payload.signature == sig && payload.lockedWidths.nonEmpty
        if (matches) {
          state.lockedWidths.clear()
          payload.lockedWidths.foreach { case (k, v) => state.lockedWidths.put(k, v) }
          state.manualWidths.foreach  { case (k, v) => state.lockedWidths.put(k, v) }
        }
        matches
      }

    def persistToSession(sig: Vector[String], state: TableState): Unit =
      Try {
        val payload = StoredWidthPayload(
          signature    = sig,
          lockedWidths = state.lockedWidths.toMap,
          manualWidths = state.manualWidths.toMap
        )
        dom.window.sessionStorage.setItem(storageKeyForCurrentPath, payload.asJson.noSpaces)
      }

    def clearStoredLockedWidthsForCurrentPath(): Unit =
      Try {
        readSessionPayloadOpt match {
          case Some(payload) if payload.manualWidths.nonEmpty =>
            val updated = payload.copy(lockedWidths = Map.empty)
            dom.window.sessionStorage.setItem(storageKeyForCurrentPath, updated.asJson.noSpaces)
          case _ =>
            dom.window.sessionStorage.removeItem(storageKeyForCurrentPath)
        }
      }

    def readSessionPayloadOpt: Option[StoredWidthPayload] =
      Try(Option(dom.window.sessionStorage.getItem(storageKeyForCurrentPath)))
        .toOption
        .flatten
        .flatMap(raw => decode[StoredWidthPayload](raw).toOption)

    def horizontalPaddingOf(elem: html.Element): Double = {
      val st = dom.window.getComputedStyle(elem)
      val pl = DomSupport.parseDoubleIgnoreTail(st.paddingLeft).getOrElse(0.0)
      val pr = DomSupport.parseDoubleIgnoreTail(st.paddingRight).getOrElse(0.0)
      pl + pr
    }

    def effectiveCellHorizontalPadding(tdElem: html.Element, fallbackPadPx: Double): Double = {
      val tdPad = horizontalPaddingOf(tdElem)
      if (tdPad > 0.0) {
        tdPad
      } else {
        val linkPad =
          tdElem
            .querySelectorOpt(":scope > a:not(.fr-summary-cell-link):not(.xforms-hidden)")
            .map(horizontalPaddingOf)
            .getOrElse(0.0)
        if (linkPad > 0.0) linkPad else fallbackPadPx
      }
    }

    def measureFontAndPadding(tableElem: html.Element): (Double, Double) = {
      val probe = dom.document.createElement("span").asInstanceOf[html.Element]
      probe.style.cssText = "position:absolute;visibility:hidden;white-space:nowrap;font-variant-numeric:tabular-nums;"
      probe.textContent   = "0000000000"
      tableElem.appendChild(probe)
      val measuredCh = probe.getBoundingClientRect().width / 10.0
      probe.remove()
      val ch = if (measuredCh > 1.0) measuredCh else 8.0

      val sampleCellOpt =
        tableElem.querySelectorOpt(
          "tbody td.fr-td, tbody td.fr-title, tbody td.fr-form-name, tbody td.fr-created, tbody td.fr-modified"
        )
      val padPx =
        sampleCellOpt
          .map(effectiveCellHorizontalPadding(_, 0.0))
          .filter(_ > 0.0)
          .getOrElse(20.0)

      (ch, padPx)
    }

    def measureHeaderMinWidth(thElem: html.Element): Double =
      thElem.querySelectorOpt("button.btn-link") match {
        case Some(btn) =>
          val prevWidth   = btn.style.width
          btn.style.width = "max-content"
          val measured    = math.max(btn.scrollWidth.toDouble, btn.getBoundingClientRect().width)
          btn.style.width = prevWidth
          val raw         = if (measured > 0.0) measured + 8.0 else MinManualWidthPx
          math.ceil(math.max(MinManualWidthPx, raw))
        case None =>
          math.ceil(math.max(SelectionMinPx, thElem.getBoundingClientRect().width))
      }

    def findFlexColumnKeyOpt(cols: Vector[ColumnDescriptor]): Option[String] = {
      val dataCols = cols.filter(_.isDataColumn)
      dataCols
        .find(_.thElem.hasClass("fr-title"))
        .orElse(dataCols.findLast(c => c.kind == ColumnKind.Dynamic && ! c.alignRight))
        .orElse(dataCols.findLast(_.kind == ColumnKind.Dynamic))
        .orElse(dataCols.lastOption)
        .map(_.key)
    }

    def areAllColumnCellsBlank(rows: Vector[html.Element], colIndex: Int): Boolean =
      rows.isEmpty || rows.forall { row =>
        val cells = findVisibleRowCells(row)
        cells.lift(colIndex).forall { td =>
          val hasVisualMedia =
            td.querySelectorOpt("img, svg, canvas, .fr-status-pills, .fr-table-pill, .fr-row-actions, [role='checkbox']").isDefined
          (! hasVisualMedia) && td.textContent.trimAllToEmpty.isEmpty
        }
      }

    def measureMaxActionsWidth(rows: Vector[html.Element], colIndex: Int, fallbackPadPx: Double): Double =
      rows.iterator.flatMap { r =>
        findVisibleRowCells(r).lift(colIndex).map { td =>
          val pad    = effectiveCellHorizontalPadding(td, fallbackPadPx)
          val innerW =
            td.querySelectorOpt(".fr-row-actions")
              .map(el => math.max(el.scrollWidth.toDouble, el.getBoundingClientRect().width))
              .getOrElse(0.0)
          if (innerW > 0.0) innerW + pad else 0.0
        }
      }.maxOption.getOrElse(0.0)

    def ensureActionsColumnFits(
      tableElem : html.Element,
      cols      : Vector[ColumnDescriptor],
      state     : TableState
    ): Boolean = {
      val actionCols = cols.filter(_.kind == ColumnKind.Actions)
      if (actionCols.isEmpty) {
        false
      } else {
        val (_, padPx) = measureFontAndPadding(tableElem)
        val rows       = findVisibleBodyRows(tableElem)
        var changed    = false
        actionCols.foreach { col =>
          val neededPx  = math.ceil(measureMaxActionsWidth(rows, col.index, padPx) + 2.0)
          val currentPx = state.lockedWidths.getOrElse(col.key, 0.0)
          // Only widen when a genuinely wider button combination appears (ignoring minor subpixel rounding)
          if (neededPx > currentPx + 8.0) {
            state.lockedWidths.put(col.key, neededPx)
            changed = true
          }
        }
        changed
      }
    }

    def clamp(minVal: Double, value: Double, maxVal: Double): Double =
      math.max(minVal, math.min(maxVal, value))

    def measureInitialWidths(
      tableElem : html.Element,
      cols      : Vector[ColumnDescriptor],
      state     : TableState
    ): Unit = {
      val (ch, padPx)  = measureFontAndPadding(tableElem)
      val flexKeyOpt   = findFlexColumnKeyOpt(cols)
      val dataColCount = cols.count(_.isDataColumn)

      // Temporarily switch to intrinsic auto sizing so Page 1 column widths can be sampled cleanly
      tableElem.classList.remove(FixedTableClass)
      tableElem.classList.add(MeasuringTableClass)
      tableElem.style.tableLayout = "auto"
      tableElem.style.width       = "auto"
      tableElem.querySelectorOpt(s":scope > colgroup.$ColgroupClass").foreach { cg =>
        cg.childrenT.foreach(_.style.width = "")
      }

      try {
        val bodyRows = findVisibleBodyRows(tableElem)
        state.lockedWidths.clear()

        cols.foreach { col =>
          state.manualWidths.get(col.key) match {
            case Some(manualPx) =>
              state.lockedWidths.put(col.key, manualPx)
            case None =>
              val pageW     = math.ceil(col.thElem.getBoundingClientRect().width)
              val headerMin = measureHeaderMinWidth(col.thElem)

              val resolvedPx = col.kind match {
                case ColumnKind.Selection =>
                  math.max(SelectionMinPx, pageW)

                case ColumnKind.Labels =>
                  math.max(headerMin, pageW + math.ceil(0.5 * ch))

                case ColumnKind.Actions =>
                  val actionsW = measureMaxActionsWidth(bodyRows, col.index, padPx)
                  math.max(SelectionMinPx, math.max(pageW, math.ceil(actionsW + 2.0)))

                case ColumnKind.Status =>
                  val minPx = math.max(headerMin, math.ceil(StatusMinCh * ch + padPx))
                  val maxPx = math.max(minPx, math.ceil(DynamicMaxCh * ch + padPx))
                  clamp(minPx, pageW + math.ceil(StatusHeadroomCh * ch), maxPx)

                case ColumnKind.Version =>
                  val maxPx = math.max(headerMin, math.ceil(VersionMaxCh * ch + padPx))
                  clamp(headerMin, pageW + math.ceil(DynamicCompactHeadroomCh * ch), maxPx)

                case ColumnKind.MetadataDate =>
                  val target = math.ceil(MetadataDateCh * ch + padPx)
                  math.max(headerMin, math.max(target, pageW))

                case ColumnKind.MetadataUser =>
                  val minPx = math.max(headerMin, math.ceil(MetadataUserMinCh * ch + padPx))
                  val maxPx = math.max(minPx, math.ceil(MetadataUserMaxCh * ch + padPx))
                  clamp(minPx, pageW + math.ceil(MetadataHeadroomCh * ch), maxPx)

                case ColumnKind.MetadataStage =>
                  val minPx = math.max(headerMin, math.ceil(MetadataStageMinCh * ch + padPx))
                  val maxPx = math.max(minPx, math.ceil(MetadataStageMaxCh * ch + padPx))
                  clamp(minPx, pageW + math.ceil(MetadataHeadroomCh * ch), maxPx)

                case ColumnKind.Dynamic if flexKeyOpt.contains(col.key) && dataColCount >= 1 =>
                  val minPx = math.max(headerMin, math.ceil(DynamicFlexMinCh * ch + padPx))
                  val capPx = math.max(minPx, math.ceil(DynamicFlexCapCh * ch + padPx))
                  clamp(minPx, pageW, capPx)

                case ColumnKind.Dynamic =>
                  val allBlank = areAllColumnCellsBlank(bodyRows, col.index)
                  if (allBlank) {
                    math.max(headerMin, math.ceil(DynamicEmptyFloorCh * ch + padPx))
                  } else {
                    val isCompactWidgetCol =
                      bodyRows.nonEmpty && bodyRows.forall { r =>
                        findVisibleRowCells(r).lift(col.index).forall { td =>
                          td.textContent.trimAllToEmpty.isEmpty ||
                            td.querySelectorOpt(".fr-status-pills, [role='checkbox']").isDefined
                        }
                      }
                    val headroomCh =
                      if (isCompactWidgetCol) DynamicCompactHeadroomCh
                      else if (col.alignRight) DynamicNumHeadroomCh
                      else DynamicTextHeadroomCh
                    val capCh =
                      if (pageW > DynamicWideThresholdCh * ch + padPx) DynamicWideMaxCh
                      else DynamicMaxCh
                    val maxPx = math.max(headerMin, math.ceil(capCh * ch + padPx))
                    clamp(headerMin, pageW + math.ceil(headroomCh * ch), maxPx)
                  }
              }

              state.lockedWidths.put(col.key, math.ceil(resolvedPx))
          }
        }
      } finally {
        tableElem.classList.remove(MeasuringTableClass)
      }
    }

    def measureMissingColumns(
      tableElem   : html.Element,
      allCols     : Vector[ColumnDescriptor],
      missingCols : Vector[ColumnDescriptor],
      state       : TableState
    ): Unit = {
      val (ch, padPx) = measureFontAndPadding(tableElem)
      val rows        = findVisibleBodyRows(tableElem)

      missingCols.foreach { col =>
        val headerMin = measureHeaderMinWidth(col.thElem)
        val maxCellW  =
          rows.iterator.flatMap { r =>
            findVisibleRowCells(r).lift(col.index).map(measureCellIntrinsicWidth(_, padPx))
          }.maxOption.getOrElse(0.0)
        val measured = math.ceil(math.max(headerMin, maxCellW + math.ceil(0.5 * ch)))
        state.lockedWidths.put(col.key, measured)
      }
    }

    def measureCellIntrinsicWidth(tdElem: html.Element, fallbackPadPx: Double): Double = {
      val padPx = effectiveCellHorizontalPadding(tdElem, fallbackPadPx)
      val innerW =
        tdElem
          .querySelectorOpt(".fr-summary-cell-value > *")
          .orElse(tdElem.querySelectorOpt(".fr-form-slug:not(.xforms-hidden) > .xforms-output:not(.xforms-disabled)"))
          .orElse(tdElem.querySelectorOpt(":scope > a:not(.fr-summary-cell-link):not(.xforms-hidden) > .xforms-output:not(.xforms-disabled)"))
          .orElse(tdElem.querySelectorOpt(".fr-status-pills"))
          .orElse(tdElem.querySelectorOpt(".fr-table-pill:not(.xforms-disabled)"))
          .orElse(tdElem.querySelectorOpt(".xforms-output:not(.xforms-disabled)"))
          .orElse(tdElem.childrenT.find(isRenderedElement))
          .map(el => math.max(el.scrollWidth.toDouble, el.getBoundingClientRect().width))
          .getOrElse(tdElem.scrollWidth.toDouble)
      innerW + padPx
    }

    def ensureColgroup(tableElem: html.Element, count: Int): Vector[html.Element] = {
      val colgroup =
        tableElem.querySelectorOpt(s":scope > colgroup.$ColgroupClass").getOrElse {
          val cg = dom.document.createElement("colgroup").asInstanceOf[html.Element]
          cg.className = ColgroupClass
          tableElem.insertBefore(cg, tableElem.firstChild)
          cg
        }

      while (colgroup.children.length > count)
        colgroup.removeChild(colgroup.lastElementChild)

      while (colgroup.children.length < count)
        colgroup.appendChild(dom.document.createElement("col"))

      colgroup.childrenT.toVector
    }

    def applyWidthsToColgroup(
      wrapperElem : html.Element,
      tableElem   : html.Element,
      cols        : Vector[ColumnDescriptor],
      state       : TableState
    ): Unit = {
      val colElems     = ensureColgroup(tableElem, cols.length)
      val wrapperWidth = wrapperElem.clientWidth
      state.lastWrapperWidth = wrapperWidth

      val flexKeyOpt = findFlexColumnKeyOpt(cols)
      val manualFlex = flexKeyOpt.exists(state.manualWidths.contains)
      val totalMin   = cols.map(c => state.lockedWidths.getOrElse(c.key, 100.0)).sum

      val fitsContainer = wrapperWidth > 0 && ! manualFlex && totalMin <= wrapperWidth

      cols.zip(colElems).foreach { case (col, colElem) =>
        colElem.setAttribute("data-fr-col", col.key)
        val px = math.round(state.lockedWidths.getOrElse(col.key, 100.0))
        if (fitsContainer && flexKeyOpt.contains(col.key))
          colElem.style.width = "auto"
        else
          colElem.style.width = s"${px}px"

        col.thElem
          .querySelectorOpt(s":scope > .$ColResizerClass")
          .foreach(_.setAttribute("aria-valuenow", px.toString))
      }

      tableElem.style.width =
        if (fitsContainer || wrapperWidth <= 0 && ! manualFlex) "100%"
        else s"${math.round(totalMin)}px"

      tableElem.style.tableLayout = "fixed"
      tableElem.classList.add(FixedTableClass)
    }

    def attachResizers(
      tableElem : html.Element,
      cols      : Vector[ColumnDescriptor]
    ): Unit =
      cols.filter(_.isResizable).foreach { col =>
        val th          = col.thElem
        val existingOpt = th.querySelectorOpt(s":scope > .$ColResizerClass")
        val handle = existingOpt.getOrElse {
          val h = dom.document.createElement("div").asInstanceOf[html.Element]
          h.className = ColResizerClass
          h.tabIndex  = 0
          h.setAttribute("role",             "separator")
          h.setAttribute("aria-orientation", "vertical")
          h.setAttribute("aria-valuemin",    MinManualWidthPx.toInt.toString)
          wireResizerEvents(tableElem, h)
          th.appendChild(h)
          h
        }
        handle.setAttribute("data-fr-col-key", col.key)

        th.querySelectorOpt("button.btn-link > .xforms-output, button.btn-link")
          .foreach { labelElem =>
            val labelId = DomSupport.generateIdIfNeeded(labelElem)
            handle.setAttribute("aria-labelledby", labelId)
          }
      }

    def wireResizerEvents(tableElem: html.Element, handleElem: html.Element): Unit = {
      handleElem.addEventListener("click", (e: dom.MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
      })

      handleElem.addEventListener("pointerdown", (e: dom.PointerEvent) =>
        if (e.button == 0) {
          e.preventDefault()
          e.stopPropagation()

          for {
            colKey      <- handleElem.attValueOpt("data-fr-col-key")
            thElem      <- handleElem.closestOpt("th")
            wrapperElem <- tableElem.closestOpt(".fr-search-results")
            state       <- findTableStateOpt(tableElem)
          } locally {
            val startX     = e.clientX
            val startWidth = thElem.getBoundingClientRect().width
            handleElem.classList.add("fr-dragging")
            Try(handleElem.setPointerCapture(e.pointerId))

            lazy val onMove: js.Function1[dom.PointerEvent, Unit] = (ev: dom.PointerEvent) => {
              val nextW = math.max(MinManualWidthPx, math.round(startWidth + (ev.clientX - startX)).toDouble)
              setManualColumnWidth(wrapperElem, tableElem, state, colKey, nextW)
            }

            lazy val onUp: js.Function1[dom.PointerEvent, Unit] = (ev: dom.PointerEvent) => {
              handleElem.classList.remove("fr-dragging")
              Try(if (handleElem.hasPointerCapture(ev.pointerId)) handleElem.releasePointerCapture(ev.pointerId))
              handleElem.removeEventListener("pointermove",   onMove)
              handleElem.removeEventListener("pointerup",     onUp)
              handleElem.removeEventListener("pointercancel", onUp)
              state.lastSignature.foreach(persistToSession(_, state))
              observeAndRefreshTruncations(tableElem, state)
            }

            handleElem.addEventListener("pointermove",   onMove)
            handleElem.addEventListener("pointerup",     onUp)
            handleElem.addEventListener("pointercancel", onUp)
          }
        }
      )

      handleElem.addEventListener("dblclick", (e: dom.MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
        for {
          colKey      <- handleElem.attValueOpt("data-fr-col-key")
          wrapperElem <- tableElem.closestOpt(".fr-search-results")
          state       <- findTableStateOpt(tableElem)
        } locally {
          autoFitColumnToCurrentPage(wrapperElem, tableElem, state, colKey)
        }
      })

      handleElem.addEventListener("keydown", (e: dom.KeyboardEvent) =>
        if (e.key == "ArrowLeft" || e.key == "ArrowRight") {
          e.preventDefault()
          e.stopPropagation()
          for {
            colKey      <- handleElem.attValueOpt("data-fr-col-key")
            thElem      <- handleElem.closestOpt("th")
            wrapperElem <- tableElem.closestOpt(".fr-search-results")
            state       <- findTableStateOpt(tableElem)
          } locally {
            val step     = if (e.shiftKey) KeyboardShiftStepPx else KeyboardStepPx
            val delta    = if (e.key == "ArrowRight") step else -step
            val currentW =
              Option(thElem.getBoundingClientRect().width)
                .filter(_ > 0.0)
                .orElse(state.lockedWidths.get(colKey))
                .getOrElse(120.0)
            val nextW = math.max(MinManualWidthPx, math.round(currentW + delta).toDouble)
            setManualColumnWidth(wrapperElem, tableElem, state, colKey, nextW)
            state.lastSignature.foreach(persistToSession(_, state))
            observeAndRefreshTruncations(tableElem, state)
          }
        }
      )
    }

    def setManualColumnWidth(
      wrapperElem : html.Element,
      tableElem   : html.Element,
      state       : TableState,
      colKey      : String,
      widthPx     : Double
    ): Unit = {
      val cols = classifyColumns(findVisibleHeaders(tableElem))
      // Materialize actual rendered width of any `auto` flex column before dragging another column if needed
      cols.foreach { c =>
        if (! state.lockedWidths.contains(c.key))
          state.lockedWidths.put(c.key, math.ceil(c.thElem.getBoundingClientRect().width))
      }
      state.manualWidths.put(colKey, widthPx)
      state.lockedWidths.put(colKey, widthPx)
      applyWidthsToColgroup(wrapperElem, tableElem, cols, state)
    }

    def autoFitColumnToCurrentPage(
      wrapperElem : html.Element,
      tableElem   : html.Element,
      state       : TableState,
      colKey      : String
    ): Unit = {
      val cols = classifyColumns(findVisibleHeaders(tableElem))
      cols.find(_.key == colKey).foreach { col =>
        val (_, padPx) = measureFontAndPadding(tableElem)
        val headerMin  = measureHeaderMinWidth(col.thElem)
        val rows       = findVisibleBodyRows(tableElem)
        val maxBodyW   =
          rows.iterator.flatMap { r =>
            findVisibleRowCells(r).lift(col.index).map(measureCellIntrinsicWidth(_, padPx))
          }.maxOption.getOrElse(0.0)

        val fittedPx = math.ceil(math.max(headerMin, maxBodyW + 4.0))
        state.manualWidths.remove(colKey)
        state.lockedWidths.put(colKey, fittedPx)
        applyWidthsToColgroup(wrapperElem, tableElem, cols, state)
        state.lastSignature.foreach(persistToSession(_, state))
        observeAndRefreshTruncations(tableElem, state)
      }
    }

    def observeAndRefreshTruncations(tableElem: html.Element, state: TableState): Unit = {
      val candidates =
        tableElem
          .querySelectorAllT(TruncationCandidateSelector)
          .iterator
          .filterNot { el =>
            (el.localName == "td" &&
              el.querySelectorOpt(":scope > a:not(.fr-summary-cell-link):not(.xforms-hidden), :scope > .fr-form-slug:not(.xforms-hidden)").isDefined) ||
            (el.localName == "a" &&
              el.querySelectorOpt(":scope > .fr-form-slug:not(.xforms-hidden)").isDefined)
          }
          .toList

      candidates.foreach { el =>
        if (! state.observedCells.has(el)) {
          state.observedCells.set(el, true)
          state.cellObserver.foreach(_.observe(el))
        }
        refreshSingleCellTruncation(el)
      }
    }

    def refreshSingleCellTruncation(elem: html.Element): Unit = {
      val hiddenPx    = math.ceil((elem.scrollWidth - elem.clientWidth).toDouble).toInt
      val isTruncated = elem.clientWidth > 0 && hiddenPx > 1
      elem.toggleClass(CellTruncatedClass, isTruncated)
      if (isTruncated) {
        elem.style.setProperty(ScrollWidthCssVar, hiddenPx.toString)
        val alreadyFocusableOrLinked =
          elem.localName == "a" ||
            elem.closestOpt("a:not(.xforms-hidden)").isDefined ||
            elem.closestOpt("td").flatMap(_.querySelectorOpt("a.fr-summary-cell-link:not(.xforms-hidden)")).isDefined
        if (! alreadyFocusableOrLinked && ! elem.hasAttribute("tabindex"))
          elem.tabIndex = 0
      } else {
        elem.style.removeProperty(ScrollWidthCssVar)
        if (elem.attValueOpt("tabindex").contains("0"))
          elem.removeAttribute("tabindex")
      }
    }
  }
}
