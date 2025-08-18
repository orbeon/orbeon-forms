package org.orbeon.xforms

import io.udash.wrappers.jquery.JQueryPromise
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.facade.{Controls, XBL}
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.|


object XFormsControls {

  def isReadonly(control: html.Element): Boolean =
    control.classList.contains("xforms-readonly")

  def setCurrentValue(
    control        : html.Element,
    newControlValue: String,
    force          : js.UndefOr[Boolean]
  ): js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = {

    // 2025-08-18: Legacy and undocumented
    val customEvent: js.Object = {
      val _control = control
      new js.Object {
        val control : html.Element = _control
        val newValue: String       = newControlValue
      }
    }

    Controls.beforeValueChange.fire(customEvent)
    Controls.valueChange.fire(customEvent)

    val isStaticReadonly = control.classList.contains("xforms-static")

    // Can be set below by an XBL component's `xformsUpdateValue()` result
    var result: js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = js.undefined

    if (control.classList.contains("xforms-output-appearance-xxforms-download")) {
      // XForms output with `xxf:download` appearance
      val anchor = control.getElementsByTagName("a").head
      if (newControlValue == "") {
          anchor.setAttribute("href", "#")
          anchor.classList.add("xforms-readonly")
      } else {
          anchor.setAttribute("href", newControlValue)
          anchor.classList.remove("xforms-readonly")
      }
    } else if (isStaticReadonly && control.classList.contains("xforms-textarea")) {
      // textarea in "static readonly" mode
      control.childrenT("pre").head.innerText = newControlValue
    } else if (isStaticReadonly && control.classList.contains("xforms-select1-appearance-full")) {
      // Radio buttons in "static readonly" mode
      control.querySelectorAllT(".xforms-selected, .xforms-deselected").foreach { item =>
        val selected = item.querySelector(".radio > span").innerText == newControlValue
        if (selected) {
          item.classList.add("xforms-selected")
          item.classList.remove("xforms-deselected")
        } else {
          item.classList.remove("xforms-selected")
          item.classList.add("xforms-deselected")
        }
      }
    } else if (control.matches(".xforms-label, .xforms-hint, .xforms-help, .xforms-alert")) {
        // External LHH
        if (control.classList.contains("xforms-mediatype-text-html"))
          control.innerHTML = newControlValue
        else
          control.innerText = newControlValue
    } else if (control.classList.contains("xforms-output") || isStaticReadonly) {
      // XForms output or other field in "static readonly" mode
      control.childrenT(".xforms-output-output, .xforms-field").headOption.foreach {
        case img: html.Image if control.classList.contains("xforms-mediatype-image") =>
          img.src = newControlValue
        case video: html.Video if control.classList.contains("xforms-mediatype-video") =>
          video.queryNestedElems[html.Source]("source", includeSelf = false).headOption.foreach { source =>
            source.src = newControlValue
            if (newControlValue == "")
              video.classList.add("empty-source")
            else
              video.classList.remove("empty-source")
            video.load()
          }
        case elem if control.classList.contains("xforms-mediatype-text-html") =>
          elem.innerHTML = newControlValue
        case elem =>
          elem.innerText = newControlValue
      }
    } else if (! force.contains(true) && AjaxFieldChangeTracker.hasChangedIdsRequest(control)) {
      // User has modified the value of this control since we sent our request so don't try to update it
      // 2017-03-29: Added `force` attribute to handle https://github.com/orbeon/orbeon-forms/issues/3130 as we
      // weren't sure we wanted to fully disable the test on `changedIdsRequest`.
    } else if (control.matches(".xforms-trigger, .xforms-submit, .xforms-upload")) {
        // No value
    } else if ((control.classList.contains("xforms-input") && ! control.classList.contains("xforms-type-boolean")) || control.classList.contains("xforms-secret")) {
      // Regular XForms input (not boolean, date, time or dateTime) or secret
      control
        .queryNestedElems[html.Input]("input", includeSelf = true)
        .headOption
        .filter(_.value != newControlValue) // needed? side effects?
        .foreach(_.value = newControlValue)
    } else if (control.matches(".xforms-select-appearance-full, .xforms-select1-appearance-full, .xforms-input.xforms-type-boolean")) {
      // Handle checkboxes and radio buttons
      val selectedValues =
        if (control.matches(".xforms-select-appearance-full"))
          newControlValue.splitTo[Set]()
        else
          Set(newControlValue)

      control
        .queryNestedElems[html.Input]("input[type = 'checkbox'], input[type = 'radio']", includeSelf = false)
        .foreach { input =>
          input.checked = selectedValues(input.value)
          XFormsUI.setOneRadioCheckboxClasses(input)
        }
    } else if (control.matches(".xforms-select-appearance-compact, .xforms-select1-appearance-compact, .xforms-select1-appearance-minimal")) {
      // Handle lists and comboboxes
      val selectedValues =
        if (control.matches(".xforms-select-appearance-compact"))
          newControlValue.splitTo[Set]()
        else
          Set(newControlValue)

      control
        .queryNestedElems[html.Select]("select", includeSelf = false)
        .head
        .options.foreach { option =>
        option.selected = selectedValues(option.value)
      }
    } else if (control.classList.contains("xforms-textarea")) {
      // Text area
      control
        .queryNestedElems[html.TextArea]("textarea", includeSelf = false)
        .head
        .value = newControlValue
    } else if (XFormsXbl.isComponent(control)) {
      val companionInstance = XBL.instanceForControl(control)
      if (XFormsXbl.isObjectWithMethod(companionInstance, "xformsUpdateValue"))
        result = companionInstance.xformsUpdateValue(newControlValue)
    }

    // 2025-08-18: Legacy and undocumented
    Controls.afterValueChange.fire(customEvent)

    result
  }
}
