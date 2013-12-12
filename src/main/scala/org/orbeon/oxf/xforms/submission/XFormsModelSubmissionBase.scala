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

import collection.JavaConverters._
import collection.mutable
import org.dom4j.{QName, Element, VisitorSupport, Document}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.model.StaticBind._
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.event.ListenersTrait
import org.orbeon.oxf.xforms.{XFormsContainingDocument, InstanceData}

abstract class XFormsModelSubmissionBase extends ListenersTrait

object XFormsModelSubmissionBase {

    // Annotate elements which have failed constraints with an xxf:error, xxf:warning or xxf:info attribute containing
    // the alert message. Only the levels passed in `annotate` are handled.
    def annotateWithAlerts(containingDocument: XFormsContainingDocument, doc: Document, annotate: String): Unit = {

        val levelsToAnnotate = stringToSet(annotate) collect LevelByName

        if (levelsToAnnotate.nonEmpty) {

            val elementsToAnnotate = mutable.Map[ValidationLevel, mutable.Map[Set[String], Element]]()

            // Iterate data to gather elements with failed constraints
            doc.accept(new VisitorSupport() {
                override def visit(element: Element): Unit = {
                    for (level ← levelsToAnnotate) {
                        val failedConstraints = InstanceData.failedConstraints(element).getOrElse(level, Nil)
                        if (failedConstraints.nonEmpty) {
                            val map = elementsToAnnotate.getOrElseUpdate(level, mutable.Map[Set[String], Element]())
                            map += (failedConstraints map (_.id) toSet) → element
                        }
                    }
                }
            })

            if (elementsToAnnotate.nonEmpty) {
                val controls = containingDocument.getControls.getCurrentControlTree.getEffectiveIdsToControls.asScala

                val relevantLevels = elementsToAnnotate.keySet

                def controlsIterator =
                    controls.iterator collect
                        { case (_, control: XFormsSingleNodeControl) if control.isRelevant && control.alertLevel.toList.toSet.subsetOf(relevantLevels) ⇒ control }

                var annotated = false

                def annotateElementIfPossible(control: XFormsSingleNodeControl) = {
                    // NOTE: We check on the whole set of constraint ids. Since the control reads in all the failed
                    // constraints for the level, the sets of ids must match.
                    for {
                        level               ← control.alertLevel
                        controlAlert        ← Option(control.getAlert)
                        failedConstraintIds = (control.failedConstraints map (_.id) toSet)
                        elementsMap         ← elementsToAnnotate.get(level)
                        element             ← elementsMap.get(failedConstraintIds)
                        qName               = QName.get(level.name, XXFORMS_NAMESPACE_SHORT)
                    } locally {
                        // There can be an existing attribute if more than one control bind to the same element
                        Option(element.attribute(qName)) match {
                            case Some(existing) ⇒ existing.setValue(existing.getValue + controlAlert)
                            case None           ⇒ element.addAttribute(qName, controlAlert)
                        }

                        annotated = true
                    }
                }

                // Iterate all controls with warnings and try to annotate the associated element nodes
                controlsIterator foreach annotateElementIfPossible

                // If there is any annotation, make sure the attribute's namespace prefix is in scope on the root element
                if (annotated)
                    doc.getRootElement.addNamespace(XXFORMS_NAMESPACE_SHORT.getPrefix, XXFORMS_NAMESPACE_SHORT.getURI)
            }
        }
    }
}