/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.builder

import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js


class OutlineContainerScrollTest extends AnyFunSpecLike {

  describe("OutlineContainerScroll.computeTargetScrollTop") {

    it("positions a section below the viewport near the top of .fb-main instead of at the bottom") {
      // Mirrors the Attachments section scenario:
      // .fb-main top = 95, clientHeight = 804, scrollHeight = 3825, currentScrollTop = 0,
      // section top in viewport = 1951 -> should land 20px below .fb-main top (scrollTop = 1836).
      val target = OutlineContainerScroll.computeTargetScrollTop(
        currentScrollTop   = 0.0,
        scrollHeight       = 3825.0,
        clientHeight       = 804.0,
        mainInnerTop       = 95.0,
        anchorTop          = 1951.0,
        stickyBannerHeight = 0.0
      )
      assert(target == 1836.0)
    }

    it("positions a section above the viewport near the top of .fb-main") {
      val target = OutlineContainerScroll.computeTargetScrollTop(
        currentScrollTop   = 2000.0,
        scrollHeight       = 3825.0,
        clientHeight       = 804.0,
        mainInnerTop       = 95.0,
        anchorTop          = -400.0,
        stickyBannerHeight = 0.0
      )
      assert(target == 1485.0)
    }

    it("snaps to 0 when targeting the first section right below the form title bar") {
      // First section sits ~58px from top of .fb-main when scrollTop = 0, so rawScrollTop = 38 < 60 -> snaps to 0
      // rather than leaving a sliced sliver of .navbar-inverse at the top.
      val fromTop = OutlineContainerScroll.computeTargetScrollTop(
        currentScrollTop   = 0.0,
        scrollHeight       = 3825.0,
        clientHeight       = 804.0,
        mainInnerTop       = 95.0,
        anchorTop          = 153.0,
        stickyBannerHeight = 0.0
      )
      assert(fromTop == 0.0)

      val fromScrolledDown = OutlineContainerScroll.computeTargetScrollTop(
        currentScrollTop   = 1836.0,
        scrollHeight       = 3825.0,
        clientHeight       = 804.0,
        mainInnerTop       = 95.0,
        anchorTop          = 153.0 - 1836.0,
        stickyBannerHeight = 0.0
      )
      assert(fromScrolledDown == 0.0)
    }

    it("accounts for sticky section focus banner height when visible") {
      val target = OutlineContainerScroll.computeTargetScrollTop(
        currentScrollTop   = 500.0,
        scrollHeight       = 2500.0,
        clientHeight       = 804.0,
        mainInnerTop       = 95.0,
        anchorTop          = 600.0,
        stickyBannerHeight = 34.0
      )
      assert(target == 951.0)
    }

    it("clamps target scrollTop to [0, scrollHeight - clientHeight]") {
      val nearEnd = OutlineContainerScroll.computeTargetScrollTop(
        currentScrollTop   = 2800.0,
        scrollHeight       = 3825.0,
        clientHeight       = 804.0,
        mainInnerTop       = 95.0,
        anchorTop          = 700.0,
        stickyBannerHeight = 0.0
      )
      assert(nearEnd == 3825.0 - 804.0)
    }
  }

  describe("OutlineContainerScroll.topAnchorElem") {

    def stubTop(elem: html.Element, topPx: Double): Unit =
      elem.asInstanceOf[js.Dynamic].getBoundingClientRect =
        (() => js.Dynamic.literal(top = topPx, bottom = topPx + 100.0, left = 0.0, right = 800.0, width = 800.0, height = 100.0)): js.Function0[js.Dynamic]

    it("uses the enclosing section when a grid or nested section is immediately below its header") {
      val host = dom.document.createElement("div").asInstanceOf[html.Element]
      host.innerHTML =
        """
          |<div id="outer-sec" class="xbl-fr-section">
          |  <h2 class="fr-section-title">Outer</h2>
          |  <div id="inner-sec" class="xbl-fr-section">
          |    <h2 class="fr-section-title">Inner</h2>
          |    <div id="grid-1" class="xbl-fr-grid"></div>
          |    <div id="grid-2" class="xbl-fr-grid"></div>
          |  </div>
          |</div>
          |""".stripMargin
      dom.document.body.appendChild(host)
      try {
        val outerSec = host.querySelectorT("#outer-sec")
        val innerSec = host.querySelectorT("#inner-sec")
        val grid1    = host.querySelectorT("#grid-1")
        val grid2    = host.querySelectorT("#grid-2")

        stubTop(outerSec, 100.0)
        stubTop(innerSec, 148.0) // 48px below outerSec header
        stubTop(grid1,    196.0) // 48px below innerSec header
        stubTop(grid2,    650.0) // far below innerSec header

        assert(OutlineContainerScroll.topAnchorElem(grid1).id    == "outer-sec")
        assert(OutlineContainerScroll.topAnchorElem(innerSec).id == "outer-sec")
        assert(OutlineContainerScroll.topAnchorElem(grid2).id    == "grid-2")
        assert(OutlineContainerScroll.topAnchorElem(outerSec).id == "outer-sec")
      } finally {
        dom.document.body.removeChild(host)
      }
    }
  }
}
