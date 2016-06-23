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

import org.orbeon.dom.Node
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, VariableAnalysis, VariableAnalysisTrait}
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.expr.LastPositionFinder
import org.orbeon.saxon.om.{Item, SequenceIterator, ValueRepresentation, VirtualNode}
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.value.{EmptySequence, SequenceExtent, StringValue}

import scala.util.control.NonFatal

// Represent an xf:var.
class Variable(val staticVariable: VariableAnalysisTrait, val containingDocument: XFormsContainingDocument) {

  private var variableValueOpt: Option[ValueRepresentation] = None

  def valueEvaluateIfNeeded(
    contextStack      : XFormsContextStack,
    sourceEffectiveId : String,
    pushOuterContext  : Boolean,
    handleNonFatal    : Boolean
  ): ValueRepresentation = {

    val justEvaluated = variableValueOpt.isEmpty

    val localValue = variableValueOpt getOrElse {
      val result =
        evaluate(
          contextStack,
          XFormsUtils.getRelatedEffectiveId(sourceEffectiveId, staticVariable.valueStaticId),
          pushOuterContext,
          handleNonFatal
        )
      variableValueOpt = Some(result)
      result
    }

    // Return value and rewrap if necessary
    localValue match {
      case sequenceExtent: SequenceExtent if ! justEvaluated ⇒
        // Rewrap NodeWrapper contained in the variable value. Not the most efficient, but at this point we have to
        // to ensure that things work properly. See RewrappingSequenceIterator for more details.
        new SequenceExtent(new RewrappingSequenceIterator(sequenceExtent.iterate))
      case _ ⇒
        localValue
    }
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
      case None ⇒
        // Inline constructor (for now, only textual content, but in the future, we could allow xf:output in it? more?)
        new StringValue(staticVariable.valueElement.getStringValue)
      case Some(expression) ⇒

        // Push binding for evaluation, so that @context and @model are evaluated
        val pushContext = pushOuterContext || staticVariable.hasNestedValue
        if (pushContext)
          contextStack.pushBinding(staticVariable.valueElement, sourceEffectiveId, staticVariable.valueScope)

        val result= {
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
                containingDocument.getFunctionLibrary,
                functionContext,
                null,
                getLocationData,
                containingDocument.getRequestStats.getReporter
              )
            } catch {
              case NonFatal(t) if handleNonFatal ⇒
                  // Don't consider this as fatal
                  // Default value is the empty sequence
                  XFormsError.handleNonFatalXPathError(contextStack.container, t)
                  EmptySequence.getInstance
              case NonFatal(t) ⇒
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

/**
 * See https://github.com/orbeon/orbeon-forms/issues/2803
 *
 * This iterator rewraps NodeWrapper elements so that the original NodeWrapper is discarded and a new one created.
 * The reason we do this is that when we keep variables around, we don't want NodeWrapper.index to be set to
 * anything but -1. If we did that, then upon insertions of nodes in the DOM, the index would be out of date.
 *
 * Q: Could we instead do this only upon insert/delete, and use the dependency engine to mark only mutated
 * variables? What about in actions? What about using wrappers for variables which don't cache the position?
 */
private class RewrappingSequenceIterator(var iter: SequenceIterator) extends SequenceIterator with LastPositionFinder {

  var current: Item = null

  @throws[XPathException]
  def next(): Item = {
    val item = iter.next
    item match {
      case virtualNode: VirtualNode ⇒
        val documentWrapper = virtualNode.getDocumentRoot.asInstanceOf[DocumentWrapper]
        current = documentWrapper.wrap(virtualNode.getUnderlyingNode.asInstanceOf[Node])
      case _ ⇒
        current = item
    }
    current
  }

  def position: Int = iter.position
  def close() = ()

  @throws[XPathException]
  def getAnother: SequenceIterator = new RewrappingSequenceIterator(iter.getAnother)

  def getProperties: Int = iter.getProperties

  @throws[XPathException]
  def getLastPosition: Int =
    iter match {
      case finder: LastPositionFinder ⇒ finder.getLastPosition
      case _                          ⇒ throw new OXFException("Call to getLastPosition() and nested iterator is not a LastPositionFinder.")
    }
}