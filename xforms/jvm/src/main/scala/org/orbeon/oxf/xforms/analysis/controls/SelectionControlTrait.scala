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

import org.apache.commons.lang3.StringUtils
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Element, QName, Text}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.InputValueControl
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.control.LHHAValue
import org.orbeon.oxf.xforms.itemset.{Item, ItemContainer, Itemset}
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.dom4j.Dom4jUtils

trait SelectionControlTrait
extends InputValueControl
   with SelectAppearanceTrait
   with ChildrenLHHAItemsetsAndActionsTrait {

  if  (element.attributeValue("selection") == "open")
    throw new ValidationException("Open selection is currently not supported.", locationData)

  // Try to figure out if we have dynamic items. This attempts to cover all cases, including
  // nested xf:output controls. Check only under xf:choices, xf:item and xf:itemset so that we
  // don't check things like event handlers. Also check for AVTs.
  val hasStaticItemset =
    ! XPathCache.evaluateSingle(
      new DocumentWrapper(
        element.getDocument,
        null,
        XPath.GlobalConfiguration
      ).wrap(element),
      """
        exists(
          (xf:choices | xf:item | xf:itemset)/
          descendant-or-self::*[
            @ref     or
            @nodeset or
            @bind    or
            @value   or
            @*[
              contains(., '{')
            ]
          ]
        )
      """,
      XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING,
      null,
      null,
      null,
      null,
      locationData,
      null
    ).asInstanceOf[Boolean]

  val isNorefresh      = element.attributeValue(XXFORMS_REFRESH_ITEMS_QNAME) == "false"
  val mustEncodeValues = Option(element.attributeValue(ENCRYPT_ITEM_VALUES)) map (_.toBoolean)

  private var itemsetAnalysis: Option[XPathAnalysis] = None
  private var _itemsetAnalyzed = false
  def itemsetAnalyzed = _itemsetAnalyzed

  final def getItemsetAnalysis = { assert(_itemsetAnalyzed); itemsetAnalysis }

  override def analyzeXPath() = {
    super.analyzeXPath()
    itemsetAnalysis = computeItemsetAnalysis()
    _itemsetAnalyzed = true
  }

  def computeItemsetAnalysis() = {

    // TODO: operate on nested ElementAnalysis instead of Element

    var combinedAnalysis: XPathAnalysis = StringAnalysis()

    Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener() {

      var stack: List[SimpleElementAnalysis] = SelectionControlTrait.this :: Nil

      def startElement(element: Element): Unit = {

        // Make lazy as might not be used
        lazy val itemElementAnalysis =
          new SimpleElementAnalysis(staticStateContext, element, Some(stack.head), None, stack.head.getChildElementScope(element))
            with ValueTrait with OptionalSingleNode with ViewTrait

        def processElement(qName: QName, required: Boolean): Unit = {
          val nestedElement = element.element(qName)

          if (required)
            require(nestedElement ne null)

          if (nestedElement ne null) {
            val nestedAnalysis = new LHHAAnalysis(
              staticStateContext = staticStateContext,
              element            = nestedElement,
              parent             = Some(itemElementAnalysis),
              preceding          = None,
              scope              = itemElementAnalysis.getChildElementScope(nestedElement)
            )
            nestedAnalysis.analyzeXPath()
            combinedAnalysis = combinedAnalysis combine nestedAnalysis.getValueAnalysis.get
          }
        }

        element.getQName match {

          case XFORMS_ITEM_QNAME | XFORMS_ITEMSET_QNAME ⇒

            // Analyze container and add as a value dependency
            // We add this as dependency because the itemset must also be recomputed if any returned item of
            // a container is changing. That's because that influences whether the item is present or not,
            // as in:
            //
            // <xf:itemset ref=".[not(context() = instance('foo'))]">
            //   <xf:label>Year</xf:label>
            //   <xf:value/>
            // </xf:itemset>
            //
            // This is not an issue with controls, as container relevance ensures we don't evaluate nested
            // expressions, but it must be done for itemsets.
            //
            // See also #289 https://github.com/orbeon/orbeon-forms/issues/289 (closed)
            itemElementAnalysis.analyzeXPath()
            combinedAnalysis = combinedAnalysis combine itemElementAnalysis.getBindingAnalysis.get.makeValuesDependencies

            processElement(LABEL_QNAME, required = true)
            processElement(XFORMS_VALUE_QNAME, required = true)

            if (isFull) {
              processElement(HELP_QNAME, required = false)
              processElement(HINT_QNAME, required = false)
            }

          case XFORMS_CHOICES_QNAME ⇒

            // Analyze container and add as a value dependency (see above)
            itemElementAnalysis.analyzeXPath()
            combinedAnalysis = combinedAnalysis combine itemElementAnalysis.getBindingAnalysis.get.makeValuesDependencies

            processElement(LABEL_QNAME, required = false) // label is optional on xf:choices

            // Always push the container
            stack ::= itemElementAnalysis

          case _ ⇒ // ignore
        }
      }

      def endElement(element: Element): Unit =
        if (element.getQName == XFORMS_CHOICES_QNAME)
          stack = stack.tail

      def text(text: Text) = ()
    })

    Some(combinedAnalysis)
  }

  override def toXMLContent(helper: XMLReceiverHelper): Unit = {
    super.toXMLContent(helper)
    if (_itemsetAnalyzed)
      getItemsetAnalysis match {
        case Some(analysis) ⇒
          helper.startElement("itemset")
          analysis.toXML(helper)
          helper.endElement()
        case _ ⇒ // NOP
      }
  }

  override def freeTransientState() = {
    super.freeTransientState()
    if (_itemsetAnalyzed && getItemsetAnalysis.isDefined)
      getItemsetAnalysis.get.freeTransientState()
  }

  // Return the control's static itemset if any
  lazy val staticItemset = hasStaticItemset option evaluateStaticItemset

  private def evaluateStaticItemset = {

    // TODO: operate on nested ElementAnalysis instead of Element

    val result = new Itemset(isMultiple)

    Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener() {

      private var position = 0
      private var currentContainer: ItemContainer = result

      def startElement(element: Element): Unit = {

        def findNestedLHHValue(qName: QName, required: Boolean) = {
          val nestedElementOpt = Option(element.element(qName))

          if (required && nestedElementOpt.isEmpty)
            throw new ValidationException(
              s"${XFORMS_ITEM_QNAME.getQualifiedName} must contain an ${qName.getQualifiedName} element.",
              ElementAnalysis.createLocationData(element)
            )

          nestedElementOpt flatMap { nestedElement ⇒
            val containsHTML = Array[Boolean](false)

            val valueOpt = XFormsUtils.getStaticChildElementValue(containerScope.fullPrefix, nestedElement, isFull, containsHTML).trimAllToOpt

            if (required)
              Some(LHHAValue(valueOpt getOrElse "", containsHTML(0)))
            else
              valueOpt map (LHHAValue(_, containsHTML(0)))
          }
        }

        element.getQName match {

          case XFORMS_ITEM_QNAME ⇒ // xf:item

            val label = findNestedLHHValue(LABEL_QNAME, required = true).get
            val help  = findNestedLHHValue(HELP_QNAME,  required = false)
            val hint  = findNestedLHHValue(HINT_QNAME,  required = false)

            val value = {
              val valueElement = element.element(XFORMS_VALUE_QNAME)
              if (valueElement eq null)
                throw new ValidationException(
                  "xf:item must contain an xf:value element.",
                  ElementAnalysis.createLocationData(element)
                )

              StringUtils.defaultString(XFormsUtils.getStaticChildElementValue(containerScope.fullPrefix, valueElement, false, null))
            }

            currentContainer.addChildItem(
              Item(
                position   = position,
                isMultiple = isMultiple,
                attributes = SelectionControlUtil.getAttributes(element),
                label      = label,
                help       = help,
                hint       = hint,
                value      = value
              )
            )
            position += 1

          case XFORMS_ITEMSET_QNAME ⇒ // xf:itemset

            throw new ValidationException(
              "xf:itemset must not appear in static itemset.",
              ElementAnalysis.createLocationData(element)
            )

          case XFORMS_CHOICES_QNAME ⇒ // xf:choices

            findNestedLHHValue(LABEL_QNAME, required = false) foreach { label ⇒
              val newContainer = Item(
                position   = position,
                isMultiple = isMultiple,
                attributes = SelectionControlUtil.getAttributes(element),
                label      = label,
                help       = None,
                hint       = None,
                value      = null
              )
              position += 1
              currentContainer.addChildItem(newContainer)
              currentContainer = newContainer
            }

          case _ ⇒ // ignore
        }
      }

      def endElement(element: Element): Unit =
        if (element.getQName == XFORMS_CHOICES_QNAME) {
          // xf:choices
          val labelElement = element.element(LABEL_QNAME)
          if (labelElement ne null)
            currentContainer = currentContainer.parent
        }

      def text(text: Text) = ()
    })
    result
  }
}

object SelectionControlUtil {

  val AttributesToPropagate = List(CLASS_QNAME, STYLE_QNAME, XXFORMS_OPEN_QNAME)
  val TopLevelItemsetQNames = Set(XFORMS_ITEM_QNAME, XFORMS_ITEMSET_QNAME, XFORMS_CHOICES_QNAME)

  def isTopLevelItemsetElement(e: Element) = TopLevelItemsetQNames(e.getQName)

  def getAttributes(itemChoiceItemset: Element) =
    for {
      attributeName   ← AttributesToPropagate
      attributeValue = itemChoiceItemset.attributeValue(attributeName)
      if attributeValue ne null
    } yield
      attributeName → attributeValue
}