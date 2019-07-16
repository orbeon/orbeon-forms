/**
 * Copyright (C) 2019 Orbeon, Inc.
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

import org.orbeon.xbl.Select2.toJQuerySelect2
import org.orbeon.xforms.{$, DocumentAPI}
import org.orbeon.xforms.facade.{Properties, XBL, XBLCompanion}
import org.scalajs.dom.{MutationObserver, MutationObserverInit}
import org.scalajs.dom
import org.scalajs.jquery.JQuery

import scala.collection.mutable
import scala.scalajs.js

object Select1Search {
  XBL.declareCompanion(name = "fr|dropdown-select1-search" , new Select1SearchCompanion())
  XBL.declareCompanion(name = "fr|databound-select1-search", new Select1SearchCompanion())
}

private class Select1SearchCompanion extends XBLCompanion {

  val DataPlaceholder           = "data-placeholder"
  val DataServicePerformsSearch = "data-service-performs-search"

  def jContainer : JQuery = $(containerElem)
  def jSelect    : JQuery = jContainer.find("select")

  override def init(): Unit = {

    val elementWithData = jContainer.children(s"[$DataPlaceholder]")
    val performsSearch  = elementWithData.attr(DataServicePerformsSearch).contains("true")

    def initOrUpdatePlaceholder() {
      jSelect.select2(new Select2.Options {
        val placeholder =
          new Select2.Option {
            val id   = "0"
            val text = elementWithData.attr(DataPlaceholder).get
          }
        val ajax =
          if (performsSearch)
            Select2Ajax
          else
            js.undefined
      })
    }

    initOrUpdatePlaceholder()
    onAttributeChange(elementWithData, DataPlaceholder, initOrUpdatePlaceholder)
  }

  // TODO: not specific to the autocomplete, should be moved to a utility class
  private def onAttributeChange(element: JQuery, attributeName: String, listener: () ⇒ Unit) {
    val observer = new MutationObserver((_, _) ⇒ listener())
    observer.observe(element.get(0), MutationObserverInit(
      attributes = true,
      attributeFilter = js.Array(attributeName)
    ))
  }

  var select2SuccessCallbacks = new mutable.Queue[Select2.Success]

  def updateSuggestions(results: String): Unit = {
    val parsedResults = js.JSON.parse(results)
    val data = new Select2.Data {
      val results    = parsedResults.asInstanceOf[js.Array[Select2.Option]]
      val pagination = new Select2.Pagination {
        val more = false
      }
    }
    val success = select2SuccessCallbacks.dequeue()
    success(data)
  }

  object Select2Ajax extends Select2.Ajax {

    val delay = Properties.delayBeforeIncrementalRequest.get()

    def transport(
      params  : Select2.Params,
      success : Select2.Success,
      failure : js.Function0[Unit]
    ): Unit = {

      val searchValue = params.data.term.getOrElse("")
      searchValue match {
        case "" ⇒
          val htmlSelect = jSelect.get(0).asInstanceOf[dom.html.Select]
          val resultsFromOptions =
            htmlSelect.options.map { option ⇒
              new Select2.Option {
                val id = option.value
                val text = option.text
              }
            }
          val data =
            new Select2.Data {
              val results = resultsFromOptions
              val pagination = new Select2.Pagination {
                val more = false
              }
            }
          success(data)
        case _ ⇒
          select2SuccessCallbacks.enqueue(success)
          DocumentAPI.dispatchEvent(
            targetId = containerElem.id,
            eventName = "fr-search",
            properties = js.Dictionary(
              "fr-search-value" → searchValue
            )
          )
      }
    }
  }
}

private object Select2 {

  implicit def toJQuerySelect2(jQuery: JQuery): JQuerySelect2 =
      jQuery.asInstanceOf[JQuerySelect2]

  @js.native
  trait JQuerySelect2 extends JQuery {
    def select2(options: Options): Unit = js.native
  }

  trait Options extends js.Object {
    val placeholder : Option
    val ajax        : js.UndefOr[Ajax]
  }

  trait Option extends js.Object {
    val id   : String
    val text : String
  }

  trait ParamsData extends js.Object {
    val term: js.UndefOr[String]
  }

  trait Params extends js.Object {
    val data: ParamsData
  }

  trait Data extends js.Object {
    val results    : js.Array[Option]
    val pagination : Pagination
  }

  trait Pagination extends js.Object {
    val more: Boolean
  }

  type Success = js.Function1[Data, Unit]

  trait Ajax extends js.Object {
    val delay: Int
    def transport(
      params  : Params,
      success : Success,
      failure : js.Function0[Unit]
    ): Unit
  }

}
