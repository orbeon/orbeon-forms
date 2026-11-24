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

import cats.data.NonEmptyList
import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.MarkupUtils.MarkupStringOps
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.LHHASupport.*
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.analysis.model.ValidationLevel
import shapeless.syntax.typeable.*


trait ControlLHHASupport {

  self: XFormsControl =>

  // Label, help, hint and alert (evaluated lazily)
  // 2013-06-19: We support multiple alerts, but only one client-facing alert value at this point.
  // NOTE: `var` because of cloning
  private[ControlLHHASupport] var lhhMap: Map[(LHHA, Boolean), LHHAProperty] = Map.empty
  private[ControlLHHASupport] var alerts: Option[(List[LHHAProperty], LHHAProperty, LHHAProperty)] = None // separate, combined local, and combined active alerts
  private[ControlLHHASupport] var byLhhValuesCopyOpt: Option[Map[LHHA, Option[String]]] = None

  // XBL Container in which dynamic LHHA elements like `xf:output` and AVTs evaluate
  def lhhaContainer: XBLContainer = container

  def markLHHADirty(): Unit = {
    lhhMap.valuesIterator.foreach(_.handleMarkDirty())
    // The overriding method will take care of calling `forceDirtyAlert()` if needed
    alerts.foreach { case (separate, local, combined) =>
      separate.foreach(_.handleMarkDirty())
      local.handleMarkDirty()
      combined.handleMarkDirty()
    }
  }

  // This is needed because, unlike the other LHH, the alert doesn't only depend on its expressions: it also depends
  // on the control's current validity and validations. Because we don't have yet a way of taking those in as
  // dependencies, we force dirty alerts whenever such validations change upon refresh.
  def forceDirtyAlert(): Unit =
    alerts = None

  def evaluateNonRelevantLHHA(): Unit = {
    lhhMap             = Map.empty
    alerts             = None
    byLhhValuesCopyOpt = None
  }

  // Make immutable eager copies of all LHHA properties
  def updateLHHACopy(copy: XFormsControl, collector: ErrorEventCollector): Unit = {

    copy.lhhMap =
      lhhMap.map {
        case (key, property) =>
          val value = property.value(collector)
          key -> new ImmutableLHHAProperty(value, property.isHTML, property.locationData)
      }

    copy.alerts =
      alerts.map { case (separate, local, combined) =>
        (
          separate.map { property =>
            val value = property.value(collector)
            new ImmutableLHHAProperty(value, property.isHTML, property.locationData)
          },
          new ImmutableLHHAProperty(local.value(collector), local.isHTML, local.locationData),
          new ImmutableLHHAProperty(combined.value(collector), combined.isHTML, combined.locationData)
        )
      }

    copy.byLhhValuesCopyOpt =
      self.staticControl match {
        case s: StaticLHHASupport =>
          val byLhhas = LHHA.values.filter(lhha => ! s.hasDirectLhha(lhha) && s.hasByLhha(lhha))
          if (byLhhas.nonEmpty)
            Some(byLhhas.map(lhha => lhha -> self.directOrByLocalLhhaValue(lhha, collector)).toMap)
          else
            None
        case _ =>
          None
      }
  }

  // 2024-12-05: always `local = true` for now. For alerts in particular, however, in particular for the Error Summary,
  // we might want to call this with `local = false` to get the combined value.
  def lhhaProperty(lhha: LHHA, local: Boolean): LHHAProperty =
    lhha match {
      case LHHA.Alert =>

        val tuple =
          alerts.getOrElse {

            val alertsOpt =
              gatherActiveAlertsProperties.map { activeAlerts =>

                val activeAlertsList = activeAlerts.toList

                def combine(alerts: List[MutableLHHAProperty]): LHHAProperty =
                  alerts match {
                    case Nil =>
                      NullLHHA // Q: should we use `Option`?
                    case head :: Nil =>
                      head
                    case head :: tail =>
                      // Combine multiple values as a single HTML value using `ul`/`li`
                      new MutableLHHAProperty(self, head.lhhaAnalysis) { // NOTE: `head.lhhaAnalysis` will not be used
                        override def isHTML: Boolean = true
                        override protected def evaluateValue(collector: ErrorEventCollector)(implicit indentedLogger: IndentedLogger): String = combinePropertiesAsUl(head :: tail, collector)(indentedLogger)
                        // These shouldn't need to be implemented as the aggregated value is simply set to `None` when needed
                        override protected def requireUpdate: Boolean = false
                        override def handleMarkDirty(force: Boolean): Unit = () // (head :: tail).foreach(_.handleMarkDirty(force))
                      }
                    }

                val local = activeAlertsList.filter(_.lhhaAnalysis.isLocal)

                (activeAlertsList, combine(local), combine(activeAlertsList))
              }

            val alertsOrDefault =
              alertsOpt.getOrElse((Nil, NullLHHA, NullLHHA))

            alerts = Some(alertsOrDefault)
            alertsOrDefault
          }

        if (local)
          tuple._2
        else
          tuple._3

      case lhh =>
        val key = (lhh, local)
        lhhMap.getOrElse(key, {
          val property = evaluateLhh(lhh, local)
          lhhMap += key -> property
          property
        })
    }

