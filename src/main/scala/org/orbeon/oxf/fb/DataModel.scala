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

import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.analysis.controls.SingleNodeTrait
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.saxon.om._
import org.orbeon.scaxon.XML._
import scala.util.control.NonFatal

object DataModel {

    private val bindWithName: PartialFunction[NodeInfo, NodeInfo] =
        { case bind if exists(bind \@ "name") ⇒ bind }

    private def childrenBindsWithNames(bind: NodeInfo) =
        bind \ (XF → "bind") collect bindWithName

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

        findTopLevelBind(inDoc) foreach update
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

//        def ancestorOrSelfBindsWithNames(bind: NodeInfo) =
//            bind ancestorOrSelf (XF → "bind") collect bindWithName
//
//        val allContainerNames = getAllContainerControlsWithIds(inDoc) map (e ⇒ controlName(e.id)) toSet
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

    // Find a bind ref by name
    def getBindRef(inDoc: NodeInfo, name: String) =
        findBindByName(inDoc, name) flatMap
            bindRefOrNodeset

    // Set a bind ref by name (annotate the expression if needed)
    def setBindRef(inDoc: NodeInfo, name: String, ref: String) =
        findBindByName(inDoc, name) foreach { bind ⇒
            delete(bind \@ "nodeset")
            ensureAttribute(bind, "ref", ref)
        }

    // XForms callers
    def getBindRefOrEmpty(inDoc: NodeInfo, name: String) = getBindRef(inDoc, name).orNull

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
                (bind ⇒ evaluateBoundItem(bind.staticBind.namespaceMapping)) exists
                    (XFormsControl.isAllowedBoundItem(control, _))
        } catch {
            case NonFatal(_) ⇒ false
        }
    }

    // For a given value control name and XPath sequence, whether the resulting bound item is acceptable
    def isAllowedBoundItem(controlName: String, itemOption: Option[Item]) = {
        for {
            item    ← itemOption
            control ← findStaticControlByName(controlName)
            if control.isInstanceOf[SingleNodeTrait]
            singleNodeTrait = control.asInstanceOf[SingleNodeTrait]
        } yield
            singleNodeTrait.isAllowedBoundItem(item)
    } getOrElse
        false
}
