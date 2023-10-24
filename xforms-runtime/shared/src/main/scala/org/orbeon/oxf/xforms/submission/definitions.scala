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

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.oxf.util.ConnectionResult
import org.orbeon.oxf.util.XPathCache.XPathContext
import org.orbeon.oxf.xforms.event.XFormsEvent.TunnelProperties
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.saxon.om

import scala.util.Try


sealed trait ReplaceType extends EnumEntry with Lowercase

object ReplaceType extends Enum[ReplaceType] {

  val values = findValues

  case object All      extends ReplaceType
  case object Instance extends ReplaceType
  case object Text     extends ReplaceType
  case object None     extends ReplaceType
  case object Binary   extends ReplaceType
}

case class RefContext(
  refNodeInfo                  : om.NodeInfo,
  refInstanceOpt               : Option[XFormsInstance],
  submissionElementContextItem : om.Item,
  xpathContext                 : XPathContext
)

case class ConnectResult(
  submissionEffectiveId : String,
  result                : Try[(Replacer, ConnectionResult)]
)

sealed trait ReplaceResult
object ReplaceResult {
  case object None                                                                                                                       extends ReplaceResult
  case class  SendDone (cxr: ConnectionResult, tunnelProperties: Option[TunnelProperties])                                               extends ReplaceResult
  case class  SendError(t: Throwable, ctx: Either[Option[ConnectionResult], Option[String]], tunnelProperties: Option[TunnelProperties]) extends ReplaceResult
  case class  Throw    (t: Throwable)                                                                                                    extends ReplaceResult
}
