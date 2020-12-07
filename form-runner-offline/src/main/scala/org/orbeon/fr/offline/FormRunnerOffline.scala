package org.orbeon.fr.offline

import org.orbeon.xforms.App
import org.orbeon.xforms.offline.demo.OfflineDemo


object FormRunnerOffline extends App {

  def onOrbeonApiLoaded(): Unit =
    OfflineDemo.onOrbeonApiLoaded()

  def onPageContainsFormsMarkup(): Unit =
    OfflineDemo.onPageContainsFormsMarkup()
}
