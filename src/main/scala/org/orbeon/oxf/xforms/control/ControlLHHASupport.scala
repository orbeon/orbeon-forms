/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.xforms._
import XFormsControl._
import org.orbeon.oxf.xforms.XFormsConstants.LHHA
import LHHASupport._
import org.orbeon.oxf.xforms.analysis.model.StaticBind._
import org.orbeon.oxf.xforms.analysis.controls.{StaticLHHASupport, LHHAAnalysis}
import org.orbeon.oxf.util.ScalaUtils._
import collection.breakOut

trait ControlLHHASupport {

    self: XFormsControl ⇒

    // Label, help, hint and alert (evaluated lazily)
    // 2013-06-19: We support multiple alerts, but only one client-facing alert value at this point.
    // NOTE: var because of cloning
    private[ControlLHHASupport] var lhha = new Array[LHHAProperty](XFormsConstants.LHHACount)

    def markLHHADirty(): Unit =
        for (currentLHHA ← lhha)
            if (currentLHHA ne null)
                currentLHHA.handleMarkDirty()

    // This is needed because, unlike the other LHH, the alert doesn't only depend on its expressions: it also depends
    // on the control's current validity and constraints. Because we don't have yet a way of taking those in as
    // dependencies, we force dirty alerts whenever such constraints change upon refresh.
    def forceDirtyAlert(): Unit = {
        val alert = lhha(XFormsConstants.LHHA.alert.ordinal)
        if (alert ne null)
            alert.handleMarkDirty(force = true)
    }

    def evaluateNonRelevantLHHA(): Unit =
        for (i ← 0 to lhha.size - 1)
            lhha(i) = null

    // Copy LHHA if not null
    def updateLHHACopy(copy: XFormsControl): Unit = {
        copy.lhha = new Array[LHHAProperty](XFormsConstants.LHHACount)
        for {
            i ← 0 to lhha.size - 1
            currentLHHA = lhha(i)
            if currentLHHA ne null
        } yield {
            // Evaluate lazy value before copying
            currentLHHA.value()

            // Copy
            copy.lhha(i) = currentLHHA.copy.asInstanceOf[LHHAProperty]
        }
    }

    def lhhaProperty(lhhaType: XFormsConstants.LHHA) = {
        val index = lhhaType.ordinal
        // Evaluate lazily
        Option(lhha(index)) getOrElse {

            // NOTE: Ugly because of imbalanced hierarchy between static/runtime controls
            val property =
                if (containingDocument.getStaticOps.hasLHHA(prefixedId, lhhaType.name) && self.staticControl.isInstanceOf[StaticLHHASupport])
                    self match {
                        case singleNodeControl: XFormsSingleNodeControl if lhhaType == XFormsConstants.LHHA.alert ⇒
                            new MutableAlertProperty(singleNodeControl, lhhaType, lhhaHTMLSupport(index))
                        case control: XFormsControl if lhhaType != XFormsConstants.LHHA.alert ⇒
                            new MutableLHHProperty(control, lhhaType, lhhaHTMLSupport(index))
                        case _ ⇒
                            NullLHHA
                    }
                else
                    NullLHHA

            lhha(index) = property
            property
        }
    }

    // Whether we support HTML LHHA
    def lhhaHTMLSupport = DefaultLHHAHTMLSupport

    def compareLHHA(other: XFormsControl) =
        LHHA.values forall (lhhaType ⇒ lhhaProperty(lhhaType).value() == other.lhhaProperty(lhhaType).value())

    // Convenience accessors
    final def getLabel = lhhaProperty(LHHA.label).value()
    final def getEscapedLabel = lhhaProperty(LHHA.label).escapedValue()
    final def isHTMLLabel = lhhaProperty(LHHA.label).isHTML
    final def getHelp = lhhaProperty(LHHA.help).value()
    final def getEscapedHelp = lhhaProperty(LHHA.help).escapedValue()
    final def isHTMLHelp = lhhaProperty(LHHA.help).isHTML
    final def getHint = lhhaProperty(LHHA.hint).value()
    final def getEscapedHint = lhhaProperty(LHHA.hint).escapedValue()
    final def isHTMLHint = lhhaProperty(LHHA.hint).isHTML
    final def getAlert = lhhaProperty(LHHA.alert).value()
    final def isHTMLAlert = lhhaProperty(LHHA.alert).isHTML
    final def getEscapedAlert = lhhaProperty(LHHA.alert).escapedValue()
}

// NOTE: Use name different from trait so that the Java compiler is happy
object LHHASupport {

    val NullLHHA = new NullLHHAProperty

    // By default all controls support HTML LHHA
    val DefaultLHHAHTMLSupport = Array.fill(4)(true)

    // Control property for LHHA
    trait LHHAProperty extends ControlProperty[String] {
        def escapedValue(): String
        def isHTML: Boolean
    }

    // Immutable null LHHA property
    class NullLHHAProperty extends ImmutableControlProperty(null: String) with LHHAProperty {
        def escapedValue(): String = null
        def isHTML = false
    }

    // Whether a given control has an associated xf:label element.
    def hasLabel(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.hasLHHA(prefixedId, "label")

    // Gather all active alerts for the given control following a selection algorithm
    //
    // - This depends on
    //     - the control validity
    //     - failed constraints
    //     - alerts in the UI matching constraints or not
    // - If no alert is active for the control, return None.
    // - Only alerts for the highest ConstraintLevel are returned.
    //
    def gatherActiveAlerts(control: XFormsSingleNodeControl): Option[(ConstraintLevel, List[LHHAAnalysis])] =
        if (control.isRelevant) {

            val staticAlerts = control.staticControl.asInstanceOf[StaticLHHASupport].alerts

            def alertsMatchingConstraints = {
                val failedConstraintIds = control.failedConstraints.map(_.id).to[Set]
                staticAlerts.iterator filter (_.forConstraints intersect failedConstraintIds nonEmpty)
            }

            // Find all alerts which match the given level, if there are any failed constraints for that level
            // NOTE: ErrorLevel is handled specially: in addition to failed constraints, the level matches if the
            // control is not valid. This is because a control can also be invalid due to a non-matching datatype or due
            // to the required-but-empty condition.
            def alertsMatchingLevel(level: ConstraintLevel) =
                staticAlerts.iterator filter (_.forLevels(level))

            // Alerts that specify neither a constraint nor a level
            def alertsMatchingAny =
                staticAlerts.iterator filter (a ⇒ a.forConstraints.isEmpty && a.forLevels.isEmpty)

            // For that given level, identify all matching alerts if any, whether they match by constraint or by level.
            // Alerts that specify neither a constraint nor a level are considered a default, that is they are not added
            // if other alerts have already been matched.
            // Q: Should the same logic apply to levels? That is, an alert only for a level should show only if there is
            // no more specific alert for that given level?
            // Alerts are returned in document order
            control.alertLevel flatMap { level ⇒
                val matchingAlertsNoDefault = alertsMatchingConstraints ++ alertsMatchingLevel(level) toList
                val matchingAlertsOrDefault = if (matchingAlertsNoDefault.nonEmpty) matchingAlertsNoDefault else alertsMatchingAny

                val matchingAlertIds = matchingAlertsOrDefault map (_.staticId) toSet
                val matchingAlerts   = staticAlerts filter (a ⇒ matchingAlertIds(a.staticId))

                matchingAlerts.nonEmpty option (level, matchingAlerts)
            }
        } else
            None
}