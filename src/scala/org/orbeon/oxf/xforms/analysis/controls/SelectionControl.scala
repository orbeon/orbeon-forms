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
import org.orbeon.oxf.util.XPathCache
import org.apache.commons.lang.StringUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, StringAnalysis, XPathAnalysis, SimpleElementAnalysis}
import org.orbeon.oxf.xforms.itemset.{ItemContainer, XFormsItemUtils, Item, Itemset}
import org.dom4j.{QName, Text, Element}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.XFormsConstants._
import collection.JavaConverters._

trait SelectionControl extends SimpleElementAnalysis with SelectAppearanceTrait {

    // Try to figure out if we have dynamic items. This attempts to cover all cases, including
    // nested xforms:output controls. Check only under xforms:choices, xforms:item and xforms:itemset so that we
    // don't check things like event handlers. Also check for AVTs ion @class and @style.
    val hasStaticItemset = !XPathCache.evaluateSingle(staticStateContext.controlsDocument.wrap(element),
            "exists((xforms:choices | xforms:item | xforms:itemset)/(., .//xforms:*)[@ref or @nodeset or @bind or @value or @*[contains(., '{')]])",
            XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData).asInstanceOf[Boolean]

    val isNorefresh = element.attributeValue(XXFORMS_REFRESH_ITEMS_QNAME) == "false"

    if  (element.attributeValue("selection") == "open")
        throw new ValidationException("Open selection is currently not supported.", locationData)

    val isEncryptValues = Option(element.attributeValue(ENCRYPT_ITEM_VALUES)) map
        (_.toBoolean) getOrElse
            staticStateContext.partAnalysis.staticState.getProperty[Boolean](XFormsProperties.ENCRYPT_ITEM_VALUES_PROPERTY)

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

                    case ITEM_QNAME | ITEMSET_QNAME ⇒

                        itemElementAnalysis.analyzeXPath()

                        processElement(LABEL_QNAME, true)
                        processElement(XFORMS_VALUE_QNAME, true)

                    case CHOICES_QNAME ⇒

                        itemElementAnalysis.analyzeXPath()

                        processElement(LABEL_QNAME, false) // label is optional on xf:choices

                        // Always push the container
                        stack push itemElementAnalysis

                    case _ ⇒ // ignore
                }
            }

            def endElement(element: Element): Unit =
                if (element == CHOICES_QNAME)
                    stack pop()

            def text(text: Text) = ()
        })

        Some(combinedAnalysis)
    }

    override def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: ⇒ Unit) {
        super.toXML(helper, attributes) {
            // Optional content
            content

            // Itemset details
            getItemsetAnalysis match {
                case Some(analysis) ⇒
                    helper.startElement("itemset")
                    analysis.toXML(helper)
                    helper.endElement()
                case _ ⇒ // NOP
            }
        }
    }

    override def freeTransientState() = {
        super.freeTransientState()
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

                element.getQName match {

                    case ITEM_QNAME ⇒ // xforms:item

                        val labelElement = element.element(LABEL_QNAME)
                        if (labelElement eq null)
                            throw new ValidationException("xforms:item must contain an xforms:label element.", ElementAnalysis.createLocationData(element))
                        val containsHTML = Array(false)
                        val label = XFormsUtils.getStaticChildElementValue(labelElement, isFull, containsHTML)

                        val valueElement = element.element(XFORMS_VALUE_QNAME)
                        if (valueElement eq null)
                            throw new ValidationException("xforms:item must contain an xforms:value element.", ElementAnalysis.createLocationData(element))
                        val value = XFormsUtils.getStaticChildElementValue(valueElement, false, null)

                        val attributes = SelectionControlUtil.getAttributes(element)
                        currentContainer.addChildItem(new Item(isMultiple, isEncryptValues, attributes, new Item.Label(label, containsHTML(0)), StringUtils.defaultString(value)))

                    case ITEMSET_QNAME ⇒ // xforms:itemset

                        throw new ValidationException("xforms:itemset must not appear in static itemset.", ElementAnalysis.createLocationData(element))

                    case CHOICES_QNAME ⇒ // xforms:choices

                        val labelElement = element.element(LABEL_QNAME)
                        if (labelElement ne null) {
                            val label = XFormsUtils.getStaticChildElementValue(labelElement, false, null)

                            assert(label ne null)

                            val attributes = SelectionControlUtil.getAttributes(element)
                            val newContainer = new Item(isMultiple, isEncryptValues, attributes, new Item.Label(label, false), null)
                            currentContainer.addChildItem(newContainer);
                            currentContainer = newContainer
                        }

                    case _ ⇒ // ignore
                }
            }

            def endElement(element: Element): Unit =
                if (element == CHOICES_QNAME) {
                    // xforms:choices
                    val labelElement = element.element(LABEL_QNAME)
                    if (labelElement ne null)
                        currentContainer = currentContainer.getParent
                }

            def text(text: Text) = ()
        })
        result
    }
}

object SelectionControlUtil {

    private val attributesToPropagate = XFormsItemUtils.ATTRIBUTES_TO_PROPAGATE.toSeq

    def getAttributes(itemChoiceItemset: Element) = {
        val tuples =
            for {
                attributeName ← attributesToPropagate
                attributeValue = itemChoiceItemset.attributeValue(attributeName)
                if attributeValue ne null
            } yield
                attributeName → attributeValue

        tuples.toMap.asJava
    }
}