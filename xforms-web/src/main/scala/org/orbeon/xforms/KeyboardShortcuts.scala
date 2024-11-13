package org.orbeon.xforms

import org.orbeon.facades.{Bowser, Mousetrap}
import org.orbeon.oxf.util.StringUtils.*
import org.scalajs.dom
import org.scalajs.dom.ext.*
import org.scalajs.dom.html

import scala.scalajs.js


object KeyboardShortcuts {

  private val KeyBoardIconCharacter = "⌨\uFE0F"

  private lazy val isAppleOs: Boolean =
    Bowser.osname.exists(Set("macOS", "iOS"))

  private val ModifiersMap = Map(
    '⌘' -> "command", // same as 'meta'
    '⌃' -> "ctrl",    // cross-platform
    '⌥' -> "option",  // same as 'alt'
    '⇧' -> "shift",   // cross-platform
  )

  // If a single shortcut is provided, use '⌘' on Apple OS and '⌃' on other OSes
  private lazy val convertIfNotApple: Char => Char = {

    val AppleToOtherMap = Map(
      '⌘' -> '⌃'
    )

    if (! isAppleOs)
      char => AppleToOtherMap.getOrElse(char, char)
    else
      identity
  }

  private lazy val convertForDisplayIfNotApple: Char => String = {

    val AppleToOtherMap = Map(
      '⌘' -> "Meta-",
      '⌥' -> "Alt-",
    )

    if (! isAppleOs)
      char => AppleToOtherMap.getOrElse(char, char.toString)
    else
      char => char.toString
  }

  private val ModifiersCharacters = ModifiersMap.keys.map(_.toString).toSet

  // Syntax:
  // - The shortcut is a string with one or two tokens separated by a space.
  //   - No tokens means no shortcut.
  //   - Extra tokens are ignored.
  // - If 2 tokens are present, the first token is for Apple OSes, the second token is for other OSes.
  //    - For other OSes, '⌘' means Meta and '⌥' means Alt.
  // - If 1 token is present
  //   - it is used for both Apple and other OSes.
  //   - A '⌘' is replaced by '⌃' on other OSes.
  def bindShortcutFromKbd(
    clickElem    : html.Element,
    rawShortcut  : String,
    updateDisplay: String => Unit,
    condition    : Option[js.Function0[Boolean]] = None
  ): Option[String] =
    rawShortcut.trimAllToOpt.flatMap { rawShortcut =>

      def makeMousetrapCommand(token: String, convert: Char => Char): String =
        if (ModifiersCharacters.exists(token.contains))
          token.map(convert).map(c => ModifiersMap.getOrElse(c, c.toLower.toString)).mkString("+")
        else
          token.map(_.toLower.toString).mkString(" ")

      def makeDisplayCommand(token: String, convert: Char => Char): String =
        if (ModifiersCharacters.exists(token.contains))
          token.map(convert).map(convertForDisplayIfNotApple).mkString
        else
          (KeyBoardIconCharacter +: token.map(_.toString)).mkString(" ")

      def registerMouseTrapCommand(token: String, convert: Char => Char): Unit = {
        updateDisplay(makeDisplayCommand(token, convert))
        Mousetrap.bind(
          makeMousetrapCommand(token, convert),
          (e: dom.KeyboardEvent, _: String) => {
            if (condition.forall(_.apply())) {
              e.preventDefault()
              clickElem.click()
            }
          }
        )
      }

      rawShortcut.splitTo[List]() match {
        case all   :: Nil                     => registerMouseTrapCommand(all,   convert = convertIfNotApple); Some(all)
        case apple :: _     :: _ if isAppleOs => registerMouseTrapCommand(apple, convert = identity);          Some(apple)
        case _     :: other :: _              => registerMouseTrapCommand(other, convert = identity);          Some(other)
        case Nil                              => None
      }
    }

  def unbindShortcutFromKbd(mousetrapCommand: String): Unit =
    Mousetrap.unbind(mousetrapCommand)
}
