/**
 * Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.dom.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.{XFormsContextStack, XFormsContextStackSupport}
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.analysis.controls.WithExpressionOrConstantTrait
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.XFormsNames.xxformsQName


/**
 * 10.12 The message Element
 */
private object XFormsMessageAction {

  val ModalQName     = QName("modal")
  val ModelessQName  = QName("modeless")
  val EphemeralQName = QName("ephemeral")

  val LogDebugQName  = xxformsQName("log-debug")
  val LogInfoQName   = xxformsQName("log-info")
  val LogWarnQName   = xxformsQName("log-warn")
  val LogErrorQName  = xxformsQName("log-error")

  val LogPrefix = "xf:message"

  // TODO: consider using `Logger`
  val ExtensionLevels = Map[QName, (IndentedLogger, String) => Unit](
    LogDebugQName -> (_.logDebug  (LogPrefix, _)),
    LogInfoQName  -> (_.logInfo   (LogPrefix, _)),
    LogWarnQName  -> (_.logWarning(LogPrefix, _)),
    LogErrorQName -> (_.logError  (LogPrefix, _)),
  )

  val StandardLevels = Set[QName](
    ModalQName,
    ModelessQName,
    EphemeralQName
  )

  val debugList =
    (StandardLevels.iterator ++ ExtensionLevels.keysIterator).map(_.qualifiedName).mkString("`", "`|`", "`")
}

class XFormsMessageAction extends XFormsAction {

  import XFormsMessageAction._

  // We handle the binding ourselves
  override val pushBinding: Boolean = false

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {


    val containingDocument = actionContext.containingDocument

    val levelQName =
      actionContext.element.attributeValueOpt(XFormsNames.LEVEL_QNAME) flatMap { levelQName =>
        Extensions.resolveQName(
          actionContext.analysis.namespaceMapping.mapping.get,
          levelQName,
          unprefixedIsNoNamespace = true
        )
      } getOrElse
        ModalQName // "The default is `modal` if the attribute is not specified."

    implicit val contextStack: XFormsContextStack = actionContext.interpreter.actionXPathContext

    val messageValue =
      XFormsContextStackSupport.evaluateExpressionOrConstant(
        childElem           = actionContext.analysis.asInstanceOf[WithExpressionOrConstantTrait],
        parentEffectiveId   = actionContext.interpreter.getSourceEffectiveId(actionContext.analysis),
        pushContextAndModel = false, // `XFormsActionInterpreter` already handles that
        eventTarget         = actionContext.interpreter.eventObserver,
        collector           = actionContext.collector
      )
      .getOrElse("")

    ExtensionLevels.get(levelQName) match {
      case Some(fn) =>
        fn(containingDocument.indentedLogger, messageValue)
      case None =>
        if (StandardLevels(levelQName))
          containingDocument.addMessageToRun(messageValue, levelQName.clarkName)
        else
          throw new OXFException(s"`xf:message` attribute `level` value `${levelQName.qualifiedName}` must be one of: $debugList.")
    }
  }
}