/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction, XFormsActionInterpreter}
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.control.{Focus, XFormsControl}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XXFormsSetindexEvent


/**
 * 9.3.7 The setindex Element
 */
class XFormsSetindexAction extends XFormsAction {

  override def execute(context: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val interpreter = context.interpreter
    val element = context.analysis.element

    val repeatStaticId =
      resolveStringAVT("repeat")(context) getOrElse
        (throw new OXFException(s"Cannot resolve mandatory 'repeat' attribute on ${context.actionName} action."))

    val indexOpt = {

      val indexXPath = element.attributeValue("index")
      val contextStack = interpreter.actionXPathContext

      val indexString = interpreter.evaluateAsString(
        element,
        contextStack.getCurrentBindingContext.nodeset,
        contextStack.getCurrentBindingContext.position,
        "number(" + indexXPath + ")"
      )

      indexString.toIntOpt // "If the index evaluates to NaN the action has no effect."
    }

    indexOpt foreach
      (XFormsSetindexAction.executeSetindexAction(interpreter, element, repeatStaticId, _))
  }
}

object XFormsSetindexAction {

  def executeSetindexAction(
    interpreter    : XFormsActionInterpreter,
    actionElement  : Element,
    repeatStaticId : String,
    index          : Int)(implicit
    logger         : IndentedLogger
  ): Int = {

    // "This XForms Action begins by invoking the deferred update behavior."
    // See also `synchronizeAndRefreshIfNeeded`
    if (interpreter.mustHonorDeferredUpdateFlags(actionElement))
      interpreter.containingDocument.synchronizeAndRefresh()

    // Find repeat control
    interpreter.resolveObject(actionElement, repeatStaticId) match {
      case control: XFormsControl =>

        val repeatControl = Some(control) collect { case repeat: XFormsRepeatControl => repeat }

        debug(
          "xf:setindex: setting index upon xf:setindex",
          ("new index" -> index.toString) ::
          (repeatControl.toList map (c => "old index" -> c.getIndex.toString))
        )

        val focusedBeforeOpt = interpreter.containingDocument.controls.getFocusedControl

        // Dispatch to any control so that other custom controls can implement the notion of "setindex"
        Dispatch.dispatchEvent(new XXFormsSetindexEvent(control, index))

        // Handle focus changes
        Focus.updateFocusWithEvents(focusedBeforeOpt)(interpreter.containingDocument)

        // However at this time return the index only for repeat controls as we don't have a generic way to
        // figure this out yet
        repeatControl map (_.getIndex) getOrElse -1

      case _ =>
        // "If there is a null search result for the target object and the source object is an XForms action
        // such as dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
        debug(
          "xf:setindex: index does not refer to an existing xf:repeat element, ignoring action",
          List("repeat id" -> repeatStaticId)
        )
        -1
    }
  }
}
