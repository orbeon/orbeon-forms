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

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.LHHASupport.*
import org.orbeon.oxf.xforms.control.XFormsControl.*
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.analysis.model.ValidationLevel
import shapeless.syntax.typeable.*


trait ControlLHHASupport {

  self: XFormsControl =>

  // Label, help, hint and alert (evaluated lazily)
  // 2013-06-19: We support multiple alerts, but only one client-facing alert value at this point.
  // NOTE: var because of cloning
  private[ControlLHHASupport] var lhhaArray = new Array[LHHAProperty](LHHA.size)

  // XBL Container in which dynamic LHHA elements like `xf:output` and AVTs evaluate
  def lhhaContainer = container

  def markLHHADirty(): Unit =
    for (currentLHHA <- lhhaArray)
      if (currentLHHA ne null)
        currentLHHA.handleMarkDirty()

  // This is needed because, unlike the other LHH, the alert doesn't only depend on its expressions: it also depends
  // on the control's current validity and validations. Because we don't have yet a way of taking those in as
  // dependencies, we force dirty alerts whenever such validations change upon refresh.
  def forceDirtyAlert(): Unit = {
    val alert = lhhaArray(LHHA.valuesToIndex(LHHA.Alert))
    if (alert ne null)
      alert.handleMarkDirty(force = true)
  }

  def evaluateNonRelevantLHHA(): Unit =
    for (i <- lhhaArray.indices)
      lhhaArray(i) = null

  // Copy LHHA if not null
  def updateLHHACopy(copy: XFormsControl, collector: ErrorEventCollector): Unit = {
    copy.lhhaArray = new Array[LHHAProperty](LHHA.size)
    for {
      i <- lhhaArray.indices
      currentLHHA = lhhaArray(i)
      if currentLHHA ne null
    } yield {
      // Evaluate lazy value before copying
      currentLHHA.value(collector)

      // Copy
      copy.lhhaArray(i) = currentLHHA.copy.asInstanceOf[LHHAProperty]
    }
  }

  def lhhaProperty(lhha: LHHA): LHHAProperty = {
    // TODO: Not great to work by index.
    val index = LHHA.valuesToIndex(lhha)
    // Evaluate lazily
    Option(lhhaArray(index)) getOrElse {

      // NOTE: Ugly because of imbalanced hierarchy between static/runtime controls
      val property =
        if (part.hasLHHA(prefixedId, lhha) && self.staticControl.isInstanceOf[StaticLHHASupport])
          self match {
            case singleNodeControl: XFormsSingleNodeControl if lhha == LHHA.Alert =>
              new MutableAlertProperty(singleNodeControl, lhha, htmlLhhaSupport(lhha))
            case control: XFormsControl if lhha != LHHA.Alert =>
              new MutableLHHProperty(control, lhha, htmlLhhaSupport(lhha))
            case _ =>
              NullLHHA
          }
        else
          NullLHHA

      lhhaArray(index) = property
      property
    }
  }

  def eagerlyEvaluateLhha(collector: ErrorEventCollector): Unit =
    for (lhha <- LHHA.values)
      lhhaProperty(lhha).value(collector)

  def htmlLhhaSupport: Set[LHHA] = LHHA.DefaultLHHAHTMLSupport
  def ajaxLhhaSupport: Seq[LHHA] = LHHA.values

  def compareLHHA(other: XFormsControl, collector: ErrorEventCollector): Boolean =
    ajaxLhhaSupport forall (lhha => lhhaProperty(lhha).value(collector) == other.lhhaProperty(lhha).value(collector))

  // Convenience accessors
  final def getLabel   (collector: ErrorEventCollector) = lhhaProperty(LHHA.Label).value(collector)
  final def isHTMLLabel(collector: ErrorEventCollector) = lhhaProperty(LHHA.Label).isHTML(collector)
  final def getHelp    (collector: ErrorEventCollector) = lhhaProperty(LHHA.Help).value(collector)
  final def getHint    (collector: ErrorEventCollector) = lhhaProperty(LHHA.Hint).value(collector)
  final def getAlert   (collector: ErrorEventCollector) = lhhaProperty(LHHA.Alert).value(collector)
  final def isHTMLAlert(collector: ErrorEventCollector) = lhhaProperty(LHHA.Alert).isHTML(collector)

