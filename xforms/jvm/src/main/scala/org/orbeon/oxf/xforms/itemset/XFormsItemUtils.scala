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

import org.apache.commons.lang3
import org.orbeon.dom.{Element, QName, Text}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{StringUtils, XPathCache}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.{LHHAAnalysis, SelectionControlUtil}
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{LHHAValue, XFormsSingleNodeControl}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData}
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsId

import scala.util.control.NonFatal

// Utilities to deal with items and itemsets
object XFormsItemUtils {

  /**
    * Return whether a select control's value is selected given an item value.
    *
    * @param isMultiple   whether multiple selection is allowed
    * @param controlValue current value of the control (to determine selected item) or null
    * @param itemValue    item value to check
    * @return true is selected, false otherwise
    */
  def isSelected(isMultiple: Boolean, controlValue: String, itemValue: String): Boolean = {
    if (controlValue eq null) {
      // TODO: Clarify what it means: control non-relevant? Template?
      false
    } else {
      if (isMultiple) {
        // Trim for select only
        val trimmedControlValue = StringUtils.trimAllToEmpty(controlValue)
        val trimmedItemValue    = StringUtils.trimAllToEmpty(itemValue)// TODO: maybe this should be trimmed in the itemset in the first place

        if (trimmedControlValue == "") {
          // Special case of empty string: check the item that has empty string if any
          trimmedItemValue == ""
        } else {
          // Case of multiple tokens
          trimmedControlValue.splitTo[scala.Iterator]() contains trimmedItemValue
        }
      } else {
        // Do exact string comparison for select1
        controlValue == itemValue
      }
    }
  }

