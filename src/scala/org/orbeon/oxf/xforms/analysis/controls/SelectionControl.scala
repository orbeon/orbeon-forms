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

import collection.mutable.Stack
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.util.{PropertyContext, XPathCache}
import org.apache.commons.lang.StringUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.common.ValidationException
import java.util.{LinkedHashMap => JLinkedHashMap}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, StringAnalysis, XPathAnalysis, SimpleElementAnalysis}
import org.orbeon.oxf.xforms.{XFormsProperties, XFormsUtils, XFormsConstants, XFormsStaticState}
import org.orbeon.oxf.xforms.itemset.{ItemContainer, XFormsItemUtils, Item, Itemset}
import org.dom4j.{QName, Text, Element}

trait SelectionControl extends SimpleElementAnalysis {

    // Try to figure out if we have dynamic items. This attempts to cover all cases, including
    // nested xforms:output controls. Check only under xforms:choices, xforms:item and xforms:itemset so that we
    // don't check things like event handlers. Also check for AVTs ion @class and @style.
    val hasStaticItemset = !XPathCache.evaluateSingle(staticStateContext.propertyContext, staticStateContext.controlsDocument.wrap(element),
            "exists((xforms:choices | xforms:item | xforms:itemset)/(., .//xforms:*)[@ref or @nodeset or @bind or @value or @*[contains(., '{')]])",
            XFormsStaticState.BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData).asInstanceOf[Boolean]

    // Remember information
    val isMultiple = element.getName == "select"
    val isOpenSelection = element.attributeValue("selection") == "open"
    val isNorefresh = element.attributeValue(XFormsConstants.XXFORMS_REFRESH_ITEMS_QNAME) == "false"
    val isFull = element.attributeValue(XFormsConstants.APPEARANCE_QNAME) == "full"

    val isEncryptValues = {
        val localEncryptItemValues = element.attributeValue(XFormsConstants.ENCRYPT_ITEM_VALUES)
        !isOpenSelection && (if (localEncryptItemValues ne null)
            localEncryptItemValues.toBoolean else staticStateContext.staticState.getBooleanProperty(XFormsProperties.ENCRYPT_ITEM_VALUES_PROPERTY))
    }

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

                def processElement(qName: QName, doRequire: Boolean) {
                    val nestedElement = element.element(qName)

                    if (doRequire)
                        require(nestedElement ne null)

                    if (nestedElement ne null) {
                        val nestedAnalysis = new LocalLHHAAnalysis(staticStateContext, nestedElement, itemElementAnalysis, None, itemElementAnalysis.getChildElementScope(nestedElement))
                        nestedAnalysis.analyzeXPath()
                        combinedAnalysis = combinedAnalysis combine nestedAnalysis.getValueAnalysis.get
                    }
                }

                element.getQName match {

                    case XFormsConstants.ITEM_QNAME | XFormsConstants.ITEMSET_QNAME =>

                        itemElementAnalysis.analyzeXPath()

                        processElement(XFormsConstants.LABEL_QNAME, true)
                        processElement(XFormsConstants.XFORMS_VALUE_QNAME, true)

                    case XFormsConstants.CHOICES_QNAME =>

                        itemElementAnalysis.analyzeXPath()

                        processElement(XFormsConstants.LABEL_QNAME, false) // label is optional on xf:choices

                        // Always push the container
                        stack push itemElementAnalysis

                    case _ => // ignore
                }
            }

            def endElement(element: Element): Unit =
                if (element == XFormsConstants.CHOICES_QNAME)
                    stack pop

            def text(text: Text) = ()
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

    /**
     * Evaluate a static itemset.
     */
    def evaluateStaticItemset(): Itemset = {

        assert(hasStaticItemset)

        val result = new Itemset

        Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener() {

            private var currentContainer: ItemContainer = result

            def startElement(element: Element) {

                def getAttributes(itemChoiceItemsetElement: Element) = {
                    val result = new JLinkedHashMap[QName, String]
                    for (attributeName <- XFormsItemUtils.ATTRIBUTES_TO_PROPAGATE) {
                        val attributeValue = itemChoiceItemsetElement.attributeValue(XFormsConstants.CLASS_QNAME)
                        if (attributeValue ne null)
                            result.put(attributeName, attributeValue)
                    }
                    result
                }

                element.getQName match {

                    case XFormsConstants.ITEM_QNAME => // xforms:item

                        val labelElement = element.element(XFormsConstants.LABEL_QNAME)
                        if (labelElement eq null)
                            throw new ValidationException("xforms:item must contain an xforms:label element.", ElementAnalysis.createLocationData(element))
                        val containsHTML = Array(false)
                        val label = XFormsUtils.getStaticChildElementValue(labelElement, isFull, containsHTML)

                        val valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME)
                        if (valueElement eq null)
                            throw new ValidationException("xforms:item must contain an xforms:value element.", ElementAnalysis.createLocationData(element))
                        val value = XFormsUtils.getStaticChildElementValue(valueElement, false, null)

                        val attributes = getAttributes(element)
                        currentContainer.addChildItem(new Item(isMultiple, isEncryptValues, attributes, new Item.Label(label, containsHTML(0)), StringUtils.defaultString(value)))

                    case XFormsConstants.ITEMSET_QNAME => // xforms:itemset

                        throw new ValidationException("xforms:itemset must not appear in static itemset.", ElementAnalysis.createLocationData(element))

                    case XFormsConstants.CHOICES_QNAME => // xforms:choices

                        val labelElement = element.element(XFormsConstants.LABEL_QNAME)
                        if (labelElement ne null) {
                            val label = XFormsUtils.getStaticChildElementValue(labelElement, false, null)

                            assert(label ne null)

                            val attributes = getAttributes(element)
                            val newContainer = new Item(isMultiple, isEncryptValues, attributes, new Item.Label(label, false), null)
                            currentContainer.addChildItem(newContainer);
                            currentContainer = newContainer
                        }

                    case _ => // ignore
                }
            }

            def endElement(element: Element): Unit =
                if (element == XFormsConstants.CHOICES_QNAME) {
                    // xforms:choices
                    val labelElement = element.element(XFormsConstants.LABEL_QNAME)
                    if (labelElement ne null)
                        currentContainer = currentContainer.getParent
                }

            def text(text: Text) = ()
        })
        result
    }
}
