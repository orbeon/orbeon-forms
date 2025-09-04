/**
 * Copyright (C) 2025 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichOption

object Pager {

  XBL.declareCompanion("fr|pager", js.constructorOf[PagerCompanion])

  class PagerCompanion(containerElem: html.Element) extends XBLCompanion {

    private var repeatedSectionNameOpt: Option[String] = None

    override def init(): Unit = {
      repeatedSectionNameOpt = Option(containerElem.closest("[id$='-section']")).map(_.id.trimSuffixIfPresent("-section"))
    }

    override def destroy(): Unit = ()

    private var listeners: List[js.Function1[PageChangeEvent, Any]] = Nil

    def addPageChangeListener(listener: js.Function1[PageChangeEvent, Any]): Unit =
      listeners ::= listener

    def removePageChangeListener(listener: js.Function1[PageChangeEvent, Any]): Unit =
      listeners = listeners.filterNot(_ eq listener)

    def onPageChange(_previousPage: Int, _currentPage: Int, pageCount: Int): Unit =
      listeners foreach (_(new PageChangeEvent {
        val repeatedSectionName: js.UndefOr[String] = repeatedSectionNameOpt.orUndefined
        val previousPage       : Int                = _previousPage
        val currentPage        : Int                = _currentPage
        val isStart            : Boolean            = _currentPage == 1
        val isEnd              : Boolean            = _currentPage == pageCount
      }))
  }

  trait PageChangeEvent extends js.Object {
    val repeatedSectionName: js.UndefOr[String]
    val previousPage       : Int
    val currentPage        : Int
    val isStart            : Boolean
    val isEnd              : Boolean
  }
}