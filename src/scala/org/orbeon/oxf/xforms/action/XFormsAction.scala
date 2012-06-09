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
import org.orbeon.oxf.xforms.analysis.VariableAnalysis
import org.orbeon.saxon.value.{StringValue, SequenceExtent}
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xml.dom4j.{LocationData, Dom4jUtils ⇒ Dom4j}
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.dom4j.Element
import org.orbeon.saxon.om.Item
import scala.collection.JavaConverters._
import org.orbeon.oxf.xforms.xbl.Scope

abstract class XFormsAction {

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

    /**
     * Add event context attributes based on nested xxforms:context elements.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param actionElement     action element
     * @param event             event to add context information to
     */
    protected def addContextAttributes(actionInterpreter: XFormsActionInterpreter, actionElement: Element, event: XFormsEvent) {

        val contextStack = actionInterpreter.actionXPathContext

        // Iterate over context information if any
        for (currentContextInfo ← Dom4j.elements(actionElement, XXFORMS_CONTEXT_QNAME).asScala) {
            // Get and check attributes
            val name =
                Option(Dom4j.qNameToExplodedQName(Dom4j.extractAttributeValueQName(currentContextInfo, NAME_QNAME))) getOrElse
                    (throw new OXFException(XXFORMS_CONTEXT_QNAME.getQualifiedName + " element must have a \"name\" attribute."))

            val value = VariableAnalysis.valueOrSelectAttribute(currentContextInfo) match {
                case valueOrSelect: String ⇒
                    // XPath expression

                    // Set context on context element
                    val currentActionScope = actionInterpreter.getActionScope(currentContextInfo)
                    contextStack.pushBinding(currentContextInfo, actionInterpreter.getSourceEffectiveId(currentContextInfo), currentActionScope, false)

                    // Evaluate context parameter
                    val result = XPathCache.evaluateAsExtent(
                        actionInterpreter.actionXPathContext.getCurrentNodeset,
                        actionInterpreter.actionXPathContext.getCurrentPosition,
                        valueOrSelect,
                        actionInterpreter.getNamespaceMappings(currentContextInfo),
                        contextStack.getCurrentVariables,
                        XFormsContainingDocument.getFunctionLibrary,
                        contextStack.getFunctionContext(actionInterpreter.getSourceEffectiveId(currentContextInfo)),
                        null,
                        currentContextInfo.getData.asInstanceOf[LocationData])

                    contextStack.returnFunctionContext()
                    contextStack.popBinding()
                    result
                case _ ⇒
                    // Literal text
                    new SequenceExtent(Array[Item](StringValue.makeStringValue(currentContextInfo.getStringValue)))
            }

            event.setCustom(name, value)
        }
    }
}