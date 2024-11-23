/**
 *  Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis.controls

import cats.data.NonEmptyList
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, LhhaControlRef, LhhaPlacementType, XPathAnalysis}
import org.orbeon.xforms.XFormsNames



// Control support for nested or external LHHA elements
trait StaticLHHASupport extends ElementAnalysis {

  import Private._

  // Because an LHHA might follow a control in the document, attachment must be deferred
  def attachDirectLhha(lhhaAnalysis: LHHAAnalysis): Unit =
    lhhaAnalysis.lhhaType match {
      case LHHA.Alert => _alerts :+= lhhaAnalysis
      case _          => _lhh     += lhhaAnalysis.lhhaType -> lhhaAnalysis
    }

  // We called this "By" in reference to `aria-labelledy`/`aria-describedby`
  def attachByLhha(lhhaAnalysis: LHHAAnalysis): Unit =
    lhhaAnalysis.lhhaType match {
      case LHHA.Alert => _alertsBy :+= lhhaAnalysis // 2022-06-14: unused
      case _          => _lhhBy     += lhhaAnalysis.lhhaType -> lhhaAnalysis
    }

  def allowMinimalLabelHint: Boolean

  def alerts: List[LHHAAnalysis] = _alerts
  def alertsNel: Option[NonEmptyList[LHHAAnalysis]] = NonEmptyList.fromList(alerts)

  def firstByOrDirectLhhaOpt(lhhaType: LHHA): Option[LHHAAnalysis] =
    lhhaType match {
      case LHHA.Alert => alertsBy.headOption orElse alerts.headOption
      case _          => lhhBy(lhhaType)     orElse directLhh(lhhaType)
    }

  def firstDirectLhha(lhhaType: LHHA): Option[LHHAAnalysis] =
    if (lhhaType == LHHA.Alert)
      alerts.headOption // for alerts, take the first one, but does this make sense?
    else
      directLhh(lhhaType)

  def allDirectLhha(lhhaType: LHHA): Iterable[LHHAAnalysis] =
    if (lhhaType == LHHA.Alert)
      alerts // for alerts, take the first one, but does this make sense?
    else
      directLhh(lhhaType)

  def hasDirectLhha(lhhaType: LHHA): Boolean =
    firstDirectLhha(lhhaType).nonEmpty

  def hasByLhha(lhhaType: LHHA): Boolean =
    firstLhhaBy(lhhaType).nonEmpty

  def hasLocal(lhhaType: LHHA): Boolean =
    lhhaAsList(lhhaType).exists(_.isLocal)

  def lhhaValueAnalyses(lhhaType: LHHA): List[XPathAnalysis] =
    lhhaAsList(lhhaType).flatMap(_.valueAnalysis)

  def hasLhhaPlaceholder(lhhaType: LHHA): Boolean =
    firstByOrDirectLhhaOpt(lhhaType) match {
      case Some(lhh) => lhh.isPlaceholder && allowMinimalLabelHint
      case None      => false
    }

  // Make `var` for `fullOpt` error "Assignment to immutable field beforeAfterTokensOpt"
  var beforeAfterTokensOpt: Option[(List[String], List[String])] =
    element.attributeValueOpt(XFormsNames.XXFORMS_ORDER_QNAME) map LHHA.getBeforeAfterOrderTokens

  // https://github.com/orbeon/orbeon-forms/issues/6279
  // If this is nested within an XBL component that is the target of an `xxf:label-for`, find the outermost control
  // that references it. Ideally, we wouldn't actually need the presence of any LHHA for that outer control, but we
  // create that relationship only if there is at least one LHHA. This covers the concrete scenarios anyway. But just
  // in case, we iterate through all the LHHA and find the first one. At the time of writing, all LHHA follow
  // `xxf:label-for`, not only `<xf:label>`.
  lazy val referencingControl: Option[StaticLHHASupport] =
    LHHA.values
      .iterator
      .flatMap(firstLhhaBy)
      .map(_.lhhaPlacementType)
      .collectFirst {
        case LhhaPlacementType.Local   (directTargetControl, LhhaControlRef.Control(_))    => directTargetControl
        case LhhaPlacementType.External(directTargetControl, LhhaControlRef.Control(_), _) => directTargetControl
      }

  // https://github.com/orbeon/orbeon-forms/issues/6279
  lazy val referencedControl: Option[StaticLHHASupport] =
    LHHA.values
      .iterator
      .flatMap(firstDirectLhha)
      .map(_.lhhaPlacementType)
      .flatMap { lhhaPlacementType =>
        lhhaPlacementType.lhhaControlRef match {
          case LhhaControlRef.Control(group: GroupControl)
            if (group ne lhhaPlacementType.directTargetControl) && group.elementQNameOrDefault.localName != "div" => // see `XFormsGroupDefaultHandler`
            Some(group)
          case _ =>
            None
        }
      }
      .nextOption()

  // analyzeXPath(): this is done as part of control analysis, see:
  // https://github.com/orbeon/orbeon-forms/issues/2185

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    allLhha foreach (_.freeTransientState())
  }

  private object Private {

    // All LHHA, local or external
    // There can only be multiple alerts for now, not multiple LHH, so store alerts separately
    var _lhh      = Map.empty[LHHA, LHHAAnalysis] // at most 3 entries
    var _lhhBy    = Map.empty[LHHA, LHHAAnalysis] // at most 3 entries
    var _alerts   = List.empty[LHHAAnalysis]
    var _alertsBy = List.empty[LHHAAnalysis]

    def lhhaAsList(lhhaType: LHHA) =
      if (lhhaType == LHHA.Alert) alerts else directLhh(lhhaType).toList

    def allLhha = _lhh.values ++ _alerts

    def directLhh(lhhaType: LHHA): Option[LHHAAnalysis] = _lhh.get(lhhaType)
    def lhhBy    (lhhaType: LHHA): Option[LHHAAnalysis] = _lhhBy.get(lhhaType)
    def alertsBy                 : List[LHHAAnalysis]   = _alertsBy

    def firstLhhaBy(lhhaType: LHHA): Option[LHHAAnalysis] =
      if (lhhaType == LHHA.Alert)
        alertsBy.headOption // for alerts, take the first one, but does this make sense?
      else
        lhhBy(lhhaType)
  }
}
