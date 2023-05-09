/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import org.orbeon.facades.Mousetrap
import org.orbeon.fr._
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.xforms.{App, DocumentAPI}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}

// Scala.js starting point for Form Builder
object FormBuilderApp extends App {

  def onOrbeonApiLoaded(): Unit = {

    FormRunnerApp.onOrbeonApiLoaded()

    val orbeonDyn = g.window.ORBEON

    val builderDyn = {
      if (js.isUndefined(orbeonDyn.builder))
        orbeonDyn.builder = new js.Object
      orbeonDyn.builder
    }

    val builderPrivateDyn = {
      if (js.isUndefined(builderDyn.`private`))
        builderDyn.`private` = new js.Object
      builderDyn.`private`
    }

    builderPrivateDyn.API = FormBuilderPrivateAPI
    registerFormBuilderKeyboardShortcuts()

    // Other initializations
    BlockCache

    // Keyboard shortcuts for cut/copy/paste/undo/redo
  }

  private def registerFormBuilderKeyboardShortcuts(): Unit = {
    case class Shortcut(
      shift     : Boolean = false,
      key       : String,
      target    : String,
      condition : Option[js.Function0[Boolean]] = None
    )
    val shortcuts = List(
      Shortcut(              key = "z", target = "undo-trigger"                                                                    ),
      Shortcut(shift = true, key = "z", target = "redo-trigger"                                                                    ),
      Shortcut(              key = "x", target = "cut-trigger"                                                                     ),
      Shortcut(              key = "c", target = "copy-trigger", condition = Some(() => dom.window.getSelection().toString.isEmpty)),
      Shortcut(              key = "v", target = "paste-trigger"                                                                   ),
    )
    shortcuts.foreach(shortcut => {
      List("command", "ctrl").foreach(modifier => {
        val keyCombination = (List(modifier) ++ shortcut.shift.list("shift") ++ List(shortcut.key)).mkString("+")
        Mousetrap.bind(command = keyCombination, callback = { (e: dom.KeyboardEvent, combo: String) =>
          val conditionPasses = shortcut.condition.forall(_())
          if (conditionPasses) {
            e.preventDefault()
            DocumentAPI.dispatchEvent(
              targetId = shortcut.target,
              eventName = "DOMActivate"
            )
          }
        })
      })
    })
  }

  def onPageContainsFormsMarkup(): Unit = {

    FormRunnerApp.onPageContainsFormsMarkup()

    DialogItemset
    ControlDnD
    SectionGridEditor
    RowEditor
    LabelEditor
    ControlEditor
    ControlLabelHintTextEditor
    GridWallDnD

    BrowserCheck.checkSupportedBrowser()
  }
}
