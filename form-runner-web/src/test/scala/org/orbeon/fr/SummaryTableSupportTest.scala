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

import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js


class SummaryTableSupportTest extends AnyFunSpecLike {

  private def mockRect(elem: dom.Element, width: Double, height: Double = 32.0): Unit =
    elem.asInstanceOf[js.Dynamic].getBoundingClientRect = () =>
      js.Dynamic.literal(
        width  = width,
        height = height,
        top    = 0.0,
        left   = 0.0,
        right  = width,
        bottom = height
      )

  private def defineNumericProp(elem: dom.Element, prop: String, value: Int): Unit =
    js.Object.defineProperty(
      elem.asInstanceOf[js.Object],
      prop,
      js.Dynamic.literal(
        value        = value,
        configurable = true
      ).asInstanceOf[js.PropertyDescriptor]
    )

  private def buildSummaryDom(wrapperWidth: Int = 1200): (html.Element, html.Element) = {
    dom.window.sessionStorage.clear()
    dom.document.body.innerHTML =
      """<form class="xforms-form">
        |  <div class="fr-dynamic">
        |    <div class="fr-search-results fr-table-frame" tabindex="0" role="region" aria-label="Summary">
        |      <table class="table table-condensed">
        |        <thead>
        |          <tr>
        |            <th class="fr-repeat-selection-checkbox" scope="col"></th>
        |            <th class="fr-repeat-labels xforms-hidden" scope="col"></th>
        |            <th class="xforms-repeat-begin-end" style="display: none;"></th>
        |            <th id="th-created" class="fr-created" scope="col">
        |              <span class="fr-sortable-th">
        |                <button class="btn-link" type="button">
        |                  <span class="xforms-output"><output class="xforms-output-output">Created</output></span>
        |                  <span class="caret-container hover-caret" aria-hidden="true">▼</span>
        |                </button>
        |              </span>
        |            </th>
        |            <th id="th-modified" class="fr-last-modified" scope="col" aria-sort="descending">
        |              <span class="fr-sortable-th">
        |                <button class="btn-link" type="button">
        |                  <span class="xforms-output"><output class="xforms-output-output">Modified</output></span>
        |                  <span class="caret-container active-caret" aria-hidden="true">▼</span>
        |                </button>
        |              </span>
        |            </th>
        |            <th id="th-app" scope="col">
        |              <span class="fr-sortable-th">
        |                <button class="btn-link" type="button">
        |                  <span class="xforms-output"><output class="xforms-output-output">Application Name</output></span>
        |                  <span class="caret-container hover-caret" aria-hidden="true">▲</span>
        |                </button>
        |              </span>
        |            </th>
        |            <th id="th-form" scope="col">
        |              <span class="fr-sortable-th">
        |                <button class="btn-link" type="button">
        |                  <span class="xforms-output"><output class="xforms-output-output">Form Name</output></span>
        |                  <span class="caret-container hover-caret" aria-hidden="true">▲</span>
        |                </button>
        |              </span>
        |            </th>
        |            <th id="th-empty" scope="col">
        |              <span class="fr-sortable-th">
        |                <button class="btn-link" type="button">
        |                  <span class="xforms-output"><output class="xforms-output-output">Ref</output></span>
        |                  <span class="caret-container hover-caret" aria-hidden="true">▲</span>
        |                </button>
        |              </span>
        |            </th>
        |            <th id="th-title" scope="col">
        |              <span class="fr-sortable-th">
        |                <button class="btn-link" type="button">
        |                  <span class="xforms-output"><output class="xforms-output-output">Form Title</output></span>
        |                  <span class="caret-container hover-caret" aria-hidden="true">▲</span>
        |                </button>
        |              </span>
        |            </th>
        |          </tr>
        |        </thead>
        |        <tbody>
        |          <tr>
        |            <td class="fr-repeat-selection-checkbox"><input type="checkbox"/></td>
        |            <td class="fr-repeat-labels xforms-hidden"></td>
        |            <td class="fr-created" style="padding-left: 10px; padding-right: 10px;">
        |              <a href="/fr/orbeon/builder/edit/1" class="fr-summary-cell-link"><span class="xforms-output">09/13/22</span></a>
        |            </td>
        |            <td class="fr-modified">
        |              <a href="/fr/orbeon/builder/edit/1" class="fr-summary-cell-link"><span class="xforms-output">6:41 pm</span></a>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/builder/edit/1" class="fr-summary-cell-link"></a>
        |              <div class="fr-summary-cell-value"><span>orbeon-features</span></div>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/builder/edit/1" class="fr-summary-cell-link"></a>
        |              <div class="fr-summary-cell-value"><span>emergency-medical-consent</span></div>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/builder/edit/1" class="fr-summary-cell-link"></a>
        |              <div class="fr-summary-cell-value"><span></span></div>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/builder/edit/1" class="fr-summary-cell-link"></a>
        |              <div class="fr-summary-cell-value"><span>Orbeon Demo: Consent for Emergency Medical Treatment</span></div>
        |            </td>
        |          </tr>
        |        </tbody>
        |      </table>
        |    </div>
        |  </div>
        |</form>""".stripMargin
    dom.document.body.className = "orbeon"

    val wrapper = dom.document.querySelectorT(".fr-search-results")
    val table   = dom.document.querySelectorT(".fr-search-results table")
    defineNumericProp(wrapper, "clientWidth", wrapperWidth)

    mockRect(table.querySelectorT(".fr-repeat-selection-checkbox"), 32.0)
    mockRect(table.querySelectorT("#th-created"),  92.0)
    mockRect(table.querySelectorT("#th-modified"), 88.0)
    mockRect(table.querySelectorT("#th-app"),      140.0)
    mockRect(table.querySelectorT("#th-form"),     210.0)
    mockRect(table.querySelectorT("#th-empty"),    52.0)
    mockRect(table.querySelectorT("#th-title"),    340.0)

    (wrapper, table)
  }

