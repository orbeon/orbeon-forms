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

import org.orbeon.dom.Element
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

import scala.collection.mutable

object LHHA {
  val LHHAQNames = Set(LABEL_QNAME, HELP_QNAME, HINT_QNAME, ALERT_QNAME)
  def isLHHA(e: Element) = LHHAQNames(e.getQName)

  def getBeforeAfterOrderTokens(tokens: String): (List[String], List[String]) = {

    val orderTokens =
      tokens.splitTo[List]()

    val controlIndex = orderTokens.indexOf("control")

    (
      if (controlIndex == -1) orderTokens else orderTokens.take(controlIndex + 1),
      if (controlIndex == -1) Nil         else orderTokens.drop(controlIndex)
    )
  }
}

// Control support for nested or external LHHA elements
trait StaticLHHASupport extends SimpleElementAnalysis {

  import Private._

  // Because an LHHA might follow a control in the document, attachment must be deferred
  def attachLHHA(lhhaAnalysis: LHHAAnalysis): Unit =
    if (lhhaAnalysis.localName == "alert")
      _alerts :+= lhhaAnalysis
    else
      _lhh += lhhaAnalysis.localName → lhhaAnalysis

  def lhh(lhhaType: String) = _lhh.get(lhhaType)
  def alerts                = _alerts

  def hasLHHA(lhhaType: String) =
    if (lhhaType == "alert") alerts.nonEmpty else lhh(lhhaType).nonEmpty

  def hasLocal(lhhaType: String) =
    lhhaAsList(lhhaType) exists (_.isLocal)

  def lhhaValueAnalyses(lhhaType: String) =
    lhhaAsList(lhhaType) flatMap (_.getValueAnalysis)

  val beforeAfterTokensOpt: Option[(List[String], List[String])] =
    element.attributeValueOpt(XFormsConstants.XXFORMS_ORDER_QNAME) map LHHA.getBeforeAfterOrderTokens

  // analyzeXPath(): this is done as part of control analysis, see:
  // https://github.com/orbeon/orbeon-forms/issues/2185

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    allLHHA foreach (_.freeTransientState())
  }

  private object Private {

    // All LHHA, local or external
    // There can only be multiple alerts for now, not multiple LHH, so store alerts separately
    val _lhh    = mutable.HashMap.empty[String, LHHAAnalysis]
    var _alerts = List.empty[LHHAAnalysis]

    def lhhaAsList(lhhaType: String) =
      if (lhhaType == "alert") alerts else lhh(lhhaType).toList

    def allLHHA = _lhh.values ++ _alerts
  }
}
