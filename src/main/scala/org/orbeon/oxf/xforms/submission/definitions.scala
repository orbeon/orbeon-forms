/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.submission

import enumeratum._
import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.saxon.om.{Item, NodeInfo}

sealed abstract class ReplaceType extends EnumEntry

object ReplaceType extends Enum[ReplaceType] {

  val values = findValues

  case object All      extends ReplaceType
  case object Instance extends ReplaceType
  case object Text     extends ReplaceType
  case object None     extends ReplaceType

  // For Java callers
  def isReplaceAll     (replaceType: ReplaceType) = replaceType == All
  def isReplaceInstance(replaceType: ReplaceType) = replaceType == Instance
  def isReplaceText    (replaceType: ReplaceType) = replaceType == Text
  def isReplaceNone    (replaceType: ReplaceType) = replaceType == None
}

sealed abstract class RelevanceHandling extends EnumEntry

object RelevanceHandling extends Enum[RelevanceHandling] {

  val values = findValues

  case object Keep   extends RelevanceHandling
  case object Remove extends RelevanceHandling
  case object Empty  extends RelevanceHandling
}

case class RefContext(
  refNodeInfo                  : NodeInfo,
  refInstanceOpt               : Option[XFormsInstance],
  submissionElementContextItem : Item,
  xpathContext                 : XPathContext
)

sealed abstract class UrlType extends EnumEntry

object UrlType extends Enum[UrlType] {

  val values = findValues

  case object Action   extends UrlType
  case object Render   extends UrlType
  case object Resource extends UrlType
}