/**
 *  Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.events.{XXFormsBindingErrorEvent, XXFormsValueChangedEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget}
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.scaxon.SimplePath._
import org.w3c.dom.Node._
import StaticDataModel._


/**
 * Represent access to the data model via the NodeInfo abstraction.
 *
 * This covers setting and getting values.
 */
object DataModel {


  /**
   * Return the value of a bound item, whether a NodeInfo or an AtomicValue.
   *
   * NOTE: We used to return null for "disallowed" items, such as document nodes. However there doesn't seem to be
   * much of a drawback to just return the string value. Scenarios where this could happen:
   *
   * - bind to simple content element
   * - insert into that element
   * - manually run revalidation
   * - XFormsModelBinds gets node value for validation
   */
  def getValue(item: Item): String = item match {
    case null => null
    case item => item.getStringValue
  }

  /**
   * Set a value on the instance using a NodeInfo and a value.
   *
   * @param nodeInfo              element or attribute NodeInfo to update
   * @param newValue              value to set
   * @param onSuccess             function called if the value was set
   * @param onError               function called if the value was not set
   *
   * Return true if the value was set.
   */
  def setValue(
    nodeInfo  : NodeInfo,
    newValue  : String,
    onSuccess : () => Unit     = () => (),
    onError   : Reason => Unit = _ => ()
  ): Boolean = {

    assert(nodeInfo ne null)
    assert(newValue ne null)

    isWritableItem(nodeInfo) match {
      case Left(virtualNode) =>
        setValueForNode(virtualNode.getUnderlyingNode.asInstanceOf[dom.Node], newValue)
        onSuccess()
        true
      case Right(reason) =>
        onError(reason)
        false
    }
  }

  /**
   * Same as setValue but only attempts to set the value if the new value is different from the old value.
   *
   * Return true if the value was changed.
   */
  def setValueIfChanged(
    nodeInfo  : NodeInfo,
    newValue  : String,
    onSuccess : String => Unit = _ => (),
    onError   : Reason => Unit = _ => ()
  ): Boolean = {

    assert(nodeInfo ne null)
    assert(newValue ne null)

    val oldValue = getValue(nodeInfo)
    val doUpdate = oldValue != newValue

    // Do not require RRR / mark the instance dirty if the value hasn't actually changed
    doUpdate &&
      setValue(nodeInfo, newValue, () => onSuccess(oldValue), onError)
  }

  /**
   * Same as `setValueIfChanged` but with default error handling.
   *
   * Used by MIPs and when setting external values on controls.
   *
   * Return true if the value was changed.
   */
  def setValueIfChangedHandleErrors(
    eventTarget        : XFormsEventTarget,
    locationData       : LocationData,
    nodeInfo           : NodeInfo,
    valueToSet         : String,
    source             : String,
    isCalculate        : Boolean,
    collector          : XFormsEvent => Unit = Dispatch.dispatchEvent)(implicit
    containingDocument : XFormsContainingDocument,
    logger             : IndentedLogger
  ): Boolean = {

    assert(containingDocument ne null)
    assert(logger ne null)

    setValueIfChanged(
      nodeInfo  = nodeInfo,
      newValue  = valueToSet,
      onSuccess = logAndNotifyValueChange(source, nodeInfo, _, valueToSet, isCalculate, collector),
      onError   = reason => collector(new XXFormsBindingErrorEvent(eventTarget, locationData, reason))
    )
  }

  // Standard success behavior: log and notify
  def logAndNotifyValueChange(
    source             : String,
    nodeInfo           : NodeInfo,
    oldValue           : String,
    newValue           : String,
    isCalculate        : Boolean,
    collector          : XFormsEvent => Unit)(implicit
    containingDocument : XFormsContainingDocument,
    logger             : IndentedLogger
  ): Unit = {
    logValueChange(source, oldValue,  newValue, findInstanceEffectiveId(containingDocument, nodeInfo))
    notifyValueChange(containingDocument, nodeInfo, oldValue, newValue, isCalculate, collector)
  }

