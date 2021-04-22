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
package org.orbeon.oxf.xforms.action

import org.orbeon.datatypes.LocationData
import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.{IndentedLogger, Logging, XPathCache}
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om.Item
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope

import scala.collection.JavaConverters._

abstract class XFormsAction extends Logging {

  // Execute the action with the given context
  // By default, run the legacy execute()
  def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit =
    execute(
      actionContext.interpreter,
      actionContext.analysis.element,
      actionContext.analysis.scope,
      actionContext.overriddenContext.isDefined,
      actionContext.overriddenContext.orNull
    )

  // Legacy execute()
  def execute(
     actionInterpreter: XFormsActionInterpreter,
     actionElement: Element,
     actionScope: Scope,
     hasOverriddenContext: Boolean,
     overriddenContext: Item): Unit = ()

  // Resolve a control given the name of an AVT
  def resolveControlAvt(attName: String, required: Boolean = true)(implicit context: DynamicActionContext): Option[XFormsControl] = {

    val interpreter = context.interpreter
    val element = context.element

    import interpreter.indentedLogger

    resolveStringAVT(attName)(context) match {
      case Some(resolvedAvt) =>

        resolveControl(resolvedAvt) match {
          case Some(control) => Some(control)
          case _ =>
            debug(
              "attribute does not refer to an existing control",
              List(
                "attribute"      -> attName,
                "value"          -> element.attributeValue("control"),
                "resolved value" -> resolvedAvt
              )
            )
            None
        }

      case None if required =>
        // This can happen if the attribute is missing or if the AVT cannot be evaluated due to an empty context
        throw new OXFException("Cannot resolve mandatory '" + attName + "' attribute on " + context.actionName + " action.")
      case None if ! required =>
        None
    }
  }

  def resolveControl(controlId: String)(implicit context: DynamicActionContext): Option[XFormsControl] =
    collectByErasedType[XFormsControl](context.interpreter.resolveObject(context.element, controlId))

  // Resolve an optional boolean AVT
  // Return None if there is no attribute or if the AVT cannot be evaluated
  def resolveStringAVT(att: String)(implicit context: DynamicActionContext): Option[String] =
    context.element.attributeValueOpt(att) flatMap
      (avt => Option(context.interpreter.resolveAVTProvideValue(context.element, avt)))

  // Resolve an optional boolean AVT
  def resolveBooleanAVT(att: String, default: Boolean)(implicit context: DynamicActionContext): Boolean =
    resolveStringAVT(att)(context) map (_ == "true") getOrElse default

  def synchronizeAndRefreshIfNeeded(context: DynamicActionContext): Unit =
    if (context.interpreter.mustHonorDeferredUpdateFlags(context.element))
      context.containingDocument.synchronizeAndRefresh()
}

object XFormsAction {

  // Obtain context attributes based on nested xf:property elements.
  def eventProperties(actionInterpreter: XFormsActionInterpreter, actionElement: Element): Map[String, Option[AnyRef]] = {

    val contextStack = actionInterpreter.actionXPathContext

    // Iterate over context information if any
    val tuples =
      for {
        element <- actionElement.elements
        if Set(XFORMS_PROPERTY_QNAME, XXFORMS_CONTEXT_QNAME)(element.getQName) // `xf:property` since XForms 2.0

        // Get and check attributes
        name =
         element.resolveAttValueQName(NAME_QNAME, unprefixedIsNoNamespace = true) map (_.clarkName) getOrElse
            (throw new OXFException(XXFORMS_CONTEXT_QNAME.qualifiedName + " element must have a \"name\" attribute."))

        value = VariableAnalysis.valueOrSelectAttribute(element) match {
          case Some(valueExpr) =>
            // XPath expression

            // Set context on context element
            val currentActionScope = actionInterpreter.getActionScope(element)
            contextStack.pushBinding(
              bindingElement    = element,
              sourceEffectiveId = actionInterpreter.getSourceEffectiveId(element),
              scope             = currentActionScope,
              handleNonFatal    = false
            )

            // Evaluate context parameter
            val result =
              XPathCache.normalizeSingletons(
                XPathCache.evaluate(
                  contextItems       = actionInterpreter.actionXPathContext.getCurrentBindingContext.nodeset,
                  contextPosition    = actionInterpreter.actionXPathContext.getCurrentBindingContext.position,
                  xpathString        = valueExpr,
                  namespaceMapping   = actionInterpreter.getNamespaceMappings(element),
                  variableToValueMap = contextStack.getCurrentBindingContext.getInScopeVariables,
                  functionLibrary    = actionInterpreter.containingDocument.functionLibrary,
                  functionContext    = contextStack.getFunctionContext(actionInterpreter.getSourceEffectiveId(element)),
                  baseURI            = null,
                  locationData       = element.getData.asInstanceOf[LocationData],
                  reporter           = actionInterpreter.containingDocument.getRequestStats.addXPathStat
                ).asScala
              )

            contextStack.popBinding()

            result
          case None =>
            // Literal text
            element.getStringValue
        }
      } yield
        (name, Option(value))

    tuples.toMap
  }
}