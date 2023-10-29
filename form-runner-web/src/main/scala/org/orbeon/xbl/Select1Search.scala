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
import org.orbeon.facades.Select2.{Success, toJQuerySelect2}
import org.orbeon.jquery._
import org.orbeon.web.DomSupport
import org.orbeon.xforms._
import org.orbeon.xforms.facade.{Controls, XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.Element
import org.scalajs.dom.{MutationObserver, MutationObserverInit, document, html}
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.collection.mutable
import scala.scalajs.js


object Select1Search {
  XBL.declareCompanion(name = "fr|dropdown-select1-search" , js.constructorOf[Select1SearchCompanion])
  XBL.declareCompanion(name = "fr|databound-select1-search", js.constructorOf[Select1SearchCompanion])
}

private class Select1SearchCompanion(containerElem: html.Element) extends XBLCompanion {

  private val    DataPlaceholder           = "data-placeholder"
  private val    DataServicePerformsSearch = "data-service-performs-search"
  private val    DataInitialLabel          = "data-initial-label"
  private val    DataInitialValue          = "data-initial-value"
  private object EventSupport              extends EventListenerSupport

  private val select2SuccessCallbacks      : mutable.Queue[Success] = new mutable.Queue[Select2.Success]
  private var onXFormsSelect1ValueChangeJs : Option[js.Function]    = None
  private var mutationObservers            : List[MutationObserver] = Nil
  private var inputElementOpt              : Option[dom.Element]    = None

  override def init(): Unit = {
    for (select <- Option(querySelect)) {
      val elementWithData       = queryElementWithData
      val servicePerformsSearch = Option(elementWithData.getAttribute(DataServicePerformsSearch)).contains("true")

      // Prevent the propagation of `focusout` so the client doesn't send an `xxforms-value` event when users click on the dropdown,
      // as at that point the `<span class="select2-selection--single">` looses the focus, and since Select2 places that element inside
      // the element that represents the `<xf:select1>`, if that event is left to propagate, the XForms code takes that event as the
      // `<xf:select1>` having lost the focus
      Support.stopFocusOutPropagation(select.parentNode.asInstanceOf[html.Element], _.target, "select2-selection--single")

      // Init Select2
      val jSelect = $(select)
      jSelect.select2(new Select2.Options {
        allowClear     = true
        ajax           = if (servicePerformsSearch) Select2Ajax else null
        width          = "100%" // For Select2 width to update as the viewport width changes
        placeholder    =
          new Select2.Option {
            val id   = "0"
            val text = elementWithData.getAttribute(DataPlaceholder)
          }
      })

      // Register event listeners
      if (servicePerformsSearch) {
        // When the search isn't done by the service, we use a `<fr:databound-select1>`, which sets the value
        // of the bound node, and in that case we also don't need to store the label
        jSelect.on("change", onChangeDispatchFrChange _)
      }
      jSelect.on("select2:open", (onOpen _))
      jSelect.data("select2").on("results:focus", onResultsFocus _)

      // Add `aria-labelledby` pointing to the label, `aria-describedby` pointing to the help and hint
      val comboboxElement = containerElem.querySelector(".select2-selection")
      val LhhaSelectorAriaAttrs = List(
        ".xforms-label"              -> "aria-labelledby",
        ".xforms-help, .xforms-hint" -> "aria-describedby"
      )
      LhhaSelectorAriaAttrs.foreach { case (selector, ariaAttr) =>
        val lhhaElems = containerElem.querySelectorAll(selector).toList.asInstanceOf[List[html.Element]]
        if (lhhaElems.nonEmpty) {
          val lhhaIds = lhhaElems.map(DomSupport.generateIdIfNeeded).mkString(" ")
          comboboxElement.setAttribute(ariaAttr, lhhaIds)
        }
      }

      // Open the dropdown on up/down arrow key press
      containerElem
        .querySelector(".select2-selection")
        .addEventListener(
          "keydown",
          (event: dom.KeyboardEvent) => {
            if (Set("ArrowUp", "ArrowDown")(event.key)) {
              jSelect.select2("open")
              event.stopPropagation() // Prevent scrolling the page
            }
          }
        )

      // Make the clear button accessible with the keyboard
      def makeClearAccessible(): Unit = {
        val clearElementOpt = Option(containerElem.querySelector(".select2-selection__clear"))
        clearElementOpt.foreach { clearElement =>
          clearElement.setAttribute("tabindex", "0")
          clearElement.setAttribute("role", "button")
          EventSupport.addListener(
            clearElement,
            "keydown", // Instead of `keyup`, so our listeners runs before Select2's
            (event: dom.KeyboardEvent) =>
              if (Set(10, 13, 32)(event.keyCode)) { // Enter and space
                event.stopPropagation() // Prevent Select2 from opening the dropdown
                jSelect.value("").trigger("change")
                xformsFocus() // Move the focus from the "x", which disappeared, to the dropdown
              }
          )
        }
      }
      makeClearAccessible()
      jSelect.on("change", makeClearAccessible _)

      if (servicePerformsSearch) {
        initOrUpdatePlaceholderCurrent()
        onAttributeChange(elementWithData, DataPlaceholder,  initOrUpdatePlaceholderCurrent)
        onAttributeChange(elementWithData, DataInitialLabel, initOrUpdatePlaceholderCurrent)
      }

      // Register and remember listener on value change
      if (! servicePerformsSearch) {
        val listener: js.Function = onXFormsSelect1ValueChange _
        onXFormsSelect1ValueChangeJs = Some(listener)
        Controls.afterValueChange.subscribe(listener)
      }
    }
  }

  override def destroy(): Unit = {

    // Remove DOM event listeners
    EventSupport.clearAllListeners()

    // Remove after value change listener
    onXFormsSelect1ValueChangeJs.foreach(Controls.afterValueChange.unsubscribe)
    onXFormsSelect1ValueChangeJs = None

    // Disconnect mutation observers
    mutationObservers.foreach(_.disconnect())
    mutationObservers = Nil

  }

  override def xformsFocus(): Unit = {
    containerElem.querySelector("[tabindex]").asInstanceOf[dom.html.Element].focus()
  }

  def updateSuggestions(results: String, isLastPage: String): Unit = {
    val parsedResults = js.JSON.parse(results)
    val data = new Select2.Data {
      val results    = parsedResults.asInstanceOf[js.Array[Select2.Option]]
      val pagination = new Select2.Pagination {
        val more = !isLastPage.toBoolean
      }
    }
    val success = select2SuccessCallbacks.dequeue()
    success(data)
  }

  private def queryElementWithData = containerElem.querySelector(s":scope > [$DataPlaceholder]")
  private def querySelect          = containerElem.querySelector("select")

  private def initOrUpdatePlaceholderCurrent(): Unit = {
    val select = querySelect
    (for {
      initialLabel <- Option(queryElementWithData.getAttribute(DataInitialLabel))
      initialValue <- Option(queryElementWithData.getAttribute(DataInitialValue))
    } yield {
      val initialOption = dom.document.createElement("option").asInstanceOf[html.Option]
      initialOption.text     = initialLabel
      initialOption.value    = initialValue
      initialOption.selected = true
      select.appendChild(initialOption)
    }).getOrElse {
      while (select.hasChildNodes())
        select.removeChild(select.firstChild)
    }
  }

  // TODO: not specific to the autocomplete, should be moved to a utility class
  private def onAttributeChange(element: Element, attributeName: String, listener: () => Unit): Unit = {
    val observer = new MutationObserver((_, _) => listener())
    mutationObservers = observer :: mutationObservers

    observer.observe(element, MutationObserverInit(
      attributes = true,
      attributeFilter = js.Array(attributeName)
    ))
  }

  // When the value of the underlying dropdown changed, typically because it set based on that the server
  // tells the client, tell the Select2 component that the value has changed
  private def onXFormsSelect1ValueChange(event: js.Dynamic): Unit = {
    val control  = event.control.asInstanceOf[html.Element]
    if (containerElem.querySelector(".xforms-select1") == control)
      $(containerElem).find("select").trigger("change")
  }

  private def onOpen(event: JQueryEventObject): Unit = {
    val dropdownElement = document.querySelector(".select2-dropdown")
    val inputElement    = dropdownElement.querySelector("input")
    val listboxElement  = dropdownElement.querySelector("[role=listbox]")
    inputElement.setAttribute("aria-owns"     , listboxElement.id)
    inputElement.setAttribute("aria-expanded" , "true")
    inputElement.setAttribute("aria-haspopup" , "listbox")
    inputElement.setAttribute("role"          , "combobox")
    inputElementOpt = Some(inputElement)
  }

  private def onResultsFocus(event: JQueryEventObject): Unit = {
    val focusedElement   = event.asInstanceOf[js.Dynamic].element.asInstanceOf[JQuery].get(0).asInstanceOf[html.Element]
    val focusedElementId = DomSupport.generateIdIfNeeded(focusedElement)
    inputElementOpt.foreach(_.setAttribute("aria-activedescendant", focusedElementId))
  }

  private def onChangeDispatchFrChange(event: JQueryEventObject): Unit = {
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

  private def dispatchFrChange(
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

    val delay: Int = Page.getXFormsFormFromHtmlElemOrThrow(containerElem).configuration.delayBeforeIncrementalRequest

    val transport = (
      params  : Select2.Params,
      success : Select2.Success,
      failure : js.Function0[Unit]
    ) => {

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
