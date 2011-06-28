/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action

import actions.{XFormsDeleteAction, XFormsInsertAction, XFormsSetvalueAction}
import util.DynamicVariable
import collection.JavaConverters._
import org.orbeon.saxon.om._
import java.util.{List => JList}
import org.orbeon.scaxon.XML._
import org.w3c.dom.Node.{ELEMENT_NODE, ATTRIBUTE_NODE}
import org.dom4j.QName
import java.lang.IllegalStateException

object XFormsAPI {

    // Dynamically set action context
    val actionContext = new DynamicVariable[XFormsActionInterpreter](null)

    // Every block of action must be run within this
    def scalaAction(actionInterpreter: XFormsActionInterpreter)(body: => Any) {
        actionContext.withValue(actionInterpreter) {
            body
        }
    }

    // Setvalue
    // @return the node whose value was set, if any
    def setvalue(ref: Seq[NodeInfo], value: String) = {
        if (ref nonEmpty) {
            val action = actionContext.value
            XFormsSetvalueAction.doSetValue(action.getContainingDocument, action.getIndentedLogger,
                null /* TODO */, ref.head, value, null, "scala setvalue", false)
            Some(ref.head)
        } else
            None
    }

    // Insert
    // @return the inserted nodes
    def insert[T](into: Seq[NodeInfo], origin: Seq[T], after: Seq[NodeInfo] = null, before: Seq[NodeInfo] = null): Seq[T] = {

        if (into.nonEmpty) {
            val action = actionContext.value

            val (positionAttribute, collectionToUpdate) =
                if (before ne null)
                    ("before", before)
                else if (after ne null)
                    ("after", after)
                else
                    ("after", Seq())

            XFormsInsertAction.doInsert(action.getContainingDocument, action.getIndentedLogger, positionAttribute,
                collectionToUpdate.asJava, into.headOption.orNull, origin.asJava, collectionToUpdate.size, doClone = true, doDispatch = true).asInstanceOf[JList[T]].asScala
        } else
            Seq()
    }

    // Delete
    def delete(ref: Seq[NodeInfo]): Seq[NodeInfo] = {

        val action = actionContext.value

        val deleteInfos = XFormsDeleteAction.doDelete(action.getContainingDocument, action.getIndentedLogger, ref.asJava, -1, doDispatch = true)
        deleteInfos.asScala map (_.nodeInfo)
    }

    // Rename an element or attribute node
    // NOTE: This should be implemented as a core XForms action (see also XQuery updates)
    def rename(nodeInfo: NodeInfo, oldName: String, newName: String) {

        require(nodeInfo ne null)
        require(Set(ELEMENT_NODE, ATTRIBUTE_NODE)(nodeInfo.getNodeKind.toShort))

        if (oldName != newName) {
            val newNodeInfo = nodeInfo.getNodeKind match {
                case ELEMENT_NODE => elementInfo(newName, nodeInfo \ *) // TODO: This only copies over the children elements. Must copy all nodes!
                case ATTRIBUTE_NODE =>  attributeInfo(newName, attValueOption(nodeInfo).get)
                case _ => throw new IllegalArgumentException
            }

            insert(into = nodeInfo.parent.get, after = nodeInfo, origin = newNodeInfo)
            delete(nodeInfo)
        }
    }

    // Move the given element before another element
    def moveElementBefore(element: NodeInfo, other: NodeInfo) {
        insert(into = element.parent.get, before = other, origin = element)
        delete(element)
    }

    // Move the given element after another element
    def moveElementAfter(element: NodeInfo, other: NodeInfo) {
        insert(into = element.parent.get, after = other, origin = element)
        delete(element)
    }

    // Set an attribute value, creating it if missing, updating it if presenty
    // NOTE: This should be implemented as an optimization of the XForms insert action.
    // @return the new or existing attribute node
    def ensureAttribute(element: NodeInfo, attName: String, value: String): NodeInfo =
        element \@ attName match {
            case Seq() => insert(into = element, origin = attributeInfo(attName, value)).head
            case Seq(att, _*) => setvalue(att, value).get
        }

    // TODO: don't duplicate code above
    def ensureAttribute(element: NodeInfo, attName: QName, value: String): NodeInfo =
        element \@ attName match {
            case Seq() => insert(into = element, origin = attributeInfoByQName(attName, value)).head
            case Seq(att, _*) => setvalue(att, value).get
        }

    // Return the instance's root element
    def instanceRoot(id: String): NodeInfo = {
        val action = actionContext.value
        val functionContext = action.getOuterFunctionContext

        try functionContext.getModel.getInstance(id).getInstanceRootElementInfo
        finally action.getContextStack.returnFunctionContext()
    }

    // Return a model
    def model(modelId: String) = {
        val action = actionContext.value
        val document = action.getContainingDocument

        // NOTE: This search is not very efficient, but this allows mocking in tests, where getObjectByEffectiveId causes issues
        document.getModels.asScala.find(_.getId == modelId)
    }
    
    def context[T](xpath: String)(body: => T): T =
        throw new IllegalStateException("NIY")

    def context[T](item: Item)(body: => T): T =
        throw new IllegalStateException("NIY")

    def event[T](attributeName: String): Seq[Item] =
        throw new IllegalStateException("NIY")
}