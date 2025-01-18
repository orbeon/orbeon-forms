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

import cats.syntax.option.*
import org.orbeon.datatypes.LocationData
import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{StaticXPath, XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsContextStackSupport.*
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.analysis.ElementAnalysis.findChildElem
import org.orbeon.oxf.xforms.analysis.controls.{LHHAAnalysis, SelectionControl, SelectionControlUtil, WithExpressionOrConstantTrait}
import org.orbeon.oxf.xforms.analysis.{ElemListener, ElementAnalysis, XPathErrorDetails}
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.XFormsControl.getEscapedHTMLValue
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.{XXFormsBindingErrorEvent, XXFormsXPathErrorEvent}
import org.orbeon.oxf.xforms.itemset.StaticItemsetSupport.isSelected
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.{SaxonUtils, XMLReceiver, XMLUtils}
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.{BindingErrorReason, XFormsCrossPlatformSupport, XFormsNames}
import org.xml.sax.SAXException

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal


object ItemsetSupport {

  def findSelectionControl(control: XFormsControl): Option[XFormsSelect1Control] =
    control match {
      case c: XFormsSelect1Control =>
        Some(c)
      case c: XFormsComponentControl if c.staticControl.commonBinding.modeSelection =>
        // Not the ideal solution, see https://github.com/orbeon/orbeon-forms/issues/1856
        ControlsIterator(c, includeSelf = false) collectFirst { case c: XFormsSelect1Control => c }
      case _ =>
        None
    }

  def getAttributeName(name: QName): String =
    if (name.namespace == Namespace.EmptyNamespace)
      name.localName
    else if (name.namespace == XFormsNames.XXFORMS_NAMESPACE)
      "xxforms-" + name.localName
    else
      throw new IllegalStateException("Invalid attribute on item: " + name.localName)

  def findMultipleItemValues(
    dataValue : Item.Value[om.NodeInfo],
    itemValue : Item.Value[om.Item]
  ): List[om.NodeInfo] =
    (dataValue, itemValue) match {
      case (Right(allDataItems), Right(firstItemXPathItem :: _)) =>
        allDataItems collect { case oneDataXPathItem if
          SaxonUtils.deepCompare(
            config                     = XPath.GlobalConfiguration,
            it1                        = Iterator.single(oneDataXPathItem),
            it2                        = Iterator.single(firstItemXPathItem),
            excludeWhitespaceTextNodes = false
          ) => oneDataXPathItem
        }
      case _ =>
        Nil
    }

  // Evaluate the itemset for a given `xf:select` or `xf:select1` control.
  def evaluateItemset(
    select1Control: XFormsSelect1Control,
    collector     : ErrorEventCollector
  ): Itemset = {

    val staticControl = select1Control.staticControl

    staticControl.staticItemset match {
      case Some(staticItemset) =>
        staticItemset
      case None =>

        def newEmptyItemset(staticControl: SelectionControl): Itemset =
          new Itemset(multiple = staticControl.isMultiple, hasCopy = staticControl.useCopy)

        // The idea is that if there is any failure in the evaluation of the itemset, we stop evaluating the itemset,
        // we report the first failure, and then we return an empty itemset, provided the original `collector` has not
        // opted to throw. If the body throws, then this throws.
        EventCollector.withFailFastCollector(
          "evaluating itemset",
          select1Control,
          collector,
          newEmptyItemset(staticControl)
        ) { failFastCollector =>

          val container         = select1Control.container
          val parentEffectiveId = select1Control.effectiveId
          val result            = newEmptyItemset(staticControl)

          // Set binding on this control, after saving the current context because the context stack must
          // remain unmodified.
          implicit val contextStack: XFormsContextStack = container.contextStack
          val savedBindingContext = contextStack.getCurrentBindingContext
          contextStack.setBinding(select1Control.bindingContext)

          // TODO: This visits all of the control's descendants. It should only visit the top-level item|itemset|choices elements.
          staticControl.visitDescendants(
            new ElemListener {

              private var position: Int = 0
              private var currentContainer: ItemContainer = result

              def startElement(elem: ElementAnalysis): Unit = {

                val domElem = elem.element

                domElem.getQName match {
                  case XFORMS_ITEM_QNAME =>

                    contextStack.pushBinding(domElem, getElementEffectiveId(parentEffectiveId, elem), elem.scope, select1Control, failFastCollector)

                    getValueOrCopyItemValue(elem) map (createValueNode(_, elem, position)) foreach { newItem =>
                      currentContainer.addChildItem(newItem)
                      position += 1
                    }

                  case XFORMS_ITEMSET_QNAME =>

                    contextStack.pushBinding(domElem, getElementEffectiveId(parentEffectiveId, elem), elem.scope, select1Control, failFastCollector)

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
                          levelAndStack = updatedLevelAndItemStack(levelAndStack, currentXPathItem) // mutates `currentContainer`!
                          currentContainer.addChildItem(
                            getValueOrCopyItemValue(elem) match {
                              case Some(newValue) => createValueNode(newValue, elem, position)
                              case None           => createChoiceNode(elem, position)
                            }
                          )
                          position += 1
                        }

                      }(contextStack)
                  case XFORMS_CHOICES_QNAME =>

                    contextStack.pushBinding(domElem, getElementEffectiveId(parentEffectiveId, elem), elem.scope, select1Control, failFastCollector)

                    if (findChildElem(elem, LABEL_QNAME).isDefined) {
                      val newItem = createChoiceNode(elem, position)
                      currentContainer.addChildItem(newItem)
                      position += 1
                      currentContainer = newItem
                    }
                  case _ =>
                }
              }

              def endElement(elem: ElementAnalysis): Unit = {

                val domElem = elem.element

                domElem.getQName match {
                  case XFORMS_ITEM_QNAME | XFORMS_ITEMSET_QNAME =>
                    contextStack.popBinding()
                  case XFORMS_CHOICES_QNAME =>
                    contextStack.popBinding()
                    if (findChildElem(elem, LABEL_QNAME).isDefined)
                      currentContainer = currentContainer.parent
                  case _ =>
                }
              }

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
                    } else /*if (newLevel > currentLevel)*/ {
                      // Going up one level, set new container as last added child
                      currentContainer = currentContainer.lastChild
                    }
                    newLevel
                  } else {
                    currentLevel
                  }
                (newLevel, currentXPathItem :: newItemStack)
              }

              private def createValueNode(value: Item.Value[om.Item], elem: ElementAnalysis, position: Int): Item.ValueNode =
                Item.ValueNode(
                  label      = findLhhValue(findChildElem(elem, LABEL_QNAME), required = true) getOrElse LHHAValue.Empty,
                  help       = findLhhValue(findChildElem(elem, HELP_QNAME),  required = false),
                  hint       = findLhhValue(findChildElem(elem, HINT_QNAME),  required = false),
                  value      = value,
                  attributes = getAttributes(elem)
                )(
                  position   = position
                )

              private def createChoiceNode(elem: ElementAnalysis, position: Int): Item.ChoiceNode =
                Item.ChoiceNode(
                  label      = findLhhValue(findChildElem(elem, LABEL_QNAME), required = true) getOrElse LHHAValue.Empty,
                  attributes = getAttributes(elem)
                )(
                  position   = position
                )

              private def getValueOrCopyItemValue(elem: ElementAnalysis): Option[Item.Value[om.Item]] = {

                def fromValueElem =
                  findChildElem(elem, XFORMS_VALUE_QNAME) map
                    (_.asInstanceOf[WithExpressionOrConstantTrait]) flatMap { valueElem =>

                      // Returns `None` if the result is `()`
                      val rawValue =
                        evaluateExpressionOrConstant(
                          childElem           = valueElem,
                          parentEffectiveId   = parentEffectiveId,
                          pushContextAndModel = true,
                          eventTarget         = select1Control,
                          collector           = failFastCollector
                        )

                      // For multiple selection:
                      //
                      // - trim the value
                      // - if the value is blank, it can't be used and the item is excluded
                      //
                      if (staticControl.isMultiple)
                        rawValue flatMap (_.trimAllToOpt)
                      else
                        rawValue

                  } map
                    Left.apply

                def fromCopyElem =
                  findChildElem(elem, XFORMS_COPY_QNAME)       flatMap
                    getCopyValue                               filter
                    (_.nonEmpty || ! staticControl.isMultiple) map
                    Right.apply

                fromValueElem orElse fromCopyElem
              }

              // Return `None` if:
              //
              // - there is no `ref` attribute
              // - there is an XPath error
              // - the context item is missing
              //
              private def getCopyValue(copyElem: ElementAnalysis): Option[List[om.Item]] =
                copyElem.element.attributeValueOpt(XFormsNames.REF_QNAME) flatMap { _ =>

                  val sourceEffectiveId = getElementEffectiveId(parentEffectiveId, copyElem)

                  withBinding(copyElem.element, sourceEffectiveId, copyElem.scope, select1Control, failFastCollector) { currentBindingContext =>
                    val currentNodeset = currentBindingContext.nodeset.asScala.toList
                    currentNodeset.nonEmpty option currentNodeset
                  }(contextStack)
                }

              private def findLhhValue(lhhaElemOpt: Option[ElementAnalysis], required: Boolean): Option[LHHAValue] =
                lhhaElemOpt match {
                  case Some(lhhaElem: LHHAAnalysis) =>

                    val lhhaValueOpt =
                      evaluateExpressionOrConstant(
                        childElem           = lhhaElem,
                        parentEffectiveId   = parentEffectiveId,
                        pushContextAndModel = true,
                        eventTarget         = select1Control,
                        collector           = failFastCollector
                      )

                    val containsHtml = lhhaElem.containsHTML

                    // XXX TODO
  //                  val supportsHtml = select1Control.isFullAppearance // only support HTML when appearance is "full"

                    val trimmed = lhhaValueOpt flatMap (_.trimAllToOpt)

                    if (required)
                      LHHAValue(trimmed getOrElse "", containsHtml).some
                    else
                      trimmed map (LHHAValue(_, containsHtml))
                  case _ =>
                    if (required)
                      collector(
                        new XXFormsBindingErrorEvent(
                          target          = select1Control,
                          locationDataOpt = Option(staticControl.locationData),
                          reason          = BindingErrorReason.Other("`xf:item` or `xf:itemset` must contain an `xf:label` element")
                        )
                      )
                    None
                }

              private def getAttributes(elem: ElementAnalysis): List[(QName, String)] =
                for {
                  name   <- SelectionControlUtil.AttributesToPropagate
                  value  = elem.element.attributeValue(name)
                  if value ne null
                  result <- findAttributeAVTValue(elem, name, value, getElementEffectiveId(parentEffectiveId, elem))
                } yield
                  result

              private def findAttributeAVTValue(
                itemChoiceItemsetElem : ElementAnalysis,
                attributeName         : QName,
                attributeValue        : String,
                elemEffectiveId       : String
              ): Option[(QName, String)] =
                if (! XMLUtils.maybeAVT(attributeValue)) {
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
                          namespaceMapping   = itemChoiceItemsetElem.namespaceMapping,
                          variableToValueMap = contextStack.getCurrentBindingContext.getInScopeVariables,
                          functionLibrary    = container.containingDocument.functionLibrary,
                          functionContext    = contextStack.getFunctionContext(elemEffectiveId),
                          baseURI            = null,
                          locationData       = itemChoiceItemsetElem.element.getData.asInstanceOf[LocationData],
                          reporter           = container.containingDocument.getRequestStats.getReporter
                        )
                      } catch {
                        case NonFatal(t) =>
                          failFastCollector(
                            new XXFormsXPathErrorEvent(
                              target         = select1Control,
                              expression     = attributeValue,
                              details        = XPathErrorDetails.ForAttribute(attributeName.localName),
                              message        = XFormsCrossPlatformSupport.getRootThrowable(t).getMessage,
                              throwable      = t
                            )
                          )
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
            }
          )

          contextStack.setBinding(savedBindingContext)

          result
        }
    }
  }

  // xxx ItemsetSupport, XFS1Handler
  def streamAsHTML(lhhaValue: LHHAValue)(implicit xmlReceiver: XMLReceiver): Unit =
    if (lhhaValue.label.nonAllBlank) {
      if (lhhaValue.isHTML)
        XFormsCrossPlatformSupport.streamHTMLFragment(lhhaValue.label, "")
      else
        text(lhhaValue.label)
    }

  // xxx XFS1Control
  def htmlValue(lhhaValue: LHHAValue): String =
    if (lhhaValue.isHTML)
      getEscapedHTMLValue(lhhaValue.label)
    else
      lhhaValue.label.escapeXmlMinimal

  private def javaScriptValue(lhhaValue: LHHAValue): String =
    htmlValue(lhhaValue).escapeJavaScript

  private def javaScriptValue(item: Item.ValueNode, encode: Boolean): String =
    item.externalValue(encode).escapeJavaScript

  // Return the list of items as a JSON tree with hierarchical information
  def asJSON(
    itemset                    : Itemset,
    controlValue               : Option[(Item.Value[om.NodeInfo], om.NodeInfo => Boolean)],
    encode                     : Boolean,
    excludeWhitespaceTextNodes : Boolean,
    locationData               : LocationData
  ): String = {

    val sb = new StringBuilder
    // Array of top-level items
    sb.append("[")
    try {
      itemset.visit(new ItemsetListener {

        def startItem(itemNode: ItemNode, first: Boolean): Unit = {
          if (! first)
            sb.append(',')

          // Start object
          sb.append("{")

          // Item LHH and value
          sb.append(""""label":"""")
          sb.append(javaScriptValue(itemNode.label))
          sb.append('"')

          itemNode match {
            case item: Item.ValueNode =>
              item.help map javaScriptValue foreach { h =>
                sb.append(""","help":"""")
                sb.append(h)
                sb.append('"')
              }
              item.hint map javaScriptValue foreach { h =>
                sb.append(""","hint":"""")
                sb.append(h)
                sb.append('"')
              }
              sb.append(""","value":"""")
              sb.append(javaScriptValue(item, encode))
              sb.append('"')
            case _ =>
          }

          // Item attributes if any
          val attributes = itemNode.attributes
          if (attributes.nonEmpty) {
            sb.append(""","attributes":{""")

            val nameValues =
              for {
                (name, value) <- attributes
                escapedName   = getAttributeName(name).escapeJavaScript
                escapedValue  = value.escapeJavaScript
              } yield
                s""""$escapedName":"$escapedValue""""

            sb.append(nameValues mkString ",")

            sb.append('}')
          }

          // Handle selection
          itemNode match {
            case item: Item.ValueNode if controlValue exists { case (dataValue, compareAtt) =>
              isSelected(itemset.multiple, dataValue, item.value, compareAtt, excludeWhitespaceTextNodes)
            } => sb.append(""","selected":true""")
            case _ =>
          }

          // Start array of children items
          if (itemNode.hasChildren)
            sb.append(""","children":[""")
        }

        def endItem(itemNode: ItemNode): Unit = {
          // End array of children items
          if (itemNode.hasChildren)
            sb.append(']')

          // End object
          sb.append("}")
        }

        def startLevel(itemNode: ItemNode): Unit = ()
        def endLevel(): Unit = ()
      })
    } catch {
      case e: SAXException =>
        throw new ValidationException("Error while creating itemset tree", e, locationData)
    }
    sb.append("]")

    sb.toString
  }

  // Return the list of items as an XML tree
  def asXML(
    itemset                    : Itemset,
    controlValue               : Option[(Item.Value[om.NodeInfo], om.NodeInfo => Boolean)],
    excludeWhitespaceTextNodes : Boolean
  ): DocumentNodeInfoType = {

    val (identity, result) = StaticXPath.newTinyTreeReceiver

    implicit val xmlReceiver: XMLReceiver = identity

    withDocument {
      withElement("itemset") {
        if (itemset.hasChildren) {
          itemset.visit(new ItemsetListener {

            def startLevel(itemNode: ItemNode): Unit =
              openElement("choices")

            def endLevel(): Unit =
              closeElement("choices")

            def startItem(itemNode: ItemNode, first: Boolean): Unit = {

              val itemAttributes =
                itemNode match {
                  case item: Item.ValueNode if controlValue exists { case (dataValue, compareAtt) =>
                    isSelected(itemset.multiple, dataValue, item.value, compareAtt, excludeWhitespaceTextNodes)
                  } =>
                    List("selected" -> "true")
                  case _ =>
                    Nil
                }

              // TODO: Item attributes if any

              openElement("item", atts = itemAttributes)

              withElement("label") {
                streamAsHTML(itemNode.label)
              }

              itemNode match {
                case item: Item.ValueNode =>
                  item.help foreach { h =>
                    withElement("help") {
                      streamAsHTML(h)
                    }
                  }

                  item.hint foreach { h =>
                    withElement("hint") {
                      streamAsHTML(h)
                    }
                  }

                  val itemExternalValue = item.externalValue(encode = false)
                  withElement("value") {
                    if (itemExternalValue.nonEmpty)
                      text(itemExternalValue)
                  }
                case _ =>
              }
            }

            def endItem(itemNode: ItemNode): Unit =
              closeElement("item")
          })
        }
      }
    }

    result()
  }
}