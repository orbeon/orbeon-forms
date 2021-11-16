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

import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, XPathAnalysis}
import org.orbeon.xforms.XFormsNames


// Control support for nested or external LHHA elements
trait StaticLHHASupport extends ElementAnalysis {

  import Private._

  // Because an LHHA might follow a control in the document, attachment must be deferred
  def attachLHHA(lhhaAnalysis: LHHAAnalysis): Unit =
    lhhaAnalysis.lhhaType match {
      case LHHA.Alert => _alerts :+= lhhaAnalysis
      case _          => _lhh     += lhhaAnalysis.lhhaType -> lhhaAnalysis
    }

  def attachLHHABy(lhhaAnalysis: LHHAAnalysis): Unit =
    lhhaAnalysis.lhhaType match {
      case LHHA.Alert => _alertsBy :+= lhhaAnalysis
      case _          => _lhhBy     += lhhaAnalysis.lhhaType -> lhhaAnalysis
    }

  def lhh(lhhaType: LHHA)  : Option[LHHAAnalysis] = _lhh.get(lhhaType)
  def lhhBy(lhhaType: LHHA): Option[LHHAAnalysis] = _lhhBy.get(lhhaType)
  def alerts               : List[LHHAAnalysis]   = _alerts

  def hasLHHA(lhhaType: LHHA): Boolean =
    if (lhhaType == LHHA.Alert) alerts.nonEmpty else lhh(lhhaType).nonEmpty

  def hasLocal(lhhaType: LHHA): Boolean =
    lhhaAsList(lhhaType) exists (_.isLocal)

  def hasLHHANotForRepeat(lhhaType: LHHA): Boolean =
    lhhaAsList(lhhaType) exists (! _.isForRepeat)

  def lhhaValueAnalyses(lhhaType: LHHA): List[XPathAnalysis] =
    lhhaAsList(lhhaType) flatMap (_.valueAnalysis)

  def hasLHHAPlaceholder(lhhaType: LHHA): Boolean =
    lhh(lhhaType) match {
      case Some(lhh) => lhh.isPlaceholder
      case None      => false
    }

  // Make `var` for `fullOpt` error "Assignment to immutable field beforeAfterTokensOpt"
  var beforeAfterTokensOpt: Option[(List[String], List[String])] =
    element.attributeValueOpt(XFormsNames.XXFORMS_ORDER_QNAME) map LHHA.getBeforeAfterOrderTokens

  // analyzeXPath(): this is done as part of control analysis, see:
  // https://github.com/orbeon/orbeon-forms/issues/2185

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    allLHHA foreach (_.freeTransientState())
  }

  private object Private {

    // All LHHA, local or external
    // There can only be multiple alerts for now, not multiple LHH, so store alerts separately
    var _lhh      = Map.empty[LHHA, LHHAAnalysis] // at most 3 entries
    var _lhhBy    = Map.empty[LHHA, LHHAAnalysis] // at most 3 entries
    var _alerts   = List.empty[LHHAAnalysis]
    var _alertsBy = List.empty[LHHAAnalysis]

    def lhhaAsList(lhhaType: LHHA) =
      if (lhhaType == LHHA.Alert) alerts else lhh(lhhaType).toList

    def allLHHA = _lhh.values ++ _alerts
  }
}
