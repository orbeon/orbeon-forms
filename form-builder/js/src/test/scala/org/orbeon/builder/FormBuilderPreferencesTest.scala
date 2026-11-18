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

import org.orbeon.builder.FormBuilderPreferences.ToolboxTab
import org.orbeon.oxf.util.JsUrlEncoderDecoder
import org.orbeon.web.DomSupport.*
import org.orbeon.xbl.Tabbable
import org.orbeon.xforms.{Form, Page, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g


class FormBuilderPreferencesTest extends AnyFunSpecLike {

  describe("FormBuilderPreferences serialization") {

    it("must round-trip preferences including toolbox-tab via cookie value and header") {
      val prefs = FormBuilderPreferences(
        controlSettingsToolbarVisible = false,
        showCellBadges                = true,
        outlineVisible                = false,
        toolboxTab                    = ToolboxTab.Advanced
      )
      val cookieValue = FormBuilderPreferences.toCookieValue(prefs)
      assert(FormBuilderPreferences.fromCookieValue(cookieValue).contains(prefs))

      val cookieHeader = s"other-cookie=123; ${FormBuilderPreferences.PreferencesCookieName}=$cookieValue; theme=dark"
      assert(FormBuilderPreferences.fromCookieHeader(cookieHeader).contains(prefs))
    }

    it("must default missing or unknown toolbox-tab values to Controls") {
      val partialEncoded = JsUrlEncoderDecoder.encode("""{"outline-visible":false}""")
      assert(
        FormBuilderPreferences.fromCookieValue(partialEncoded).contains(
          FormBuilderPreferences(
            controlSettingsToolbarVisible = true,
            showCellBadges                = true,
            outlineVisible                = false,
            toolboxTab                    = ToolboxTab.Controls
          )
        )
      )

      val unknownTabEncoded = JsUrlEncoderDecoder.encode("""{"toolbox-tab":"other"}""")
      assert(
        FormBuilderPreferences.fromCookieValue(unknownTabEncoded).contains(
          FormBuilderPreferences(
            controlSettingsToolbarVisible = true,
            showCellBadges                = true,
            outlineVisible                = true,
            toolboxTab                    = ToolboxTab.Controls
          )
        )
      )
    }
  }

  describe("FormBuilderPreferencesClient and cookie persistence") {

    it("must update toolbox-tab in cookie on fb-toolbox-tabs selection and preserve other preferences") {
      FormBuilderPreferencesClient.init()

      // Clear preferences cookie
      dom.document.cookie = s"${FormBuilderPreferences.PreferencesCookieName}=; path=/; max-age=0"

      val host = dom.document.createDivElement
      host.innerHTML =
        """
          |<div class="fb-main fb-has-control-toolbar fb-hide-cell-badges">
          |  <div id="fb-toolbox-tabs" class="xbl-fr-tabbable">
          |    <div>
          |      <ul class="nav nav-tabs">
          |        <li class="xforms-dnd-item"><a data-toggle="tabbable">Controls</a></li>
          |        <li class="active xforms-dnd-item"><a data-toggle="tabbable">Advanced</a></li>
          |      </ul>
          |      <div class="tab-content">
          |        <div id="fb-toolbox-controls-tab" class="tab-pane"></div>
          |        <div id="fb-toolbox-advanced-tab" class="tab-pane active"></div>
          |      </div>
          |    </div>
          |  </div>
          |  <div id="fb-tabbable" class="xbl-fr-tabbable"></div>
          |</div>
          |""".stripMargin
      dom.document.body.appendChild(host)

      try {
        val toolboxTabs = host.querySelectorT("#fb-toolbox-tabs")
        val dialogTabs  = host.querySelectorT("#fb-tabbable")
        val controlsTab = host.querySelectorT("#fb-toolbox-controls-tab")
        val advancedTab = host.querySelectorT("#fb-toolbox-advanced-tab")

        // 1. Selecting Advanced tab in toolbox writes `toolbox-tab = advanced` and preserves `.fb-main` flags
        dispatchCustomEvent(toolboxTabs, Tabbable.TabSelectedEvent)

        assert(
          FormBuilderPreferences.fromCookieHeader(dom.document.cookie).contains(
            FormBuilderPreferences(
              controlSettingsToolbarVisible = true,
              showCellBadges                = false,
              outlineVisible                = false,
              toolboxTab                    = ToolboxTab.Advanced
            )
          )
        )

        // 2. Calling `updateToolbarPreferences` (e.g. toggling outline) preserves `toolbox-tab = advanced`
        FormBuilderPrivateAPI.updateToolbarPreferences(
          controlSettingsToolbarVisible = false,
          showCellBadges                = true,
          outlineVisible                = true
        )

        assert(
          FormBuilderPreferences.fromCookieHeader(dom.document.cookie).contains(
            FormBuilderPreferences(
              controlSettingsToolbarVisible = false,
              showCellBadges                = true,
              outlineVisible                = true,
              toolboxTab                    = ToolboxTab.Advanced
            )
          )
        )

        // 3. Switching another `fr:tabbable` (e.g. a dialog) must not overwrite toolbox preferences
        advancedTab.classList.remove("active")
        controlsTab.classList.add("active")
        dispatchCustomEvent(dialogTabs, Tabbable.TabSelectedEvent)

        assert(
          FormBuilderPreferences.fromCookieHeader(dom.document.cookie).exists(_.toolboxTab == ToolboxTab.Advanced)
        )

        // 4. Switching `#fb-toolbox-tabs` back to Controls updates cookie to `toolbox-tab = controls`
        val fbMain = host.querySelectorT(".fb-main")
        fbMain.classList.remove("fb-has-control-toolbar")
        fbMain.classList.remove("fb-hide-cell-badges")
        fbMain.classList.add("fb-has-outline")

        dispatchCustomEvent(toolboxTabs, Tabbable.TabSelectedEvent)

        assert(
          FormBuilderPreferences.fromCookieHeader(dom.document.cookie).contains(
            FormBuilderPreferences(
              controlSettingsToolbarVisible = false,
              showCellBadges                = true,
              outlineVisible                = true,
              toolboxTab                    = ToolboxTab.Controls
            )
          )
        )
      } finally {
        dom.document.body.removeChild(host)
      }
    }

    it("must select initial fr-tab-default tab without notifying and update cookie when user clicks tabs") {
      Tabbable
      FormBuilderPreferencesClient.init()

      if (js.isUndefined(g.window.bowser))
        g.window.bowser = js.Dynamic.literal(osname = "macOS")
      if (js.isUndefined(g.window.Mousetrap))
        g.window.Mousetrap = js.Dynamic.literal(prototype = js.Dynamic.literal())
      if (js.isUndefined(g.ORBEON))
        g.ORBEON = new js.Object
      if (js.isUndefined(g.ORBEON.bootstrap))
        g.ORBEON.bootstrap = js.Dynamic.literal(
          Tooltip = js.Dynamic.literal(
            getInstance         = (() => null): js.Function0[js.Any],
            getOrCreateInstance = (() => js.Dynamic.literal(dispose = (() => ()): js.Function0[Unit])): js.Function0[js.Any]
          )
        )

      dom.document.cookie = s"${FormBuilderPreferences.PreferencesCookieName}=; path=/; max-age=0"

      val host = dom.document.createDivElement
      host.innerHTML =
        """
          |<form id="xforms-form" class="xforms-form">
          |  <div class="fb-main fb-has-control-toolbar fb-has-outline">
          |    <div id="fb-toolbox-tabs" class="xbl-component xbl-javascript-lifecycle xbl-fr-tabbable">
          |      <div>
          |        <ul class="nav nav-tabs">
          |          <li id="controls-li" class="xforms-dnd-item"><a id="controls-link" data-toggle="tabbable">Controls</a></li>
          |          <li id="advanced-li" class="active fr-tab-default xforms-dnd-item"><a id="advanced-link" data-toggle="tabbable">Advanced</a></li>
          |        </ul>
          |        <div class="tab-content">
          |          <div id="fb-toolbox-controls-tab" class="tab-pane"></div>
          |          <div id="fb-toolbox-advanced-tab" class="tab-pane active"></div>
          |        </div>
          |      </div>
          |    </div>
          |  </div>
          |</form>
          |""".stripMargin
      dom.document.body.appendChild(host)

      val formElem     = host.querySelectorT("#xforms-form").asInstanceOf[html.Form]
      val dummyForm    = js.Dynamic.literal(uuid = "test-uuid", elem = formElem, xblInstances = js.Array()).asInstanceOf[Form]
      Page.registerForm("xforms-form", dummyForm)

      val toolboxTabs  = host.querySelectorT("#fb-toolbox-tabs")
      val controlsLi   = host.querySelectorT("#controls-li")
      val advancedLi   = host.querySelectorT("#advanced-li")
      val controlsPane = host.querySelectorT("#fb-toolbox-controls-tab")
      val advancedPane = host.querySelectorT("#fb-toolbox-advanced-tab")
      val controlsLink = host.querySelectorT("#controls-link")
      val advancedLink = host.querySelectorT("#advanced-link")

      val companionOpt = XFormsXbl.findInstanceForControl(toolboxTabs)
      try {
        val companion = companionOpt.get
        // Initial companion `init()` + `xxforms-visible` (`selectDefaultTab()`) keep Advanced active without writing cookie
        companion.asInstanceOf[js.Dynamic].selectDefaultTab()
        assert(advancedLi.hasClass("active"))
        assert(advancedPane.hasClass("active"))
        assert(! controlsLi.hasClass("active"))
        assert(! controlsPane.hasClass("active"))
        assert(FormBuilderPreferences.fromCookieHeader(dom.document.cookie).isEmpty)

        // Clicking Controls switches tab and saves `toolbox-tab = controls`
        controlsLink.click()
        assert(controlsLi.hasClass("active"))
        assert(controlsPane.hasClass("active"))
        assert(! advancedLi.hasClass("active"))
        assert(! advancedPane.hasClass("active"))
        assert(FormBuilderPreferences.fromCookieHeader(dom.document.cookie).exists(_.toolboxTab == ToolboxTab.Controls))

        // Clicking Advanced switches tab and saves `toolbox-tab = advanced`
        advancedLink.click()
        assert(advancedLi.hasClass("active"))
        assert(advancedPane.hasClass("active"))
        assert(! controlsLi.hasClass("active"))
        assert(! controlsPane.hasClass("active"))
        assert(FormBuilderPreferences.fromCookieHeader(dom.document.cookie).exists(_.toolboxTab == ToolboxTab.Advanced))
      } finally {
        companionOpt.foreach(_.destroy())
        Page.unregisterForm(dummyForm)
        dom.document.body.removeChild(host)
      }
    }
  }
}
