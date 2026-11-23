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


class SearchResultsTableSupportTest extends AnyFunSpecLike {

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
    SearchResultsTableSupport.resetAllStates()
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
        |              <a href="/fr/orbeon/bookshelf/edit/1" class="fr-summary-cell-link"><span class="xforms-output">09/13/22</span></a>
        |            </td>
        |            <td class="fr-modified">
        |              <a href="/fr/orbeon/bookshelf/edit/1" class="fr-summary-cell-link"><span class="xforms-output">6:41 pm</span></a>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/bookshelf/edit/1" class="fr-summary-cell-link"></a>
        |              <div class="fr-summary-cell-value"><span>orbeon-features</span></div>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/bookshelf/edit/1" class="fr-summary-cell-link"></a>
        |              <div class="fr-summary-cell-value"><span>emergency-medical-consent</span></div>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/bookshelf/edit/1" class="fr-summary-cell-link"></a>
        |              <div class="fr-summary-cell-value"><span></span></div>
        |            </td>
        |            <td class="fr-td">
        |              <a href="/fr/orbeon/bookshelf/edit/1" class="fr-summary-cell-link"></a>
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

  private def publishedFormsMarkup(
    appName     : String  = "orbeon",
    formName    : String  = "bookshelf",
    title       : String  = "Orbeon Demo: Bookshelf",
    linked      : Boolean = true,
    showNewBtn  : Boolean = true
  ): String = {
    val linkClass = if (linked) "" else "xforms-hidden"
    val spanClass = if (linked) "xforms-hidden" else ""
    val newClass  = if (showNewBtn) "fr-row-action fr-row-action-new" else "fr-row-action fr-row-action-new xforms-hidden"
    s"""<div class="fr-search-results fr-table-frame" role="region" aria-label="Published Forms">
       |  <table class="fr-forms-table table table-condensed">
       |    <thead>
       |      <tr>
       |        <th id="th-title" class="fr-title" scope="col">
       |          <span class="fr-sortable-th">
       |            <button class="btn-link" type="button">
       |              <span class="xforms-output"><output class="xforms-output-output">Form Title</output></span>
       |              <span class="caret-container hover-caret" aria-hidden="true">▲</span>
       |            </button>
       |          </span>
       |        </th>
       |        <th id="th-app" class="fr-application-name" scope="col">
       |          <span class="fr-sortable-th">
       |            <button class="btn-link" type="button">
       |              <span class="xforms-output"><output class="xforms-output-output">Application Name</output></span>
       |              <span class="caret-container hover-caret" aria-hidden="true">▲</span>
       |            </button>
       |          </span>
       |        </th>
       |        <th id="th-form" class="fr-form-name" scope="col">
       |          <span class="fr-sortable-th">
       |            <button class="btn-link" type="button">
       |              <span class="xforms-output"><output class="xforms-output-output">Form Name</output></span>
       |              <span class="caret-container hover-caret" aria-hidden="true">▲</span>
       |            </button>
       |          </span>
       |        </th>
       |        <th id="th-version" class="fr-version" scope="col">
       |          <span class="fr-sortable-th">
       |            <button class="btn-link" type="button">
       |              <span class="xforms-output"><output class="xforms-output-output">Version</output></span>
       |              <span class="caret-container hover-caret" aria-hidden="true">▲</span>
       |            </button>
       |          </span>
       |        </th>
       |        <th id="th-modified" class="fr-last-modified" scope="col" aria-sort="descending">
       |          <span class="fr-sortable-th">
       |            <button class="btn-link" type="button">
       |              <span class="xforms-output"><output class="xforms-output-output">Modified</output></span>
       |              <span class="caret-container active-caret" aria-hidden="true">▼</span>
       |            </button>
       |          </span>
       |        </th>
       |        <th id="th-actions" class="fr-form-actions" scope="col">
       |          <span class="xforms-hidden">Operation</span>
       |        </th>
       |      </tr>
       |    </thead>
       |    <tbody>
       |      <tr>
       |        <td class="fr-title" style="padding: 0;">
       |          <a href="/fr/$appName/$formName/summary" class="$linkClass" style="padding-left: 16px; padding-right: 16px;">
       |            <span class="xforms-output"><output class="xforms-output-output">$title</output></span>
       |          </a>
       |          <span class="xforms-output $spanClass"><output class="xforms-output-output">$title</output></span>
       |        </td>
       |        <td class="fr-application-name" style="padding-left: 16px; padding-right: 16px;">
       |          <span class="xforms-trigger fr-table-pill fr-app-pill fr-pill-neutral">
       |            <button class="btn-link" type="button"><span class="xforms-output">$appName</span></button>
       |          </span>
       |        </td>
       |        <td class="fr-form-name" style="padding: 0;">
       |          <a href="/fr/$appName/$formName/summary" class="$linkClass" style="padding-left: 16px; padding-right: 16px;">
       |            <span class="fr-form-slug"><span class="xforms-output">$formName</span></span>
       |          </a>
       |          <span class="fr-form-slug $spanClass"><span class="xforms-output">$formName</span></span>
       |        </td>
       |        <td class="fr-version" style="padding-left: 16px; padding-right: 16px;">
       |          <span class="xforms-trigger fr-table-pill fr-version-badge fr-pill-neutral">
       |            <button class="btn-link" type="button"><span class="xforms-output">v1</span></button>
       |          </span>
       |        </td>
       |        <td class="fr-modified" style="padding: 0;">
       |          <a href="/fr/$appName/$formName/summary" class="$linkClass" style="padding-left: 16px; padding-right: 16px;">
       |            <span class="xforms-output">6:41 pm</span>
       |          </a>
       |        </td>
       |        <td class="fr-form-actions" style="padding-left: 16px; padding-right: 16px;">
       |          <div class="fr-row-actions">
       |            <a href="/fr/$appName/$formName/new" class="$newClass"><span>New</span></a>
       |            <a href="/fr/$appName/$formName/summary" class="fr-row-action fr-row-action-summary"><span>Summary</span></a>
       |          </div>
       |        </td>
       |      </tr>
       |    </tbody>
       |  </table>
       |</div>""".stripMargin
  }

