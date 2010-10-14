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

import org.orbeon.oxf.xforms.{XFormsUtils, XFormsConstants}
import org.dom4j.Element

/**
 * Trait representing a variable element, whether in the model or in the view.
 */
trait VariableAnalysisTrait extends SimpleElementAnalysis with ContainerTrait {

    // Variable name
    val name = element.attributeValue(XFormsConstants.NAME_QNAME)

    override val canHoldValue = true // TODO: not clear that this is useful at this point, see who calls this

    override def computeValueAnalysis = {

        element.element(XFormsConstants.XXFORMS_SEQUENCE_QNAME) match {
            case sequenceElement: Element =>
                // Value is provided by nested xxf:sequence/@select

                // First figure out the scope for xxf:sequence
                val sequenceScope = {
                    val sequencePrefixedId =  XFormsUtils.getRelatedEffectiveId(prefixedId, XFormsUtils.getElementStaticId(sequenceElement))
                    staticStateContext.staticState.getXBLBindings.getResolutionScopeByPrefixedId(sequencePrefixedId)
                }

                val sequenceAnalysis = new SimpleElementAnalysis(staticStateContext, sequenceElement, Some(VariableAnalysisTrait.this), None, sequenceScope) {
                    override protected def computeValueAnalysis = {
                        Some(element.attributeValue(XFormsConstants.SELECT_QNAME) match {
                            case selectAttribute: String =>
                                // Value is provided by @select
                                analyzeXPath(getChildrenContext, selectAttribute)
                            case _ =>
                                // Value is constant
                                PathMapXPathAnalysis.CONSTANT_ANALYSIS
                                // TODO: store constant value?
                        })
                    }

                    // Same in-scope variables than the parent variable element
                    override lazy val inScopeVariables = VariableAnalysisTrait.this.inScopeVariables
                }
                sequenceAnalysis.analyzeXPath()
                sequenceAnalysis.getValueAnalysis
            case _ =>
                // No nested xxf:sequence element
                Some(element.attributeValue(XFormsConstants.SELECT_QNAME) match {
                    case selectAttribute: String =>
                        // Value is provided by @select
                        analyzeXPath(getChildrenContext, selectAttribute)
                    case _ =>
                        // Value is constant
                        PathMapXPathAnalysis.CONSTANT_ANALYSIS
                        // TODO: store constant value?
                })
        }
    }
}