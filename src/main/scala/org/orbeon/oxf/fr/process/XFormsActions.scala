/**
 *  Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunner.{recombineQuery ⇒ _, splitQueryDecodeParams ⇒ _, _}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

import scala.util.Try

trait XFormsActions {

    self: ProcessInterpreter ⇒

    def AllowedXFormsActions = Map[String, Action](
        "xf:send"     → tryXFormsSend,
        "xf:dispatch" → tryXFormsDispatch,
        "xf:show"     → tryShowDialog,
        "xf:setvalue" → trySetvalue
    )

    def tryXFormsSend(params: ActionParams): Try[Any] =
        Try {
            val submission = paramByNameOrDefault(params, "submission")
            submission foreach (sendThrowOnError(_))
        }

    private val StandardDispatchParams = Set("name", "targetid")
    private val StandardShowParams     = Set("dialog")

    private def collectCustomProperties(params: ActionParams, standardParamNames: Set[String]) = params.collect {
        case (Some(name), value) if ! standardParamNames(name) ⇒ name → Option(evaluateValueTemplate(value))
    }

    def tryXFormsDispatch(params: ActionParams): Try[Any] =
        Try {
            val eventName     = paramByNameOrDefault(params, "name")
            val eventTargetId = paramByName(params, "targetid") getOrElse FormModel

            eventName foreach (dispatch(_, eventTargetId, properties = collectCustomProperties(params, StandardDispatchParams)))
        }

    def tryShowDialog(params: ActionParams): Try[Any] =
        Try {
            val dialogName = paramByNameOrDefault(params, "dialog")

            dialogName foreach (show(_, properties = collectCustomProperties(params, StandardShowParams)))
        }

    def trySetvalue(params: ActionParams): Try[Any] =
        Try {
            evaluateOne(requiredParamByName(params, "setvalue", "ref")) match {
                case nodeInfo: NodeInfo ⇒
                    val valueToSet = params.get(Some("value")) match {
                        case Some(valueExpr) ⇒
                            evaluateString(
                                item = nodeInfo,
                                expr = valueExpr
                            )
                        case None            ⇒ ""
                    }
                    setvalue(nodeInfo, valueToSet)
                case _ ⇒
                    debug("setvalue: `ref` parameter did not return a node, ignoring")
            }
        }
}
