/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames

import scala.util.{Failure, Success, Try}

/**
 * 10.1.9 The setvalue Element
 */
class XFormsSetvalueAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val actionInterpreter = actionContext.interpreter
    val actionElement     = actionContext.element
    val contextStack      = actionInterpreter.actionXPathContext

    implicit val containingDocument: XFormsContainingDocument = actionInterpreter.containingDocument

    val valueExpression = Option(actionElement.attributeValue(XFormsNames.VALUE_QNAME))

    // Determine value to set
    def evaluateValueToSet =
      valueExpression match {
        case Some(valueExpression) =>
          // Value to set is computed with an XPath expression
          actionInterpreter.evaluateAsString(
            actionContext.analysis,
            actionElement,
            contextStack.getCurrentBindingContext.nodeset,
            contextStack.getCurrentBindingContext.position,
            valueExpression
          )
        case None =>
          // Value to set is static content
          actionElement.getStringValue
      }

    // Set the value on target node if possible
    contextStack.getCurrentBindingContext.getSingleItemOrNull match {
      case node: om.NodeInfo =>
        // NOTE: XForms 1.1 seems to require dispatching xforms-binding-exception in case the target node cannot
        // be written to. But because of the way we now handle errors in actions, we throw an exception instead
        // and action processing is interrupted.

        val valueToSet = evaluateValueToSet

        DataModel.setValueIfChanged(
          nodeInfo   = node,
          newValue   = valueToSet,
          onSuccess  = oldValue => DataModel.logAndNotifyValueChange(
            source             = "setvalue",
            nodeInfo           = node,
            oldValue           = oldValue,
            newValue           = valueToSet,
            isCalculate        = false,
            collector          = (event: XFormsEvent) => Dispatch.dispatchEvent(event, actionContext.collector)
          ),
          reason => throw new OXFException(reason.message)
        )
      case _ =>
        // Node doesn't exist: NOP
        debug(
          "xf:setvalue: not setting instance value",
          List(
            "reason" -> "destination node not found",
            Try(evaluateValueToSet) match {
              case Success(v) => "value" -> v
              case Failure(t) => "value error" -> t.getMessage
            }
          )
        )
    }
  }
}
