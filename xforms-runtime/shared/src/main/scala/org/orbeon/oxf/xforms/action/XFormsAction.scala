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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.{IndentedLogger, Logging, XPathCache}
import org.orbeon.oxf.xforms.analysis.EventHandler.PropertyQNames
import org.orbeon.oxf.xforms.analysis.WithChildrenTrait
import org.orbeon.oxf.xforms.analysis.controls.{ActionTrait, PropertyControl, VariableAnalysis}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent.{ActionPropertyGetter, PropertyValue}
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.xforms.XFormsNames.*
import shapeless.syntax.typeable.typeableOps

import scala.jdk.CollectionConverters.*


abstract class XFormsAction extends Logging {

  val pushBinding: Boolean = true

  // Execute the action with the given context
  def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit

  // Resolve a control given the name of an AVT
  def resolveControlAvt(attName: String, required: Boolean = true)(implicit context: DynamicActionContext): Option[XFormsControl] = {

    val interpreter = context.interpreter

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
                "value"          -> context.analysis.attributeValue("control"),
                "resolved value" -> resolvedAvt
              )
            )
            None
        }

      case None =>
        if (required)
          // This can happen if the attribute is missing or if the AVT cannot be evaluated due to an empty context
          throw new OXFException("Cannot resolve mandatory '" + attName + "' attribute on " + context.actionName + " action.")
        else
          None
    }
  }

  def resolveControl(controlId: String)(implicit context: DynamicActionContext): Option[XFormsControl] =
    context.interpreter.resolveObject(context.analysis, controlId).cast[XFormsControl]

  // Resolve an optional boolean AVT
  // Return `None` if there is no attribute or if the AVT cannot be evaluated
  def resolveStringAVT(att: String)(implicit context: DynamicActionContext): Option[String] =
    context.analysis.attributeValueOpt(att) flatMap
      (avt => Option(context.interpreter.resolveAVTProvideValue(context.analysis, avt)))

  // Resolve an optional boolean AVT
  def resolveBooleanAVT(att: String, default: Boolean)(implicit context: DynamicActionContext): Boolean =
    resolveStringAVT(att)(context) map (_ == "true") getOrElse default

  def synchronizeAndRefreshIfNeeded(context: DynamicActionContext): Unit =
    if (context.interpreter.mustHonorDeferredUpdateFlags(context.analysis))
      context.containingDocument.synchronizeAndRefresh()
}

object XFormsAction {

  // Obtain context attributes based on nested `xf:property` elements.
  def eventProperties(
    actionInterpreter : XFormsActionInterpreter,
    actionAnalysis    : ActionTrait,
    eventTarget       : XFormsEventTarget,
    collector         : ErrorEventCollector
  ): ActionPropertyGetter = {

    val contextStack = actionInterpreter.actionXPathContext

    val properties =
      actionAnalysis.narrowTo[ActionTrait & WithChildrenTrait].toList flatMap
        (_.children) collect {
          case e if PropertyQNames(e.qName) => e
        }

    // Iterate over context information if any
    val propertyValues =
      for {
        property <- properties

        // Get and check attributes
        name =
          resolveQName(
           property.namespaceMapping.mapping.get,
           property.attributeValue(NAME_QNAME),
           unprefixedIsNoNamespace = true
        ) map (_.clarkName) getOrElse
          (throw new OXFException(XXFORMS_CONTEXT_QNAME.qualifiedName + " element must have a \"name\" attribute."))

        value = VariableAnalysis.valueOrSelectAttribute(property) match {
          case Some(valueExpr) =>
            // XPath expression

            // Set context on context element
            contextStack.pushBinding(
              bindingAnalysis   = property,
              sourceEffectiveId = actionInterpreter.getSourceEffectiveId(property),
              scope             = property.scope,
              eventTarget       = eventTarget,
              collector         = collector
            )

            // Evaluate context parameter
            val result =
              XPathCache.normalizeSingletons(
                XPathCache.evaluate(
                  contextItems       = actionInterpreter.actionXPathContext.getCurrentBindingContext.nodeset,
                  contextPosition    = actionInterpreter.actionXPathContext.getCurrentBindingContext.position,
                  xpathString        = valueExpr,
                  namespaceMapping   = property.namespaceMapping,
                  variableToValueMap = contextStack.getCurrentBindingContext.getInScopeVariables,
                  functionLibrary    = actionInterpreter.container.partAnalysis.functionLibrary(property.isFunctionsRestricted),
                  functionContext    = contextStack.getFunctionContext(actionInterpreter.getSourceEffectiveId(property)),
                  baseURI            = null,
                  locationData       = property.locationData,
                  reporter           = actionInterpreter.containingDocument.getRequestStats.addXPathStat
                ).asScala
              )

            contextStack.popBinding()

            result
          case None =>
            // Literal text
            property match {
              case p: PropertyControl => p.constantValueOpt.getOrElse("")
              case _                  => ""
            }
        }

        tunnel = property.attributeValueOpt("tunnel").contains("true")
      } yield
        PropertyValue(name, Option(value), tunnel)

    ActionPropertyGetter(propertyValues)
  }
}