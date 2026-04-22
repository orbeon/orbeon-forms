package org.orbeon.xforms

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.{Controls, Events}
import org.scalajs.dom.{UIEvent, html}


object XFormsUiEventHandlers {

  def input(event: UIEvent): Unit = {

    if (XFormsUI.modalProgressPanelShown) {
      event.preventDefault()
      return
    }

    Option(Events._findParentXFormsControl(event.target)).foreach { target =>

      XFormsUI.fieldValueChanged(target)

      // Incremental control: treat keypress as a value change event
      if (target.hasClass("xforms-incremental"))
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName   = EventNames.XXFormsValue,
            targetId    = target.id,
            properties  = Map("value" -> Controls.getCurrentValue(target)), // Q: What if `getCurrentValue` is undefined?
            incremental = true
          )
        )
    }
  }

  def change(event: UIEvent): Unit =
    Option(Events._findParentXFormsControl(event.target)).foreach { target =>
      if (target.hasAllClasses("xbl-component", "xbl-javascript-lifecycle")) {
        // We might exclude *all* changes under `.xbl-component` but for now, to be conservative, we
        // exclude those that support the JavaScript lifecycle.
        // https://github.com/orbeon/orbeon-forms/issues/4169
      } else if (target.hasClass("xforms-upload")) {
        // Dispatch change event to upload control
        Page.getUploadControl(target).change()
      } else {

        if (target.hasClass("xforms-select1-appearance-compact")) {
          // For select1 list, make sure we have exactly one value selected
          val select = target.queryNestedElems[html.Select]("select").head
          if (select.value == "") {
            // Stop end-user from deselecting last selected value
            select.options.head.selected = true
          } else {
            // Deselect options other than the first one
            var foundSelected = false
            for (option <- select.options)
              if (option.selected) {
                if (foundSelected)
                  option.selected = false
                else
                  foundSelected = true
              }
          }
        }

        if (! target.hasClass("xforms-static") && (
            target.hasAnyClass("xforms-select1-appearance-full", "xforms-select-appearance-full") ||
            target.hasAllClasses("xforms-input", "xforms-type-boolean")
          )) {
          // Update classes right away to give user visual feedback
          XFormsUI.setRadioCheckboxClasses(target)
        }

        // Fire change event if the control has a value
        Controls.getCurrentValue(target).foreach { controlCurrentValue =>
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName  = EventNames.XXFormsValue,
              targetId   = target.id,
              properties = Map("value" -> controlCurrentValue)
            )
          )
        }
      }
    }
}
