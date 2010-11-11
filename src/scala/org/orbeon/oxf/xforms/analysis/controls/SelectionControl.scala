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

import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsStaticState}
import collection.mutable.Stack
import org.orbeon.oxf.xforms.analysis.{StringAnalysis, XPathAnalysis, SimpleElementAnalysis}
import org.dom4j.{Text, Element}
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.util.{PropertyContext, XPathCache}

trait SelectionControl extends SimpleElementAnalysis {

    // Try to figure out if we have dynamic items. This attempts to cover all cases, including
    // nested xforms:output controls. Check only under xforms:choices, xforms:item and xforms:itemset so that we
    // don't check things like event handlers. Also check for AVTs ion @class and @style.
    // TODO: fix this, seems incorrect: if there is an itemset, consider dynamic; also handle AVTs on any child element of label/value
    val hasNonStaticItem = XPathCache.evaluateSingle(staticStateContext.propertyContext, staticStateContext.controlsDocument.wrap(element),
            "exists(./(xforms:choices | xforms:item | xforms:itemset)//xforms:*[@ref or @nodeset or @bind or @value or (@class, @style)[contains(., '{')]])",
            XFormsStaticState.BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData).asInstanceOf[Boolean]

    // Remember information
    val isMultiple = element.getName == "select"

    private var itemsetAnalysis: Option[XPathAnalysis] = None
    private var itemsetAnalyzed = false

    final def getItemsetAnalysis = { assert(itemsetAnalyzed); itemsetAnalysis }

    override def analyzeXPath() = {
        super.analyzeXPath()
        itemsetAnalysis = computeItemsetAnalysis()
        itemsetAnalyzed = true
    }

    def computeItemsetAnalysis() = {

        var combinedAnalysis: XPathAnalysis = StringAnalysis()

        Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener() {

            val stack: Stack[SimpleElementAnalysis] = Stack(SelectionControl.this)

            def startElement(element: Element) {

                // Make lazy as might not be used
                lazy val itemElementAnalysis = new SimpleElementAnalysis(staticStateContext, element, Some(stack.top), None, stack.top.getChildElementScope(element)) with ValueTrait with ViewTrait

                element.getQName match {

                    case XFormsConstants.ITEM_QNAME | XFormsConstants.ITEMSET_QNAME =>

                        itemElementAnalysis.analyzeXPath()

                        val labelElement = element.element(XFormsConstants.LABEL_QNAME)
                        val valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME)

                        require(labelElement ne null)
                        require(valueElement ne null)

                        val labelAnalysis = new LocalLHHAAnalysis(staticStateContext, labelElement, itemElementAnalysis, None, itemElementAnalysis.getChildElementScope(labelElement))
                        val valueAnalysis = new LocalLHHAAnalysis(staticStateContext, valueElement, itemElementAnalysis, None, itemElementAnalysis.getChildElementScope(valueElement))

                        labelAnalysis.analyzeXPath()
                        valueAnalysis.analyzeXPath()

                        combinedAnalysis = combinedAnalysis combine labelAnalysis.getValueAnalysis.get
                        combinedAnalysis = combinedAnalysis combine valueAnalysis.getValueAnalysis.get

                    case XFormsConstants.CHOICES_QNAME =>

                        itemElementAnalysis.analyzeXPath()

                        val labelElement = element.element(XFormsConstants.LABEL_QNAME)
                        if (labelElement ne null) { // label is optional
                            val labelAnalysis = new LocalLHHAAnalysis(staticStateContext, labelElement, itemElementAnalysis, None, itemElementAnalysis.getChildElementScope(labelElement))
                            labelAnalysis.analyzeXPath()

                            combinedAnalysis = combinedAnalysis combine labelAnalysis.getValueAnalysis.get
                        }

                        // Always push the container
                        stack push itemElementAnalysis

                    case _ => // ignore
                }
            }

            def endElement(element: Element): Unit =
                if (element == XFormsConstants.CHOICES_QNAME)
                    stack pop

            def text(text: Text) = {}
        })

        Some(combinedAnalysis)
    }

    override def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper, attributes: List[String])(content: => Unit) {
        super.toXML(propertyContext, helper, attributes) {
            // Optional content
            content

            // Itemset details
            getItemsetAnalysis match {
                case Some(analysis) =>
                    helper.startElement("itemset")
                    analysis.toXML(propertyContext, helper)
                    helper.endElement()
                case _ => // NOP
            }
        }
    }

    override def freeTransientState() = {
        super.freeTransientState
        if (itemsetAnalyzed && getItemsetAnalysis.isDefined)
            getItemsetAnalysis.get.freeTransientState()
    }
}
