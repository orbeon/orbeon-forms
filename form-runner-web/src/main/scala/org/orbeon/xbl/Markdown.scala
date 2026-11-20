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

import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object Markdown {

  XFormsXbl.declareCompanion("fr|markdown", js.constructorOf[MarkdownCompanion])

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
          if (! isDesignTime && (ev.metaKey || ev.ctrlKey) && ! ev.altKey && ! ev.shiftKey) {
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

      ta.focus()
      ta.value = value.substring(0, start) + replacement + value.substring(end)
      ta.setSelectionRange(start + selStartOffset, start + selEndOffset)
      ta.dispatchEvent(new dom.Event(DomEventNames.Input, new dom.EventInit { bubbles = true }))
      ta.dispatchEvent(new dom.Event(DomEventNames.Change, new dom.EventInit { bubbles = true }))
    }
  }
}
