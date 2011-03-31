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
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.collection.JavaConversions._

abstract class LHHAAnalysis(staticStateContext: StaticStateContext, element: Element, parent: ContainerTrait, preceding: Option[ElementAnalysis], scope: XBLBindings#Scope)
        extends SimpleElementAnalysis(staticStateContext, element, Some(parent), preceding, scope) {

    require(parent ne null)

    val isLocal: Boolean

    // TODO: make use of static value
    //
    // o output static value in HTML markup and repeat templates
    // o if has static value, don't attempt to compare values upon diff, and never send new related information to client
    val (staticValue, containsHTML) =
        if (LHHAAnalysis.hasStaticValue(staticStateContext, element)) {
            // TODO: figure out whether to allow HTML or not (could default to true?)
            val containsHTML = Array(false)
            (Option(XFormsUtils.getStaticChildElementValue(element, true, containsHTML)), containsHTML(0))
        } else
            (None, false)

    def debugOut(): Unit =
        if (staticValue.isDefined)
            println("static value for control " + prefixedId + " => " + staticValue.get)

    // Consider that LHHA don't have context/binding as we delegate implementation in computeValueAnalysis
    override protected def computeContextAnalysis = None
    override protected def computeBindingAnalysis = None

    override protected def computeValueAnalysis = {
        if (staticValue.isEmpty) {
            // Value is likely not static

            // Delegate to concrete implementation
            val delegateAnalysis = new SimpleElementAnalysis(staticStateContext, element, Some(parent), preceding, scope) with ValueTrait with ViewTrait
            delegateAnalysis.analyzeXPath()

            if (ref.isDefined || value.isDefined) {
                // 1. E.g. <xforms:label model="…" context="…" value|ref="…"/>
                assert(element.elements.isEmpty) // no children elements allowed in this case

                // Use value provided by the delegate
                delegateAnalysis.getValueAnalysis
            } else {
                // 2. E.g. <xforms:label>…<xforms:output value|ref=""…/>…<span class="{…}">…</span></xforms:label>

                // NOTE: We do allow @context and/or @model on LHHA element, which can change the context

                // The subtree can only contain HTML elements interspersed with xf:output. HTML elements may have AVTs.
                var combinedAnalysis: XPathAnalysis = StringAnalysis()

                Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener {
                    val hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs
                    def startElement(element: Element) {
                        if (element.getQName == XFormsConstants.XFORMS_OUTPUT_QNAME) {
                            // Add dependencies
                            val outputAnalysis = new SimpleElementAnalysis(staticStateContext, element, Some(delegateAnalysis), None, delegateAnalysis.getChildElementScope(element))
                                    with ValueTrait with ViewTrait
                            outputAnalysis.analyzeXPath()
                            if (outputAnalysis.getValueAnalysis.isDefined)
                                combinedAnalysis = combinedAnalysis combine outputAnalysis.getValueAnalysis.get
                        } else if (hostLanguageAVTs) {
                            val attributes = element.attributes
                            for (o <- attributes; attributeValue = o.asInstanceOf[Attribute].getValue; if XFormsUtils.maybeAVT(attributeValue)) {
                                // TODO: handle AVTs
                                combinedAnalysis = NegativeAnalysis(attributeValue) // not supported just yet
                            }
                        }
                    }

                    def endElement(element: Element) = ()
                    def text(text: Text) = ()
                })

                // Result of all combined analyses
                Some(combinedAnalysis)
            }
        } else
            // Value of LHHA is 100% static and analysis is constant
            Some(StringAnalysis())
    }
}

object LHHAAnalysis {
    private def hasStaticValue(staticStateContext: StaticStateContext, lhhaElement: Element): Boolean = {
        // Try to figure out if we have a dynamic LHHA element, including nested xforms:output and AVTs.
        XPathCache.evaluateSingle(staticStateContext.controlsDocument.wrap(lhhaElement),
            "not(exists(descendant-or-self::xforms:*[@ref or @nodeset or @bind or @value] | descendant::*[@*[contains(., '{')]]))",
            XFormsStaticState.BASIC_NAMESPACE_MAPPING, null, null, null, null, ElementAnalysis.createLocationData(lhhaElement)).asInstanceOf[Boolean]
    }
}