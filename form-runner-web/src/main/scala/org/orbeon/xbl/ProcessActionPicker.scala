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
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{AjaxClient, DocumentAPI}
import org.scalajs.dom
import org.scalajs.dom.html


object ProcessActionPicker {

  import Private.*

  locally {
    AjaxClient.ajaxResponseProcessed.add(_ => syncHiddenInputs())

    dom.document.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        for {
          target     <- event.targetOpt
          triggerBtn <- target.closestOpt(TriggerSelector)
        } {
          event.preventDefault()
          event.stopPropagation()
          for {
            fieldEl <- triggerBtn.closestOpt(FieldSelector)
            if ! fieldEl.booleanAttValueOpt("data-orbeon-readonly").contains(true)
          } {
            if (PopoverSelectPicker.isOpenFor(triggerBtn))
              PopoverSelectPicker.close(restoreFocus = true)
            else
              openForField(fieldEl, triggerBtn)
          }
        }
      }
    )

    dom.document.addEventListener(
      "keydown",
      (event: dom.KeyboardEvent) => {
        if (event.key == "ArrowDown" || event.key == "ArrowUp") {
          for {
            target     <- event.targetOpt
            triggerBtn <- target.closestOpt(TriggerSelector)
            if ! PopoverSelectPicker.isOpenFor(triggerBtn)
            fieldEl    <- triggerBtn.closestOpt(FieldSelector)
            if ! fieldEl.booleanAttValueOpt("data-orbeon-readonly").contains(true)
          } {
            event.preventDefault()
            openForField(fieldEl, triggerBtn)
          }
        }
      }
    )

    dom.document.addEventListener(
      "focusin",
      (event: dom.FocusEvent) => {
        for {
          target  <- event.targetOpt
          if target.matches(s"$HiddenInputSelector input")
          fieldEl <- target.closestOpt(FieldSelector)
          trigger <- fieldEl.querySelectorOpt(TriggerSelector)
        } {
          val currentAction = fieldEl.attValueOpt("data-orbeon-action-name").flatMap(_.trimAllToOpt)
          val isReadonly    = fieldEl.booleanAttValueOpt("data-orbeon-readonly").contains(true)
          if (currentAction.isEmpty && ! isReadonly)
            openForField(fieldEl, trigger)
          else
            trigger.focus()
        }
      }
    )

    syncHiddenInputs()
  }

  private object Private {

    val EditorSelector      = ".xbl-fr-dialog-process-editor, .xbl-fr-process-editor"
    val CatalogSelector     = ".fr-process-action-catalog"
    val FieldSelector       = ".fr-process-action-picker-field"
    val TriggerSelector     = ".fr-process-action-trigger"
    val HiddenInputSelector = ".fr-process-action-hidden-input"

    def syncHiddenInputs(): Unit =
      dom.document.querySelectorAllT(s"$HiddenInputSelector input").foreach { inputEl =>
        inputEl.tabIndex = -1
      }

    def openForField(fieldEl: html.Element, triggerBtn: html.Element): Unit =
      for {
        editorEl <- fieldEl.closestOpt(EditorSelector)
        catRoot  <- editorEl.querySelectorOpt(CatalogSelector)
      } {
        val placeholder  = catRoot.attValueOpt("data-placeholder").filter(_.nonAllBlank).getOrElse("Search actions…")
        val emptyMessage = catRoot.attValueOpt("data-empty-message").filter(_.nonAllBlank).getOrElse("No matching actions")
        val allLabel     = catRoot.attValueOpt("data-all-label").filter(_.nonAllBlank).getOrElse("All")

        val categories =
          catRoot.querySelectorAllT("[data-cat-id]").iterator.map { el =>
            val id    = el.attValueOpt("data-cat-id").getOrElse("")
            val full  = el.attValueOpt("data-cat-label").filter(_.nonAllBlank).getOrElse(id)
            val short = el.attValueOpt("data-cat-short").filter(_.nonAllBlank).getOrElse(full)
            PopoverSelectPicker.Category(id = id, shortLabel = short, fullLabel = full)
          }.filter(_.id.nonEmpty).toList

        val catLabelById = categories.map(c => c.id -> c.fullLabel).toMap

        val items =
          catRoot.querySelectorAllT("[data-action-name]").iterator.map { el =>
            val name     = el.attValueOpt("data-action-name").getOrElse("")
            val label    = el.attValueOpt("data-action-label").filter(_.nonAllBlank).getOrElse(name)
            val cat      = el.attValueOpt("data-action-category").getOrElse("")
            val keywords = el.attValueOpt("data-action-keywords").getOrElse("")
            PopoverSelectPicker.Item(
              value         = name,
              label         = label,
              badgeOpt      = Some(name),
              categoryId    = cat,
              categoryLabel = catLabelById.getOrElse(cat, cat),
              extraKeywords = keywords
            )
          }.filter(_.value.nonEmpty).toList

        val hostContainer =
          fieldEl.closestOpt("dialog.xforms-dialog, .xforms-dialog").getOrElse(editorEl)

        val currentAction = fieldEl.attValueOpt("data-orbeon-action-name").getOrElse("").trim

        PopoverSelectPicker.open(
          PopoverSelectPicker.Config(
            triggerBtn        = triggerBtn,
            hostContainer     = hostContainer,
            currentValue      = currentAction,
            searchPlaceholder = placeholder,
            emptyMessage      = emptyMessage,
            allLabel          = allLabel,
            categories        = categories,
            items             = items,
            minWidthPx        = 380.0,
            onSelect          = chosen => commitSelection(fieldEl, triggerBtn, chosen)
          )
        )
      }

    def commitSelection(fieldEl: html.Element, triggerBtn: html.Element, item: PopoverSelectPicker.Item): Unit = {
      fieldEl.setAttribute("data-orbeon-action-name", item.value)
      fieldEl.setAttribute("data-orbeon-has-def", "true")
      triggerBtn.querySelectorOpt(".fr-process-action-trigger-label output, .fr-process-action-trigger-label .xforms-output-output")
        .foreach(_.textContent = item.label)
      triggerBtn.querySelectorOpt(".fr-process-action-trigger-token output, .fr-process-action-trigger-token .xforms-output-output")
        .foreach(_.textContent = item.value)

      fieldEl.querySelectorOpt(HiddenInputSelector).foreach { hiddenControlEl =>
        if (hiddenControlEl.id.nonEmpty)
          DocumentAPI.setValue(hiddenControlEl.id, item.value)
      }
    }
  }
}
