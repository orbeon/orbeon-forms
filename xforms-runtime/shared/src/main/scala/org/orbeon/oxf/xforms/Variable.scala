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
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, XPathErrorDetails}
import org.orbeon.oxf.xforms.analysis.controls.{VariableAnalysis, VariableAnalysisTrait}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.saxon.value.{EmptySequence, StringValue}
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsId}

import scala.util.control.NonFatal

// Represent an xf:var.
class Variable(val staticVariable: VariableAnalysisTrait, val containingDocument: XFormsContainingDocument) {

  private var variableValueOpt: Option[ValueRepresentationType] = None

  def valueEvaluateIfNeeded(
    contextStack     : XFormsContextStack,
    sourceEffectiveId: String,
    pushOuterContext : Boolean,
    eventTarget      : XFormsEventTarget,
    collector        : ErrorEventCollector
  ): ValueRepresentationType =
    variableValueOpt getOrElse {
      val result =
        evaluate(
          contextStack,
          XFormsId.getRelatedEffectiveId(sourceEffectiveId, staticVariable.valueStaticId),
          pushOuterContext,
          eventTarget,
          collector
        )
      variableValueOpt = Some(result)
      result
    }

  def markDirty(): Unit             = variableValueOpt = None
  def getLocationData: LocationData = staticVariable.asInstanceOf[ElementAnalysis].locationData

  private def evaluate(
    contextStack     : XFormsContextStack,
    sourceEffectiveId: String,
    pushOuterContext : Boolean,
    eventTarget      : XFormsEventTarget,
    collector        : ErrorEventCollector
  ): ValueRepresentationType =
    staticVariable.expressionOrConstant match {
      case Right(constant) =>
        // Inline constructor (for now, only textual content, but in the future, we could allow xf:output in it? more?)
        new StringValue(constant)
      case Left(expression) =>

        // Push binding for evaluation, so that @context and @model are evaluated
        val pushContext = pushOuterContext || staticVariable.hasNestedValue
        if (pushContext)
          contextStack.pushBinding(
            staticVariable.valueElement,
            sourceEffectiveId,
            staticVariable.valueScope,
            eventTarget,
            collector
          )

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
              ).reduce() // not sure if reducing is required after an evaluation
            } catch {
              case NonFatal(t) =>
                collector(
                  new XXFormsXPathErrorEvent(
                    target         = eventTarget,
                    expression     = expression,
                    details        = XPathErrorDetails.ForVariable(staticVariable.name),
                    message        = XFormsCrossPlatformSupport.getRootThrowable(t).getMessage,
                    throwable      = t
                  )
                )
                EmptySequence.getInstance
            }
          } else {
            EmptySequence.getInstance
          }
        }

        if (pushContext)
          contextStack.popBinding()

        result
    }
}