  def alertsForValidation(
    validationIdOpt: Option[String],
    forLevels      : Set[ValidationLevel] // TODO
  ): List[MutableLHHAProperty] = {
    lhhaProperty(LHHA.Alert, local = true) // force evaluation?
    validationIdOpt match {
      case Some(validationId) =>
        alerts
          .toList
          .map(_._1)
          .flatMap(lHHAProperties => lHHAProperties.collect {
            case mutableLhhaProperty: MutableLHHAProperty
              if mutableLhhaProperty.lhhaAnalysis.forValidationId.contains(validationId) =>
                mutableLhhaProperty
          })
      case None =>
        alerts
          .toList
          .map(_._3)
          .flatMap { combined =>
            combined.cast[MutableLHHAProperty]
          }
    }
  }

  // NOTE: Ugly because of imbalanced hierarchy between static/runtime controls
  private def evaluateLhh(lhha: LHHA, local: Boolean): LHHAProperty =
    self.staticControl match {
      case staticLhhaSupport: StaticLHHASupport =>
        val analysisOpt =
          if (local)
            staticLhhaSupport.firstDirectLocalLhha(lhha)
          else
            staticLhhaSupport.firstDirectLocalLhha(lhha)
              .orElse(staticLhhaSupport.firstDirectLhha(lhha))
        analysisOpt
          .map(lhh => new MutableLHHAProperty(self, lhh))
          .getOrElse(NullLHHA)
      case _ =>
        NullLHHA
    }

  private def gatherActiveAlertsProperties: Option[NonEmptyList[MutableLHHAProperty]] =
    self.cast[XFormsSingleNodeControl].flatMap { singleNodeControl =>
      LHHASupport.gatherActiveAlerts(singleNodeControl).map { case (_, activeAlerts) =>
        activeAlerts.map(new MutableLHHAProperty(singleNodeControl, _))
      }
    }

  private[ControlLHHASupport] def hasLocalLhha(lhha: LHHA): Boolean =
    self.staticControl.cast[StaticLHHASupport].exists(_.hasLocal(lhha))

  def eagerlyEvaluateLhha(collector: ErrorEventCollector): Unit =
    for (lhha <- LHHA.values)
      lhhaProperty(lhha, local = hasLocalLhha(lhha)).value(collector)

  def htmlLhhaSupport: Set[LHHA] = LHHA.DefaultLHHAHTMLSupport
  def ajaxLhhaSupport: Seq[LHHA] = LHHA.values

  def compareLHHA(other: XFormsControl, collector: ErrorEventCollector): Boolean =
    ajaxLhhaSupport forall (lhha => lhhaProperty(lhha, local = true).value(collector) == other.lhhaProperty(lhha, local = true).value(collector))

  // Convenience accessors
  final def getLabel   (collector: ErrorEventCollector): Option[String] = Option(lhhaProperty(LHHA.Label, local = hasLocalLhha(LHHA.Label)).value(collector))
  final def isHTMLLabel                                : Boolean        = lhhaProperty(LHHA.Label,        local = hasLocalLhha(LHHA.Label)).isHTML
  final def getHelp    (collector: ErrorEventCollector): Option[String] = Option(lhhaProperty(LHHA.Help,  local = hasLocalLhha(LHHA.Help)).value(collector))
  final def getHint    (collector: ErrorEventCollector): Option[String] = Option(lhhaProperty(LHHA.Hint,  local = hasLocalLhha(LHHA.Hint)).value(collector))
  final def getAlert   (collector: ErrorEventCollector): Option[String] = Option(lhhaProperty(LHHA.Alert, local = hasLocalLhha(LHHA.Alert)).value(collector))

  def referencingControl: Option[(StaticLHHASupport, XFormsSingleNodeControl)] =
    for {
      lhhaSupport           <- self.staticControl.cast[StaticLHHASupport]
      staticRc              <- lhhaSupport.referencingControl
      concreteRcEffectiveId = XFormsId.buildEffectiveId(staticRc.prefixedId, XFormsId.getEffectiveIdSuffixParts(self.effectiveId))
      concreteRc            <- containingDocument.findControlByEffectiveId(concreteRcEffectiveId)
      concreteSnRc          <- concreteRc.cast[XFormsSingleNodeControl]
    } yield
      staticRc -> concreteSnRc

