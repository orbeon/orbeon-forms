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

import org.orbeon.builder.ToolboxSearch.SearchEntry
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalatest.funspec.AnyFunSpecLike


class ToolboxSearchTest extends AnyFunSpecLike {

  describe("ToolboxSearch.normalizeForSearch") {

    it("lowercases and strips diacritics") {
      assert(ToolboxSearch.normalizeForSearch("Téléphone") == "telephone")
      assert(ToolboxSearch.normalizeForSearch("Numéro de téléphone") == "numero de telephone")
      assert(ToolboxSearch.normalizeForSearch("Dropdown Menu") == "dropdown menu")
    }
  }

  describe("ToolboxSearch.matchScore") {

    val radio = SearchEntry(
      label      = "Radio Buttons",
      category   = "Selection Controls",
      extraTerms = "xf select1 full",
      enabled    = true
    )

    val dropdown = SearchEntry(
      label      = "Dropdown Menu",
      category   = "Selection Controls",
      extraTerms = "fr dropdown select1",
      enabled    = true
    )

    val disabledSection = SearchEntry(
      label      = "Radio Section",
      category   = "Form Structure",
      extraTerms = "section",
      enabled    = false
    )

    it("ranks label prefix ahead of word-start, substring, and category/extra terms") {
      val prefixScore   = ToolboxSearch.matchScore(radio, "rad")
      val wordStart     = ToolboxSearch.matchScore(radio, "but")
      val substring     = ToolboxSearch.matchScore(radio, "dio")
      val categoryMatch = ToolboxSearch.matchScore(radio, "selection")
      val extraMatch    = ToolboxSearch.matchScore(radio, "select1")

      assert(prefixScore.contains(0))
      assert(wordStart.contains(1))
      assert(substring.contains(2))
      assert(categoryMatch.contains(3))
      assert(extraMatch.contains(3))
    }

    it("supports multi-token queries across label, category, and extra terms") {
      assert(ToolboxSearch.matchScore(dropdown, "drop select1").isDefined)
      assert(ToolboxSearch.matchScore(dropdown, "selection menu").isDefined)
      assert(ToolboxSearch.matchScore(dropdown, "dropdown date").isEmpty)
    }

    it("ranks enabled entries ahead of disabled entries") {
      val enabledScore  = ToolboxSearch.matchScore(radio, "radio").get
      val disabledScore = ToolboxSearch.matchScore(disabledSection, "radio").get
      assert(enabledScore < disabledScore)
    }

    it("matches diacritics-insensitively") {
      val frenchPhone = SearchEntry(
        label      = "Numéro de téléphone",
        category   = "Contrôles typés",
        extraTerms = "US Phone Number",
        enabled    = true
      )
      assert(ToolboxSearch.matchScore(frenchPhone, "telephone").isDefined)
      assert(ToolboxSearch.matchScore(frenchPhone, "controles").isDefined)
      assert(ToolboxSearch.matchScore(frenchPhone, "phone").isDefined)
    }
  }

  describe("ToolboxSearch DOM filtering") {

    def withToolboxDom(body: => Unit): Unit = {
      val container = dom.document.createElement("div")
      container.innerHTML =
        """<div id="fb-toolbox-tabs">
          |  <div id="fb-toolbox-controls-tab" class="active">
          |    <div class="fb-toolbox-search">
          |      <button type="button" class="fb-toolbox-search-toggle" aria-expanded="false"></button>
          |      <div class="fb-toolbox-search-bar">
          |        <input type="text" class="fb-toolbox-search-input"/>
          |        <button type="button" class="fb-toolbox-search-clear"></button>
          |      </div>
          |    </div>
          |    <div class="fb-tools" id="group-text">
          |      <div class="fb-tools-title">Text Controls</div>
          |      <div class="fb-tool" id="tool-input" data-fb-search-terms="Single-Line Text xf input">
          |        <button class="btn">Single-Line Text<kbd>IT</kbd></button>
          |      </div>
          |      <div class="fb-tool" id="tool-textarea" data-fb-search-terms="Multiline Text xf textarea">
          |        <button class="btn">Multiline Text</button>
          |      </div>
          |    </div>
          |    <div class="fb-tools" id="group-date">
          |      <div class="fb-tools-title">Date and Time</div>
          |      <div class="fb-tool" id="tool-date" data-fb-search-terms="Date fr date">
          |        <button class="btn">Date</button>
          |      </div>
          |    </div>
          |    <div class="fb-toolbox-search-empty xforms-hidden">No matching controls</div>
          |  </div>
          |</div>""".stripMargin
      dom.document.body.appendChild(container)
      try body
      finally {
        ToolboxSearch.closeSearch()
        container.remove()
      }
    }

    it("hides non-matching tools and empty groups, and highlights the best match") {
      withToolboxDom {
        ToolboxSearch.openSearch()
        ToolboxSearch.applyFilter("date")

        assert(dom.document.getElementById("group-text").classList.contains("fb-tools-search-hidden"))
        assert(! dom.document.getElementById("group-date").classList.contains("fb-tools-search-hidden"))
        assert(dom.document.getElementById("tool-input").classList.contains("fb-tool-search-hidden"))
        assert(! dom.document.getElementById("tool-date").classList.contains("fb-tool-search-hidden"))
        assert(dom.document.getElementById("tool-date").classList.contains("fb-tool-search-selected"))
        assert(dom.document.querySelectorOpt(".fb-toolbox-search-empty").exists(_.classList.contains("xforms-hidden")))
      }
    }

    it("shows the empty state when no controls match and restores all controls on close") {
      withToolboxDom {
        ToolboxSearch.openSearch()
        ToolboxSearch.applyFilter("zzzzzz")

        assert(dom.document.getElementById("group-text").classList.contains("fb-tools-search-hidden"))
        assert(dom.document.getElementById("group-date").classList.contains("fb-tools-search-hidden"))
        assert(dom.document.querySelectorOpt(".fb-toolbox-search-empty").exists(! _.classList.contains("xforms-hidden")))

        ToolboxSearch.closeSearch()

        assert(! dom.document.getElementById("group-text").classList.contains("fb-tools-search-hidden"))
        assert(! dom.document.getElementById("group-date").classList.contains("fb-tools-search-hidden"))
        assert(! dom.document.getElementById("tool-input").classList.contains("fb-tool-search-hidden"))
        assert(dom.document.querySelectorOpt(".fb-toolbox-search-empty").exists(_.classList.contains("xforms-hidden")))
      }
    }

    it("ignores keyboard shortcut <kbd> text when matching labels") {
      withToolboxDom {
        ToolboxSearch.applyFilter("it")
        // Should NOT match "Single-Line Text" solely because of <kbd>IT</kbd>
        assert(dom.document.getElementById("tool-input").classList.contains("fb-tool-search-hidden"))
      }
    }

    it("treats component buttons as enabled even when enclosing fb-tools group is xforms-readonly") {
      withToolboxDom {
        dom.document.getElementById("group-date").classList.add("xforms-readonly")
        val items = ToolboxSearch.collectItems()
        val dateItem = items.find(_.label == "Date")
        assert(dateItem.exists(_.enabled))
      }
    }
  }
}