  private def buildPublishedFormsDom(
    wrapperWidth : Int     = 1300,
    actionsWidth : Double  = 124.0,
    linked       : Boolean = true,
    showNewBtn   : Boolean = true
  ): (html.Element, html.Element) = {
    SearchResultsTableSupport.resetAllStates()
    dom.window.sessionStorage.clear()
    dom.document.body.innerHTML =
      s"""<form class="xforms-form">
         |  <div class="fr-published-forms" id="home-host">
         |    ${publishedFormsMarkup(linked = linked, showNewBtn = showNewBtn)}
         |  </div>
         |</form>""".stripMargin
    dom.document.body.className = "orbeon"

    val wrapper = dom.document.querySelectorT(".fr-search-results")
    val table   = dom.document.querySelectorT(".fr-forms-table")
    defineNumericProp(wrapper, "clientWidth", wrapperWidth)

    mockRect(table.querySelectorT("#th-title"),    320.0)
    mockRect(table.querySelectorT("#th-app"),      154.0)
    mockRect(table.querySelectorT("#th-form"),     165.0)
    mockRect(table.querySelectorT("#th-version"),  86.0)
    mockRect(table.querySelectorT("#th-modified"), 102.0)
    mockRect(table.querySelectorT("#th-actions"),  actionsWidth + 32.0)
    mockRect(table.querySelectorT(".fr-row-actions"), actionsWidth)

    (wrapper, table)
  }

  private def colWidthsByKey(table: html.Element): Map[String, String] =
    table
      .querySelectorAllT(s"colgroup.${SearchResultsTableSupport.ColgroupClass} > col")
      .map(col => col.getAttribute("data-fr-col") -> col.style.width)
      .toMap

  describe("SearchResultsTableSupport column-width locking") {

    it("must lock metadata and dynamic columns with headroom and keep widths invariant across paging and sorting") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)

