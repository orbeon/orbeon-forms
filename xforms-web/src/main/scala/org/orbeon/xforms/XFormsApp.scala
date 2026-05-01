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
import scala.scalajs.js.Dynamic.global as g


// Scala.js starting point for XForms
object XFormsApp extends App {

  private var _clientServerChannel: ClientServerChannel = _
  def clientServerChannel: ClientServerChannel = _clientServerChannel

  def onOrbeonApiLoaded(): Unit =
    onOrbeonApiLoaded(RemoteClientServerChannel, isBrowserEnvironment = false)

  private var _isBrowserEnvironment: Boolean = _
  def isBrowserEnvironment: Boolean = _isBrowserEnvironment

  def onOrbeonApiLoaded(clientServerChannel: ClientServerChannel, isBrowserEnvironment: Boolean): Unit = {

    _clientServerChannel  = clientServerChannel
    _isBrowserEnvironment = isBrowserEnvironment

    // By this point, `window.ORBEON` should already be defined by our jQuery wrapper, but we created it if needed
    // also in anticipation for the time jQuery will be removed.
    if (js.isUndefined(g.window.ORBEON))
      g.window.ORBEON = new js.Object
    val orbeonDyn = g.window.ORBEON

    if (js.isUndefined(orbeonDyn.xforms))
      orbeonDyn.xforms = new js.Object
    val xformsDyn = orbeonDyn.xforms

    xformsDyn.InitSupport = js.Dynamic.global.OrbeonInitSupport

    xformsDyn.RpcClient = js.Dynamic.global.OrbeonRpcClient // TODO: move to Form Builder module

    // Public API
    xformsDyn.XBL        = js.Dynamic.global.OrbeonXFormsXbl
    xformsDyn.AjaxClient = js.Dynamic.global.OrbeonAjaxClient
    xformsDyn.Document   = DocumentAPI

    // Configure logging
//    setLoggerThreshold("org.orbeon.oxf.xforms", LogLevel)

    // Register XBL components
    org.orbeon.xforms.Upload
    org.orbeon.xbl.Date
    org.orbeon.xbl.Time
    org.orbeon.xbl.Range
    org.orbeon.xbl.CodeMirror

    org.orbeon.xforms.ItemHint
    org.orbeon.xforms.Help
  }

  def onPageContainsFormsMarkup(): Unit = {
    setupGlobalClassesIfNeeded()
    InitSupport.pageContainsFormsMarkup()
  }
}
