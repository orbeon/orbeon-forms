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

import collection.JavaConverters._
import java.{util ⇒ ju}
import org.dom4j.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.XFormsControlFactory
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.om.{NodeInfo, ValueRepresentation, Item}
import xbl.Scope

// Represent the XPath binding of an XForms object (control, action, etc.)
case class BindingContext(
        parent: BindingContext,
        model: XFormsModel,
        bind: RuntimeBind,
        nodeset: ju.List[Item],
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
    def pushVariable(variableElement: ElementAnalysis, name: String, value: ValueRepresentation, scope: Scope) = {

        def ancestorOrSelfInScope(scope: Scope) =
            new AncestorIterator(includeSelf = true) find (_.scope == scope) getOrElse (throw new IllegalStateException)

        new BindingContext(this, ancestorOrSelfInScope(scope), variableElement.element, variableElement.locationData, name, value, scope)
    }

    def getSingleItem =
        if (nodeset.isEmpty)
            null
        else
            nodeset.get(position - 1)

    def getInScopeVariables: ju.Map[String, ValueRepresentation] = getInScopeVariables(scopeModelVariables = true)

    def getInScopeVariables(scopeModelVariables: Boolean): ju.Map[String, ValueRepresentation] = {

        val tempVariablesMap = new ju.HashMap[String, ValueRepresentation]

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

    // We don't have a bound element, but the parent is bound to xf:repeat
    def isRepeatIterationBindingContext =
        (parent ne null) && (controlElement eq null) && parent.isRepeatBindingContext

    def isRepeatBindingContext =
        (controlElement ne null) && controlElement.getName == "repeat"

    // Return the closest enclosing repeat id, throw if not found
    def enclosingRepeatId: String =
        // Handle case where we are within a repeat iteration, as well as case where we are directly within the repeat
        // container object
        new AncestorIterator(includeSelf = true) collectFirst {
            case binding if binding.isRepeatIterationBindingContext || binding.isRepeatBindingContext ⇒
                binding.parent.elementId
        } getOrElse {
            throw new ValidationException("Enclosing xf:repeat not found.", locationData)
        }

    // Get the current repeat sequence for the given repeat id
    def repeatItems(repeatId: String): ju.List[Item] =
        new AncestorIterator(includeSelf = true) collectFirst {
            case binding if repeatId == binding.elementId && (binding.controlElement ne null) && binding.controlElement.getName == "repeat" ⇒
                binding.nodeset
        } getOrElse {
            throw new ValidationException(s"No enclosing xf:repeat found for id $repeatId", locationData)
        }

    // Obtain the single-node binding for an enclosing xf:group, xf:switch, or xf:repeat. It takes one mandatory string
    // parameter containing the id of an enclosing grouping XForms control. For xf:repeat, the context returned is the
    // context of the current iteration.
    def contextForId(contextId: String): Item = {
        
        def matchesContainer(binding: BindingContext) =
            (binding.controlElement ne null) &&
            XFormsControlFactory.isContainerControl(binding.controlElement.getNamespaceURI, binding.controlElement.getName) &&
            binding.elementId == contextId
        
        def matchesRepeat(binding: BindingContext) =
            (binding.controlElement eq null) &&
            binding.isRepeatIterationBindingContext && binding.parent.elementId == contextId
        
        new AncestorIterator(includeSelf = true) collectFirst {
            case binding if matchesContainer(binding) || matchesRepeat(binding) ⇒ binding.getSingleItem
        } getOrElse {
            throw new ValidationException(s"No enclosing container XForms control found for id $contextId", locationData)
        }
    }
    
    def enclosingRepeatIterationBindingContext(repeatId: Option[String]): BindingContext = {

        def matches(binding: BindingContext) =
            binding.isRepeatIterationBindingContext && (repeatId.isEmpty || binding.parent.elementId == repeatId.get)

        new AncestorIterator(includeSelf = true) collectFirst {
            case binding if matches(binding) ⇒ binding
        } getOrElse {
            val message =
                repeatId match {
                    case Some(id) ⇒ s"No enclosing xf:repeat found for repeat id $id"
                    case None     ⇒  "No enclosing xf:repeat found"
                }

            throw new ValidationException(message, locationData)
        }
    }

    def currentBindingContextForModel(model: XFormsModel) =
        new AncestorIterator(includeSelf = true) find (_.model eq model) orNull

    // Get the current binding for the given model.
    def currentNodeset(model: XFormsModel ): ju.List[Item] = {

        val bindingContext = currentBindingContextForModel(model)

        // If a context exists, return its nodeset
        if (bindingContext ne null)
            return bindingContext.nodeset

        // If there is no default instance, return an empty node-set
        val defaultInstance = model.getDefaultInstance
        if (defaultInstance eq null)
            return XFormsConstants.EMPTY_ITEM_LIST

        // If not found, return the document element of the model's default instance
        ju.Collections.singletonList(defaultInstance.rootElement.asInstanceOf[Item])
    }

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