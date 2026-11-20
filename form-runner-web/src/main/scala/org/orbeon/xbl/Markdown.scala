/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object Markdown {

  import Private.*

  case class TextEdit(
    replaceStart   : Int,
    replaceEnd     : Int,
    replacement    : String,
    selectionStart : Int,
    selectionEnd   : Int
  ) {
    def applyTo(value: String): String =
      value.substring(0, replaceStart) + replacement + value.substring(replaceEnd)
  }

  XFormsXbl.declareCompanion("fr|markdown", js.constructorOf[MarkdownCompanion])

  private[xbl] def computeEnterEdit(value: String, selStart: Int, selEnd: Int): Option[TextEdit] = {
    val lineStart        = value.lastIndexOf('\n', selStart - 1) + 1
    val lineEnd          = { val idx = value.indexOf('\n', selEnd); if (idx == -1) value.length else idx }
    val lineBeforeCursor = value.substring(lineStart, selStart)
    val lineAfterCursor  = value.substring(selEnd, lineEnd)

    lineBeforeCursor match {
      case TaskListRegex(indent, bullet, spaceBeforeBox, spaceAfterBox, rest) =>
        if (selStart == selEnd && rest.trim.isEmpty && lineAfterCursor.trim.isEmpty) {
          Some(TextEdit(lineStart, lineEnd, "", lineStart, lineStart))
        } else {
          val replacement = s"\n$indent$bullet$spaceBeforeBox[ ]$spaceAfterBox"
          val cursor      = selStart + replacement.length
          Some(TextEdit(selStart, selEnd, replacement, cursor, cursor))
        }

      case UnorderedListRegex(indent, bullet, spaces, rest) =>
        if (selStart == selEnd && rest.trim.isEmpty && lineAfterCursor.trim.isEmpty) {
          Some(TextEdit(lineStart, lineEnd, "", lineStart, lineStart))
        } else {
          val replacement = s"\n$indent$bullet$spaces"
          val cursor      = selStart + replacement.length
          Some(TextEdit(selStart, selEnd, replacement, cursor, cursor))
        }

      case OrderedListRegex(indent, numStr, delim, spaces, rest) =>
        numStr.toLongOption.map { num =>
          if (selStart == selEnd && rest.trim.isEmpty && lineAfterCursor.trim.isEmpty) {
            TextEdit(lineStart, lineEnd, "", lineStart, lineStart)
          } else {
            val nextNum  = num + 1
            val inserted = s"\n$indent$nextNum$delim$spaces"
            val cursor   = selStart + inserted.length

            renumberSubsequentOrderedItems(value, lineEnd + 1, indent, delim, nextNum + 1) match {
              case Some((renumberEnd, renumberedTail)) =>
                TextEdit(
                  replaceStart   = selStart,
                  replaceEnd     = renumberEnd,
                  replacement    = inserted + lineAfterCursor + "\n" + renumberedTail,
                  selectionStart = cursor,
                  selectionEnd   = cursor
                )
              case None =>
                TextEdit(selStart, selEnd, inserted, cursor, cursor)
            }
          }
        }

      case _ =>
        None
    }
  }

  private[xbl] def computePasteEdit(
    value      : String,
    selStart   : Int,
    selEnd     : Int,
    pastedText : String
  ): Option[TextEdit] =
    if (selStart < selEnd) {
      val sel = value.substring(selStart, selEnd)
      for {
        url <- pastedText.trimAllToOpt
        if HttpUrlRegex.pattern.matcher(url).matches()
        if ! sel.contains('\n') && ! HttpUrlRegex.pattern.matcher(sel.trim).matches()
      } yield {
        val replacement = s"[$sel]($url)"
        val cursor      = selStart + replacement.length
        TextEdit(selStart, selEnd, replacement, cursor, cursor)
      }
    } else {
      None
    }

  private class MarkdownCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private def isDesignTime: Boolean =
      containerElem.closestOpt(".fb-main").isDefined

    private def findTextAreaOpt: Option[html.TextArea] =
      containerElem.queryNestedElems[html.TextArea](".fr-markdown-write-panel textarea").headOption

    override def init(): Unit = {
      EventSupport.addListener[dom.MouseEvent](
        containerElem,
        DomEventNames.Click,
        ev => {
          if (! isDesignTime) {
            ev.target.asInstanceOf[dom.Node] match {
              case el: html.Element =>
                el.closestOpt("button.fr-markdown-tool").foreach { btn =>
                  Option(btn.getAttribute("data-md-action")).foreach { action =>
                    ev.preventDefault()
                    findTextAreaOpt.foreach(applyAction(_, action))
                  }
                }
              case _ =>
            }
          }
        }
      )

      EventSupport.addListener[dom.KeyboardEvent](
        containerElem,
        DomEventNames.KeyDown,
        ev => {
          if (! isDesignTime && ! ev.isComposing) {
            if ((ev.metaKey || ev.ctrlKey) && ! ev.altKey && ! ev.shiftKey) {
              val actionOpt = ev.key.toLowerCase match {
                case "b" => Some("bold")
                case "i" => Some("italic")
                case "k" => Some("link")
                case _   => None
              }
              actionOpt.foreach { action =>
                findTextAreaOpt.filter(_ eq ev.target).foreach { ta =>
                  ev.preventDefault()
                  applyAction(ta, action)
                }
              }
            } else if (ev.key == "Enter" && ! ev.shiftKey && ! ev.ctrlKey && ! ev.metaKey && ! ev.altKey) {
              findTextAreaOpt.filter(_ eq ev.target).foreach { ta =>
                computeEnterEdit(ta.value, ta.selectionStart, ta.selectionEnd).foreach { edit =>
                  ev.preventDefault()
                  applyTextEdit(ta, edit, dispatchChange = false)
                }
              }
            }
          }
        }
      )

      EventSupport.addListener[dom.ClipboardEvent](
        containerElem,
        DomEventNames.Paste,
        ev => {
          if (! isDesignTime) {
            findTextAreaOpt.filter(_ eq ev.target).foreach { ta =>
              val pasted = Option(ev.clipboardData).map(_.getData("text/plain")).getOrElse("")
              computePasteEdit(ta.value, ta.selectionStart, ta.selectionEnd, pasted).foreach { edit =>
                ev.preventDefault()
                applyTextEdit(ta, edit, dispatchChange = false)
              }
            }
          }
        }
      )
    }

    override def destroy(): Unit =
      EventSupport.clearAllListeners()

    private def applyAction(ta: html.TextArea, action: String): Unit = {
      val value = ta.value
      val start = ta.selectionStart
      val end   = ta.selectionEnd
      val sel   = value.substring(start, end)

      val (replacement, selStartOffset, selEndOffset) = action match {
        case "bold" =>
          val inner = if (sel.nonEmpty) sel else "bold text"
          (s"**$inner**", 2, 2 + inner.length)

        case "italic" =>
          val inner = if (sel.nonEmpty) sel else "italic text"
          (s"*$inner*", 1, 1 + inner.length)

        case "heading" =>
          val inner = if (sel.nonEmpty) sel else "Heading"
          val prefix = if (start == 0 || value.charAt(start - 1) == '\n') "## " else "\n## "
          (s"$prefix$inner", prefix.length, prefix.length + inner.length)

        case "link" =>
          if (sel.startsWith("http://") || sel.startsWith("https://")) {
            val label = "link text"
            (s"[$label]($sel)", 1, 1 + label.length)
          } else {
            val label = if (sel.nonEmpty) sel else "link text"
            val url   = "https://"
            val text  = s"[$label]($url)"
            (text, label.length + 3, label.length + 3 + url.length)
          }

        case "bullet-list" =>
          val lines = (if (sel.nonEmpty) sel else "List item").split("\n", -1).map(l => s"- $l").mkString("\n")
          val prefix = if (start == 0 || value.charAt(start - 1) == '\n') "" else "\n"
          (s"$prefix$lines", prefix.length + 2, prefix.length + lines.length)

        case "numbered-list" =>
          val lines = (if (sel.nonEmpty) sel else "List item").split("\n", -1).zipWithIndex.map { case (l, i) => s"${i + 1}. $l" }.mkString("\n")
          val prefix = if (start == 0 || value.charAt(start - 1) == '\n') "" else "\n"
          (s"$prefix$lines", prefix.length + 3, prefix.length + lines.length)

        case "quote" =>
          val lines = (if (sel.nonEmpty) sel else "Quote").split("\n", -1).map(l => s"> $l").mkString("\n")
          val prefix = if (start == 0 || value.charAt(start - 1) == '\n') "" else "\n"
          (s"$prefix$lines", prefix.length + 2, prefix.length + lines.length)

        case "code" =>
          if (sel.contains("\n")) {
            val text = s"```\n$sel\n```"
            (text, 4, 4 + sel.length)
          } else {
            val inner = if (sel.nonEmpty) sel else "code"
            (s"`$inner`", 1, 1 + inner.length)
          }

        case _ =>
          return
      }

      applyTextEdit(
        ta,
        TextEdit(start, end, replacement, start + selStartOffset, start + selEndOffset),
        dispatchChange = true
      )
    }
  }

  private object Private {

    val TaskListRegex      = """^(\s*)([-*+])(\s+)\[[ xX]\](\s+)(.*)$""".r
    val UnorderedListRegex = """^(\s*)([-*+])(\s+)(.*)$""".r
    val OrderedListRegex   = """^(\s*)(\d+)([.)])(\s+)(.*)$""".r
    val HttpUrlRegex       = """^https?://\S+$""".r

    def applyTextEdit(ta: html.TextArea, edit: TextEdit, dispatchChange: Boolean): Unit = {
      ta.focus()
      ta.setSelectionRange(edit.replaceStart, edit.replaceEnd)

      val expectedValue = edit.applyTo(ta.value)

      val appliedViaExecCommand =
        try {
          val ok =
            if (edit.replacement.isEmpty)
              dom.document.execCommand("delete", showUI = false)
            else
              dom.document.execCommand("insertText", showUI = false, edit.replacement)
          ok && ta.value == expectedValue
        } catch {
          case _: Throwable => false
        }

      if (! appliedViaExecCommand) {
        ta.value = expectedValue
        ta.dispatchEvent(new dom.Event(DomEventNames.Input, new dom.EventInit { bubbles = true }))
      }

      ta.setSelectionRange(edit.selectionStart, edit.selectionEnd)

      if (dispatchChange)
        ta.dispatchEvent(new dom.Event(DomEventNames.Change, new dom.EventInit { bubbles = true }))
    }

    def renumberSubsequentOrderedItems(
      value        : String,
      startOffset  : Int,
      indent       : String,
      delim        : String,
      firstNextNum : Long
    ): Option[(Int, String)] =
      if (startOffset >= value.length) {
        None
      } else {
        val committed = new StringBuilder
        val pending   = new StringBuilder
        var pos       = startOffset
        var nextNum   = firstNextNum
        var lastEnd   = -1
        var done      = false

        while (! done && pos <= value.length) {
          val nextNl  = value.indexOf('\n', pos)
          val lineEnd = if (nextNl == -1) value.length else nextNl
          val line    = value.substring(pos, lineEnd)

          if (line.trim.isEmpty) {
            done = true
          } else {
            val leadingWsLen = line.segmentLength(c => c == ' ' || c == '\t')
            if (leadingWsLen > indent.length) {
              if (committed.nonEmpty || pending.nonEmpty)
                pending.append('\n')
              pending.append(line)
            } else if (leadingWsLen < indent.length) {
              done = true
            } else {
              line match {
                case OrderedListRegex(lIndent, _, lDelim, lSpaces, lRest) if lIndent == indent && lDelim == delim =>
                  if (pending.nonEmpty) {
                    committed.append(pending)
                    pending.clear()
                  }
                  if (committed.nonEmpty)
                    committed.append('\n')
                  committed.append(lIndent).append(nextNum).append(lDelim).append(lSpaces).append(lRest)
                  nextNum += 1
                  lastEnd = lineEnd
                case _ =>
                  done = true
              }
            }
          }

          if (nextNl == -1)
            done = true
          else
            pos = nextNl + 1
        }

        if (lastEnd != -1) Some((lastEnd, committed.toString)) else None
      }
  }
}
