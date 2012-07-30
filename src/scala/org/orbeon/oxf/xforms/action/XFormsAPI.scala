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

import actions.{XFormsSetindexAction, XFormsDeleteAction, XFormsInsertAction}
import collection.JavaConverters._
import org.orbeon.saxon.om._
import java.util.{List ⇒ JList}
import org.orbeon.scaxon.XML._
import org.w3c.dom.Node.{ELEMENT_NODE, ATTRIBUTE_NODE}
import org.orbeon.oxf.xforms.xbl.XBLContainer

import org.orbeon.oxf.util.DynamicVariable
import org.orbeon.oxf.xforms.model.DataModel
import org.dom4j.QName

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsContainingDocument

object XFormsAPI {

    // Dynamically set context
    val containingDocumentDyn = new DynamicVariable[XFormsContainingDocument]
    val actionInterpreterDyn = new DynamicVariable[XFormsActionInterpreter]

    // Every block of action must be run within this
    def withScalaAction(interpreter: XFormsActionInterpreter)(body: ⇒ Any) {
        actionInterpreterDyn.withValue(interpreter) {
            body
        }
    }

    // For Java callers
    def withContainingDocumentJava(containingDocument: XFormsContainingDocument, runnable: Runnable) =
        withContainingDocument(containingDocument) {
            runnable.run()
        }

    // Every block of action must be run within this
    def withContainingDocument(containingDocument: XFormsContainingDocument)(body: ⇒ Any) {
        containingDocumentDyn.withValue(containingDocument) {
            body
        }
    }

    // Setvalue
    // @return the node whose value was set, if any
    def setvalue(ref: Seq[NodeInfo], value: String) = {
        if (ref nonEmpty) {
            val nodeInfo = ref.head

            def onSuccess(oldValue: String): Unit =
                for {
                    action ← actionInterpreterDyn.value
                    containingDocument = action.containingDocument
                    indentedLogger = action.indentedLogger
                } yield
                    DataModel.logAndNotifyValueChange(containingDocument, indentedLogger, "scala setvalue", nodeInfo, oldValue, value, isCalculate = false)

            DataModel.setValueIfChanged(nodeInfo, value, onSuccess)

            Some(nodeInfo)
        } else
            None
    }

    // Setindex
    // @return:
    //
    // - None        if the control is not found
    // - Some(0)     if the control is non-relevant or doesn't have any iterations
    // - Some(index) otherwise, where index is the control's new index
    def setindex(repeatStaticId: String, index: Int) =
        actionInterpreterDyn.value map
            { interpreter ⇒ XFormsSetindexAction.executeSetindexAction(interpreter, interpreter.outerActionElement, repeatStaticId, index) } collect
                { case newIndex if newIndex >= 0 ⇒ newIndex }

    // Insert
    // @return the inserted nodes
    def insert[T](origin: Seq[T], into: Seq[NodeInfo] = Seq(), after: Seq[NodeInfo] = Seq(), before: Seq[NodeInfo] = Seq()): Seq[T] = {

        if (origin.nonEmpty && (into.nonEmpty || after.nonEmpty || before.nonEmpty)) {
            val action = actionInterpreterDyn.value

            val (positionAttribute, collectionToUpdate) =
                if (before.nonEmpty)
                    ("before", before)
                else
                    ("after", after)

            XFormsInsertAction.doInsert(
                action map (_.containingDocument) orNull,
                action map (_.indentedLogger) orNull,
                positionAttribute,
                collectionToUpdate.asJava,
                into.headOption.orNull,
                origin.asJava,
                collectionToUpdate.size,
                doClone = true,
                doDispatch = true).asInstanceOf[JList[T]].asScala
        } else
            Seq()
    }

    // Delete
    def delete(ref: Seq[NodeInfo]): Seq[NodeInfo] = {

        val action = actionInterpreterDyn.value

        val deleteInfos = XFormsDeleteAction.doDelete(action map (_.containingDocument) orNull, action map (_.indentedLogger) orNull, ref.asJava, -1, doDispatch = true)
        deleteInfos.asScala map (_.nodeInfo)
    }

    // Rename an element or attribute node
    // NOTE: This should be implemented as a core XForms action (see also XQuery updates)
    def rename(nodeInfo: NodeInfo, oldName: String, newName: String) {

        require(nodeInfo ne null)
        require(Set(ELEMENT_NODE, ATTRIBUTE_NODE)(nodeInfo.getNodeKind.toShort))

        if (oldName != newName) {
            val newNodeInfo = nodeInfo.getNodeKind match {
                case ELEMENT_NODE ⇒ elementInfo(newName, (nodeInfo \@ @*) ++ (nodeInfo \ Node))
                case ATTRIBUTE_NODE ⇒  attributeInfo(newName, attValueOption(nodeInfo).get)
                case _ ⇒ throw new IllegalArgumentException
            }

            insert(into = nodeInfo parent *, after = nodeInfo, origin = newNodeInfo)
            delete(nodeInfo)
        }
    }

    // Move the given element before another element
    def moveElementBefore(element: NodeInfo, other: NodeInfo) = {
        val inserted = insert(into = element parent *, before = other, origin = element)
        delete(element)
        inserted.head
    }

    // Move the given element after another element
    def moveElementAfter(element: NodeInfo, other: NodeInfo) = {
        val inserted = insert(into = element parent *, after = other, origin = element)
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
            case Seq() ⇒ insert(into = element, origin = attributeInfo(attName, value))
            case Seq(att, _*) ⇒ setvalue(att, value)
        }

    // Return an instance's root element in the current action context
    def instanceRoot(staticId: String, searchAncestors: Boolean = false): Option[NodeInfo] = {

        assert(actionInterpreterDyn.value.isDefined)

        def ancestorXBLContainers = {
            def recurse(container: XBLContainer): List[XBLContainer] = container :: (container.getParentXBLContainer match {
                case parent: XBLContainer ⇒ recurse(parent)
                case _ ⇒ Nil
            })

            recurse(actionInterpreterDyn.value.get.container)
        }

        val containersToSearch =
            if (searchAncestors) ancestorXBLContainers else List(actionInterpreterDyn.value.get.container)

        containersToSearch map
                (_.findInstance(staticId)) find
                    (_ ne null) map
                        (_.instanceRoot)
    }

    // Return a model
    // TODO: This searches only top-level models, find a better way
    def model(modelId: String) =
        // NOTE: This search is not very efficient, but this allows mocking in tests, where getObjectByEffectiveId causes issues
        containingDocument.models find (_.getId == modelId)

    // Return the containing document
    def containingDocument = { assert(containingDocumentDyn.value.isDefined); containingDocumentDyn.value.get }
    
    def context[T](xpath: String)(body: ⇒ T): T = ???
    def context[T](item: Item)(body: ⇒ T): T = ???
    def event[T](attributeName: String): Seq[Item] = ???
}   