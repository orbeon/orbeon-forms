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
import org.dom4j.Element
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xforms.model.DataModel

/**
 * Trait representing a variable element, whether in the model or in the view.
 */
trait VariableAnalysisTrait extends SimpleElementAnalysis with VariableTrait {

    variableSelf ⇒

    // Variable name and value
    val name = element.attributeValue(XFormsConstants.NAME_QNAME)

    private lazy val sequenceAnalysis =
        element.element(XFormsConstants.XXFORMS_SEQUENCE_QNAME) match { // lazy because accessing scopeModel
            case sequenceElement: Element ⇒
                Some(new SimpleElementAnalysis(staticStateContext, sequenceElement, Some(variableSelf), None, getChildElementScope(sequenceElement)) {

                    sequenceSelf ⇒

                    override protected def computeValueAnalysis =
                        Some(VariableAnalysis.valueOrSelectAttribute(element) match {
                            // @value or @select
                            case value: String ⇒ analyzeXPath(sequenceSelf.getChildrenContext, value)
                            // Value is constant
                            case _ ⇒ StringAnalysis() // TODO: store constant value?
                        })

                    // If in same scope as xf:var, in-scope variables are the same as xxf:var because we don't
                    // want the variable defined by xf:var to be in-scope for xxf:sequence. Otherwise, use
                    // default algorithm.

                    // TODO: This is bad architecture as we duplicate the logic in ViewTrait.
                    override lazy val inScopeVariables =
                        if (variableSelf.scope == sequenceSelf.scope)
                            variableSelf.inScopeVariables
                        else
                            getRootVariables ++ sequenceSelf.treeInScopeVariables

                    override protected def getRootVariables = variableSelf match {
                        case _: ViewTrait ⇒ sequenceSelf.model match { case Some(model) ⇒ model.variablesMap; case None ⇒ Map() }
                        case _ ⇒ Map()
                    }
                })
            case _ ⇒ None
        }

    // Scope of xf:var OR nested xxf:sequence if present
    lazy val (hasSequence, valueScope, valueNamespaceMapping, valueStaticId) = sequenceAnalysis match {
        case Some(sequenceAnalysis) ⇒
            (true, sequenceAnalysis.scope, sequenceAnalysis.namespaceMapping, sequenceAnalysis.staticId)
        case None ⇒
            (false, scope, namespaceMapping, staticId)
    }

    def variableAnalysis = getValueAnalysis

    override def computeValueAnalysis =
        sequenceAnalysis match {
            case Some(sequenceAnalysis) ⇒
                // Value is provided by nested xxf:sequence/@value
                sequenceAnalysis.analyzeXPath()
                sequenceAnalysis.getValueAnalysis
            case None ⇒
                // No nested xxf:sequence element
                Some(VariableAnalysis.valueOrSelectAttribute(element) match {
                    // @value or @select
                    case value: String ⇒ analyzeXPath(getChildrenContext, value)
                    // Value is constant
                    case _ ⇒ StringAnalysis() // TODO: store constant value?
                })
        }
}

object VariableAnalysis {

    def valueOrSelectAttribute(element: Element) = {
        val select = element.attributeValue(XFormsConstants.SELECT_QNAME)
        if (select ne null) select else element.attributeValue(XFormsConstants.VALUE_QNAME)
    }
}