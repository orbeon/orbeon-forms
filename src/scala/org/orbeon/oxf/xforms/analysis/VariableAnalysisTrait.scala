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
package org.orbeon.oxf.xforms.analysis

import controls.{SimpleAnalysis, ViewAnalysis}
import org.orbeon.oxf.xforms.{XFormsUtils, XFormsConstants}
import org.orbeon.oxf.util.PropertyContext
import org.orbeon.oxf.xml.ContentHandlerHelper

trait VariableAnalysisTrait {

    this: SimpleAnalysis =>

    val name = element.attributeValue("name")

    def computeVariableValueAnalysis: XPathAnalysis = {

        val sequenceElement = element.element(XFormsConstants.XXFORMS_SEQUENCE_QNAME)
        if (sequenceElement != null) {
            // Value is provided by nested xxf:sequence/@select

            // First figure out the scope for xxf:sequence
            val sequencePrefixedId =  XFormsUtils.getRelatedEffectiveId(prefixedId, XFormsUtils.getElementStaticId(sequenceElement))
            val sequenceScope = staticState.getXBLBindings().getResolutionScopeByPrefixedId(sequencePrefixedId)

            val sequenceAnalysis = new ViewAnalysis(staticState, sequenceScope, sequenceElement, this, getInScopeVariables, false) {
                override def computeValueAnalysis(): XPathAnalysis = {
                    val selectAttribute = element.attributeValue("select")
                    if (selectAttribute != null) {
                        // Value is provided by @select
                        val baseAnalysis = findOrCreateBaseAnalysis(this)
                        return analyzeXPath(staticState, baseAnalysis, prefixedId, selectAttribute)
                    } else {
                        // Value is constant
                        return XPathAnalysis2.CONSTANT_ANALYSIS
                    }
                }
            }
            sequenceAnalysis.analyzeXPath()
            return sequenceAnalysis.getValueAnalysis
        } else {
            val selectAttribute = element.attributeValue("select")
            if (selectAttribute != null) {
                // Value is provided by @select
                val baseAnalysis = findOrCreateBaseAnalysis(this)
                return analyzeXPath(staticState, baseAnalysis, prefixedId, selectAttribute)
            } else {
                // Value is constant
                return XPathAnalysis2.CONSTANT_ANALYSIS
            }
        }
    }

    def toVariableXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) {

        helper.startElement("variable", Array(
                "scope", scope.scopeId,
                "prefixed-id", prefixedId,
                "model-prefixed-id", getModelPrefixedId,
                "binding", hasNodeBinding.toString,
                "value", canHoldValue.toString,
                "name", name
        ))

        // Control binding and value analysis
        if (getBindingAnalysis != null && hasNodeBinding) {
            helper.startElement("binding")
            getBindingAnalysis.toXML(propertyContext, helper)
            helper.endElement()
        }
        if (getValueAnalysis != null) {
            helper.startElement("value")
            getValueAnalysis.toXML(propertyContext, helper)
            helper.endElement()
        }

        helper.endElement()
    }
}