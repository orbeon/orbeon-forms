package org.orbeon.oxf.xforms.analysis.controls

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.XFormsNames.XFORMS_NAMESPACE_SHORT


sealed trait LHHA extends EnumEntry with Lowercase

object LHHA extends Enum[LHHA] {

  val values = findValues

  // NOTE: Order as "LHHA" is important to some callers so don't change it!
  case object Label extends LHHA
  case object Help  extends LHHA
  case object Hint  extends LHHA
  case object Alert extends LHHA

  val size = values.size

  val QNameForValue: Map[LHHA, QName] = values map (value => value -> QName(value.entryName, XFORMS_NAMESPACE_SHORT)) toMap
  val NamesSet     : Set[String]      = values.map(_.entryName).toSet
  val QNamesSet    : Set[QName]       = QNameForValue.values.toSet

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