  lazy val referencingControl: Option[(StaticLHHASupport, XFormsSingleNodeControl)] = {
    for {
      lhhaSupport           <- self.staticControl.cast[StaticLHHASupport]
      staticRc              <- lhhaSupport.referencingControl
      concreteRcEffectiveId = XFormsId.buildEffectiveId(staticRc.prefixedId, XFormsId.getEffectiveIdSuffixParts(self.effectiveId))
      concreteRc            <- containingDocument.findControlByEffectiveId(concreteRcEffectiveId)
      concreteSnRc          <- concreteRc.cast[XFormsSingleNodeControl]
    } yield
      staticRc -> concreteSnRc
  }

  def lhhaValue(lhha: LHHA): Option[String] =
    (
      self.staticControl match { // Scala 3: `.match`
        case s: StaticLHHASupport if s.hasDirectLhha(lhha) => Some(self)
        case s: StaticLHHASupport if s.hasByLhha(lhha)     => self.referencingControl.map(_._2)
        case _                                             => None
      }
    )
    .flatMap(_.lhhaProperty(lhha)
    .valueOpt(EventCollector.Throw))
}

// NOTE: Use name different from trait so that the Java compiler is happy
object LHHASupport {

  val NullLHHA = new NullLHHAProperty

  // Control property for LHHA
  trait LHHAProperty extends ControlProperty[String] {
    def escapedValue(collector: ErrorEventCollector): String
    def isHTML(collector: ErrorEventCollector): Boolean
  }

  // Immutable null LHHA property
  class NullLHHAProperty extends ImmutableControlProperty(null: String) with LHHAProperty {
    def escapedValue(collector: ErrorEventCollector): String = null
    def isHTML(collector: ErrorEventCollector) = false
  }

  // Gather all active alerts for the given control following a selection algorithm
  //
  // - This depends on
  //     - the control validity
  //     - failed validations
  //     - alerts in the UI matching validations or not
  // - If no alert is active for the control, return None.
  // - Only alerts for the highest ValidationLevel are returned.
  //
  def gatherActiveAlerts(control: XFormsSingleNodeControl): Option[(ValidationLevel, List[LHHAAnalysis])] =
    if (control.isRelevant) {

      val staticAlerts = control.staticControl.asInstanceOf[StaticLHHASupport].alerts

      def nonEmptyOption[T](l: List[T]) = l.nonEmpty option l

      def alertsMatchingValidations = {
        val failedValidationsIds = control.failedValidations.map(_.id).to(Set)
        nonEmptyOption(staticAlerts filter (_.forValidations intersect failedValidationsIds nonEmpty))
      }

      // Find all alerts which match the given level, if there are any failed validations for that level
      // NOTE: ErrorLevel is handled specially: in addition to failed validations, the level matches if the
      // control is not valid for any reason including failed schema validation.
      def alertsMatchingLevel(level: ValidationLevel) =
        nonEmptyOption(staticAlerts filter (_.forLevels(level)))

      // Alerts that specify neither a validations nor a level
      def alertsMatchingAny =
        nonEmptyOption(staticAlerts filter (a => a.forValidations.isEmpty && a.forLevels.isEmpty))

      // For that given level, identify all matching alerts if any, whether they match by validations or by level.
      // Alerts that specify neither a validation nor a level are considered a default, that is they are not added
      // if other alerts have already been matched.
      // Alerts are returned in document order
      control.alertLevel flatMap { level =>

        val alerts =
          alertsMatchingValidations  orElse
          alertsMatchingLevel(level) orElse
          alertsMatchingAny          getOrElse
          Nil

        val matchingAlertIds = alerts map (_.staticId) toSet
        val matchingAlerts   = staticAlerts filter (a => matchingAlertIds(a.staticId))

        matchingAlerts.nonEmpty option (level, matchingAlerts)
      }
    } else
      None
}