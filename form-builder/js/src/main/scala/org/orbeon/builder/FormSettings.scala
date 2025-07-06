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

      def findCardsForDirections(direction: Direction): Option[html.Element] = {
        val cards = containerElem.querySelectorAllT(".fb-template-card")

        val CardsOnRow = 4 // this is what the CSS allows right now; could we determine it dynamically?

        Some(cards.indexWhere(_.classList.contains("xforms-repeat-selected-item-1")))
          .filter(_ >= 0)
          .flatMap { index =>

            val lifted = cards.lift

            direction match {
              case Direction.Left  => lifted(index - 1)
              case Direction.Right => lifted(index + 1)
              case Direction.Up    => lifted(index - CardsOnRow)
              case Direction.Down  => lifted(index + CardsOnRow)
            }
          }
      }

      if (isNew) {
        EventSupport.addListener[KeyboardEvent](containerElem, "keydown", e => {
          val templateCardsActive =
            containerElem.querySelectorAllT(".tab-pane.active .fb-template-cards").nonEmpty
          if (templateCardsActive)
            KeyMapping.get(e.key).foreach { direction =>
              // Only react to arrow keys on the template selection page (first page of wizard)
              if (containerElem.querySelectorOpt(".fb-template-cards-container").nonEmpty) {
                findCardsForDirections(direction).foreach { card =>
                  moveIntoViewIfNeeded(
                    containerElem.querySelectorOpt(".fb-template-cards-container").get,
                    containerElem.querySelectorOpt(".fb-template-cards").get,
                    card,
                    margin = 10
                  )
                  card.click()
                }
              }
            }
        })
      }
    }

    def dialogClosing(): Unit =
      EventSupport.clearAllListeners()
  }
}
