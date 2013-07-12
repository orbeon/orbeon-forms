/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.xforms.event.ListenersTrait
import org.dom4j.{Element, VisitorSupport, Document}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, InstanceData}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.model.StaticBind.WarningLevel
import collection.mutable
import collection.JavaConverters._
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl

abstract class XFormsModelSubmissionBase extends ListenersTrait {

    // Annotate elements which have failed warning constraints with an xxf:warning attribute containing the alert message
    protected def annotateWithWarnings(containingDocument: XFormsContainingDocument, doc: Document): Unit = {

        var elementsToAnnotate = mutable.Map[Set[String], Element]()

        // Iterate data to gather elements with failed constraints
        doc.accept(new VisitorSupport() {
            override def visit(element: Element): Unit = {
                val failedWarningConstraints = InstanceData.failedConstraints(element).getOrElse(WarningLevel, Nil)
                if (failedWarningConstraints.nonEmpty)
                    elementsToAnnotate += (failedWarningConstraints map (_.id) toSet) → element
            }
        })

        if (elementsToAnnotate.nonEmpty) {
            val controls = containingDocument.getControls.getCurrentControlTree.getEffectiveIdsToControls.asScala

            def warningControlsIterator =
                controls.iterator collect
                    { case (_, control: XFormsSingleNodeControl) if control.isRelevant && control.alertLevel == Some(WarningLevel) ⇒ control }

            var annotated = false

            def annotateElementIfPossible(control: XFormsSingleNodeControl) = {
                // NOTE: We check on the whole set of constraint ids. Since the control reads in all the failed
                // constraints for the level, the sets of ids must match.
                val failedConstraintIds = control.failedConstraints map (_.id) toSet

                elementsToAnnotate.get(failedConstraintIds) foreach { element ⇒
                    element.addAttribute(XXFORMS_WARNING_QNAME, control.getAlert)
                    annotated = true
                }
            }

            // Iterate all controls with warnings and try to annotate the associated element nodes
            warningControlsIterator foreach annotateElementIfPossible

            // If there is any annotation, make sure the attribute's namespace prefix is in scope on the root element
            if (annotated)
                doc.getRootElement.addNamespace(XXFORMS_WARNING_QNAME.getNamespacePrefix, XXFORMS_WARNING_QNAME.getNamespaceURI)
        }
    }
}