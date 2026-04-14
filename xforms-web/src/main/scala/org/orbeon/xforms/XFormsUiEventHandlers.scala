package org.orbeon.xforms

import org.orbeon.xforms.facade.{Controls, Events}
import org.scalajs.dom.UIEvent


object XFormsUiEventHandlers {

  def input(event: UIEvent): Unit = {

    if (XFormsUI.modalProgressPanelShown) {
      event.preventDefault()
      return
    }

    Option(Events._findParentXFormsControl(event.target)).foreach { target =>

      XFormsUI.fieldValueChanged(target)

      // Incremental control: treat keypress as a value change event
      if (target.classList.contains("xforms-incremental"))
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName   = EventNames.XXFormsValue,
            targetId    = target.id,
            properties  = Map("value" -> Controls.getCurrentValue(target)),
            incremental = true,
          )
        )
    }
  }
}
