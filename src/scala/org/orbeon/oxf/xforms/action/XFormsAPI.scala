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
import java.lang.IllegalStateException
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.event.{XFormsEventObserver, XFormsEvent}
import org.dom4j.{Element, QName}

object XFormsAPI {

    // Dynamically set action context
    val actionContext = new DynamicVariable[Option[XFormsActionInterpreter]](None)

    // Helper for Java side of things
    def scalaActionJava(actionInterpreter: XFormsActionInterpreter, event: XFormsEvent, eventObserver: XFormsEventObserver, eventHandlerElement: Element) =
        scalaAction(actionInterpreter) {
            actionInterpreter.runAction(event, eventObserver, eventHandlerElement)
        }

    // Every block of action must be run within this
    def scalaAction(actionInterpreter: XFormsActionInterpreter)(body: => Any) {
        actionContext.withValue(Some(actionInterpreter)) {
            body
        }
    }

    // Setvalue
    // @return the node whose value was set, if any
    def setvalue(ref: Seq[NodeInfo], value: String) = {
        if (ref nonEmpty) {
            val action = actionContext.value
            XFormsSetvalueAction.doSetValue(action map(_.getContainingDocument) orNull, action map (_.getIndentedLogger) orNull,
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

            XFormsInsertAction.doInsert(action map(_.getContainingDocument) orNull, action map (_.getIndentedLogger) orNull, positionAttribute,
                collectionToUpdate.asJava, into.headOption.orNull, origin.asJava, collectionToUpdate.size, doClone = true, doDispatch = true).asInstanceOf[JList[T]].asScala
        } else
            Seq()
    }

    // Delete
    def delete(ref: Seq[NodeInfo]): Seq[NodeInfo] = {

        val action = actionContext.value

        val deleteInfos = XFormsDeleteAction.doDelete(action map(_.getContainingDocument) orNull, action map (_.getIndentedLogger) orNull, ref.asJava, -1, doDispatch = true)
        deleteInfos.asScala map (_.nodeInfo)
    }

    // Rename an element or attribute node
    // NOTE: This should be implemented as a core XForms action (see also XQuery updates)
    def rename(nodeInfo: NodeInfo, oldName: String, newName: String) {

        require(nodeInfo ne null)
        require(Set(ELEMENT_NODE, ATTRIBUTE_NODE)(nodeInfo.getNodeKind.toShort))

        if (oldName != newName) {
            val newNodeInfo = nodeInfo.getNodeKind match {
                case ELEMENT_NODE => elementInfo(newName, (nodeInfo \@ @*) ++ (nodeInfo \ node))
                case ATTRIBUTE_NODE =>  attributeInfo(newName, attValueOption(nodeInfo).get)
                case _ => throw new IllegalArgumentException
            }

            insert(into = nodeInfo.parent.get, after = nodeInfo, origin = newNodeInfo)
            delete(nodeInfo)
        }
    }

    // Move the given element before another element
    def moveElementBefore(element: NodeInfo, other: NodeInfo) = {
        val inserted = insert(into = element.parent.get, before = other, origin = element)
        delete(element)
        inserted.head
    }

    // Move the given element after another element
    def moveElementAfter(element: NodeInfo, other: NodeInfo) = {
        val inserted = insert(into = element.parent.get, after = other, origin = element)
        delete(element)
        inserted.head
    }

    // Move the given element into another element as the last element
    def moveElementIntoAsLast(element: NodeInfo, other: NodeInfo) = {
        val inserted = insert(into = other, after = other \ *, origin = element)
        delete(element)
        inserted.head
    }

    // Set an attribute value, creating it if missing, updating it if present
    // NOTE: This should be implemented as an optimization of the XForms insert action.
    // @return the new or existing attribute node
    // NOTE: Would be nice to return attribute (new or existing), but doInsert() is not always able to wrap the inserted
    // nodes.
    def ensureAttribute(element: NodeInfo, attName: QName, value: String): Unit =
        element \@ attName match {
            case Seq() => insert(into = element, origin = attributeInfo(attName, value))
            case Seq(att, _*) => setvalue(att, value)
        }

    // Return an instance's root element in the current action context
    def instanceRoot(staticId: String, searchAncestors: Boolean = false): Option[NodeInfo] = {

        assert(actionContext.value.isDefined)

        def ancestorXBLContainers = {
            def recurse(container: XBLContainer): List[XBLContainer] = container :: (container.getParentXBLContainer match {
                case parent: XBLContainer => recurse(parent)
                case _ => Nil
            })

            recurse(actionContext.value.get.getXBLContainer)
        }

        val containersToSearch =
            if (searchAncestors) ancestorXBLContainers else List(actionContext.value.get.getXBLContainer)

        containersToSearch map
                (_.findInstance(staticId)) find
                    (_ ne null) map
                        (_.getInstanceRootElementInfo)
    }

    // Return a model
    // TODO: This searches only to-level models, find a better way
    def model(modelId: String) =
        // NOTE: This search is not very efficient, but this allows mocking in tests, where getObjectByEffectiveId causes issues
        containingDocument.getModels.asScala.find(_.getId == modelId)

    // Return the containing document
    def containingDocument = { assert(actionContext.value.isDefined); actionContext.value.get.getContainingDocument }
    
    def context[T](xpath: String)(body: => T): T =
        throw new IllegalStateException("NIY")

    def context[T](item: Item)(body: => T): T =
        throw new IllegalStateException("NIY")

    def event[T](attributeName: String): Seq[Item] =
        throw new IllegalStateException("NIY")
}   