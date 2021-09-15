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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.xforms._
import org.orbeon.xforms.XFormsNames._

import scala.collection.compat._
import scala.collection.mutable


trait PartEventHandlerScripts {

  import PartEventHandlerScripts._

  def controlTypes: mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]]

  private[PartEventHandlerScripts] var _scriptsByPrefixedId: Map[String, StaticScript] = Map.empty
  def scriptsByPrefixedId: Map[String, StaticScript] = _scriptsByPrefixedId
  private[PartEventHandlerScripts] var _uniqueJsScripts: List[ShareableScript] = Nil
  def uniqueJsScripts: List[ShareableScript] = _uniqueJsScripts

  def gatherScripts(): Unit = {

    // Used to eliminate duplicates: we store a single copy of each ShareableScript per digest
    val shareableByDigest = mutable.LinkedHashMap[String, ShareableScript]()

    def extractStaticScript(analysis: ElementAnalysis, scriptType: ScriptType) = {

      val elem     = analysis.element
      val isClient = elem.attributeValue("runat") != "server"

      if (! isClient)
        throw new NotImplementedError(s"""`runat="server"` is not supported""")

      val params =
        elem.elements(XFORMS_PARAM_QNAME) map (p => p.attributeValue("name") -> p.attributeValue("value"))

      val body =
        if (params.nonEmpty)
          elem.elements(XFORMS_BODY_QNAME).headOption map (_.getStringValue) getOrElse ""
        else
          elem.getStringValue

      StaticScriptBuilder(
        prefixedId        = analysis.prefixedId,
        scriptType        = scriptType,
        body              = body,
        params            = params.to(List),
        shareableByDigest = shareableByDigest
      )
    }

    def elemHasScriptType(e: ElementAnalysis, scriptType: ScriptType, default: Option[ScriptType]) =
      StaticScriptBuilder.scriptTypeFromElem(e, default) contains scriptType

    def findForActionIt(action: String, scriptType: ScriptType, default: Option[ScriptType]) =
      controlTypes.get(action).iterator.flatMap(_.values).filter(elemHasScriptType(_, scriptType, default))

    def findForScriptTypeIt(scriptType: ScriptType) =
      findForActionIt(ActionActionName,  scriptType, None) ++
      findForActionIt(HandlerActionName, scriptType, None) ++
      findForActionIt(ScriptActionName,  scriptType, Some(ScriptType.JavaScript)) map
      (extractStaticScript(_, scriptType))

    val jsScripts      = findForScriptTypeIt(ScriptType.JavaScript).toList
    val xpathScriptsIt = findForScriptTypeIt(ScriptType.XPath)

    _scriptsByPrefixedId ++=
      jsScripts.iterator ++
      xpathScriptsIt     map
      (script => script.prefixedId -> script)

    // Keep only one script body for a given digest
    _uniqueJsScripts ++= jsScripts.keepDistinctBy(_.shared.digest) map (_.shared)
  }

  def deregisterScript(eventHandler: EventHandler): Unit = {

    if (ActionNames(eventHandler.localName))
      _scriptsByPrefixedId -= eventHandler.prefixedId

    // NOTE: Can't update eventNames and _uniqueClientScripts without checking all handlers again, so for now leave that untouched
  }
}

private object PartEventHandlerScripts {

  val ActionActionName  = "action"
  val ScriptActionName  = "script"
  val HandlerActionName = "handler"

  val ActionNames = Set(ActionActionName, ScriptActionName, HandlerActionName)
}