/**
 *  Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import controls.ViewTrait
import org.orbeon.dom.Element
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.common.ValidationException

/**
 * Trait representing a variable element, whether in the model or in the view.
 */
trait VariableAnalysisTrait extends SimpleElementAnalysis with VariableTrait {

  variableSelf =>

  import VariableAnalysis._

  // Variable name and value
  val name = variableSelf.element.attributeValue(NAME_QNAME)
  if (name eq null)
    throw new ValidationException(s"`${element.getQualifiedName}` element must have a `name` attribute", ElementAnalysis.createLocationData(element))

  val valueElement        = valueOrSequenceElement(variableSelf.element) getOrElse variableSelf.element
  val expressionStringOpt = VariableAnalysis.valueOrSelectAttribute(valueElement)

  // Lazy because accessing scopeModel
  private lazy val nestedAnalysis =
    valueOrSequenceElement(variableSelf.element) map { valueElement =>
      new SimpleElementAnalysis(staticStateContext, valueElement, Some(variableSelf), None, getChildElementScope(valueElement)) {

        nestedSelf =>

        override protected def computeValueAnalysis: Option[XPathAnalysis] =
          valueOrSelectAttribute(nestedSelf.element) match {
            case Some(value) => Some(analyzeXPath(nestedSelf.getChildrenContext, value))
            case None        => Some(StringAnalysis()) // TODO: store constant value?
          }

        // If in same scope as xf:var, in-scope variables are the same as xxf:var because we don't
        // want the variable defined by xf:var to be in-scope for xxf:value. Otherwise, use
        // default algorithm.

        // TODO: This is bad architecture as we duplicate the logic in ViewTrait.
        override lazy val inScopeVariables: Map[String, VariableTrait] =
          if (variableSelf.scope == nestedSelf.scope)
            variableSelf.inScopeVariables
          else
            getRootVariables ++ nestedSelf.treeInScopeVariables

        override protected def getRootVariables: Map[String, VariableAnalysisTrait] = variableSelf match {
          case _: ViewTrait => nestedSelf.model match { case Some(model) => model.variablesMap; case None => Map() }
          case _            => Map()
        }
      }
    }

  // Scope of xf:var OR nested xxf:value if present
  lazy val (hasNestedValue, valueScope, valueNamespaceMapping, valueStaticId) = nestedAnalysis match {
    case Some(nestedAnalysis) =>
      (true, nestedAnalysis.scope, nestedAnalysis.namespaceMapping, nestedAnalysis.staticId)
    case None =>
      (false, scope, namespaceMapping, staticId)
  }

  def variableAnalysis = getValueAnalysis

  override def computeValueAnalysis =
    nestedAnalysis match {
      case Some(nestedAnalysis) =>
        // Value is provided by nested xxf:value/@value
        nestedAnalysis.analyzeXPath()
        nestedAnalysis.getValueAnalysis
      case None =>
        // No nested xxf:value element
        valueOrSelectAttribute(element) match {
          case Some(value) => Some(analyzeXPath(getChildrenContext, value))
          case _           => Some(StringAnalysis()) // TODO: store constant value?
        }
    }
}

object VariableAnalysis {

  def valueOrSelectAttribute(element: Element) =
    Option(element.attributeValue(VALUE_QNAME)) orElse Option(element.attributeValue(SELECT_QNAME))

  def valueOrSequenceElement(element: Element) =
    Option(element.element(XXFORMS_VALUE_QNAME)) orElse Option(element.element(XXFORMS_SEQUENCE_QNAME))

  // See https://github.com/orbeon/orbeon-forms/issues/1104 and https://github.com/orbeon/orbeon-forms/issues/1132
  def variableScopesModelVariables(v: VariableAnalysisTrait) =
    v.isInstanceOf[ViewTrait] || v.model != (v.parent flatMap (_.model))
}