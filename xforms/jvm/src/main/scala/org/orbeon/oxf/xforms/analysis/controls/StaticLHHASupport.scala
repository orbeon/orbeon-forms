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

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.analysis.{SimpleElementAnalysis, XPathAnalysis}
import org.orbeon.xforms.XFormsNames

import scala.collection.compat._

sealed trait LHHA extends EnumEntry with Lowercase

object LHHA extends Enum[LHHA] {

  val values = findValues

  // NOTE: Order as "LHHA" is important to some callers so don't change it!
  case object Label extends LHHA
  case object Help  extends LHHA
  case object Hint  extends LHHA
  case object Alert extends LHHA

  val size = values.size

  val QNameForValue = values map (value => value -> QName(value.entryName, XFORMS_NAMESPACE_SHORT)) toMap
  val NamesSet      : Set[String] = values.map(_.entryName).to(Set)
  val QNamesSet     : Set[QName] = QNameForValue.values.to(Set)

  // By default all controls support HTML LHHA
  val DefaultLHHAHTMLSupport: Set[LHHA] = values.toSet

  def isLHHA(e: Element): Boolean = QNamesSet(e.getQName)

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
    lhhaAsList(lhhaType) flatMap (_.getValueAnalysis)

  def hasLHHAPlaceholder(lhhaType: LHHA): Boolean =
    lhh(lhhaType) match {
      case Some(lhh) => lhh.isPlaceholder
      case None      => false

    }

  val beforeAfterTokensOpt: Option[(List[String], List[String])] =
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
