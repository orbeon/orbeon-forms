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
