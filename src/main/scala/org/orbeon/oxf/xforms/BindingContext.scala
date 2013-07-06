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
package org.orbeon.oxf.xforms

import xbl.Scope
import java.util.{List ⇒ JList, Map ⇒ JMap, HashMap ⇒ JHashMap}
import org.dom4j.Element
import collection.JavaConverters._
import org.orbeon.saxon.om.{NodeInfo, ValueRepresentation, Item}
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}

// Represent the XPath binding of an XForms object (control, action, etc.)
case class BindingContext(
        parent: BindingContext,
        model: XFormsModel,
        bind: RuntimeBind,
        nodeset: JList[Item],
        position: Int,
        elementId: String,
        newBind: Boolean,
        controlElement: Element,
        private val _locationData: LocationData,
        hasOverriddenContext: Boolean,
        contextItem: Item,
        scope: Scope) {

    self ⇒

    require(scope ne null)
    require(nodeset ne null)

    // Location data associated with the XForms element (typically, a control) associated with the binding. If location
    // data was passed during construction, pass that, otherwise try to get location data from passed element.
    val locationData = Option(_locationData) orElse (Option(controlElement) map (_.getData.asInstanceOf[LocationData])) orNull
    private var _variable: Option[VariableNameValue] = None
    def variable = _variable

    // Constructor for scoping a variable
    def this(
            parent: BindingContext,
            base: BindingContext,
            controlElement: Element,
            locationData: LocationData,
            variableName: String,
            variableValue: ValueRepresentation,
            scope: Scope) {

        this(parent, base.model, null, base.nodeset, base.position, base.elementId, false,
             controlElement, locationData, false, base.contextItem, scope)

        _variable = Some(new VariableNameValue(variableName, variableValue))
    }

    // Create a copy with a new variable in scope
    def pushVariable(variableElement: Element, name: String, value: ValueRepresentation, scope: Scope) = {

        def ancestorOrSelfInScope(scope: Scope) =
            new AncestorIterator(includeSelf = true) find (_.scope == scope) getOrElse (throw new IllegalStateException)

        val locationData = new ExtendedLocationData(variableElement.getData.asInstanceOf[LocationData], "pushing variable binding", variableElement)
        new BindingContext(this, ancestorOrSelfInScope(scope), variableElement, locationData, name, value, scope)
    }

    // Java callers
    def getNodeset = nodeset
    def getPosition = position
    def isNewBind = newBind
    def getControlElement = controlElement

    def getSingleItem =
        if (nodeset.isEmpty)
            null
        else
            nodeset.get(position - 1)

    def getInScopeVariables: JMap[String, ValueRepresentation] = getInScopeVariables(scopeModelVariables = true)

    def getInScopeVariables(scopeModelVariables: Boolean): JMap[String, ValueRepresentation] = {

        val tempVariablesMap = new JHashMap[String, ValueRepresentation]

        // Scope view variables in the same scope only
        for {
            bindingContext ← new AncestorIterator(includeSelf = true)
            if bindingContext.scope == scope
            VariableNameValue(name, value) ← bindingContext.variable
        } locally {
            // The binding defines a variable and there is not already a variable with that name
            // NOTE: Put condition here to make sure we take previous variables into account
            if (! tempVariablesMap.containsKey(name))
                tempVariablesMap.put(name, value)
        }

        // Scope model variables at the bottom if needed
        if (scopeModelVariables && (model ne null))
            for ((name, value) ← model.getTopLevelVariables.asScala)
                if (! tempVariablesMap.containsKey(name))
                    tempVariablesMap.put(name, value)

        tempVariablesMap
    }

    /*
    def scopeVariable(staticVariable: VariableAnalysisTrait, sourceEffectiveId: String, handleNonFatal: Boolean): VariableInfo = {

        // Create variable object
        val variable = new Variable(staticVariable, this)

        // Find variable scope
        val newScope = staticVariable.scope

        // Push the variable on the context stack. Note that we do as if each variable was a "parent" of the
        // following controls and variables.

        // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation
        // in the future.

        // NOTE: We used to simply add variables to the current bindingContext, but this could cause issues
        // because getVariableValue() can itself use variables declared previously. This would work at first,
        // but because BindingContext caches variables in scope, after a first request for in-scope variables,
        // further variables values could not be added. The method below temporarily adds more elements on the
        // stack but it is safer.
        getFunctionContext(sourceEffectiveId)
        val result = pushVariable(staticVariable.element, variable.getVariableName, variable.getVariableValue(sourceEffectiveId, true, handleNonFatal), newScope)
        returnFunctionContext()

        assert(result.variables.size == 1)
        result.variables.get(0)
    }

    private def scopeModelVariables(model: XFormsModel): Unit = {

        val variableInfos =
        for (variable ← model.getStaticModel.variablesSeq)
            yield scopeVariable(variable, model.getEffectiveId, true)

        val indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY)
        if (indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("", "evaluated model variables", "count", Integer.toString(variableInfos.size))

        setVariables(variableInfos)
    }
    */

    // NOTE: This is as of 2009-09-17 used only to determine the submission instance based on a submission node.
    def instanceOrNull =
        Option(getSingleItem) collect
            { case node: NodeInfo ⇒ model.containingDocument.getInstanceForNode(node) } orNull

    class AncestorIterator(includeSelf: Boolean) extends Iterator[BindingContext] {
        private var _next = if (includeSelf) self else parent
        def hasNext = _next ne null
        def next() = {
            val result = _next
            _next = _next.parent
            result
        }
    }
}

// Hold a variable name/value
case class VariableNameValue(name: String, value: ValueRepresentation)

object BindingContext {
    // NOTE: Ideally, we would like the empty context to be a constant, as nobody should use it! Or, the binding context
    // should simply be None.
    def empty(bindingElement: Element, scope: Scope) =
        BindingContext(null, null, null, Seq.empty[Item].asJava, 0, null, false, bindingElement, null, false, null, scope)
}