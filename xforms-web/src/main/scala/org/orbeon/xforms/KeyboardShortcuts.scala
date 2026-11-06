package org.orbeon.xforms

import org.orbeon.facades.{Bowser, Mousetrap}
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.ext.*
import org.scalajs.dom.{MutationObserver, MutationObserverInit, html}

import scala.collection.mutable
import scala.scalajs.js


object KeyboardShortcuts {

  val KeyBoardIconCharacter = "⌨\uFE0F"

  private val KbdSelector        = "kbd[data-orbeon-keyboard-shortcut]"
  private val ShortcutAttributes = js.Array("data-orbeon-keyboard-shortcut", "data-orbeon-keyboard-shortcut-condition")

  // Track which `<kbd>` element currently owns each Mousetrap command so that an async unbind on a previous `<kbd>`
  // does not unbind a command that was already rebound by another `<kbd>`.
  private var activeKbdByCommand: Map[String, html.Element] = Map.empty

  lazy val isAppleOs: Boolean =
    Bowser.osname.exists(Set("macOS", "iOS"))

  private val FormFieldTags = Set("INPUT", "SELECT", "TEXTAREA")
  private val ModifiersMap  = Map(
    '⌘' -> "command", // same as 'meta'
    '⌃' -> "ctrl",    // cross-platform
    '⌥' -> "option",  // same as 'alt'
    '⇧' -> "shift",   // cross-platform
  )

  private def isFormField(element: dom.Element): Boolean =
    FormFieldTags.contains(element.tagName) ||
    element.asInstanceOf[js.Dynamic].isContentEditable.asInstanceOf[Boolean]

  // Handle shortcuts with modifiers inside form fields, except for native edit commands like cut/copy/paste/undo
  Mousetrap.asInstanceOf[js.Dynamic].prototype.stopCallback =
    (e: dom.KeyboardEvent, element: dom.Element, _: String) => {
      val isModifierKey   = (e.altKey || e.ctrlKey || e.metaKey)
      val isNativeEditKey = (e.ctrlKey || e.metaKey) &&
                            Set("x", "c", "v", "z").contains(e.key.toLowerCase)
      isFormField(element) && (!isModifierKey || isNativeEditKey)
    }

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

  // Syntax for the keyboard shortcut in the `data-orbeon-keyboard-shortcut` attribute:
  // - The shortcut is a string with one or two tokens separated by a space.
  //   - No tokens means no shortcut.
  //   - Extra tokens are ignored.
  // - If 2 tokens are present, the first token is for Apple OSes, the second token is for other OSes.
  //    - For other OSes, '⌘' means Meta and '⌥' means Alt.
  // - If 1 token is present
  //   - it is used for both Apple and other OSes.
  //   - A '⌘' is replaced by '⌃' on other OSes.
  def bindShortcutFromKbd(
    buttonOrAnchor: html.Element,
    updateDisplay : (String, html.Element) => Unit
  ): Option[dom.MutationObserver] = {

    val registeredCommandByKbd = mutable.Map.empty[html.Element, String]

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

    def unbindKbd(kbd: html.Element): Unit =
      registeredCommandByKbd.remove(kbd).foreach { command =>
        if (activeKbdByCommand.get(command).contains(kbd)) {
          activeKbdByCommand -= command
          Mousetrap.unbind(command)
        }
      }

    def registerMouseTrapCommand(
      clickElem: html.Element,
      kbd      : html.Element,
      token    : String,
      convert  : Char => Char,
      condition: Option[js.Function0[Boolean]]
    ): String = {
      val mousetrapCommand = makeMousetrapCommand(token, convert)
      activeKbdByCommand += mousetrapCommand -> kbd
      Mousetrap.bind(
        mousetrapCommand,
        (e: dom.KeyboardEvent, _: String) => {
          if (condition.forall(_.apply())) {
            e.preventDefault()
            dom.document.activeElementOpt.foreach { activeElem =>
              if (activeElem == clickElem) {
                clickElem.click()
              } else {
                // We need to tell the server about any changes to the control that currently has focus before
                // triggering the click. Otherwise we might lose that value change, for example if the click closes
                // the dialog containing the control.
                activeElem.dispatchEvent(new dom.Event("change", new dom.EventInit {
                  bubbles = true
                }))
                clickElem.click()
              }
            }
          }
        }
      )
      mousetrapCommand
    }

    def bindOrUpdateKbd(kbd: html.Element): Unit = {

      // Unbind any previously registered shortcut for this `<kbd>` or any detached `<kbd>`
      unbindKbd(kbd)
      registeredCommandByKbd.keys.filterNot(buttonOrAnchor.contains).toList.foreach(unbindKbd)

      val clickElem = kbd.closestOpt("button, a").getOrElse(buttonOrAnchor)

      // Read the optional condition
      val condition =
        kbd.dataset.get("orbeonKeyboardShortcutCondition")
          .filter(_ == "clipboard-empty")
          .map(_ => (() => dom.window.getSelection().toString.isEmpty): js.Function0[Boolean])

      // Register the new shortcut if present, or clear the display if empty
      val registeredCommandOpt =
        kbd.dataset.get("orbeonKeyboardShortcut").flatMap(_.trimAllToOpt).flatMap { rawShortcut =>
          rawShortcut.splitTo[List]() match {
            case all   :: Nil                     =>
              val command = registerMouseTrapCommand(clickElem, kbd, all, convert = convertIfNotApple, condition)
              updateDisplay(makeDisplayCommand(all, convertIfNotApple), kbd)
              Some(command)
            case apple :: _     :: _ if isAppleOs =>
              val command = registerMouseTrapCommand(clickElem, kbd, apple, convert = identity, condition)
              updateDisplay(makeDisplayCommand(apple, identity), kbd)
              Some(command)
            case _     :: other :: _              =>
              val command = registerMouseTrapCommand(clickElem, kbd, other, convert = identity, condition)
              updateDisplay(makeDisplayCommand(other, identity), kbd)
              Some(command)
            case Nil                              =>
              None
          }
        }

      registeredCommandOpt match {
        case Some(command) =>
          registeredCommandByKbd(kbd) = command
        case None          =>
          updateDisplay("", kbd)
      }
    }

    val initialKbds = buttonOrAnchor.querySelectorAllT(KbdSelector)
    initialKbds.nonEmpty.option {
      initialKbds.foreach(bindOrUpdateKbd)
      val observer = new MutationObserver((mutations, _) => {
        mutations.foreach { mutation =>
          if (mutation.`type` == "childList") {
            mutation.removedNodes.foreach {
              case element: html.Element =>
                element.queryNestedElems(KbdSelector, includeSelf = true).foreach(unbindKbd)
              case _ =>
            }
            mutation.addedNodes.foreach {
              case element: html.Element =>
                element.queryNestedElems(KbdSelector, includeSelf = true).foreach(bindOrUpdateKbd)
              case _ =>
            }
          } else if (mutation.`type` == "attributes") {
            mutation.target match {
              case kbd: html.Element if kbd.matches(KbdSelector) =>
                bindOrUpdateKbd(kbd)
              case _ =>
            }
          }
        }
      })
      observer.observe(
        buttonOrAnchor,
        new MutationObserverInit {
          childList       = true
          subtree         = true
          attributes      = true
          attributeFilter = ShortcutAttributes
        }
      )
      observer
    }
  }
}
