package org.orbeon.builder

import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.EventListenerSupport
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.{KeyboardEvent, html}

import scala.scalajs.js


object DialogControlSettings {

  XBL.declareCompanion("fb|dialog-control-settings", js.constructorOf[DialogControlSettingsCompanion])

  private class DialogControlSettingsCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport
    def dialogOpening(): Unit =  EventSupport.addListener(dom.document, "keydown", handleKeyDown)
    def dialogClosing(): Unit =  EventSupport.clearAllListeners()

    private def handleKeyDown(e: KeyboardEvent): Unit = {

      def triggerButton(direction: String): Unit = {
        val button = containerElem.querySelectorT(s".fb-$direction-button button")
        button.focus()
        button.click()
      }

      if (e.ctrlKey) {
        if (e.key == "[") triggerButton("prev")
        if (e.key == "]") triggerButton("next")
      }
    }
  }
}