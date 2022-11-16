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
import org.orbeon.xforms.facade.{Controls, XBL, XBLCompanion}
import org.orbeon.xforms._
import org.scalajs.dom
import org.scalajs.dom.{MutationObserver, MutationObserverInit, document, html}
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.collection.mutable
import scala.scalajs.js

object Select1Search {
  XBL.declareCompanion(name = "fr|dropdown-select1-search" , new Select1SearchCompanion)
  XBL.declareCompanion(name = "fr|databound-select1-search", new Select1SearchCompanion)
}

private class Select1SearchCompanion extends XBLCompanion {

  private val DataPlaceholder              = "data-placeholder"
  private val DataServicePerformsSearch    = "data-service-performs-search"
  private val DataInitialLabel             = "data-initial-label"
  private val DataInitialValue             = "data-initial-value"

  private val select2SuccessCallbacks      : mutable.Queue[Success] = new mutable.Queue[Select2.Success]
  private var onXFormsSelect1ValueChangeJs : Option[js.Function]    = None
  private var mutationObservers            : List[MutationObserver] = Nil
  private var inputElementOpt              : Option[dom.Element]    = None

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

        // Init Select2
        jSelect.select2(new Select2.Options {
          allowClear     = true
          ajax           = if (servicePerformsSearch) new Select2Ajax(select2SuccessCallbacks, containerElem) else null
          width          = "100%" // For Select2 width to update as the viewport width changes
          placeholder    =
            new Select2.Option {
              val id   = "0"
              val text = elementWithData.attr(DataPlaceholder).get
            }
        })

        // Register event listeners
        val isDatabound = containerElem.classList.contains("xbl-fr-databound-select1-search")
        if (isDatabound) jSelect.on("change", onChange _)
        jSelect.on("select2:open", (onOpen _))
        jSelect.data("select2").on("results:focus", onResultsFocus _)

        // Update `aria-labelledby`, so the screen reader can read the field's label when it gets the focus
        val comboboxElement = containerElem.querySelector(".select2-selection")
        val labelElement    = containerElem.querySelector(".xforms-label")
        if (labelElement != null) { // Field might not have a label, which happens in Form Builder
          val labelId       = DomSupport.generateIdIfNeeded(labelElement)
          comboboxElement.setAttribute("aria-labelledby", labelId)
        }
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
    onXFormsSelect1ValueChangeJs.foreach(Controls.afterValueChange.unsubscribe)
    onXFormsSelect1ValueChangeJs = None

    // Disconnect mutation observers
    mutationObservers.foreach(_.disconnect())
    mutationObservers = Nil
  }

  override def xformsFocus(): Unit =
    containerElem.querySelector("select").asInstanceOf[dom.html.Select].focus()

  // TODO: not specific to the autocomplete, should be moved to a utility class
  private def onAttributeChange(element: JQuery, attributeName: String, listener: () => Unit): Unit = {
    val observer = new MutationObserver((_, _) => listener())
    mutationObservers = observer :: mutationObservers

    observer.observe(element.get(0), MutationObserverInit(
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

  private def onChange(event: JQueryEventObject): Unit = {
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

}

// We shouldn't have to declare the constructor parameters as `val`, but if we don't, at runtime the JavaScript object
// for doesn't have a property for `select2SuccessCallbacks`, so we get an `undefined` when trying to access it.
private class Select2Ajax(
  val select2SuccessCallbacks : mutable.Queue[Success],
  val containerElem           : html.Element
) extends Select2.Ajax {

  val delay: Int = Page.getFormFromElemOrThrow(containerElem).configuration.delayBeforeIncrementalRequest

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