      SearchResultsTableSupport.syncTable(table)

      assert(table.hasClass(SearchResultsTableSupport.FixedTableClass))
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

      SearchResultsTableSupport.syncTable(table)
      assert(colWidthsByKey(table) == initialCols)

      // Simulate sorting by `Form Name` ascending (caret & aria-sort change, header labels stay the same)
      table.querySelectorT("#th-modified").removeAttribute("aria-sort")
      table.querySelectorT("#th-form").setAttribute("aria-sort", "ascending")
      table.querySelectorT("#th-form .caret-container").textContent = "▲"

      SearchResultsTableSupport.syncTable(table)
      assert(colWidthsByKey(table) == initialCols)
    }

    it("must switch trailing flex column to explicit px and expand table width when narrower than total locked widths") {
      val (_, table) = buildSummaryDom(wrapperWidth = 700)

      SearchResultsTableSupport.syncTable(table)

      val cols = colWidthsByKey(table)
      assert(cols("dyn:3") != "auto")
      assert(table.style.width.endsWith("px"))
      assert(table.style.width != "100%")
    }

    it("must handle `.fr-repeat-labels` becoming visible on a later page without shifting locked neighbor columns") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)
      SearchResultsTableSupport.syncTable(table)
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

      SearchResultsTableSupport.syncTable(table)
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
      SearchResultsTableSupport.syncTable(table)

      // Manually resize `Application Name` (`dyn:0`) via keyboard ArrowRight (+24px with Shift)
      val appTh      = table.querySelectorT("#th-app")
      val appResizer = appTh.querySelectorT(s".${SearchResultsTableSupport.ColResizerClass}")
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
      SearchResultsTableSupport.markUnlockForNextSync()
      SearchResultsTableSupport.syncAll()

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

      SearchResultsTableSupport.syncTable(table)

      assert(titleCellValue.hasClass(SearchResultsTableSupport.CellTruncatedClass))
      assert(titleCellValue.style.getPropertyValue(SearchResultsTableSupport.ScrollWidthCssVar) == "165")
      // Row has an active `.fr-summary-cell-link`, so no duplicate tabindex stop is added to `.fr-summary-cell-value`
      assert(! titleCellValue.hasAttribute("tabindex"))
    }

    it("must defer locking while `form.xforms-form` has `xforms-initially-hidden` and lock once revealed") {
      val (_, table) = buildSummaryDom(wrapperWidth = 1400)
      val formElem   = table.closestT("form.xforms-form")
      formElem.classList.add("xforms-initially-hidden")

      mockRect(table.querySelectorT("#th-app"), 0.0)
      SearchResultsTableSupport.syncTable(table)

      assert(! table.hasClass(SearchResultsTableSupport.FixedTableClass))

      formElem.classList.remove("xforms-initially-hidden")
      mockRect(table.querySelectorT("#th-app"), 140.0)
      SearchResultsTableSupport.syncTable(table)

      assert(table.hasClass(SearchResultsTableSupport.FixedTableClass))
      assert(colWidthsByKey(table)("dyn:0") == "160px")
    }

    it("must stretch leading `th.fr-title` on `/fr/forms` and keep columns locked across `xxf:update='full'` DOM replacements") {
      val (wrapper, table) = buildPublishedFormsDom(wrapperWidth = 1300, actionsWidth = 124.0)

      SearchResultsTableSupport.syncTable(table)

      // Wrapper receives `tabindex="0"` for keyboard horizontal scroll
      assert(wrapper.getAttribute("tabindex") == "0")

      val initialCols = colWidthsByKey(table)
      // `Form Title` (`dyn:0` with `fr-title`) is first column and stretches via `auto`!
      assert(initialCols("dyn:0") == "auto")
      // `Application Name` (`154px` + `2.5 * 8 = 20px`) -> `174px`
      assert(initialCols("dyn:1") == "174px")
      // `Form Name` (`165px` + `20px`) -> `185px`
      assert(initialCols("dyn:2") == "185px")
      // `Version` (`86px` + `1 * 8 = 8px`) -> `94px`
      assert(initialCols("meta:version") == "94px")
      // `Modified` (`10.5 * 8 + 32px` link padding = `116px` > `102px`) -> `116px`
      assert(initialCols("meta:last-modified") == "116px")
      // `Actions` (`124 + 32 + 2 = 158px`), non-resizable
      assert(initialCols("_actions") == "158px")
      assert(table.querySelectorOpt(s"#th-actions .${SearchResultsTableSupport.ColResizerClass}").isEmpty)

      // Simulate `xxf:update="full"` replacing the entire `.fr-search-results` & `<table>` DOM subtree on Page 2
      // where `Application Name` is `orbeon-screenshots` and `Actions` only has `Summary` (`62px` instead of `124px`)
      val host = dom.document.getElementById("home-host").asInstanceOf[html.Element]
      host.innerHTML = publishedFormsMarkup(
        appName    = "orbeon-screenshots",
        formName   = "quantity",
        title      = "Quantity Control Demo",
        showNewBtn = false
      )

      val newWrapper = host.querySelectorT(".fr-search-results")
      val newTable   = host.querySelectorT(".fr-forms-table")
      assert(newTable ne table)
      defineNumericProp(newWrapper, "clientWidth", 1300)
      mockRect(newTable.querySelectorT("#th-title"),    210.0)
      mockRect(newTable.querySelectorT("#th-app"),      182.0)
      mockRect(newTable.querySelectorT("#th-form"),     110.0)
      mockRect(newTable.querySelectorT("#th-version"),  86.0)
      mockRect(newTable.querySelectorT("#th-modified"), 88.0)
      mockRect(newTable.querySelectorT("#th-actions"),  94.0)
      mockRect(newTable.querySelectorT(".fr-row-actions"), 62.0)

      SearchResultsTableSupport.syncAll()

      assert(newWrapper.getAttribute("tabindex") == "0")
      assert(colWidthsByKey(newTable) == initialCols)
    }

    it("must monotonically widen `_actions` if Page 1 had single-button rows and Page 2 reveals `New` + `Summary`") {
      val (_, table) = buildPublishedFormsDom(wrapperWidth = 1300, actionsWidth = 64.0, showNewBtn = false)
      SearchResultsTableSupport.syncTable(table)

      val page1Cols = colWidthsByKey(table)
      assert(page1Cols("_actions") == "98px")

      // Page 2 has both `New` and `Summary` (`124px` + `32px` padding + `2px` = `158px`)
      mockRect(table.querySelectorT(".fr-row-actions"), 124.0)
      mockRect(table.querySelectorT("#th-app"), 190.0) // Neighbor natural width changes must still be ignored
      SearchResultsTableSupport.syncTable(table)

      val page2Cols = colWidthsByKey(table)
      assert(page2Cols("_actions") == "158px")
      assert(page2Cols("dyn:1") == page1Cols("dyn:1"))
      assert(page2Cols("dyn:2") == page1Cols("dyn:2"))
    }

    it("must skip `tr.header-groups` and disambiguate duplicate Local/Remote columns on `/fr/admin`") {
      SearchResultsTableSupport.resetAllStates()
      dom.window.sessionStorage.clear()
      dom.document.body.innerHTML =
        """<form class="xforms-form">
          |  <div class="fr-forms-admin">
          |    <div class="fr-search-results fr-table-frame" role="region" aria-label="Forms Admin">
          |      <table class="fr-forms-table table table-condensed">
          |        <thead>
          |          <tr class="header-groups">
          |            <th></th>
          |            <th colspan="2">Local</th>
          |            <th colspan="2">Remote</th>
          |            <th colspan="4">Form Identification</th>
          |          </tr>
          |          <tr>
          |            <th id="th-select" class="fr-home-select" scope="col"></th>
          |            <th id="th-local-status" class="fr-status" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Status</span></button></span>
          |            </th>
          |            <th id="th-local-mod" class="fr-last-modified" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Modified</span></button></span>
          |            </th>
          |            <th id="th-remote-status" class="fr-status" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Status</span></button></span>
          |            </th>
          |            <th id="th-remote-mod" class="fr-last-modified" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Modified</span></button></span>
          |            </th>
          |            <th id="th-title" class="fr-title" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Form Title</span></button></span>
          |            </th>
          |            <th id="th-app" class="fr-application-name" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Application Name</span></button></span>
          |            </th>
          |            <th id="th-form" class="fr-form-name" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Form Name</span></button></span>
          |            </th>
          |            <th id="th-ver" class="fr-version" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Version</span></button></span>
          |            </th>
          |          </tr>
          |        </thead>
          |        <tbody>
          |          <tr>
          |            <td class="fr-home-select"><input type="checkbox"/></td>
          |            <td class="fr-status" style="padding-left: 12px; padding-right: 12px;"><div class="fr-status-pills"><span>Available</span></div></td>
          |            <td class="fr-modified" style="padding-left: 12px; padding-right: 12px;"><span class="xforms-output">Nov 21</span></td>
          |            <td class="fr-status" style="padding-left: 12px; padding-right: 12px;"><div class="fr-status-pills"><span>Available</span><span>Newer</span></div></td>
          |            <td class="fr-modified" style="padding-left: 12px; padding-right: 12px;"><span class="xforms-output">Nov 22</span></td>
          |            <td class="fr-title" style="padding-left: 12px; padding-right: 12px;"><span class="xforms-output">Bookshelf</span></td>
          |            <td class="fr-application-name" style="padding-left: 12px; padding-right: 12px;"><span class="fr-table-pill">orbeon</span></td>
          |            <td class="fr-form-name" style="padding-left: 12px; padding-right: 12px;"><span class="fr-form-slug"><span class="xforms-output">bookshelf</span></span></td>
          |            <td class="fr-version" style="padding-left: 12px; padding-right: 12px;"><span class="fr-table-pill">v1</span></td>
          |          </tr>
          |        </tbody>
          |      </table>
          |    </div>
          |  </div>
          |</form>""".stripMargin
      dom.document.body.className = "orbeon"

      val wrapper = dom.document.querySelectorT(".fr-search-results")
      val table   = dom.document.querySelectorT(".fr-forms-table")
      defineNumericProp(wrapper, "clientWidth", 1500)

      mockRect(table.querySelectorT("#th-select"),        36.0)
      mockRect(table.querySelectorT("#th-local-status"),  98.0)
      mockRect(table.querySelectorT("#th-local-mod"),     96.0)
      mockRect(table.querySelectorT("#th-remote-status"), 180.0)
      mockRect(table.querySelectorT("#th-remote-mod"),    96.0)
      mockRect(table.querySelectorT("#th-title"),         260.0)
      mockRect(table.querySelectorT("#th-app"),           142.0)
      mockRect(table.querySelectorT("#th-form"),          152.0)
      mockRect(table.querySelectorT("#th-ver"),           82.0)

      SearchResultsTableSupport.syncTable(table)

      val cols = colWidthsByKey(table)
      assert(cols.size == 9)
      assert(cols("_selection") == "36px")
      // Local Status uses `18 * 8 + 24 = 168px` floor; Remote Status (`180 + 16 = 196px`) stays independent
      assert(cols("meta:status") == "168px")
      assert(cols("meta:status#1") == "196px")
      assert(cols("meta:last-modified") == "108px")
      assert(cols("meta:last-modified#1") == "108px")
      assert(cols("dyn:0") == "auto")
    }

    it("must detect truncated `.fr-form-slug` and `td.fr-title` cells with proper keyboard focusability") {
      // Linked `/fr/forms` row: `a` is already focusable so no duplicate `tabindex` is added
      val (_, linkedTable) = buildPublishedFormsDom(wrapperWidth = 1300, linked = true)
      val titleAnchor      = linkedTable.querySelectorT("tbody td.fr-title > a")
      val linkedSlug       = linkedTable.querySelectorT("tbody td.fr-form-name .fr-form-slug")
      defineNumericProp(titleAnchor, "clientWidth", 260)
      defineNumericProp(titleAnchor, "scrollWidth", 410)
      defineNumericProp(linkedSlug,  "clientWidth", 140)
      defineNumericProp(linkedSlug,  "scrollWidth", 230)

      SearchResultsTableSupport.syncTable(linkedTable)

      assert(titleAnchor.hasClass(SearchResultsTableSupport.CellTruncatedClass))
      assert(titleAnchor.style.getPropertyValue(SearchResultsTableSupport.ScrollWidthCssVar) == "150")
      assert(! titleAnchor.hasAttribute("tabindex"))
      assert(linkedSlug.hasClass(SearchResultsTableSupport.CellTruncatedClass))
      assert(! linkedSlug.hasAttribute("tabindex"))

      // Unlinked `/fr/admin` row: truncated `td.fr-title` and `.fr-form-slug` receive `tabindex="0"` for keyboard reveal
      val (_, unlinkedTable) = buildPublishedFormsDom(wrapperWidth = 1300, linked = false)
      val titleTd            = unlinkedTable.querySelectorT("tbody td.fr-title")
      val unlinkedSlug       = unlinkedTable.querySelectorT("tbody td.fr-form-name .fr-form-slug:not(.xforms-hidden)")
      defineNumericProp(titleTd,      "clientWidth", 260)
      defineNumericProp(titleTd,      "scrollWidth", 410)
      defineNumericProp(unlinkedSlug, "clientWidth", 140)
      defineNumericProp(unlinkedSlug, "scrollWidth", 230)

      SearchResultsTableSupport.syncTable(unlinkedTable)

      assert(titleTd.hasClass(SearchResultsTableSupport.CellTruncatedClass))
      assert(titleTd.getAttribute("tabindex") == "0")
      assert(unlinkedSlug.hasClass(SearchResultsTableSupport.CellTruncatedClass))
      assert(unlinkedSlug.getAttribute("tabindex") == "0")
    }

    it("must collapse overflowing `.fr-status-pills` using span/latest-preserving `+N` pill without `.fr-cell-truncated` and auto-fit on resizer dblclick") {
      SearchResultsTableSupport.resetAllStates()
      dom.window.sessionStorage.clear()
      dom.document.body.innerHTML =
        """<form class="xforms-form">
          |  <div class="fr-dynamic">
          |    <div class="fr-search-results fr-table-frame" role="region" aria-label="Form Builder Summary">
          |      <table class="table table-condensed">
          |        <thead>
          |          <tr>
          |            <th id="th-app" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Application Name</span></button></span>
          |            </th>
          |            <th id="th-form" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Form Name</span></button></span>
          |            </th>
          |            <th id="th-version" scope="col">
          |              <span><button class="btn-link" type="button"><span class="xforms-output">Version</span></button></span>
          |            </th>
          |            <th id="th-title" scope="col">
          |              <span class="fr-sortable-th"><button class="btn-link" type="button"><span class="xforms-output">Form Title</span></button></span>
          |            </th>
          |          </tr>
          |        </thead>
          |        <tbody>
          |          <tr id="row-unpub">
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a href="#edit-1" class="fr-summary-cell-link"></a>
          |              <div class="fr-summary-cell-value"><span>hr</span></div>
          |            </td>
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a href="#edit-1" class="fr-summary-cell-link"></a>
          |              <div class="fr-summary-cell-value"><span>onboarding</span></div>
          |            </td>
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a href="#edit-1" class="fr-summary-cell-link"></a>
          |              <div id="ver-cell-unpub" class="fr-summary-cell-value"><span class="fr-version-empty" aria-hidden="true">—</span></div>
          |            </td>
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a href="#edit-1" class="fr-summary-cell-link"></a>
          |              <div class="fr-summary-cell-value"><span>Onboarding</span></div>
          |            </td>
          |          </tr>
          |          <tr id="row-multi">
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a href="#edit-2" class="fr-summary-cell-link"></a>
          |              <div class="fr-summary-cell-value"><span>acme</span></div>
          |            </td>
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a href="#edit-2" class="fr-summary-cell-link"></a>
          |              <div class="fr-summary-cell-value"><span>sales-order</span></div>
          |            </td>
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a id="row-2-ver-link" href="#edit-2" class="fr-summary-cell-link"></a>
          |              <div id="ver-cell-multi" class="fr-summary-cell-value">
          |                <span class="xforms-output xforms-mediatype-text-html xforms-static">
          |                  <span class="xforms-output-output">
          |                    <span class="fr-status-pills">
          |                      <span id="p-v1" class="fr-table-pill fr-version-badge fr-pill-muted fr-pill-unavailable" title="Version: v1 — Unavailable">v1<span class="visually-hidden"> (Unavailable)</span></span>
          |                      <span id="p-v2" class="fr-table-pill fr-version-badge fr-pill-success" title="Version: v2 — Available">v2<span class="visually-hidden"> (Available)</span></span>
          |                      <span id="p-v3" class="fr-table-pill fr-version-badge fr-pill-success" title="Version: v3 — Available">v3<span class="visually-hidden"> (Available)</span></span>
          |                      <span id="p-v4" class="fr-table-pill fr-version-badge fr-pill-success" title="Version: v4 — Available">v4<span class="visually-hidden"> (Available)</span></span>
          |                      <span id="p-v5" class="fr-table-pill fr-version-badge fr-pill-success" title="Version: v5 — Available">v5<span class="visually-hidden"> (Available)</span></span>
          |                    </span>
          |                  </span>
          |                </span>
          |              </div>
          |            </td>
          |            <td class="fr-td" style="padding-left: 10px; padding-right: 10px;">
          |              <a href="#edit-2" class="fr-summary-cell-link"></a>
          |              <div class="fr-summary-cell-value"><span>Customer Sales Order</span></div>
          |            </td>
          |          </tr>
          |        </tbody>
          |      </table>
          |    </div>
          |  </div>
          |</form>""".stripMargin
      dom.document.body.className = "orbeon"

      val wrapper   = dom.document.querySelectorT(".fr-search-results")
      val table     = dom.document.querySelectorT(".fr-search-results table")
      val verCell   = dom.document.getElementById("ver-cell-multi").asInstanceOf[html.Element]
      val pillsElem = verCell.querySelectorT(".fr-status-pills")

      defineNumericProp(wrapper, "clientWidth", 1200)
      mockRect(table.querySelectorT("#th-app"),     130.0)
      mockRect(table.querySelectorT("#th-form"),    150.0)
      mockRect(table.querySelectorT("#th-version"), 76.0)
      mockRect(table.querySelectorT("#th-title"),   300.0)

      pillsElem.querySelectorAllT(".fr-table-pill").foreach(p => mockRect(p, 34.0))

      def visiblePillLabelsInVisualOrder(): List[String] =
        pillsElem
          .childrenT
          .zipWithIndex
          .filter { case (el, _) =>
            ! el.hasAttribute("hidden") && ! el.hasClass(SearchResultsTableSupport.PillCollapsedClass)
          }
          .sortBy { case (el, domIdx) =>
            val orderNum = Option(el.style.getPropertyValue("order")).flatMap(_.toIntOption).getOrElse(0)
            (orderNum, domIdx)
          }
          .map { case (el, _) =>
            val clone = el.cloneNodeT(deep = true)
            clone.querySelectorAllT(".visually-hidden").foreach(_.remove())
            clone.textContent.trim
          }
          .toList

      // 1. Even though `#th-version` measured `76px`, status-pills column applies `PillsMinCh` (`15 * 8 + 20 = 140px`) floor
      defineNumericProp(verCell, "clientWidth", 120)
      defineNumericProp(verCell, "scrollWidth", 190)
      SearchResultsTableSupport.syncTable(table)

      assert(colWidthsByKey(table)("dyn:2") == "140px")
      assert(! verCell.hasClass(SearchResultsTableSupport.CellTruncatedClass))
      assert(verCell.style.getPropertyValue(SearchResultsTableSupport.ScrollWidthCssVar).isEmpty)

      // At `120px` content width (`34*2 + 34 + 2*5 = 112 <= 120`), 3 badges fit: `[v1] [+3] [v5]`
      assert(visiblePillLabelsInVisualOrder() == List("v1", "+3", "v5"))

      // 2. At `160px` content width (`34*3 + 34 + 3*5 = 151 <= 160`), 4 badges fit: `[v1] [+2] [v4] [v5]`
      defineNumericProp(verCell, "clientWidth", 160)
      SearchResultsTableSupport.syncTable(table)
      assert(visiblePillLabelsInVisualOrder() == List("v1", "+2", "v4", "v5"))

      // 3. At `80px` content width (`34 + 34 + 5 = 73 <= 80`), 2 badges fit: `[+4] [v5]`
      defineNumericProp(verCell, "clientWidth", 80)
      SearchResultsTableSupport.syncTable(table)
      assert(visiblePillLabelsInVisualOrder() == List("+4", "v5"))

      // 4. At `44px` content width (`< 73`), falls back to `[+5]`
      defineNumericProp(verCell, "clientWidth", 44)
      SearchResultsTableSupport.syncTable(table)
      assert(visiblePillLabelsInVisualOrder() == List("+5"))

      // 5. Popover interactions: click `+5` pins popover open with all 5 badges, without firing row link or duplicating IDs
      val triggerBtn = pillsElem.querySelectorT(s".${SearchResultsTableSupport.PillsOverflowTriggerClass}")
      var rowLinkClicked = false
      dom.document.getElementById("row-2-ver-link").addEventListener("click", (_: dom.Event) => rowLinkClicked = true)

      assert(triggerBtn.getAttribute("aria-expanded") == "false")
      triggerBtn.click()

      assert(! rowLinkClicked)
      assert(triggerBtn.getAttribute("aria-expanded") == "true")

      val popover = wrapper.querySelectorT(s".${SearchResultsTableSupport.PillsOverflowPopoverClass}")
      assert(popover.hasClass(SearchResultsTableSupport.PopoverOpenClass))
      assert(triggerBtn.getAttribute("aria-controls") == popover.id)

      val popBadges = popover.querySelectorAllT(".fr-table-pill")
      assert(popBadges.length == 5)
      assert(popBadges.head.hasClass("fr-pill-unavailable"))
      assert(popBadges.head.getAttribute("title") == "Version: v1 — Unavailable")
      assert(popBadges.head.querySelectorOpt(".visually-hidden").map(_.textContent).contains(" (Unavailable)"))
      assert(popover.querySelectorAllT("[id]").isEmpty)

      // Pressing Escape dismisses the popover and resets `aria-expanded`
      dom.document.dispatchEvent(
        new dom.KeyboardEvent(
          "keydown",
          new dom.KeyboardEventInit {
            key     = "Escape"
            bubbles = true
          }
        )
      )
      assert(! popover.hasClass(SearchResultsTableSupport.PopoverOpenClass))
      assert(triggerBtn.getAttribute("aria-expanded") == "false")

      // 6. Double-clicking `.fr-col-resizer` on `#th-version` measures uncollapsed `190px + 20px + 4px = 214px` and restores all 5 inline pills
      val verResizer = table.querySelectorT(s"#th-version .${SearchResultsTableSupport.ColResizerClass}")
      defineNumericProp(verCell, "clientWidth", 194)
      verResizer.dispatchEvent(new dom.MouseEvent("dblclick", new dom.MouseEventInit { bubbles = true }))

      assert(colWidthsByKey(table)("dyn:2") == "214px")
      assert(visiblePillLabelsInVisualOrder() == List("v1", "v2", "v3", "v4", "v5"))
      assert(triggerBtn.hasAttribute("hidden"))
    }
  }
}
