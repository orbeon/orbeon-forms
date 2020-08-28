/**
 * Copyright (C) 2016 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.itemset

import cats.syntax.option._
import org.orbeon.dom.{Element, QName, Text}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsContextStackSupport._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.{LHHAAnalysis, SelectionControlUtil}
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{XFormsId, XFormsNames}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal


object ItemsetSupport {

  def isSelected(
    isMultiple                 : Boolean,
    dataValue                  : Item.Value[om.NodeInfo],
    itemValue                  : Item.Value[om.Item],
    compareAtt                 : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes : Boolean
  ): Boolean =
    if (isMultiple)
      compareMultipleItemValues(dataValue, itemValue)
    else
      compareSingleItemValues(dataValue, itemValue, compareAtt, excludeWhitespaceTextNodes)

  def partitionAttributes(items: List[om.Item]): (List[om.NodeInfo], List[om.Item]) = {

    val hasAtt = items exists {
      case att: om.NodeInfo if att.isAttribute => true
      case _                                   => false
    }

    if (hasAtt) {
      val l = ListBuffer[om.NodeInfo]()
      val r = ListBuffer[om.Item]()

      items foreach {
        case att: om.NodeInfo if att.isAttribute => l += att
        case other                               => r += other
      }

      (l.result, r.result)
    } else {
      (Nil, items)
    }
  }

  def compareSingleItemValues(
    dataValue                  : Item.Value[om.Item],
    itemValue                  : Item.Value[om.Item],
    compareAtt                 : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes : Boolean
  ): Boolean =
    (dataValue, itemValue) match {
      case (Left(dataValue), Left(itemValue)) =>
        dataValue == itemValue
      case (Right(dataXPathItems), Right(itemXPathItems)) =>

        val (attItems, otherItems) = partitionAttributes(itemXPathItems)

        def compareContent =
          SaxonUtils.deepCompare(
            config                     = XPath.GlobalConfiguration,
            it1                        = dataXPathItems.iterator,
            it2                        = otherItems.iterator,
            excludeWhitespaceTextNodes = excludeWhitespaceTextNodes
          )

        (attItems forall compareAtt) && compareContent

      case _ =>
        // Mixing and matching `xf:copy` and `xf:value` is not supported for now
        false
    }

  def compareMultipleItemValues(
    dataValue : Item.Value[om.NodeInfo],
    itemValue : Item.Value[om.Item]
  ): Boolean =
    (dataValue, itemValue) match {
      case (Left(dataValue), Left(trimmedItemValue)) =>
        val trimmedControlValue = dataValue.trimAllToEmpty

        if (trimmedControlValue.isEmpty)
          trimmedItemValue.isEmpty // special case
        else
          trimmedControlValue.splitTo[scala.Iterator]() contains trimmedItemValue
      case (Right(allDataItems), Right(firstItemXPathItem :: _)) =>
        allDataItems exists { oneDataXPathItem =>
          SaxonUtils.deepCompare(
            config                     = XPath.GlobalConfiguration,
            it1                        = Iterator(oneDataXPathItem),
            it2                        = Iterator(firstItemXPathItem),
            excludeWhitespaceTextNodes = false
          )
        }
      case (Right(_), Right(Nil)) =>
        // Itemset construction doesn't ever produce an empty `List[om.Item]` for multiple selection
        false
      case _ =>
        // Mixing and matching `xf:copy` and `xf:value` is not supported
        false
    }

  def findMultipleItemValues(
    dataValue : Item.Value[om.NodeInfo],
    itemValue : Item.Value[om.Item]
  ): List[om.NodeInfo] =
    (dataValue, itemValue) match {
      case (Right(allDataItems), Right(firstItemXPathItem :: _)) =>
        allDataItems collect { case oneDataXPathItem if
          SaxonUtils.deepCompare(
            config                     = XPath.GlobalConfiguration,
            it1                        = Iterator(oneDataXPathItem),
            it2                        = Iterator(firstItemXPathItem),
            excludeWhitespaceTextNodes = false
          ) => oneDataXPathItem
        }
      case _ =>
        Nil
    }

  // Evaluate the itemset for a given `xf:select` or `xf:select1` control.
  def evaluateItemset(select1Control: XFormsSelect1Control): Itemset = {

    val staticControl = select1Control.staticControl

    staticControl.staticItemset match {
      case Some(staticItemset) =>
        staticItemset
      case None =>

        val container = select1Control.container
        val result = new Itemset(multiple = staticControl.isMultiple, hasCopy = staticControl.useCopy)

        // Set binding on this control, after saving the current context because the context stack must
        // remain unmodified.
        val contextStack = container.getContextStack
        val savedBindingContext = contextStack.getCurrentBindingContext
        contextStack.setBinding(select1Control.bindingContext)

        // TODO: This visits all of the control's descendants. It should only visit the top-level item|itemset|choices elements.
        select1Control.element.visitDescendants(
          new VisitorListener {

            private var position: Int = 0
            private var currentContainer: ItemContainer = result

            private def getElementEffectiveId(elem: Element): String =
              XFormsId.getRelatedEffectiveId(select1Control.getEffectiveId, XFormsUtils.getElementId(elem))

            def startElement(elem: Element): Unit = {

              elem.getQName match {
                case XFORMS_ITEM_QNAME =>

                  contextStack.pushBinding(elem, getElementEffectiveId(elem), select1Control.getChildElementScope(elem))

                  createValueNode(elem, position) foreach { newItem =>
                    currentContainer.addChildItem(newItem)
                    position += 1
                  }

                case XFORMS_ITEMSET_QNAME =>

                  contextStack.pushBinding(elem, getElementEffectiveId(elem), select1Control.getChildElementScope(elem))

                  val currentBindingContext = contextStack.getCurrentBindingContext

                  val currentSequence = currentBindingContext.nodeset

                  // Node stack tracks the relative position of the current node wrt ancestor nodes
                  var levelAndStack: (Int, List[om.Item]) = (0, Nil)

                  for (currentPosition <- 1 to currentSequence.size)
                    withIteration(currentPosition) { currentXPathItem =>

                      // We support relevance of items as an extension to XForms
                      // NOTE: If a node is non-relevant, all its descendants will be non-relevant as
                      // well. If a node is non-relevant, it should be as if it had not even been part of
                      // the nodeset.
                      if (XFormsSingleNodeControl.isRelevantItem(currentXPathItem)) {
                        createValueNode(elem, position) foreach { newItem =>
                          levelAndStack = updatedLevelAndItemStack(levelAndStack, currentXPathItem)
                          currentContainer.addChildItem(newItem)
                          position += 1
                        }
                      }

                    }(contextStack)
                case XFORMS_CHOICES_QNAME =>

                  contextStack.pushBinding(elem, getElementEffectiveId(elem), select1Control.getChildElementScope(elem))

                  if (elem.elementOpt(LABEL_QNAME).isDefined) {
                    val newItem = createChoiceNode(elem, position)
                    currentContainer.addChildItem(newItem)
                    position += 1
                    currentContainer = newItem
                  }
                case _ =>
              }
            }

            def endElement(elem: Element): Unit =
              elem.getQName match {
                case XFORMS_ITEM_QNAME | XFORMS_ITEMSET_QNAME =>
                  contextStack.popBinding()
                case XFORMS_CHOICES_QNAME =>
                  contextStack.popBinding()
                  if (elem.elementOpt(LABEL_QNAME).isDefined)
                    currentContainer = currentContainer.parent
                case _ =>
              }

            def text(text: Text): Unit = ()

            private def updatedLevelAndItemStack(
              currentLevelAndStack: (Int, List[om.Item]),
              currentXPathItem    : om.Item
            ): (Int, List[om.Item]) = {

              val currentLevel = currentLevelAndStack._1
              var newItemStack: List[om.Item] = currentLevelAndStack._2

              val newLevel =
                if (newItemStack.nonEmpty) {
                  val newLevel = getXPathItemLevel(currentXPathItem, newItemStack)
                  if (newLevel == currentLevel) {
                    //  We are staying at the same level, pop old item
                    newItemStack = newItemStack.tail
                  } else if (newLevel < currentLevel) {
                    //  We are going down one or more levels
                    newItemStack = newItemStack.tail
                    for (_ <- newLevel until currentLevel) {
                      newItemStack = newItemStack.tail
                      currentContainer = currentContainer.parent
                    }
                  } else if (newLevel > currentLevel) {
                    // Going up one level, set new container as last added child
                    currentContainer = currentContainer.lastChild
                  }
                  newLevel
                } else {
                  currentLevel
                }
              (newLevel, currentXPathItem :: newItemStack)
            }

            private def createValueNode(elem: Element, position: Int): Option[Item.ValueNode] =
              getValueOrCopyItemValue(elem) map { value =>
                Item.ValueNode(
                  label      = findLhhValue(elem.elementOpt(LABEL_QNAME), required = true) getOrElse LHHAValue.Empty,
                  help       = findLhhValue(elem.elementOpt(HELP_QNAME),  required = false),
                  hint       = findLhhValue(elem.elementOpt(HINT_QNAME),  required = false),
                  value      = value,
                  attributes = getAttributes(elem)
                )(
                  position   = position
                )
              }

            private def createChoiceNode(elem: Element, position: Int): Item.ChoiceNode =
              Item.ChoiceNode(
                label      = findLhhValue(elem.elementOpt(LABEL_QNAME), required = true) getOrElse LHHAValue.Empty,
                attributes = getAttributes(elem)
              )(
                position   = position
              )

            private def getValueOrCopyItemValue(elem: Element): Option[Item.Value[om.Item]] = {

              def fromValueElem =
                elem.elementOpt(XFORMS_VALUE_QNAME) flatMap
                  getValueValue                     map
                  Left.apply

              def fromCopyElem =
                elem.elementOpt(XFORMS_COPY_QNAME)           flatMap
                  getCopyValue                               filter
                  (_.nonEmpty || ! staticControl.isMultiple) map
                  Right.apply

              fromValueElem orElse fromCopyElem
            }

            private def getValueValue(valueElem: Element): Option[String] = {

              val rawValue =
                getChildElementValue(
                  container          = container,
                  sourceEffectiveId  = getElementEffectiveId(valueElem),
                  scope              = select1Control.getChildElementScope(valueElem),
                  childElement       = valueElem,
                  acceptHTML         = false,
                  defaultHTML        = false
                )._1

              // For multiple selection:
              //
              // - trim the value
              // - if the value is blank, it can't be used and the item is excluded
              //
              if (select1Control.staticControl.isMultiple)
                rawValue flatMap (_.trimAllToOpt)
              else
                rawValue
            }

            // Return `None` if:
            //
            // - there is no `ref` attribute
            // - there is an XPath error
            // - the context item is missing
            //
            private def getCopyValue(copyElem: Element): Option[List[om.Item]] =
              copyElem.attributeValueOpt(XFormsNames.REF_QNAME) flatMap { refAtt =>

                val sourceEffectiveId = getElementEffectiveId(copyElem)

                withBinding(copyElem, sourceEffectiveId, select1Control.getChildElementScope(copyElem)) { currentBindingContext =>
                  val currentNodeset = currentBindingContext.nodeset.asScala.toList
                  currentNodeset.nonEmpty option currentNodeset
                }(contextStack)
              }

            private def findLhhValue(lhhaElemOpt: Option[Element], required: Boolean): Option[LHHAValue] =
              lhhaElemOpt match {
                case Some(lhhaElem) =>
                  val elemScope = select1Control.getChildElementScope(lhhaElem)
                  val elemEffectiveId = getElementEffectiveId(lhhaElem)
                  val supportsHtml = select1Control.isFullAppearance // only support HTML when appearance is "full"

                  // FIXME: Would be good to do this check statically
                  val defaultToHTML = LHHAAnalysis.isHTML(lhhaElem)
                  val (lhhaValueOpt, containsHtml) =
                    getChildElementValue(container, elemEffectiveId, elemScope, lhhaElem, supportsHtml, defaultToHTML)

                  val trimmed = lhhaValueOpt flatMap (_.trimAllToOpt)

                  if (required)
                    LHHAValue(trimmed getOrElse "", containsHtml).some
                  else
                    trimmed map (LHHAValue(_, containsHtml))
                case None =>
                  if (required)
                    throw new ValidationException(
                      "`xf:item` or `xf:itemset` must contain an `xf:label` element",
                      select1Control.getLocationData
                    )
                  else
                    None
              }

            private def getAttributes(elem: Element): List[(QName, String)] =
              for {
                name   <- SelectionControlUtil.AttributesToPropagate
                value  = elem.attributeValue(name)
                if value ne null
                result <- findAttributeAVTValue(elem, name, value, getElementEffectiveId(elem))
              } yield
                result

            private def findAttributeAVTValue(
              itemChoiceItemsetElem : Element,
              attributeName         : QName,
              attributeValue        : String,
              elemEffectiveId       : String
            ): Option[(QName, String)] =
              if (! XFormsUtils.maybeAVT(attributeValue)) {
                Some(attributeName -> attributeValue)
              } else {
                val currentBindingContext = contextStack.getCurrentBindingContext
                val currentNodeset = currentBindingContext.nodeset
                if (! currentNodeset.isEmpty) {
                  val tempResult =
                    try {
                      XPathCache.evaluateAsAvt(
                        contextItems       = currentNodeset,
                        contextPosition    = currentBindingContext.position,
                        xpathString        = attributeValue,
                        namespaceMapping   = container.getNamespaceMappings(itemChoiceItemsetElem),
                        variableToValueMap = contextStack.getCurrentBindingContext.getInScopeVariables,
                        functionLibrary    = container.getContainingDocument.functionLibrary,
                        functionContext    = contextStack.getFunctionContext(elemEffectiveId),
                        baseURI            = null,
                        locationData       = itemChoiceItemsetElem.getData.asInstanceOf[LocationData],
                        reporter           = container.getContainingDocument.getRequestStats.getReporter
                      )
                    } catch {
                      case NonFatal(t) =>
                        XFormsError.handleNonFatalXPathError(container, t)
                        ""
                    }
                  Some(attributeName -> tempResult)
                } else {
                  None
                }
              }

            // Item level for the given item. If the stack is empty, the level is 0.
            private def getXPathItemLevel(itemToCheck: om.Item, stack: List[om.Item]): Int = {
              itemToCheck match {
                case nodeInfo: om.NodeInfo =>
                  // Only nodes can have ancestor relationship
                  var level = stack.size

                  stack.iterator foreach { currentItem =>
                    currentItem match {
                      case currentNode: om.NodeInfo if SaxonUtils.isFirstNodeAncestorOfSecondNode(currentNode, nodeInfo, includeSelf = false) =>
                        return level
                      case _ =>
                    }
                    level -= 1
                  }
                  level
                case _ =>
                  // If it's not a node, stay at current level
                  stack.size - 1
              }
            }
          },
          mutable = false
        )

        contextStack.setBinding(savedBindingContext)
        result
    }
  }

  private def getChildElementValue(
    container          : XBLContainer,
    sourceEffectiveId  : String,  // source effective id for id resolution
    scope              : Scope,
    childElement       : Element, // element to evaluate (`xf:label`, etc.)
    acceptHTML         : Boolean, // whether the result may contain HTML
    defaultHTML        : Boolean
  ): (Option[String], Boolean) = {
    val contextStack = container.getContextStack
    withBinding(childElement, sourceEffectiveId, scope) { _ =>
      val containsHTML = Array[Boolean](false)
      XFormsUtils.getElementValue(
        container,
        contextStack,
        sourceEffectiveId,
        childElement,
        acceptHTML,
        defaultHTML,
        containsHTML
      ) -> containsHTML.head
    }(contextStack)
  }
}