/**
  * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{VariableAnalysis, VariableAnalysisTrait}
import org.orbeon.saxon.om.ValueRepresentation
import org.orbeon.saxon.value.{EmptySequence, StringValue}
import org.orbeon.xforms.XFormsId

import scala.util.control.NonFatal

// Represent an xf:var.
class Variable(val staticVariable: VariableAnalysisTrait, val containingDocument: XFormsContainingDocument) {

  private var variableValueOpt: Option[ValueRepresentation] = None

  def valueEvaluateIfNeeded(
    contextStack      : XFormsContextStack,
    sourceEffectiveId : String,
    pushOuterContext  : Boolean,
    handleNonFatal    : Boolean
  ): ValueRepresentation =
    variableValueOpt getOrElse {
      val result =
        evaluate(
          contextStack,
          XFormsId.getRelatedEffectiveId(sourceEffectiveId, staticVariable.valueStaticId),
          pushOuterContext,
          handleNonFatal
        )
      variableValueOpt = Some(result)
      result
    }

  def markDirty(): Unit             = variableValueOpt = None
  def getLocationData: LocationData = staticVariable.asInstanceOf[ElementAnalysis].locationData

  private def evaluate(
    contextStack      : XFormsContextStack,
    sourceEffectiveId : String,
    pushOuterContext  : Boolean,
    handleNonFatal    : Boolean
  ): ValueRepresentation =
    staticVariable.expressionStringOpt match {
      case None =>
        // Inline constructor (for now, only textual content, but in the future, we could allow xf:output in it? more?)
        new StringValue(staticVariable.valueElement.getStringValue)
      case Some(expression) =>

        // Push binding for evaluation, so that @context and @model are evaluated
        val pushContext = pushOuterContext || staticVariable.hasNestedValue
        if (pushContext)
          contextStack.pushBinding(staticVariable.valueElement, sourceEffectiveId, staticVariable.valueScope)

        val result = {
          val bindingContext = contextStack.getCurrentBindingContext
          val currentNodeset = bindingContext.nodeset
          if (! currentNodeset.isEmpty) {
            // TODO: in the future, we should allow null context for expressions that do not depend on the context
            val functionContext = contextStack.getFunctionContext(sourceEffectiveId)
            val scopeModelVariables = VariableAnalysis.variableScopesModelVariables(staticVariable)
            try {
              XPathCache.evaluateAsExtent(
                currentNodeset,
                bindingContext.position,
                expression,
                staticVariable.valueNamespaceMapping,
                bindingContext.getInScopeVariables(scopeModelVariables),
                containingDocument.functionLibrary,
                functionContext,
                null,
                getLocationData,
                containingDocument.getRequestStats.getReporter
              )
            } catch {
              case NonFatal(t) if handleNonFatal =>
                  // Don't consider this as fatal
                  // Default value is the empty sequence
                  XFormsError.handleNonFatalXPathError(contextStack.container, t)
                  EmptySequence.getInstance
              case NonFatal(t) =>
                throw new OXFException(t)
            }
          } else {
            EmptySequence.getInstance
          }
        }

        if (pushContext)
          contextStack.popBinding

        result
    }
}