  private def colWidthsByKey(table: html.Element): Map[String, String] =
    table
      .querySelectorAllT(s"colgroup.${SummaryTableSupport.ColgroupClass} > col")
      .map(col => col.getAttribute("data-fr-col") -> col.style.width)
      .toMap

  describe("SummaryTableSupport column-width locking") {

    it("must lock metadata and dynamic columns with headroom and keep widths invariant across paging and sorting") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)

      SummaryTableSupport.syncTable(table)

      assert(table.hasClass(SummaryTableSupport.FixedTableClass))
      assert(table.style.tableLayout == "fixed")
      assert(table.style.width == "100%")

      val initialCols = colWidthsByKey(table)
      // 7 visible columns (`fr-repeat-labels` is `.xforms-hidden` and `xforms-repeat-begin-end` is `display: none`)
      assert(initialCols.size == 7)
      assert(initialCols("_selection") == "32px")
      // Metadata dates reserve at least `10.5 * 8 + 20 = 104px`
      assert(initialCols("meta:created") == "104px")
      assert(initialCols("meta:last-modified") == "104px")
      // Dynamic `app` (`140px`) gets `+2.5 * 8 = +20px` headroom -> `160px`
      assert(initialCols("dyn:0") == "160px")
      // Dynamic `form` (`210px`) gets `+20px` headroom -> `230px` (within `28 * 8 + 20 = 244px` cap)
      assert(initialCols("dyn:1") == "230px")
      // Blank Page-1 dynamic column `Ref` (`52px`) gets `12 * 8 + 20 = 116px` empty floor
      assert(initialCols("dyn:2") == "116px")
      // Trailing flex column (`Form Title`) stretches via `auto` when total widths fit the container
      assert(initialCols("dyn:3") == "auto")

      // Simulate paging to Page 2 where intrinsic widths would have shifted dramatically
      mockRect(table.querySelectorT("#th-modified"), 72.0)
      mockRect(table.querySelectorT("#th-app"),      158.0)
      mockRect(table.querySelectorT("#th-form"),     110.0)
      table.querySelectorAllT("tbody .fr-summary-cell-value > span")(0).textContent = "orbeon-screenshots"
      table.querySelectorAllT("tbody .fr-summary-cell-value > span")(1).textContent = "quantity"

      SummaryTableSupport.syncTable(table)
      assert(colWidthsByKey(table) == initialCols)

      // Simulate sorting by `Form Name` ascending (caret & aria-sort change, header labels stay the same)
      table.querySelectorT("#th-modified").removeAttribute("aria-sort")
      table.querySelectorT("#th-form").setAttribute("aria-sort", "ascending")
      table.querySelectorT("#th-form .caret-container").textContent = "▲"

