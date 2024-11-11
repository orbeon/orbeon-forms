package org.orbeon.builder

import org.orbeon.datatypes.Direction
import org.orbeon.web.DomSupport.{DomElemOps, moveIntoViewIfNeeded}
import org.orbeon.xforms.EventListenerSupport
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom.{KeyboardEvent, html}

import scala.scalajs.js


object FormSettings {

  XBL.declareCompanion("fb|dialog-form-settings", js.constructorOf[ControlSettingsCompanion])

  private class ControlSettingsCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private val KeyMapping = Map(
      "ArrowLeft"  -> Direction.Left,
      "ArrowRight" -> Direction.Right,
      "ArrowUp"    -> Direction.Up,
      "ArrowDown"  -> Direction.Down
    )

    def dialogOpening(): Unit = {

      val isNew = containerElem.querySelectorOpt(".fb-settings-mode-new").nonEmpty

      def findCardsForDirections: Map[Direction, html.Element] = {
        val cards = containerElem.querySelectorAllT(".fb-template-card")

        Some(cards.indexWhere(_.classList.contains("xforms-repeat-selected-item-1")))
          .filter(_ >= 0)
          .map { index =>

            val lifted = cards.lift

            (
              lifted(index - 1).map((Direction.Left : Direction) -> _).toList :::
              lifted(index + 1).map((Direction.Right: Direction) -> _).toList :::
              lifted(index - 4).map((Direction.Up   : Direction) -> _).toList :::
              lifted(index + 4).map((Direction.Down : Direction) -> _).toList
            ).toMap
          }
          .getOrElse(Map.empty)
      }

      if (isNew)
        EventSupport.addListener[KeyboardEvent](containerElem, "keydown", e =>
          KeyMapping.get(e.key).foreach { direction =>
            findCardsForDirections.get(direction).foreach { card =>
              moveIntoViewIfNeeded(
                containerElem.querySelectorOpt(".fb-template-cards-container").get,
                containerElem.querySelectorOpt(".fb-template-cards").get,
                card
              )
              card.click()
            }
          }
        )
    }

    def dialogClosing(): Unit =
      EventSupport.clearAllListeners()
  }
}
