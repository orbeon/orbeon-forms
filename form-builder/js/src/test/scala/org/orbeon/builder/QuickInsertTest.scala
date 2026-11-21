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

import org.orbeon.builder.ToolboxSearch.ToolboxItem
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike


class QuickInsertTest extends AnyFunSpecLike {

  describe("ToolboxSearch.filterAndRankItems") {

    def mkItem(id: String, label: String, category: String, enabled: Boolean = true): ToolboxItem =
      ToolboxItem(
        id         = id,
        label      = label,
        category   = category,
        extraTerms = "",
        iconHtml   = "",
        shortcut   = None,
        isControl  = true,
        enabled    = enabled,
        button     = dom.document.createElement("button").asInstanceOf[html.Button]
      )

    it("breaks ties among equal match scores using recentIds") {
      val items = List(
        mkItem("date",       "Date",       "Date and Time"),
        mkItem("date-range", "Date Range", "Date and Time"),
        mkItem("date-time",  "Date and Time", "Date and Time")
      )

      val ranked = ToolboxSearch.filterAndRankItems(items, "date", recentIds = List("date-range"))
      assert(ranked.map(_.id) == List("date-range", "date", "date-time"))
    }
  }

  describe("QuickInsert.computePopoverPosition") {

    it("places popover below the anchor when there is enough space below") {
      val pos = QuickInsert.computePopoverPosition(
        anchorLeft     = 400.0,
        anchorTop      = 200.0,
        anchorWidth    = 200.0,
        anchorHeight   = 44.0,
        viewportWidth  = 1280.0,
        viewportHeight = 800.0
      )
      assert(! pos.placedAbove)
      assert(pos.top == 250.0)
      assert(pos.left == 355.0)
    }

    it("flips popover above the anchor when near the bottom of the viewport") {
      val pos = QuickInsert.computePopoverPosition(
        anchorLeft     = 400.0,
        anchorTop      = 700.0,
        anchorWidth    = 200.0,
        anchorHeight   = 44.0,
        viewportWidth  = 1280.0,
        viewportHeight = 800.0
      )
      assert(pos.placedAbove)
      assert(pos.top < 700.0)
    }

    it("clamps popover horizontally within the viewport") {
      val nearLeft = QuickInsert.computePopoverPosition(
        anchorLeft     = 10.0,
        anchorTop      = 200.0,
        anchorWidth    = 40.0,
        anchorHeight   = 40.0,
        viewportWidth  = 1280.0,
        viewportHeight = 800.0
      )
      assert(nearLeft.left == 8.0)

      val nearRight = QuickInsert.computePopoverPosition(
        anchorLeft     = 1200.0,
        anchorTop      = 200.0,
        anchorWidth    = 60.0,
        anchorHeight   = 40.0,
        viewportWidth  = 1280.0,
        viewportHeight = 800.0
      )
      assert(nearRight.left == 1280.0 - 290.0 - 8.0)
    }
  }

  describe("QuickInsert palette DOM") {

    def withFormBuilderDom(body: => Unit): Unit = {
      val root = dom.document.createElement("div")
      root.classList.add("orbeon")
      root.innerHTML =
        """<div class="fb-toolbox">
          |  <div id="fb-toolbox-tabs">
          |    <div id="fb-toolbox-controls-tab" class="tab-pane active">
          |      <div class="fb-toolbox-search"
          |           data-fb-recent-label="Recent"
          |           data-fb-insert-control-label="Insert control"
          |           data-fb-no-results-label="No matching controls"
          |           data-fb-placeholder="Search controls…"></div>
          |      <div class="fb-tools">
          |        <div class="fb-tools-title">Text Controls</div>
          |        <div class="fb-tool" data-fb-search-terms="Single-Line Text xf input">
          |          <span class="fb-insert-control fb-insert-control-xf-input">
          |            <button class="btn"><i class="fa fa-font"></i>Text Field<kbd>ICI</kbd></button>
          |          </span>
          |        </div>
          |      </div>
          |      <div class="fb-tools">
          |        <div class="fb-tools-title">Date and Time</div>
          |        <div class="fb-tool" data-fb-search-terms="Date fr date">
          |          <span class="fb-insert-control fb-insert-control-fr-date">
          |            <button class="btn"><i class="fa fa-calendar"></i>Date<kbd>ITD</kbd></button>
          |          </span>
          |        </div>
          |      </div>
          |    </div>
          |  </div>
          |</div>
          |<div class="fb-main">
          |  <div class="xbl-fr-section">
          |    <div class="fr-section-label"><button class="btn-link">Customer Info</button></div>
          |    <div class="fr-grid fr-editable">
          |      <div class="fr-grid-td fb-selected" id="cell-1">
          |        <div id="fb≡customer-section≡grid-1≡first-name-control">
          |          <label class="xforms-label">First Name</label>
          |        </div>
          |      </div>
          |      <div class="fr-grid-td" id="cell-2"></div>
          |    </div>
          |  </div>
          |</div>""".stripMargin
      dom.document.body.appendChild(root)
      try body
      finally {
        QuickInsert.close()
        ToolboxSearch.clearRecentItems()
        root.remove()
      }
    }

    it("opens palette in Insert tab with Recent section when recent items exist and closes cleanly") {
      withFormBuilderDom {
        ToolboxSearch.recordRecentItem("fb-insert-control-fr-date")
        QuickInsert.openPalette(QuickInsert.TabInsert)

        assert(QuickInsert.isOpen)
        assert(QuickInsert.activePaletteTabOpt.contains(QuickInsert.TabInsert))
        val groupTitles = dom.document.querySelectorAllT(".fb-quick-insert-group-title").map(_.textContent)
        assert(groupTitles.headOption.contains("Recent"))
        assert(dom.document.querySelectorAllT(".fb-quick-insert-item").length == 3) // 1 recent + 2 categorized

        QuickInsert.close()
        assert(! QuickInsert.isOpen)
        assert(dom.document.querySelectorOpt(".fb-quick-insert-panel").isEmpty)
      }
    }

    it("collects existing form controls and opens Go to tab (Cmd+J behavior)") {
      withFormBuilderDom {
        val controls = QuickInsert.collectFormControls()
        assert(controls.length == 1)
        assert(controls.head.controlName == "first-name")
        assert(controls.head.label == "First Name")
        assert(controls.head.sectionLabel == "Customer Info")

        QuickInsert.openPalette(QuickInsert.TabGoTo)
        assert(QuickInsert.activePaletteTabOpt.contains(QuickInsert.TabGoTo))
        assert(dom.document.querySelectorAllT(".fb-quick-insert-goto-item").length == 1)
        assert(dom.document.querySelectorOpt(".fb-quick-insert-settings-btn").isDefined)
      }
    }

    it("orders Go to ahead of Insert in All tab when a populated cell is selected, and Insert first when an empty cell is selected") {
      withFormBuilderDom {
        // cell-1 (populated) is selected initially
        QuickInsert.openPalette(QuickInsert.TabAll)
        val populatedOrder = dom.document.querySelectorAllT(".fb-quick-insert-group-title").map(_.textContent)
        assert(populatedOrder == Seq("Go to Control", "Insert"))
        QuickInsert.close()

        // Switch selection to cell-2 (empty)
        dom.document.getElementById("cell-1").classList.remove("fb-selected")
        dom.document.getElementById("cell-2").classList.add("fb-selected")
        QuickInsert.openPalette(QuickInsert.TabAll)
        val emptyOrder = dom.document.querySelectorAllT(".fb-quick-insert-group-title").map(_.textContent)
        assert(emptyOrder == Seq("Insert", "Go to Control"))
      }
    }
  }
}