  def directOrByLocalLhhaValue(
    lhha     : LHHA,
    collector: ErrorEventCollector = EventCollector.Throw
  ): Option[String] =
    byLhhValuesCopyOpt match {
      case Some(byLhhValues) if byLhhValues.contains(lhha) =>
        byLhhValues(lhha)
      case _ =>
        (
          self.staticControl match { // Scala 3: `.match`
            case s: StaticLHHASupport if s.hasDirectLhha(lhha) => Some(self)
            case s: StaticLHHASupport if s.hasByLhha(lhha)     => self.referencingControl.map(_._2)
            case _                                             => None
          }
        )
        .flatMap(c => c.lhhaProperty(lhha, local = c.hasLocalLhha(lhha)).valueOpt(collector))
    }
}

// NOTE: Use name different from trait so that the Java compiler is happy
object LHHASupport {

  def combinePropertiesAsUl(properties: List[MutableLHHAProperty], collector: ErrorEventCollector)(implicit indentedLogger: IndentedLogger): String =
    properties
      .map(property => if (!property.isHTML) property.value(collector).escapeXmlMinimal else property.value(collector))
      .mkString("<ul><li>", "</li><li>", "</li></ul>")

  // Immutable null LHHA property
  object NullLHHA extends ImmutableLHHAProperty(null: String, isHTML = false, locationData = null) {
    override def escapedValue(collector: ErrorEventCollector)(implicit indentedLogger: IndentedLogger): String = null
  }

  // Control property for LHHA
  trait LHHAProperty extends ControlProperty[String] {
    def locationData: LocationData
    def isHTML: Boolean
    def escapedValue(collector: ErrorEventCollector)(implicit indentedLogger: IndentedLogger): String = {
      val rawValue = value(collector)
      if (isHTML)
        XFormsControl.getEscapedHTMLValue(rawValue)
      else
        rawValue.escapeXmlMinimal
    }
  }

  class ImmutableLHHAProperty(value: String, val isHTML: Boolean, val locationData: LocationData)
    extends ImmutableControlProperty(value)
      with LHHAProperty

  // Gather all active alerts for the given control following a selection algorithm
  //
  // - This depends on
  //     - the control validity
  //     - failed validations
  //     - alerts in the UI matching validations or not
  // - If no alert is active for the control, return `None`.
  // - Only alerts for the highest `ValidationLevel` are returned.
  //
  def gatherActiveAlerts(control: XFormsSingleNodeControl): Option[(ValidationLevel, NonEmptyList[LHHAAnalysis])] =
    if (control.isRelevant) {
      control
        .staticControl
        .cast[StaticLHHASupport]
        .flatMap(_.alertsNel)
        .flatMap { staticAlerts =>

        def alertsMatchingValidations: Option[NonEmptyList[LHHAAnalysis]] = {
          val failedValidationsIds = control.failedValidations.map(_.id).to(Set)
          NonEmptyList.fromList(staticAlerts.filter(_.forValidations.intersect(failedValidationsIds).nonEmpty))
        }

        // Find all alerts which match the given level, if there are any failed validations for that level
        // NOTE: ErrorLevel is handled specially: in addition to failed validations, the level matches if the
        // control is not valid for any reason including failed schema validation.
        def alertsMatchingLevel(level: ValidationLevel): Option[NonEmptyList[LHHAAnalysis]] =
          NonEmptyList.fromList(staticAlerts.filter(_.forLevels(level)))

        // Alerts that specify neither a validations nor a level
        def alertsMatchingAny: Option[NonEmptyList[LHHAAnalysis]] =
          NonEmptyList.fromList(staticAlerts.filter(a => a.forValidations.isEmpty && a.forLevels.isEmpty))

        // For that given level, identify all matching alerts if any, whether they match by validations or by level.
        // Alerts that specify neither a validation nor a level are considered a default, that is, they are not added
        // if other alerts have already been matched.
        // Alerts are returned in document order
        control.alertLevel.flatMap { level =>

          val alerts =
            alertsMatchingValidations  orElse
            alertsMatchingLevel(level) orElse
            alertsMatchingAny

          val matchingAlertIds = alerts.iterator.flatMap(_.iterator.map(_.staticId)).toSet
          val matchingAlerts   = staticAlerts.filter(a => matchingAlertIds(a.staticId))

          NonEmptyList.fromList(matchingAlerts).map(level -> _)
        }
      }
    } else
      None
}