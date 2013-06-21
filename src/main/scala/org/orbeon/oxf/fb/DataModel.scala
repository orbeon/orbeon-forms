/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fb.FormBuilderFunctions._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.saxon.om._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.analysis.controls.SingleNodeTrait
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xforms.{BindNode, InstanceData}
import org.orbeon.oxf.util.XPath
import scala.util.control.NonFatal

object DataModel {


    // Whether the current form has a custom instance
    def isCustomInstance = {
        val metadataInstance = topLevelModel("fr-form-model").get.getVariable("is-custom-instance")
        (metadataInstance ne null) && effectiveBooleanValue(metadataInstance)
    }

    private val bindWithName: PartialFunction[NodeInfo, NodeInfo] =
        { case bind if exists(bind \@ "name") ⇒ bind }

    private def childrenBindsWithNames(bind: NodeInfo) =
        bind \ (XF → "bind") collect bindWithName

    private def ancestorOrSelfBindsWithNames(bind: NodeInfo) =
        bind ancestorOrSelf (XF → "bind") collect bindWithName

    // Create a new data model from the binds
    def dataModelFromBinds(inDoc: NodeInfo): NodeInfo = {

        def insertChildren(holder: NodeInfo, bind: NodeInfo): NodeInfo = {

            val newChildren =
                childrenBindsWithNames(bind) map
                    (bind ⇒ insertChildren(elementInfo(bind attValue "name" ), bind))

            insert(into = holder, origin = newChildren)

            holder
        }

        findTopLevelBind(inDoc).headOption map (insertChildren(elementInfo("form"), _)) orNull
    }

    private def foreachBindWithName(inDoc: NodeInfo)(op: NodeInfo ⇒ Any) {
        def update(bind: NodeInfo) {
            childrenBindsWithNames(bind) foreach { child ⇒
                op(child)
                update(child)
            }
        }

        findTopLevelBind(inDoc) foreach (update(_))
    }

    // Update binds for automatic mode
    def updateBindsForAutomatic(inDoc: NodeInfo) =
        foreachBindWithName(inDoc) { child ⇒
            delete(child \@ "nodeset")
            ensureAttribute(child, "ref", child attValue "name")
        }

    // Update binds for custom mode
    def updateBindsForCustom(inDoc: NodeInfo) = {
        // NOTE: We used to remove xf:bind/@ref for container controls, but we shouldn't do this until we properly
        // support sections without bindings (and possibly other controls like triggers) to nodes. Also, if we do this,
        // we must modify insertNewSection and dialog-section-details.xml.

//        val allContainerNames = getAllContainerControlsWithIds(inDoc) map (e ⇒ controlName(e attValue "id")) toSet
//
//        foreachBindWithName(inDoc) { child ⇒
//            def path = (ancestorOrSelfBindsWithNames(child) map (_ attValue "name") reverse) mkString "/"
//
//            delete(child \@ "nodeset")
//            if (allContainerNames(child attValue "name"))
//                delete(child \@ "ref")
//            else
//                ensureAttribute(child, "ref", path)
//        }
    }

    // Find a bind ref by name (deannotate the expression if needed)
    def getBindRef(inDoc: NodeInfo, name: String) =
        findBindByName(inDoc, name) flatMap
            bindRefOrNodeset map
                deAnnotatedBindRef

    // Set a bind ref by name (annotate the expression if needed)
    def setBindRef(inDoc: NodeInfo, name: String, ref: String) =
        findBindByName(inDoc, name) foreach { bind ⇒
            delete(bind \@ "nodeset")
            ensureAttribute(bind, "ref", annotatedBindRefIfNeeded(bindId(name), ref))
        }

    // XForms callers
    def getBindRefOrEmpty(inDoc: NodeInfo, name: String) = getBindRef(inDoc, name).orNull

    private val AnnotatedBindRef = """dataModel:bindRef\([^,]*,(.*)\)$""".r

    def annotatedBindRef(bindId: String, ref: String) = "dataModel:bindRef('" + bindId + "', " + ref + ")"
    def deAnnotatedBindRef(ref: String) = AnnotatedBindRef.replaceFirstIn(ref.trim, "$1").trim

    def annotatedBindRefIfNeeded(bindId: String, ref: String) =
        if (isCustomInstance) annotatedBindRef(bindId, ref) else ref

    // Function called via `dataModel:bindRef()` from the binds to retrieve the adjusted bind node at design time. If
    // the bound item is non-empty and acceptable for the control, then it is returned. Otherwise, a pointer to
    // a dangling element is returned. This allows that element to hold MIPs. The element is marked as readonly so that
    // controls cannot write to it directly.
    def bindRef(bindId: String, i: SequenceIterator): SequenceIterator =
        if (isAllowedBoundItem(controlName(bindId), asScalaSeq(i).headOption))
            i.getAnother
        else {
            // Create and wrap the dangling element
            val newElement = Dom4jUtils.createElement("orbeon-dangling-element")
            val newElementInfo = XPath.DocumentWrapper.wrap(newElement)
            // Then attach a readonly bind node to it. This is as if there was a <bind readonly="true" for the node>
            val bindNode = new BindNode(bindId, newElementInfo, null)
            bindNode.setReadonly(true)
            InstanceData.addBindNode(newElementInfo, bindNode)

            SingletonIterator.makeIterator(newElementInfo)
        }

    // For a given value control name and XPath expression, whether the resulting bound item is acceptable
    // Called from control details dialog
    def isAllowedValueBindingExpression(controlName: String, expr: String): Boolean =
        findConcreteControlByName(controlName) exists (isAllowedBindingExpression(_, expr))

    // Leave public for unit tests
    def isAllowedBindingExpression(control: XFormsControl, expr: String): Boolean = {

        def evaluateBoundItem(namespaces: NamespaceMapping) =
            Option(evalOne(control.bindingContext.contextItem, expr, namespaces, null, containingDocument.getRequestStats.addXPathStat))

        try {
            control.bind flatMap
                (bind ⇒ evaluateBoundItem(bind.namespaceMapping)) exists
                    (XFormsControl.isAllowedBoundItem(control, _))
        } catch {
            case NonFatal(_) ⇒ false
        }
    }

    // For a given value control name and XPath sequence, whether the resulting bound item is acceptable
    def isAllowedBoundItem(controlName: String, itemOption: Option[Item]) = {
        for {
            item ← itemOption
            control ← findStaticControlByName(controlName)
            if control.isInstanceOf[SingleNodeTrait]
            singleNodeTrait = control.asInstanceOf[SingleNodeTrait]
        } yield
            singleNodeTrait.isAllowedBoundItem(item)
    } getOrElse
        false
}
