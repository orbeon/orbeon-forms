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
import org.orbeon.oxf.xforms.XFormsConstants
import org.dom4j.Element

/**
 * Trait representing a variable element, whether in the model or in the view.
 */
trait VariableAnalysisTrait extends SimpleElementAnalysis with ContainerTrait with VariableTrait {

    // Variable name and value
    val name = element.attributeValue(XFormsConstants.NAME_QNAME)
    def variableAnalysis = getValueAnalysis

    override val canHoldValue = true // TODO: not clear that this is useful at this point, see who calls this

    override def computeValueAnalysis = {
    
        element.element(XFormsConstants.XXFORMS_SEQUENCE_QNAME) match {
            case sequenceElement: Element =>
                // Value is provided by nested xxf:sequence/@select

                // First figure out the scope for xxf:sequence
                val sequenceScope = getChildElementScope(sequenceElement)

                val sequenceAnalysis = new SimpleElementAnalysis(staticStateContext, sequenceElement, Some(VariableAnalysisTrait.this), None, sequenceScope) {

                    override protected def computeValueAnalysis =
                        Some(VariableAnalysis.valueOrSelectAttribute(element) match {
                            // @value or @select
                            case value: String => analyzeXPath(getChildrenContext, value)
                            // Value is constant
                            case _ => StringAnalysis() // TODO: store constant value?
                        })

                    // If in same scope as xxf:variable, in-scope variables are the same as xxf:variable because we don't
                    // want the variable defined by xxf:variable to be in-scope for xxf:sequence. Otherwise, use
                    // default algorithm.

                    // TODO: This is bad architecture as we duplicate the logic in ViewTrait.
                    override lazy val inScopeVariables =
                        if (VariableAnalysisTrait.this.scopeModel.scope == scopeModel.scope)
                            VariableAnalysisTrait.this.inScopeVariables
                        else
                            getRootVariables ++ treeInScopeVariables

                    override protected def getRootVariables = VariableAnalysisTrait.this match {
                        case _: ViewTrait => scopeModel.containingModel match { case Some(model) => model.variablesMap; case None => Map.empty }
                        case _ => Map.empty
                    }
                }
                sequenceAnalysis.analyzeXPath()
                sequenceAnalysis.getValueAnalysis
            case _ =>
                // No nested xxf:sequence element
                Some(VariableAnalysis.valueOrSelectAttribute(element) match {
                    // @value or @select
                    case value: String => analyzeXPath(getChildrenContext, value)
                    // Value is constant
                    case _ => StringAnalysis() // TODO: store constant value?
                })
        }
    }
}

object VariableAnalysis {

    def valueOrSelectAttribute(element: Element) = {
        val select = element.attributeValue(XFormsConstants.SELECT_QNAME)
        if (select ne null) select else element.attributeValue(XFormsConstants.VALUE_QNAME)
    }

    def isVariableElement(element: Element) =
        element.getName == XFormsConstants.XXFORMS_VAR_QNAME.getName ||
            element.getName == XFormsConstants.XXFORMS_VARIABLE_QNAME.getName
}