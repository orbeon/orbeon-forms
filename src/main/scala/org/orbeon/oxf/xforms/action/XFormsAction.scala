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

import org.dom4j.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.VariableAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData}
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xml.Dom4j
import collection.JavaConverters._

abstract class XFormsAction extends Logging {

    // Execute the action with the given context
    // By default, run the legacy execute()
    def execute(actionContext: DynamicActionContext): Unit =
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
    def resolveControl(attName: String, required: Boolean = true)(implicit context: DynamicActionContext): Option[XFormsControl] = {

        val interpreter = context.interpreter
        val element = context.element

        resolveStringAVT(attName)(context) match {
            case Some(resolvedAvt) ⇒

                val controlObject = interpreter.resolveObject(element, resolvedAvt)

                Option(controlObject) match {
                    case Some(control: XFormsControl) ⇒ Some(control)
                    case _ ⇒
                        implicit val indentedLogger = interpreter.indentedLogger
                        debug(
                            "attribute does not refer to an existing control",
                            Seq("attribute"      → attName,
                                "value"          → element.attributeValue("control"),
                                "resolved value" → resolvedAvt)
                        )
                        None
                }

            case None if required ⇒
                // This can happen if the attribute is missing or if the AVT cannot be evaluated due to an empty context
                throw new OXFException("Cannot resolve mandatory '" + attName + "' attribute on " + context.actionName + " action.")
            case None if ! required ⇒
                None
        }
    }

    // Resolve an optional boolean AVT
    // Return None if there is no attribute or if the AVT cannot be evaluated
    def resolveStringAVT(att: String)(implicit context: DynamicActionContext) =
        Option(context.element.attributeValue(att)) flatMap
            (avt ⇒ Option(context.interpreter.resolveAVTProvideValue(context.element, avt)))

    // Resolve an optional boolean AVT
    def resolveBooleanAVT(att: String, default: Boolean)(implicit context: DynamicActionContext) =
        resolveStringAVT(att)(context) map (_ == "true") getOrElse default

    def synchronizeAndRefreshIfNeeded(context: DynamicActionContext): Unit =
        if (context.interpreter.isDeferredUpdates(context.element))
            context.containingDocument.synchronizeAndRefresh()
}

object XFormsAction {
    /**
     * Obtain context attributes based on nested xxf:context elements.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param actionElement     action element
     */
    def eventProperties(actionInterpreter: XFormsActionInterpreter, actionElement: Element): PropertyGetter = {

        val contextStack = actionInterpreter.actionXPathContext

        // Iterate over context information if any
        val tuples =
            for {
                element ← Dom4j.elements(actionElement)
                if Set(XFORMS_PROPERTY_QNAME, XXFORMS_CONTEXT_QNAME)(element.getQName) // xf:property since XForms 2.0

                // Get and check attributes
                name =
                    Option(Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractAttributeValueQName(element, NAME_QNAME))) getOrElse
                        (throw new OXFException(XXFORMS_CONTEXT_QNAME.getQualifiedName + " element must have a \"name\" attribute."))

                value = VariableAnalysis.valueOrSelectAttribute(element) match {
                    case valueOrSelect: String ⇒
                        // XPath expression

                        // Set context on context element
                        val currentActionScope = actionInterpreter.getActionScope(element)
                        contextStack.pushBinding(element, actionInterpreter.getSourceEffectiveId(element), currentActionScope, false)

                        // Evaluate context parameter
                        val result = XPathCache.normalizeSingletons(XPathCache.evaluate(
                            actionInterpreter.actionXPathContext.getCurrentBindingContext.nodeset,
                            actionInterpreter.actionXPathContext.getCurrentBindingContext.position,
                            valueOrSelect,
                            actionInterpreter.getNamespaceMappings(element),
                            contextStack.getCurrentBindingContext.getInScopeVariables,
                            XFormsContainingDocument.getFunctionLibrary,
                            contextStack.getFunctionContext(actionInterpreter.getSourceEffectiveId(element)),
                            null,
                            element.getData.asInstanceOf[LocationData],
                            actionInterpreter.containingDocument().getRequestStats.addXPathStat).asScala)

                        contextStack.popBinding()

                        result
                    case _ ⇒
                        // Literal text
                        element.getStringValue
                }
            } yield
                (name, Option(value))

        tuples.toMap
    }
}