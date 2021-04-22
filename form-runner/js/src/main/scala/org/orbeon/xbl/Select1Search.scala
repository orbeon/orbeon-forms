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

import org.orbeon.facades.Select2
import org.orbeon.facades.Select2.toJQuerySelect2
import org.orbeon.jquery._
import org.orbeon.xforms.facade.{Controls, Properties, XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, ServerValueStore, Support}
import org.scalajs.dom
import org.scalajs.dom.{FocusEvent, MutationObserver, MutationObserverInit, html}
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.collection.mutable
import scala.scalajs.js

object Select1Search {
  XBL.declareCompanion(name = "fr|dropdown-select1-search" , new Select1SearchCompanion())
  XBL.declareCompanion(name = "fr|databound-select1-search", new Select1SearchCompanion())
}

private class Select1SearchCompanion extends XBLCompanion {

  import Private._

  val DataPlaceholder           = "data-placeholder"
  val DataServicePerformsSearch = "data-service-performs-search"
  val DataInitialLabel          = "data-initial-label"
  val DataInitialValue          = "data-initial-value"

  override def init(): Unit = {
    for {
      jContainer                    <- $(containerElem).headJQuery
      (xformsSelect, jXFormsSelect) <- jContainer.find(".xforms-select1").headElemJQuery
      (select, jSelect)             <- jXFormsSelect.find("select").headElemJQuery
      htmlSelect                    = select.asInstanceOf[dom.html.Select]
    } locally {

      val elementWithData        = jContainer.children(s"[$DataPlaceholder]")
      val servicePerformsSearch  = elementWithData.attr(DataServicePerformsSearch).contains("true")

      // Prevent the propagation of `focusout` so the client doesn't send an `xxforms-value` event when users click on the dropdown,
      // as at that point the `<span class="select2-selection--single">` looses the focus, and since Select2 places that element inside
      // the element that represents the `<xf:select1>`, if that event is left to propagate, the XForms code takes that event as the
      // `<xf:select1>` having lost the focus
      Support.stopFocusOutPropagation(xformsSelect, _.target, "select2-selection--single")

      def initOrUpdatePlaceholder(): Unit = {

        // Store server value for the dropdown on initialization, as a workaround to #4198
        ServerValueStore.set(jXFormsSelect.attr("id").get, htmlSelect.value)

        if (servicePerformsSearch) {
          val initialLabel = elementWithData.attr(DataInitialLabel).get
          val initialValue = elementWithData.attr(DataInitialValue).get
          if (initialValue.nonEmpty) {
            val initialOption = dom.document.createElement("option").asInstanceOf[html.Option]
            initialOption.text     = initialLabel
            initialOption.value    = initialValue
            initialOption.selected = true
            htmlSelect.appendChild(initialOption)
          } else {
            while (htmlSelect.hasChildNodes())
              htmlSelect.removeChild(htmlSelect.firstChild)
          }
        }

        object options extends Select2.Options {
          allowClear     = true
          ajax           = if (servicePerformsSearch) Select2Ajax else null
          width          = "100%" // For Select2 width to update as the viewport width changes
          placeholder    =
            new Select2.Option {
              val id   = "0"
              val text = elementWithData.attr(DataPlaceholder).get
            }
        }

        jSelect.select2(options)
        val isDatabound = containerElem.classList.contains("xbl-fr-databound-select1-search")
        if (isDatabound)
          jSelect.on("change", onChange _)
      }

      initOrUpdatePlaceholder()
      onAttributeChange(elementWithData, DataPlaceholder,  initOrUpdatePlaceholder)
      onAttributeChange(elementWithData, DataInitialLabel, initOrUpdatePlaceholder)

      // Register and remember listener on value change
      if (! servicePerformsSearch) {
        val listener: js.Function = onXFormsSelect1ValueChange _
        onXFormsSelect1ValueChangeJs = Some(listener)
        Controls.afterValueChange.subscribe(listener)
      }
    }
  }

  override def destroy(): Unit = {
    // Unsubscribe to listener on value change
    onXFormsSelect1ValueChangeJs.foreach { listener =>
      Controls.afterValueChange.unsubscribe(listener)
      onXFormsSelect1ValueChangeJs = None
    }
  }

  override def xformsFocus(): Unit =
    containerElem.querySelector("select").asInstanceOf[dom.html.Select].focus()

  def updateSuggestions(results: String, isLastPage: String): Unit = {
    val parsedResults = js.JSON.parse(results)
    val data = new Select2.Data {
      val results    = parsedResults.asInstanceOf[js.Array[Select2.Option]]
      val pagination = new Select2.Pagination {
        val more = ! isLastPage.toBoolean
      }
    }
    val success = select2SuccessCallbacks.dequeue()
    success(data)
  }

  private object Private {

    val select2SuccessCallbacks                           = new mutable.Queue[Select2.Success]
    var onXFormsSelect1ValueChangeJs: Option[js.Function] = None

    // TODO: not specific to the autocomplete, should be moved to a utility class
    def onAttributeChange(element: JQuery, attributeName: String, listener: () => Unit) {
      val observer = new MutationObserver((_, _) => listener())
      observer.observe(element.get(0), MutationObserverInit(
        attributes = true,
        attributeFilter = js.Array(attributeName)
      ))
    }

    // When the value of the underlying dropdown changed, typically because it set based on that the server tells the client,
    // tell the Select2 component that the value has changed
    def onXFormsSelect1ValueChange(event: js.Dynamic): Unit = {
      val control  = event.control.asInstanceOf[html.Element]
      val newValue = event.newValue.asInstanceOf[String]
      if (containerElem.querySelector(".xforms-select1") == control)
        $(containerElem).find("select").trigger("change")
    }

    def onChange(event: JQueryEventObject): Unit = {
      val htmlSelect = event.target.asInstanceOf[html.Select]
      if (htmlSelect.selectedIndex == -1) {
        // `selectedIndex` is -1 when the value was cleared
        dispatchFrChange(
          label = "",
          value = ""
        )
      } else {
        val selectedOption = htmlSelect.options(htmlSelect.selectedIndex)
        dispatchFrChange(
          label = selectedOption.text,
          value = selectedOption.value
        )
      }
    }

    def dispatchFrChange(
      label : String,
      value : String
    ): Unit = {
      AjaxClient.fireEvent(
        AjaxEvent(
          eventName  = "fr-change",
          targetId   = containerElem.id,
          properties = Map(
            "fr-label" -> label,
            "fr-value" -> value
          )
        )
      )
    }

    object Select2Ajax extends Select2.Ajax {

      val delay = Properties.delayBeforeIncrementalRequest.get()

      def transport(
        params  : Select2.Params,
        success : Select2.Success,
        failure : js.Function0[Unit]
      ): Unit = {

        val searchValue = params.data.term.getOrElse("")
        val searchPage  = params.data.page.getOrElse(1)
        select2SuccessCallbacks.enqueue(success)
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName  = "fr-search",
            targetId   = containerElem.id,
            properties = Map(
              "fr-search-value" -> searchValue,
              "fr-search-page"  -> searchPage.toString
            )
          )
        )
      }
    }
  }
}
