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
package org.orbeon.xbl

import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike


class GridSectionMenusTest extends AnyFunSpecLike {

  private def buildGlobalMenu(componentName: String): html.Element = {
    val menu = dom.document.createElement("div").asInstanceOf[html.Element]
    menu.className = s"dropdown fr-$componentName-dropdown-menu"
    menu.innerHTML =
      """<button class="btn btn-mini dropdown-toggle xforms-hidden" type="button" tabindex="-1"></button>
        |<ul class="dropdown-menu" role="menu">
        |  <li class="fr-insert-above"><a href="#">Insert Above</a></li>
        |  <li class="fr-insert-below"><a href="#">Insert Below</a></li>
        |  <li class="fr-move-up"><a href="#">Move Up</a></li>
        |  <li class="fr-move-down"><a href="#">Move Down</a></li>
        |  <li class="fr-clear"><a href="#">Clear</a></li>
        |  <li class="fr-remove"><a href="#">Remove</a></li>
        |</ul>""".stripMargin
    menu
  }

  private def buildGridWithRowButton(
    gridId       : String,
    buttonId     : String,
    iterationCls : String
  ): (html.Element, html.Element) = {
    val xblGrid = dom.document.createElement("div").asInstanceOf[html.Element]
    xblGrid.id        = gridId
    xblGrid.className = "xbl-component xbl-fr-grid"
    xblGrid.innerHTML =
      s"""<div class="fr-grid fr-repeat">
         |  <div class="fr-grid-repeat-iteration $iterationCls">
         |    <div class="fr-repeat-column-left">
         |      <div class="dropdown">
         |        <button id="$buttonId" class="btn btn-mini fr-grid-dropdown-button" type="button"></button>
         |      </div>
         |    </div>
         |  </div>
         |</div>""".stripMargin
    val button = xblGrid.querySelectorT(".fr-grid-dropdown-button")
    (xblGrid, button)
  }

  describe("GridSectionMenus menu container placement") {

    it("must place the menu outside `.fr-grid.fr-repeat` and inside `form.xforms-form`, `.fb-main`, or `dialog`") {
      dom.document.body.innerHTML = ""
      dom.document.body.className = "orbeon"

      val form = dom.document.createElement("form").asInstanceOf[html.Form]
      form.className = "xforms-form"
      dom.document.body.appendChild(form)

      val globalMenu = buildGlobalMenu("grid")
      form.appendChild(globalMenu)

      // Initialize once after `globalMenu` is in the DOM
      object TestGridMenus extends GridSectionMenus {
        override def componentName: String = "grid"
      }
      val _ = TestGridMenus

      // 1. Grid directly inside `form.xforms-form`
      val (formGrid, formButton) =
        buildGridWithRowButton("my-grid", "my-grid≡btn⊙1", "can-insert-above can-move-down")
      form.appendChild(formGrid)

      formButton.click()

      assert(globalMenu.parentNode eq form)
      assert(globalMenu.closestOpt(".fr-grid.fr-repeat").isEmpty)
      assert(! globalMenu.querySelectorT(".fr-insert-above").hasClass("disabled"))
      assert(! globalMenu.querySelectorT(".fr-move-down").hasClass("disabled"))
      assert(globalMenu.querySelectorT(".fr-move-up").hasClass("disabled"))

      // 2. Grid inside Form Builder `.fb-main`
      val fbMain = dom.document.createElement("div").asInstanceOf[html.Element]
      fbMain.className = "fb-main"
      form.appendChild(fbMain)

      val (fbGrid, fbButton) =
        buildGridWithRowButton("fb-grid", "fb-grid≡btn⊙1", "can-remove")
      fbMain.appendChild(fbGrid)

      fbButton.click()

      assert(globalMenu.parentNode eq fbMain)
      assert(globalMenu.closestOpt(".fr-grid.fr-repeat").isEmpty)
      assert(! globalMenu.querySelectorT(".fr-remove").hasClass("disabled"))
      assert(globalMenu.querySelectorT(".fr-insert-above").hasClass("disabled"))

      // 3. Grid inside `<dialog>`
      val dialog = dom.document.createElement("dialog").asInstanceOf[html.Element]
      dialog.className = "xforms-dialog"
      form.appendChild(dialog)

      val (dialogGrid, dialogButton) =
        buildGridWithRowButton("dlg-grid", "dlg-grid≡btn⊙1", "can-move-up")
      dialog.appendChild(dialogGrid)

      dialogButton.click()

      assert(globalMenu.parentNode eq dialog)
      assert(globalMenu.closestOpt(".fr-grid.fr-repeat").isEmpty)
      assert(! globalMenu.querySelectorT(".fr-move-up").hasClass("disabled"))
    }
  }
}
