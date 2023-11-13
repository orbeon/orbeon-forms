/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.xforms.InitSupport.setupGlobalClassesIfNeeded
import org.orbeon.xforms.rpc.{ClientServerChannel, RemoteClientServerChannel}

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}


// Scala.js starting point for XForms
object XFormsApp extends App {

  private var _clientServerChannel: ClientServerChannel = _
  def clientServerChannel: ClientServerChannel = _clientServerChannel

  def onOrbeonApiLoaded(): Unit =
    onOrbeonApiLoaded(RemoteClientServerChannel)

  def onOrbeonApiLoaded(clientServerChannel: ClientServerChannel): Unit = {

    _clientServerChannel = clientServerChannel

    // By this point, `window.ORBEON` is already defined by our jQuery wrapper
    val orbeonDyn = g.window.ORBEON

    // `window.ORBEON.common` *should not* already exist, but this doesn't hurt
    if (js.isUndefined(orbeonDyn.common))
      orbeonDyn.common = new js.Object

    orbeonDyn.common.MarkupUtils = js.Dynamic.global.OrbeonMarkupUtils
    orbeonDyn.common.StringUtils = js.Dynamic.global.OrbeonStringUtils

    // We know that `window.ORBEON.xforms` already exists
    val xformsDyn = orbeonDyn.xforms

    xformsDyn.Message                = js.Dynamic.global.OrbeonMessage
    xformsDyn.Page                   = js.Dynamic.global.OrbeonPage
    xformsDyn.ServerValueStore       = js.Dynamic.global.OrbeonServerValueStore
    xformsDyn.AjaxEvent              = js.Dynamic.global.OrbeonAjaxEvent
    xformsDyn.Language               = js.Dynamic.global.OrbeonLanguage
    xformsDyn.AjaxClient             = js.Dynamic.global.OrbeonAjaxClient
    xformsDyn.Globals                = js.Dynamic.global.OrbeonGlobals
    xformsDyn.XFormsUi               = js.Dynamic.global.OrbeonXFormsUi
    xformsDyn.XFormsXbl              = js.Dynamic.global.OrbeonXFormsXbl
    xformsDyn.Help                   = js.Dynamic.global.OrbeonHelp
    xformsDyn.InitSupport            = js.Dynamic.global.OrbeonInitSupport
    xformsDyn.RpcClient              = js.Dynamic.global.OrbeonRpcClient
    xformsDyn.AjaxFieldChangeTracker = js.Dynamic.global.OrbeonAjaxFieldChangeTracker

    // Public API
    xformsDyn.Document               = DocumentAPI

    // Configure logging
//    setLoggerThreshold("org.orbeon.oxf.xforms", LogLevel)

    // Register XBL components
    org.orbeon.xforms.Upload
    org.orbeon.xbl.Date
    org.orbeon.xbl.Time
    org.orbeon.xbl.Range

    org.orbeon.xforms.ItemHint
    org.orbeon.xforms.Help
  }

  def onPageContainsFormsMarkup(): Unit = {
    setupGlobalClassesIfNeeded()
    StateHandling.initializeHashChangeListener()
    InitSupport.pageContainsFormsMarkup()
  }
}
