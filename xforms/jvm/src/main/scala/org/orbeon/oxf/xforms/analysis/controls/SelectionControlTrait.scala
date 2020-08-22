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

import cats.syntax.option._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Element, QName, Text}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.InputValueControl
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.itemset.{Item, ItemContainer, Itemset, LHHAValue}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

trait SelectionControlTrait
extends InputValueControl
   with SelectAppearanceTrait
   with ChildrenLHHAItemsetsAndActionsTrait {

  if (element.attributeValue("selection") == "open")
    throw new ValidationException("Open selection is currently not supported.", locationData)

  private def newElemWrapper: NodeInfo = new DocumentWrapper(
    element.getDocument,
    null,
    XPath.GlobalConfiguration
  ).wrap(element)

  // Try to figure out if we have dynamic items. This attempts to cover all cases, including
  // nested xf:output controls. Check only under xf:choices, xf:item and xf:itemset so that we
  // don't check things like event handlers. Also check for AVTs.
  val hasStaticItemset: Boolean =
    ! XPathCache.evaluateSingle(
      contextItem = newElemWrapper,
      xpathString =
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
      namespaceMapping   = XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING,
      variableToValueMap = null,
      functionLibrary    = null,
      functionContext    = null,
      baseURI            = null,
      locationData       = locationData,
      reporter           = null
    ).asInstanceOf[Boolean]

  val useCopy: Boolean = {

    val wrapper = newElemWrapper

    val hasCopyElem  = wrapper descendant XFORMS_COPY_QNAME  nonEmpty
    val hasValueElem = wrapper descendant XFORMS_VALUE_QNAME nonEmpty

    // This limitation could be lifted in the future
    if (hasValueElem && hasCopyElem)
      throw new ValidationException(
        s"an itemset cannot have both `xf:copy` and `xf:value` elements",
        ElementAnalysis.createLocationData(element)
      )

    hasCopyElem
  }

  val excludeWhitespaceTextNodesForCopy: Boolean =
    element.attributeValue(EXCLUDE_WHITESPACE_TEXT_NODES_QNAME) == "true"

  val isNorefresh: Boolean =
    element.attributeValue(XXFORMS_REFRESH_ITEMS_QNAME) == "false"

  val mustEncodeValues: Option[Boolean] =
    if (useCopy)
      true.some
    else
      element.attributeValueOpt(ENCRYPT_ITEM_VALUES) map (_.toBoolean)

  private var itemsetAnalysis: Option[XPathAnalysis] = None
  private var _itemsetAnalyzed = false
  def itemsetAnalyzed: Boolean = _itemsetAnalyzed

  final def getItemsetAnalysis: Option[XPathAnalysis] = { assert(_itemsetAnalyzed); itemsetAnalysis }

  override def isAllowedBoundItem(item: om.Item): Boolean =
    if (useCopy)
      DataModel.isAllowedBoundItem(item)
    else
      super.isAllowedBoundItem(item)

  override def analyzeXPath(): Unit = {
    super.analyzeXPath()
    itemsetAnalysis = computeItemsetAnalysis()
    _itemsetAnalyzed = true
  }

  def computeItemsetAnalysis(): Option[XPathAnalysis] = {

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
            val nestedAnalysis = new LHHAAnalysis( // TODO: Weird! This is not an LHHA analysis.
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

          case XFORMS_ITEM_QNAME | XFORMS_ITEMSET_QNAME =>

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
            processElement(XFORMS_VALUE_QNAME, required = false)
            processElement(XFORMS_COPY_QNAME, required = false)

            if (isFull) {
              processElement(HELP_QNAME, required = false)
              processElement(HINT_QNAME, required = false)
            }

          case XFORMS_CHOICES_QNAME =>

            // Analyze container and add as a value dependency (see above)
            itemElementAnalysis.analyzeXPath()
            combinedAnalysis = combinedAnalysis combine itemElementAnalysis.getBindingAnalysis.get.makeValuesDependencies

            processElement(LABEL_QNAME, required = false) // label is optional on xf:choices

            // Always push the container
            stack ::= itemElementAnalysis

          case _ => // ignore
        }
      }

      def endElement(element: Element): Unit =
        if (element.getQName == XFORMS_CHOICES_QNAME)
          stack = stack.tail

      def text(text: Text): Unit = ()
    })

    Some(combinedAnalysis)
  }

  override def toXMLContent(helper: XMLReceiverHelper): Unit = {
    super.toXMLContent(helper)
    if (_itemsetAnalyzed)
      getItemsetAnalysis match {
        case Some(analysis) =>
          helper.startElement("itemset")
          analysis.toXML(helper)
          helper.endElement()
        case _ => // NOP
      }
  }

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    if (_itemsetAnalyzed && getItemsetAnalysis.isDefined)
      getItemsetAnalysis.get.freeTransientState()
  }

  // Return the control's static itemset if any
  lazy val staticItemset: Option[Itemset] = hasStaticItemset option evaluateStaticItemset

  private def evaluateStaticItemset = {

    // TODO: operate on nested ElementAnalysis instead of Element

    val result = new Itemset(isMultiple, hasCopy = false)

    Dom4jUtils.visitSubtree(element, new Dom4jUtils.VisitorListener() {

      private var position = 0
      private var currentContainer: ItemContainer = result

      def startElement(element: Element): Unit = {

        def findLhhValue(qName: QName, required: Boolean): Option[LHHAValue] = {

          element.elementOpt(qName) match {
            case Some(lhhaElem) =>

              val containsHTML = Array[Boolean](false)

              val valueOpt =
                XFormsUtils.getStaticChildElementValue(
                  containerScope.fullPrefix,
                  lhhaElem,
                  isFull,
                  containsHTML
                ).trimAllToOpt

              if (required)
                LHHAValue(valueOpt getOrElse "", containsHTML(0)).some
              else
                valueOpt map (LHHAValue(_, containsHTML(0)))

            case None =>
              if (required)
                throw new ValidationException(
                  "`xf:item` or `xf:itemset` must contain an `xf:label` element",
                  ElementAnalysis.createLocationData(element)
                )
              else
                None
          }
        }

        element.getQName match {

          case XFORMS_ITEM_QNAME => // xf:item

            val labelOpt = findLhhValue(LABEL_QNAME, required = true)
            val helpOpt  = findLhhValue(HELP_QNAME,  required = false)
            val hintOpt  = findLhhValue(HINT_QNAME,  required = false)

            val valueOpt = {

              val rawValue =
                element.elementOpt(XFORMS_VALUE_QNAME) map (
                  XFormsUtils.getStaticChildElementValue(
                    containerScope.fullPrefix,
                    _,
                    false,
                    null
                  )
                ) getOrElse (
                  throw new ValidationException(
                    "xf:item must contain an xf:value element.",
                    ElementAnalysis.createLocationData(element)
                  )
                )

              if (isMultiple)
                rawValue.trimAllToOpt
              else
                rawValue.some
            }

            valueOpt foreach { value =>
              currentContainer.addChildItem(
                Item.ValueNode(
                  label      = labelOpt getOrElse LHHAValue.Empty,
                  help       = helpOpt,
                  hint       = hintOpt,
                  value      = Left(value),
                  attributes = SelectionControlUtil.getAttributes(element)
                )(
                  position   = position
                )
              )
              position += 1
            }

          case XFORMS_ITEMSET_QNAME => // xf:itemset

            throw new ValidationException(
              "xf:itemset must not appear in static itemset.",
              ElementAnalysis.createLocationData(element)
            )

          case XFORMS_CHOICES_QNAME => // xf:choices

            val labelOpt = findLhhValue(LABEL_QNAME, required = false)

            labelOpt foreach { _ =>
              val newContainer = Item.ChoiceNode(
                label      = labelOpt getOrElse LHHAValue.Empty,
                attributes = SelectionControlUtil.getAttributes(element)
              )(
                position   = position
              )
              position += 1
              currentContainer.addChildItem(newContainer)
              currentContainer = newContainer
            }

          case _ => // ignore
        }
      }

      def endElement(element: Element): Unit =
        if (element.getQName == XFORMS_CHOICES_QNAME) {
          if (element.elementOpt(LABEL_QNAME).isDefined)
            currentContainer = currentContainer.parent
        }

      def text(text: Text): Unit = ()
    })
    result
  }
}

object SelectionControlUtil {

  val AttributesToPropagate = List(CLASS_QNAME, STYLE_QNAME, XXFORMS_OPEN_QNAME)
  val TopLevelItemsetQNames = Set(XFORMS_ITEM_QNAME, XFORMS_ITEMSET_QNAME, XFORMS_CHOICES_QNAME)

  def isTopLevelItemsetElement(e: Element): Boolean = TopLevelItemsetQNames(e.getQName)

  def getAttributes(itemChoiceItemset: Element): List[(QName, String)] =
    for {
      attributeName   <- AttributesToPropagate
      attributeValue = itemChoiceItemset.attributeValue(attributeName)
      if attributeValue ne null
    } yield
      attributeName -> attributeValue
}