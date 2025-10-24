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

  import Private.*

  // Because an LHHA might follow a control in the document, attachment must be deferred
  def attachDirectLhha(lhhaAnalysis: LHHAAnalysis): Unit =
     _directLhh += lhhaAnalysis.lhhaType -> (_directLhh.getOrElse(lhhaAnalysis.lhhaType, Nil) :+ lhhaAnalysis) // xxx or `::` ?

  // We called this "By" in reference to `aria-labelledy`/`aria-describedby`
  def attachByLhha(lhhaAnalysis: LHHAAnalysis): Unit =
     _byLhh += lhhaAnalysis.lhhaType -> (_byLhh.getOrElse(lhhaAnalysis.lhhaType, Nil) :+ lhhaAnalysis) // xxx or `::` ?

  def allowMinimalLabelHint: Boolean

  def alerts: List[LHHAAnalysis] = directLhh(LHHA.Alert)
  def alertsNel: Option[NonEmptyList[LHHAAnalysis]] = NonEmptyList.fromList(alerts)

  def firstByOrDirectLhhaOpt(lhhaType: LHHA): Option[LHHAAnalysis] =
    byLhh(lhhaType).headOption orElse directLhh(lhhaType).headOption

  def allByOrDirectLhha(lhhaType: LHHA): List[LHHAAnalysis] =
    byLhh(lhhaType) ++ directLhh(lhhaType)

  def firstDirectLhha(lhhaType: LHHA): Option[LHHAAnalysis] =
    directLhh(lhhaType).headOption

  def firstDirectLocalLhha(lhhaType: LHHA): Option[LHHAAnalysis] =
    directLhh(lhhaType).find(_.isLocal)

  def allDirectLhha(lhhaType: LHHA): Iterable[LHHAAnalysis] =
    directLhh(lhhaType)

  def hasDirectLhha(lhhaType: LHHA): Boolean =
    allDirectLhha(lhhaType).nonEmpty

  def hasByLhha(lhhaType: LHHA): Boolean =
    firstLhhaBy(lhhaType).nonEmpty

  def hasLocal(lhhaType: LHHA): Boolean =
    directLhh(lhhaType).exists(_.isLocal)

  def lhhaValueAnalyses(lhhaType: LHHA): List[XPathAnalysis] =
    directLhh(lhhaType).flatMap(_.valueAnalysis)

  def hasLhhaPlaceholder(lhhaType: LHHA): Boolean = {
    val lhhas = allByOrDirectLhha(lhhaType)
    lhhas.exists(_.isPlaceholder) && allowMinimalLabelHint
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
    allLhha.foreach(_.freeTransientState())
  }

  private object Private {

    // All LHHA, local or external
    var _directLhh = Map.empty[LHHA, List[LHHAAnalysis]] // at most 4 entries
    var _byLhh     = Map.empty[LHHA, List[LHHAAnalysis]] // at most 4 entries

    def allLhha: Iterable[LHHAAnalysis] = _directLhh.values.flatten

    def directLhh(lhhaType: LHHA): List[LHHAAnalysis] = _directLhh.getOrElse(lhhaType, Nil)
    def byLhh    (lhhaType: LHHA): List[LHHAAnalysis] = _byLhh.getOrElse(lhhaType, Nil)

    def firstLhhaBy(lhhaType: LHHA): Option[LHHAAnalysis] =
      byLhh(lhhaType).headOption
  }
}