  private def findInstanceEffectiveId(containingDocument: XFormsContainingDocument, nodeInfo: NodeInfo) =
    containingDocument.instanceForNodeOpt(nodeInfo) map (_.getEffectiveId)

  private def logValueChange(
    source              : String,
    oldValue            : String,
    newValue            : String,
    instanceEffectiveId : Option[String])(implicit
    logger              : IndentedLogger
  ): Unit =
    if (logger.debugEnabled)
      logger.logDebug("xf:setvalue", "setting instance value", "source", source,
        "old value", oldValue, "new value", newValue,
        "instance", instanceEffectiveId getOrElse "N/A")

  private def notifyValueChange(
    containingDocument : XFormsContainingDocument,
    nodeInfo           : NodeInfo,
    oldValue           : String,
    newValue           : String,
    isCalculate        : Boolean,
    collector          : XFormsEvent => Unit
  ): Unit =
    containingDocument.instanceForNodeOpt(nodeInfo) match {
      case Some(modifiedInstance) =>
        // Tell the model about the value change
        modifiedInstance.markModified()
        modifiedInstance.model.markValueChange(nodeInfo, isCalculate)

        // Dispatch extension event to instance
        collector(new XXFormsValueChangedEvent(modifiedInstance, nodeInfo, oldValue, newValue))
      case None =>
        // Value modified is not in an instance
        // Q: Is the code below the right thing to do?
        containingDocument.controls.markDirtySinceLastRequest(true)
    }

  private def setValueForNode(node: dom.Node, newValue: String): Unit =
    node match {
      case element   : dom.Element                   => element.clearContent(); if (newValue.nonEmpty) element.setText(newValue)
      case attribute : dom.Attribute                 => attribute.setValue(newValue)
      case text      : dom.Text if newValue.nonEmpty => text.setText(newValue)
      case text      : dom.Text                      => text.getParent.remove(text)
      case pi        : dom.ProcessingInstruction     => pi.setText(newValue)
      case comment   : dom.Comment                   => comment.setText(newValue)
      // Should not happen as caller checks for isWritableItem()
      case _ => throw new IllegalStateException(s"Setting value on disallowed node type: ${dom.Node.nodeTypeName(node)}")
    }

  // Whether the item is an element node.
  def isElement(item: Item): Boolean = isNodeType(item, ELEMENT_NODE)

  // Whether the an item is a document node.
  def isDocument(item: Item): Boolean = isNodeType(item, DOCUMENT_NODE)

  private def isNodeType(item: Item, nodeType: Int) = item match {
    case nodeInfo: NodeInfo if nodeInfo.getNodeKind == nodeType => true
    case _ => false
  }

  // Return the given node's first child element
  def firstChildElement(node: NodeInfo): NodeInfo = {
    val iterator = node.iterateAxis(Axis.CHILD)
    var next = iterator.next()
    while (next ne null) {
      next match {
        case child: NodeInfo if child.getNodeKind == ELEMENT_NODE => return child
        case _ =>
      }
      next = iterator.next()
    }
    null
  }

  // Visit the given element, its attributes, and its children nodes recursively
  // NOTE: Because this can be a hot spot, this is written more Java-like. Make changes carefully.
  def visitElement(e: NodeInfo, visit: NodeInfo => Unit): Unit = {

    visit(e)

    locally {
      val iterator = e.iterateAxis(Axis.ATTRIBUTE)
      var next = iterator.next()
      while (next ne null) {
        // We know that iterateAxis(Axis.ATTRIBUTE) returns only NodeInfo
        visit(next.asInstanceOf[NodeInfo])
        next = iterator.next()
      }
    }

    locally {
      val iterator = e.iterateAxis(Axis.CHILD)
      var next = iterator.next()
      while (next ne null) {
        next match {
          case node: NodeInfo if node.getNodeKind == ELEMENT_NODE => visitElement(node, visit)
          case _ =>
        }
        next = iterator.next()
      }
    }
  }
}