  /**
    * Evaluate the itemset for a given xf:select or xf:select1 control.
    *
    * @param select1Control control to evaluate
    * @return Itemset
    */
  def evaluateItemset(select1Control: XFormsSelect1Control): Itemset = {

    val staticControl = select1Control.staticControl

    staticControl.staticItemset match {
      case Some(staticItemset) ⇒
        staticItemset
      case None ⇒

        val container = select1Control.container
        val result = new Itemset(multiple = staticControl.isMultiple)

        // Set binding on this control, after saving the current context because the context stack must
        // remain unmodified.
        val contextStack = container.getContextStack
        val savedBindingContext = contextStack.getCurrentBindingContext
        contextStack.setBinding(select1Control.bindingContext)

        // TODO: This visits all of the control's descendants. It should only visit the top-level item|itemset|choices elements.
        Dom4jUtils.visitSubtree(
          select1Control.element, new Dom4jUtils.VisitorListener() {

            private var position: Int = 0
            private var currentContainer: ItemContainer = result

            private def getElementEffectiveId(elem: Element): String =
              XFormsId.getRelatedEffectiveId(select1Control.getEffectiveId, XFormsUtils.getElementId(elem))

            def startElement(elem: Element): Unit = {

              elem.getQName match {
                case XFORMS_ITEM_QNAME ⇒

                  contextStack.pushBinding(elem, getElementEffectiveId(elem), select1Control.getChildElementScope(elem))

                  currentContainer.addChildItem(
                    Item(
                      label      = getLabelValue(elem.element(LABEL_QNAME), required = true).orNull,
                      help       = getLabelValue(elem.element(HELP_QNAME),  required = false),
                      hint       = getLabelValue(elem.element(HINT_QNAME),  required = false),
                      value      = lang3.StringUtils.defaultString(getValueValueOrNull(elem.element(XFORMS_VALUE_QNAME))),
                      attributes = getAttributes(elem)
                    )(
                      position   = position
                    )
                  )
                  position += 1

                case XFORMS_ITEMSET_QNAME ⇒

                  contextStack.pushBinding(elem, getElementEffectiveId(elem), select1Control.getChildElementScope(elem))

                  val currentBindingContext = contextStack.getCurrentBindingContext

                  val currentNodeset = currentBindingContext.nodeset

                  // Node stack tracks the relative position of the current node wrt ancestor nodes
                  var itemStack: List[om.Item] = Nil

                  var currentLevel: Int = 0

                  for (currentPosition ← 1 to currentNodeset.size) {

                    contextStack.pushIteration(currentPosition)

                    val currentItem = currentNodeset.get(currentPosition - 1)

                    // Handle children of xf:itemset

                    // We support relevance of items as an extension to XForms

                    // NOTE: If a node is non-relevant, all its descendants will be non-relevant as
                    // well. If a node is non-relevant, it should be as if it had not even been part of
                    // the nodeset.
                    if (XFormsSingleNodeControl.isRelevantItem(currentItem)) {

                      // Update stack and containers
                      if (itemStack.nonEmpty) {
                        val newLevel = getItemLevel(currentItem, itemStack)
                        if (newLevel == currentLevel) {
                          //  We are staying at the same level, pop old item
                          itemStack = itemStack.tail
                        } else if (newLevel < currentLevel) {
                          //  We are going down one or more levels
                          itemStack = itemStack.tail
                          for (_ ← newLevel until currentLevel) {
                            itemStack = itemStack.tail
                            currentContainer = currentContainer.parent
                          }
                        } else if (newLevel > currentLevel) {
                          // Going up one level, set new container as last added child
                          currentContainer = currentContainer.lastChild
                        }
                        currentLevel = newLevel
                      }

                      val valueOrCopyElementOpt =
                        Option(elem.element(XFORMS_VALUE_QNAME)) orElse Option(elem.element(XFORMS_COPY_QNAME))

                      valueOrCopyElementOpt match {
                        case Some(valueElem) if valueElem.getQName == XFORMS_VALUE_QNAME ⇒
                          currentContainer.addChildItem(
                            Item(
                              label      = getLabelValue(elem.element(LABEL_QNAME), required = true).orNull,
                              help       = getLabelValue(elem.element(HELP_QNAME),  required = false),
                              hint       = getLabelValue(elem.element(HINT_QNAME),  required = false),
                              value      = getValueValueOrNull(valueElem), // NOTE: can be null if evaluation failed,
                              attributes = getAttributes(elem)
                            )(
                              position   = position
                            )
                          )
                          position += 1
                        case Some(copyElem) if copyElem.getQName == XFORMS_COPY_QNAME ⇒
                          throw new ValidationException("xf:copy is not yet supported.", select1Control.getLocationData)
                        case _ ⇒
                          throw new ValidationException("xf:itemset element must contain one xf:value or one xf:copy element.", select1Control.getLocationData)
                      }

                      itemStack ::= currentItem
                    }

                    contextStack.popBinding()
                  }
                case XFORMS_CHOICES_QNAME ⇒
                  contextStack.pushBinding(elem, getElementEffectiveId(elem), select1Control.getChildElementScope(elem))
                  val labelElem = elem.element(LABEL_QNAME)
                  if (labelElem ne null) {
                    val newContainer = Item(
                      label      = getLabelValue(labelElem, required = true).orNull, // NOTE: returned label can be null in some cases
                      help       = None,
                      hint       = None,
                      value      = null,
                      attributes = getAttributes(elem)
                    )(
                      position   = position
                    )
                    currentContainer.addChildItem(newContainer)
                    currentContainer = newContainer

                    position += 1
                  }
                case _ ⇒
              }
            }

            def endElement(elem: Element): Unit =
              elem.getQName match {
                case XFORMS_ITEM_QNAME ⇒
                  contextStack.popBinding()
                case  XFORMS_ITEMSET_QNAME ⇒
                  contextStack.popBinding()
                case XFORMS_CHOICES_QNAME ⇒
                  contextStack.popBinding()
                  val labelElement = elem.element(LABEL_QNAME)
                  if (labelElement ne null)
                    currentContainer = currentContainer.parent
                case _ ⇒
              }

            def text(text: Text) = ()

            private def getValueValueOrNull(valueElem: Element): String = {

              if (valueElem eq null)
                throw new ValidationException("xf:item or xf:itemset must contain an xf:value element.", select1Control.getLocationData)

              val elemScope = select1Control.getChildElementScope(valueElem)
              val elemEffectiveId = getElementEffectiveId(valueElem)

              XFormsUtils.getChildElementValue(container, elemEffectiveId, elemScope, valueElem, false, false, null)
            }

            private def getLabelValue(labelElem: Element, required: Boolean): Option[LHHAValue] = {

              if (required && (labelElem eq null))
                throw new ValidationException("xf:item or xf:itemset must contain an xf:label element.", select1Control.getLocationData)

              if (labelElem eq null)
                return None

              val elemScope = select1Control.getChildElementScope(labelElem)
              val elemEffectiveId = getElementEffectiveId(labelElem)
              val supportsHTML = select1Control.isFullAppearance // Only support HTML when appearance is "full"
              val containsHTML = Array[Boolean](false)

              // FIXME: Would be good to do this check statically
              val defaultToHTML = LHHAAnalysis.isHTML(labelElem)
              val label = XFormsUtils.getChildElementValue(container, elemEffectiveId, elemScope, labelElem, supportsHTML, defaultToHTML, containsHTML)

              if (required)
                Some(LHHAValue(StringUtils.trimAllToEmpty(label), containsHTML(0)))
              else
                StringUtils.trimAllToOpt(label) map (LHHAValue(_, containsHTML(0)))
            }

            private def getAttributes(elem: Element): List[(QName, String)] =
              for {
                name   ← SelectionControlUtil.AttributesToPropagate
                value  = elem.attributeValue(name)
                if value ne null
                result ← findAttributeAVTValue(elem, name, value, getElementEffectiveId(elem))
              } yield
                result

            private def findAttributeAVTValue(
              itemChoiceItemsetElem : Element,
              attributeName         : QName,
              attributeValue        : String,
              elemEffectiveId       : String
            ): Option[(QName, String)] =
              if (! XFormsUtils.maybeAVT(attributeValue)) {
                Some(attributeName → attributeValue)
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
                        functionLibrary    = container.getContainingDocument.getFunctionLibrary,
                        functionContext    = contextStack.getFunctionContext(elemEffectiveId),
                        baseURI            = null,
                        locationData       = itemChoiceItemsetElem.getData.asInstanceOf[LocationData],
                        reporter           = container.getContainingDocument.getRequestStats.getReporter
                      )
                    } catch {
                      case NonFatal(t) ⇒
                        XFormsError.handleNonFatalXPathError(container, t)
                        ""
                    }
                  Some(attributeName → tempResult)
                } else {
                  None
                }
              }

            // Item level for the given item. If the stack is empty, the level is 0.
            private def getItemLevel(itemToCheck: om.Item, stack: List[om.Item]): Int = {
              itemToCheck match {
                case nodeInfo: om.NodeInfo ⇒
                  // Only nodes can have ancestor relationship
                  var level = stack.size

                  stack.iterator foreach { currentItem ⇒
                    currentItem match {
                      case currentNode: om.NodeInfo if SaxonUtils.isFirstNodeAncestorOfSecondNode(currentNode, nodeInfo, includeSelf = false) ⇒
                        return level
                      case _ ⇒
                    }
                    level -= 1
                  }
                  level
                case _ ⇒
                  // If it's not a node, stay at current level
                  stack.size - 1
              }
            }
          }
        )

        contextStack.setBinding(savedBindingContext)
        result.pruneNonRelevantChildren()
        result
    }
  }
}