      SummaryTableSupport.syncTable(table)
      assert(colWidthsByKey(table) == initialCols)
    }

    it("must switch trailing flex column to explicit px and expand table width when narrower than total locked widths") {
      val (_, table) = buildSummaryDom(wrapperWidth = 700)

      SummaryTableSupport.syncTable(table)

      val cols = colWidthsByKey(table)
      assert(cols("dyn:3") != "auto")
      assert(table.style.width.endsWith("px"))
      assert(table.style.width != "100%")
    }

    it("must handle `.fr-repeat-labels` becoming visible on a later page without shifting locked neighbor columns") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)
      SummaryTableSupport.syncTable(table)
      val before = colWidthsByKey(table)
      assert(! before.contains("_labels"))

      // Page 2 introduces an Autosaved draft badge, unhiding `.fr-repeat-labels`
      val labelsTh = table.querySelectorT("th.fr-repeat-labels")
      val labelsTd = table.querySelectorT("td.fr-repeat-labels")
      labelsTh.classList.remove("xforms-hidden")
      labelsTd.classList.remove("xforms-hidden")
      labelsTd.innerHTML = """<span class="xforms-output label">Autosaved</span>"""
      mockRect(labelsTh, 68.0)
      defineNumericProp(labelsTd.firstElementChild, "scrollWidth", 64)

      // Even if other columns' natural rects changed on Page 2, their locked widths must not change
      mockRect(table.querySelectorT("#th-form"), 95.0)

      SummaryTableSupport.syncTable(table)
      val after = colWidthsByKey(table)
      assert(after.contains("_labels"))
      assert(after("meta:created") == before("meta:created"))
      assert(after("meta:last-modified") == before("meta:last-modified"))
      assert(after("dyn:0") == before("dyn:0"))
      assert(after("dyn:1") == before("dyn:1"))
      assert(after("dyn:2") == before("dyn:2"))
    }

    it("must unlock and re-measure un-resized dynamic columns on search reset while keeping manual column resizes") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)
      SummaryTableSupport.syncTable(table)

      // Manually resize `Application Name` (`dyn:0`) via keyboard ArrowRight (+24px with Shift)
      val appTh      = table.querySelectorT("#th-app")
      val appResizer = appTh.querySelectorT(s".${SummaryTableSupport.ColResizerClass}")
      mockRect(appTh, 160.0)
      appResizer.dispatchEvent(
        new dom.KeyboardEvent(
          "keydown",
          new dom.KeyboardEventInit {
            key      = "ArrowRight"
            shiftKey = true
            bubbles  = true
          }
        )
      )
      assert(colWidthsByKey(table)("dyn:0") == "184px")

      // Run a new search (`markUnlockForNextSync` + `syncAll`) where `Form Name` (`dyn:1`) is much narrower (`120px`)
      mockRect(table.querySelectorT("#th-form"), 120.0)
      SummaryTableSupport.markUnlockForNextSync()
      SummaryTableSupport.syncAll()

      val afterSearch = colWidthsByKey(table)
      // Manual width on `dyn:0` is preserved
      assert(afterSearch("dyn:0") == "184px")
      // Un-resized `dyn:1` re-measured from new Page 1 (`120 + 20 = 140px`)
      assert(afterSearch("dyn:1") == "140px")
    }

    it("must mark overflowing `.fr-summary-cell-value` elements with `.fr-cell-truncated` and `--fr-cell-scroll-width`") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)
      val titleCellValue = table.querySelectorAllT("tbody .fr-summary-cell-value").last
      defineNumericProp(titleCellValue, "clientWidth", 220)
      defineNumericProp(titleCellValue, "scrollWidth", 385)

      SummaryTableSupport.syncTable(table)

      assert(titleCellValue.hasClass(SummaryTableSupport.CellTruncatedClass))
      assert(titleCellValue.style.getPropertyValue(SummaryTableSupport.ScrollWidthCssVar) == "165")
      // Row has an active `.fr-summary-cell-link`, so no duplicate tabindex stop is added to `.fr-summary-cell-value`
      assert(! titleCellValue.hasAttribute("tabindex"))
    }

    it("must defer locking while `form.xforms-form` has `xforms-initially-hidden` and lock once revealed") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)
      val formElem   = table.closestT("form.xforms-form")
      formElem.classList.add("xforms-initially-hidden")

      // While hidden, rects would report 0 in a real browser
      mockRect(table.querySelectorT("#th-app"), 0.0)
      SummaryTableSupport.syncTable(table)

      assert(! table.hasClass(SummaryTableSupport.FixedTableClass))

      // Reveal form and restore real layout rects
      formElem.classList.remove("xforms-initially-hidden")
      mockRect(table.querySelectorT("#th-app"), 140.0)
      SummaryTableSupport.syncTable(table)

      assert(table.hasClass(SummaryTableSupport.FixedTableClass))
      assert(colWidthsByKey(table)("dyn:0") == "160px")
    }
  }
}
