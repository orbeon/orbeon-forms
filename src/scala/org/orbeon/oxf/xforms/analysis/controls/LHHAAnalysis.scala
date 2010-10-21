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
package org.orbeon.oxf.xforms.analysis.controls

import org.dom4j._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms._
import analysis._
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.orbeon.oxf.xml.dom4j._
import scala.collection.JavaConversions._

class LHHAAnalysis(staticStateContext: StaticStateContext, scope: XBLBindings#Scope,
                   element: Element, parent: ContainerTrait, val isLocal: Boolean)
        extends SimpleElementAnalysis(staticStateContext, element, Some(parent), None, scope) {

    val staticValue: Option[String] =
        if (LHHAAnalysis.hasStaticValue(staticStateContext, element))
            // TODO: figure out whether to allow HTML or not (could default to true?)
            Option(XFormsUtils.getStaticChildElementValue(element, true, null))
        else
            None

    // Consider that LHHA don't have context/binding
    override protected def computeContextAnalysis = None
    override protected def computeBindingAnalysis = None

    override protected def computeValueAnalysis = {
        if (staticValue.isEmpty) {

            // Delegate to concrete implementation
            val delegateAnalysis = new SimpleElementAnalysis(staticStateContext, element, Some(parent), None, scope) with ContainerTrait with ValueTrait
            delegateAnalysis.analyzeXPath()

            if (ref.isDefined || value.isDefined) {
                // 1. E.g. <xforms:label model="…" context="…" value|ref="…"/>
                assert (element.elements.isEmpty) // no children elements allowed in this case

                // Use value provided by the delegate
                delegateAnalysis.getValueAnalysis
            } else {
                // 2. E.g. <xforms:label>…<xforms:output value|ref=""…/>…<span class="{…}">…</span></xforms:label>

                // NOTE: We do allow @context and/or @model on LHHA element, which can change the context

                // The subtree can only contain HTML elements interspersed with xf:output. HTML elements may have AVTs.
                var analyses: List[XPathAnalysis] = Nil
                Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener {
                    val hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs
                    def startElement(element: Element) {
                        if (element.getQName == XFormsConstants.XFORMS_OUTPUT_QNAME) {
                            // Add dependencies
                            // TODO XXX: scope must match element, right? check status here
                            val outputAnalysis = new SimpleElementAnalysis(staticStateContext, element, Some(delegateAnalysis), None, scope) with ValueTrait
                            outputAnalysis.analyzeXPath()
                            if (outputAnalysis.getValueAnalysis.isDefined)
                                analyses = outputAnalysis.getValueAnalysis.get :: analyses
                        } else if (hostLanguageAVTs) {
                            val attributes = element.attributes
                            for (o <- attributes; attributeValue = o.asInstanceOf[Attribute].getValue; if XFormsUtils.maybeAVT(attributeValue)) {
                                // TODO: handle AVTs
                                analyses = List(PathMapXPathAnalysis.CONSTANT_NEGATIVE_ANALYSIS) // not supported just yet
                            }
                        }
                    }

                    def endElement(element: Element) {}
                    def text(text: Text) {}
                })

                // Combine all
                Some((PathMapXPathAnalysis.CONSTANT_ANALYSIS.asInstanceOf[XPathAnalysis] /: analyses) (_ combine _))
            }
        } else
            // Value of LHHA is 100% static and analysis is constant
            Some(PathMapXPathAnalysis.CONSTANT_ANALYSIS)
    }
}

object LHHAAnalysis {

    private def hasStaticValue(staticStateContext: StaticStateContext, lhhaElement: Element): Boolean = {

        // Try to figure out if we have a dynamic LHHA element. This attempts to cover all cases, including nested
        // xforms:output controls. Also check for AVTs on @class and @style.
        // TODO: check do we support other attributes?
        XPathCache.evaluateSingle(staticStateContext.propertyContext, staticStateContext.controlsDocument.wrap(lhhaElement),
            "not(exists(descendant-or-self::xforms:*[@ref or @nodeset or @bind or @value or (@class, @style)[contains(., '{')]]))",
            XFormsStaticState.BASIC_NAMESPACE_MAPPING, null, null, null, null, ElementAnalysis.createLocationData(lhhaElement)).asInstanceOf[Boolean]
    }
}